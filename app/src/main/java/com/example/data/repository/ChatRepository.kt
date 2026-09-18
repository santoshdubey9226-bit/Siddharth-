package com.example.data.repository

import com.example.data.local.dao.ChatDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.model.AppSettings
import com.example.data.model.AssistantMode
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiGenerationConfig
import com.example.data.remote.GeminiInlineData
import com.example.data.remote.GeminiPart
import com.example.data.remote.GeminiRequest
import com.example.data.remote.GeminiThinkingConfig
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiService: GeminiApiService
) {
    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun createNewSession(mode: AssistantMode, customTitle: String? = null): String {
        val sessionId = UUID.randomUUID().toString()
        val title = customTitle ?: "New ${mode.displayName} Chat"
        val session = ChatSessionEntity(
            sessionId = sessionId,
            title = title,
            mode = mode.id,
            createdAt = System.currentTimeMillis(),
            lastUpdatedAt = System.currentTimeMillis()
        )
        chatDao.insertSession(session)
        return sessionId
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        chatDao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearAll() {
        chatDao.clearAllSessions()
    }

    suspend fun saveUserMessage(
        sessionId: String,
        text: String,
        imageBase64: String? = null,
        imageUri: String? = null,
        attachmentName: String? = null,
        mode: AssistantMode
    ): Long {
        val message = ChatMessageEntity(
            sessionId = sessionId,
            role = "user",
            content = text,
            imageBase64 = imageBase64,
            imageUri = imageUri,
            attachmentName = attachmentName,
            mode = mode.id,
            timestamp = System.currentTimeMillis()
        )
        val id = chatDao.insertMessage(message)

        // Update session title if it's the first user message
        val snapshot = chatDao.getMessagesSnapshot(sessionId)
        if (snapshot.size <= 2) {
            val shortTitle = if (text.length > 32) text.take(30) + "…" else text
            chatDao.updateSessionTitle(sessionId, shortTitle.ifBlank { "Chat (${mode.displayName})" })
        }

        return id
    }

    suspend fun saveAssistantMessage(
        sessionId: String,
        text: String,
        mode: AssistantMode
    ): Long {
        val message = ChatMessageEntity(
            sessionId = sessionId,
            role = "assistant",
            content = text,
            mode = mode.id,
            timestamp = System.currentTimeMillis()
        )
        return chatDao.insertMessage(message)
    }

    suspend fun deleteLastAssistantMessage(sessionId: String) {
        val messages = chatDao.getMessagesSnapshot(sessionId)
        val lastAssistant = messages.lastOrNull { it.role == "assistant" }
        if (lastAssistant != null) {
            chatDao.deleteMessageById(lastAssistant.id)
        }
    }

    /**
     * Calls Gemini with conversation context and system instructions.
     */
    suspend fun sendToGemini(
        sessionId: String,
        mode: AssistantMode,
        settings: AppSettings,
        extraAttachmentText: String? = null
    ): Result<String> {
        val history = chatDao.getMessagesSnapshot(sessionId)

        // Map database messages to GeminiContent objects
        val geminiContents = history.map { entity ->
            val parts = mutableListOf<GeminiPart>()

            // If image is present on user message
            if (!entity.imageBase64.isNullOrBlank()) {
                parts.add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = "image/jpeg",
                            data = entity.imageBase64
                        )
                    )
                )
            }

            // Append text content
            var textBody = entity.content
            if (entity.role == "user" && !entity.attachmentName.isNullOrBlank() && !extraAttachmentText.isNullOrBlank()) {
                textBody += "\n\n[Attached File: ${entity.attachmentName}]\n$extraAttachmentText"
            }
            parts.add(GeminiPart(text = textBody))

            GeminiContent(
                role = if (entity.role == "user") "user" else "model",
                parts = parts
            )
        }

        if (geminiContents.isEmpty()) {
            return Result.failure(IllegalStateException("No message to send"))
        }

        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = mode.systemPrompt + "\n" +
                            "Preferred language mode: ${settings.languagePreference}."
                )
            )
        )

        val thinkingConfig = if (settings.thinkingLevel != "none") {
            GeminiThinkingConfig(thinkingLevel = settings.thinkingLevel)
        } else null

        val generationConfig = GeminiGenerationConfig(
            temperature = settings.temperature,
            thinkingConfig = thinkingConfig
        )

        val request = GeminiRequest(
            contents = geminiContents,
            systemInstruction = systemInstruction,
            generationConfig = generationConfig
        )

        return apiService.generateContent(
            model = settings.selectedModel.modelId,
            request = request
        )
    }
}

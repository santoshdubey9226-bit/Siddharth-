package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.model.AppSettings
import com.example.data.model.AssistantMode
import com.example.data.remote.GeminiApiService
import com.example.data.repository.ChatRepository
import com.example.util.FileUtils
import com.example.util.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val apiService = GeminiApiService()
    val repository = ChatRepository(database.chatDao(), apiService)
    val ttsManager = TtsManager(application)

    // Current active mode
    private val _currentMode = MutableStateFlow(AssistantMode.GENERAL)
    val currentMode: StateFlow<AssistantMode> = _currentMode.asStateFlow()

    // Current active session ID
    private val _currentSessionId = MutableStateFlow<String>("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    // Input text in text box
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // Attached image
    private val _attachedImageUri = MutableStateFlow<Uri?>(null)
    val attachedImageUri: StateFlow<Uri?> = _attachedImageUri.asStateFlow()

    // Attached document
    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName: StateFlow<String?> = _attachedFileName.asStateFlow()

    private val _attachedFileContent = MutableStateFlow<String?>(null)

    // Generation state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Active generation coroutine job (used for "Stop Generating")
    private var generationJob: Job? = null

    // App Settings (Model, Temperature, Theme, etc.)
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // UI Feedback events (e.g. errors or alerts)
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    // All chat sessions from Room
    val sessions: StateFlow<List<ChatSessionEntity>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Messages for the current active session
    val messages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isBlank()) flowOf(emptyList())
            else repository.getMessagesForSession(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize with a new session on startup
        createNewChat()
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun onModeSelected(mode: AssistantMode) {
        _currentMode.value = mode
    }

    fun onSettingsChanged(newSettings: AppSettings) {
        _settings.value = newSettings
    }

    fun createNewChat(mode: AssistantMode = _currentMode.value) {
        viewModelScope.launch {
            ttsManager.stop()
            clearAttachment()
            _currentMode.value = mode
            val newSessionId = repository.createNewSession(mode)
            _currentSessionId.value = newSessionId
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            ttsManager.stop()
            clearAttachment()
            _currentSessionId.value = sessionId
            // Update mode from session
            val session = database.chatDao().getSessionById(sessionId)
            if (session != null) {
                _currentMode.value = AssistantMode.fromId(session.mode)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                createNewChat()
            }
        }
    }

    fun clearCurrentChat() {
        viewModelScope.launch {
            ttsManager.stop()
            if (_currentSessionId.value.isNotBlank()) {
                database.chatDao().deleteMessagesForSession(_currentSessionId.value)
            }
            clearAttachment()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            ttsManager.stop()
            repository.clearAll()
            createNewChat()
            _snackbarMessages.emit("All chat history cleared")
        }
    }

    fun setAttachedImage(uri: Uri?) {
        _attachedImageUri.value = uri
        if (uri != null) {
            _attachedFileName.value = null
            _attachedFileContent.value = null
        }
    }

    fun setAttachedFile(uri: Uri?) {
        if (uri == null) {
            clearAttachment()
            return
        }
        viewModelScope.launch {
            _attachedImageUri.value = null
            _attachedFileName.value = FileUtils.getFileName(getApplication(), uri)
            val content = FileUtils.readTextFromUri(getApplication(), uri)
            _attachedFileContent.value = content
        }
    }

    fun clearAttachment() {
        _attachedImageUri.value = null
        _attachedFileName.value = null
        _attachedFileContent.value = null
    }

    /**
     * Stop Generating: cancels current ongoing Gemini request immediately.
     */
    fun stopGenerating() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        viewModelScope.launch {
            _snackbarMessages.emit("Generation cancelled")
        }
    }

    /**
     * Send button action.
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        val imageUri = _attachedImageUri.value
        val fileName = _attachedFileName.value
        val fileContent = _attachedFileContent.value

        if (text.isBlank() && imageUri == null && fileName == null) {
            return
        }

        if (_isGenerating.value) {
            return
        }

        val promptToSend = if (text.isNotBlank()) text else "Analyze the attached file/image."
        _inputText.value = ""

        generationJob = viewModelScope.launch {
            _isGenerating.value = true

            // Ensure a session exists
            if (_currentSessionId.value.isBlank()) {
                _currentSessionId.value = repository.createNewSession(_currentMode.value)
            }
            val sessionId = _currentSessionId.value

            // Prepare base64 for image if attached
            var base64Image: String? = null
            if (imageUri != null) {
                base64Image = FileUtils.uriToBase64Jpeg(getApplication(), imageUri)
            }

            // Save user message to Room
            repository.saveUserMessage(
                sessionId = sessionId,
                text = promptToSend,
                imageBase64 = base64Image,
                imageUri = imageUri?.toString(),
                attachmentName = fileName,
                mode = _currentMode.value
            )

            // Clear active attachments from input bar
            clearAttachment()

            // Call Gemini API
            val result = repository.sendToGemini(
                sessionId = sessionId,
                mode = _currentMode.value,
                settings = _settings.value,
                extraAttachmentText = fileContent
            )

            result.fold(
                onSuccess = { assistantText ->
                    repository.saveAssistantMessage(
                        sessionId = sessionId,
                        text = assistantText,
                        mode = _currentMode.value
                    )
                },
                onFailure = { error ->
                    val friendlyError = "⚠️ ${error.message ?: "Could not get response from Gemini."}"
                    repository.saveAssistantMessage(
                        sessionId = sessionId,
                        text = friendlyError,
                        mode = _currentMode.value
                    )
                    _snackbarMessages.emit(friendlyError)
                }
            )

            _isGenerating.value = false
        }
    }

    /**
     * Regenerate the last assistant response.
     */
    fun regenerateLastResponse() {
        if (_isGenerating.value || _currentSessionId.value.isBlank()) return

        val sessionId = _currentSessionId.value
        generationJob = viewModelScope.launch {
            _isGenerating.value = true

            // Remove last assistant message
            repository.deleteLastAssistantMessage(sessionId)

            val result = repository.sendToGemini(
                sessionId = sessionId,
                mode = _currentMode.value,
                settings = _settings.value
            )

            result.fold(
                onSuccess = { assistantText ->
                    repository.saveAssistantMessage(
                        sessionId = sessionId,
                        text = assistantText,
                        mode = _currentMode.value
                    )
                },
                onFailure = { error ->
                    val friendlyError = "⚠️ ${error.message ?: "Failed to regenerate response."}"
                    repository.saveAssistantMessage(
                        sessionId = sessionId,
                        text = friendlyError,
                        mode = _currentMode.value
                    )
                    _snackbarMessages.emit(friendlyError)
                }
            )

            _isGenerating.value = false
        }
    }

    fun speakMessage(message: ChatMessageEntity) {
        ttsManager.speak(message.id, message.content)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}

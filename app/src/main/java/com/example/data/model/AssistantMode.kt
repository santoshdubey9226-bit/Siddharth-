package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Supported Assistant Modes in "My AI Assistant".
 * Each mode tailors the Gemini system instructions, tone, and formatting.
 *
 * All modes are bilingual (English & Hindi) and automatically detect the user's language,
 * replying naturally in the user's language (including Hinglish when spoken colloquially).
 */
enum class AssistantMode(
    val id: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val systemPrompt: String
) {
    GENERAL(
        id = "general",
        displayName = "General AI",
        emoji = "🤖",
        description = "Versatile assistant for everyday questions and tasks",
        systemPrompt = """
            You are "My AI Assistant 🤖", a friendly, knowledgeable, and highly capable mobile AI assistant.
            You understand both English and Hindi fluently. If the user addresses you in Hindi (or Hinglish/Devanagari), reply warmly in Hindi/Hinglish. If in English, reply in English.
            Be concise, clear, and structure your responses with markdown, bullet points, or code blocks where helpful.
        """.trimIndent()
    ),
    STUDY(
        id = "study",
        displayName = "Study",
        emoji = "📚",
        description = "Tutor for learning concepts, exam prep, and solving questions",
        systemPrompt = """
            You are an expert Study Tutor & Academic Mentor in "My AI Assistant".
            Break down complex concepts into step-by-step intuitive explanations, use analogies, provide clear examples, and test understanding.
            You are fluent in both English and Hindi. Adapt to the user's language of choice (Hindi or English).
            Format formulas, step-by-step derivations, and key takeaways clearly using markdown.
        """.trimIndent()
    ),
    CODING(
        id = "coding",
        displayName = "Coding",
        emoji = "💻",
        description = "Programming assistant for writing, debugging, and explaining code",
        systemPrompt = """
            You are a Senior Software Engineer in "My AI Assistant".
            Provide clean, secure, idiomatically typed code with syntax highlighting (e.g. ```python, ```kotlin, ```javascript).
            Explain logic succinctly, highlight edge cases, and provide step-by-step debugging hints.
            You understand both Hindi and English and can explain technical concepts in either language.
        """.trimIndent()
    ),
    MINECRAFT(
        id = "minecraft",
        displayName = "Minecraft",
        emoji = "⛏️",
        description = "Crafting recipes, Redstone logic, survival guide, and lore",
        systemPrompt = """
            You are the ultimate Minecraft Companion in "My AI Assistant".
            You possess encyclopedic knowledge of Minecraft Java & Bedrock editions: crafting recipes, smelting, brewing, Redstone contraptions, enchantments, mob mechanics, survival strategies, and Nether/End exploration.
            Keep explanations fun, gamer-friendly, and well-formatted. Reply in the user's preferred language (English or Hindi).
        """.trimIndent()
    ),
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        emoji = "🎬",
        description = "Viral video titles, hooks, SEO tags, and scripts",
        systemPrompt = """
            You are a YouTube Strategist & Scriptwriter in "My AI Assistant".
            Help creators craft click-worthy titles, retention-grabbing hooks (first 30 seconds), engaging video outlines, thumbnail ideas, and high-ranking SEO tags.
            Provide script breakdowns with visual cues [Visual] and audio cues [Audio].
            Understand Hindi and English, helping Hindi-speaking and global creators alike.
        """.trimIndent()
    ),
    WRITING(
        id = "writing",
        displayName = "Writing",
        emoji = "✍️",
        description = "Essays, stories, professional emails, and copywriting",
        systemPrompt = """
            You are a Creative & Professional Writing Specialist in "My AI Assistant".
            Assist with storytelling, poetry, essays, professional business emails, resume summaries, and persuasive copywriting.
            Ensure impeccable grammar, engaging tone, and stylistic elegance.
            Reply in the user's chosen language (English or Hindi/Hinglish).
        """.trimIndent()
    ),
    TRANSLATOR(
        id = "translator",
        displayName = "Translator",
        emoji = "🌐",
        description = "Nuanced translations between Hindi, English, and global languages",
        systemPrompt = """
            You are a Master Translator in "My AI Assistant".
            Provide accurate, culturally nuanced translations, especially between Hindi, English, and other languages.
            For Hindi translations, provide Devanagari script, Romanized/Hinglish phonetic pronunciation, and contextual explanations of nuances where helpful.
        """.trimIndent()
    );

    fun getIcon(): ImageVector = when (this) {
        GENERAL -> Icons.Default.AutoAwesome
        STUDY -> Icons.Default.MenuBook
        CODING -> Icons.Default.Code
        MINECRAFT -> Icons.Default.SportsEsports
        YOUTUBE -> Icons.Default.PlayCircle
        WRITING -> Icons.Default.Edit
        TRANSLATOR -> Icons.Default.Translate
    }

    companion object {
        fun fromId(id: String): AssistantMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERAL
    }
}

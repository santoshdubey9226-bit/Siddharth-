package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentMessageId = MutableStateFlow<Long?>(null)
    val currentMessageId: StateFlow<Long?> = _currentMessageId.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentMessageId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentMessageId.value = null
                }
            })
        }
    }

    /**
     * Speaks the given text. Detects Hindi vs English and sets the optimal locale.
     */
    fun speak(messageId: Long, text: String) {
        if (!isInitialized || textToSpeech == null) return

        // If clicking the currently playing message, stop it
        if (_isSpeaking.value && _currentMessageId.value == messageId) {
            stop()
            return
        }

        stop()

        // Clean markdown syntax for cleaner speech
        val cleanedText = cleanMarkdownForSpeech(text)

        // Detect Hindi characters
        val containsHindi = text.any { it in '\u0900'..'\u097F' }
        val targetLocale = if (containsHindi) Locale("hi", "IN") else Locale.ENGLISH

        val result = textToSpeech?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech?.setLanguage(Locale.getDefault())
        }

        _currentMessageId.value = messageId
        _isSpeaking.value = true
        textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "TTS_$messageId")
    }

    fun stop() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        _currentMessageId.value = null
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), " [code snippet] ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("#+\\s*"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .trim()
    }
}

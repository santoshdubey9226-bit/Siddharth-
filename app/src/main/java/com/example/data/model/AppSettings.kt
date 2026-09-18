package com.example.data.model

enum class ThemeMode(val displayName: String) {
    SYSTEM("Follow System"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class GeminiModelOption(val modelId: String, val displayName: String, val description: String) {
    GEMINI_3_5_FLASH("gemini-3.5-flash", "Gemini 3.5 Flash (Recommended)", "Fast, versatile, and high intelligence for everyday text and multimodal tasks"),
    GEMINI_3_1_PRO("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "Advanced reasoning, deep coding, and complex logic")
}

data class AppSettings(
    val selectedModel: GeminiModelOption = GeminiModelOption.GEMINI_3_5_FLASH,
    val temperature: Float = 0.7f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languagePreference: String = "Auto (Hindi / English)",
    val thinkingLevel: String = "none" // "none", "low", "high"
)

package com.nutriscan.app.ai

import com.nutriscan.app.data.AiProvider
import com.nutriscan.app.data.AppSettings

/** Elige Claude o Gemini según lo configurado en Ajustes. */
class FoodAnalyzer(
    private val claude: ClaudeAnalyzer = ClaudeAnalyzer(),
    private val gemini: GeminiAnalyzer = GeminiAnalyzer(),
) {
    suspend fun analyze(settings: AppSettings, jpeg: ByteArray, userHint: String?): FoodAnalysis =
        when (settings.provider) {
            AiProvider.CLAUDE -> claude.analyze(settings.apiKey, settings.claudeModel, jpeg, userHint)
            AiProvider.GEMINI -> gemini.analyze(settings.geminiKey, settings.geminiModel, jpeg, userHint)
        }
}

package com.nutriscan.app.ai

import com.nutriscan.app.data.AiModel
import com.nutriscan.app.data.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Envía la foto a Gemini (Google) con la API REST `generateContent`. */
class GeminiAnalyzer(
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(
        apiKey: String,
        model: AiModel,
        jpeg: ByteArray,
        userHint: String?,
    ): FoodAnalysis = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AnalysisException("Añade tu API key de Google Gemini en Ajustes para poder escanear.")
        require(model.provider == AiProvider.GEMINI)

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", FoodPrompt.SYSTEM))))
            .put(
                "contents", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts", JSONArray()
                                .put(
                                    JSONObject().put(
                                        "inlineData", JSONObject()
                                            .put("mimeType", "image/jpeg")
                                            .put("data", Base64.getEncoder().encodeToString(jpeg))
                                    )
                                )
                                .put(JSONObject().put("text", FoodPrompt.instruction(userHint)))
                        )
                )
            )
            .put(
                "generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", FoodPrompt.geminiSchema())
            )

        val request = Request.Builder()
            .url("$baseUrl/v1beta/models/${model.id}:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val (code, text) = send(request)
        if (code !in 200..299) throw errorFor(code, text)
        FoodPrompt.parse(extractText(text))
    }

    /** Envía la petición reintentando errores temporales del servidor o de red. */
    private suspend fun send(request: Request): Pair<Int, String> {
        var attempt = 0
        while (true) {
            attempt++
            try {
                http.newCall(request).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (r.code in RETRYABLE && attempt < MAX_ATTEMPTS) {
                        delay(1000L * attempt)
                        return@use null
                    }
                    return r.code to text
                }
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw AnalysisException("Sin conexión a internet o la conexión ha fallado.")
                delay(1000L * attempt)
            }
        }
    }

    private fun extractText(raw: String): String {
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw AnalysisException("Respuesta inesperada de Gemini. Vuelve a intentarlo.")
        }
        json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
            throw AnalysisException("La IA no ha podido analizar esta imagen. Prueba con otra foto o añádelo a mano.")
        }
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AnalysisException("Gemini no ha devuelto ninguna respuesta. Vuelve a intentarlo.")
        when (candidate.optString("finishReason")) {
            "", "STOP", "FINISH_REASON_UNSPECIFIED" -> Unit
            "MAX_TOKENS", "CONTINUATION" ->
                throw AnalysisException("La respuesta de la IA llegó incompleta. Vuelve a intentarlo.")
            else ->
                throw AnalysisException("La IA no ha podido analizar esta imagen. Prueba con otra foto o añádelo a mano.")
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        // Las partes con "thought": true son razonamiento interno, no la respuesta.
        return (0 until parts.length())
            .map { parts.getJSONObject(it) }
            .filter { !it.optBoolean("thought", false) }
            .joinToString("") { it.optString("text") }
    }

    private fun errorFor(code: Int, raw: String): AnalysisException {
        val error = runCatching { JSONObject(raw).getJSONObject("error") }.getOrNull()
        val message = error?.optString("message").orEmpty()
        val invalidKey = raw.contains("API_KEY_INVALID") || message.contains("API key not valid", ignoreCase = true)
        return AnalysisException(
            when {
                invalidKey -> "La API key de Gemini no es válida. Revísala en Ajustes."
                code == 400 -> "Petición rechazada por Gemini: $message"
                code == 401 || code == 403 -> "Tu API key de Gemini no tiene permiso para usar este modelo. Prueba otro en Ajustes."
                code == 404 -> "Este modelo de Gemini no está disponible. Elige otro en Ajustes."
                code == 429 -> "Has superado el límite de uso de Gemini (el nivel gratuito tiene un máximo por minuto y por día). Espera un poco."
                code >= 500 -> "Gemini está saturado ahora mismo. Inténtalo en unos segundos."
                else -> "Error de Gemini ($code). Inténtalo de nuevo."
            }
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        val RETRYABLE = setOf(500, 502, 503, 504)
    }
}

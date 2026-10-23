package com.nutriscan.app

import com.nutriscan.app.ai.AnalysisException
import com.nutriscan.app.ai.FoodAnalyzer
import com.nutriscan.app.ai.GeminiAnalyzer
import com.nutriscan.app.data.AiModel
import com.nutriscan.app.data.AiProvider
import com.nutriscan.app.data.AppSettings
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Comprueba la petición a Gemini y la interpretación de la respuesta con un servidor falso local. */
class GeminiAnalyzerTest {
    private lateinit var server: MockWebServer
    private val responses = ArrayDeque<MockResponse>()
    private var lastBody: JSONObject? = null
    private var lastPath: String? = null
    private var lastKey: String? = null
    private var requests = 0

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 8, 7)

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests++
                lastPath = request.path
                lastKey = request.getHeader("x-goog-api-key")
                lastBody = JSONObject(request.body.readUtf8())
                return responses.removeFirstOrNull() ?: MockResponse().setResponseCode(500)
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun analyzer() = GeminiAnalyzer(baseUrl = server.url("/").toString().trimEnd('/'))

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody(body)

    private fun candidate(text: String, finish: String = "STOP", thought: String? = null): String {
        val parts = JSONArray()
        if (thought != null) parts.put(JSONObject().put("text", thought).put("thought", true))
        parts.put(JSONObject().put("text", text))
        return JSONObject().put(
            "candidates", JSONArray().put(
                JSONObject()
                    .put("content", JSONObject().put("role", "model").put("parts", parts))
                    .put("finishReason", finish)
            )
        ).toString()
    }

    private val sample = """
        {"is_food": true, "dish_name": "Ensalada césar", "confidence": "alta", "notes": "Ración individual.",
         "ingredients": [
           {"name": "lechuga romana", "grams": 100, "kcal": 17, "protein_g": 1.2, "carbs_g": 3.3, "fat_g": 0.3},
           {"name": "salsa césar", "grams": 30, "kcal": 160, "protein_g": 0.6, "carbs_g": 1, "fat_g": 17}
         ]}
    """.trimIndent()

    @Test
    fun sendsImageAndSchemaAndParsesResult() = runTest {
        responses += ok(candidate(sample, thought = "pensando…"))
        val result = analyzer().analyze("AIza-test", AiModel.GEMINI_FLASH, jpeg, "sin picatostes")

        assertEquals("Ensalada césar", result.dishName)
        assertEquals(2, result.ingredients.size)
        assertEquals("Lechuga romana", result.ingredients[0].name)
        assertEquals(17.0, result.ingredients[1].fat, 0.001)

        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", lastPath)
        assertEquals("AIza-test", lastKey)
        val body = lastBody!!
        println("Gemini request (image trimmed): " + body.toString().replace(Regex("\"data\":\"[^\"]*\""), "\"data\":\"…\""))
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val inline = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("image/jpeg", inline.getString("mimeType"))
        assertEquals(java.util.Base64.getEncoder().encodeToString(jpeg), inline.getString("data"))
        assertTrue(parts.getJSONObject(1).getString("text").contains("sin picatostes"))
        val config = body.getJSONObject("generationConfig")
        assertEquals("application/json", config.getString("responseMimeType"))
        val schema = config.getJSONObject("responseSchema")
        assertEquals("OBJECT", schema.getString("type"))
        assertTrue(schema.getJSONObject("properties").has("ingredients"))
        assertTrue(body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text").contains("nutricionista"))
    }

    @Test
    fun retriesOnServerOverload() = runTest {
        responses += MockResponse().setResponseCode(503).setBody("""{"error":{"code":503,"message":"overloaded","status":"UNAVAILABLE"}}""")
        responses += ok(candidate(sample))
        val result = analyzer().analyze("AIza-test", AiModel.GEMINI_LITE, jpeg, null)
        assertEquals(2, requests)
        assertEquals("Ensalada césar", result.dishName)
    }

    @Test
    fun invalidKeyBecomesFriendlyError() = runTest {
        responses += MockResponse().setResponseCode(400).setBody(
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""
        )
        expectError("API key de Gemini no es válida") { analyzer().analyze("bad", AiModel.GEMINI_FLASH, jpeg, null) }
    }

    @Test
    fun quotaBecomesFriendlyError() = runTest {
        responses += MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}""")
        expectError("límite de uso") { analyzer().analyze("AIza-test", AiModel.GEMINI_FLASH, jpeg, null) }
    }

    @Test
    fun blockedImageBecomesFriendlyError() = runTest {
        responses += ok("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
        expectError("no ha podido analizar") { analyzer().analyze("AIza-test", AiModel.GEMINI_FLASH, jpeg, null) }
        responses += ok(candidate("", finish = "SAFETY"))
        expectError("no ha podido analizar") { analyzer().analyze("AIza-test", AiModel.GEMINI_FLASH, jpeg, null) }
    }

    @Test
    fun dispatcherUsesSelectedProviderAndKey() = runTest {
        responses += ok(candidate(sample))
        val settings = AppSettings(apiKey = "sk-ant-x", geminiKey = "AIza-y", provider = AiProvider.GEMINI)
        FoodAnalyzer(gemini = analyzer()).analyze(settings, jpeg, null)
        assertEquals("AIza-y", lastKey)
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", lastPath)
    }

    @Test
    fun missingKeyFailsBeforeCallingApi() = runTest {
        expectError("API key de Google Gemini") { analyzer().analyze("", AiModel.GEMINI_FLASH, jpeg, null) }
        assertEquals(0, requests)
    }

    private suspend fun expectError(fragment: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Debería fallar")
        } catch (e: AnalysisException) {
            assertTrue("Mensaje: ${e.message}", e.message!!.contains(fragment))
        }
    }
}

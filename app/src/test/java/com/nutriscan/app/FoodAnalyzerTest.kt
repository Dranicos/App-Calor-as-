package com.nutriscan.app

import com.nutriscan.app.ai.AnalysisException
import com.nutriscan.app.ai.FoodAnalyzer
import com.nutriscan.app.data.AiModel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Comprueba la petición que se envía a Claude y cómo se interpreta la respuesta, con un servidor falso local. */
class FoodAnalyzerTest {
    private lateinit var server: MockWebServer
    private var status = 200
    private var responseBody = ""
    private var lastBody: JSONObject? = null
    private var lastPath: String? = null
    private var lastBeta: String? = null
    private var lastKey: String? = null

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                lastPath = request.path
                lastBeta = request.getHeader("anthropic-beta")
                lastKey = request.getHeader("Authorization")
                lastBody = JSONObject(request.body.readUtf8())
                return MockResponse()
                    .setResponseCode(status)
                    .setHeader("content-type", "application/json")
                    .setBody(responseBody)
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun analyzer() = FoodAnalyzer(baseUrl = server.url("/").toString().trimEnd('/'))

    private fun message(text: String, stopReason: String = "end_turn") = JSONObject()
        .put("id", "msg_test")
        .put("type", "message")
        .put("role", "assistant")
        .put("model", "claude-opus-5-5")
        .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        .put("stop_reason", stopReason)
        .put("stop_sequence", JSONObject.NULL)
        .put("usage", JSONObject().put("input_tokens", 1500).put("output_tokens", 300))
        .toString()

    private val sample = """
        {"is_food": true, "dish_name": "Pollo con arroz", "confidence": "media",
         "notes": "Ración mediana.",
         "ingredients": [
           {"name": "pechuga de pollo", "grams": 150, "kcal": 248, "protein_g": 46.5, "carbs_g": 0, "fat_g": 5.4},
           {"name": "arroz blanco cocido", "grams": 200, "kcal": 260, "protein_g": 5.4, "carbs_g": 56, "fat_g": 0.6}
         ]}
    """.trimIndent()

    @Test
    fun sendsImageSchemaAndFallbackAndParsesResult() = runTest {
        responseBody = message(sample)
        val result = analyzer().analyze("sk-ant-test", AiModel.OPUS, jpeg, "sin aceite")

        // Respuesta interpretada
        assertTrue(result.isFood)
        assertEquals("Pollo con arroz", result.dishName)
        assertEquals(2, result.ingredients.size)
        assertEquals("Pechuga de pollo", result.ingredients[0].name)
        assertEquals(46.5, result.ingredients[0].protein, 0.001)

        // Petición enviada
        // El SDK 3.x envía la API key como "Authorization: Bearer".
        assertEquals("Bearer sk-ant-test", lastKey)
        assertTrue(lastPath!!.startsWith("/v1/messages"))
        assertTrue(lastBeta!!.contains("server-side-fallback-2026-07-01"))
        val body = lastBody!!
        println("Request body (image data trimmed): " + body.toString().replace(Regex("\"data\":\"[^\"]*\""), "\"data\":\"…\""))
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals("default", body.getString("fallbacks"))
        assertFalse(body.has("thinking"))
        val outputConfig = body.getJSONObject("output_config")
        assertEquals("medium", outputConfig.getString("effort"))
        assertEquals("json_schema", outputConfig.getJSONObject("format").getString("type"))
        val schema = outputConfig.getJSONObject("format").getJSONObject("schema")
        assertEquals(false, schema.getBoolean("additionalProperties"))
        assertTrue(schema.getJSONObject("properties").has("ingredients"))

        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val image = content.getJSONObject(0)
        assertEquals("image", image.getString("type"))
        assertEquals("image/jpeg", image.getJSONObject("source").getString("media_type"))
        assertEquals(java.util.Base64.getEncoder().encodeToString(jpeg), image.getJSONObject("source").getString("data"))
        assertTrue(content.getJSONObject(1).getString("text").contains("sin aceite"))
    }

    @Test
    fun haikuDoesNotSendFallbacks() = runTest {
        responseBody = message(sample)
        analyzer().analyze("sk-ant-test", AiModel.HAIKU, jpeg, null)
        assertEquals("claude-haiku-5-5", lastBody!!.getString("model"))
        assertFalse(lastBody!!.has("fallbacks"))
    }

    @Test
    fun refusalBecomesFriendlyError() = runTest {
        responseBody = message("", stopReason = "refusal")
        try {
            analyzer().analyze("sk-ant-test", AiModel.OPUS, jpeg, null)
            fail("Debería fallar")
        } catch (e: AnalysisException) {
            assertTrue(e.message!!.contains("no ha podido analizar"))
        }
    }

    @Test
    fun invalidKeyBecomesFriendlyError() = runTest {
        status = 401
        responseBody = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        try {
            analyzer().analyze("sk-ant-bad", AiModel.OPUS, jpeg, null)
            fail("Debería fallar")
        } catch (e: AnalysisException) {
            assertTrue(e.message!!.contains("API key no es válida"))
        }
    }

    @Test
    fun missingKeyFailsBeforeCallingApi() = runTest {
        try {
            analyzer().analyze("", AiModel.OPUS, jpeg, null)
            fail("Debería fallar")
        } catch (e: AnalysisException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(null, lastBody)
    }
}

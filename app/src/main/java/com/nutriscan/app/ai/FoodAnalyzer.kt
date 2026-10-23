package com.nutriscan.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.nutriscan.app.data.AiModel
import com.nutriscan.app.domain.Ingredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.util.Base64

data class FoodAnalysis(
    val isFood: Boolean,
    val dishName: String,
    val confidence: String,
    val notes: String,
    val ingredients: List<Ingredient>,
)

class AnalysisException(message: String) : Exception(message)

/** Envía la foto a Claude y devuelve una estimación de calorías y macros por ingrediente. */
class FoodAnalyzer(private val baseUrl: String? = null) {

    suspend fun analyze(
        apiKey: String,
        model: AiModel,
        jpeg: ByteArray,
        userHint: String?,
    ): FoodAnalysis = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AnalysisException("Añade tu API key de Anthropic en Ajustes para poder escanear.")

        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(120))
            .maxRetries(2)
            .apply { if (baseUrl != null) baseUrl(baseUrl) }
            .build()

        try {
            val image = BetaImageBlockParam.builder()
                .source(
                    BetaBase64ImageSource.builder()
                        .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                        .data(Base64.getEncoder().encodeToString(jpeg))
                        .build()
                )
                .build()

            val instruction = buildString {
                append("Analiza esta foto y estima las calorías y macronutrientes.")
                if (!userHint.isNullOrBlank()) {
                    append("\n\nIndicaciones del usuario (tenlas en cuenta y corrige la estimación): ")
                    append(userHint.trim())
                }
            }

            val builder = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(
                    BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.MEDIUM)
                        .format(BetaJsonOutputFormat.builder().schema(schema()).build())
                        .build()
                )
                .addUserMessageOfContentBlockParams(
                    listOf(BetaContentBlockParam.ofImage(image), BetaContentBlockParam.ofText(instruction))
                )

            // Si el filtro de seguridad rechazara la petición (muy raro con comida),
            // la API la reintenta automáticamente con otro modelo.
            if (model != AiModel.HAIKU) {
                builder.addBeta(FALLBACK_BETA).fallbacksDefault()
            }

            val response = client.beta().messages().create(builder.build())

            val stop = response.stopReason().orElse(null)
            if (stop == BetaStopReason.REFUSAL) {
                throw AnalysisException("La IA no ha podido analizar esta imagen. Prueba con otra foto o añádelo a mano.")
            }
            if (stop == BetaStopReason.MAX_TOKENS) {
                throw AnalysisException("La respuesta de la IA llegó incompleta. Vuelve a intentarlo.")
            }

            val text = response.content()
                .mapNotNull { it.text().orElse(null)?.text() }
                .joinToString("")
            parse(text)
        } catch (e: AnalysisException) {
            throw e
        } catch (e: UnauthorizedException) {
            throw AnalysisException("La API key no es válida. Revísala en Ajustes.")
        } catch (e: PermissionDeniedException) {
            throw AnalysisException("Tu API key no tiene permiso para usar este modelo. Prueba otro en Ajustes.")
        } catch (e: RateLimitException) {
            throw AnalysisException("Demasiadas peticiones o sin saldo en tu cuenta de Anthropic. Espera un momento.")
        } catch (e: BadRequestException) {
            throw AnalysisException("Petición rechazada: ${e.message}")
        } catch (e: InternalServerException) {
            throw AnalysisException("El servicio de IA está saturado ahora mismo. Inténtalo en unos segundos.")
        } catch (e: AnthropicServiceException) {
            throw AnalysisException("Error del servicio de IA (${e.statusCode()}). Inténtalo de nuevo.")
        } catch (e: AnthropicIoException) {
            throw AnalysisException("Sin conexión a internet o la conexión ha fallado.")
        } finally {
            client.close()
        }
    }

    private fun parse(text: String): FoodAnalysis {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw AnalysisException("No se pudo interpretar la respuesta de la IA. Vuelve a intentarlo.")
        }
        val arr = json.optJSONArray("ingredients")
        val ingredients = (0 until (arr?.length() ?: 0)).map { i ->
            val o = arr!!.getJSONObject(i)
            Ingredient(
                name = o.optString("name").replaceFirstChar { it.uppercase() },
                grams = o.optDouble("grams", 0.0).coerceAtLeast(0.0),
                kcal = o.optDouble("kcal", 0.0).coerceAtLeast(0.0),
                protein = o.optDouble("protein_g", 0.0).coerceAtLeast(0.0),
                carbs = o.optDouble("carbs_g", 0.0).coerceAtLeast(0.0),
                fat = o.optDouble("fat_g", 0.0).coerceAtLeast(0.0),
            )
        }
        return FoodAnalysis(
            isFood = json.optBoolean("is_food", true),
            dishName = json.optString("dish_name"),
            confidence = json.optString("confidence", "media"),
            notes = json.optString("notes"),
            ingredients = ingredients,
        )
    }

    private fun schema(): BetaJsonOutputFormat.Schema {
        val number = mapOf("type" to "number")
        val ingredient = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("name", "grams", "kcal", "protein_g", "carbs_g", "fat_g"),
            "properties" to mapOf(
                "name" to mapOf("type" to "string", "description" to "Nombre del ingrediente en español"),
                "grams" to number,
                "kcal" to number,
                "protein_g" to number,
                "carbs_g" to number,
                "fat_g" to number,
            ),
        )
        val props = mapOf(
            "is_food" to mapOf("type" to "boolean"),
            "dish_name" to mapOf("type" to "string", "description" to "Nombre corto del plato o producto"),
            "confidence" to mapOf("type" to "string", "enum" to listOf("alta", "media", "baja")),
            "notes" to mapOf("type" to "string", "description" to "Comentario breve sobre la estimación (máx. 2 frases)"),
            "ingredients" to mapOf("type" to "array", "items" to ingredient),
        )
        return BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .putAdditionalProperty("required", JsonValue.from(props.keys.toList()))
            .putAdditionalProperty("properties", JsonValue.from(props))
            .build()
    }

    private companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        val SYSTEM_PROMPT = """
            Eres un nutricionista que estima calorías y macronutrientes a partir de fotos para una app de seguimiento personal.

            La foto puede ser un plato cocinado, una bebida, una pieza de fruta o un producto envasado.
            - Producto envasado con la etiqueta nutricional visible: usa los valores de la etiqueta para el envase completo o para una ración típica, y di cuál en las notas.
            - Plato: identifica los ingredientes principales visibles y los probables aunque no se vean (aceite de cocinar, salsas, azúcar, mantequilla). Estima los gramos de cada uno por el tamaño aparente de la ración, usando como referencia el plato, los cubiertos o las manos.
            - Usa valores nutricionales de referencia estándar (tablas tipo BEDCA o USDA).
            - Las kcal de cada ingrediente deben cuadrar con sus macros (≈ 4·proteína + 4·carbohidratos + 9·grasa).
            - Agrupa los ingredientes menores; como máximo unos 10 ingredientes.
            - Escribe los nombres en español.
            - Si en la imagen no hay comida ni bebida, devuelve is_food = false y la lista de ingredientes vacía.

            Es una estimación orientativa: basta con un valor razonable, no hace falta precisión de laboratorio.
        """.trimIndent()
    }
}

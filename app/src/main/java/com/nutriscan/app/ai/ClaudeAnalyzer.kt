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
import com.nutriscan.app.data.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.Base64

/** Envía la foto a Claude (Anthropic) y devuelve una estimación de calorías y macros por ingrediente. */
class ClaudeAnalyzer(private val baseUrl: String? = null) {

    suspend fun analyze(
        apiKey: String,
        model: AiModel,
        jpeg: ByteArray,
        userHint: String?,
    ): FoodAnalysis = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AnalysisException("Añade tu API key de Anthropic en Ajustes para poder escanear.")
        require(model.provider == AiProvider.CLAUDE)

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

            val instruction = FoodPrompt.instruction(userHint)

            val builder = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(16000L)
                .system(FoodPrompt.SYSTEM)
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
            FoodPrompt.parse(text)
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

    private fun schema(): BetaJsonOutputFormat.Schema {
        val schema = FoodPrompt.jsonSchema()
        return BetaJsonOutputFormat.Schema.builder()
            .apply { schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }
            .build()
    }

    private companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}

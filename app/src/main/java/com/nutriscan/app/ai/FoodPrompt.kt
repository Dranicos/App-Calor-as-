package com.nutriscan.app.ai

import com.nutriscan.app.domain.Ingredient
import org.json.JSONObject

data class FoodAnalysis(
    val isFood: Boolean,
    val dishName: String,
    val confidence: String,
    val notes: String,
    val ingredients: List<Ingredient>,
)

class AnalysisException(message: String) : Exception(message)

/** Prompt, esquema de respuesta e interpretación comunes a Claude y Gemini. */
object FoodPrompt {
    val SYSTEM = """
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

    fun instruction(userHint: String?): String = buildString {
        append("Analiza esta foto y estima las calorías y macronutrientes.")
        if (!userHint.isNullOrBlank()) {
            append("\n\nIndicaciones del usuario (tenlas en cuenta y corrige la estimación): ")
            append(userHint.trim())
        }
    }

    private val ingredientFields = listOf("name", "grams", "kcal", "protein_g", "carbs_g", "fat_g")
    private val topFields = listOf("is_food", "dish_name", "confidence", "notes", "ingredients")

    /** Esquema JSON Schema estándar (Claude). */
    fun jsonSchema(): Map<String, Any> {
        val number = mapOf("type" to "number")
        val ingredient = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to ingredientFields,
            "properties" to mapOf(
                "name" to mapOf("type" to "string", "description" to "Nombre del ingrediente en español"),
                "grams" to number,
                "kcal" to number,
                "protein_g" to number,
                "carbs_g" to number,
                "fat_g" to number,
            ),
        )
        return mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to topFields,
            "properties" to mapOf(
                "is_food" to mapOf("type" to "boolean"),
                "dish_name" to mapOf("type" to "string", "description" to "Nombre corto del plato o producto"),
                "confidence" to mapOf("type" to "string", "enum" to listOf("alta", "media", "baja")),
                "notes" to mapOf("type" to "string", "description" to "Comentario breve sobre la estimación (máx. 2 frases)"),
                "ingredients" to mapOf("type" to "array", "items" to ingredient),
            ),
        )
    }

    /** El mismo esquema en el formato `responseSchema` de Gemini (subconjunto de OpenAPI). */
    fun geminiSchema(): JSONObject {
        fun type(t: String, description: String? = null) = JSONObject().put("type", t).apply {
            if (description != null) put("description", description)
        }
        val ingredient = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("name", type("STRING", "Nombre del ingrediente en español"))
                    .put("grams", type("NUMBER"))
                    .put("kcal", type("NUMBER"))
                    .put("protein_g", type("NUMBER"))
                    .put("carbs_g", type("NUMBER"))
                    .put("fat_g", type("NUMBER"))
            )
            .put("required", org.json.JSONArray(ingredientFields))
            .put("propertyOrdering", org.json.JSONArray(ingredientFields))
        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("is_food", type("BOOLEAN"))
                    .put("dish_name", type("STRING", "Nombre corto del plato o producto"))
                    .put("confidence", type("STRING").put("enum", org.json.JSONArray(listOf("alta", "media", "baja"))))
                    .put("notes", type("STRING", "Comentario breve sobre la estimación (máx. 2 frases)"))
                    .put("ingredients", JSONObject().put("type", "ARRAY").put("items", ingredient))
            )
            .put("required", org.json.JSONArray(topFields))
            .put("propertyOrdering", org.json.JSONArray(topFields))
    }

    /** Convierte el JSON devuelto por la IA en un [FoodAnalysis]. */
    fun parse(text: String): FoodAnalysis {
        val clean = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = try {
            JSONObject(clean)
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
}

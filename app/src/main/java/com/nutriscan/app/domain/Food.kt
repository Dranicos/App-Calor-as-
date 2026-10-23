package com.nutriscan.app.domain

import org.json.JSONArray
import org.json.JSONObject

/** Un ingrediente con sus valores para la cantidad indicada en [grams]. */
data class Ingredient(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
) {
    /** Cambia los gramos y escala calorías y macros en proporción. */
    fun withGrams(newGrams: Double): Ingredient {
        if (grams <= 0.0) return copy(grams = newGrams)
        val f = newGrams / grams
        return copy(grams = newGrams, kcal = kcal * f, protein = protein * f, carbs = carbs * f, fat = fat * f)
    }
}

data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
}

fun List<Ingredient>.totals(): Macros =
    fold(Macros()) { acc, i -> acc + Macros(i.kcal, i.protein, i.carbs, i.fat) }

enum class MealType(val label: String) {
    DESAYUNO("Desayuno"),
    COMIDA("Comida"),
    CENA("Cena"),
    SNACK("Snack");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: SNACK

        /** Sugiere el tipo de comida según la hora del día. */
        fun forHour(hour: Int) = when (hour) {
            in 5..11 -> DESAYUNO
            in 12..16 -> COMIDA
            in 20..23 -> CENA
            else -> SNACK
        }
    }
}

object IngredientJson {
    fun encode(list: List<Ingredient>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("name", it.name)
                    .put("grams", it.grams)
                    .put("kcal", it.kcal)
                    .put("protein", it.protein)
                    .put("carbs", it.carbs)
                    .put("fat", it.fat)
            )
        }
    }.toString()

    fun decode(json: String?): List<Ingredient> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { idx ->
            val o = arr.getJSONObject(idx)
            Ingredient(
                name = o.optString("name"),
                grams = o.optDouble("grams", 0.0),
                kcal = o.optDouble("kcal", 0.0),
                protein = o.optDouble("protein", 0.0),
                carbs = o.optDouble("carbs", 0.0),
                fat = o.optDouble("fat", 0.0),
            )
        }
    }
}

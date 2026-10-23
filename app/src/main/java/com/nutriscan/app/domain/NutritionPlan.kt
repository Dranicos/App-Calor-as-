package com.nutriscan.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

enum class Sex(val label: String) { HOMBRE("Hombre"), MUJER("Mujer") }

enum class Activity(val label: String, val description: String, val factor: Double) {
    SEDENTARIO("Sedentario", "Poco o ningún ejercicio", 1.2),
    LIGERO("Ligero", "Ejercicio 1–3 días/semana", 1.375),
    MODERADO("Moderado", "Ejercicio 3–5 días/semana", 1.55),
    ACTIVO("Activo", "Ejercicio 6–7 días/semana", 1.725),
    MUY_ACTIVO("Muy activo", "Trabajo físico o 2 sesiones/día", 1.9),
}

enum class Goal(val label: String, val kcalFactor: Double, val proteinPerKg: Double) {
    PERDER("Perder grasa", 0.80, 2.0),
    MANTENER("Mantener peso", 1.0, 1.6),
    GANAR("Ganar músculo", 1.10, 1.8),
}

data class Profile(
    val sex: Sex,
    val age: Int,
    val weightKg: Double,
    val heightCm: Double,
    val activity: Activity,
    val goal: Goal,
)

data class Plan(
    val bmr: Int,
    val tdee: Int,
    val targetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val bmi: Double,
    val bmiLabel: String,
    val waterLiters: Double,
)

/**
 * Plan diario basado en la ecuación de Mifflin-St Jeor.
 * - Gasto total = metabolismo basal × factor de actividad.
 * - Objetivo: −20 % para perder grasa, +10 % para ganar músculo.
 * - Proteína según objetivo (g/kg), grasa 27 % de las kcal, el resto carbohidratos.
 */
fun Profile.plan(): Plan {
    val base = 10 * weightKg + 6.25 * heightCm - 5 * age
    val bmr = if (sex == Sex.HOMBRE) base + 5 else base - 161
    val tdee = bmr * activity.factor
    val minKcal = if (sex == Sex.HOMBRE) 1500.0 else 1200.0
    val target = max(tdee * goal.kcalFactor, minKcal)

    val protein = weightKg * goal.proteinPerKg
    val fat = target * 0.27 / 9
    val carbs = max(0.0, (target - protein * 4 - fat * 9) / 4)

    val heightM = heightCm / 100
    val bmi = if (heightM > 0) weightKg / (heightM * heightM) else 0.0
    val bmiLabel = when {
        bmi < 18.5 -> "Bajo peso"
        bmi < 25 -> "Peso normal"
        bmi < 30 -> "Sobrepeso"
        else -> "Obesidad"
    }

    return Plan(
        bmr = bmr.roundToInt(),
        tdee = tdee.roundToInt(),
        targetKcal = (target / 10).roundToInt() * 10,
        proteinG = protein.roundToInt(),
        carbsG = carbs.roundToInt(),
        fatG = fat.roundToInt(),
        bmi = bmi,
        bmiLabel = bmiLabel,
        waterLiters = (weightKg * 0.035 * 10).roundToInt() / 10.0,
    )
}

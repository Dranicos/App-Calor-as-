package com.nutriscan.app

import com.nutriscan.app.domain.Activity
import com.nutriscan.app.domain.Goal
import com.nutriscan.app.domain.Ingredient
import com.nutriscan.app.domain.IngredientJson
import com.nutriscan.app.domain.Profile
import com.nutriscan.app.domain.Sex
import com.nutriscan.app.domain.plan
import com.nutriscan.app.domain.totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPlanTest {
    @Test
    fun maleMaintenancePlan() {
        // 80 kg, 180 cm, 30 años: BMR = 800 + 1125 - 150 + 5 = 1780; ×1.55 = 2759
        val plan = Profile(Sex.HOMBRE, 30, 80.0, 180.0, Activity.MODERADO, Goal.MANTENER).plan()
        assertEquals(1780, plan.bmr)
        assertEquals(2759, plan.tdee)
        assertEquals(2760, plan.targetKcal)
        assertEquals(128, plan.proteinG) // 1.6 g/kg
        // Los macros deben sumar aprox. el objetivo calórico.
        val kcalFromMacros = plan.proteinG * 4 + plan.carbsG * 4 + plan.fatG * 9
        assertTrue(kotlin.math.abs(kcalFromMacros - plan.targetKcal) < 20)
        assertEquals("Peso normal", plan.bmiLabel)
    }

    @Test
    fun femaleLossPlanHasDeficitButFloor() {
        val plan = Profile(Sex.MUJER, 40, 50.0, 155.0, Activity.SEDENTARIO, Goal.PERDER).plan()
        assertTrue(plan.targetKcal < plan.tdee)
        assertTrue(plan.targetKcal >= 1200)
    }

    @Test
    fun ingredientScalesWithGrams() {
        val rice = Ingredient("Arroz", 100.0, 130.0, 2.7, 28.0, 0.3)
        val double = rice.withGrams(200.0)
        assertEquals(260.0, double.kcal, 0.001)
        assertEquals(56.0, double.carbs, 0.001)
    }

    @Test
    fun ingredientJsonRoundTrip() {
        val list = listOf(Ingredient("Pollo", 150.0, 248.0, 46.5, 0.0, 5.4), Ingredient("Aceite", 10.0, 90.0, 0.0, 0.0, 10.0))
        val back = IngredientJson.decode(IngredientJson.encode(list))
        assertEquals(list, back)
        assertEquals(338.0, back.totals().kcal, 0.001)
    }
}

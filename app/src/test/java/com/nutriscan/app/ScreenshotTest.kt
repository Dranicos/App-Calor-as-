package com.nutriscan.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.nutriscan.app.data.MealEntity
import com.nutriscan.app.domain.Activity
import com.nutriscan.app.domain.Goal
import com.nutriscan.app.domain.Ingredient
import com.nutriscan.app.domain.IngredientJson
import com.nutriscan.app.domain.MealType
import com.nutriscan.app.domain.Profile
import com.nutriscan.app.domain.Sex
import com.nutriscan.app.domain.totals
import com.nutriscan.app.ui.MainViewModel
import com.nutriscan.app.ui.ReviewViewModel
import com.nutriscan.app.ui.screens.HomeScreen
import com.nutriscan.app.ui.screens.PlanScreen
import com.nutriscan.app.ui.screens.ReviewScreen
import com.nutriscan.app.ui.screens.SettingsScreen
import com.nutriscan.app.ui.theme.NutriScanTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renderiza las pantallas principales con datos de ejemplo y guarda capturas PNG. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<NutriApp>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun seed(): Long = runBlocking {
        app.settings.setProfile(Profile(Sex.HOMBRE, 30, 78.0, 178.0, Activity.MODERADO, Goal.PERDER))
        app.settings.setApiKey("sk-ant-demo")
        val dao = app.database.meals()
        val today = LocalDate.now().toString()
        fun meal(type: MealType, name: String, ings: List<Ingredient>): MealEntity {
            val t = ings.totals()
            return MealEntity(0, System.currentTimeMillis(), today, type.name, name, null,
                IngredientJson.encode(ings), t.kcal, t.protein, t.carbs, t.fat, null)
        }
        dao.upsert(meal(MealType.DESAYUNO, "Tostadas con aguacate y huevo", listOf(
            Ingredient("Pan integral", 60.0, 148.0, 7.8, 24.6, 2.0),
            Ingredient("Aguacate", 50.0, 80.0, 1.0, 4.3, 7.3),
            Ingredient("Huevo", 55.0, 79.0, 6.9, 0.4, 5.5),
        )))
        dao.upsert(meal(MealType.COMIDA, "Pollo con arroz y verduras", listOf(
            Ingredient("Pechuga de pollo", 150.0, 248.0, 46.5, 0.0, 5.4),
            Ingredient("Arroz blanco cocido", 180.0, 234.0, 4.9, 50.4, 0.5),
            Ingredient("Brócoli", 80.0, 27.0, 2.3, 5.3, 0.3),
            Ingredient("Aceite de oliva", 10.0, 88.0, 0.0, 0.0, 10.0),
        )))
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home() {
        seed()
        val vm = MainViewModel(app)
        compose.setContent {
            NutriScanTheme { Surface { HomeScreen(vm, PaddingValues(), {}, {}, {}) } }
        }
        Thread.sleep(500)
        save("1_hoy")
    }

    @Test
    fun review() {
        seed()
        val vm = ReviewViewModel(app, SavedStateHandle(mapOf("entryId" to 2L)))
        compose.setContent { NutriScanTheme { ReviewScreen(onDone = {}, vm = vm) } }
        Thread.sleep(500)
        save("2_revisar")
    }

    @Test
    fun plan() {
        seed()
        val vm = MainViewModel(app)
        compose.setContent { NutriScanTheme { Surface { PlanScreen(vm, PaddingValues()) } } }
        Thread.sleep(500)
        save("3_plan")
    }

    @Test
    fun settings() {
        seed()
        val vm = MainViewModel(app)
        compose.setContent { NutriScanTheme { Surface { SettingsScreen(vm, PaddingValues()) } } }
        Thread.sleep(500)
        save("4_ajustes")
    }
}

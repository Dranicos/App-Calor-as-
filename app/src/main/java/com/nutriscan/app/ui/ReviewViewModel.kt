package com.nutriscan.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nutriscan.app.NutriApp
import com.nutriscan.app.ai.AnalysisException
import com.nutriscan.app.data.MealEntity
import com.nutriscan.app.domain.Ingredient
import com.nutriscan.app.domain.IngredientJson
import com.nutriscan.app.domain.MealType
import com.nutriscan.app.domain.totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

sealed interface AnalysisStatus {
    data object Idle : AnalysisStatus
    data object Analyzing : AnalysisStatus
    data class Error(val message: String) : AnalysisStatus
}

/** Pantalla de revisión: analiza la foto y permite corregir ingredientes antes de guardar. */
class ReviewViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val nutri = app as NutriApp
    private val dao = nutri.database.meals()

    private val entryId: Long = handle.get<Long>("entryId") ?: -1L
    /** Foto recién hecha (temporal) o miniatura de una comida ya guardada. */
    private var photoFile: File? = handle.get<String>("photo")?.takeIf { it.isNotBlank() }?.let(::File)
    private var existing: MealEntity? = null
    private var analysisJob: Job? = null

    val isEditing get() = entryId >= 0
    val hasPhoto get() = photoFile != null

    var image by mutableStateOf<ImageBitmap?>(null); private set
    var status by mutableStateOf<AnalysisStatus>(AnalysisStatus.Idle); private set
    var name by mutableStateOf("")
    var mealType by mutableStateOf(MealType.forHour(LocalTime.now().hour))
    var notes by mutableStateOf("")
    var confidence by mutableStateOf("")
    var hint by mutableStateOf("")
    val ingredients = mutableStateListOf<Ingredient>()
    var saved by mutableStateOf(false); private set

    val totals get() = ingredients.toList().totals()

    init {
        viewModelScope.launch {
            if (isEditing) {
                val meal = dao.get(entryId) ?: return@launch
                existing = meal
                name = meal.name
                mealType = meal.type
                notes = meal.notes.orEmpty()
                ingredients.addAll(meal.ingredients)
                photoFile = meal.imagePath?.let(::File)?.takeIf { it.exists() }
                loadImage()
            } else if (photoFile != null) {
                loadImage()
                analyze()
            }
        }
    }

    private suspend fun loadImage() {
        val file = photoFile ?: return
        image = withContext(Dispatchers.IO) { nutri.photos.loadBitmap(file.absolutePath)?.asImageBitmap() }
    }

    /** Analiza la foto con Claude. Usa la pista del usuario si la hay. */
    fun analyze() {
        val file = photoFile ?: return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            status = AnalysisStatus.Analyzing
            try {
                val settings = nutri.settings.settings.first()
                val jpeg = withContext(Dispatchers.IO) { nutri.photos.compressedJpeg(file, maxSide = 1568) }
                val result = nutri.analyzer.analyze(settings.apiKey, settings.model, jpeg, hint)
                if (!result.isFood) {
                    status = AnalysisStatus.Error(
                        "No parece que haya comida en la foto. Puedes añadir los ingredientes a mano o probar otra foto."
                    )
                    return@launch
                }
                name = result.dishName
                notes = result.notes
                confidence = result.confidence
                ingredients.clear()
                ingredients.addAll(result.ingredients)
                hint = ""
                status = AnalysisStatus.Idle
            } catch (e: AnalysisException) {
                status = AnalysisStatus.Error(e.message ?: "Error desconocido")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                status = AnalysisStatus.Error("No se pudo analizar la foto: ${e.message}")
            }
        }
    }

    fun dismissError() {
        status = AnalysisStatus.Idle
    }

    fun updateIngredient(index: Int, ingredient: Ingredient) {
        if (index in ingredients.indices) ingredients[index] = ingredient else ingredients.add(ingredient)
    }

    fun removeIngredient(index: Int) {
        if (index in ingredients.indices) ingredients.removeAt(index)
    }

    /** Multiplica todas las cantidades (p. ej. si te comiste media ración o el doble). */
    fun scaleAll(factor: Double) {
        val scaled = ingredients.map { it.withGrams(it.grams * factor) }
        ingredients.clear()
        ingredients.addAll(scaled)
    }

    fun save() {
        if (ingredients.isEmpty()) return
        viewModelScope.launch {
            val t = totals
            val old = existing
            val imagePath = withContext(Dispatchers.IO) {
                when {
                    old != null -> old.imagePath
                    photoFile != null -> nutri.photos.saveThumbnail(photoFile!!).also { photoFile!!.delete() }
                    else -> null
                }
            }
            dao.upsert(
                MealEntity(
                    id = old?.id ?: 0,
                    timestamp = old?.timestamp ?: System.currentTimeMillis(),
                    day = old?.day ?: LocalDate.now().toString(),
                    mealType = mealType.name,
                    name = name.ifBlank { ingredients.first().name },
                    imagePath = imagePath,
                    ingredientsJson = IngredientJson.encode(ingredients.toList()),
                    kcal = t.kcal,
                    protein = t.protein,
                    carbs = t.carbs,
                    fat = t.fat,
                    notes = notes.ifBlank { null },
                )
            )
            saved = true
        }
    }

    override fun onCleared() {
        // Si no se guardó, borra la foto temporal.
        if (!saved && !isEditing) photoFile?.delete()
    }
}

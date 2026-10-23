package com.nutriscan.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.app.NutriApp
import com.nutriscan.app.data.AiModel
import com.nutriscan.app.data.AppSettings
import com.nutriscan.app.data.MealEntity
import com.nutriscan.app.domain.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val nutri = app as NutriApp
    private val dao = nutri.database.meals()
    val photos = nutri.photos

    /** null mientras se cargan los ajustes por primera vez. */
    val settings: StateFlow<AppSettings?> =
        nutri.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val today = MutableStateFlow(LocalDate.now().toString())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayMeals: StateFlow<List<MealEntity>> = today
        .flatMapLatest { dao.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allMeals: StateFlow<List<MealEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Por si la app queda abierta pasada la medianoche. */
    fun refreshDay() {
        today.value = LocalDate.now().toString()
    }

    fun saveProfile(profile: Profile) = viewModelScope.launch { nutri.settings.setProfile(profile) }

    fun saveApiKey(key: String) = viewModelScope.launch { nutri.settings.setApiKey(key) }

    fun saveModel(model: AiModel) = viewModelScope.launch { nutri.settings.setModel(model) }

    fun deleteMeal(meal: MealEntity) = viewModelScope.launch {
        dao.delete(meal.id)
        photos.delete(meal.imagePath)
    }

    fun deleteAllData() = viewModelScope.launch {
        dao.deleteAll()
        nutri.settings.clearAll()
        photos.cleanUp(keep = emptySet())
    }

    init {
        // Borra fotos temporales que quedaron sin guardar.
        viewModelScope.launch {
            photos.cleanUp(keep = dao.allImagePaths().toSet())
        }
    }
}

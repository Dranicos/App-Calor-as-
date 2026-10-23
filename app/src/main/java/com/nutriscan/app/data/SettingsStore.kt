package com.nutriscan.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nutriscan.app.domain.Activity
import com.nutriscan.app.domain.Goal
import com.nutriscan.app.domain.Profile
import com.nutriscan.app.domain.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Modelos de Claude que se pueden elegir en Ajustes. */
enum class AiModel(val id: String, val label: String, val description: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "El más preciso (recomendado)"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Equilibrado entre precisión y coste"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "El más rápido y barato");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

data class AppSettings(
    val apiKey: String = "",
    val model: AiModel = AiModel.OPUS,
    val profile: Profile? = null,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val sex = stringPreferencesKey("sex")
        val age = intPreferencesKey("age")
        val weight = doublePreferencesKey("weight")
        val height = doublePreferencesKey("height")
        val activity = stringPreferencesKey("activity")
        val goal = stringPreferencesKey("goal")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[Keys.apiKey].orEmpty(),
            model = AiModel.fromId(p[Keys.model]),
            profile = p.toProfile(),
        )
    }

    private fun Preferences.toProfile(): Profile? {
        val age = this[Keys.age] ?: return null
        val weight = this[Keys.weight] ?: return null
        val height = this[Keys.height] ?: return null
        return Profile(
            sex = Sex.entries.firstOrNull { it.name == this[Keys.sex] } ?: Sex.HOMBRE,
            age = age,
            weightKg = weight,
            heightCm = height,
            activity = Activity.entries.firstOrNull { it.name == this[Keys.activity] } ?: Activity.LIGERO,
            goal = Goal.entries.firstOrNull { it.name == this[Keys.goal] } ?: Goal.MANTENER,
        )
    }

    suspend fun setApiKey(key: String) = context.dataStore.edit { it[Keys.apiKey] = key.trim() }

    suspend fun setModel(model: AiModel) = context.dataStore.edit { it[Keys.model] = model.id }

    suspend fun setProfile(profile: Profile) = context.dataStore.edit {
        it[Keys.sex] = profile.sex.name
        it[Keys.age] = profile.age
        it[Keys.weight] = profile.weightKg
        it[Keys.height] = profile.heightCm
        it[Keys.activity] = profile.activity.name
        it[Keys.goal] = profile.goal.name
    }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}

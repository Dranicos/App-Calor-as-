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

/** Proveedores de IA que se pueden usar para analizar las fotos. */
enum class AiProvider(
    val label: String,
    val company: String,
    val keyUrl: String,
    val keyHint: String,
) {
    CLAUDE("Claude", "Anthropic", "https://console.anthropic.com/settings/keys", "sk-ant-…"),
    GEMINI("Gemini", "Google", "https://aistudio.google.com/apikey", "AIza…"),
}

/** Modelos que se pueden elegir en Ajustes. */
enum class AiModel(val provider: AiProvider, val id: String, val label: String, val description: String) {
    OPUS(AiProvider.CLAUDE, "claude-opus-5-5", "Claude Opus 5.5", "El más preciso (recomendado)"),
    SONNET(AiProvider.CLAUDE, "claude-sonnet-5-5", "Claude Sonnet 5.5", "Equilibrado entre precisión y coste"),
    HAIKU(AiProvider.CLAUDE, "claude-haiku-5-5", "Claude Haiku 5.5", "El más rápido y barato"),
    GEMINI_FLASH(AiProvider.GEMINI, "gemini-3.8-flash", "Gemini 3.8 Flash", "Rápido y preciso, con nivel gratuito (recomendado)"),
    GEMINI_ARGON(AiProvider.GEMINI, "gemini-4-argon", "Gemini 4 Argon", "El más potente de Google (solo de pago)"),
    GEMINI_LITE(AiProvider.GEMINI, "gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "El más barato, con nivel gratuito");

    companion object {
        fun forProvider(provider: AiProvider) = entries.filter { it.provider == provider }
        fun default(provider: AiProvider) = forProvider(provider).first()
        fun fromId(id: String?, provider: AiProvider) =
            entries.firstOrNull { it.id == id && it.provider == provider } ?: default(provider)
    }
}

data class AppSettings(
    /** API key de Anthropic (Claude). */
    val apiKey: String = "",
    /** API key de Google AI Studio (Gemini). */
    val geminiKey: String = "",
    val provider: AiProvider = AiProvider.CLAUDE,
    val claudeModel: AiModel = AiModel.OPUS,
    val geminiModel: AiModel = AiModel.GEMINI_FLASH,
    val profile: Profile? = null,
) {
    /** Modelo del proveedor activo. */
    val model: AiModel get() = if (provider == AiProvider.GEMINI) geminiModel else claudeModel

    fun keyFor(p: AiProvider) = if (p == AiProvider.GEMINI) geminiKey else apiKey

    /** ¿Hay API key para el proveedor activo? */
    val hasActiveKey: Boolean get() = keyFor(provider).isNotBlank()
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val geminiKey = stringPreferencesKey("gemini_key")
        val geminiModel = stringPreferencesKey("gemini_model")
        val provider = stringPreferencesKey("provider")
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
            geminiKey = p[Keys.geminiKey].orEmpty(),
            provider = AiProvider.entries.firstOrNull { it.name == p[Keys.provider] } ?: AiProvider.CLAUDE,
            claudeModel = AiModel.fromId(p[Keys.model], AiProvider.CLAUDE),
            geminiModel = AiModel.fromId(p[Keys.geminiModel], AiProvider.GEMINI),
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

    suspend fun setApiKey(provider: AiProvider, key: String) = context.dataStore.edit {
        it[if (provider == AiProvider.GEMINI) Keys.geminiKey else Keys.apiKey] = key.trim()
    }

    suspend fun setProvider(provider: AiProvider) = context.dataStore.edit { it[Keys.provider] = provider.name }

    suspend fun setModel(model: AiModel) = context.dataStore.edit {
        it[if (model.provider == AiProvider.GEMINI) Keys.geminiModel else Keys.model] = model.id
    }

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

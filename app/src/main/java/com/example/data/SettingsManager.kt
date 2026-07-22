package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled_v2")
        val USER_PIN = stringPreferencesKey("user_pin_v2")
        val THEME_MODE = stringPreferencesKey("theme_mode_v2") // "system", "light", "dark"
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed_v2")
        val USER_SIGNED_IN = booleanPreferencesKey("user_signed_in_v2")
        val USE_LOCAL_LLM = booleanPreferencesKey("use_local_llm_v2")
        val PREFER_CLOUD_LLM = booleanPreferencesKey("prefer_cloud_llm_v2")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key_v2")
    }

    val pinEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PIN_ENABLED] ?: false
    }

    val useLocalLlmFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_LOCAL_LLM] ?: false
    }

    // When true (and a Groq API key is set), analysis uses the Groq cloud model even on
    // high-RAM phones that would otherwise run the on-device model.
    val preferCloudLlmFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PREFER_CLOUD_LLM] ?: false
    }

    val groqApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GROQ_API_KEY] ?: ""
    }

    val userPinFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_PIN] ?: ""
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    val setupCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SETUP_COMPLETED] ?: false
    }

    val userSignedInFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USER_SIGNED_IN] ?: false
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PIN_ENABLED] = enabled
        }
    }

    suspend fun setUserPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_PIN] = pin
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SETUP_COMPLETED] = completed
        }
    }

    suspend fun setUserSignedIn(signedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USER_SIGNED_IN] = signedIn
        }
    }

    suspend fun setUseLocalLlm(useLocal: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_LOCAL_LLM] = useLocal
        }
    }

    suspend fun setPreferCloudLlm(preferCloud: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PREFER_CLOUD_LLM] = preferCloud
        }
    }

    suspend fun setGroqApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[GROQ_API_KEY] = apiKey
        }
    }
}

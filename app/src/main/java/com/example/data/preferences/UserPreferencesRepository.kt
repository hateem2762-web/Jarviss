package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_preferences"
)

data class JarvisPreferences(
    val userContext: String = "",
    val darkTheme: Boolean = true,
    val autoExecuteActions: Boolean = true,
    val speakReplies: Boolean = false,
    val persistentTriggerEnabled: Boolean = false,
    val floatingBubbleEnabled: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val manualProfilePackageName: String = "",
    val manualProfileName: String = "",
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_CONTEXT = stringPreferencesKey("user_context")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_EXECUTE_ACTIONS = booleanPreferencesKey("auto_execute_actions")
        val SPEAK_REPLIES = booleanPreferencesKey("speak_replies")
        val PERSISTENT_TRIGGER = booleanPreferencesKey("persistent_trigger")
        val FLOATING_BUBBLE = booleanPreferencesKey("floating_bubble")
        val WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        val MANUAL_PROFILE_PKG = stringPreferencesKey("manual_profile_pkg")
        val MANUAL_PROFILE_NAME = stringPreferencesKey("manual_profile_name")
    }

    val preferences: Flow<JarvisPreferences> = context.jarvisDataStore.data.map { prefs ->
        JarvisPreferences(
            userContext = prefs[Keys.USER_CONTEXT] ?: "",
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            autoExecuteActions = prefs[Keys.AUTO_EXECUTE_ACTIONS] ?: true,
            speakReplies = prefs[Keys.SPEAK_REPLIES] ?: false,
            persistentTriggerEnabled = prefs[Keys.PERSISTENT_TRIGGER] ?: false,
            floatingBubbleEnabled = prefs[Keys.FLOATING_BUBBLE] ?: false,
            wakeWordEnabled = prefs[Keys.WAKE_WORD] ?: false,
            manualProfilePackageName = prefs[Keys.MANUAL_PROFILE_PKG] ?: "",
            manualProfileName = prefs[Keys.MANUAL_PROFILE_NAME] ?: "",
        )
    }

    suspend fun updateUserContext(value: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.USER_CONTEXT] = value.trim()
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    suspend fun setAutoExecuteActions(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.AUTO_EXECUTE_ACTIONS] = enabled
        }
    }

    suspend fun setSpeakReplies(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.SPEAK_REPLIES] = enabled
        }
    }

    suspend fun setPersistentTriggerEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PERSISTENT_TRIGGER] = enabled
        }
    }

    suspend fun setFloatingBubbleEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.FLOATING_BUBBLE] = enabled
        }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD] = enabled
        }
    }

    suspend fun disableAllOverlayModes() {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.FLOATING_BUBBLE] = false
            prefs[Keys.PERSISTENT_TRIGGER] = false
            prefs[Keys.WAKE_WORD] = false
        }
    }

    suspend fun setManualProfile(packageName: String, displayName: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MANUAL_PROFILE_PKG] = packageName.trim()
            prefs[Keys.MANUAL_PROFILE_NAME] = displayName.trim()
        }
    }
}

package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AirGestureHorizontalAction
import com.example.model.AirGestureScope
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
    val proactiveAlertsEnabled: Boolean = true,
    val importanceThreshold: Int = 7,
    val manualProfilePackageName: String = "",
    val manualProfileName: String = "",
    val newsAirGestureEnabled: Boolean = false,
    val maleVoiceEnabled: Boolean = true,
    val selectedTtsVoiceName: String = "",
    val webAgentConfirmBeforeDownload: Boolean = true,
    val globalAirGestureEnabled: Boolean = false,
    val globalAirGestureScope: AirGestureScope = AirGestureScope.ALL_APPS,
    val globalAirGestureHorizontalAction: AirGestureHorizontalAction = AirGestureHorizontalAction.SWIPE,
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
        val PROACTIVE_ALERTS = booleanPreferencesKey("proactive_alerts_enabled")
        val IMPORTANCE_THRESHOLD = intPreferencesKey("importance_threshold")
        val MANUAL_PROFILE_PKG = stringPreferencesKey("manual_profile_pkg")
        val MANUAL_PROFILE_NAME = stringPreferencesKey("manual_profile_name")
        val NEWS_AIR_GESTURE = booleanPreferencesKey("news_air_gesture_enabled")
        val MALE_VOICE_ENABLED = booleanPreferencesKey("male_voice_enabled")
        val SELECTED_TTS_VOICE = stringPreferencesKey("selected_tts_voice_name")
        val WEB_AGENT_CONFIRM_DOWNLOAD = booleanPreferencesKey("web_agent_confirm_download")
        val GLOBAL_AIR_GESTURE_ENABLED = booleanPreferencesKey("global_air_gesture_enabled")
        val GLOBAL_AIR_GESTURE_SCOPE = stringPreferencesKey("global_air_gesture_scope")
        val GLOBAL_AIR_GESTURE_HORIZONTAL = stringPreferencesKey("global_air_gesture_horizontal")
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
            proactiveAlertsEnabled = prefs[Keys.PROACTIVE_ALERTS] ?: true,
            importanceThreshold = (prefs[Keys.IMPORTANCE_THRESHOLD] ?: 7).coerceIn(1, 10),
            manualProfilePackageName = prefs[Keys.MANUAL_PROFILE_PKG] ?: "",
            manualProfileName = prefs[Keys.MANUAL_PROFILE_NAME] ?: "",
            newsAirGestureEnabled = prefs[Keys.NEWS_AIR_GESTURE] ?: false,
            maleVoiceEnabled = prefs[Keys.MALE_VOICE_ENABLED] ?: true,
            selectedTtsVoiceName = prefs[Keys.SELECTED_TTS_VOICE] ?: "",
            webAgentConfirmBeforeDownload = prefs[Keys.WEB_AGENT_CONFIRM_DOWNLOAD] ?: true,
            globalAirGestureEnabled = prefs[Keys.GLOBAL_AIR_GESTURE_ENABLED] ?: false,
            globalAirGestureScope = AirGestureScope.fromWire(prefs[Keys.GLOBAL_AIR_GESTURE_SCOPE]),
            globalAirGestureHorizontalAction = AirGestureHorizontalAction.fromWire(
                prefs[Keys.GLOBAL_AIR_GESTURE_HORIZONTAL]
            ),
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

    suspend fun setProactiveAlertsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PROACTIVE_ALERTS] = enabled
        }
    }

    suspend fun setImportanceThreshold(threshold: Int) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.IMPORTANCE_THRESHOLD] = threshold.coerceIn(1, 10)
        }
    }

    suspend fun setManualProfile(packageName: String, displayName: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MANUAL_PROFILE_PKG] = packageName.trim()
            prefs[Keys.MANUAL_PROFILE_NAME] = displayName.trim()
        }
    }

    suspend fun setNewsAirGestureEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.NEWS_AIR_GESTURE] = enabled
        }
    }

    suspend fun setMaleVoiceEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MALE_VOICE_ENABLED] = enabled
        }
    }

    suspend fun setSelectedTtsVoiceName(voiceName: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.SELECTED_TTS_VOICE] = voiceName
        }
    }

    suspend fun setWebAgentConfirmBeforeDownload(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.WEB_AGENT_CONFIRM_DOWNLOAD] = enabled
        }
    }

    suspend fun setGlobalAirGestureEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_AIR_GESTURE_ENABLED] = enabled
        }
    }

    suspend fun setGlobalAirGestureScope(scope: AirGestureScope) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_AIR_GESTURE_SCOPE] = scope.wireValue
        }
    }

    suspend fun setGlobalAirGestureHorizontalAction(action: AirGestureHorizontalAction) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_AIR_GESTURE_HORIZONTAL] = action.wireValue
        }
    }
}

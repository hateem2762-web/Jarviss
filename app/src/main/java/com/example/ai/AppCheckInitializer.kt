package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var appCheckConfigured = false

@Volatile
private var appCheckConfiguredWithIntentToken = false

fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            true
        } else {
            FirebaseApp.initializeApp(appContext) != null
        }
    } catch (e: Exception) {
        Log.e(TAG, "FirebaseApp.initializeApp failed", e)
        false
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        if (!ensureFirebaseInitialized(appContext)) {
            Log.e(
                TAG,
                "Default FirebaseApp could not be initialized. Ensure app/google-services.json is included in the build."
            )
            return
        }

        val debugToken = if (BuildConfig.DEBUG) intent?.getStringExtra(DEBUG_TOKEN_EXTRA) else null
        if (appCheckConfigured && (debugToken.isNullOrEmpty() || appCheckConfiguredWithIntentToken)) {
            return
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = appContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )

        if (BuildConfig.DEBUG) {
            val savedToken = prefs.getString(DEBUG_SECRET_KEY, null)

            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() ensures the secret is written before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckConfigured = true
                appCheckConfiguredWithIntentToken = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed with intent token.")
                return
            }

            if (!savedToken.isNullOrEmpty()) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckConfigured = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed with persisted token.")
                return
            }

            // Standalone debug APK on a physical device outside AI Studio:
            // Try reCAPTCHA Enterprise first if site key is bundled, otherwise fall back to DebugAppCheckProviderFactory.
            if (installRecaptchaIfAvailable(appContext)) {
                appCheckConfigured = true
                return
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            appCheckConfigured = true
            Log.w(TAG, "DebugAppCheckProviderFactory installed without registered debug token.")
        } else {
            if (installRecaptchaIfAvailable(appContext)) {
                appCheckConfigured = true
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaIfAvailable(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    return try {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
        )
        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        true
    } catch (e: Exception) {
        Log.e(TAG, "RecaptchaAppCheckProviderFactory init failed", e)
        false
    }
}

package dev.jara.notebooklm.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * Spravce autentizace pro NotebookLM.
 *
 * Flow:
 * 1. LoginActivity (WebView) provede Google login a vrati cookies
 * 2. saveCookies() ulozi cookies do EncryptedSharedPreferences
 * 3. fetchTokens() stahne CSRF a session tokeny z NotebookLM homepage
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "notebooklm_auth"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_CSRF = "csrf_token"
        private const val KEY_SESSION_ID = "session_id"
        const val NOTEBOOKLM_URL = "https://notebooklm.google.com/"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    data class AuthTokens(
        val cookies: String,
        val csrfToken: String,
        val sessionId: String,
    )

    /** Ulozi cookies ziskane z LoginActivity */
    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
    }

    /** Stahne tokeny z NotebookLM homepage */
    suspend fun fetchTokens(httpClient: HttpClient): AuthTokens? {
        val cookies = prefs.getString(KEY_COOKIES, null) ?: return null

        val response = httpClient.get(NOTEBOOKLM_URL) {
            header("Cookie", cookies)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
        }

        val html = response.bodyAsText()

        // Extrakce CSRF tokenu (SNlM0e)
        val csrfMatch = Regex(""""SNlM0e"\s*:\s*"([^"]+)"""").find(html)
            ?: return null
        val csrfToken = csrfMatch.groupValues[1]

        // Extrakce session ID (FdrFJe)
        val sessionMatch = Regex(""""FdrFJe"\s*:\s*"([^"]+)"""").find(html)
            ?: return null
        val sessionId = sessionMatch.groupValues[1]

        prefs.edit()
            .putString(KEY_CSRF, csrfToken)
            .putString(KEY_SESSION_ID, sessionId)
            .apply()

        return AuthTokens(cookies, csrfToken, sessionId)
    }

    fun loadTokens(): AuthTokens? {
        val cookies = prefs.getString(KEY_COOKIES, null) ?: return null
        val csrf = prefs.getString(KEY_CSRF, null) ?: return null
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        return AuthTokens(cookies, csrf, sessionId)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = prefs.getString(KEY_COOKIES, null) != null
            && prefs.getString(KEY_CSRF, null) != null
}

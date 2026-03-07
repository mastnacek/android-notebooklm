package dev.jara.notebooklm.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Activity s WebView pro Google login.
 * WebView sdili CookieManager s aplikaci — po prihlaseni muzeme cookies vytahnout.
 *
 * Flow: otevre notebooklm.google.com -> Google login redirect ->
 *       po prihlaseni se vrati na notebooklm.google.com -> zavre se a vrati cookies.
 */
class LoginActivity : Activity() {

    companion object {
        private const val TAG = "LoginActivity"
        const val RESULT_COOKIES = "cookies"
        private const val NOTEBOOKLM_URL = "https://notebooklm.google.com/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(WebView(this), true)

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Jsme na NotebookLM po prihlaseni?
                    if (url != null && url.startsWith(NOTEBOOKLM_URL)) {
                        // Pockame az se stranka nacte
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null && url.startsWith(NOTEBOOKLM_URL)) {
                        // Ziskame cookies pro vice domen
                        val nbCookies = cookieManager.getCookie("https://notebooklm.google.com") ?: ""
                        val gCookies = cookieManager.getCookie("https://google.com") ?: ""
                        val dotGCookies = cookieManager.getCookie("https://.google.com") ?: ""
                        val accCookies = cookieManager.getCookie("https://accounts.google.com") ?: ""

                        Log.i(TAG, "nbCookies: ${nbCookies.take(200)}")
                        Log.i(TAG, "gCookies: ${gCookies.take(200)}")
                        Log.i(TAG, "dotGCookies: ${dotGCookies.take(200)}")
                        Log.i(TAG, "accCookies: ${accCookies.take(200)}")

                        val combined = mergeCookies(gCookies, dotGCookies, accCookies, nbCookies)
                        Log.i(TAG, "combined cookies (${combined.length} chars): ${combined.take(300)}")

                        val hasAuth = combined.contains("SID=") || combined.contains("__Secure-1PSID=")
                        Log.i(TAG, "hasAuth=$hasAuth")

                        if (hasAuth) {
                            intent.putExtra(RESULT_COOKIES, combined)
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    }
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(NOTEBOOKLM_URL)
    }

    private fun mergeCookies(vararg cookieStrings: String): String {
        val map = linkedMapOf<String, String>()
        for (cs in cookieStrings) {
            for (part in cs.split(";")) {
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val name = trimmed.substring(0, eq)
                    val value = trimmed.substring(eq + 1)
                    map[name] = value
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}

package market.foodhome.app.auth

import android.app.Activity
import android.net.Uri
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI

/** Persist only the attempt ID, never a bank code, provider token or website credential. */
class AndroidSberAuthCoordinator(
    private val activity: Activity,
    trustedOrigin: URI,
    private val enabled: Boolean,
) {
    private val policy = SberAuthPolicy(trustedOrigin)
    private val preferences = activity.getSharedPreferences("foodhome-sber-auth-v1", Activity.MODE_PRIVATE)

    fun launch(attemptId: String, authorizationUrl: String): Boolean {
        if (!enabled || !policy.acceptsAuthorization(attemptId, authorizationUrl)) return false
        if (!preferences.edit().putString("attempt", attemptId)
                .putLong("expiresAt", System.currentTimeMillis() + 300_000).commit()) return false
        CookieManager.getInstance().flush()
        return runCatching {
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(authorizationUrl))
            true
        }.getOrElse {
            preferences.edit().clear().apply()
            false
        }
    }

    fun acceptsReturn(rawUrl: String): Boolean {
        if (!enabled) return false
        val attempt = policy.returnAttempt(rawUrl) ?: return false
        return attempt == preferences.getString("attempt", null) &&
            preferences.getLong("expiresAt", 0) > System.currentTimeMillis()
    }
}

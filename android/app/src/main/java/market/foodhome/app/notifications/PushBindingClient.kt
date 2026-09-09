package market.foodhome.app.notifications

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Isolated HTTP client: never accesses WebView cookies or accepts a caller-supplied URL. */
internal class PushBindingClient(private val client: OkHttpClient = isolatedClient()) {
    companion object {
        internal fun isolatedClient(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(4, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
    }

    fun redeem(action: String, nonce: String, body: JSONObject): JSONObject? {
        require(action == "bind" || action == "revoke")
        val request = Request.Builder()
            .url("https://foodhome.market/api/v1/mobile/installations/$action/")
            .header("Authorization", "FoodHomeBinding $nonce")
            .header("Cache-Control", "no-store")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.code !in setOf(200, 201)) return null
            val source = response.body.source()
            // Never materialize an unbounded response or expose a provider/backend error body.
            if (source.request(8_193)) return null
            JSONObject(source.readUtf8())
        }
    }
}

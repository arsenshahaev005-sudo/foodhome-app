package market.foodhome.app.notifications

import okhttp3.CookieJar
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PushBindingClientTest {
    @Test fun `transport has no cookie store cache redirects or retries and bounded deadlines`() {
        val client = PushBindingClient.isolatedClient()
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertTrue(client.interceptors.isEmpty())
        assertEquals(4_000, client.callTimeoutMillis)
    }

    @Test fun `nonce goes only to fixed HTTPS binding endpoint and response is bounded`() {
        var calls = 0
        // Test-only interceptor short-circuits the call; no network request leaves this process.
        val client = PushBindingClient.isolatedClient().newBuilder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            assertEquals("https://foodhome.market/api/v1/mobile/installations/bind/", request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("FoodHomeBinding test-nonce", request.header("Authorization"))
            assertNull(request.header("Cookie"))
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            assertEquals("test-provider-token", JSONObject(buffer.readUtf8()).getString("pushToken"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(201).message("Created")
                .body("""{"installationId":"test-id"}""".toResponseBody()).build()
        }.build()
        val result = PushBindingClient(client).redeem("bind", "test-nonce", JSONObject().put("pushToken", "test-provider-token"))
        assertEquals("test-id", result?.getString("installationId"))
        assertEquals(1, calls)
    }

    @Test fun `redirect errors and oversized responses are never treated as binding success`() {
        for ((code, body) in listOf(302 to "{}", 500 to "private error", 200 to "x".repeat(8_193))) {
            val client = PushBindingClient.isolatedClient().newBuilder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                    .body(body.toResponseBody()).build()
            }.build()
            assertNull(PushBindingClient(client).redeem("revoke", "test-nonce", JSONObject()))
        }
    }

    @Test fun `only matching active production generation receipt enables visible push`() {
        fun receipt() = JSONObject().put("installationId", "test-id").put("bindingId", "a".repeat(64))
            .put("environment", "production").put("platform", "android").put("provider", "fcm")
            .put("notificationStatus", "authorized").put("revokedAt", JSONObject.NULL).put("invalidatedAt", JSONObject.NULL)
        assertTrue(PushBindingPolicy.acceptsReceipt(receipt(), "test-id", "a".repeat(64)))
        assertFalse(PushBindingPolicy.acceptsReceipt(receipt(), "other-id", "a".repeat(64)))
        for ((key, value) in listOf("bindingId" to "old", "environment" to "staging", "provider" to "apns",
            "platform" to "ios", "notificationStatus" to "denied", "revokedAt" to "yesterday", "invalidatedAt" to "yesterday")) {
            assertFalse(PushBindingPolicy.acceptsReceipt(receipt().put(key, value), "test-id", "a".repeat(64)))
        }
        val legacy = receipt().also { it.remove("bindingId") }
        assertFalse(PushBindingPolicy.acceptsReceipt(legacy, "test-id", "a".repeat(64)))
    }
}

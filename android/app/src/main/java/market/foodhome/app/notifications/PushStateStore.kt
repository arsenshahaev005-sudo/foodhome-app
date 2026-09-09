package market.foodhome.app.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class PushStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("foodhome_push_binding", Context.MODE_PRIVATE)
    private var current = read()

    fun get() = current

    fun set(state: PushRegistrationState): Boolean {
        val json = JSONObject()
            .put("installationId", state.installationId)
            .put("revision", state.revision)
            .put("bindingId", state.bindingId ?: JSONObject.NULL)
            .put("tokenDigest", state.tokenDigest ?: JSONObject.NULL)
            .put("needsBinding", state.needsBinding)
            .put("optedIn", state.optedIn)
            .put("seenEvents", JSONArray(state.seenEvents))
        val written = preferences.edit().putString("state", json.toString()).commit()
        // Fail closed in memory too if persistence fails.
        current = if (written) state else state.clear()
        return written
    }

    private fun read(): PushRegistrationState = runCatching {
        val value = JSONObject(preferences.getString("state", null) ?: error("No state"))
        val id = value.getString("installationId")
        require(UUID.fromString(id).version() == 4 && UUID.fromString(id).toString() == id)
        val revision = value.getLong("revision").also { require(it >= 0) }
        fun digest(key: String) = value.opt(key).let {
            if (it == null || it == JSONObject.NULL) null else (it as String).also { digest ->
                require(digest.matches(Regex("^[0-9a-f]{64}$")))
            }
        }
        val seen = value.getJSONArray("seenEvents")
        require(seen.length() <= 128)
        PushRegistrationState(id, revision, digest("bindingId"), digest("tokenDigest"),
            value.getBoolean("needsBinding"), List(seen.length()) { seen.getString(it) }, value.getBoolean("optedIn"))
    }.getOrElse { PushRegistrationState(UUID.randomUUID().toString()) }
}

package market.foodhome.app.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object PushStateCodec {
    fun encode(state: PushRegistrationState): String {
        val json = JSONObject()
            .put("installationId", state.installationId)
            .put("revision", state.revision)
            .put("bindingId", state.bindingId ?: JSONObject.NULL)
            .put("tokenDigest", state.tokenDigest ?: JSONObject.NULL)
            .put("needsBinding", state.needsBinding)
            .put("optedIn", state.optedIn)
            .put("seenEvents", JSONArray(state.seenEvents))
            .put("eventExpiries", JSONObject(state.eventExpiries))
            .put("openedEvents", JSONArray(state.openedEvents.toList()))
            .put("syncRequired", state.syncRequired)
        return json.toString()
    }

    fun decode(raw: String, nowMillis: Long): PushRegistrationState? = runCatching {
        val value = JSONObject(raw)
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
        val ids = List(seen.length()) { seen.get(it) as String }
        require(ids.distinct().size == ids.size)
        require(ids.all { UUID.fromString(it).toString() == it })
        val expiry = if (value.has("eventExpiries")) value.getJSONObject("eventExpiries") else null
        require(expiry == null || expiry.keySet() == ids.toSet())
        // Old APK receipts lack expiry. Conservatively retain them for another
        // maximum TTL after upgrade; never re-display one during migration.
        val expiries = ids.associateWith { eventId ->
            if (expiry == null) nowMillis + VisiblePushPolicy.MAX_LIFETIME_MILLIS
            else when (val number = expiry.get(eventId)) {
                is Long -> number
                is Int -> number.toLong()
                else -> error("Invalid receipt expiry")
            }.also { require(it > 0) }
        }
        val opened = if (value.has("openedEvents")) value.getJSONArray("openedEvents") else JSONArray()
        require(opened.length() <= 128)
        val openedIds = List(opened.length()) { opened.get(it) as String }.toSet()
        require(ids.containsAll(openedIds))
        PushRegistrationState(id, revision, digest("bindingId"), digest("tokenDigest"),
            value.getBoolean("needsBinding"), ids, value.getBoolean("optedIn"), expiries, openedIds,
            if (value.has("syncRequired")) value.get("syncRequired") as Boolean else false)
    }.getOrNull()
}

internal class PushStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("foodhome_push_binding", Context.MODE_PRIVATE)
    private val initialRaw = preferences.getString("state", null)
    private var current = initialRaw?.let { PushStateCodec.decode(it, System.currentTimeMillis()) }
        ?: PushRegistrationState(UUID.randomUUID().toString())

    init {
        // Persist a legacy horizon once; do not extend it on every cold start.
        if (current.seenEvents.isNotEmpty() && initialRaw != null && !JSONObject(initialRaw).has("eventExpiries")) set(current)
    }

    fun get() = current

    fun set(state: PushRegistrationState): Boolean {
        val written = preferences.edit().putString("state", PushStateCodec.encode(state)).commit()
        current = if (written) state else state.clear()
        return written
    }
}

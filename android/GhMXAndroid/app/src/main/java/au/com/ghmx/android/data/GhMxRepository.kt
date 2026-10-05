package au.com.ghmx.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GhMxRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ghmx_data", Context.MODE_PRIVATE)

    suspend fun refresh(force: Boolean): RemoteData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val vendors = get("ghmx-vendors.json", force)?.let(::JSONArray)?.let(::parseVendors)
            ?: cachedArray("vendors")?.let(::parseVendors).orEmpty()
        val status = get("schedule_2026_status.json", force)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val published = status?.optBoolean("published", prefs.getBoolean("published", false))
            ?: prefs.getBoolean("published", false)
        if (published == false) {
            prefs.edit().remove("central").remove("cosplay").putBoolean("published", false)
                .remove("schedule_is_sample").putLong("last_checked", now).apply()
            return@withContext RemoteData(vendors = vendors, published = false,
                message = status?.optString("message")?.ifBlank { "The full 2026 program will be available closer to the event." }
                    ?: "The full 2026 program will be available closer to the event.", lastChecked = now)
        }

        val centralText = get("schedule_central.json", force)
        val cosplayText = get("schedule_cosplay.json", force)
        val central = centralText?.let(::parseArraySafely) ?: cachedArray("central")?.let(::parseSchedule).orEmpty()
        val cosplay = cosplayText?.let(::parseArraySafely) ?: cachedArray("cosplay")?.let(::parseSchedule).orEmpty()
        val isPublished = published && (central + cosplay).any { it.events.isNotEmpty() }
        val edit = prefs.edit().putBoolean("published", isPublished).putLong("last_checked", now)
        centralText?.let { edit.putString("central", it) }
        cosplayText?.let { edit.putString("cosplay", it) }
        if (!isPublished) edit.remove("central").remove("cosplay").remove("schedule_is_sample")
        edit.apply()

        RemoteData(vendors = vendors, central = central, cosplay = cosplay, published = isPublished,
            message = status?.optString("message")?.takeIf { !it.isNullOrBlank() } ?: "The 2026 program will be available closer to the event.",
            lastChecked = now,
            error = if (vendors.isEmpty()) "Event information could not be downloaded. Check your connection and try again." else null)
    }

    fun readCache(): RemoteData {
        // Discard the sample schedule cached by an earlier build. schedule.json is never a source
        // for the Android event program; only the 2026 status and stage files are authoritative.
        if (prefs.getBoolean("schedule_is_sample", false)) {
            prefs.edit().remove("central").remove("cosplay").putBoolean("published", false)
                .remove("schedule_is_sample").apply()
        }
        val vendors = cachedArray("vendors")?.let(::parseVendors).orEmpty()
        val central = cachedArray("central")?.let(::parseSchedule).orEmpty()
        val cosplay = cachedArray("cosplay")?.let(::parseSchedule).orEmpty()
        return RemoteData(vendors, central, cosplay, prefs.getBoolean("published", false),
            lastChecked = prefs.getLong("last_checked", 0L).takeIf { it > 0 })
    }

    private fun get(file: String, force: Boolean): String? = try {
        val separator = "?refresh=${System.currentTimeMillis()}${if (force) "&force=true" else ""}"
        val connection = (URL("$DATA_BASE/$file$separator").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 12_000
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    } catch (_: Exception) { null }

    private fun cachedArray(key: String): JSONArray? = prefs.getString(key, null)?.let {
        runCatching { JSONArray(it) }.getOrNull()
    }

    private fun parseArraySafely(text: String): List<EventDay> = runCatching { parseSchedule(JSONArray(text)) }.getOrDefault(emptyList())

    private fun parseVendors(array: JSONArray) = (0 until array.length()).mapNotNull { i ->
        val row = array.optJSONObject(i) ?: return@mapNotNull null
        GhMxVendor(row.optString("id"), row.optString("name"), row.optString("booth"),
            row.optString("description"), row.optString("website"), row.optDoubleOrNull("x"),
            row.optDoubleOrNull("y"), row.optDoubleOrNull("width"), row.optDoubleOrNull("height"))
    }.sortedWith(compareBy({ it.booth.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.booth }, { it.name }))
        .also { prefs.edit().putString("vendors", array.toString()).apply() }

    private fun parseSchedule(array: JSONArray): List<EventDay> = (0 until array.length()).mapNotNull { i ->
        val row = array.optJSONObject(i) ?: return@mapNotNull null
        val events = row.optJSONArray("events") ?: JSONArray()
        val day = row.optString("day")
        val date = row.optString("date").takeIf(String::isNotBlank)
        EventDay(day, date,
            (0 until events.length()).mapNotNull { j -> events.optJSONObject(j)?.let { e ->
                GhMxEvent(e.optString("time"), e.optString("title"), e.optString("stage"),
                    e.optString("description"), e.optString("image"), day, date)
            } })
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf(Double::isFinite)
}

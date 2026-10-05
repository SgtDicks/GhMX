package au.com.ghmx.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("ghmx_attendee_plan", Context.MODE_PRIVATE)

    fun vendors(): List<SavedVendor> = runCatching {
        val array = JSONArray(prefs.getString("vendors", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { row ->
            SavedVendor(row.optString("id"), row.optString("name"), row.optString("booth"),
                row.optString("note"), row.optLong("reminderAt").takeIf { it > 0 })
        } }
    }.getOrDefault(emptyList())

    fun saveVendor(vendor: SavedVendor) {
        val all = vendors().associateBy { it.id }.toMutableMap()
        all[vendor.id] = vendor
        writeVendors(all.values.toList())
    }

    fun removeVendor(id: String) = writeVendors(vendors().filterNot { it.id == id })

    private fun writeVendors(vendors: List<SavedVendor>) {
        val array = JSONArray()
        vendors.forEach { vendor -> array.put(JSONObject().apply {
            put("id", vendor.id); put("name", vendor.name); put("booth", vendor.booth)
            put("note", vendor.note); put("reminderAt", vendor.reminderAt ?: 0L)
        }) }
        prefs.edit().putString("vendors", array.toString()).apply()
    }

    fun favoriteEvents(): Set<String> = prefs.getStringSet("favorite_events", emptySet())?.toSet() ?: emptySet()
    fun migrateFavoriteEvents(events: List<GhMxEvent>) {
        val updated = favoriteEvents().toMutableSet()
        var changed = false
        updated.toList().forEach { savedId ->
            if (events.any { it.legacyId == savedId } && events.none { it.id == savedId }) {
                val match = events.firstOrNull { it.legacyId == savedId } ?: return@forEach
                updated.remove(savedId)
                updated.add(match.id)
                changed = true
            }
        }
        if (changed) prefs.edit().putStringSet("favorite_events", updated).apply()
    }
    fun setFavorite(id: String, favorite: Boolean) {
        val updated = favoriteEvents().toMutableSet().apply { if (favorite) add(id) else remove(id) }
        prefs.edit().putStringSet("favorite_events", updated).apply()
    }

    fun reminders(): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString("reminders", "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    fun saveReminder(id: String, title: String, body: String, at: Long) {
        val array = JSONArray().apply {
            reminders().filterNot { it.optString("id") == id }.forEach(::put)
            put(JSONObject().apply { put("id", id); put("title", title); put("body", body); put("at", at) })
        }
        prefs.edit().putString("reminders", array.toString()).apply()
    }

    fun removeReminder(id: String) {
        val array = JSONArray().apply { reminders().filterNot { it.optString("id") == id }.forEach(::put) }
        prefs.edit().putString("reminders", array.toString()).apply()
    }
}

package au.com.ghmx.android.data

data class EventDay(val day: String, val date: String?, val events: List<GhMxEvent>)
data class GhMxEvent(
    val time: String,
    val title: String,
    val stage: String,
    val description: String,
    val image: String,
    val day: String = "",
    val date: String? = null,
) {
    val id: String get() = "${date.orEmpty()}|$day|$stage|$time|$title"
    val legacyId: String get() = "$stage|$time|$title"
}

data class GhMxVendor(
    val id: String,
    val name: String,
    val booth: String,
    val description: String,
    val website: String,
    val x: Double?,
    val y: Double?,
    val width: Double?,
    val height: Double?,
)

data class FoodVendor(val name: String, val image: String) {
    val imageUrl: String get() = "$DATA_BASE/food/$image"

    companion object {
        val all = listOf(
            FoodVendor("Mr Pulled", "mr-pulled.jpg"), FoodVendor("Tea 365", "tea-365.jpg"),
            FoodVendor("That Chicken Chick", "that-chicken-chick.jpg"), FoodVendor("Chef 365", "chef-365.jpg"),
            FoodVendor("Gimme Donuts", "gimme-donuts.jpg"),
        )
    }
}

data class SavedVendor(
    val id: String,
    val name: String,
    val booth: String,
    val note: String = "",
    val reminderAt: Long? = null,
)

data class RemoteData(
    val vendors: List<GhMxVendor> = emptyList(),
    val central: List<EventDay> = emptyList(),
    val cosplay: List<EventDay> = emptyList(),
    val published: Boolean = false,
    val message: String = "The full 2026 program will be available closer to the event.",
    val lastChecked: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    // Published test rows (including placeholder titles) are intentional: publication state
    // controls visibility, while the schedule files control which rows are shown.
    val scheduleAvailable: Boolean get() = published && (central + cosplay).any { it.events.isNotEmpty() }
}

const val DATA_BASE = "https://raw.githubusercontent.com/SgtDicks/GhMX/refs/heads/main"

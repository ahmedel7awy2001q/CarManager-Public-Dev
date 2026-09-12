package com.ahmed.carmanager.data.gps

import android.content.Context

enum class AccessoryTrackerSlot(val key: String, val titleAr: String) {
    KEY("key", "مفتاح المركبة"),
    VEHICLE("vehicle", "داخل المركبة")
}

data class AccessoryTrackerConfig(
    val slot: AccessoryTrackerSlot,
    val name: String,
    val network: String = "Apple Find My"
)

/**
 * Stores user-declared accessory trackers locally. Find My does not expose a public Android API
 * for live item coordinates, therefore this store never pretends that a tag is a GPS source.
 */
class AccessoryTrackerStore(context: Context) {
    private val prefs = context.getSharedPreferences("cm_accessory_trackers", Context.MODE_PRIVATE)

    fun load(slot: AccessoryTrackerSlot): AccessoryTrackerConfig? {
        val name = prefs.getString("${slot.key}_name", null)?.trim().orEmpty()
        if (name.isBlank()) return null
        val network = prefs.getString("${slot.key}_network", "Apple Find My") ?: "Apple Find My"
        return AccessoryTrackerConfig(slot, name, network)
    }

    fun save(slot: AccessoryTrackerSlot, name: String, network: String = "Apple Find My") {
        prefs.edit().putString("${slot.key}_name", name.trim()).putString("${slot.key}_network", network).apply()
    }

    fun remove(slot: AccessoryTrackerSlot) {
        prefs.edit().remove("${slot.key}_name").remove("${slot.key}_network").apply()
    }
}

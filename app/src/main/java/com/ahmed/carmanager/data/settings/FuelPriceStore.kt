package com.ahmed.carmanager.data.settings

import android.content.Context
import com.ahmed.carmanager.data.local.model.FuelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FuelMarketPrice(
    val fuelType: FuelType,
    val price: Double,
    val source: String,
    val updatedAt: Long,
    val isManual: Boolean
) {
    val isStale: Boolean
        get() = isStaleAt(System.currentTimeMillis())

    fun isStaleAt(now: Long): Boolean =
        updatedAt <= 0L || now - updatedAt > FuelPriceStore.REFRESH_INTERVAL_MS
}

class FuelPriceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "carmanager_fuel_prices",
        Context.MODE_PRIVATE
    )

    private val _prices = MutableStateFlow(readAll())
    val prices: StateFlow<Map<FuelType, FuelMarketPrice>> = _prices.asStateFlow()

    fun get(fuelType: FuelType): FuelMarketPrice? =
        _prices.value[fuelType]

    fun hasCompleteSnapshot(): Boolean =
        SUPPORTED_TYPES.all { _prices.value.containsKey(it) }

    fun needsRefresh(now: Long = System.currentTimeMillis()): Boolean =
        SUPPORTED_TYPES.any { type ->
            val item = _prices.value[type]
            item == null || item.isStaleAt(now)
        }

    fun latestUpdateAt(): Long? =
        _prices.value.values
            .map { it.updatedAt }
            .filter { it > 0L }
            .maxOrNull()

    fun oldestUpdateAt(): Long? =
        _prices.value.values
            .map { it.updatedAt }
            .filter { it > 0L }
            .minOrNull()

    fun save(
        fuelType: FuelType,
        price: Double,
        source: String,
        updatedAt: Long = System.currentTimeMillis(),
        isManual: Boolean = false
    ) {
        require(fuelType in SUPPORTED_TYPES) {
            "Unsupported fuel type: $fuelType"
        }
        require(price in VALID_PRICE_RANGE) {
            "Fuel price is outside the valid range"
        }

        require(updatedAt > 0L) {
            "Fuel price update time must be valid"
        }

        prefs.edit()
            .putFloat(keyPrice(fuelType), price.toFloat())
            .putString(keySource(fuelType), source.trim())
            .putLong(keyUpdatedAt(fuelType), updatedAt)
            .putBoolean(keyManual(fuelType), isManual)
            .apply()

        refreshState()
    }

    fun saveMarketPrices(
        gasoline80: Double,
        gasoline92: Double,
        gasoline95: Double,
        diesel: Double,
        source: String,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        require(
            gasoline80 in VALID_PRICE_RANGE &&
                    gasoline92 in VALID_PRICE_RANGE &&
                    gasoline95 in VALID_PRICE_RANGE &&
                    diesel in VALID_PRICE_RANGE
        ) {
            "Market snapshot contains an invalid fuel price"
        }

        require(updatedAt > 0L) {
            "Market snapshot update time must be valid"
        }

        val cleanSource = source.trim()
        val editor = prefs.edit()

        listOf(
            FuelType.GASOLINE_80 to gasoline80,
            FuelType.GASOLINE_92 to gasoline92,
            FuelType.GASOLINE_95 to gasoline95,
            FuelType.DIESEL to diesel
        ).forEach { (type, value) ->
            editor
                .putFloat(keyPrice(type), value.toFloat())
                .putString(keySource(type), cleanSource)
                .putLong(keyUpdatedAt(type), updatedAt)
                .putBoolean(keyManual(type), false)
        }

        editor.apply()
        refreshState()
    }

    fun clear(fuelType: FuelType) {
        if (fuelType !in SUPPORTED_TYPES) return

        prefs.edit()
            .remove(keyPrice(fuelType))
            .remove(keySource(fuelType))
            .remove(keyUpdatedAt(fuelType))
            .remove(keyManual(fuelType))
            .apply()

        refreshState()
    }

    fun clearAll() {
        val editor = prefs.edit()

        SUPPORTED_TYPES.forEach { type ->
            editor
                .remove(keyPrice(type))
                .remove(keySource(type))
                .remove(keyUpdatedAt(type))
                .remove(keyManual(type))
        }

        editor.apply()
        refreshState()
    }

    private fun refreshState() {
        _prices.value = readAll()
    }

    private fun readAll(): Map<FuelType, FuelMarketPrice> =
        SUPPORTED_TYPES.mapNotNull { type ->
            read(type)?.let { type to it }
        }.toMap()

    private fun read(type: FuelType): FuelMarketPrice? {
        if (!prefs.contains(keyPrice(type))) return null

        val price = prefs.getFloat(keyPrice(type), 0f).toDouble()
        if (price !in VALID_PRICE_RANGE) return null

        return FuelMarketPrice(
            fuelType = type,
            price = price,
            source = prefs.getString(keySource(type), "") ?: "",
            updatedAt = prefs.getLong(keyUpdatedAt(type), 0L),
            isManual = prefs.getBoolean(keyManual(type), false)
        )
    }

    private fun keyPrice(type: FuelType) = "${type.name}_price"
    private fun keySource(type: FuelType) = "${type.name}_source"
    private fun keyUpdatedAt(type: FuelType) = "${type.name}_updated_at"
    private fun keyManual(type: FuelType) = "${type.name}_manual"

    companion object {
        const val REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
        private val VALID_PRICE_RANGE = 1.0..100.0

        val SUPPORTED_TYPES = listOf(
            FuelType.GASOLINE_80,
            FuelType.GASOLINE_92,
            FuelType.GASOLINE_95,
            FuelType.DIESEL
        )
    }
}

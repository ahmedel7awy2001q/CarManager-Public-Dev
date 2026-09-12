package com.ahmed.carmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_quotes",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["vehicleId"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleId"),
        Index(value = ["vehicleId", "createdAt"]),
        Index("convertedTripId")
    ]
)
data class TripQuoteEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val routeProvider: String? = null,
    val oneWayDistanceKm: Double,
    val roundTrip: Boolean,
    val totalDistanceKm: Double,
    val passengerCount: Int,
    val totalSeatCapacity: Int? = null,
    val waitingHours: Double,
    val waitingRatePerHour: Double,
    val tolls: Double,
    val driverExpense: Double,
    val consumptionLitersPer100Km: Double? = null,
    val fuelPricePerLiter: Double? = null,
    val maintenancePerKm: Double,
    val includeMaintenance: Boolean,
    val annualFixedPerKm: Double,
    val includeAnnualFixed: Boolean,
    val depreciationPerKm: Double,
    val includeDepreciation: Boolean,
    val profitMarginPercent: Double,
    val marketOffer: Double? = null,
    val estimatedFuelLiters: Double? = null,
    val fuelCost: Double? = null,
    val trueTripCost: Double,
    val suggestedQuote: Double,
    val perPassengerQuote: Double? = null,
    val readinessPercent: Int,
    val notes: String? = null,
    val convertedTripId: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

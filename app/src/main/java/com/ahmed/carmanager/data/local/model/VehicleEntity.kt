package com.ahmed.carmanager.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["vehicleId"], unique = true),
        Index(value = ["ownerUserId"]),
        Index(value = ["plateNumber"]),
        Index(value = ["licenseNumber"]),
        Index(value = ["vin"], unique = true)
    ]
)
data class VehicleEntity(
    @PrimaryKey val vehicleId: String = newEntityId(),
    val ownerUserId: String? = null,
    val displayName: String? = null,
    val brand: String,
    val model: String,
    val trim: String? = null,
    val year: Int,
    val color: String? = null,
    val fuelType: FuelType = FuelType.GASOLINE_95,
    val transmissionType: TransmissionType = TransmissionType.AUTOMATIC,
    /** Human-readable engine family/name, e.g. Gamma MPI / Ecotec / TSI. */
    val engineName: String? = null,
    /** Manufacturer engine code, e.g. G4FG. Used as a strong parts-fitment signal. */
    val engineCode: String? = null,
    val engineCapacityCc: Int? = null,
    /** Generation/chassis code, e.g. BD / N17 / CN7. */
    val generationCode: String? = null,
    /** Human-readable transmission family/name, e.g. 6AT / IVT / 7DCT. */
    val transmissionName: String? = null,
    /** Manufacturer transmission code when known. */
    val transmissionCode: String? = null,
    val plateNumber: String? = null,
    /** Legacy field kept in schema for safe upgrades. New UI uses plateNumber instead. */
    val licenseNumber: String? = null,
    val vin: String? = null,
    val engineNumber: String? = null,
    val tankCapacityLiters: Double? = null,
    val tireSize: String? = null,
    /** Total legal seating positions including the driver. Used for trip capacity validation. */
    val passengerCapacity: Int? = null,
    val purchaseDate: Long? = null,
    val purchaseOdometerKm: Double? = null,
    val purchasePrice: Double? = null,
    val currentOdometerKm: Double = 0.0,
    val vehiclePhotoUri: String? = null,
    val status: VehicleStatus = VehicleStatus.ACTIVE,
    val isPrimary: Boolean = false,
    val archivedAt: Long? = null,
    val soldAt: Long? = null,
    val saleOdometerKm: Double? = null,
    val salePrice: Double? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false,
    /** Physical vehicle family. Existing installations migrate safely to CAR. */
    val vehicleType: VehicleType = VehicleType.CAR,
    /** User-defined type label used only when vehicleType == OTHER. */
    val customVehicleType: String? = null,
    /**
     * Serialized inspection-template overrides for this vehicle. Keeping the configuration on the
     * vehicle makes it local-first and automatically included in the existing backup/cloud snapshot.
     */
    val inspectionTemplateConfig: String? = null,
    /** Defaults used by the true-trip-cost engine. All are optional and user-controlled. */
    val annualLicenseCost: Double? = null,
    val annualInsuranceCost: Double? = null,
    val annualOtherFixedCost: Double? = null,
    val annualDistanceKm: Double? = null,
    val currentMarketValue: Double? = null,
    val depreciationAnnualPercent: Double? = null,
    val includeAnnualFixedCostsInTripCost: Boolean = true,
    val includeDepreciationInTripCost: Boolean = true
)

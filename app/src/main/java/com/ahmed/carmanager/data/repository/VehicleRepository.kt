package com.ahmed.carmanager.data.repository

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleType
import kotlinx.coroutines.flow.Flow

data class CreateVehicleRequest(
    val displayName: String? = null,
    val brand: String,
    val model: String,
    val trim: String? = null,
    val year: Int,
    val color: String? = null,
    val fuelType: FuelType = FuelType.GASOLINE_95,
    val transmissionType: TransmissionType = TransmissionType.AUTOMATIC,
    val engineName: String? = null,
    val engineCode: String? = null,
    val engineCapacityCc: Int? = null,
    val generationCode: String? = null,
    val transmissionName: String? = null,
    val transmissionCode: String? = null,
    val plateNumber: String? = null,
    val licenseNumber: String? = null,
    val vin: String? = null,
    val engineNumber: String? = null,
    val tankCapacityLiters: Double? = null,
    val tireSize: String? = null,
    val passengerCapacity: Int? = null,
    val odometerKm: Double = 0.0,
    val photoUri: String? = null,
    val purchasePrice: Double? = null,
    val purchaseDate: Long? = null,
    val vehicleType: VehicleType = VehicleType.CAR,
    val customVehicleType: String? = null,
    val annualLicenseCost: Double? = null,
    val annualInsuranceCost: Double? = null,
    val annualOtherFixedCost: Double? = null,
    val annualDistanceKm: Double? = null,
    val currentMarketValue: Double? = null,
    val depreciationAnnualPercent: Double? = null,
    val includeAnnualFixedCostsInTripCost: Boolean = true,
    val includeDepreciationInTripCost: Boolean = true
)

data class UpdateVehicleRequest(
    val vehicleId: String,
    val displayName: String? = null,
    val brand: String,
    val model: String,
    val trim: String? = null,
    val year: Int,
    val color: String? = null,
    val fuelType: FuelType,
    val transmissionType: TransmissionType,
    val engineName: String? = null,
    val engineCode: String? = null,
    val engineCapacityCc: Int? = null,
    val generationCode: String? = null,
    val transmissionName: String? = null,
    val transmissionCode: String? = null,
    val plateNumber: String? = null,
    val licenseNumber: String? = null,
    val vin: String? = null,
    val engineNumber: String? = null,
    val tankCapacityLiters: Double? = null,
    val tireSize: String? = null,
    val passengerCapacity: Int? = null,
    val photoUri: String? = null,
    val purchasePrice: Double? = null,
    val purchaseDate: Long? = null,
    val odometerKm: Double? = null,
    val vehicleType: VehicleType = VehicleType.CAR,
    val customVehicleType: String? = null,
    val annualLicenseCost: Double? = null,
    val annualInsuranceCost: Double? = null,
    val annualOtherFixedCost: Double? = null,
    val annualDistanceKm: Double? = null,
    val currentMarketValue: Double? = null,
    val depreciationAnnualPercent: Double? = null,
    val includeAnnualFixedCostsInTripCost: Boolean = true,
    val includeDepreciationInTripCost: Boolean = true
)

data class SellVehicleRequest(
    val vehicleId: String,
    val odometerKm: Double,
    val salePrice: Double? = null,
    val soldAt: Long = System.currentTimeMillis()
)

sealed interface VehicleRepositoryResult<out T> {
    data class Success<T>(val value: T) : VehicleRepositoryResult<T>
    data class Error(val messageAr: String, val cause: Throwable? = null) : VehicleRepositoryResult<Nothing>
}

interface VehicleRepository {
    fun observeVehicles(): Flow<List<VehicleEntity>>
    suspend fun getPrimaryVehicle(): VehicleEntity?
    suspend fun hasUnclaimedVehicles(): Boolean
    suspend fun claimUnclaimedVehicles(): VehicleRepositoryResult<Int>
    suspend fun addVehicle(request: CreateVehicleRequest): VehicleRepositoryResult<String>
    suspend fun updateVehicle(request: UpdateVehicleRequest): VehicleRepositoryResult<Unit>
    suspend fun makePrimary(vehicleId: String): VehicleRepositoryResult<Unit>
    suspend fun archive(vehicleId: String): VehicleRepositoryResult<Unit>
    suspend fun restore(vehicleId: String): VehicleRepositoryResult<Unit>
    suspend fun markSold(request: SellVehicleRequest): VehicleRepositoryResult<Unit>
    suspend fun softDelete(vehicleId: String): VehicleRepositoryResult<Unit>
    suspend fun saveInspectionTemplateConfig(vehicleId: String, config: String?): VehicleRepositoryResult<Unit>
}

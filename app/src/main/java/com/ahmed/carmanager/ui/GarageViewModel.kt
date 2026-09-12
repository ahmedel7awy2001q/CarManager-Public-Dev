package com.ahmed.carmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleType
import com.ahmed.carmanager.data.repository.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ahmed.carmanager.data.local.model.TirePosition

data class MaintenanceBaselineDraft(
    val titleAr: String,
    val lastServiceOdometerKm: Double? = null,
    val lastServiceDate: Long? = null
)

data class InitialTireSetDraft(
    val brand: String? = null,
    val model: String? = null,
    val size: String? = null,
    val installOdometerKm: Double? = null,
    val installDate: Long? = null,
    val totalCost: Double? = null
)

data class InitialBatteryDraft(
    val brand: String? = null,
    val model: String? = null,
    val capacityAh: Double? = null,
    val installOdometerKm: Double? = null,
    val installDate: Long? = null,
    val cost: Double? = null,
    val warrantyMonths: Int? = null
)

data class NewVehicleInput(
    val brand: String,
    val model: String,
    val year: Int,
    val plateNumber: String? = null,
    val licenseNumber: String? = null,
    val vin: String? = null,
    val engineNumber: String? = null,
    val odometerKm: Double = 0.0,
    val photoUri: String? = null,
    val displayName: String? = null,
    val trim: String? = null,
    val color: String? = null,
    val fuelType: FuelType = FuelType.GASOLINE_95,
    val transmissionType: TransmissionType = TransmissionType.AUTOMATIC,
    val engineName: String? = null,
    val engineCode: String? = null,
    val engineCapacityCc: Int? = null,
    val generationCode: String? = null,
    val transmissionName: String? = null,
    val transmissionCode: String? = null,
    val tankCapacityLiters: Double? = null,
    val tireSize: String? = null,
    val passengerCapacity: Int? = null,
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
    val includeDepreciationInTripCost: Boolean = true,
    val maintenanceBaseline: List<MaintenanceBaselineDraft> = emptyList(),
    val initialTireSet: InitialTireSetDraft? = null,
    val initialBattery: InitialBatteryDraft? = null
)

data class EditVehicleInput(
    val vehicleId: String,
    val brand: String,
    val model: String,
    val year: Int,
    val plateNumber: String? = null,
    val licenseNumber: String? = null,
    val odometerKm: Double? = null,
    val photoUri: String? = null,
    val displayName: String? = null,
    val trim: String? = null,
    val color: String? = null,
    val fuelType: FuelType,
    val transmissionType: TransmissionType,
    val engineName: String? = null,
    val engineCode: String? = null,
    val engineCapacityCc: Int? = null,
    val generationCode: String? = null,
    val transmissionName: String? = null,
    val transmissionCode: String? = null,
    val tankCapacityLiters: Double? = null,
    val tireSize: String? = null,
    val passengerCapacity: Int? = null,
    val vin: String? = null,
    val engineNumber: String? = null,
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

sealed interface GarageUiEvent { data class Message(val textAr: String) : GarageUiEvent }

/**
 * ViewModel صغير متوافق مع النسخة الأولى. الواجهة الاحترافية تستخدم CarManagerViewModel،
 * لكن الإبقاء عليه يمنع كسر أي مسار قديم أثناء التطوير.
 */
class GarageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as CarManagerApplication).container.vehicleRepository
    val vehicles = repository.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _events = MutableSharedFlow<GarageUiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun addVehicle(input: NewVehicleInput) = launchOperation {
        repository.addVehicle(input.toCreateRequest())
    }
    fun makePrimary(vehicleId: String) = launchOperation { repository.makePrimary(vehicleId) }
    fun archive(vehicleId: String) = launchOperation { repository.archive(vehicleId) }
    fun restore(vehicleId: String) = launchOperation { repository.restore(vehicleId) }
    fun markSold(vehicleId: String, odometerKm: Double, salePrice: Double?) = launchOperation { repository.markSold(SellVehicleRequest(vehicleId, odometerKm, salePrice)) }

    private fun launchOperation(block: suspend () -> VehicleRepositoryResult<*>) = viewModelScope.launch {
        if (block() is VehicleRepositoryResult.Error) _events.emit(GarageUiEvent.Message("تعذر تنفيذ العملية."))
    }
}

internal fun NewVehicleInput.toCreateRequest() = CreateVehicleRequest(
    displayName = displayName,
    brand = brand,
    model = model,
    trim = trim,
    year = year,
    color = color,
    fuelType = fuelType,
    transmissionType = transmissionType,
    engineName = engineName,
    engineCode = engineCode,
    engineCapacityCc = engineCapacityCc,
    generationCode = generationCode,
    transmissionName = transmissionName,
    transmissionCode = transmissionCode,
    plateNumber = plateNumber,
    licenseNumber = licenseNumber,
    vin = vin,
    engineNumber = engineNumber,
    tankCapacityLiters = tankCapacityLiters,
    tireSize = tireSize,
    passengerCapacity = passengerCapacity,
    odometerKm = odometerKm,
    photoUri = photoUri,
    purchasePrice = purchasePrice,
    purchaseDate = purchaseDate,
    vehicleType = vehicleType,
    customVehicleType = customVehicleType,
    annualLicenseCost = annualLicenseCost,
    annualInsuranceCost = annualInsuranceCost,
    annualOtherFixedCost = annualOtherFixedCost,
    annualDistanceKm = annualDistanceKm,
    currentMarketValue = currentMarketValue,
    depreciationAnnualPercent = depreciationAnnualPercent,
    includeAnnualFixedCostsInTripCost = includeAnnualFixedCostsInTripCost,
    includeDepreciationInTripCost = includeDepreciationInTripCost
)

internal fun EditVehicleInput.toUpdateRequest() = UpdateVehicleRequest(
    vehicleId = vehicleId,
    displayName = displayName,
    brand = brand,
    model = model,
    trim = trim,
    year = year,
    color = color,
    fuelType = fuelType,
    transmissionType = transmissionType,
    engineName = engineName,
    engineCode = engineCode,
    engineCapacityCc = engineCapacityCc,
    generationCode = generationCode,
    transmissionName = transmissionName,
    transmissionCode = transmissionCode,
    plateNumber = plateNumber,
    licenseNumber = licenseNumber,
    vin = vin,
    engineNumber = engineNumber,
    tankCapacityLiters = tankCapacityLiters,
    tireSize = tireSize,
    passengerCapacity = passengerCapacity,
    photoUri = photoUri,
    purchasePrice = purchasePrice,
    purchaseDate = purchaseDate,
    odometerKm = odometerKm,
    vehicleType = vehicleType,
    customVehicleType = customVehicleType,
    annualLicenseCost = annualLicenseCost,
    annualInsuranceCost = annualInsuranceCost,
    annualOtherFixedCost = annualOtherFixedCost,
    annualDistanceKm = annualDistanceKm,
    currentMarketValue = currentMarketValue,
    depreciationAnnualPercent = depreciationAnnualPercent,
    includeAnnualFixedCostsInTripCost = includeAnnualFixedCostsInTripCost,
    includeDepreciationInTripCost = includeDepreciationInTripCost
)

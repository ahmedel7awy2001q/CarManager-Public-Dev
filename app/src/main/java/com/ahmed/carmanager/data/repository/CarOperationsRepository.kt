package com.ahmed.carmanager.data.repository

import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenancePreset
import kotlinx.coroutines.flow.Flow

data class FuelInput(
    val amountPaid: Double,
    val pricePerLiter: Double,
    val odometerKm: Double,
    val fuelType: FuelType,
    val stationName: String? = null,
    val isFullTank: Boolean = false,
    val expectedConsumptionL100: Double? = null,
    val notes: String? = null,
    val date: Long = System.currentTimeMillis()
)

data class MaintenancePlanInput(
    val titleAr: String,
    val category: String = "دورية",
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val reminderRule: ReminderRule = ReminderRule.WHICHEVER_COMES_FIRST,
    val estimatedCost: Double? = null,
    val warningBeforeKm: Double? = 1000.0,
    val warningBeforeDays: Int? = 30,
    val notes: String? = null,
    val lastServiceOdometerKm: Double? = null,
    val lastServiceDate: Long? = null,
    val nextDueOdometerKm: Double? = null,
    val nextDueDate: Long? = null,
    val priority: Int = 0
)

data class MaintenanceRecordInput(
    val titleAr: String,
    val category: String = "صيانة",
    val odometerKm: Double,
    val totalCost: Double,
    val planId: String? = null,
    val laborCost: Double? = null,
    val partsCost: Double? = null,
    val serviceCenter: String? = null,
    val technician: String? = null,
    val invoiceNumber: String? = null,
    val warrantyUntil: Long? = null,
    val notes: String? = null,
    val date: Long = System.currentTimeMillis()
)

data class TripInput(
    val tripType: TripType,
    val distanceKm: Double,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val fuelCost: Double? = null,
    val operatingCost: Double? = null,
    val notes: String? = null,
    val date: Long = System.currentTimeMillis()
)

data class TripQuoteInput(
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
    val notes: String? = null
)

data class ExpenseInput(
    val category: ExpenseCategory,
    val amount: Double,
    val descriptionAr: String,
    val odometerKm: Double? = null,
    val merchant: String? = null,
    val notes: String? = null,
    val date: Long = System.currentTimeMillis()
)

data class GpsDeviceInput(
    val provider: GpsProvider,
    val deviceIdentifier: String,
    val deviceName: String? = null,
    val imei: String? = null,
    val installedOdometerKm: Double? = null,
    val providerMileageAtInstallKm: Double? = null,
    val notes: String? = null
)

data class PartInput(
    val nameAr: String,
    val category: String,
    val brand: String? = null,
    val partNumber: String? = null,
    val installOdometerKm: Double? = null,
    val cost: Double? = null,
    val expectedLifeKm: Double? = null,
    val expectedLifeMonths: Int? = null,
    val supplier: String? = null,
    val notes: String? = null,
    val purchaseDate: Long? = null,
    val installDate: Long? = System.currentTimeMillis(),
    val warrantyUntil: Long? = null
)

data class TireInput(
    val brand: String?,
    val model: String?,
    val size: String?,
    val position: TirePosition,
    val installOdometerKm: Double? = null,
    val cost: Double? = null,
    val pressurePsi: Double? = null,
    val notes: String? = null,
    val serialNumber: String? = null,
    val manufactureDateText: String? = null,
    val installDate: Long? = System.currentTimeMillis()
)

data class BatteryInput(
    val brand: String?,
    val model: String?,
    val capacityAh: Double? = null,
    val installOdometerKm: Double? = null,
    val cost: Double? = null,
    val warrantyMonths: Int? = null,
    val expectedLifeMonths: Int? = null,
    val notes: String? = null,
    val purchaseDate: Long? = null,
    val installDate: Long? = System.currentTimeMillis()
)

data class FaultInput(
    val symptomAr: String,
    val diagnosisAr: String? = null,
    val severity: FaultSeverity = FaultSeverity.MEDIUM,
    val odometerKm: Double? = null,
    val repairCost: Double? = null,
    val notes: String? = null
)

data class DocumentInput(
    val type: DocumentType,
    val number: String? = null,
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val fileUri: String? = null,
    val notes: String? = null
)

data class ReminderInput(
    val titleAr: String,
    val rule: ReminderRule,
    val dueDate: Long? = null,
    val dueOdometerKm: Double? = null,
    val warningBeforeDays: Int? = 30,
    val warningBeforeKm: Double? = 1000.0,
    val priority: Int = 0,
    val notes: String? = null
)

interface CarOperationsRepository {
    fun observeMaintenancePlans(vehicleId: String): Flow<List<MaintenancePlanEntity>>
    fun observeAllMaintenancePlans(vehicleId: String): Flow<List<MaintenancePlanEntity>>
    fun observeMaintenanceHistory(vehicleId: String): Flow<List<MaintenanceRecordEntity>>
    fun observeOdometer(vehicleId: String): Flow<List<OdometerRecordEntity>>
    fun observeFuel(vehicleId: String): Flow<List<FuelRecordEntity>>
    fun observeTrips(vehicleId: String): Flow<List<TripEntity>>
    fun observeTripQuotes(vehicleId: String): Flow<List<TripQuoteEntity>>
    fun observeExpenses(vehicleId: String): Flow<List<ExpenseEntity>>
    fun observeGpsDevices(vehicleId: String): Flow<List<GpsDeviceEntity>>
    fun observeLatestGps(vehicleId: String): Flow<GpsReadingEntity?>
    fun observeParts(vehicleId: String): Flow<List<PartEntity>>
    fun observeTires(vehicleId: String): Flow<List<TireEntity>>
    fun observeBatteries(vehicleId: String): Flow<List<BatteryRecordEntity>>
    fun observeFaults(vehicleId: String): Flow<List<FaultRecordEntity>>
    fun observeDocuments(vehicleId: String): Flow<List<VehicleDocumentEntity>>
    fun observeReminders(vehicleId: String): Flow<List<ReminderEntity>>

    suspend fun addMaintenancePlan(vehicleId: String, input: MaintenancePlanInput): VehicleRepositoryResult<Unit>
    suspend fun updateMaintenancePlan(vehicleId: String, planId: String, input: MaintenancePlanInput): VehicleRepositoryResult<Unit>
    suspend fun setMaintenancePlanActive(vehicleId: String, planId: String, active: Boolean): VehicleRepositoryResult<Unit>
    suspend fun deleteMaintenancePlan(vehicleId: String, planId: String): VehicleRepositoryResult<Unit>
    suspend fun installMaintenancePreset(vehicleId: String, preset: MaintenancePreset): VehicleRepositoryResult<Int>
    suspend fun addMaintenanceRecord(vehicleId: String, input: MaintenanceRecordInput): VehicleRepositoryResult<Unit>
    suspend fun addFuel(vehicleId: String, input: FuelInput): VehicleRepositoryResult<Unit>
    suspend fun addTrip(vehicleId: String, input: TripInput): VehicleRepositoryResult<Unit>
    suspend fun deleteTrip(vehicleId: String, tripId: String): VehicleRepositoryResult<Unit>
    suspend fun addTripQuote(vehicleId: String, input: TripQuoteInput): VehicleRepositoryResult<String>
    suspend fun deleteTripQuote(vehicleId: String, quoteId: String): VehicleRepositoryResult<Unit>
    suspend fun convertTripQuoteToTrip(vehicleId: String, quoteId: String): VehicleRepositoryResult<String>
    suspend fun addExpense(vehicleId: String, input: ExpenseInput): VehicleRepositoryResult<Unit>
    suspend fun addGpsDevice(vehicleId: String, input: GpsDeviceInput): VehicleRepositoryResult<Unit>
    suspend fun calibrateGps(vehicleId: String, deviceId: String, gpsMileageKm: Double, vehicleOdometerKm: Double): VehicleRepositoryResult<Unit>
    suspend fun addPart(vehicleId: String, input: PartInput): VehicleRepositoryResult<Unit>
    suspend fun addTire(vehicleId: String, input: TireInput): VehicleRepositoryResult<Unit>
    suspend fun addBattery(vehicleId: String, input: BatteryInput): VehicleRepositoryResult<Unit>
    suspend fun addFault(vehicleId: String, input: FaultInput): VehicleRepositoryResult<Unit>
    suspend fun addDocument(vehicleId: String, input: DocumentInput): VehicleRepositoryResult<Unit>
    suspend fun addReminder(vehicleId: String, input: ReminderInput): VehicleRepositoryResult<Unit>
    suspend fun completeReminder(reminder: ReminderEntity): VehicleRepositoryResult<Unit>
    suspend fun setOdometer(vehicleId: String, odometerKm: Double, source: OdometerSource = OdometerSource.MANUAL, note: String? = null): VehicleRepositoryResult<Unit>
}

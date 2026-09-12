package com.ahmed.carmanager.data.local.dao

import androidx.room.*
import com.ahmed.carmanager.data.local.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_plans WHERE vehicleId=:vehicleId AND isDeleted=0 AND isActive=1 ORDER BY priority DESC, COALESCE(nextDueOdometerKm, 1e18), COALESCE(nextDueDate, 9223372036854775807)") fun observePlans(vehicleId: String): Flow<List<MaintenancePlanEntity>>
    @Query("SELECT * FROM maintenance_plans WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY isActive DESC, priority DESC, COALESCE(nextDueOdometerKm, 1e18), titleAr") fun observeAllPlans(vehicleId: String): Flow<List<MaintenancePlanEntity>>
    @Query("SELECT * FROM maintenance_plans WHERE vehicleId=:vehicleId AND isDeleted=0") suspend fun getAllPlans(vehicleId: String): List<MaintenancePlanEntity>
    @Query("SELECT * FROM maintenance_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY serviceDate DESC, odometerKm DESC") fun observeHistory(vehicleId: String): Flow<List<MaintenanceRecordEntity>>
    @Query("SELECT * FROM maintenance_records WHERE vehicleId=:vehicleId AND maintenancePlanId=:planId AND isDeleted=0 ORDER BY serviceDate DESC, odometerKm DESC LIMIT 1") suspend fun latestRecordForPlan(vehicleId: String, planId: String): MaintenanceRecordEntity?
    @Query("SELECT * FROM maintenance_plans WHERE id=:id AND vehicleId=:vehicleId AND isDeleted=0 LIMIT 1") suspend fun getPlan(vehicleId: String, id: String): MaintenancePlanEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlan(item: MaintenancePlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecord(item: MaintenanceRecordEntity)
    @Update suspend fun updatePlan(item: MaintenancePlanEntity)
    @Update suspend fun updateRecord(item: MaintenanceRecordEntity)
}

@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY fuelDate DESC, createdAt DESC") fun observeByVehicle(vehicleId: String): Flow<List<FuelRecordEntity>>
    @Query("SELECT * FROM fuel_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY fuelDate DESC, createdAt DESC LIMIT 1") suspend fun latest(vehicleId: String): FuelRecordEntity?
    @Query("SELECT * FROM fuel_records WHERE vehicleId=:vehicleId AND isDeleted=0 AND isFullTank=1 ORDER BY fuelDate DESC, createdAt DESC LIMIT 1") suspend fun latestFullTank(vehicleId: String): FuelRecordEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: FuelRecordEntity)
    @Update suspend fun update(item: FuelRecordEntity)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY startTime DESC") fun observeByVehicle(vehicleId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id=:tripId AND isDeleted=0 LIMIT 1")
    suspend fun getById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE vehicleId=:vehicleId AND endTime IS NULL AND isDeleted=0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getOpenTrip(vehicleId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE vehicleId=:vehicleId AND endTime IS NULL AND isDeleted=0 ORDER BY startTime DESC LIMIT 1")
    fun observeOpenTrip(vehicleId: String): Flow<TripEntity?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: TripEntity)
    @Update suspend fun update(item: TripEntity)
}

@Dao
interface TripQuoteDao {
    @Query("SELECT * FROM trip_quotes WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY createdAt DESC")
    fun observeByVehicle(vehicleId: String): Flow<List<TripQuoteEntity>>

    @Query("SELECT * FROM trip_quotes WHERE id=:quoteId AND vehicleId=:vehicleId AND isDeleted=0 LIMIT 1")
    suspend fun getById(vehicleId: String, quoteId: String): TripQuoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TripQuoteEntity)

    @Update
    suspend fun update(item: TripQuoteEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY expenseDate DESC") fun observeByVehicle(vehicleId: String): Flow<List<ExpenseEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: ExpenseEntity)
    @Update suspend fun update(item: ExpenseEntity)
}

@Dao
interface OdometerDao {
    @Query("SELECT * FROM odometer_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY recordedAt DESC") fun observeByVehicle(vehicleId: String): Flow<List<OdometerRecordEntity>>
    @Query("SELECT * FROM odometer_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY recordedAt DESC LIMIT 1") suspend fun latest(vehicleId: String): OdometerRecordEntity?
    @Query("SELECT * FROM odometer_records WHERE vehicleId=:vehicleId AND source=:source AND odometerKm=:odometerKm AND recordedAt=:recordedAt AND isDeleted=0 ORDER BY ABS(createdAt - :createdAt) ASC, createdAt DESC LIMIT 1")
    suspend fun findMatchingSourceRecord(
        vehicleId: String,
        source: OdometerSource,
        odometerKm: Double,
        recordedAt: Long,
        createdAt: Long
    ): OdometerRecordEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: OdometerRecordEntity)
    @Update suspend fun update(item: OdometerRecordEntity)
}

@Dao
interface GpsDao {
    @Query("SELECT * FROM gps_devices WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY isActive DESC, updatedAt DESC") fun observeDevices(vehicleId: String): Flow<List<GpsDeviceEntity>>
    @Query("SELECT * FROM gps_devices WHERE vehicleId=:vehicleId AND isDeleted=0 AND isActive=1") suspend fun getActiveDevices(vehicleId: String): List<GpsDeviceEntity>
    @Query("SELECT * FROM gps_devices WHERE vehicleId=:vehicleId AND provider=:provider AND isDeleted=0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getDeviceByProvider(vehicleId: String, provider: GpsProvider): GpsDeviceEntity?

    @Query("SELECT * FROM gps_devices WHERE vehicleId=:vehicleId AND deviceIdentifier=:deviceIdentifier AND isDeleted=0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getDeviceByIdentifier(vehicleId: String, deviceIdentifier: String): GpsDeviceEntity?
    @Query("SELECT * FROM gps_devices WHERE isDeleted=0 AND isActive=1") suspend fun getAllActiveDevices(): List<GpsDeviceEntity>
    @Query("SELECT * FROM gps_readings WHERE vehicleId=:vehicleId AND gpsDeviceId=:deviceId ORDER BY timestamp DESC LIMIT :limit") suspend fun recentReadings(vehicleId: String, deviceId: String, limit: Int = 1000): List<GpsReadingEntity>
    @Query("SELECT * FROM gps_readings WHERE vehicleId=:vehicleId AND gpsDeviceId=:deviceId AND timestamp>=:fromTimestamp ORDER BY timestamp ASC")
    fun observeReadingsSince(
        vehicleId: String,
        deviceId: String,
        fromTimestamp: Long
    ): Flow<List<GpsReadingEntity>>

    @Query("SELECT * FROM gps_readings WHERE vehicleId=:vehicleId AND gpsDeviceId=:deviceId AND timestamp>=:startTimestamp AND timestamp<=:endTimestamp ORDER BY timestamp ASC")
    fun observeReadingsForWindow(
        vehicleId: String,
        deviceId: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<GpsReadingEntity>>
    @Query("SELECT * FROM gps_readings WHERE vehicleId=:vehicleId ORDER BY timestamp DESC LIMIT 1") fun observeLatestReading(vehicleId: String): Flow<GpsReadingEntity?>

    @Query("SELECT r.* FROM gps_readings r INNER JOIN gps_devices d ON d.id=r.gpsDeviceId WHERE r.vehicleId=:vehicleId AND d.vehicleId=:vehicleId AND d.provider=:provider AND d.isDeleted=0 AND d.isActive=1 ORDER BY r.timestamp DESC LIMIT 1")
    fun observeLatestReadingByProvider(
        vehicleId: String,
        provider: GpsProvider
    ): Flow<GpsReadingEntity?>
    @Query("SELECT * FROM gps_calibrations WHERE vehicleId=:vehicleId AND gpsDeviceId=:deviceId AND isDeleted=0 ORDER BY calibrationDate DESC LIMIT 1") suspend fun latestCalibration(vehicleId: String, deviceId: String): GpsCalibrationEntity?
    @Query("DELETE FROM gps_readings WHERE timestamp < :cutoff AND vehicleId IN (SELECT vehicleId FROM vehicles WHERE ownerUserId=:ownerUserId)")
    suspend fun deleteReadingsOlderThanForOwner(cutoff: Long, ownerUserId: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDevice(item: GpsDeviceEntity)
    @Update suspend fun updateDevice(item: GpsDeviceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCalibration(item: GpsCalibrationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReading(item: GpsReadingEntity)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM parts WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY installDate DESC") fun observeParts(vehicleId: String): Flow<List<PartEntity>>
    @Query("SELECT * FROM tires WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY position") fun observeTires(vehicleId: String): Flow<List<TireEntity>>
    @Query("SELECT * FROM battery_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY installDate DESC") fun observeBatteries(vehicleId: String): Flow<List<BatteryRecordEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPart(item: PartEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTire(item: TireEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTireRotation(item: TireRotationEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBattery(item: BatteryRecordEntity)
}

@Dao
interface SupportDao {
    @Query("SELECT * FROM fault_records WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY reportedDate DESC") fun observeFaults(vehicleId: String): Flow<List<FaultRecordEntity>>
    @Query("SELECT * FROM fault_records WHERE id=:id AND vehicleId=:vehicleId AND isDeleted=0 LIMIT 1") suspend fun getFault(vehicleId: String, id: String): FaultRecordEntity?
    @Query("SELECT * FROM vehicle_documents WHERE vehicleId=:vehicleId AND isDeleted=0 ORDER BY COALESCE(expiryDate, 9223372036854775807)") fun observeDocuments(vehicleId: String): Flow<List<VehicleDocumentEntity>>
    @Query("SELECT * FROM reminders WHERE vehicleId=:vehicleId AND isDeleted=0 AND isCompleted=0 ORDER BY priority DESC, COALESCE(dueDate, 9223372036854775807)") fun observeOpenReminders(vehicleId: String): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE isDeleted=0 AND isCompleted=0") suspend fun getAllOpenReminders(): List<ReminderEntity>
    @Query("SELECT * FROM attachments WHERE vehicleId=:vehicleId AND entityType=:type AND entityId=:entityId AND isDeleted=0 ORDER BY createdAt DESC") fun observeAttachments(vehicleId: String, type: EntityType, entityId: String): Flow<List<AttachmentEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFault(item: FaultRecordEntity)
    @Update suspend fun updateFault(item: FaultRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocumentRaw(item: VehicleDocumentEntity)

    @Transaction
    suspend fun insertDocument(item: VehicleDocumentEntity) {
        val now = System.currentTimeMillis()
        require(item.issueDate == null || item.issueDate <= now) {
            "تاريخ إصدار المستند لا يمكن أن يكون في المستقبل."
        }
        require(item.issueDate == null || item.expiryDate == null || item.expiryDate >= item.issueDate) {
            "تاريخ انتهاء المستند لا يمكن أن يسبق تاريخ الإصدار."
        }
        insertDocumentRaw(item)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReminder(item: ReminderEntity)
    @Update suspend fun updateReminder(item: ReminderEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAttachment(item: AttachmentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOwnership(item: OwnershipRecordEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSnapshot(item: MonthlySnapshotEntity)
}

package com.ahmed.carmanager.data.local.dao

import androidx.room.*
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus
import com.ahmed.carmanager.data.local.model.VehicleWithSummary
import kotlinx.coroutines.flow.Flow

@Dao
abstract class VehicleDao {
    @Query("SELECT * FROM vehicles WHERE ownerUserId = :ownerUserId AND isDeleted = 0 ORDER BY isPrimary DESC, updatedAt DESC")
    abstract fun observeAll(ownerUserId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE ownerUserId = :ownerUserId AND isDeleted = 0 ORDER BY isPrimary DESC, updatedAt DESC")
    abstract suspend fun getAll(ownerUserId: String): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0 LIMIT 1")
    abstract fun observeById(vehicleId: String, ownerUserId: String): Flow<VehicleEntity?>

    @Transaction
    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0 LIMIT 1")
    abstract fun observeWithSummary(vehicleId: String, ownerUserId: String): Flow<VehicleWithSummary?>

    @Query("SELECT COUNT(*) FROM vehicles WHERE ownerUserId = :ownerUserId AND isDeleted = 0")
    abstract suspend fun countVisible(ownerUserId: String): Int

    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0 LIMIT 1")
    abstract suspend fun getById(vehicleId: String, ownerUserId: String): VehicleEntity?

    /** Internal compatibility lookup for repositories that already receive an account-scoped vehicleId. */
    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId AND isDeleted = 0 LIMIT 1")
    abstract suspend fun getById(vehicleId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE ownerUserId = :ownerUserId AND isPrimary = 1 AND isDeleted = 0 LIMIT 1")
    abstract suspend fun getPrimary(ownerUserId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE ownerUserId = :ownerUserId AND isPrimary = 1 AND isDeleted = 0 LIMIT 1")
    abstract fun observePrimary(ownerUserId: String): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE ownerUserId = :ownerUserId AND isDeleted = 0 AND status IN ('ACTIVE','SECONDARY') ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun getFirstEligibleForPrimary(ownerUserId: String): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles WHERE ownerUserId IS NULL AND isDeleted = 0")
    abstract suspend fun countUnclaimed(): Int

    @Query("SELECT * FROM vehicles WHERE ownerUserId IS NULL AND isDeleted = 0 ORDER BY updatedAt DESC")
    abstract suspend fun getUnclaimed(): List<VehicleEntity>

    @Query("UPDATE vehicles SET ownerUserId = :ownerUserId, updatedAt = :now WHERE ownerUserId IS NULL")
    abstract suspend fun claimUnclaimed(ownerUserId: String, now: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(vehicle: VehicleEntity)

    @Update
    abstract suspend fun update(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET isPrimary = 0, status = CASE WHEN status = 'ACTIVE' THEN 'SECONDARY' ELSE status END, updatedAt = :now WHERE ownerUserId = :ownerUserId AND isDeleted = 0")
    protected abstract suspend fun clearPrimary(ownerUserId: String, now: Long)

    @Query("UPDATE vehicles SET isPrimary = 1, status = 'ACTIVE', archivedAt = NULL, updatedAt = :now WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0")
    protected abstract suspend fun setPrimaryFlag(vehicleId: String, ownerUserId: String, now: Long)

    @Transaction
    open suspend fun makePrimary(vehicleId: String, ownerUserId: String) {
        val now = System.currentTimeMillis()
        clearPrimary(ownerUserId, now)
        setPrimaryFlag(vehicleId, ownerUserId, now)
    }

    @Query("UPDATE vehicles SET status = 'ARCHIVED', archivedAt = :now, isPrimary = 0, updatedAt = :now WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0")
    abstract suspend fun archive(vehicleId: String, ownerUserId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE vehicles SET status = :status, archivedAt = NULL, soldAt = NULL, saleOdometerKm = NULL, salePrice = NULL, updatedAt = :now WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0")
    abstract suspend fun restore(
        vehicleId: String,
        ownerUserId: String,
        status: VehicleStatus = VehicleStatus.SECONDARY,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE vehicles SET status = 'SOLD', soldAt = :soldAt, saleOdometerKm = :odometerKm, salePrice = :salePrice, currentOdometerKm = MAX(currentOdometerKm, :odometerKm), isPrimary = 0, updatedAt = :now WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId AND isDeleted = 0")
    abstract suspend fun markSold(
        vehicleId: String,
        ownerUserId: String,
        soldAt: Long,
        odometerKm: Double,
        salePrice: Double?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE vehicles SET isDeleted = 1, isPrimary = 0, updatedAt = :now WHERE vehicleId = :vehicleId AND ownerUserId = :ownerUserId")
    abstract suspend fun softDelete(vehicleId: String, ownerUserId: String, now: Long = System.currentTimeMillis())
}
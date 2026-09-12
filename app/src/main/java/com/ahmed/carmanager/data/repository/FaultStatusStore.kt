package com.ahmed.carmanager.data.repository

import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultStatus

/**
 * Account-scoped lifecycle updates for an existing fault.
 * Status changes never delete the historical record. Resolved/closed states stamp resolvedDate;
 * reopening clears resolvedDate while preserving diagnosis, cost, notes and DTC metadata.
 */
class FaultStatusStore(
    private val database: CarDatabase,
    private val authRepository: AuthRepository
) {
    suspend fun updateStatus(fault: FaultRecordEntity, status: FaultStatus): VehicleRepositoryResult<Unit> {
        return try {
            val uid = authRepository.currentUid()
                ?: return VehicleRepositoryResult.Error("لا يوجد حساب مسجل للدخول.")
            database.vehicleDao().getById(fault.vehicleId, uid)
                ?: return VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")

            val current = database.supportDao().getFault(fault.vehicleId, fault.id)
                ?: return VehicleRepositoryResult.Error("سجل العطل لم يعد موجودًا.")
            if (current.status == status) return VehicleRepositoryResult.Success(Unit)

            val now = System.currentTimeMillis()
            val resolvedDate = when (status) {
                FaultStatus.RESOLVED, FaultStatus.CLOSED -> current.resolvedDate ?: now
                FaultStatus.OPEN, FaultStatus.DIAGNOSED, FaultStatus.IN_REPAIR -> null
            }
            database.supportDao().updateFault(
                current.copy(
                    status = status,
                    resolvedDate = resolvedDate,
                    updatedAt = now
                )
            )
            VehicleRepositoryResult.Success(Unit)
        } catch (t: Throwable) {
            VehicleRepositoryResult.Error("تعذر تحديث حالة العطل. لم يتم حذف أو فقد أي بيانات.", t)
        }
    }
}

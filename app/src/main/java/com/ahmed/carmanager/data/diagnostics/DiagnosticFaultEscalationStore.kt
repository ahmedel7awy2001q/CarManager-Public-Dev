package com.ahmed.carmanager.data.diagnostics

import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.FaultStatus
import com.ahmed.carmanager.data.repository.VehicleRepositoryResult

/**
 * Small account-scoped write path used only to escalate an already-open diagnostic fault.
 * It never creates, resolves, deletes, or downgrades a fault and does not alter Room schema.
 */
class DiagnosticFaultEscalationStore(
    private val database: CarDatabase,
    private val authRepository: AuthRepository
) {
    suspend fun update(proposed: FaultRecordEntity): VehicleRepositoryResult<Unit> {
        return try {
            val uid = authRepository.currentUid()
                ?: return VehicleRepositoryResult.Error("لا يوجد حساب مسجل للدخول.")
            database.vehicleDao().getById(proposed.vehicleId, uid)
                ?: return VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")

            val current = database.supportDao().getFault(proposed.vehicleId, proposed.id)
                ?: return VehicleRepositoryResult.Error("سجل العطل لم يعد موجودًا أو تم حذفه.")
            if (current.status == FaultStatus.RESOLVED || current.status == FaultStatus.CLOSED) {
                return VehicleRepositoryResult.Success(Unit)
            }
            if (severityRank(proposed.severity) <= severityRank(current.severity)) {
                return VehicleRepositoryResult.Success(Unit)
            }

            database.supportDao().updateFault(
                current.copy(
                    severity = proposed.severity,
                    notes = proposed.notes,
                    updatedAt = System.currentTimeMillis()
                )
            )
            VehicleRepositoryResult.Success(Unit)
        } catch (t: Throwable) {
            VehicleRepositoryResult.Error("تعذر تصعيد درجة العطل الحالي. لم يتم حذف أو فقد أي بيانات.", t)
        }
    }

    private fun severityRank(value: FaultSeverity): Int = when (value) {
        FaultSeverity.LOW -> 0
        FaultSeverity.MEDIUM -> 1
        FaultSeverity.HIGH -> 2
        FaultSeverity.CRITICAL -> 3
    }
}

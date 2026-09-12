package com.ahmed.carmanager.data.repository

import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.FuelRecordEntity
import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.OdometerRecordEntity
import com.ahmed.carmanager.data.local.model.OdometerSource
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Gasoline-focused decorator around the main operations repository.
 *
 * The old add-fuel path calculated a full-tank consumption from only the final refill. That is
 * wrong whenever partial refills exist between two full-tank anchors. This decorator keeps the
 * existing repository contract intact, delegates every unrelated operation unchanged, and owns
 * only the gasoline passenger-car recording rules until the underlying repository is refactored.
 */
class GasolineCarOperationsRepository(
    private val database: CarDatabase,
    private val authRepository: AuthRepository,
    private val delegate: CarOperationsRepository
) : CarOperationsRepository by delegate {

    override suspend fun addFuel(vehicleId: String, input: FuelInput): VehicleRepositoryResult<Unit> {
        val gasolineGrades = setOf(FuelType.GASOLINE_80, FuelType.GASOLINE_92, FuelType.GASOLINE_95)

        if (!input.amountPaid.isFinite() || input.amountPaid <= 0.0 ||
            !input.pricePerLiter.isFinite() || input.pricePerLiter <= 0.0
        ) {
            return VehicleRepositoryResult.Error("أدخل مبلغًا وسعر لتر صحيحين.")
        }
        if (!input.odometerKm.isFinite() || input.odometerKm < 0.0) {
            return VehicleRepositoryResult.Error("قراءة العداد غير صحيحة.")
        }
        if (input.date <= 0L || input.date > System.currentTimeMillis()) {
            return VehicleRepositoryResult.Error("تاريخ التموين لا يمكن أن يكون في المستقبل.")
        }
        if (input.expectedConsumptionL100 != null &&
            (!input.expectedConsumptionL100.isFinite() || input.expectedConsumptionL100 <= 0.0 || input.expectedConsumptionL100 > 100.0)
        ) {
            return VehicleRepositoryResult.Error("الاستهلاك المتوقع يجب أن يكون رقمًا منطقيًا أكبر من صفر.")
        }

        val uid = authRepository.currentUid()
            ?: return VehicleRepositoryResult.Error("انتهت جلسة الحساب. سجّل الدخول مرة أخرى.")
        val vehicle = database.vehicleDao().getById(vehicleId, uid)
            ?: return VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")

        // Non-gasoline support remains delegated for now. The product priority is gasoline passenger cars.
        if (vehicle.fuelType !in gasolineGrades) return delegate.addFuel(vehicleId, input)
        if (input.fuelType !in gasolineGrades) {
            return VehicleRepositoryResult.Error("هذه السيارة مسجلة كبنزين؛ اختر بنزين 80 أو 92 أو 95 للتعبئة.")
        }
        if (input.odometerKm < vehicle.currentOdometerKm) {
            return VehicleRepositoryResult.Error("قراءة العداد أقل من آخر قراءة مسجلة.")
        }

        // One immutable snapshot is enough for a normal foreground add operation. We re-check the
        // vehicle odometer inside the transaction before committing so other odometer writers cannot
        // make this record move the car backwards.
        val chronological = delegate.observeFuel(vehicleId)
            .first()
            .filter { !it.isDeleted }
            .sortedWith(compareBy<FuelRecordEntity> { it.fuelDate }.thenBy { it.createdAt })

        val latest = chronological.lastOrNull()
        if (latest != null && input.odometerKm < latest.odometerKm) {
            return VehicleRepositoryResult.Error("عداد التموين أقل من آخر عداد مسجل في سجل الوقود.")
        }
        if (latest != null && input.date < latest.fuelDate && !sameLocalDay(input.date, latest.fuelDate)) {
            return VehicleRepositoryResult.Error("إضافة تموين جديد بتاريخ أقدم من آخر تموين غير مسموحة. استخدم تعديل السجل القديم إذا كنت تصحح بيانات سابقة.")
        }

        // Date pickers can return midnight for “today” while an earlier quick-add record contains a
        // clock time. Preserve the visible calendar date but never persist the new odometer before a
        // previous record from that same day.
        val effectiveDate = if (latest != null && input.date < latest.fuelDate && sameLocalDay(input.date, latest.fuelDate)) {
            latest.fuelDate
        } else input.date

        return try {
            database.withTransaction {
                val currentVehicle = database.vehicleDao().getById(vehicleId, uid)
                    ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
                if (input.odometerKm < currentVehicle.currentOdometerKm) {
                    return@withTransaction VehicleRepositoryResult.Error("قراءة العداد أقل من آخر قراءة مسجلة.")
                }

                val liters = input.amountPaid / input.pricePerLiter
                if (!liters.isFinite() || liters <= 0.0) {
                    return@withTransaction VehicleRepositoryResult.Error("تعذر حساب كمية الوقود من المبلغ وسعر اللتر.")
                }

                val pendingKey = "__pending_fuel__"
                val cycleRows = chronological.map { record ->
                    FuelCycleMath.Row(
                        key = record.id,
                        odometerKm = record.odometerKm,
                        liters = record.liters,
                        amountPaid = record.amountPaid,
                        isFullTank = record.isFullTank
                    )
                } + FuelCycleMath.Row(
                    key = pendingKey,
                    odometerKm = input.odometerKm,
                    liters = liters,
                    amountPaid = input.amountPaid,
                    isFullTank = input.isFullTank
                )
                val derived = FuelCycleMath.recalculate(cycleRows)[pendingKey]
                    ?: return@withTransaction VehicleRepositoryResult.Error("تعذر حساب دورة الوقود الحالية.")

                val effectiveConsumption = derived.consumptionLitersPer100Km ?: input.expectedConsumptionL100
                val estimatedRange = FuelCycleMath.estimatedRangeKm(
                    isFullTank = input.isFullTank,
                    purchasedLiters = liters,
                    tankCapacityLiters = currentVehicle.tankCapacityLiters,
                    consumptionLitersPer100Km = effectiveConsumption
                )

                // Fuel and odometer history are two views of the same user action. Give both rows the
                // same creation timestamp so later edit/delete matching remains deterministic even
                // when two fills happen on the same date and odometer.
                val operationCreatedAt = System.currentTimeMillis()
                database.fuelDao().insert(
                    FuelRecordEntity(
                        vehicleId = vehicleId,
                        fuelDate = effectiveDate,
                        odometerKm = input.odometerKm,
                        fuelType = input.fuelType,
                        stationName = input.stationName.cleanOrNull(),
                        pricePerLiter = input.pricePerLiter,
                        amountPaid = input.amountPaid,
                        liters = liters,
                        isFullTank = input.isFullTank,
                        distanceSincePreviousKm = derived.distanceSincePreviousKm,
                        consumptionLitersPer100Km = derived.consumptionLitersPer100Km,
                        kmPerLiter = derived.kmPerLiter,
                        costPerKm = derived.costPerKm,
                        estimatedRangeKm = estimatedRange,
                        notes = input.notes.cleanOrNull(),
                        createdAt = operationCreatedAt,
                        updatedAt = operationCreatedAt
                    )
                )

                if (input.odometerKm > currentVehicle.currentOdometerKm) {
                    database.vehicleDao().update(
                        currentVehicle.copy(
                            currentOdometerKm = input.odometerKm,
                            updatedAt = operationCreatedAt
                        )
                    )
                }
                database.odometerDao().insert(
                    OdometerRecordEntity(
                        vehicleId = vehicleId,
                        odometerKm = input.odometerKm,
                        source = OdometerSource.FUEL,
                        recordedAt = effectiveDate,
                        notes = "تسجيل بنزين ${input.fuelType.gasolineLabel()}",
                        createdAt = operationCreatedAt,
                        updatedAt = operationCreatedAt
                    )
                )

                VehicleRepositoryResult.Success(Unit)
            }
        } catch (t: Throwable) {
            VehicleRepositoryResult.Error("تعذر حفظ التموين. لم يتم تغيير البيانات.", t)
        }
    }

    private fun sameLocalDay(first: Long, second: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = first }
        val b = Calendar.getInstance().apply { timeInMillis = second }
        return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun FuelType.gasolineLabel(): String = when (this) {
        FuelType.GASOLINE_80 -> "80"
        FuelType.GASOLINE_92 -> "92"
        FuelType.GASOLINE_95 -> "95"
        else -> ""
    }
}

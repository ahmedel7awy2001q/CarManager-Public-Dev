package com.ahmed.carmanager.ui

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.FuelRecordEntity
import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.OdometerSource
import com.ahmed.carmanager.data.repository.FuelCycleMath
import com.ahmed.carmanager.data.repository.FuelInput
import java.util.Calendar
import kotlinx.coroutines.launch

private val EDITABLE_GASOLINE_GRADES = setOf(
    FuelType.GASOLINE_80,
    FuelType.GASOLINE_92,
    FuelType.GASOLINE_95
)

/**
 * Edits an existing fuel entry without creating a duplicate and recalculates the fuel chain that
 * depends on chronological full-tank records. Raw user input remains the source of truth.
 */
fun CarManagerViewModel.updateFuelRecord(
    existing: FuelRecordEntity,
    visibleRecords: List<FuelRecordEntity>,
    input: FuelInput
) {
    val vehicle = selectedVehicle.value
    if (vehicle == null || vehicle.vehicleId != existing.vehicleId) {
        showMessage("تعذر تعديل التموين لأن المركبة الحالية لا تطابق السجل.")
        return
    }
    if (!input.amountPaid.isFinite() || input.amountPaid <= 0.0 ||
        !input.pricePerLiter.isFinite() || input.pricePerLiter <= 0.0 ||
        !input.odometerKm.isFinite() || input.odometerKm < 0.0
    ) {
        showMessage("راجع مبلغ التموين وسعر اللتر وقراءة العداد.")
        return
    }
    if (vehicle.fuelType in EDITABLE_GASOLINE_GRADES && input.fuelType !in EDITABLE_GASOLINE_GRADES) {
        showMessage("هذه السيارة مسجلة كبنزين؛ اختر بنزين 80 أو 92 أو 95.")
        return
    }
    if (input.expectedConsumptionL100 != null &&
        (!input.expectedConsumptionL100.isFinite() || input.expectedConsumptionL100 <= 0.0 || input.expectedConsumptionL100 > 100.0)
    ) {
        showMessage("الاستهلاك المتوقع يجب أن يكون أكبر من صفر ولا يتجاوز 100 لتر/100كم.")
        return
    }
    if (input.odometerKm > vehicle.currentOdometerKm) {
        showMessage("عداد التموين المعدّل أعلى من عداد المركبة الحالي. حدّث عداد المركبة أولًا إذا كانت القراءة صحيحة.")
        return
    }
    val now = System.currentTimeMillis()
    if (input.date <= 0L || input.date > now) {
        showMessage("تاريخ التموين لا يمكن أن يكون في المستقبل.")
        return
    }

    // The UI chooses a calendar date, not a clock time. Preserve the original record's time-of-day
    // where possible. If that old clock time would put a record selected for today in the future,
    // cap it at now rather than persisting a hidden future timestamp.
    val effectiveDate = minOf(mergeSelectedDateWithOriginalTime(input.date, existing.fuelDate), now)

    val app = getApplication<CarManagerApplication>()
    viewModelScope.launch {
        val result = runCatching {
            app.container.database.withTransaction {
                val editedLiters = input.amountPaid / input.pricePerLiter
                if (!editedLiters.isFinite() || editedLiters <= 0.0) error("INVALID_LITERS")

                val edited = existing.copy(
                    fuelDate = effectiveDate,
                    odometerKm = input.odometerKm,
                    fuelType = input.fuelType,
                    stationName = input.stationName?.trim()?.ifBlank { null },
                    pricePerLiter = input.pricePerLiter,
                    amountPaid = input.amountPaid,
                    liters = editedLiters,
                    isFullTank = input.isFullTank,
                    notes = input.notes?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )

                val ordered = visibleRecords
                    .filter { !it.isDeleted && it.vehicleId == existing.vehicleId }
                    .map { if (it.id == existing.id) edited else it }
                    .sortedWith(compareBy<FuelRecordEntity> { it.fuelDate }.thenBy { it.createdAt })

                if (ordered.none { it.id == existing.id }) error("Fuel record not found")
                validateOdometerSequence(ordered)

                // A fuel save creates an odometer-history row too. Keep that row aligned when the
                // receipt is corrected, otherwise reports can retain the old km/date after an edit.
                app.container.database.odometerDao().findMatchingSourceRecord(
                    vehicleId = existing.vehicleId,
                    source = OdometerSource.FUEL,
                    odometerKm = existing.odometerKm,
                    recordedAt = existing.fuelDate,
                    createdAt = existing.createdAt
                )?.let { reading ->
                    app.container.database.odometerDao().update(
                        reading.copy(
                            odometerKm = input.odometerKm,
                            recordedAt = effectiveDate,
                            notes = "تسجيل بنزين ${input.fuelType.gasolineGradeLabel()}",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                rebuildFuelChain(
                    database = app.container.database,
                    ordered = ordered,
                    tankCapacityLiters = vehicle.tankCapacityLiters,
                    editedId = existing.id,
                    editedExpectedConsumptionL100 = input.expectedConsumptionL100
                )
            }
        }

        if (result.isSuccess) {
            showMessage("تم تعديل التموين وإعادة حساب الاستهلاك المرتبط به.")
            runCatching { app.container.cloudBackupManager.uploadLatest() }
        } else {
            val message = when (result.exceptionOrNull()?.message) {
                "ODOMETER_SEQUENCE" -> "قراءة العداد لا تتوافق مع ترتيب سجلات التموين قبلها وبعدها. راجع التاريخ أو العداد."
                "INVALID_LITERS" -> "تعذر حساب كمية الوقود من المبلغ وسعر اللتر."
                else -> "تعذر تعديل التموين الآن. لم يتم تغيير السجل."
            }
            showMessage(message)
        }
    }
}

/**
 * Soft-deletes one fuel entry and rebuilds all derived consumption values that follow it.
 * Deleting a fuel receipt never moves the vehicle odometer backwards because the odometer may have
 * progressed from trips, GPS or another manual reading after this record was created.
 */
fun CarManagerViewModel.deleteFuelRecord(
    existing: FuelRecordEntity,
    visibleRecords: List<FuelRecordEntity>
) {
    val vehicle = selectedVehicle.value
    if (vehicle == null || vehicle.vehicleId != existing.vehicleId) {
        showMessage("تعذر حذف التموين لأن المركبة الحالية لا تطابق السجل.")
        return
    }
    if (existing.isDeleted) {
        showMessage("سجل التموين محذوف بالفعل.")
        return
    }

    val app = getApplication<CarManagerApplication>()
    viewModelScope.launch {
        val result = runCatching {
            app.container.database.withTransaction {
                val now = System.currentTimeMillis()
                app.container.database.fuelDao().update(
                    existing.copy(isDeleted = true, updatedAt = now)
                )

                // Soft-delete the odometer history row that originated from this fuel receipt. The
                // vehicle's current odometer itself is intentionally never moved backwards here.
                app.container.database.odometerDao().findMatchingSourceRecord(
                    vehicleId = existing.vehicleId,
                    source = OdometerSource.FUEL,
                    odometerKm = existing.odometerKm,
                    recordedAt = existing.fuelDate,
                    createdAt = existing.createdAt
                )?.let { reading ->
                    app.container.database.odometerDao().update(
                        reading.copy(isDeleted = true, updatedAt = now)
                    )
                }

                val remaining = visibleRecords
                    .filter { !it.isDeleted && it.vehicleId == existing.vehicleId && it.id != existing.id }
                    .sortedWith(compareBy<FuelRecordEntity> { it.fuelDate }.thenBy { it.createdAt })

                // Deletion cannot create a new odometer-order conflict, but validating protects the
                // transaction if legacy/imported data was already inconsistent.
                validateOdometerSequence(remaining)
                rebuildFuelChain(
                    database = app.container.database,
                    ordered = remaining,
                    tankCapacityLiters = vehicle.tankCapacityLiters,
                    editedId = null,
                    editedExpectedConsumptionL100 = null
                )
            }
        }

        if (result.isSuccess) {
            showMessage("تم حذف التموين وإعادة حساب الاستهلاك للسجلات المتبقية. لم يتم إنقاص عداد السيارة.")
            runCatching { app.container.cloudBackupManager.uploadLatest() }
        } else {
            val message = if (result.exceptionOrNull()?.message == "ODOMETER_SEQUENCE") {
                "لم يتم الحذف لأن ترتيب عدادات سجلات الوقود الحالية يحتاج مراجعة أولًا."
            } else {
                "تعذر حذف التموين الآن. لم يتم تغيير السجل."
            }
            showMessage(message)
        }
    }
}

private fun validateOdometerSequence(ordered: List<FuelRecordEntity>) {
    ordered.zipWithNext().forEach { (older, newer) ->
        if (newer.odometerKm < older.odometerKm) error("ODOMETER_SEQUENCE")
    }
}

/** Recomputes only derived fields; amount, liters, date, odometer and other raw user values stay intact. */
private suspend fun rebuildFuelChain(
    database: CarDatabase,
    ordered: List<FuelRecordEntity>,
    tankCapacityLiters: Double?,
    editedId: String?,
    editedExpectedConsumptionL100: Double?
) {
    val derivedById = FuelCycleMath.recalculate(
        ordered.map { raw ->
            FuelCycleMath.Row(
                key = raw.id,
                odometerKm = raw.odometerKm,
                liters = raw.liters,
                amountPaid = raw.amountPaid,
                isFullTank = raw.isFullTank
            )
        }
    )

    ordered.forEach { raw ->
        val derived = derivedById[raw.id] ?: error("FUEL_DERIVED")
        val estimatedConsumption = when {
            raw.id == editedId -> derived.consumptionLitersPer100Km ?: editedExpectedConsumptionL100
            derived.consumptionLitersPer100Km != null -> derived.consumptionLitersPer100Km
            else -> null
        }
        val recalculatedRange = estimatedConsumption?.let { consumption ->
            FuelCycleMath.estimatedRangeKm(
                isFullTank = raw.isFullTank,
                purchasedLiters = raw.liters,
                tankCapacityLiters = tankCapacityLiters,
                consumptionLitersPer100Km = consumption
            )
        }
        val estimatedRange = when {
            recalculatedRange != null -> recalculatedRange
            // An explicit edit that removed expected consumption must also remove the old manual
            // range when there is no measured full-to-full consumption left to support it.
            raw.id == editedId -> null
            // If this row previously carried measured consumption but the rebuilt sequence no longer
            // has a valid full-to-full cycle, its old range was derived from data that no longer exists.
            raw.consumptionLitersPer100Km != null -> null
            // Otherwise preserve a range that may have originated from a user-supplied expected
            // consumption on an untouched record that never had measured full-to-full consumption.
            else -> raw.estimatedRangeKm
        }

        database.fuelDao().update(
            raw.copy(
                distanceSincePreviousKm = derived.distanceSincePreviousKm,
                consumptionLitersPer100Km = derived.consumptionLitersPer100Km,
                kmPerLiter = derived.kmPerLiter,
                costPerKm = derived.costPerKm,
                estimatedRangeKm = estimatedRange,
                updatedAt = if (raw.id == editedId) System.currentTimeMillis() else raw.updatedAt
            )
        )
    }
}

private fun mergeSelectedDateWithOriginalTime(selectedDate: Long, originalDate: Long): Long {
    val selected = Calendar.getInstance().apply { timeInMillis = selectedDate }
    val original = Calendar.getInstance().apply { timeInMillis = originalDate }
    selected.set(Calendar.HOUR_OF_DAY, original.get(Calendar.HOUR_OF_DAY))
    selected.set(Calendar.MINUTE, original.get(Calendar.MINUTE))
    selected.set(Calendar.SECOND, original.get(Calendar.SECOND))
    selected.set(Calendar.MILLISECOND, original.get(Calendar.MILLISECOND))
    return selected.timeInMillis
}

private fun FuelType.gasolineGradeLabel(): String = when (this) {
    FuelType.GASOLINE_80 -> "80"
    FuelType.GASOLINE_92 -> "92"
    FuelType.GASOLINE_95 -> "95"
    else -> arLabel()
}

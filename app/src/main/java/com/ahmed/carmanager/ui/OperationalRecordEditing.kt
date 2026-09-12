package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.local.model.ExpenseEntity
import com.ahmed.carmanager.data.local.model.TripEntity
import com.ahmed.carmanager.data.local.model.TripType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.ExpenseInput
import com.ahmed.carmanager.data.repository.TripInput
import kotlinx.coroutines.launch

fun CarManagerViewModel.updateExpenseRecord(existing: ExpenseEntity, input: ExpenseInput) {
    val vehicle = selectedVehicle.value
    if (vehicle == null || existing.vehicleId != vehicle.vehicleId) {
        showMessage("تعذر تعديل المصروف لأن المركبة الحالية لا تطابق السجل.")
        return
    }
    if (input.amount <= 0.0 || input.descriptionAr.isBlank()) {
        showMessage("راجع وصف المصروف والمبلغ.")
        return
    }
    val app = getApplication<CarManagerApplication>()
    viewModelScope.launch {
        val result = runCatching {
            app.container.database.expenseDao().update(
                existing.copy(
                    expenseDate = input.date,
                    category = input.category,
                    amount = input.amount,
                    odometerKm = input.odometerKm,
                    descriptionAr = input.descriptionAr.trim(),
                    merchant = input.merchant?.trim()?.ifBlank { null },
                    notes = input.notes?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        if (result.isSuccess) {
            showMessage("تم تعديل المصروف دون إنشاء حركة جديدة.")
            runCatching { app.container.cloudBackupManager.uploadLatest() }
        } else showMessage("تعذر تعديل المصروف الآن؛ لم يتغير السجل.")
    }
}

fun CarManagerViewModel.updateTripRecord(existing: TripEntity, input: TripInput) {
    val vehicle = selectedVehicle.value
    if (vehicle == null || existing.vehicleId != vehicle.vehicleId) {
        showMessage("تعذر تعديل الرحلة لأن المركبة الحالية لا تطابق السجل.")
        return
    }
    if (input.distanceKm <= 0.0) {
        showMessage("المسافة يجب أن تكون أكبر من صفر.")
        return
    }
    val app = getApplication<CarManagerApplication>()
    viewModelScope.launch {
        val result = runCatching {
            val startOdo = input.startOdometerKm ?: existing.startOdometerKm
            val endOdo = input.endOdometerKm ?: startOdo?.plus(input.distanceKm) ?: existing.endOdometerKm
            app.container.database.tripDao().update(
                existing.copy(
                    tripType = input.tripType,
                    startTime = input.date,
                    startOdometerKm = startOdo,
                    endOdometerKm = endOdo,
                    distanceKm = input.distanceKm,
                    startAddress = input.startAddress?.trim()?.ifBlank { null },
                    endAddress = input.endAddress?.trim()?.ifBlank { null },
                    estimatedFuelLiters = if (input.distanceKm == existing.distanceKm) existing.estimatedFuelLiters else null,
                    fuelCost = input.fuelCost,
                    estimatedOperatingCost = input.operatingCost,
                    notes = input.notes?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        if (result.isSuccess) {
            showMessage("تم تعديل الرحلة. لم يغيّر التصحيح قراءة العداد الحالية تلقائيًا.")
            runCatching { app.container.cloudBackupManager.uploadLatest() }
        } else showMessage("تعذر تعديل الرحلة الآن؛ لم يتغير السجل.")
    }
}

@Composable
internal fun TripEditDialog(
    vehicle: VehicleEntity,
    trip: TripEntity,
    onDismiss: () -> Unit,
    onSave: (TripInput) -> Unit
) {
    var type by remember(trip.id) { mutableStateOf(trip.tripType) }
    var distance by remember(trip.id) { mutableStateOf(trimEditableNumber(trip.distanceKm)) }
    var start by remember(trip.id) { mutableStateOf(trip.startAddress.orEmpty()) }
    var end by remember(trip.id) { mutableStateOf(trip.endAddress.orEmpty()) }
    var fuelCost by remember(trip.id) { mutableStateOf(trip.fuelCost?.let(::trimEditableNumber).orEmpty()) }
    var totalCost by remember(trip.id) { mutableStateOf(trip.estimatedOperatingCost?.let(::trimEditableNumber).orEmpty()) }
    var notes by remember(trip.id) { mutableStateOf(trip.notes.orEmpty()) }
    var date by remember(trip.id) { mutableStateOf(trip.startTime) }
    val d = distance.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EditRoad, null) },
        title = { Text("تعديل الرحلة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 540.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                EnumSelector("نوع الرحلة", TripType.entries, type, { type = it }) { it.arLabel() }
                AppField(distance, { distance = numericInput(it) }, "المسافة (كم)")
                AppField(start, { start = it }, "من - اختياري")
                AppField(end, { end = it }, "إلى - اختياري")
                AppDateSelector("التاريخ", date, allowClear = false) { selected -> if (selected != null) date = selected }
                AppField(fuelCost, { fuelCost = numericInput(it) }, "تكلفة الوقود - اختياري")
                AppField(totalCost, { totalCost = numericInput(it) }, "تكلفة التشغيل - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Text(
                    "التعديل يصحح سجل الرحلة نفسه ولا يحرك عداد المركبة الحالي بصمت. لو كان الخطأ في قراءة العداد عدّلها من مصدرها الصحيح.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = d?.let { it > 0.0 } == true,
                onClick = {
                    onSave(
                        TripInput(
                            tripType = type,
                            distanceKm = d!!,
                            startOdometerKm = trip.startOdometerKm,
                            endOdometerKm = trip.startOdometerKm?.plus(d),
                            startAddress = start,
                            endAddress = end,
                            fuelCost = fuelCost.toDoubleOrNull(),
                            operatingCost = totalCost.toDoubleOrNull(),
                            notes = notes,
                            date = date
                        )
                    )
                }
            ) { Text("حفظ التعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun trimEditableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

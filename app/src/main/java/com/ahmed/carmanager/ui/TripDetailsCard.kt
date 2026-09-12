package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.gps.LiveTripStatsCalculator
import com.ahmed.carmanager.data.local.model.GpsReadingEntity
import com.ahmed.carmanager.data.local.model.TripEntity
import com.ahmed.carmanager.data.local.model.OdometerSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

@Composable
fun TripDetailsCard(
    trip: TripEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val container = remember(context) {
        (context.applicationContext as CarManagerApplication)
            .container
    }

    val readingsFlow = remember(
        trip.id,
        trip.gpsDeviceId,
        trip.startTime,
        trip.endTime
    ) {
        val deviceId = trip.gpsDeviceId

        if (deviceId != null) {
            container.database.gpsDao()
                .observeReadingsForWindow(
                    vehicleId = trip.vehicleId,
                    deviceId = deviceId,
                    startTimestamp = trip.startTime,
                    endTimestamp = trip.endTime ?: Long.MAX_VALUE
                )
        } else {
            flowOf(emptyList<GpsReadingEntity>())
        }
    }

    val readings by readingsFlow.collectAsState(
        initial = emptyList()
    )

    val stats = remember(trip, readings) {
        LiveTripStatsCalculator.calculate(trip, readings)
    }

    val maxSpeed = remember(readings) {
        readings.mapNotNull { it.speedKmh }
            .filter { it >= 0.0 }
            .maxOrNull()
    }

    val vehicles by container.vehicleRepository
        .observeVehicles()
        .collectAsState(initial = emptyList())

    val vehicle =
        vehicles.firstOrNull { it.vehicleId == trip.vehicleId }

    val suggestedOdometer =
        trip.startOdometerKm?.let { it + trip.distanceKm }

    var confirmOdometer by remember(trip.id) {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()

    suggestedOdometer?.let { target ->
        if (confirmOdometer) {
            AlertDialog(
                onDismissRequest = { confirmOdometer = false },
                title = { Text("\u062A\u062D\u062F\u064A\u062B \u0627\u0644\u0639\u062F\u0627\u062F\u061F") },
                text = { Text("\u0647\u0630\u0647 \u0642\u0631\u0627\u0621\u0629 \u0645\u0642\u062A\u0631\u062D\u0629 \u0645\u0646 \u0645\u0633\u0627\u0641\u0629 GPS \u0648\u0644\u064A\u0633\u062A \u0642\u0631\u0627\u0621\u0629 \u0641\u0639\u0644\u064A\u0629 \u0645\u0646 \u0639\u062F\u0627\u062F \u0627\u0644\u0645\u0631\u0643\u0628\u0629.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmOdometer = false
                        scope.launch {
                            container.operationsRepository.setOdometer(
                                trip.vehicleId,
                                target,
                                OdometerSource.TRIP,
                                "\u062A\u062D\u062F\u064A\u062B \u0645\u0642\u062A\u0631\u062D \u0645\u0646 \u0631\u062D\u0644\u0629 GPS"
                            )
                        }
                    }) { Text("\u062A\u062D\u062F\u064A\u062B") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmOdometer = false }) {
                        Text("\u0625\u0644\u063A\u0627\u0621")
                    }
                }
            )
        }
    }
    val seconds =
        (trip.durationSeconds ?: 0L)
            .coerceAtLeast(0L)

    val duration = String.format(
        "%02d:%02d:%02d",
        seconds / 3600,
        (seconds % 3600) / 60,
        seconds % 60
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestedOdometer?.let { target ->
                if (
                    trip.endTime != null &&
                    trip.distanceKm >= 0.1 &&
                    vehicle != null &&
                    target > vehicle.currentOdometerKm + 0.1
                ) {
                    OutlinedButton(
                        onClick = { confirmOdometer = true }
                    ) {
                        Text(
                            "\u0627\u0642\u062A\u0631\u0627\u062D \u062A\u062D\u062F\u064A\u062B \u0627\u0644\u0639\u062F\u0627\u062F: " +
                                String.format("%.0f", target) +
                                " \u0643\u0645"
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    if (trip.endTime == null)
                        "رحلة جارية"
                    else
                        "تفاصيل الرحلة",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    trip.tripType.arLabel(),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                "${formatKm(trip.distanceKm)} كم • $duration",
                fontWeight = FontWeight.Bold
            )

            if (trip.endTime != null) {
                Text(
                    "${formatDate(trip.startTime)} ← ${formatDate(trip.endTime)}",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "بدأت: ${formatDate(trip.startTime)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                TripMetric(
                    title = "المتوسط",
                    value = (if (readings.isNotEmpty()) stats.averageSpeedKmh else null)
                        ?.let { "${formatKm(it)} كم/س" }
                        ?: "—",
                    modifier = Modifier.weight(1f)
                )

                TripMetric(
                    title = "أقصى سرعة",
                    value = maxSpeed
                        ?.let { "${formatKm(it)} كم/س" }
                        ?: "—",
                    modifier = Modifier.weight(1f)
                )

                TripMetric(
                    title = "التوقفات",
                    value = if (readings.isNotEmpty()) stats.stopCount.toString() else "—",
                    modifier = Modifier.weight(1f)
                )
            }

            if (readings.isNotEmpty()) {
                Text(
                    "مسار الرحلة",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                TripRouteMap(
                    readings = readings
                )

                Text(
                    "نقاط المسار المحفوظة: ${readings.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trip.startLatitude != null &&
                trip.startLongitude != null
            ) {
                Text(
                    "البداية: ${formatKm(trip.startLatitude)}, ${formatKm(trip.startLongitude)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (trip.endLatitude != null &&
                trip.endLongitude != null
            ) {
                Text(
                    "النهاية: ${formatKm(trip.endLatitude)}, ${formatKm(trip.endLongitude)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!trip.startAddress.isNullOrBlank() ||
                !trip.endAddress.isNullOrBlank()
            ) {
                Text(
                    "${trip.startAddress ?: "—"} ← ${trip.endAddress ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (trip.gpsDeviceId == null) {
                Text(
                    "رحلة مسجلة يدويا — لا يوجد مسار GPS محفوظ.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (readings.isEmpty()) {
                Text(
                    "لا توجد قراءات GPS محفوظة لهذه الرحلة لذلك لا تتوفر إحصاءات السرعة والتوقفات.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TripMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                title,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}



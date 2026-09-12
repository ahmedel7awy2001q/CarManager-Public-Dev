package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

internal enum class TripPointTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripLocationPickerSheet(
    start: LatLng?,
    end: LatLng?,
    initialTarget: TripPointTarget,
    routeGeometry: List<LatLng>,
    onStartChanged: (LatLng) -> Unit,
    onEndChanged: (LatLng) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember { mutableStateOf(initialTarget) }
    val defaultEgypt = LatLng(26.8206, 30.8025)
    val camera = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(start ?: end ?: defaultEgypt, if (start != null || end != null) 11f else 5.5f)
    }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(loaded, start, end, routeGeometry) {
        if (!loaded) return@LaunchedEffect
        val points = if (routeGeometry.isNotEmpty()) routeGeometry else listOfNotNull(start, end)
        if (points.size >= 2) {
            val bounds = LatLngBounds.Builder().apply { points.forEach(::include) }.build()
            runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 90)) }
        } else if (points.size == 1) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("اختيار مواقع المشوار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Text("اختر ما تريد تحديده ثم اضغط على الخريطة. يمكنك تغيير البداية والوصول أكثر من مرة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = target == TripPointTarget.START, onClick = { target = TripPointTarget.START }, label = { Text("تحديد البداية") }, modifier = Modifier.weight(1f))
                FilterChip(selected = target == TripPointTarget.END, onClick = { target = TripPointTarget.END }, label = { Text("تحديد الوصول") }, modifier = Modifier.weight(1f))
            }
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
                GoogleMap(
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                    cameraPositionState = camera,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = true),
                    onMapLoaded = { loaded = true },
                    onMapClick = { point ->
                        if (target == TripPointTarget.START) {
                            onStartChanged(point)
                            target = TripPointTarget.END
                        } else onEndChanged(point)
                    }
                ) {
                    start?.let { Marker(state = rememberUpdatedMarkerState(it), title = "نقطة البداية") }
                    end?.let { Marker(state = rememberUpdatedMarkerState(it), title = "نقطة الوصول") }
                    if (routeGeometry.size > 1) Polyline(points = routeGeometry, width = 8f)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        start == null -> "حدد نقطة البداية"
                        end == null -> "حدد نقطة الوصول"
                        else -> "تم تحديد النقطتين"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = onDismiss, enabled = start != null && end != null, modifier = Modifier.fillMaxWidth()) { Text("تم") }
        }
    }
}

@Composable
private fun rememberUpdatedMarkerState(position: LatLng): MarkerState {
    val state = remember { MarkerState(position = position) }
    LaunchedEffect(position) { state.position = position }
    return state
}

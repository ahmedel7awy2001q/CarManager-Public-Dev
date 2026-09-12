package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.GpsReadingEntity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun TripRouteMap(
    readings: List<GpsReadingEntity>,
    modifier: Modifier = Modifier
) {
    val points = remember(readings) {
        readings.mapNotNull { reading ->
            val lat = reading.latitude
            val lon = reading.longitude

            if (lat != null &&
                lon != null &&
                lat in -90.0..90.0 &&
                lon in -180.0..180.0
            ) {
                LatLng(lat, lon)
            } else {
                null
            }
        }
    }

    if (points.isEmpty()) return

    val cameraPositionState =
        rememberCameraPositionState()

    val startPoint = points.first()
    val endPoint = points.last()

    val startMarkerState =
        remember(startPoint) {
            MarkerState(position = startPoint)
        }

    val endMarkerState =
        remember(endPoint) {
            MarkerState(position = endPoint)
        }

    var mapLoaded by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        mapLoaded,
        points
    ) {
        if (!mapLoaded) return@LaunchedEffect

        if (points.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    startPoint,
                    16f
                )
            )
        } else {
            val boundsBuilder =
                LatLngBounds.Builder()

            points.forEach {
                boundsBuilder.include(it)
            }

            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    72
                )
            )
        }
    }

    GoogleMap(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false
        ),
        onMapLoaded = {
            mapLoaded = true
        }
    ) {
        if (points.size > 1) {
            Polyline(
                points = points,
                width = 7f,
                geodesic = true
            )
        }

        Marker(
            state = startMarkerState,
            title = "بداية الرحلة"
        )

        if (points.size > 1) {
            Marker(
                state = endMarkerState,
                title = "نهاية الرحلة"
            )
        }
    }
}

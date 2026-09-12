package com.ahmed.carmanager.ui

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

internal data class RouteCalculation(
    val distanceKm: Double,
    val durationMinutes: Double?,
    val geometry: List<LatLng>,
    val provider: String
)

internal object TripRoutePlanner {
    suspend fun reverseGeocode(context: Context, point: LatLng): String? = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(context, Locale("ar", "EG"))
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(point.latitude, point.longitude, 1) { matches ->
                        val address = matches.firstOrNull()
                        val label = address?.getAddressLine(0)
                            ?: listOfNotNull(address?.subAdminArea, address?.adminArea).joinToString("، ").ifBlank { null }
                        if (cont.isActive) cont.resume(label)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1)
                    ?.firstOrNull()
                    ?.let { it.getAddressLine(0) ?: listOfNotNull(it.subAdminArea, it.adminArea).joinToString("، ") }
            }
        }.getOrNull()
    }

    suspend fun calculateDrivingRoute(context: Context, start: LatLng, end: LatLng): RouteCalculation = withContext(Dispatchers.IO) {
        googleDirections(context, start, end)
            ?: osrmRoute(start, end)
            ?: throw IllegalStateException("تعذر حساب مسافة الطريق. يمكنك إدخال المسافة يدويًا.")
    }

    private fun googleDirections(context: Context, start: LatLng, end: LatLng): RouteCalculation? = runCatching {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            ?.takeUnless { it.isBlank() || it == "DEFAULT_API_KEY" }
            ?: return null
        val origin = "${start.latitude},${start.longitude}"
        val destination = "${end.latitude},${end.longitude}"
        val url = URL(
            "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${URLEncoder.encode(origin, "UTF-8")}" +
                "&destination=${URLEncoder.encode(destination, "UTF-8")}" +
                "&mode=driving&alternatives=false&language=ar&region=eg&key=${URLEncoder.encode(key, "UTF-8")}" 
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CarManager/1.0 route")
            GoogleMapsWebServiceAuth.apply(context, this)
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optString("status") != "OK") return null
            val route = root.getJSONArray("routes").optJSONObject(0) ?: return null
            val legs = route.optJSONArray("legs") ?: return null
            var meters = 0.0
            var seconds = 0.0
            for (i in 0 until legs.length()) {
                val leg = legs.optJSONObject(i) ?: continue
                meters += leg.optJSONObject("distance")?.optDouble("value") ?: 0.0
                seconds += leg.optJSONObject("duration")?.optDouble("value") ?: 0.0
            }
            if (meters <= 0.0) return null
            val encoded = route.optJSONObject("overview_polyline")?.optString("points").orEmpty()
            RouteCalculation(
                distanceKm = meters / 1000.0,
                durationMinutes = seconds.takeIf { it > 0.0 }?.div(60.0),
                geometry = if (encoded.isBlank()) listOf(start, end) else decodePolyline(encoded),
                provider = "Google Maps"
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun osrmRoute(start: LatLng, end: LatLng): RouteCalculation? = runCatching {
        val url = URL(
            "https://router.project-osrm.org/route/v1/driving/" +
                "${start.longitude},${start.latitude};${end.longitude},${end.latitude}" +
                "?overview=full&geometries=geojson&steps=false"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CarManager/1.0 route-fallback")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optString("code") != "Ok") return null
            val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
            val meters = route.optDouble("distance", 0.0)
            if (meters <= 0.0) return null
            val seconds = route.optDouble("duration", 0.0)
            val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates")
            val geometry = buildList {
                if (coords != null) {
                    for (i in 0 until coords.length()) {
                        val pair = coords.optJSONArray(i) ?: continue
                        if (pair.length() >= 2) add(LatLng(pair.optDouble(1), pair.optDouble(0)))
                    }
                }
            }.ifEmpty { listOf(start, end) }
            RouteCalculation(
                distanceKm = meters / 1000.0,
                durationMinutes = seconds.takeIf { it > 0.0 }?.div(60.0),
                geometry = geometry,
                provider = "OSRM / OpenStreetMap"
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dLat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            val dLng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dLng
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}

package com.ahmed.carmanager.ui

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

internal data class TripPlaceSuggestion(
    val id: String,
    val primaryText: String,
    val secondaryText: String,
    val coordinate: LatLng?,
    val provider: String,
    val providerPlaceId: String? = null
) {
    val displayLabel: String
        get() = listOf(primaryText, secondaryText).filter { it.isNotBlank() }.joinToString("، ")
}

internal data class TripPlaceSelection(
    val label: String,
    val coordinate: LatLng,
    val provider: String
)

/**
 * Search service used by the trip quotation flow.
 *
 * Design goals:
 * - Prefer Google's place index when the configured Maps key can call Places APIs.
 * - Gracefully fall back to OpenStreetMap/Nominatim instead of blocking trip pricing.
 * - Bias Egypt without hard-locking results to Egypt, because work trips can cross borders.
 * - Never infer a road distance from text: a suggestion must first resolve to coordinates.
 */
internal object TripPlaceSearchEngine {
    fun newSessionToken(): String = UUID.randomUUID().toString()

    suspend fun search(
        context: Context,
        query: String,
        sessionToken: String,
        near: LatLng? = null,
        limit: Int = 6
    ): List<TripPlaceSuggestion> = withContext(Dispatchers.IO) {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) return@withContext emptyList()

        val googleEgypt = googleAutocomplete(context, clean, sessionToken, near, limit, egyptOnly = true)
        if (googleEgypt.isNotEmpty()) return@withContext googleEgypt
        val googleGlobal = googleAutocomplete(context, clean, sessionToken, near, limit, egyptOnly = false)
        if (googleGlobal.isNotEmpty()) return@withContext googleGlobal
        val osmEgypt = nominatimSearch(clean, near, limit, egyptHint = true)
        if (osmEgypt.isNotEmpty()) return@withContext osmEgypt
        nominatimSearch(clean, near, limit, egyptHint = false)
    }

    suspend fun resolve(
        context: Context,
        suggestion: TripPlaceSuggestion,
        sessionToken: String
    ): TripPlaceSelection? = withContext(Dispatchers.IO) {
        suggestion.coordinate?.let {
            return@withContext TripPlaceSelection(suggestion.displayLabel, it, suggestion.provider)
        }
        val placeId = suggestion.providerPlaceId ?: return@withContext null
        googlePlaceDetails(context, placeId, sessionToken)
    }

    private fun mapsApiKey(context: Context): String? = runCatching {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            ?.trim()
            ?.takeUnless { it.isBlank() || it == "DEFAULT_API_KEY" }
    }.getOrNull()

    private fun googleAutocomplete(
        context: Context,
        query: String,
        sessionToken: String,
        near: LatLng?,
        limit: Int,
        egyptOnly: Boolean
    ): List<TripPlaceSuggestion> = runCatching {
        val key = mapsApiKey(context) ?: return emptyList()
        val locationBias = near?.let { "&location=${it.latitude},${it.longitude}&radius=80000" }.orEmpty()
        val url = URL(
            "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                "?input=${URLEncoder.encode(query, "UTF-8")}" +
                "&language=ar&region=eg" +
                (if (egyptOnly) "&components=country:eg" else "") +
                "&sessiontoken=${URLEncoder.encode(sessionToken, "UTF-8")}" +
                locationBias +
                "&key=${URLEncoder.encode(key, "UTF-8")}" 
        )
        val body = httpGet(url, userAgent = "CarManager/1.0 place-search", context = context) ?: return emptyList()
        val root = JSONObject(body)
        if (root.optString("status") !in setOf("OK", "ZERO_RESULTS")) return emptyList()
        val predictions = root.optJSONArray("predictions") ?: return emptyList()
        buildList {
            for (i in 0 until predictions.length().coerceAtMost(limit)) {
                val item = predictions.optJSONObject(i) ?: continue
                val formatting = item.optJSONObject("structured_formatting")
                val primary = formatting?.optString("main_text").orEmpty().ifBlank { item.optString("description") }
                val secondary = formatting?.optString("secondary_text").orEmpty()
                val id = item.optString("place_id")
                if (primary.isBlank() || id.isBlank()) continue
                add(
                    TripPlaceSuggestion(
                        id = "google:$id",
                        primaryText = primary,
                        secondaryText = secondary,
                        coordinate = null,
                        provider = "Google Places",
                        providerPlaceId = id
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    private fun googlePlaceDetails(
        context: Context,
        placeId: String,
        sessionToken: String
    ): TripPlaceSelection? = runCatching {
        val key = mapsApiKey(context) ?: return null
        val url = URL(
            "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=${URLEncoder.encode(placeId, "UTF-8")}" +
                "&fields=formatted_address,name,geometry&language=ar&region=eg" +
                "&sessiontoken=${URLEncoder.encode(sessionToken, "UTF-8")}" +
                "&key=${URLEncoder.encode(key, "UTF-8")}" 
        )
        val body = httpGet(url, userAgent = "CarManager/1.0 place-details", context = context) ?: return null
        val root = JSONObject(body)
        if (root.optString("status") != "OK") return null
        val result = root.optJSONObject("result") ?: return null
        val location = result.optJSONObject("geometry")?.optJSONObject("location") ?: return null
        val lat = location.optDouble("lat", Double.NaN)
        val lng = location.optDouble("lng", Double.NaN)
        if (!lat.isFinite() || !lng.isFinite()) return null
        val label = result.optString("formatted_address")
            .ifBlank { result.optString("name") }
            .ifBlank { "$lat, $lng" }
        TripPlaceSelection(label, LatLng(lat, lng), "Google Places")
    }.getOrNull()

    private fun nominatimSearch(query: String, near: LatLng?, limit: Int, egyptHint: Boolean): List<TripPlaceSuggestion> = runCatching {
        val q = if (!egyptHint || query.contains("مصر", ignoreCase = true) || query.lowercase(Locale.ROOT).contains("egypt")) query else "$query, مصر"
        val viewbox = near?.let {
            val west = it.longitude - 1.2
            val east = it.longitude + 1.2
            val north = it.latitude + 1.0
            val south = it.latitude - 1.0
            "&viewbox=$west,$north,$east,$south&bounded=0"
        }.orEmpty()
        val url = URL(
            "https://nominatim.openstreetmap.org/search" +
                "?q=${URLEncoder.encode(q, "UTF-8")}&format=jsonv2&addressdetails=1" +
                "&accept-language=ar&limit=${(limit * 2).coerceIn(4, 12)}$viewbox"
        )
        val body = httpGet(url, userAgent = "CarManager/1.0 (trip place search)") ?: return emptyList()
        val array = org.json.JSONArray(body)
        val wanted = normalizePlaceQuery(query)
        val ranked = mutableListOf<Pair<Double, TripPlaceSuggestion>>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            val display = item.optString("display_name")
            if (display.isBlank()) continue
            val name = item.optString("name").ifBlank { display.substringBefore(',') }
            val secondary = display.removePrefix(name).trim().trimStart('،', ',').trim()
            val osmType = item.optString("osm_type")
            val osmId = item.optString("osm_id")
            val addressType = item.optString("addresstype").lowercase(Locale.ROOT)
            val featureType = item.optString("type").lowercase(Locale.ROOT)
            val countryCode = item.optJSONObject("address")?.optString("country_code").orEmpty().lowercase(Locale.ROOT)
            val normalizedName = normalizePlaceQuery(name)
            val exactScore = when {
                normalizedName == wanted -> 120.0
                normalizedName.startsWith(wanted) -> 70.0
                normalizedName.contains(wanted) -> 35.0
                else -> 0.0
            }
            val administrativeScore = when (addressType) {
                "city", "town", "state", "region", "governorate", "administrative", "municipality" -> 55.0
                "village", "county" -> 35.0
                "road", "residential", "street" -> -25.0
                else -> if (featureType in setOf("city", "town", "administrative")) 35.0 else 0.0
            }
            val egyptScore = if (egyptHint && countryCode == "eg") 45.0 else 0.0
            val importanceScore = item.optDouble("importance", 0.0).coerceAtLeast(0.0) * 25.0
            ranked += (exactScore + administrativeScore + egyptScore + importanceScore) to TripPlaceSuggestion(
                id = "osm:$osmType:$osmId",
                primaryText = name,
                secondaryText = secondary,
                coordinate = LatLng(lat, lon),
                provider = "OpenStreetMap"
            )
        }
        ranked.sortedByDescending { it.first }.map { it.second }.distinctBy { it.id }.take(limit.coerceIn(1, 8))
    }.getOrElse { emptyList() }

    private fun normalizePlaceQuery(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")

    private fun httpGet(url: URL, userAgent: String, context: Context? = null): String? {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 9_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
            if (context != null) GoogleMapsWebServiceAuth.apply(context, this)
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal object TripPlaceHistoryStore {
    private const val PREFS = "trip_place_history_v1"
    private const val KEY = "places"
    private const val MAX = 10

    fun recent(context: Context): List<TripPlaceSelection> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val label = item.optString("label")
                    val lat = item.optDouble("lat", Double.NaN)
                    val lng = item.optDouble("lng", Double.NaN)
                    if (label.isNotBlank() && lat.isFinite() && lng.isFinite()) {
                        add(TripPlaceSelection(label, LatLng(lat, lng), item.optString("provider", "السجل")))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun remember(context: Context, selection: TripPlaceSelection) {
        val existing = recent(context).filterNot {
            it.label.equals(selection.label, ignoreCase = true) ||
                (kotlin.math.abs(it.coordinate.latitude - selection.coordinate.latitude) < 0.0002 &&
                    kotlin.math.abs(it.coordinate.longitude - selection.coordinate.longitude) < 0.0002)
        }
        val merged = (listOf(selection) + existing).take(MAX)
        val array = org.json.JSONArray()
        merged.forEach {
            array.put(JSONObject().apply {
                put("label", it.label)
                put("lat", it.coordinate.latitude)
                put("lng", it.coordinate.longitude)
                put("provider", it.provider)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

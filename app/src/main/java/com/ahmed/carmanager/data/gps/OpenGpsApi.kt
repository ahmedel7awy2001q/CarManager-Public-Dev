package com.ahmed.carmanager.data.gps

import com.ahmed.carmanager.data.local.model.AccStatus
import com.ahmed.carmanager.data.local.model.GpsConnectionStatus
import com.ahmed.carmanager.data.local.model.GpsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest

internal data class OpenGpsTrack(
    val imei: String,
    val timestampSeconds: Long,
    val latitude: Double?,
    val longitude: Double?,
    val speedKmh: Double?,
    val courseDegrees: Double?,
    val accStatus: AccStatus,
    val mileageKm: Double?,
    val todayMileageKm: Double?,
    val externalVoltage: Double?,
    val connectionStatus: GpsConnectionStatus
)

internal class OpenGpsApi {
    suspend fun authorize(provider: GpsProvider, credentials: GpsCredentials): String = withContext(Dispatchers.IO) {
        val time = System.currentTimeMillis() / 1000L
        val signature = md5(md5(credentials.password) + time)
        val url = "${baseUrl(provider)}/api/authorization?time=$time&account=${encode(credentials.account)}&signature=$signature"
        val root = request(url)
        checkApi(root)
        root.getJSONObject("record").getString("access_token")
    }

    suspend fun track(provider: GpsProvider, accessToken: String, imei: String): OpenGpsTrack = withContext(Dispatchers.IO) {
        val url = "${baseUrl(provider)}/api/track?access_token=${encode(accessToken)}&imeis=${encode(imei)}"
        val root = request(url)
        checkApi(root)
        val array = root.optJSONArray("record") ?: error("لم يرسل خادم GPS بيانات للجهاز.")
        if (array.length() == 0) error("الجهاز غير موجود في حساب GPS.")
        val item = array.getJSONObject(0)
        val mileageMeters = item.optLong("mileage", -1L)
        val todayMeters = item.optLong("todaymileage", -1L)
        val dataStatus = item.optInt("datastatus", -1)
        val acc = when (item.optInt("accstatus", -1)) { 1 -> AccStatus.ON; 0 -> AccStatus.OFF; else -> AccStatus.UNKNOWN }
        OpenGpsTrack(
            imei = item.optString("imei", imei),
            timestampSeconds = listOf(item.optLong("gpstime", 0L), item.optLong("systemtime", 0L), item.optLong("servertime", 0L)).firstOrNull { it > 0 } ?: (System.currentTimeMillis() / 1000L),
            latitude = item.optDoubleOrNull("latitude"),
            longitude = item.optDoubleOrNull("longitude"),
            speedKmh = item.optDoubleOrNull("speed"),
            courseDegrees = item.optDoubleOrNull("course"),
            accStatus = acc,
            mileageKm = mileageMeters.takeIf { it >= 0 }?.div(1000.0),
            todayMileageKm = todayMeters.takeIf { it >= 0 }?.div(1000.0),
            externalVoltage = item.optString("externalpower", "").toDoubleOrNull(),
            connectionStatus = if (dataStatus == 2) GpsConnectionStatus.ONLINE else GpsConnectionStatus.OFFLINE
        )
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "CarManager-Android/0.2")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("تعذر الاتصال بخادم GPS (HTTP $code).")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun checkApi(root: JSONObject) {
        val code = root.optInt("code", -1)
        if (code != 0) {
            val message = root.optString("message", "").ifBlank { "رمز الخطأ $code" }
            error("رفض خادم GPS الطلب: $message")
        }
    }

    private fun baseUrl(provider: GpsProvider): String = when (provider) {
        GpsProvider.ITRACK -> "https://api.itrack.top"
        GpsProvider.ETRACK -> "https://api.etrack.vip"
        else -> error("هذا المزود لا يدعم المزامنة المباشرة.")
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { get(key).toString().toDouble() }.getOrNull()
    }
}

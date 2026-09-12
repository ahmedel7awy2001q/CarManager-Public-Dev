package com.ahmed.carmanager.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Adds Android application-restriction headers to direct Google Maps Platform web-service calls.
 *
 * Keeping the API key Android-restricted is safer than shipping an unrestricted web-service key.
 * Google accepts these headers for supported Maps Platform web services when Android application
 * restrictions are configured for the package/signing certificate pair.
 */
internal object GoogleMapsWebServiceAuth {
    fun apply(context: Context, connection: HttpURLConnection) {
        connection.setRequestProperty("X-Android-Package", context.packageName)
        signingSha1(context)?.let { connection.setRequestProperty("X-Android-Cert", it) }
    }

    private fun signingSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return@runCatching null
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures
                ?.firstOrNull()
                ?.toByteArray()
        } ?: return@runCatching null

        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02X".format(it) }
    }.getOrNull()
}

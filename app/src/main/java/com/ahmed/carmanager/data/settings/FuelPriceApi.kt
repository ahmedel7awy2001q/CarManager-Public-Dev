package com.ahmed.carmanager.data.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal data class FuelPriceSnapshot(
    val gasoline80: Double,
    val gasoline92: Double,
    val gasoline95: Double,
    val diesel: Double,
    val source: String,
    val fetchedAt: Long
)

internal class FuelPriceApi {

    suspend fun fetchOfficialEgyptPrices(): FuelPriceSnapshot =
        withContext(Dispatchers.IO) {
            val html = request(OFFICIAL_URL)

            val gasoline80 = extractPrice(html, "بنزين 80")
            val gasoline92 = extractPrice(html, "بنزين 92")
            val gasoline95 = extractPrice(html, "بنزين 95")
            val diesel = extractPrice(html, "سولار")

            require(
                gasoline80 > 0 &&
                        gasoline92 > 0 &&
                        gasoline95 > 0 &&
                        diesel > 0
            ) {
                "تعذر قراءة جميع أسعار الوقود الرسمية."
            }

            FuelPriceSnapshot(
                gasoline80 = gasoline80,
                gasoline92 = gasoline92,
                gasoline95 = gasoline95,
                diesel = diesel,
                source = OFFICIAL_URL,
                fetchedAt = System.currentTimeMillis()
            )
        }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 CarManager-Android"
        )
        connection.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml"
        )

        return try {
            val code = connection.responseCode
            val stream =
                if (code in 200..299)
                    connection.inputStream
                else
                    connection.errorStream

            val text = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                error("تعذر الاتصال بمصدر أسعار الوقود الرسمي (HTTP $code).")
            }

            if (text.isBlank()) {
                error("مصدر أسعار الوقود أعاد استجابة فارغة.")
            }

            text
        } finally {
            connection.disconnect()
        }
    }

    private fun extractPrice(
        html: String,
        label: String
    ): Double {
        val normalized = html
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("\u00A0", " ")

        val escapedLabel = Regex.escape(label)

        val priceRegex = Regex(
            """<td[^>]*>\s*$escapedLabel\s*</td>\s*<td[^>]*>\s*(\d+(?:\.\d+)?)\s*</td>""",
            setOf(
                RegexOption.IGNORE_CASE,
                RegexOption.DOT_MATCHES_ALL
            )
        )

        val price = priceRegex
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

        return price
            ?.takeIf { it in 1.0..100.0 }
            ?: error("تعذر قراءة سعر $label من المصدر الرسمي.")
    }
    private companion object {
        const val OFFICIAL_URL =
            "https://www.petroleum.gov.eg/ar-eg/Pages/HomePage.aspx"
    }
}
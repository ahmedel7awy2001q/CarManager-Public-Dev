package com.ahmed.carmanager.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ahmed.carmanager.data.local.model.ExpenseEntity
import com.ahmed.carmanager.data.local.model.FuelRecordEntity
import com.ahmed.carmanager.data.local.model.MaintenanceRecordEntity
import com.ahmed.carmanager.data.local.model.TripEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal data class VehicleReportExportData(
    val vehicle: VehicleEntity,
    val periodLabel: String,
    val totalCost: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val otherCost: Double,
    val distanceKm: Double?,
    val costPerKm: Double?,
    val consumptionL100: Double?,
    val fuel: List<FuelRecordEntity>,
    val maintenance: List<MaintenanceRecordEntity>,
    val expenses: List<ExpenseEntity>,
    val trips: List<TripEntity>
)

internal object VehicleReportExporter {
    fun exportCsv(context: Context, uri: Uri, data: VehicleReportExportData) {
        context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
            OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                fun row(vararg cells: Any?) {
                    writer.write(cells.joinToString(",") { csvCell(it?.toString().orEmpty()) })
                    writer.write("\r\n")
                }

                row("CarManager - تقرير السيارة")
                row("السيارة", data.vehicle.displayName ?: "${data.vehicle.brand} ${data.vehicle.model}")
                row("الفترة", data.periodLabel)
                row("العداد الحالي", data.vehicle.currentOdometerKm)
                row("إجمالي التكلفة", data.totalCost)
                row("الوقود", data.fuelCost)
                row("الصيانة", data.maintenanceCost)
                row("مصاريف أخرى", data.otherCost)
                row("المسافة", data.distanceKm)
                row("تكلفة الكيلومتر", data.costPerKm)
                row("متوسط الاستهلاك لتر/100كم", data.consumptionL100)
                row()

                row("الوقود")
                row("التاريخ", "العداد", "اللترات", "سعر اللتر", "المبلغ", "الاستهلاك ل/100كم", "المحطة")
                data.fuel.sortedByDescending { it.fuelDate }.forEach {
                    row(formatDate(it.fuelDate), it.odometerKm, it.liters, it.pricePerLiter, it.amountPaid, it.consumptionLitersPer100Km, it.stationName)
                }
                row()

                row("الصيانة")
                row("التاريخ", "البند", "العداد", "التكلفة", "مركز الخدمة", "رقم الفاتورة")
                data.maintenance.sortedByDescending { it.serviceDate }.forEach {
                    row(formatDate(it.serviceDate), it.titleAr, it.odometerKm, it.totalCost, it.serviceCenter, it.invoiceNumber)
                }
                row()

                row("المصاريف")
                row("التاريخ", "التصنيف", "الوصف", "المبلغ", "الجهة")
                data.expenses.sortedByDescending { it.expenseDate }.forEach {
                    row(formatDate(it.expenseDate), it.category.arLabel(), it.descriptionAr, it.amount, it.merchant)
                }
                row()

                row("الرحلات")
                row("التاريخ", "النوع", "المسافة كم", "من", "إلى", "تكلفة الوقود", "تكلفة التشغيل")
                data.trips.sortedByDescending { it.startTime }.forEach {
                    row(formatDate(it.startTime), it.tripType.arLabel(), it.distanceKm, it.startAddress, it.endAddress, it.fuelCost, it.estimatedOperatingCost)
                }
            }
        } ?: error("تعذر إنشاء ملف CSV")
    }

    fun exportPdf(context: Context, uri: Uri, data: VehicleReportExportData) {
        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 42f
            val right = pageWidth - margin
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(15, 23, 42)
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            var pageNumber = 0
            var y = 0f

            fun newPage() {
                page?.let(document::finishPage)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                y = 58f
            }

            fun line(text: String, size: Float = 12f, bold: Boolean = false, gap: Float = 22f) {
                if (y > pageHeight - 55f) newPage()
                paint.textSize = size
                paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
                page!!.canvas.drawText(text, right, y, paint)
                y += gap
            }

            fun divider() {
                if (y > pageHeight - 55f) newPage()
                val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(203, 213, 225)
                    strokeWidth = 1f
                }
                page!!.canvas.drawLine(margin, y, right, y, dividerPaint)
                y += 16f
            }

            newPage()
            line("CarManager", 22f, true, 30f)
            line("تقرير السيارة", 18f, true, 28f)
            line(data.vehicle.displayName ?: "${data.vehicle.brand} ${data.vehicle.model}", 15f, true)
            line("${data.vehicle.year}  •  العداد ${formatKm(data.vehicle.currentOdometerKm)} كم")
            line("الفترة: ${data.periodLabel}")
            divider()
            line("الملخص المالي", 15f, true, 27f)
            line("إجمالي التكلفة: ${formatMoney(data.totalCost)}", 14f, true)
            line("الوقود: ${formatMoney(data.fuelCost)}")
            line("الصيانة: ${formatMoney(data.maintenanceCost)}")
            line("مصاريف أخرى: ${formatMoney(data.otherCost)}")
            data.distanceKm?.let { line("المسافة القابلة للحساب: ${formatKm(it)} كم") }
            data.costPerKm?.let { line("تكلفة الكيلومتر المسجلة: ${formatKm(it)} ج/كم") }
            data.consumptionL100?.let { line("متوسط الاستهلاك: ${formatKm(it)} لتر/100كم") }
            divider()

            line("أعلى سجلات الصيانة", 15f, true, 27f)
            if (data.maintenance.isEmpty()) line("لا توجد صيانة مسجلة في هذه الفترة.")
            data.maintenance.sortedByDescending { it.totalCost }.take(8).forEach {
                line("${formatMoney(it.totalCost)}  —  ${it.titleAr}  —  ${formatDate(it.serviceDate)}")
            }
            divider()

            line("أحدث عمليات الوقود", 15f, true, 27f)
            if (data.fuel.isEmpty()) line("لا توجد عمليات وقود مسجلة في هذه الفترة.")
            data.fuel.sortedByDescending { it.fuelDate }.take(8).forEach {
                val consumption = it.consumptionLitersPer100Km?.let { value -> " • ${formatKm(value)} ل/100" }.orEmpty()
                line("${formatMoney(it.amountPaid)} • ${formatLiters(it.liters)}$consumption • ${formatDate(it.fuelDate)}")
            }
            divider()

            line("ملاحظة", 13f, true)
            line("الأرقام مبنية على البيانات المسجلة داخل التطبيق، ولا تمثل تشخيصًا ميكانيكيًا أو تقديرًا لمسافة غير مسجلة.", 10f, false, 18f)

            page?.let(document::finishPage)
            page = null
            context.contentResolver.openOutputStream(uri, "w")?.use(document::writeTo)
                ?: error("تعذر إنشاء ملف PDF")
        } finally {
            page?.let { runCatching { document.finishPage(it) } }
            document.close()
        }
    }

    private fun csvCell(value: String): String {
        val safe = value.replace("\"", "\"\"")
        return "\"$safe\""
    }
}

package com.ahmed.carmanager.domain

import com.ahmed.carmanager.data.local.model.DocumentType
import com.ahmed.carmanager.data.local.model.ExpenseEntity
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.FaultStatus
import com.ahmed.carmanager.data.local.model.FuelRecordEntity
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.MaintenanceRecordEntity
import com.ahmed.carmanager.data.local.model.ReminderEntity
import com.ahmed.carmanager.data.local.model.TripEntity
import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import java.util.Calendar
import kotlin.math.abs

/**
 * Data-driven observations only. This engine never claims to diagnose the vehicle mechanically.
 * It compares the user's own history, current maintenance plan and saved records.
 */
enum class InsightLevel { GOOD, INFO, ATTENTION, URGENT }

data class SmartInsight(
    val titleAr: String,
    val detailAr: String,
    val level: InsightLevel,
    val key: String
)

data class VehicleSmartSummary(
    val monthSpend: Double,
    val previousMonthSpend: Double,
    val spendChangePercent: Double?,
    val monthDistanceKm: Double,
    val averageConsumptionL100: Double?,
    val recentConsumptionL100: Double?,
    val fuelDeviationPercent: Double?,
    val overdueMaintenanceCount: Int,
    val dueSoonMaintenanceCount: Int,
    val openImportantFaultCount: Int,
    val documentsNeedingAttention: Int,
    val estimatedKnownUpcomingMaintenanceCost: Double,
    val upcomingMaintenanceMissingPriceCount: Int,
    val dataConfidence: Int,
    val insights: List<SmartInsight>
)

object SmartVehicleInsights {
    fun analyze(
        vehicle: VehicleEntity,
        plans: List<MaintenancePlanEntity>,
        maintenance: List<MaintenanceRecordEntity>,
        fuel: List<FuelRecordEntity>,
        expenses: List<ExpenseEntity>,
        trips: List<TripEntity>,
        reminders: List<ReminderEntity>,
        faults: List<FaultRecordEntity>,
        documents: List<VehicleDocumentEntity>,
        now: Long = System.currentTimeMillis()
    ): VehicleSmartSummary {
        val monthStart = startOfMonth(now)
        val previousMonthStart = startOfPreviousMonth(now)
        val activeMaintenance = maintenance.filter { !it.isDeleted }
        val activeFuel = fuel.filter { !it.isDeleted }
        val activeExpenses = expenses.filter { !it.isDeleted }
        val activeTrips = trips.filter { !it.isDeleted }

        val monthSpend = activeFuel.filter { it.fuelDate in monthStart..now }.sumOf { it.amountPaid } +
            activeMaintenance.filter { it.serviceDate in monthStart..now }.sumOf { it.totalCost } +
            activeExpenses.filter { it.expenseDate in monthStart..now }.sumOf { it.amount }

        val previousMonthSpend = activeFuel.filter { it.fuelDate in previousMonthStart until monthStart }.sumOf { it.amountPaid } +
            activeMaintenance.filter { it.serviceDate in previousMonthStart until monthStart }.sumOf { it.totalCost } +
            activeExpenses.filter { it.expenseDate in previousMonthStart until monthStart }.sumOf { it.amount }

        val spendChange = previousMonthSpend.takeIf { it > 0.0 }?.let { (monthSpend - it) / it * 100.0 }
        val monthDistance = activeTrips.filter { it.startTime in monthStart..now }.sumOf { it.distanceKm.coerceAtLeast(0.0) }

        val consumptionRecords = activeFuel
            .filter { it.consumptionLitersPer100Km != null && it.consumptionLitersPer100Km > 0.0 }
            .sortedByDescending { it.fuelDate }

        val averageConsumption = consumptionRecords.takeIf { it.isNotEmpty() }
            ?.mapNotNull { it.consumptionLitersPer100Km }
            ?.average()

        val recentValues = consumptionRecords.take(2).mapNotNull { it.consumptionLitersPer100Km }
        val baselineValues = consumptionRecords.drop(2).take(6).mapNotNull { it.consumptionLitersPer100Km }
        val recentConsumption = recentValues.takeIf { it.isNotEmpty() }?.average()
        val baselineConsumption = baselineValues.takeIf { it.size >= 2 }?.average()
        val fuelDeviation = if (recentConsumption != null && baselineConsumption != null && baselineConsumption > 0.0) {
            (recentConsumption - baselineConsumption) / baselineConsumption * 100.0
        } else null

        val statuses = plans.filter { it.isActive && !it.isDeleted }.map { MaintenanceAdvisor.statusFor(vehicle, it, now) }
        val overdue = statuses.count { it.urgency == MaintenanceUrgency.OVERDUE }
        val dueSoon = statuses.count { it.urgency == MaintenanceUrgency.DUE_SOON }
        val nextBundle = MaintenanceAdvisor.nextBundle(vehicle, plans)
        val upcomingKnownCost = nextBundle?.items?.sumOf { it.plan.estimatedCost ?: 0.0 } ?: 0.0
        val upcomingMissingPriceCount = nextBundle?.items?.count { it.plan.estimatedCost == null } ?: 0

        val importantFaults = faults.count {
            !it.isDeleted && it.status != FaultStatus.RESOLVED && it.status != FaultStatus.CLOSED &&
                (it.severity == FaultSeverity.HIGH || it.severity == FaultSeverity.CRITICAL)
        }

        val docAttention = documents.count { doc ->
            if (doc.isDeleted || doc.expiryDate == null) false
            else doc.expiryDate <= now + 30L * DAY_MS
        }

        val insights = buildList {
            if (overdue > 0) add(
                SmartInsight(
                    "صيانة تحتاج إجراء",
                    "لديك $overdue بند صيانة متأخر حسب العداد أو التاريخ المسجل.",
                    InsightLevel.URGENT,
                    "maintenance_overdue"
                )
            ) else if (dueSoon > 0) add(
                SmartInsight(
                    "الصيانة القادمة تقترب",
                    "$dueSoon بند يقترب موعده. راجع الحزمة القادمة قبل الموعد.",
                    InsightLevel.ATTENTION,
                    "maintenance_due"
                )
            ) else if (plans.any { it.isActive && !it.isDeleted }) add(
                SmartInsight(
                    "الصيانة تحت السيطرة",
                    "لا توجد بنود صيانة مسجلة متأخرة الآن.",
                    InsightLevel.GOOD,
                    "maintenance_good"
                )
            )

            if (importantFaults > 0) add(
                SmartInsight(
                    "أعطال مهمة مفتوحة",
                    "يوجد $importantFaults عطل مرتفع أو حرج ما زال مفتوحًا في السجل.",
                    InsightLevel.URGENT,
                    "faults"
                )
            )

            if (docAttention > 0) add(
                SmartInsight(
                    "مستندات تحتاج متابعة",
                    "$docAttention مستند منتهي أو سينتهي خلال 30 يومًا.",
                    InsightLevel.ATTENTION,
                    "documents"
                )
            )

            if (fuelDeviation != null && recentConsumption != null) {
                when {
                    fuelDeviation >= 25.0 -> add(
                        SmartInsight(
                            "ارتفاع واضح في الاستهلاك المسجل",
                            "آخر القراءات أعلى بنحو ${fuelDeviation.toInt()}% من خطك المعتاد. راجع نمط القيادة وضغط الإطارات ودقة التزويد قبل اعتبارها مشكلة ميكانيكية.",
                            InsightLevel.ATTENTION,
                            "fuel_high"
                        )
                    )
                    fuelDeviation >= 12.0 -> add(
                        SmartInsight(
                            "استهلاك الوقود أعلى من المعتاد",
                            "آخر القراءات أعلى بنحو ${fuelDeviation.toInt()}% من متوسط قراءاتك السابقة.",
                            InsightLevel.INFO,
                            "fuel_watch"
                        )
                    )
                    fuelDeviation <= -10.0 -> add(
                        SmartInsight(
                            "تحسن في الاستهلاك المسجل",
                            "آخر القراءات أفضل بنحو ${abs(fuelDeviation).toInt()}% من خطك السابق.",
                            InsightLevel.GOOD,
                            "fuel_better"
                        )
                    )
                }
            }

            if (spendChange != null && previousMonthSpend >= 300.0) {
                when {
                    spendChange >= 35.0 -> add(
                        SmartInsight(
                            "الإنفاق هذا الشهر أعلى",
                            "المصروفات المسجلة ارتفعت بنحو ${spendChange.toInt()}% مقارنة بالشهر السابق.",
                            InsightLevel.INFO,
                            "spend_up"
                        )
                    )
                    spendChange <= -25.0 -> add(
                        SmartInsight(
                            "الإنفاق هذا الشهر أقل",
                            "المصروفات المسجلة أقل بنحو ${abs(spendChange).toInt()}% من الشهر السابق.",
                            InsightLevel.GOOD,
                            "spend_down"
                        )
                    )
                }
            }

            val activeReminders = reminders.count { !it.isDeleted && !it.isCompleted }
            if (activeReminders > 0) add(
                SmartInsight(
                    "لديك تذكيرات مفتوحة",
                    "$activeReminders تذكير ينتظر الإجراء.",
                    InsightLevel.INFO,
                    "reminders"
                )
            )

            if (isEmpty()) add(
                SmartInsight(
                    "ابدأ ببناء تاريخ السيارة",
                    "كلما سجلت الوقود والصيانة والعداد أصبحت المقارنات والتوقعات أدق.",
                    InsightLevel.INFO,
                    "starter"
                )
            )
        }

        return VehicleSmartSummary(
            monthSpend = monthSpend,
            previousMonthSpend = previousMonthSpend,
            spendChangePercent = spendChange,
            monthDistanceKm = monthDistance,
            averageConsumptionL100 = averageConsumption,
            recentConsumptionL100 = recentConsumption,
            fuelDeviationPercent = fuelDeviation,
            overdueMaintenanceCount = overdue,
            dueSoonMaintenanceCount = dueSoon,
            openImportantFaultCount = importantFaults,
            documentsNeedingAttention = docAttention,
            estimatedKnownUpcomingMaintenanceCost = upcomingKnownCost,
            upcomingMaintenanceMissingPriceCount = upcomingMissingPriceCount,
            dataConfidence = confidenceScore(vehicle, plans, activeMaintenance, consumptionRecords, activeTrips, documents),
            insights = insights.sortedBy { levelRank(it.level) }
        )
    }

    private fun confidenceScore(
        vehicle: VehicleEntity,
        plans: List<MaintenancePlanEntity>,
        maintenance: List<MaintenanceRecordEntity>,
        consumption: List<FuelRecordEntity>,
        trips: List<TripEntity>,
        documents: List<VehicleDocumentEntity>
    ): Int {
        var score = 20
        if (vehicle.currentOdometerKm > 0) score += 15
        if (plans.any { it.isActive && !it.isDeleted }) score += 20
        if (maintenance.isNotEmpty()) score += 15
        if (consumption.size >= 3) score += 20 else if (consumption.isNotEmpty()) score += 10
        if (trips.size >= 3) score += 5
        if (documents.any { !it.isDeleted && (it.documentType == DocumentType.VEHICLE_LICENSE || it.expiryDate != null) }) score += 5
        return score.coerceIn(0, 100)
    }

    private fun levelRank(level: InsightLevel): Int = when (level) {
        InsightLevel.URGENT -> 0
        InsightLevel.ATTENTION -> 1
        InsightLevel.INFO -> 2
        InsightLevel.GOOD -> 3
    }

    private fun startOfMonth(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfPreviousMonth(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfMonth(time)
        add(Calendar.MONTH, -1)
    }.timeInMillis

    private const val DAY_MS = 86_400_000L
}

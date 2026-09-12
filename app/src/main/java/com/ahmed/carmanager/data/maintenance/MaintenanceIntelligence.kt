package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

enum class MaintenancePreset {
    SMART_DEFAULT,
    CERATO_2021_EGYPT
}

data class MaintenanceTemplate(
    val titleAr: String,
    val category: String,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val estimatedCost: Double? = null,
    val warningBeforeKm: Double? = 1_000.0,
    val warningBeforeDays: Int? = 30,
    val notes: String? = null,
    val priority: Int = 0,
    val lastServiceOdometerKm: Double? = null,
    val nextDueOdometerKm: Double? = null
)

enum class MaintenanceUrgency { OVERDUE, DUE_SOON, UPCOMING }
enum class ProjectionConfidence { LOW, MEDIUM, HIGH }

data class UsageProjection(
    val observedKm: Double,
    val observedDays: Double,
    val kmPerDay: Double,
    val annualKm: Double,
    val confidence: ProjectionConfidence
)

data class MaintenanceItemStatus(
    val plan: MaintenancePlanEntity,
    val urgency: MaintenanceUrgency,
    val remainingKm: Double?,
    val remainingDays: Long?,
    val resolvedNextDueOdometerKm: Double?,
    val resolvedNextDueDate: Long?,
    val projectedOdometerDueDate: Long?,
    val forecastDueDate: Long?,
    val forecastRemainingDays: Long?,
    val usageProjection: UsageProjection?,
    val overdueByKm: Double?,
    val overdueByDays: Long?
)

data class MaintenanceBundle(
    val items: List<MaintenanceItemStatus>,
    val estimatedCost: Double,
    val unknownCostCount: Int,
    val targetOdometerKm: Double?,
    val targetDate: Long?,
    val hasOverdueItems: Boolean
)

object MaintenanceCatalog {
    fun presetFor(vehicle: VehicleEntity): MaintenancePreset =
        presetFor(vehicle.brand, vehicle.model, vehicle.year, vehicle.displayName)

    fun presetFor(brand: String, model: String, year: Int, displayName: String? = null): MaintenancePreset {
        val haystack = "$brand $model ${displayName.orEmpty()}".lowercase()
        return if (year == 2021 && ("cerato" in haystack || "سيراتو" in haystack)) {
            MaintenancePreset.CERATO_2021_EGYPT
        } else {
            MaintenancePreset.SMART_DEFAULT
        }
    }

    fun titleFor(preset: MaintenancePreset): String = when (preset) {
        MaintenancePreset.SMART_DEFAULT -> "قائمة CarManager الذكية"
        MaintenancePreset.CERATO_2021_EGYPT -> "Kia Grand Cerato 2021 — مصر"
    }

    /**
     * A single schedule source is used everywhere in the app. This prevents the add-plan picker,
     * vehicle setup and owner guide from showing conflicting intervals for the same maintenance item.
     * MaintenanceGuidanceCatalog also labels whether a row is OEM, market-reference or the
     * conservative CarManager fallback.
     */
    fun templatesFor(vehicle: VehicleEntity, preset: MaintenancePreset): List<MaintenanceTemplate> =
        MaintenanceGuidanceCatalog.templatesFor(vehicle, preset)
}

object MaintenanceAdvisor {
    private const val DAY_MS = 86_400_000L
    private const val YEAR_DAYS = 365.25
    private const val MIN_PROJECTION_DAYS = 7.0
    private const val MIN_PROJECTION_DISTANCE_KM = 100.0
    private const val MAX_REASONABLE_KM_PER_DAY = 1_500.0
    private const val BUNDLE_KM_WINDOW = 1_000.0
    private const val BUNDLE_DAY_WINDOW = 30L

    fun statusFor(vehicle: VehicleEntity, plan: MaintenancePlanEntity, now: Long = System.currentTimeMillis()): MaintenanceItemStatus {
        val resolvedNextKm = resolveNextOdometer(plan)
        val resolvedNextDate = resolveNextDate(plan)
        val remainingKm = if (plan.reminderRule == ReminderRule.DATE_ONLY) null else resolvedNextKm?.minus(vehicle.currentOdometerKm)
        val remainingDays = if (plan.reminderRule == ReminderRule.ODOMETER_ONLY) null else resolvedNextDate?.let { daysBetween(now, it) }

        val usage = estimateUsage(vehicle, plan, now)
        val projectedOdometerDueDate = if (plan.reminderRule == ReminderRule.DATE_ONLY) null else {
            val dueKm = resolvedNextKm
            if (dueKm != null && usage != null) projectDateForOdometer(vehicle.currentOdometerKm, dueKm, usage.kmPerDay, now) else null
        }
        val forecastDate = when (plan.reminderRule) {
            ReminderRule.DATE_ONLY -> resolvedNextDate
            ReminderRule.ODOMETER_ONLY -> projectedOdometerDueDate
            ReminderRule.WHICHEVER_COMES_FIRST -> earliestDate(resolvedNextDate, projectedOdometerDueDate)
        }
        val forecastRemainingDays = forecastDate?.let { daysBetween(now, it) }

        // A due date later today must remain "due soon", not "overdue" merely because whole-day
        // rounding produced remainingDays == 0. Compare the actual timestamp for the date side.
        val odometerOverdue = remainingKm?.let { it <= 0.0 } == true
        val dateOverdue = if (plan.reminderRule == ReminderRule.ODOMETER_ONLY) {
            false
        } else {
            resolvedNextDate?.let { now >= it } == true
        }
        val overdue = odometerOverdue || dateOverdue
        val dueSoon = !overdue && (
            remainingKm?.let { it <= (plan.warningBeforeKm ?: 1_000.0) } == true ||
                remainingDays?.let { it <= (plan.warningBeforeDays ?: 30) } == true
            )

        return MaintenanceItemStatus(
            plan = plan,
            urgency = when {
                overdue -> MaintenanceUrgency.OVERDUE
                dueSoon -> MaintenanceUrgency.DUE_SOON
                else -> MaintenanceUrgency.UPCOMING
            },
            remainingKm = remainingKm,
            remainingDays = remainingDays,
            resolvedNextDueOdometerKm = resolvedNextKm,
            resolvedNextDueDate = resolvedNextDate,
            projectedOdometerDueDate = projectedOdometerDueDate,
            forecastDueDate = forecastDate,
            forecastRemainingDays = forecastRemainingDays,
            usageProjection = usage,
            overdueByKm = remainingKm?.takeIf { it < 0.0 }?.let { -it },
            overdueByDays = remainingDays?.takeIf { it < 0L }?.let { -it }
        )
    }

    fun nextBundle(vehicle: VehicleEntity, plans: List<MaintenancePlanEntity>, now: Long = System.currentTimeMillis()): MaintenanceBundle? {
        val statuses = plans
            .filter { it.isActive && !it.isDeleted }
            .map { statusFor(vehicle, it, now) }
            .filter { it.remainingKm != null || it.remainingDays != null || it.forecastDueDate != null }
        if (statuses.isEmpty()) return null

        val urgent = statuses.filter { it.urgency != MaintenanceUrgency.UPCOMING }
        val selected = if (urgent.isNotEmpty()) urgent else {
            val anchor = statuses.minByOrNull { relativeScore(it) } ?: return null
            statuses.filter { candidate ->
                val kmClose = anchor.remainingKm?.let { a -> candidate.remainingKm?.let { abs(it - a) <= BUNDLE_KM_WINDOW } ?: false } ?: false
                val dayAnchor = anchor.remainingDays ?: anchor.forecastRemainingDays
                val dayCandidate = candidate.remainingDays ?: candidate.forecastRemainingDays
                val dayClose = dayAnchor?.let { a -> dayCandidate?.let { abs(it - a) <= BUNDLE_DAY_WINDOW } ?: false } ?: false
                candidate.plan.id == anchor.plan.id || kmClose || dayClose
            }
        }.sortedWith(compareBy<MaintenanceItemStatus>({ it.urgency.ordinal }, { it.remainingKm ?: Double.MAX_VALUE }, { it.forecastRemainingDays ?: Long.MAX_VALUE }))

        val knownCosts = selected.mapNotNull { it.plan.estimatedCost }
        return MaintenanceBundle(
            items = selected,
            estimatedCost = knownCosts.sum(),
            unknownCostCount = selected.size - knownCosts.size,
            targetOdometerKm = selected.mapNotNull { it.resolvedNextDueOdometerKm }.minOrNull(),
            targetDate = selected.mapNotNull { it.forecastDueDate ?: it.resolvedNextDueDate }.minOrNull(),
            hasOverdueItems = selected.any { it.urgency == MaintenanceUrgency.OVERDUE }
        )
    }

    fun estimateUsage(vehicle: VehicleEntity, plan: MaintenancePlanEntity, now: Long = System.currentTimeMillis()): UsageProjection? {
        val lastKm = plan.lastServiceOdometerKm ?: return null
        val lastDate = plan.lastServiceDate ?: return null
        if (lastDate >= now || vehicle.currentOdometerKm < lastKm) return null

        val observedKm = vehicle.currentOdometerKm - lastKm
        val observedDays = (now - lastDate).toDouble() / DAY_MS.toDouble()
        if (observedKm < MIN_PROJECTION_DISTANCE_KM || observedDays < MIN_PROJECTION_DAYS) return null

        val kmPerDay = observedKm / observedDays
        if (!kmPerDay.isFinite() || kmPerDay <= 0.0 || kmPerDay > MAX_REASONABLE_KM_PER_DAY) return null

        val confidence = when {
            observedDays >= 90.0 -> ProjectionConfidence.HIGH
            observedDays >= 30.0 -> ProjectionConfidence.MEDIUM
            else -> ProjectionConfidence.LOW
        }
        return UsageProjection(
            observedKm = observedKm,
            observedDays = observedDays,
            kmPerDay = kmPerDay,
            annualKm = kmPerDay * YEAR_DAYS,
            confidence = confidence
        )
    }

    private fun resolveNextOdometer(plan: MaintenancePlanEntity): Double? {
        if (plan.reminderRule == ReminderRule.DATE_ONLY) return null
        return plan.nextDueOdometerKm ?: run {
            val last = plan.lastServiceOdometerKm
            val interval = plan.intervalKm
            if (last != null && interval != null && interval > 0.0) last + interval else null
        }
    }

    private fun resolveNextDate(plan: MaintenancePlanEntity): Long? {
        if (plan.reminderRule == ReminderRule.ODOMETER_ONLY) return null
        return plan.nextDueDate ?: run {
            val last = plan.lastServiceDate
            val interval = plan.intervalMonths
            if (last != null && interval != null && interval > 0) addMonths(last, interval) else null
        }
    }

    private fun projectDateForOdometer(currentKm: Double, dueKm: Double, kmPerDay: Double, now: Long): Long {
        val dayOffset = (dueKm - currentKm) / kmPerDay
        return now + (dayOffset * DAY_MS.toDouble()).toLong()
    }

    private fun earliestDate(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> min(first, second)
    }

    private fun daysBetween(from: Long, to: Long): Long = floor((to - from).toDouble() / DAY_MS.toDouble()).toLong()

    private fun relativeScore(item: MaintenanceItemStatus): Double {
        val kmScore = item.remainingKm?.coerceAtLeast(0.0)?.div(1_000.0) ?: Double.MAX_VALUE
        val dayScore = (item.remainingDays ?: item.forecastRemainingDays)?.coerceAtLeast(0L)?.toDouble()?.div(30.0) ?: Double.MAX_VALUE
        return min(kmScore, dayScore)
    }

    private fun addMonths(time: Long, months: Int): Long = Calendar.getInstance().run {
        timeInMillis = time
        add(Calendar.MONTH, months)
        timeInMillis
    }
}

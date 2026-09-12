package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.FaultStatus
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Explainable maintenance triage. This is a planning aid, not a mechanical diagnosis.
 * Every decision is accompanied by a short Arabic reason that can be shown directly in the UI.
 */
enum class MaintenancePriorityLevel { CRITICAL, IMPORTANT, CAN_DEFER }

data class MaintenancePriorityDecision(
    val level: MaintenancePriorityLevel,
    val reasonAr: String,
    val score: Int
)

data class PrioritizedMaintenanceItem(
    val status: MaintenanceItemStatus,
    val decision: MaintenancePriorityDecision
)

data class MaintenancePriorityCostSummary(
    val level: MaintenancePriorityLevel,
    val itemCount: Int,
    val estimatedCost: Double,
    val unknownCostCount: Int
)

data class PrioritizedMaintenanceBundle(
    val source: MaintenanceBundle,
    val items: List<PrioritizedMaintenanceItem>,
    val costByPriority: List<MaintenancePriorityCostSummary>,
    val highestPriority: MaintenancePriorityLevel,
    val highestPriorityReasonAr: String
) {
    fun summary(level: MaintenancePriorityLevel): MaintenancePriorityCostSummary =
        costByPriority.firstOrNull { it.level == level }
            ?: MaintenancePriorityCostSummary(level, 0, 0.0, 0)
}

object MaintenancePriorityEngine {
    private val safetyKeywords = listOf(
        "فرامل", "تيل", "brake", "توجيه", "steering", "إطار", "إطارات", "كاوتش", "tire", "tyre"
    )

    /**
     * Semantic groups make an open fault such as "المساعدين" relate to a suspension inspection plan
     * even when the exact Arabic word form is different.
     */
    private val relationGroups = listOf(
        setOf("فرامل", "تيل", "طنابير", "حلل", "brake"),
        setOf("اطار", "إطار", "اطارات", "إطارات", "كاوتش", "tire", "tyre"),
        setOf("توجيه", "دركسيون", "باور", "steering"),
        setOf("عفشة", "مساعد", "مساعدين", "مساعدات", "مقص", "مقصات", "جلب", "suspension", "shock"),
        setOf("كوبلن", "كبالن", "cv", "axle"),
        setOf("تبريد", "ردياتير", "رادياتير", "مياه", "coolant", "radiator"),
        setOf("بطارية", "شحن", "دينامو", "battery", "charging"),
        setOf("سير", "سيور", "شداد", "بكرة", "بكرات", "belt", "tensioner")
    )

    fun evaluate(
        item: MaintenanceItemStatus,
        faults: List<FaultRecordEntity> = emptyList()
    ): MaintenancePriorityDecision {
        val plan = item.plan
        val text = "${plan.titleAr} ${plan.category} ${plan.notes.orEmpty()}".lowercase()
        val safetyRelated = safetyKeywords.any(text::contains)
        val linkedImportantFault = faults.firstOrNull { fault -> isOpenImportantFault(fault) && appearsRelated(plan, fault) }

        if (linkedImportantFault != null) {
            return MaintenancePriorityDecision(
                MaintenancePriorityLevel.CRITICAL,
                "مرتبط بعطل ${if (linkedImportantFault.severity == FaultSeverity.CRITICAL) "حرج" else "مهم"} مفتوح",
                100
            )
        }

        if (item.urgency == MaintenanceUrgency.OVERDUE && safetyRelated) {
            val detail = when {
                item.overdueByKm != null -> " • متأخر ${formatCompactKm(item.overdueByKm)} كم"
                item.overdueByDays != null -> " • متأخر ${item.overdueByDays} يوم"
                else -> ""
            }
            return MaintenancePriorityDecision(MaintenancePriorityLevel.CRITICAL, "سلامة + تجاوز الموعد$detail", 97)
        }

        val overdueKm = item.overdueByKm ?: 0.0
        val overdueDays = item.overdueByDays ?: 0L
        if (item.urgency == MaintenanceUrgency.OVERDUE && (overdueKm >= 1_500.0 || overdueDays >= 30L || plan.priority >= 10)) {
            val reason = when {
                overdueKm >= 1_500.0 -> "تجاوز الموعد بـ ${formatCompactKm(overdueKm)} كم"
                overdueDays >= 30L -> "تجاوز الموعد بـ $overdueDays يوم"
                else -> "متأخر وبند عالي الأهمية"
            }
            return MaintenancePriorityDecision(MaintenancePriorityLevel.CRITICAL, reason, 92)
        }

        if (item.urgency == MaintenanceUrgency.OVERDUE) {
            return MaintenancePriorityDecision(MaintenancePriorityLevel.IMPORTANT, "تجاوز موعد الصيانة", 78)
        }

        if (safetyRelated && item.urgency == MaintenanceUrgency.DUE_SOON) {
            return MaintenancePriorityDecision(
                MaintenancePriorityLevel.IMPORTANT,
                "سلامة + ${dueSoonReason(item)}",
                74
            )
        }

        if (item.urgency == MaintenanceUrgency.DUE_SOON) {
            return MaintenancePriorityDecision(MaintenancePriorityLevel.IMPORTANT, dueSoonReason(item), 66)
        }

        if (plan.priority >= 10) {
            return MaintenancePriorityDecision(MaintenancePriorityLevel.IMPORTANT, "بند عالي الأهمية في الخطة", 61)
        }

        if (safetyRelated) {
            return MaintenancePriorityDecision(
                MaintenancePriorityLevel.CAN_DEFER,
                "مرتبط بالسلامة لكنه ما زال بعيدًا عن الاستحقاق",
                42
            )
        }

        return MaintenancePriorityDecision(
            MaintenancePriorityLevel.CAN_DEFER,
            "ما زال داخل فترة الاستحقاق",
            30 + plan.priority.coerceIn(0, 10)
        )
    }

    fun sort(
        items: List<MaintenanceItemStatus>,
        faults: List<FaultRecordEntity> = emptyList()
    ): List<Pair<MaintenanceItemStatus, MaintenancePriorityDecision>> = items
        .map { it to evaluate(it, faults) }
        .sortedWith(
            compareByDescending<Pair<MaintenanceItemStatus, MaintenancePriorityDecision>> { it.second.score }
                .thenBy { it.first.resolvedNextDueOdometerKm ?: Double.MAX_VALUE }
                .thenBy { it.first.resolvedNextDueDate ?: Long.MAX_VALUE }
        )

    /**
     * Official entry point for the "next maintenance bundle" UI.
     * It keeps MaintenanceAdvisor responsible for due-date selection, then adds explainable
     * priority, ordering and cost grouping without duplicating the scheduling rules.
     */
    fun prioritizeNextBundle(
        vehicle: VehicleEntity,
        plans: List<MaintenancePlanEntity>,
        faults: List<FaultRecordEntity> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): PrioritizedMaintenanceBundle? = MaintenanceAdvisor.nextBundle(vehicle, plans, now)
        ?.let { prioritizeBundle(it, faults) }

    fun prioritizeBundle(
        bundle: MaintenanceBundle,
        faults: List<FaultRecordEntity> = emptyList()
    ): PrioritizedMaintenanceBundle {
        val prioritized = sort(bundle.items, faults).map { (status, decision) ->
            PrioritizedMaintenanceItem(status, decision)
        }

        val summaries = MaintenancePriorityLevel.entries.map { level ->
            val group = prioritized.filter { it.decision.level == level }
            val knownCosts = group.mapNotNull { it.status.plan.estimatedCost }
            MaintenancePriorityCostSummary(
                level = level,
                itemCount = group.size,
                estimatedCost = knownCosts.sum(),
                unknownCostCount = group.size - knownCosts.size
            )
        }

        val top = prioritized.firstOrNull()?.decision
            ?: MaintenancePriorityDecision(MaintenancePriorityLevel.CAN_DEFER, "لا توجد بنود ذات أولوية عاجلة", 0)

        return PrioritizedMaintenanceBundle(
            source = bundle,
            items = prioritized,
            costByPriority = summaries,
            highestPriority = top.level,
            highestPriorityReasonAr = top.reasonAr
        )
    }

    private fun dueSoonReason(item: MaintenanceItemStatus): String {
        val km = item.remainingKm?.takeIf { it >= 0.0 }
        val days = item.remainingDays?.takeIf { it >= 0L }
        return when {
            km != null && days != null -> "قريب: ${formatCompactKm(km)} كم أو $days يوم"
            km != null -> "مستحق خلال ${formatCompactKm(km)} كم"
            days != null -> "مستحق خلال $days يوم"
            item.forecastRemainingDays != null -> "متوقع خلال ${item.forecastRemainingDays.coerceAtLeast(0L)} يوم"
            else -> "موعده قريب"
        }
    }

    private fun isOpenImportantFault(fault: FaultRecordEntity): Boolean =
        !fault.isDeleted && fault.status != FaultStatus.RESOLVED && fault.status != FaultStatus.CLOSED &&
            (fault.severity == FaultSeverity.HIGH || fault.severity == FaultSeverity.CRITICAL)

    private fun appearsRelated(plan: MaintenancePlanEntity, fault: FaultRecordEntity): Boolean {
        val planText = normalize("${plan.titleAr} ${plan.category} ${plan.notes.orEmpty()}")
        val faultText = normalize("${fault.symptomAr} ${fault.diagnosisAr.orEmpty()} ${fault.notes.orEmpty()}")

        val planTokens = tokens(planText)
        val faultTokens = tokens(faultText)
        if (planTokens.any { it.length >= 4 && it in faultTokens }) return true

        return relationGroups.any { group ->
            group.any { normalize(it) in planText } && group.any { normalize(it) in faultText }
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')

    private fun tokens(value: String): Set<String> = normalize(value)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .map(String::trim)
        .filter { it.length >= 3 }
        .toSet()

    private fun formatCompactKm(value: Double): String = when {
        value >= 10_000 -> value.toLong().toString()
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> "%.1f".format(value)
    }
}

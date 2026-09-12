package com.ahmed.carmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.maintenance.*
import com.ahmed.carmanager.data.repository.MaintenancePlanInput

/**
 * Read-only owner-guide view. The comprehensive catalog is never installed automatically as
 * MaintenancePlanEntity rows. A user can deliberately promote one item into their active plan.
 */
@Composable
internal fun OwnerGuideScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    onAddPlan: (MaintenancePlanInput) -> Unit,
    onMessage: (String) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة من الجراج أولًا لعرض الدليل.", Icons.Default.MenuBook)
        return
    }

    val resolved = remember(
        vehicle.vehicleId,
        vehicle.brand,
        vehicle.model,
        vehicle.year,
        vehicle.displayName,
        vehicle.engineName,
        vehicle.engineCode,
        vehicle.engineCapacityCc,
        vehicle.transmissionType,
        vehicle.transmissionName,
        vehicle.transmissionCode,
        vehicle.fuelType
    ) { ResolvedOwnerGuideCatalog.resolve(vehicle) }

    var query by remember(vehicle.vehicleId) { mutableStateOf("") }
    var category by remember(vehicle.vehicleId) { mutableStateOf<String?>(null) }
    var expandedId by remember(vehicle.vehicleId) { mutableStateOf<String?>(null) }

    val activeTitles = remember(plans) {
        plans.asSequence()
            .filter { !it.isDeleted && it.isActive }
            .map { normalizeGuideText(it.titleAr) }
            .toSet()
    }
    val allItems = remember(resolved) { resolved.specificItems + resolved.generalItems }
    val categories = remember(allItems) { allItems.map { it.categoryAr }.distinct().sorted() }
    val visibleSpecific = remember(resolved.specificItems, query, category) {
        resolved.specificItems.filter { it.matchesGuideFilter(query, category) }
    }
    val visibleGeneral = remember(resolved.generalItems, query, category) {
        resolved.generalItems.filter { it.matchesGuideFilter(query, category) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "دليل السيارة",
                subtitle = "مرجع صيانة وفحص مستقل عن خطة التنبيهات",
                icon = Icons.Default.MenuBook
            )
        }

        item {
            GuideCoverageCard(vehicle = vehicle, resolved = resolved)
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(CMIcons.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "مسح") }
                },
                label = { Text("ابحث: مساعدين، تيل، زيت فتيس، تيش ميزان...") },
                shape = RoundedCornerShape(13.dp)
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("الكل") }
                )
                categories.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = if (category == item) null else item },
                        label = { Text(item) }
                    )
                }
            }
        }

        if (visibleSpecific.isNotEmpty()) {
            item {
                GuideSectionHeader(
                    title = resolved.specificSourceLabelAr ?: "مرجع خاص بالسيارة",
                    subtitle = "هذه البنود لها مرجع خاص بالموديل/العائلة وتسبق المرجع العام عند التعارض.",
                    sourceType = resolved.specificSourceType ?: OwnerGuideSourceType.CARMANAGER_GENERAL
                )
            }
            items(visibleSpecific, key = { "specific-${it.id}-${it.titleAr}" }) { item ->
                OwnerGuideCard(
                    item = item,
                    expanded = expandedId == "specific:${item.id}:${item.titleAr}",
                    inPlan = item.isRepresentedBy(activeTitles),
                    onExpand = {
                        val key = "specific:${item.id}:${item.titleAr}"
                        expandedId = if (expandedId == key) null else key
                    },
                    onAddPlan = {
                        addGuideItemToPlan(item, onAddPlan)
                        onMessage("تمت إضافة «${item.titleAr}» إلى خطة الصيانة. الدليل نفسه ظل مستقلاً ولم يضف باقي البنود.")
                    }
                )
            }
        }

        if (visibleGeneral.isNotEmpty()) {
            item {
                GuideSectionHeader(
                    title = "مرجع CarManager العام المحافظ",
                    subtitle = if (resolved.specificItems.isEmpty())
                        "لا يوجد لدينا دليل مصنع موثق مضمّن لهذه السيارة حاليًا؛ هذه فترات محافظة عامة وليست ادعاء مصنع."
                    else
                        "يكمل الأجزاء التي لا يغطيها المرجع الخاص أعلاه؛ دليل المصنع الخاص بسيارتك يتفوق عليه دائمًا.",
                    sourceType = OwnerGuideSourceType.CARMANAGER_GENERAL
                )
            }
            items(visibleGeneral, key = { "general-${it.id}" }) { item ->
                OwnerGuideCard(
                    item = item,
                    expanded = expandedId == "general:${item.id}",
                    inPlan = item.isRepresentedBy(activeTitles),
                    onExpand = {
                        val key = "general:${item.id}"
                        expandedId = if (expandedId == key) null else key
                    },
                    onAddPlan = {
                        addGuideItemToPlan(item, onAddPlan)
                        onMessage("تمت إضافة «${item.titleAr}» إلى خطة الصيانة كمرجع CarManager عام محافظ.")
                    }
                )
            }
        }

        if (visibleSpecific.isEmpty() && visibleGeneral.isEmpty()) {
            item {
                EmptyState("لا يوجد بند مطابق", "جرّب اسم القطعة أو اختر «الكل» من التصنيفات.", Icons.Default.SearchOff)
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f)
            ) {
                Text(
                    "مهم: بنود الفحص مثل المساعدين والتيل والطنابير والجلب والكبالن لا تعني تغييرًا تلقائيًا عند الرقم المعروض؛ الرقم هو موعد فحص محافظ، ويُتخذ قرار التغيير بالقياس والحالة أو حسب دليل المصنع.",
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun GuideCoverageCard(vehicle: VehicleEntity, resolved: ResolvedOwnerGuide) {
    val hasSpecific = resolved.specificItems.isNotEmpty()
    val tone = autoToneColors(if (hasSpecific && resolved.specificSourceType == OwnerGuideSourceType.OEM_VERIFIED) AutoTone.GREEN else AutoTone.TEAL)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = tone.soft.copy(alpha = .62f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.strong.copy(alpha = .20f))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (hasSpecific) Icons.Default.Verified else Icons.Default.Info, null, tint = tone.strong)
                Spacer(Modifier.weight(1f))
                Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model} ${vehicle.year}", fontWeight = FontWeight.ExtraBold)
            }
            if (hasSpecific) {
                Text(resolved.specificSourceLabelAr.orEmpty(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = tone.strong)
                Text("ويستكمل CarManager باقي أجزاء السيارة بمرجعه العام المحافظ مع تمييز المصدر على كل بند.", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            } else {
                Text("لا يوجد دليل مصنع موثق مضمّن لهذا الملف حتى الآن.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("لن ننسب أرقامًا إلى المصنع بلا مصدر. يعرض التطبيق بدلًا من ذلك مرجع CarManager العام المحافظ لكل أجزاء السيارة.", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun GuideSectionHeader(title: String, subtitle: String, sourceType: OwnerGuideSourceType) {
    val (icon, tone) = when (sourceType) {
        OwnerGuideSourceType.OEM_VERIFIED -> Icons.Default.Verified to AutoTone.GREEN
        OwnerGuideSourceType.MARKET_REFERENCE -> Icons.Default.Storefront to AutoTone.AMBER
        OwnerGuideSourceType.CARMANAGER_GENERAL -> Icons.Default.Shield to AutoTone.TEAL
    }
    val colors = autoToneColors(tone)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AutomotiveIconBadge(icon, tone, size = 32)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.strong)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun OwnerGuideCard(
    item: OwnerGuideItem,
    expanded: Boolean,
    inPlan: Boolean,
    onExpand: () -> Unit,
    onAddPlan: () -> Unit
) {
    val sourceTone = when (item.sourceType) {
        OwnerGuideSourceType.OEM_VERIFIED -> AutoTone.GREEN
        OwnerGuideSourceType.MARKET_REFERENCE -> AutoTone.AMBER
        OwnerGuideSourceType.CARMANAGER_GENERAL -> AutoTone.TEAL
    }
    val colors = autoToneColors(sourceTone)
    val canSchedule = item.intervalKm != null || item.intervalMonths != null
    OutlinedCard(
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = colors.soft) {
                    Text(
                        guideActionLabel(item.action),
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSoft,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.titleAr, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                    Text(item.categoryAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                guideIntervalLabel(item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (item.safetyCritical) {
                    Icon(Icons.Default.HealthAndSafety, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("سلامة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = colors.soft.copy(alpha = .75f)) {
                    Text(
                        item.sourceLabelAr,
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSoft,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(item.noteAr, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                when {
                    inPlan -> {
                        FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("موجود بالفعل في خطة الصيانة")
                        }
                    }
                    canSchedule -> {
                        OutlinedButton(onClick = onAddPlan, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AddTask, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("إضافة هذا البند فقط لخطة الصيانة")
                        }
                    }
                    else -> {
                        FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.MenuBook, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("مرجع فقط — لا توجد دورة ثابتة آمنة")
                        }
                    }
                }
            }
        }
    }
}

private fun OwnerGuideItem.matchesGuideFilter(query: String, category: String?): Boolean {
    if (category != null && categoryAr != category) return false
    val q = normalizeGuideText(query)
    if (q.isBlank()) return true
    return normalizeGuideText("$titleAr $categoryAr $noteAr ${guideActionLabel(action)}").contains(q)
}

private fun OwnerGuideItem.isRepresentedBy(activeTitles: Set<String>): Boolean {
    val own = normalizeGuideText(titleAr)
    return activeTitles.any { planTitle ->
        planTitle == own || planTitle.contains(own) || own.contains(planTitle)
    }
}

private fun addGuideItemToPlan(item: OwnerGuideItem, onAddPlan: (MaintenancePlanInput) -> Unit) {
    onAddPlan(
        MaintenancePlanInput(
            titleAr = item.titleAr,
            category = item.categoryAr,
            intervalKm = item.intervalKm,
            intervalMonths = item.intervalMonths,
            estimatedCost = null,
            notes = "${item.sourceLabelAr}: ${item.noteAr}"
        )
    )
}

private fun guideIntervalLabel(item: OwnerGuideItem): String {
    val km = item.intervalKm?.let { "كل ${formatKm(it)} كم" }
    val months = item.intervalMonths?.let {
        when {
            it % 12 == 0 && it >= 12 -> {
                val years = it / 12
                if (years == 1) "كل 12 شهر" else "كل $years سنوات"
            }
            else -> "كل $it شهر"
        }
    }
    return when {
        km != null && months != null -> "$km أو $months — أيهما أقرب"
        km != null -> km
        months != null -> months
        else -> "حسب الحالة ودليل المصنع"
    }
}

private fun guideActionLabel(action: OwnerGuideAction): String = when (action) {
    OwnerGuideAction.INSPECT -> "فحص"
    OwnerGuideAction.REPLACE -> "تغيير"
    OwnerGuideAction.CHANGE_FLUID -> "تغيير سائل"
    OwnerGuideAction.ROTATE -> "تدوير"
    OwnerGuideAction.CLEAN_OR_SERVICE -> "خدمة/تنظيف"
    OwnerGuideAction.REVIEW_OEM -> "راجع دليل المصنع"
}

private fun normalizeGuideText(value: String): String = value
    .lowercase()
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    .replace('ة', 'ه').replace('ى', 'ي')
    .replace(Regex("[ًٌٍَُِّْـ]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

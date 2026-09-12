package com.ahmed.carmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.VehicleEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class PartBrowseCategory(
    val title: String,
    val icon: ImageVector,
    val tone: AutoTone,
    val queries: List<String>
)

private val primaryPartBrowseCategories = listOf(
    PartBrowseCategory("الفرامل", Icons.Default.Speed, AutoTone.RED, listOf("تيل فرامل أمامي", "تيل فرامل خلفي", "طنابير فرامل", "سائل الفرامل")),
    PartBrowseCategory("العفشة", Icons.Default.CarRepair, AutoTone.VIOLET, listOf("مساعدين أمامي", "مساعدين خلفي", "تيش ميزان", "جلب مقصات", "بيض مقص")),
    PartBrowseCategory("الفلاتر", Icons.Default.FilterAlt, AutoTone.GREEN, listOf("فلتر زيت", "فلتر هواء", "فلتر تكييف", "فلتر بنزين")),
    PartBrowseCategory("الزيوت والسوائل", Icons.Default.Opacity, AutoTone.AMBER, listOf("زيت محرك", "زيت فتيس", "سائل تبريد", "زيت فرامل")),
    PartBrowseCategory("المحرك", Icons.Default.Settings, AutoTone.CORAL, listOf("بوجيهات", "قواعد موتور", "سير مجموعة", "بلف PCV")),
    PartBrowseCategory("التبريد", Icons.Default.AcUnit, AutoTone.BLUE, listOf("ردياتير", "طرمبة مياه", "ثرموستات", "خرطوم ردياتير")),
    PartBrowseCategory("التكييف", Icons.Default.Air, AutoTone.TEAL, listOf("فلتر تكييف", "كمبروسر تكييف", "سربنتينة تكييف", "مروحة تكييف")),
    PartBrowseCategory("الكهرباء", Icons.Default.ElectricBolt, AutoTone.AMBER, listOf("بطارية", "دينامو", "مارش", "فيوز")),
    PartBrowseCategory("نقل الحركة", Icons.Default.SyncAlt, AutoTone.GRAPHITE, listOf("زيت فتيس", "كوبلن داخلي", "كوبلن خارجي", "قاعدة فتيس"))
)

/**
 * Lightweight, results-first parts search.
 * It deliberately owns no maintenance/fault/installed-part data; the advanced center loads those
 * streams only after the user explicitly opens it.
 */
@Composable
internal fun PartsHubV2Screen(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    onSelectVehicle: (String) -> Unit,
    onOpenAdvanced: () -> Unit,
    onMessage: (String) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة من الجراج أولًا.", CMIcons.Parts)
        return
    }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember(vehicle.vehicleId) { VehicleMarketProfileStore.prefs(context, vehicle.vehicleId) }
    val learnedAliases = remember(vehicle.vehicleId) { VehicleMarketProfileStore.learnedAliases(context, vehicle.vehicleId) }
    val learnedProfiles = remember(vehicle.vehicleId) { VehicleMarketProfileStore.learnedProfiles(context, vehicle.vehicleId) }
    val identity = remember(
        vehicle.vehicleId,
        vehicle.brand,
        vehicle.model,
        vehicle.year,
        vehicle.trim,
        vehicle.displayName,
        vehicle.generationCode,
        vehicle.engineName,
        vehicle.engineCode,
        vehicle.engineCapacityCc,
        vehicle.transmissionName,
        vehicle.transmissionCode,
        vehicle.transmissionType,
        learnedAliases,
        learnedProfiles
    ) {
        VehicleMarketIdentityResolver.resolve(vehicle, learnedAliases, learnedProfiles)
    }

    val providers = remember { ExpandedPartsPriceEngine.capabilities }
    val defaultEnabled = remember(providers) { providers.mapTo(linkedSetOf()) { it.id } }
    var enabledProviders by remember(vehicle.vehicleId) {
        mutableStateOf(prefs.getStringSet("enabled_sources", defaultEnabled)?.toSet() ?: defaultEnabled)
    }
    var query by remember(vehicle.vehicleId) { mutableStateOf("") }
    var result by remember(vehicle.vehicleId) { mutableStateOf<PartsPriceSearchResult?>(null) }
    var searching by remember(vehicle.vehicleId) { mutableStateOf(false) }
    var showSources by remember(vehicle.vehicleId) { mutableStateOf(false) }
    var vehicleMenu by remember(vehicle.vehicleId) { mutableStateOf(false) }
    var selectedBrowseCategory by remember(vehicle.vehicleId) { mutableStateOf<PartBrowseCategory?>(null) }
    var searchJob by remember(vehicle.vehicleId) { mutableStateOf<Job?>(null) }
    var generation by remember(vehicle.vehicleId) { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    DisposableEffect(vehicle.vehicleId) {
        onDispose { searchJob?.cancel() }
    }

    fun persistEnabled(value: Set<String>) {
        enabledProviders = value
        prefs.edit().putStringSet("enabled_sources", value).apply()
    }

    fun startSearch(rawQuery: String = query) {
        val clean = identity.sanitizePartQuery(rawQuery.trim()).trim()
        if (clean.isBlank()) {
            onMessage("اكتب اسم القطعة أو رقم OEM أولًا.")
            return
        }
        query = clean
        selectedBrowseCategory = null
        generation += 1L
        val currentGeneration = generation
        searchJob?.cancel()
        searching = true
        result = null
        searchJob = scope.launch {
            try {
                val searched = ExpandedPartsPriceEngine.search(
                    vehicle = vehicle,
                    rawPart = clean,
                    enabledProviderIds = enabledProviders,
                    identity = identity
                )
                if (currentGeneration == generation) result = searched
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (currentGeneration == generation) {
                    result = PartsPriceSearchResult(
                        requestedPart = clean,
                        errors = mapOf("search" to "تعذر التحقق الآن. يمكنك فتح المصادر مباشرة."),
                        checkedAt = System.currentTimeMillis()
                    )
                }
            } finally {
                if (currentGeneration == generation) searching = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CMPremium.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "parts-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onOpenAdvanced,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Default.MenuBook, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("الدليل والمركز الكامل", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("بحث قطع الغيار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("سعر • توفر • توافق السيارة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "parts-vehicle") {
            Box {
                Surface(
                    onClick = { vehicleMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${vehicle.year} • ${identity.canonicalName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        AutomotiveIconBadge(CMIcons.Vehicle, AutoTone.TEAL, size = 32)
                    }
                }
                DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                    vehicles.filter { !it.isDeleted }.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.displayName ?: "${item.brand} ${item.model} • ${item.year}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(if (item.vehicleId == vehicle.vehicleId) Icons.Default.CheckCircle else CMIcons.Vehicle, null)
                            },
                            onClick = {
                                vehicleMenu = false
                                if (item.vehicleId != vehicle.vehicleId) onSelectVehicle(item.vehicleId)
                            }
                        )
                    }
                }
            }
        }

        if (query.isBlank() && result == null && !searching) {
            item(key = "parts-categories-title") {
                AutomotiveSectionTitle(
                    title = "الأقسام الرئيسية",
                    subtitle = "اختيار سريع بدون تحميل صور أو متجر ثقيل"
                )
            }
            primaryPartBrowseCategories.chunked(3).forEachIndexed { rowIndex, rowItems ->
                item(key = "parts-category-row-$rowIndex") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { category ->
                            CompactPartCategoryTile(
                                category = category,
                                selected = selectedBrowseCategory?.title == category.title,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedBrowseCategory = if (selectedBrowseCategory?.title == category.title) null else category
                                }
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            selectedBrowseCategory?.let { category ->
                item(key = "parts-category-queries-${category.title}") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text("${category.title} — اختر القطعة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            category.queries.forEach { part ->
                                AssistChip(
                                    onClick = { startSearch(part) },
                                    label = { Text(part, maxLines = 1) },
                                    leadingIcon = { Icon(CMIcons.Search, null, Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "parts-query") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isNotBlank()) selectedBrowseCategory = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                label = { Text("اسم القطعة أو رقم OEM") },
                placeholder = { Text("مثال: سائل فرامل أو 26300-35505") },
                leadingIcon = { Icon(CMIcons.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = {
                            generation += 1L
                            searchJob?.cancel()
                            searching = false
                            query = ""
                            result = null
                            selectedBrowseCategory = null
                        }) { Icon(Icons.Default.Close, "مسح") }
                    }
                }
            )
        }

        item(key = "parts-search") {
            Button(
                onClick = { startSearch() },
                enabled = query.isNotBlank() && !searching,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 7.dp)
            ) {
                if (searching) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.PriceCheck, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (searching) "جارٍ التحقق من المتاجر…" else "بحث ومقارنة الأسعار")
            }
        }

        when {
            searching -> item(key = "parts-progress") {
                PartsSearchProgressCard(enabledProviders.count { it in ExpandedPartsPriceEngine.priceCapableProviderIds })
            }

            result?.offers?.isNotEmpty() == true -> {
                val offers = result!!.offers
                item(key = "parts-summary") { PartsSearchSummaryCard(offers) }
                items(offers, key = { "${it.providerId}|${it.sourceUrl}|${it.title}|${it.priceEgp}" }) { offer ->
                    CompactPartsOfferCard(offer) {
                        runCatching { uriHandler.openUri(offer.sourceUrl) }
                            .onFailure { onMessage("تعذر فتح رابط المنتج.") }
                    }
                }
            }

            result != null -> item(key = "parts-empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                        Text("لم نجد سعرًا موثوقًا مطابقًا", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "لن يعرض CarManager قطعة من موديل آخر لمجرد كلمة مشتركة. جرّب رقم OEM أو افتح المصادر يدويًا.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                        result!!.errors.values.distinct().take(2).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (query.isNotBlank() && !searching) {
            item(key = "parts-external") {
                val external = providers.filter { !it.priceInApp }.take(2)
                if (external.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        external.forEach { provider ->
                            OutlinedButton(
                                onClick = {
                                    ExpandedPartsPriceEngine.providerSearchUrl(provider.id, vehicle, query, identity)?.let { url ->
                                        runCatching { uriHandler.openUri(url) }
                                            .onFailure { onMessage("تعذر فتح ${provider.name}.") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item(key = "parts-sources-toggle") {
            OutlinedButton(
                onClick = { showSources = !showSources },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(if (showSources) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showSources) "إخفاء المصادر" else "المصادر والمتاجر (${providers.size})", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (showSources) {
            item(key = "parts-source-note") {
                Text(
                    "مصادر التحقق تعمل داخل التطبيق عند البحث؛ باقي المتاجر تُفتح خارجيًا بدون اختلاق سعر أو مخزون.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            items(providers, key = { "source-${it.id}" }) { provider ->
                CompactPartsProviderRow(
                    provider = provider,
                    enabled = provider.id in enabledProviders,
                    onToggle = { id ->
                        persistEnabled(if (id in enabledProviders) enabledProviders - id else enabledProviders + id)
                    },
                    onOpen = {
                        ExpandedPartsPriceEngine.providerSearchUrl(provider.id, vehicle, query.ifBlank { "قطع غيار" }, identity)?.let { url ->
                            runCatching { uriHandler.openUri(url) }
                                .onFailure { onMessage("تعذر فتح ${provider.name}.") }
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun CompactPartCategoryTile(
    category: PartBrowseCategory,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = autoToneColors(category.tone)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.soft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) colors.strong.copy(alpha = .45f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(category.icon, null, Modifier.size(18.dp), tint = colors.strong)
            Spacer(Modifier.height(3.dp))
            Text(category.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PartsSearchProgressCard(activeSources: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("التحقق جارٍ", fontWeight = FontWeight.Bold)
                Text("$activeSources مصادر أسعار تعمل بالتوازي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PartsSearchSummaryCard(offers: List<PartsPriceOffer>) {
    val summary = remember(offers) {
        val available = offers.filter { it.availability != PartsOfferAvailability.OUT_OF_STOCK }
        Triple((available.ifEmpty { offers }).minByOrNull { it.priceEgp }, offers.map { it.storeName }.distinct().size, offers.size)
    }
    val best = summary.first
    val stores = summary.second
    val count = summary.third
    val colors = autoToneColors(AutoTone.GREEN)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.soft,
        border = BorderStroke(1.dp, colors.strong.copy(alpha = .18f))
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            AutomotiveIconBadge(Icons.Default.Verified, AutoTone.GREEN, size = 34)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("$count عرض من $stores متجر", fontWeight = FontWeight.ExtraBold)
                best?.let { Text("أقل سعر: ${formatMoney(it.priceEgp)} • ${it.storeName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                Text("الترتيب: التوافق ثم التوفر ثم السعر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactPartsOfferCard(offer: PartsPriceOffer, onOpen: () -> Unit) {
    val confidence = offer.fitmentConfidence
    val tone = when {
        confidence == null -> AutoTone.AMBER
        confidence >= 85 -> AutoTone.GREEN
        confidence >= 65 -> AutoTone.TEAL
        else -> AutoTone.AMBER
    }
    val colors = autoToneColors(tone)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(formatMoney(offer.priceEgp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = colors.strong)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(offer.storeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        offer.availability.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (offer.availability) {
                            PartsOfferAvailability.IN_STOCK -> autoToneColors(AutoTone.GREEN).strong
                            PartsOfferAvailability.OUT_OF_STOCK -> MaterialTheme.colorScheme.error
                            PartsOfferAvailability.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(offer.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                confidence?.let {
                    Surface(shape = RoundedCornerShape(50), color = colors.soft) {
                        Text("توافق $it%", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = colors.strong, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(offer.vehicleMatch.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            offer.fitmentReason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2)
            }
            if ((confidence ?: 0) < 85) {
                Text("أكد رقم OEM والمواصفات قبل الشراء.", style = MaterialTheme.typography.labelSmall, color = autoToneColors(AutoTone.AMBER).strong, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CompactPartsProviderRow(
    provider: PartsProviderCapability,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("فتح", style = MaterialTheme.typography.labelSmall)
            }
            if (provider.priceInApp) {
                Switch(checked = enabled, onCheckedChange = { onToggle(provider.id) })
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(provider.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (provider.priceInApp) "تحقق داخل التطبيق" else "فتح خارجي للمقارنة",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.priceInApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

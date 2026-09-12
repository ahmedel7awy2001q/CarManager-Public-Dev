package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.FactCheck

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.R
import com.ahmed.carmanager.data.auth.AccountUser
import com.ahmed.carmanager.data.local.model.VehicleEntity

private data class ControlShortcut(val id: String, val title: String, val icon: ImageVector, val action: () -> Unit)

@Composable
internal fun SettingsHubV090Screen(
    vehicle: VehicleEntity?, accountUser: AccountUser?, onOpen: (MoreDestination) -> Unit,
    onMaintenanceSetup: () -> Unit, onHealthCenter: () -> Unit, onInspection: () -> Unit,
    onDiagnostics: () -> Unit, onTimeline: () -> Unit, onFuel: () -> Unit, onGps: () -> Unit,
    onAppearance: () -> Unit, onAccount: () -> Unit, onBackup: () -> Unit,
    onAppDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cm_control_center_v090", Context.MODE_PRIVATE) }
    val defaultFavorites = setOf("health", "maintenance", "fuel", "gps", "parts", "reports")
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", defaultFavorites)?.toSet() ?: defaultFavorites) }
    var customize by remember { mutableStateOf(false) }
    var vehicleExpanded by rememberSaveable { mutableStateOf(true) }
    var operationExpanded by rememberSaveable { mutableStateOf(true) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }

    val shortcuts = listOf(
        ControlShortcut("health", "صحة المركبة", CMIcons.Health, onHealthCenter),
        ControlShortcut("maintenance", "الصيانة", CMIcons.Maintenance, onMaintenanceSetup),
        ControlShortcut("fuel", "الوقود", CMIcons.Fuel, onFuel),
        ControlShortcut("gps", "GPS", CMIcons.Gps, onGps),
        ControlShortcut("parts", "القطع والدليل", CMIcons.Parts) { onOpen(MoreDestination.PARTS) },
        ControlShortcut("reports", "التقارير", CMIcons.Reports) { onOpen(MoreDestination.REPORTS) },
        ControlShortcut("faults", "الأعطال", CMIcons.Fault) { onOpen(MoreDestination.FAULTS) },
        ControlShortcut("timeline", "السجل", CMIcons.Timeline, onTimeline),
        ControlShortcut("documents", "المستندات", CMIcons.Document) { onOpen(MoreDestination.DOCUMENTS) },
        ControlShortcut("expenses", "المصاريف", CMIcons.Expense) { onOpen(MoreDestination.EXPENSES) }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionHeader("المزيد", "مركز تحكم مختصر وقابل للتخصيص") }
        item {
            ElevatedCard(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(13.dp), horizontalAlignment = Alignment.End) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { customize = true }) {
                            Icon(Icons.Default.Tune, "تخصيص مركز التحكم", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("مركز إدارة المركبة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(
                                vehicle?.let { "${it.brand} ${it.model} • ${formatKm(it.currentOdometerKm)} كم" } ?: "لا توجد مركبة محددة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilledTonalButton(onClick = onAccount, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(4.dp)); Text(accountUser?.displayName?.take(12) ?: "الحساب")
                        }
                        FilledTonalButton(onClick = { onOpen(MoreDestination.GARAGE) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Garage, null); Spacer(Modifier.width(4.dp)); Text("الجراج")
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { customize = true }) { Icon(Icons.Default.Tune, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("تخصيص") }
                Spacer(Modifier.weight(1f))
                Text("المفضلة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        val favoriteItems = shortcuts.filter { it.id in favorites }.take(6)
        favoriteItems.chunked(3).forEach { row ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { shortcut -> ControlShortcutTile(shortcut, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        item { ControlSectionHeader("المركبة والصيانة", vehicleExpanded) { vehicleExpanded = !vehicleExpanded } }
        if (vehicleExpanded) {
            item { ControlRow("صحة المركبة", "ملخص الصيانة والأعطال والفحص", Icons.Default.HealthAndSafety, onHealthCenter) }
            item { ControlRow("الفحص الذاتي الذكي", "فحص سريع/كامل/قبل السفر", Icons.AutoMirrored.Filled.FactCheck, onInspection) }
            item { ControlRow("ThinkDiag والتشخيص", "الأكواد والتفسير والمتابعة", Icons.Default.Memory, onDiagnostics) }
            item { ControlRow("قائمة الصيانة الذكية", "المواعيد والأولوية وخطة السيارة", Icons.Default.Engineering, onMaintenanceSetup) }
            item { ControlRow("دليل الصيانة وقطع الغيار", "المواعيد، OEM، المتاجر، التوافق والبدائل", CMIcons.Parts) { onOpen(MoreDestination.PARTS) } }
            item { ControlRow("تعديل بيانات المركبة", "الجراج والمركبات", Icons.Default.DirectionsCar) { onOpen(MoreDestination.GARAGE) } }
        }

        item { ControlSectionHeader("التشغيل والسجل", operationExpanded) { operationExpanded = !operationExpanded } }
        if (operationExpanded) {
            item { ControlRow("تاريخ المركبة الكامل", "كل الأحداث بترتيب زمني", Icons.Default.History, onTimeline) }
            item { ControlRow("الوقود واستهلاك التشغيل", "التموين والاستهلاك والتكلفة/كم", Icons.Default.LocalGasStation, onFuel) }
            item { ControlRow("GPS وأجهزة التتبع", "الهاتف، الشاشة، GPS وملحقات التتبع", Icons.Default.LocationOn, onGps) }
            item { ControlRow("المشاوير وحاسبة الشغل", "الشخصي والعمل والسفر والخدمة والتكلفة الحقيقية وعروض التسعير", CMIcons.Trip) { onOpen(MoreDestination.TRIPS) } }
            item { ControlRow("المصاريف", "تكلفة التشغيل والمشتريات", CMIcons.Expense) { onOpen(MoreDestination.EXPENSES) } }
            item { ControlRow("الإطارات والبطارية", "العمر والفحص والتركيب", Icons.Default.TireRepair) { onOpen(MoreDestination.TIRES_BATTERY) } }
            item { ControlRow("الأعطال", "الحالات المفتوحة ومسار الإصلاح", CMIcons.Fault) { onOpen(MoreDestination.FAULTS) } }
            item { ControlRow("التنبيهات", "المواعيد والتنبيهات الذكية", CMIcons.Reminder) { onOpen(MoreDestination.REMINDERS) } }
        }

        item { ControlSectionHeader("البيانات والتجربة", dataExpanded) { dataExpanded = !dataExpanded } }
        if (dataExpanded) {
            item { ControlRow("خزنة المستندات", "الرخص والفواتير والصور وPDF", CMIcons.Document) { onOpen(MoreDestination.DOCUMENTS) } }
            item { ControlRow("التقارير والتحليلات", "المصاريف والوقود والصيانة والمشاوير", CMIcons.Reports) { onOpen(MoreDestination.REPORTS) } }
            item { ControlRow("المظهر والألوان", "فاتح، داكن ولوحات الألوان", Icons.Default.Palette, onAppearance) }
            item { ControlRow("تشخيص التطبيق", "فحص حالة التطبيق والبيانات والمزامنة", Icons.Default.BugReport, onAppDiagnostics) }
            item { ControlRow("الحساب والأمان والمزامنة", accountUser?.email ?: "إدارة الحساب", Icons.Default.AccountCircle, onAccount) }
            item { ControlRow("النسخ الاحتياطي والاستعادة", "نسخة محلية ومزامنة الحساب", Icons.Default.Backup, onBackup) }
        }

        item { DeveloperCreditCard(context) }
        item { Spacer(Modifier.height(96.dp)) }
    }

    if (customize) {
        AlertDialog(
            onDismissRequest = { customize = false },
            title = { Text("تخصيص مركز التحكم") },
            text = {
                Column(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("اختر حتى 6 اختصارات تظهر في المفضلة. باقي الأدوات تظل متاحة في الأقسام بالأسفل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    shortcuts.forEach { item ->
                        val checked = item.id in favorites
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                enabled = checked || favorites.size < 6,
                                onCheckedChange = { value ->
                                    favorites = if (value) favorites + item.id else favorites - item.id
                                    prefs.edit().putStringSet("favorites", favorites).apply()
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { customize = false }) { Text("تم") } },
            dismissButton = {
                TextButton(onClick = {
                    favorites = defaultFavorites
                    prefs.edit().putStringSet("favorites", defaultFavorites).apply()
                }) { Text("الافتراضي") }
            }
        )
    }
}

@Composable
private fun DeveloperCreditCard(context: Context) {
    Surface(
        onClick = { openDeveloperWhatsApp(context) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_whatsapp),
                contentDescription = "التواصل عبر واتساب",
                modifier = Modifier.size(19.dp),
                tint = androidx.compose.ui.graphics.Color(0xFF25D366)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "تصميم وتنفيذ أحمد الحاوي",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openDeveloperWhatsApp(context: Context) {
    // Public-safe mirror: private contact number intentionally omitted.
    val number = ""
    if (number.isBlank()) return
    val uri = Uri.parse("https://wa.me/$number")
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    for (pkg in packages) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(pkg) }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

@Composable private fun ControlShortcutTile(item: ControlShortcut, modifier: Modifier) {
    Surface(onClick = item.action, modifier = modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            Text(item.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable private fun ControlSectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun ControlRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChevronLeft, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2)
            }
            Spacer(Modifier.width(9.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(7.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

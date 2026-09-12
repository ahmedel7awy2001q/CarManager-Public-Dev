package com.ahmed.carmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.auth.AccountUser
import com.ahmed.carmanager.data.local.model.VehicleEntity

/** v0.8 visual control hub: feature-first, colourful and much easier to scan than a long settings list. */
@Composable
internal fun SettingsHubScreen(
    vehicle: VehicleEntity?,
    accountUser: AccountUser?,
    onOpen: (MoreDestination) -> Unit,
    onMaintenanceSetup: () -> Unit,
    onHealthCenter: () -> Unit,
    onInspection: () -> Unit,
    onDiagnostics: () -> Unit,
    onTimeline: () -> Unit,
    onFuel: () -> Unit,
    onGps: () -> Unit,
    onAppearance: () -> Unit,
    onAccount: () -> Unit,
    onBackup: () -> Unit,
    onAppDiagnostics: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ControlHubHero(vehicle) }

        item { AutomotiveSectionTitle("المركبة", "كل ما يخص الحالة والصيانة والتشخيص") }
        item {
            FeatureGridRow(
                left = FeatureSpec("صحة المركبة", "مؤشر شامل ذكي", CMIcons.Health, AutoTone.GREEN, onHealthCenter),
                right = FeatureSpec("الفحص الذكي", "سريع أو شامل", CMIcons.Inspection, AutoTone.BLUE, onInspection)
            )
        }
        item {
            FeatureGridRow(
                left = FeatureSpec("ThinkDiag", "تقارير وأعطال", CMIcons.Diagnostics, AutoTone.VIOLET, onDiagnostics),
                right = FeatureSpec("قائمة الصيانة", "مواعيد وأسعار", CMIcons.Maintenance, AutoTone.CORAL, onMaintenanceSetup)
            )
        }
        item {
            FeatureGridRow(
                left = FeatureSpec("الجراج", "المركبات والملفات", CMIcons.Garage, AutoTone.TEAL) { onOpen(MoreDestination.GARAGE) },
                right = FeatureSpec("قطع الغيار", "العمر والتاريخ", Icons.Rounded.SettingsSuggest, AutoTone.AMBER) { onOpen(MoreDestination.PARTS) }
            )
        }

        item { AutomotiveSectionTitle("التشغيل", "الرحلات والوقود والتكلفة والتاريخ") }
        item {
            FeatureGridRow(
                left = FeatureSpec("GPS والرحلات", "موقع وتتبع ومصادر", CMIcons.Gps, AutoTone.BLUE, onGps),
                right = FeatureSpec("الوقود والطاقة", "تعبئات واستهلاك", CMIcons.Fuel, AutoTone.TEAL, onFuel)
            )
        }
        item {
            FeatureGridRow(
                left = FeatureSpec("السجل الكامل", "Timeline موحد", CMIcons.History, AutoTone.VIOLET, onTimeline),
                right = FeatureSpec("المصاريف", "تكلفة وتشغيل", CMIcons.Expense, AutoTone.AMBER) { onOpen(MoreDestination.EXPENSES) }
            )
        }
        item {
            FeatureGridRow(
                left = FeatureSpec("الإطارات والبطارية", "سجل وحالة", Icons.Rounded.TireRepair, AutoTone.GREEN) { onOpen(MoreDestination.TIRES_BATTERY) },
                right = FeatureSpec("الأعطال والإصلاحات", "متابعة حتى الحل", CMIcons.Fault, AutoTone.RED) { onOpen(MoreDestination.FAULTS) }
            )
        }

        item { AutomotiveSectionTitle("المتابعة والوثائق", "ما تحتاجه لاحقًا بدون إغراق الرئيسية") }
        item {
            FeatureGridRow(
                left = FeatureSpec("التنبيهات", "مواعيد وعداد", CMIcons.Reminder, AutoTone.CORAL) { onOpen(MoreDestination.REMINDERS) },
                right = FeatureSpec("خزنة المستندات", "رخص وفواتير", CMIcons.Document, AutoTone.BLUE) { onOpen(MoreDestination.DOCUMENTS) }
            )
        }
        item {
            FeatureGridRow(
                left = FeatureSpec("التقارير", "تكاليف واتجاهات", CMIcons.Reports, AutoTone.VIOLET) { onOpen(MoreDestination.REPORTS) },
                right = FeatureSpec("المظهر", "فاتح وGraphite", Icons.Rounded.Palette, AutoTone.TEAL, onAppearance)
            )
        }

        item { AutomotiveSectionTitle("الحساب والأمان", accountUser?.email ?: "بياناتك تحت سيطرتك") }
        item {
            AccountControlCard(
                email = accountUser?.email,
                onAccount = onAccount,
                onBackup = onBackup,
                onDiagnostics = onAppDiagnostics
            )
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

private data class FeatureSpec(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: AutoTone,
    val onClick: () -> Unit
)

@Composable
private fun FeatureGridRow(left: FeatureSpec, right: FeatureSpec) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AutomotiveFeatureTile(left.title, left.subtitle, left.icon, left.tone, Modifier.weight(1f), left.onClick)
        AutomotiveFeatureTile(right.title, right.subtitle, right.icon, right.tone, Modifier.weight(1f), right.onClick)
    }
}

@Composable
private fun ControlHubHero(vehicle: VehicleEntity?) {
    val teal = autoToneColors(AutoTone.TEAL)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF0A1A20), Color(0xFF123941), Color(0xFF0B252C)))
            ).padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(66.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .08f)) {
                        Icon(Icons.Rounded.Tune, null, Modifier.padding(16.dp).size(30.dp), tint = Color(0xFF62DDE6))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("مركز التحكم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        vehicle?.let { "${it.displayName ?: "${it.brand} ${it.model}"} • ${formatKm(it.currentOdometerKm)} كم" }
                            ?: "إدارة كل أقسام CarManager من مكان واحد",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = .68f),
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(9.dp))
                    Surface(shape = RoundedCornerShape(50), color = teal.strong.copy(alpha = .18f)) {
                        Text("Premium Automotive v0.8", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF8DE9EF))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountControlCard(email: String?, onAccount: () -> Unit, onBackup: () -> Unit, onDiagnostics: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AutomotiveIconBadge(Icons.Rounded.AccountCircle, AutoTone.GRAPHITE, size = 48)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("الحساب والمزامنة", fontWeight = FontWeight.ExtraBold)
                    Text(email ?: "إدارة الحساب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallControlButton("تشخيص", CMIcons.Diagnostics, AutoTone.CORAL, Modifier.weight(1f), onDiagnostics)
                SmallControlButton("نسخ احتياطي", CMIcons.Backup, AutoTone.GREEN, Modifier.weight(1f), onBackup)
                SmallControlButton("الحساب", Icons.Rounded.AccountCircle, AutoTone.BLUE, Modifier.weight(1f), onAccount)
            }
        }
    }
}

@Composable
private fun SmallControlButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: AutoTone, modifier: Modifier, onClick: () -> Unit) {
    val c = autoToneColors(tone)
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(15.dp), color = c.soft) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.strong)
            Spacer(Modifier.height(5.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c.onSoft, maxLines = 1)
        }
    }
}

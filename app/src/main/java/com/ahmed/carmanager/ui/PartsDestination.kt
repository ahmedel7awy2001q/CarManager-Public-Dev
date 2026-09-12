package com.ahmed.carmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmed.carmanager.data.local.model.VehicleEntity

private enum class PartsDestinationMode { QUICK, GUIDE, ADVANCED }

/**
 * Three deliberately separate layers:
 * QUICK    = results-first price/fitment search.
 * GUIDE    = read-only owner guide, independent from the active maintenance plan.
 * ADVANCED = only the user's active maintenance needs and installed-parts record.
 *
 * The former all-in-one center had a second "guide" tab backed by MaintenancePlanEntity. That
 * duplicate source of truth is intentionally retired so users never confuse an active plan with
 * the comprehensive vehicle guide.
 */
@Composable
internal fun PartsDestination(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    viewModel: CarManagerViewModel
) {
    var mode by remember(vehicle?.vehicleId) { mutableStateOf(PartsDestinationMode.QUICK) }

    BackHandler(enabled = mode != PartsDestinationMode.QUICK) { mode = PartsDestinationMode.QUICK }

    when (mode) {
        PartsDestinationMode.QUICK -> {
            PartsHubV2Screen(
                vehicle = vehicle,
                vehicles = vehicles,
                onSelectVehicle = viewModel::selectVehicle,
                onOpenAdvanced = { mode = PartsDestinationMode.GUIDE },
                onMessage = viewModel::showMessage
            )
        }

        PartsDestinationMode.GUIDE -> {
            val plans by viewModel.allMaintenancePlans.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxSize()) {
                PartsModeHeader(
                    title = "دليل السيارة والصيانة",
                    subtitle = "مرجع مستقل • لا يضيف بنودًا للخطة تلقائيًا",
                    onQuick = { mode = PartsDestinationMode.QUICK },
                    actionLabel = "الاحتياج وسجل القطع",
                    actionIcon = Icons.Default.Tune,
                    onAction = { mode = PartsDestinationMode.ADVANCED }
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    OwnerGuideScreen(
                        vehicle = vehicle,
                        plans = plans,
                        onAddPlan = viewModel::addMaintenancePlan,
                        onMessage = viewModel::showMessage
                    )
                }
            }
        }

        PartsDestinationMode.ADVANCED -> {
            val plans by viewModel.allMaintenancePlans.collectAsStateWithLifecycle()
            val maintenance by viewModel.maintenanceHistory.collectAsStateWithLifecycle()
            val faults by viewModel.faults.collectAsStateWithLifecycle()
            val parts by viewModel.parts.collectAsStateWithLifecycle()

            Column(Modifier.fillMaxSize()) {
                PartsModeHeader(
                    title = "مركز قطع الغيار",
                    subtitle = "احتياجي • سجل القطع المركبة",
                    onQuick = { mode = PartsDestinationMode.QUICK },
                    actionLabel = "دليل السيارة",
                    actionIcon = Icons.Default.MenuBook,
                    onAction = { mode = PartsDestinationMode.GUIDE }
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PartsAdvancedCenterScreen(
                        vehicle = vehicle,
                        plans = plans,
                        maintenance = maintenance,
                        faults = faults,
                        parts = parts,
                        onAddPart = viewModel::addPart,
                        onAddMaintenance = viewModel::addMaintenanceRecord,
                        onMessage = viewModel::showMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun PartsModeHeader(
    title: String,
    subtitle: String,
    onQuick: () -> Unit,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onQuick, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("بحث سريع", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(actionIcon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

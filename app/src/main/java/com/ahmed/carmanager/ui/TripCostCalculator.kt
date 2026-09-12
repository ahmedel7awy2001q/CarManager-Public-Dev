package com.ahmed.carmanager.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Compatibility entry point retained for older callers. There is only one pricing UI and one
 * pricing engine now; all calls are forwarded to the route-aware assistant.
 */
@Composable
internal fun TripCostCalculatorSheet(
    vehicle: VehicleEntity,
    onDismiss: () -> Unit,
    appViewModel: CarManagerViewModel = viewModel()
) {
    TripPricingAssistantSheet(vehicle = vehicle, onDismiss = onDismiss, appViewModel = appViewModel)
}

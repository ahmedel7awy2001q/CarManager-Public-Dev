package com.ahmed.carmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmed.carmanager.data.auth.AccountUser
import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Navigation callbacks deliberately live outside the destination composables. Keeping routing here
 * lets CarManagerApp stay subscribed only to account/vehicle identity while each visible screen
 * subscribes to the Room-backed flows it actually needs.
 */
internal data class AppRouteActions(
    val openMain: (MainSection) -> Unit,
    val openMore: (MoreDestination) -> Unit,
    val openAttention: () -> Unit,
    val openDiagnostics: () -> Unit,
    val openInspection: () -> Unit,
    val openTimeline: () -> Unit,
    val openFuel: () -> Unit,
    val openGps: () -> Unit,
    val openHealth: () -> Unit,
    val openMaintenanceSettings: () -> Unit,
    val openAppearance: () -> Unit,
    val openAccount: () -> Unit,
    val openBackup: () -> Unit,
    val openAppDiagnostics: () -> Unit,
    val openQuickAdd: () -> Unit
)

@Composable
internal fun CarManagerScreenHost(
    section: MainSection,
    moreDestination: MoreDestination?,
    showAttentionCenter: Boolean,
    showDiagnostics: Boolean,
    showInspection: Boolean,
    showTimeline: Boolean,
    showFuel: Boolean,
    showGps: Boolean,
    showHealthCenter: Boolean,
    showMaintenanceSettings: Boolean,
    selectedVehicle: VehicleEntity?,
    selectedVehicleId: String?,
    vehicles: List<VehicleEntity>,
    accountUser: AccountUser?,
    viewModel: CarManagerViewModel,
    actions: AppRouteActions
) {
    when {
        showAttentionCenter -> AttentionDestination(selectedVehicle, viewModel, actions)
        showDiagnostics -> DiagnosticsDestination(selectedVehicle, viewModel, actions)
        showInspection -> InspectionDestination(selectedVehicle, viewModel)
        showTimeline -> TimelineDestination(selectedVehicle, viewModel)
        showFuel -> FuelDestination(selectedVehicle, viewModel)
        showGps -> GpsDestination(selectedVehicle, viewModel)
        showHealthCenter -> HealthDestination(selectedVehicle, viewModel, actions)
        showMaintenanceSettings -> MaintenanceSettingsDestination(selectedVehicle, viewModel)
        moreDestination != null -> MoreDestinationContent(
            destination = moreDestination,
            selectedVehicle = selectedVehicle,
            selectedVehicleId = selectedVehicleId,
            vehicles = vehicles,
            viewModel = viewModel
        )
        else -> when (section) {
            MainSection.HOME -> HomeDestination(selectedVehicle, vehicles.count(VehicleLifecyclePolicy::isOperational), viewModel, actions)
            MainSection.MAINTENANCE -> MaintenanceDestination(selectedVehicle, viewModel)
            MainSection.REPORTS -> TripsDestination(selectedVehicle, viewModel)
            MainSection.MORE -> SettingsHubV090Screen(
                vehicle = selectedVehicle,
                accountUser = accountUser,
                onOpen = actions.openMore,
                onMaintenanceSetup = actions.openMaintenanceSettings,
                onHealthCenter = actions.openHealth,
                onInspection = actions.openInspection,
                onDiagnostics = actions.openDiagnostics,
                onTimeline = actions.openTimeline,
                onFuel = actions.openFuel,
                onGps = actions.openGps,
                onAppearance = actions.openAppearance,
                onAccount = actions.openAccount,
                onBackup = actions.openBackup,
                onAppDiagnostics = actions.openAppDiagnostics
            )
            MainSection.ADD -> Unit
        }
    }
}

@Composable
private fun HomeDestination(
    vehicle: VehicleEntity?,
    vehicleCount: Int,
    viewModel: CarManagerViewModel,
    actions: AppRouteActions
) {
    val plans by viewModel.maintenancePlans.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenanceHistory.collectAsStateWithLifecycle()
    val fuel by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val latestGps by viewModel.latestGps.collectAsStateWithLifecycle()

    PremiumDashboardScreen(
        vehicle = vehicle,
        plans = plans,
        maintenance = maintenance,
        fuel = fuel,
        expenses = expenses,
        trips = trips,
        reminders = reminders,
        faults = faults,
        documents = documents,
        latestGps = latestGps,
        onSetOdometer = viewModel::setOdometer,
        vehicleCount = vehicleCount,
        onOpenMaintenance = { actions.openMain(MainSection.MAINTENANCE) },
        onOpenFuel = actions.openFuel,
        onOpenGps = actions.openGps,
        onOpenGarage = { actions.openMore(MoreDestination.GARAGE) },
        onOpenHealth = actions.openHealth,
        onOpenAttention = actions.openAttention,
        onOpenReports = { actions.openMore(MoreDestination.REPORTS) },
        onOpenParts = { actions.openMore(MoreDestination.PARTS) },
        onQuickAdd = actions.openQuickAdd
    )
}

@Composable
private fun MaintenanceDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val plans by viewModel.maintenancePlans.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenanceHistory.collectAsStateWithLifecycle()
    val faults by viewModel.faults.collectAsStateWithLifecycle()

    MaintenanceScreen(
        vehicle = vehicle,
        plans = plans,
        history = maintenance,
        onAddPlan = viewModel::addMaintenancePlan,
        onUpdatePlan = viewModel::updateMaintenancePlan,
        onAddRecord = viewModel::addMaintenanceRecord,
        onMessage = viewModel::showMessage,
        faults = faults
    )
}

@Composable
private fun TripsDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    TripsScreen(vehicle, trips, viewModel::addTrip)
}

@Composable
private fun ReportsDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val fuel by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenanceHistory.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val odometer by viewModel.odometerRecords.collectAsStateWithLifecycle()

    ProfessionalReportsScreen(
        vehicle = vehicle,
        fuel = fuel,
        maintenance = maintenance,
        expenses = expenses,
        trips = trips,
        odometer = odometer
    )
}

@Composable
private fun AttentionDestination(
    vehicle: VehicleEntity?,
    viewModel: CarManagerViewModel,
    actions: AppRouteActions
) {
    val plans by viewModel.maintenancePlans.collectAsStateWithLifecycle()
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    AttentionCenterScreen(
        vehicle = vehicle,
        plans = plans,
        faults = faults,
        documents = documents,
        reminders = reminders,
        onOpenMaintenance = { actions.openMain(MainSection.MAINTENANCE) },
        onOpenFaults = { actions.openMore(MoreDestination.FAULTS) },
        onOpenDocuments = { actions.openMore(MoreDestination.DOCUMENTS) },
        onOpenReminders = { actions.openMore(MoreDestination.REMINDERS) }
    )
}

@Composable
private fun DiagnosticsDestination(
    vehicle: VehicleEntity?,
    viewModel: CarManagerViewModel,
    actions: AppRouteActions
) {
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    ThinkDiagReportScreen(
        vehicle = vehicle,
        faults = faults,
        documents = documents,
        onAddFault = viewModel::addFault,
        onSaveSession = viewModel::addDocument,
        onMessage = viewModel::showMessage,
        onOpenHealth = actions.openHealth,
        onOpenFaults = { actions.openMore(MoreDestination.FAULTS) }
    )
}

@Composable
private fun InspectionDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    VehicleInspectionScreen(
        vehicle = vehicle,
        faults = faults,
        onAddFault = viewModel::addFault,
        onSaveInspection = viewModel::addDocument,
        onSaveTemplateConfig = viewModel::saveInspectionTemplateConfig,
        onMessage = viewModel::showMessage
    )
}

@Composable
private fun TimelineDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    // Timeline is intentionally the only destination that subscribes to the full operational set.
    val maintenance by viewModel.maintenanceHistory.collectAsStateWithLifecycle()
    val fuel by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val odometer by viewModel.odometerRecords.collectAsStateWithLifecycle()
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    val tires by viewModel.tires.collectAsStateWithLifecycle()
    val batteries by viewModel.batteries.collectAsStateWithLifecycle()

    UnifiedTimelineScreen(
        vehicle = vehicle,
        maintenance = maintenance,
        fuel = fuel,
        expenses = expenses,
        trips = trips,
        odometer = odometer,
        faults = faults,
        documents = documents,
        parts = parts,
        tires = tires,
        batteries = batteries
    )
}

@Composable
private fun FuelDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val fuel by viewModel.fuelRecords.collectAsStateWithLifecycle()
    FuelScreen(
        vehicle = vehicle,
        records = fuel,
        onAdd = viewModel::addFuel,
        onMessage = viewModel::showMessage
    )
}

@Composable
private fun GpsDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val devices by viewModel.gpsDevices.collectAsStateWithLifecycle()
    val latest by viewModel.latestGps.collectAsStateWithLifecycle()
    GpsScreen(
        vehicle = vehicle,
        devices = devices,
        latest = latest,
        onAddDevice = viewModel::addGpsDevice,
        onCalibrate = viewModel::calibrateGps,
        hasCredentials = viewModel::hasGpsCredentials,
        accountFor = viewModel::gpsAccount,
        onSaveCredentials = viewModel::saveGpsCredentials,
        onSyncNow = viewModel::syncGpsNow
    )
}

@Composable
private fun HealthDestination(
    vehicle: VehicleEntity?,
    viewModel: CarManagerViewModel,
    actions: AppRouteActions
) {
    val plans by viewModel.allMaintenancePlans.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    val tires by viewModel.tires.collectAsStateWithLifecycle()
    val batteries by viewModel.batteries.collectAsStateWithLifecycle()
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    VehicleHealthScreen(
        vehicle = vehicle,
        plans = plans,
        parts = parts,
        tires = tires,
        batteries = batteries,
        faults = faults,
        documents = documents,
        reminders = reminders,
        onOpenMaintenance = { actions.openMain(MainSection.MAINTENANCE) },
        onOpenDocuments = { actions.openMore(MoreDestination.DOCUMENTS) },
        onOpenParts = { actions.openMore(MoreDestination.PARTS) },
        onOpenFaults = { actions.openMore(MoreDestination.FAULTS) },
        onOpenInspection = actions.openInspection,
        onOpenDiagnostics = actions.openDiagnostics
    )
}

@Composable
private fun MaintenanceSettingsDestination(vehicle: VehicleEntity?, viewModel: CarManagerViewModel) {
    val plans by viewModel.allMaintenancePlans.collectAsStateWithLifecycle()
    MaintenanceSettingsScreen(
        vehicle = vehicle,
        plans = plans,
        onInstallPreset = viewModel::installMaintenancePreset,
        onAddPlan = viewModel::addMaintenancePlan,
        onUpdatePlan = viewModel::updateMaintenancePlan,
        onSetActive = viewModel::setMaintenancePlanActive,
        onDeletePlan = viewModel::deleteMaintenancePlan
    )
}

@Composable
private fun MoreDestinationContent(
    destination: MoreDestination,
    selectedVehicle: VehicleEntity?,
    selectedVehicleId: String?,
    vehicles: List<VehicleEntity>,
    viewModel: CarManagerViewModel
) {
    when (destination) {
        MoreDestination.TRIPS -> TripsDestination(selectedVehicle, viewModel)
        MoreDestination.EXPENSES -> {
            val expenses by viewModel.expenses.collectAsStateWithLifecycle()
            ProfessionalExpensesScreen(selectedVehicle, expenses, viewModel::addExpense, viewModel::showMessage)
        }
        MoreDestination.PARTS -> PartsDestination(
            vehicle = selectedVehicle,
            vehicles = vehicles.filter(VehicleLifecyclePolicy::isOperational),
            viewModel = viewModel
        )
        MoreDestination.TIRES_BATTERY -> {
            val tires by viewModel.tires.collectAsStateWithLifecycle()
            val batteries by viewModel.batteries.collectAsStateWithLifecycle()
            ProfessionalTiresBatteryScreen(selectedVehicle, tires, batteries, viewModel::addTire, viewModel::addBattery)
        }
        MoreDestination.FAULTS -> {
            val faults by viewModel.faults.collectAsStateWithLifecycle()
            FaultsScreen(selectedVehicle, faults, viewModel::addFault)
        }
        MoreDestination.DOCUMENTS -> {
            val documents by viewModel.documents.collectAsStateWithLifecycle()
            ProfessionalDocumentsScreen(selectedVehicle, documents, viewModel::addDocument, viewModel::showMessage)
        }
        MoreDestination.REMINDERS -> {
            val reminders by viewModel.reminders.collectAsStateWithLifecycle()
            RemindersScreen(selectedVehicle, reminders, viewModel::addReminder, viewModel::completeReminder)
        }
        MoreDestination.REPORTS -> ReportsDestination(selectedVehicle, viewModel)
        MoreDestination.GARAGE -> GarageScreen(
            vehicles = vehicles,
            selectedVehicleId = selectedVehicleId,
            onSelect = viewModel::selectVehicle,
            onAdd = viewModel::addVehicle,
            onUpdate = viewModel::updateVehicle,
            onMakePrimary = viewModel::makePrimary,
            onArchive = viewModel::archive,
            onRestore = viewModel::restore,
            onSold = viewModel::markSold,
            onSoftDelete = viewModel::softDeleteVehicle
        )
    }
}

@Composable
internal fun GlobalSearchDestination(
    vehicle: VehicleEntity?,
    viewModel: CarManagerViewModel,
    onNavigate: (GlobalSearchTarget) -> Unit,
    onDismiss: () -> Unit
) {
    // The expensive all-record search graph exists only while the sheet is actually visible.
    val plans by viewModel.maintenancePlans.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenanceHistory.collectAsStateWithLifecycle()
    val fuel by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    val faults by viewModel.faults.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    GlobalSearchSheet(
        vehicle = vehicle,
        plans = plans,
        maintenance = maintenance,
        fuel = fuel,
        expenses = expenses,
        trips = trips,
        parts = parts,
        faults = faults,
        documents = documents,
        onOpenMaintenance = { onNavigate(GlobalSearchTarget.MAINTENANCE) },
        onOpenFaults = { onNavigate(GlobalSearchTarget.FAULTS) },
        onOpenDocuments = { onNavigate(GlobalSearchTarget.DOCUMENTS) },
        onOpenFuel = { onNavigate(GlobalSearchTarget.FUEL) },
        onOpenTrips = { onNavigate(GlobalSearchTarget.TRIPS) },
        onOpenExpenses = { onNavigate(GlobalSearchTarget.EXPENSES) },
        onOpenParts = { onNavigate(GlobalSearchTarget.PARTS) },
        onOpenTimeline = { onNavigate(GlobalSearchTarget.TIMELINE) },
        onDismiss = onDismiss
    )
}

internal enum class GlobalSearchTarget {
    MAINTENANCE, FAULTS, DOCUMENTS, FUEL, TRIPS, EXPENSES, PARTS, TIMELINE
}

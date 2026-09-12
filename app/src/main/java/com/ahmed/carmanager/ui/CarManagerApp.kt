package com.ahmed.carmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed.carmanager.CarManagerApplication

/**
 * Root navigation stays deliberately light. Room-backed vehicle records are collected by the
 * currently visible destination in CarManagerScreenHost instead of being subscribed here forever.
 */
internal enum class MainSection(val label: String) {
    HOME("الرئيسية"), MAINTENANCE("الصيانة"), ADD("إضافة"), REPORTS("المشاوير"), MORE("المزيد")
}

private data class PageSnapshot(
    val section: MainSection,
    val moreDestination: MoreDestination?,
    val maintenanceSettings: Boolean,
    val healthCenter: Boolean,
    val attentionCenter: Boolean,
    val inspection: Boolean,
    val diagnostics: Boolean,
    val timeline: Boolean,
    val fuel: Boolean,
    val gps: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarManagerApp(modifier: Modifier = Modifier, viewModel: CarManagerViewModel = viewModel()) {
    val accountUser by viewModel.accountUser.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
    val cloudBusy by viewModel.cloudBusy.collectAsStateWithLifecycle()
    val hasUnclaimed by viewModel.hasUnclaimedVehicles.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val appearancePreferences = remember(context) {
        (context.applicationContext as CarManagerApplication).container.appearancePreferences
    }
    val themeMode by appearancePreferences.themeMode.collectAsStateWithLifecycle()
    val accentPalette by appearancePreferences.accentPalette.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is AppUiEvent.Message) snackbarHostState.showSnackbar(event.textAr)
        }
    }

    if (accountUser == null) {
        Box(modifier.fillMaxSize()) {
            SignInScreen(
                busy = authBusy,
                onSignInEmail = viewModel::signInWithEmail,
                onCreateAccount = viewModel::createAccount,
                onGoogleToken = viewModel::signInWithGoogle,
                onResetPassword = viewModel::sendPasswordReset,
                onMessage = viewModel::showMessage
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            )
        }
        return
    }

    // These are the only vehicle streams the root needs on every screen.
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val selectedVehicle by viewModel.selectedVehicle.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(MainSection.HOME) }
    var moreDestination by remember { mutableStateOf<MoreDestination?>(null) }
    var showMaintenanceSettings by remember { mutableStateOf(false) }
    var showHealthCenter by remember { mutableStateOf(false) }
    var showAttentionCenter by remember { mutableStateOf(false) }
    var showInspection by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var showFuel by remember { mutableStateOf(false) }
    var showGps by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showAppDiagnostics by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var legacyPromptDismissed by remember(accountUser?.uid) { mutableStateOf(false) }

    var pageHistory by remember { mutableStateOf<List<PageSnapshot>>(emptyList()) }
    var restoringHistory by remember { mutableStateOf(false) }
    var observedPage by remember { mutableStateOf<PageSnapshot?>(null) }

    fun pageSnapshot() = PageSnapshot(
        section = section,
        moreDestination = moreDestination,
        maintenanceSettings = showMaintenanceSettings,
        healthCenter = showHealthCenter,
        attentionCenter = showAttentionCenter,
        inspection = showInspection,
        diagnostics = showDiagnostics,
        timeline = showTimeline,
        fuel = showFuel,
        gps = showGps
    )

    fun restorePage(snapshot: PageSnapshot) {
        section = snapshot.section
        moreDestination = snapshot.moreDestination
        showMaintenanceSettings = snapshot.maintenanceSettings
        showHealthCenter = snapshot.healthCenter
        showAttentionCenter = snapshot.attentionCenter
        showInspection = snapshot.inspection
        showDiagnostics = snapshot.diagnostics
        showTimeline = snapshot.timeline
        showFuel = snapshot.fuel
        showGps = snapshot.gps
    }

    val trackedPage = pageSnapshot()
    LaunchedEffect(trackedPage) {
        val previous = observedPage
        if (previous == null) {
            observedPage = trackedPage
        } else if (previous != trackedPage) {
            if (restoringHistory) restoringHistory = false
            else pageHistory = (pageHistory + previous).takeLast(40)
            observedPage = trackedPage
        }
    }

    fun navigateBackPage(): Boolean {
        val previous = pageHistory.lastOrNull() ?: return false
        pageHistory = pageHistory.dropLast(1)
        restoringHistory = true
        restorePage(previous)
        return true
    }

    fun clearDestinationOverlays() {
        moreDestination = null
        showMaintenanceSettings = false
        showHealthCenter = false
        showAttentionCenter = false
        showInspection = false
        showDiagnostics = false
        showTimeline = false
        showFuel = false
        showGps = false
    }

    fun openMain(target: MainSection) {
        clearDestinationOverlays()
        section = if (target == MainSection.ADD) section else target
    }

    fun openMore(target: MoreDestination) {
        clearDestinationOverlays()
        moreDestination = target
    }

    fun openSingleDestination(setter: () -> Unit) {
        clearDestinationOverlays()
        setter()
    }

    fun closeSubPage() {
        when {
            showAttentionCenter -> showAttentionCenter = false
            showDiagnostics -> showDiagnostics = false
            showInspection -> showInspection = false
            showTimeline -> showTimeline = false
            showFuel -> showFuel = false
            showGps -> showGps = false
            showHealthCenter -> showHealthCenter = false
            showMaintenanceSettings -> showMaintenanceSettings = false
            moreDestination != null -> moreDestination = null
        }
    }

    val isSubPage = moreDestination != null || showMaintenanceSettings || showHealthCenter ||
        showAttentionCenter || showInspection || showDiagnostics || showTimeline || showFuel || showGps

    val pageTitle = when {
        showAttentionCenter -> "مركز الانتباه"
        showDiagnostics -> "ThinkDiag والتشخيص"
        showInspection -> "فحص المركبة"
        showTimeline -> "تاريخ المركبة"
        showFuel -> "الوقود والطاقة"
        showGps -> "GPS"
        showHealthCenter -> "صحة المركبة"
        showMaintenanceSettings -> "قائمة الصيانة"
        moreDestination != null -> moreDestination!!.label
        section == MainSection.HOME -> "إدارة المركبات"
        section == MainSection.MAINTENANCE -> "الصيانة"
        section == MainSection.REPORTS -> "المشاوير"
        section == MainSection.MORE -> "المزيد"
        else -> "إدارة المركبات"
    }

    val routeActions = AppRouteActions(
        openMain = ::openMain,
        openMore = ::openMore,
        openAttention = { openSingleDestination { showAttentionCenter = true } },
        openDiagnostics = { openSingleDestination { showDiagnostics = true } },
        openInspection = { openSingleDestination { showInspection = true } },
        openTimeline = { openSingleDestination { showTimeline = true } },
        openFuel = { openSingleDestination { showFuel = true } },
        openGps = { openSingleDestination { showGps = true } },
        openHealth = { openSingleDestination { showHealthCenter = true } },
        openMaintenanceSettings = { openSingleDestination { showMaintenanceSettings = true } },
        openAppearance = { showAppearance = true },
        openAccount = { showAccount = true },
        openBackup = { showBackup = true },
        openAppDiagnostics = { showAppDiagnostics = true },
        openQuickAdd = { showQuickAdd = true }
    )

    BackHandler {
        when {
            showExitConfirmation -> showExitConfirmation = false
            showAppearance -> showAppearance = false
            showQuickAdd -> showQuickAdd = false
            showAccount -> showAccount = false
            showBackup -> showBackup = false
            showGlobalSearch -> showGlobalSearch = false
            showAppDiagnostics -> showAppDiagnostics = false
            isSubPage || section != MainSection.HOME -> {
                if (!navigateBackPage()) {
                    if (isSubPage) closeSubPage() else openMain(MainSection.HOME)
                }
            }
            else -> showExitConfirmation = true
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                isSubPage -> TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        Surface(
                            onClick = { if (!navigateBackPage()) closeSubPage() },
                            modifier = Modifier.padding(start = 8.dp).size(36.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "رجوع", Modifier.size(19.dp))
                            }
                        }
                    },
                    title = { Text(pageTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
                )
                section != MainSection.HOME -> TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Text(pageTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            selectedVehicle?.let {
                                Text(
                                    it.displayName ?: "${it.brand} ${it.model}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showGlobalSearch = true }) { Icon(CMIcons.Search, "بحث شامل") }
                        IconButton(onClick = { showAccount = true }) {
                            Icon(if (cloudBusy) Icons.Rounded.CloudSync else Icons.Rounded.AccountCircle, "الحساب")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isSubPage) {
                Surface(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AutomotiveNavButton(MainSection.HOME, section == MainSection.HOME) { openMain(MainSection.HOME) }
                        AutomotiveNavButton(MainSection.MAINTENANCE, section == MainSection.MAINTENANCE) { openMain(MainSection.MAINTENANCE) }
                        Surface(
                            onClick = { showQuickAdd = true },
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = autoToneColors(AutoTone.TEAL).strong,
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(CMIcons.Add, "إضافة", Modifier.size(20.dp), tint = Color.White)
                            }
                        }
                        AutomotiveNavButton(MainSection.REPORTS, section == MainSection.REPORTS) { openMain(MainSection.REPORTS) }
                        AutomotiveNavButton(MainSection.MORE, section == MainSection.MORE) { openMain(MainSection.MORE) }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            CarManagerScreenHost(
                section = section,
                moreDestination = moreDestination,
                showAttentionCenter = showAttentionCenter,
                showDiagnostics = showDiagnostics,
                showInspection = showInspection,
                showTimeline = showTimeline,
                showFuel = showFuel,
                showGps = showGps,
                showHealthCenter = showHealthCenter,
                showMaintenanceSettings = showMaintenanceSettings,
                selectedVehicle = selectedVehicle,
                selectedVehicleId = selectedVehicleId,
                vehicles = vehicles,
                accountUser = accountUser,
                viewModel = viewModel,
                actions = routeActions
            )
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            vehicle = selectedVehicle,
            onDismiss = { showQuickAdd = false },
            onFuel = { showQuickAdd = false; routeActions.openFuel() },
            onMaintenance = { showQuickAdd = false; openMain(MainSection.MAINTENANCE) },
            onExpense = { showQuickAdd = false; openMore(MoreDestination.EXPENSES) },
            onTrip = { showQuickAdd = false; openMain(MainSection.REPORTS) },
            onReminder = { showQuickAdd = false; openMore(MoreDestination.REMINDERS) },
            onDocument = { showQuickAdd = false; openMore(MoreDestination.DOCUMENTS) },
            onFault = { showQuickAdd = false; openMore(MoreDestination.FAULTS) },
            onInspection = { showQuickAdd = false; routeActions.openInspection() },
            onSetOdometer = viewModel::setOdometer
        )
    }

    if (showGlobalSearch) {
        GlobalSearchDestination(
            vehicle = selectedVehicle,
            viewModel = viewModel,
            onNavigate = { target ->
                showGlobalSearch = false
                when (target) {
                    GlobalSearchTarget.MAINTENANCE -> openMain(MainSection.MAINTENANCE)
                    GlobalSearchTarget.FAULTS -> openMore(MoreDestination.FAULTS)
                    GlobalSearchTarget.DOCUMENTS -> openMore(MoreDestination.DOCUMENTS)
                    GlobalSearchTarget.FUEL -> routeActions.openFuel()
                    GlobalSearchTarget.TRIPS -> openMain(MainSection.REPORTS)
                    GlobalSearchTarget.EXPENSES -> openMore(MoreDestination.EXPENSES)
                    GlobalSearchTarget.PARTS -> openMore(MoreDestination.PARTS)
                    GlobalSearchTarget.TIMELINE -> routeActions.openTimeline()
                }
            },
            onDismiss = { showGlobalSearch = false }
        )
    }

    if (showAppDiagnostics) {
        AppDiagnosticsSheet(onDismiss = { showAppDiagnostics = false })
    }

    if (showAppearance) {
        AppearanceSheet(
            themeMode = themeMode,
            accentPalette = accentPalette,
            onThemeMode = appearancePreferences::setThemeMode,
            onAccent = appearancePreferences::setAccentPalette,
            onDismiss = { showAppearance = false }
        )
    }

    if (hasUnclaimed && !legacyPromptDismissed) {
        AlertDialog(
            onDismissRequest = { legacyPromptDismissed = true },
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text("ربط بياناتك الحالية بالحساب") },
            text = {
                Text(
                    "وجدنا مركبة أو مركبات محفوظة قبل إضافة نظام الحسابات. ربطها الآن يجعلها خاصة بهذا الحساب ويمنع ظهورها لأي حساب آخر على نفس الهاتف."
                )
            },
            confirmButton = { Button(onClick = viewModel::claimLegacyVehicles) { Text("ربط بحسابي") } },
            dismissButton = {
                TextButton(onClick = {
                    legacyPromptDismissed = true
                    showAccount = true
                }) { Text("لاحقًا") }
            }
        )
    }

    if (showAccount) {
        ModalBottomSheet(onDismissRequest = { showAccount = false }) {
            accountUser?.let { user ->
                AccountSheet(
                    user = user,
                    vehicleCount = vehicles.size,
                    hasUnclaimedVehicles = hasUnclaimed,
                    cloudBusy = cloudBusy,
                    accountBusy = authBusy,
                    onUpdateDisplayName = viewModel::updateAccountDisplayName,
                    onSendVerification = viewModel::sendVerificationEmail,
                    onRefreshAccount = viewModel::refreshAccount,
                    onResetPassword = viewModel::resetCurrentAccountPassword,
                    onClaimLegacy = viewModel::claimLegacyVehicles,
                    onSyncCloud = viewModel::syncCloudNow,
                    onRestoreCloud = viewModel::restoreCloudNow,
                    onOpenBackup = {
                        showAccount = false
                        showBackup = true
                    },
                    onSignOut = {
                        showAccount = false
                        viewModel.signOut()
                    },
                    onDismiss = { showAccount = false }
                )
            }
        }
    }

    if (showBackup) {
        ModalBottomSheet(onDismissRequest = { showBackup = false }) {
            BackupSheet(
                onDismiss = { showBackup = false },
                onExport = viewModel::exportBackup,
                onImport = viewModel::importBackup
            )
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
            title = { Text("إغلاق إدارة المركبات؟") },
            text = { Text("أنت الآن في الشاشة الرئيسية. هل تريد إغلاق التطبيق؟") },
            confirmButton = {
                val activity = androidx.activity.compose.LocalActivity.current
                Button(onClick = {
                    showExitConfirmation = false
                    activity?.finish()
                }) { Text("إغلاق") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun AutomotiveNavButton(item: MainSection, selected: Boolean, onClick: () -> Unit) {
    val (icon, tone) = when (item) {
        MainSection.HOME -> CMIcons.Vehicle to AutoTone.TEAL
        MainSection.MAINTENANCE -> CMIcons.Maintenance to AutoTone.CORAL
        MainSection.REPORTS -> CMIcons.Trip to AutoTone.VIOLET
        MainSection.MORE -> CMIcons.Hub to AutoTone.GRAPHITE
        MainSection.ADD -> CMIcons.Add to AutoTone.TEAL
    }
    val c = autoToneColors(tone)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(54.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) c.soft else Color.Transparent
    ) {
        Column(
            Modifier.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                item.label,
                Modifier.size(19.dp),
                tint = if (selected) c.strong else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) c.strong else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

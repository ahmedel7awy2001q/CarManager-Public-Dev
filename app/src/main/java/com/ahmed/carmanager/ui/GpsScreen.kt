package com.ahmed.carmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ahmed.carmanager.CarManagerApplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.gps.LiveTripStatsCalculator
import com.ahmed.carmanager.data.gps.AccessoryTrackerStore
import com.ahmed.carmanager.data.gps.TrackerDeviceRole
import com.ahmed.carmanager.data.gps.TrackerLeaseSessionState
import com.ahmed.carmanager.data.repository.GpsDeviceInput
import kotlinx.coroutines.flow.flowOf

@Composable
fun GpsScreen(
    vehicle: VehicleEntity?,
    devices: List<GpsDeviceEntity>,
    latest: GpsReadingEntity?,
    onAddDevice: (GpsDeviceInput) -> Unit,
    onCalibrate: (String, Double, Double) -> Unit,
    hasCredentials: (GpsProvider) -> Boolean,
    accountFor: (GpsProvider) -> String?,
    onSaveCredentials: (GpsProvider, String, String) -> Unit,
    onSyncNow: () -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة من الجراج أولًا.", Icons.Default.LocationOn)
        return
    }
    val context = LocalContext.current
    val trustedBluetoothManager = remember(context) {
        (context.applicationContext as CarManagerApplication)
            .container
            .trustedBluetoothManager
    }

    val accessoryTrackerStore = remember(context) { AccessoryTrackerStore(context.applicationContext) }

    val trustedBluetoothConnectionMonitor = remember(context) {
        (context.applicationContext as CarManagerApplication)
            .container
            .trustedBluetoothConnectionMonitor
    }

    val vehicleContextStore = remember(context) {
        (context.applicationContext as CarManagerApplication)
            .container
            .vehicleContextStore
    }

    val trackerDeviceIdentityStore = remember(context) {
        (context.applicationContext as CarManagerApplication)
            .container
            .trackerDeviceIdentityStore
    }

    val androidAutoConnectionMonitor = remember(context) {
        (context.applicationContext as CarManagerApplication)
            .container
            .androidAutoConnectionMonitor
    }

    val appContainer = remember(context) {
        (context.applicationContext as CarManagerApplication).container
    }

    val liveTripTrackingController =
        appContainer.liveTripTrackingController

    val leaseSession by
        appContainer.trackerLeaseSessionController
            .session
            .collectAsState()

    val trackerRole =
        trackerDeviceIdentityStore.role()

    val trackerSourceLabel =
        when (trackerRole) {
            TrackerDeviceRole.PHONE -> "الهاتف"
            TrackerDeviceRole.HEAD_UNIT -> "شاشة السيارة"
        }

    val openTrip by remember(vehicle.vehicleId) {
        appContainer.database.tripDao()
            .observeOpenTrip(vehicle.vehicleId)
    }.collectAsState(initial = null)


    val liveReadingsFlow =
        remember(
            openTrip?.id,
            openTrip?.gpsDeviceId
        ) {
            val trip = openTrip
            val deviceId = trip?.gpsDeviceId

            if (trip != null && deviceId != null) {
                appContainer.database.gpsDao()
                    .observeReadingsSince(
                        vehicleId = trip.vehicleId,
                        deviceId = deviceId,
                        fromTimestamp = trip.startTime
                    )
            } else {
                flowOf(emptyList<GpsReadingEntity>())
            }
        }

    val liveReadings by
        liveReadingsFlow.collectAsState(
            initial = emptyList()
        )

    val liveTripStats =
        remember(openTrip, liveReadings) {
            openTrip?.let { trip ->
                LiveTripStatsCalculator.calculate(
                    trip,
                    liveReadings
                )
            }
        }
    var locationPermissionMessage by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (granted) {
                locationPermissionMessage = null
                liveTripTrackingController
                    .startManual(vehicle.vehicleId)
            } else {
                locationPermissionMessage =
                    "يلزم السماح بالموقع الدقيق لتسجيل المسار."
            }
        }

    val startLiveTracking = {
        val fine =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        if (fine) {
            locationPermissionMessage = null
            liveTripTrackingController
                .startManual(vehicle.vehicleId)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var credentialsProvider by remember { mutableStateOf<GpsProvider?>(null) }
    var calibrateDevice by remember { mutableStateOf<GpsDeviceEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "GPS والتتبع",
                subtitle = "مصادر الموقع والرحلات والأجهزة المرتبطة بـ ${vehicle.displayName ?: vehicle.model}",
                icon = CMIcons.Gps
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AutomotiveActionCard("مزامنة", "تحديث المصادر", Icons.Default.Refresh, AutoTone.GREEN, Modifier.weight(1f), onClick = onSyncNow)
                AutomotiveActionCard("إضافة جهاز", "GPS أو مزود خارجي", CMIcons.Add, AutoTone.BLUE, Modifier.weight(1f), onClick = { showAdd = true })
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (openTrip == null) {
                        Text(
                            "تتبع الرحلات",
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(6.dp))

                        Button(
                            onClick = startLiveTracking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.GpsFixed, null)
                            Spacer(Modifier.width(6.dp))
                            Text("بدء تتبع الرحلة")
                        }

                        Text(
                            "يتم تسجيل المسار والمسافة والمدة محليا على هذا الجهاز.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        locationPermissionMessage?.let { message ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        val trip = openTrip!!
                        val seconds =
                            (trip.durationSeconds ?: 0L)
                                .coerceAtLeast(0L)

                        val duration =
                            String.format(
                                "%02d:%02d:%02d",
                                seconds / 3600,
                                (seconds % 3600) / 60,
                                seconds % 60
                            )

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    liveTripTrackingController.stop()
                                }
                            ) {
                                Text("إيقاف")
                            }

                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    "رحلة نشطة",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    "${formatKm(trip.distanceKm)} كم • $duration",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Text(
                                    "المصدر: $trackerSourceLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                leaseSession
                                    ?.takeIf {
                                        it.vehicleId == trip.vehicleId &&
                                            it.sessionId == trip.id
                                    }
                                    ?.let { lease ->
                                        val leaseText =
                                            when (lease.state) {
                                                TrackerLeaseSessionState.OWNED ->
                                                    "التتبع متصل"

                                                TrackerLeaseSessionState.DEGRADED_OFFLINE ->
                                                    "التتبع محلي — الإنترنت غير متاح"

                                                TrackerLeaseSessionState.LOST ->
                                                    "تم فقد ملكية التتبع"
                                            }

                                        val leaseColor =
                                            if (lease.state ==
                                                TrackerLeaseSessionState.LOST
                                            ) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }

                                        Text(
                                            leaseText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = leaseColor
                                        )
                                    }

                                liveTripStats?.let { stats ->
                                    Spacer(Modifier.height(5.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                stats.currentSpeedKmh
                                                    ?.let { "${formatKm(it)} كم/س" }
                                                    ?: "—",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "السرعة الآن",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                stats.averageSpeedKmh
                                                    ?.let { "${formatKm(it)} كم/س" }
                                                    ?: "—",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "المتوسط",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                stats.stopCount.toString(),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "التوقفات",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                    Text("آخر حالة", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (latest == null) {
                        Text("لا توجد قراءة GPS محفوظة بعد. بعد ربط الجهاز أدخل بيانات الحساب واضغط مزامنة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            if (latest.connectionStatus == GpsConnectionStatus.ONLINE) "متصل" else "غير متصل",
                            color = if (latest.connectionStatus == GpsConnectionStatus.ONLINE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        latest.speedKmh?.let { Text("السرعة: ${formatKm(it)} كم/س", style = MaterialTheme.typography.bodySmall) }
                        latest.todayMileageKm?.let { Text("مسافة اليوم: ${formatKm(it)} كم", style = MaterialTheme.typography.bodySmall) }
                        latest.gpsMileageKm?.let { Text("عداد GPS: ${formatKm(it)} كم", style = MaterialTheme.typography.bodySmall) }
                        latest.calculatedVehicleOdometerKm?.let { Text("عداد المركبة المحسوب: ${formatKm(it)} كم", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                        latest.externalVoltage?.let { Text("جهد الجهاز: ${formatKm(it)} V", style = MaterialTheme.typography.bodySmall) }
                        if (latest.latitude != null && latest.longitude != null) Text("الموقع: ${formatKm(latest.latitude)}, ${formatKm(latest.longitude)}", style = MaterialTheme.typography.bodySmall)
                        Text("آخر تحديث: ${formatDate(latest.timestamp)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            SectionHeader("حسابات التتبع", "بيانات الدخول مشفرة داخل الهاتف ولا تُحفظ في Room")
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProviderCredentialCard("iTrack", hasCredentials(GpsProvider.ITRACK), { credentialsProvider = GpsProvider.ITRACK }, Modifier.weight(1f))
                ProviderCredentialCard("eTrack", hasCredentials(GpsProvider.ETRACK), { credentialsProvider = GpsProvider.ETRACK }, Modifier.weight(1f))
            }
        }

        item {
            TrackerDeviceModeSection(
                vehicleId = vehicle.vehicleId,
                identityStore = trackerDeviceIdentityStore,
                contextStore = vehicleContextStore
            )
        }
        item {
            FindMyAccessorySection(accessoryTrackerStore)
        }
        item {
            TrustedBluetoothSection(
                vehicleId = vehicle.vehicleId,
                manager = trustedBluetoothManager,
                connectionMonitor = trustedBluetoothConnectionMonitor
            )
        }
        item {
            AndroidAutoSection(
                vehicleId = vehicle.vehicleId,
                store = vehicleContextStore,
                monitor = androidAutoConnectionMonitor
            )
        }
        item {
            val autoEnabled by remember(vehicle.vehicleId) {
                vehicleContextStore.observeAutoTrackingEnabled(vehicle.vehicleId)
            }.collectAsState(
                initial = vehicleContextStore.autoTrackingEnabled(vehicle.vehicleId)
            )

            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تتبع الرحلات تلقائيا",
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = autoEnabled,
                        onCheckedChange = {
                            vehicleContextStore.setAutoTrackingEnabled(
                                vehicle.vehicleId,
                                it
                            )
                        },
                        enabled = trackerRole == TrackerDeviceRole.PHONE
                    )
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                    Text("تحديث تلقائي", fontWeight = FontWeight.Bold)
                    Text("بعد حفظ بيانات الحساب، يحاول التطبيق مزامنة أجهزة GPS النشطة تلقائيًا كل ساعة عند توفر الإنترنت والبطارية المناسبة. الاتصال قراءة فقط ولا يرسل أوامر تحكم للمركبة.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { Text("الأجهزة المرتبطة بهذه المركبة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (devices.isEmpty()) {
            item { EmptyState("لا يوجد جهاز GPS", "أضف جهاز iTrack أو eTrack واربطه بهذه المركبة فقط.", Icons.Default.GpsFixed) }
        } else {
            items(devices, key = { it.id }) { device ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (device.isActive) "نشط" else "غير نشط", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Text(device.deviceName ?: device.provider.arLabel(), fontWeight = FontWeight.Bold)
                        }
                        Text("المزود: ${device.provider.arLabel()}", style = MaterialTheme.typography.bodySmall)
                        Text("المعرف: ${device.deviceIdentifier}", style = MaterialTheme.typography.bodySmall)
                        if (!device.imei.isNullOrBlank()) Text("IMEI: ${device.imei}", style = MaterialTheme.typography.bodySmall)
                        device.installedOdometerKm?.let { Text("عداد التركيب: ${formatKm(it)} كم", style = MaterialTheme.typography.bodySmall) }
                        device.lastSyncAt?.let { Text("آخر مزامنة: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { calibrateDevice = device }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("معايرة العداد") }
                    }
                }
            }
        }
    }

    if (showAdd) GpsDeviceDialog(vehicle, { showAdd = false }) { onAddDevice(it); showAdd = false }
    calibrateDevice?.let { device ->
        GpsCalibrationDialog(vehicle, device, { calibrateDevice = null }) { gps, currentVehicleKm ->
            onCalibrate(device.id, gps, currentVehicleKm); calibrateDevice = null
        }
    }
    credentialsProvider?.let { provider ->
        GpsCredentialsDialog(
            provider = provider,
            existingAccount = accountFor(provider).orEmpty(),
            onDismiss = { credentialsProvider = null },
            onSave = { account, password -> onSaveCredentials(provider, account, password); credentialsProvider = null }
        )
    }
}

@Composable
private fun ProviderCredentialCard(label: String, connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(if (connected) "بيانات الدخول محفوظة ✓" else "إعداد الحساب", color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GpsCredentialsDialog(provider: GpsProvider, existingAccount: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var account by remember(provider) { mutableStateOf(existingAccount) }
    var password by remember(provider) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيانات ${provider.arLabel()}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("تُشفّر كلمة المرور بواسطة Android Keystore ولا تظهر داخل قاعدة بيانات المركبات.", style = MaterialTheme.typography.bodySmall)
                AppField(account, { account = it }, "اسم الحساب")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(enabled = account.isNotBlank() && password.isNotBlank(), onClick = { onSave(account.trim(), password) }) { Text("حفظ مشفر") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun GpsDeviceDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (GpsDeviceInput) -> Unit) {
    var provider by remember { mutableStateOf(GpsProvider.ITRACK) }
    var identifier by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var gpsMileage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط جهاز GPS") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnumSelector("مزود GPS", listOf(GpsProvider.ITRACK, GpsProvider.ETRACK, GpsProvider.GENERIC), provider, { provider = it }) { it.arLabel() }
                AppField(identifier, { identifier = it }, "معرف الجهاز")
                AppField(name, { name = it }, "اسم الجهاز - اختياري")
                AppField(imei, { imei = numericInput(it) }, "IMEI - مهم للمزامنة")
                AppField(gpsMileage, { gpsMileage = numericInput(it) }, "قراءة GPS الحالية - اختياري")
                Text("لـ iTrack وeTrack يفضّل إدخال IMEI كما يظهر في تطبيق التتبع.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = identifier.isNotBlank() || imei.isNotBlank(), onClick = {
                onSave(
                    GpsDeviceInput(
                        provider = provider,
                        deviceIdentifier = identifier.ifBlank { imei },
                        deviceName = name,
                        imei = imei,
                        installedOdometerKm = vehicle.currentOdometerKm,
                        providerMileageAtInstallKm = gpsMileage.toDoubleOrNull()
                    )
                )
            }) { Text("ربط") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun GpsCalibrationDialog(vehicle: VehicleEntity, device: GpsDeviceEntity, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var gps by remember { mutableStateOf(device.providerMileageAtInstallKm?.toString() ?: "") }
    var vehicleKm by remember { mutableStateOf(vehicle.currentOdometerKm.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("معايرة عداد GPS") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("يحفظ التطبيق نقطة مرجعية مستقلة لهذه المركبة، لذلك يمكن نقل نفس جهاز GPS إلى مركبة أخرى دون خلط العدادات.", style = MaterialTheme.typography.bodySmall)
                AppField(gps, { gps = numericInput(it) }, "قراءة GPS")
                AppField(vehicleKm, { vehicleKm = numericInput(it) }, "عداد المركبة الحقيقي")
                val offset = gps.toDoubleOrNull()?.let { g -> vehicleKm.toDoubleOrNull()?.let { current -> current - g } }
                offset?.let { Text("فرق المعايرة: ${formatKm(it)} كم", fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            Button(
                enabled = gps.toDoubleOrNull() != null && vehicleKm.toDoubleOrNull()?.let { it >= vehicle.currentOdometerKm } == true,
                onClick = { onSave(gps.toDouble(), vehicleKm.toDouble()) }
            ) { Text("حفظ المعايرة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

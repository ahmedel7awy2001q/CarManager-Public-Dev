@file:Suppress("DEPRECATION")

package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.rounded.List

import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenanceCatalog
import com.ahmed.carmanager.data.maintenance.MaintenanceGuidanceCatalog
import com.ahmed.carmanager.data.maintenance.MaintenanceTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    vehicles: List<VehicleEntity>,
    selectedVehicleId: String?,
    onSelect: (String) -> Unit,
    onAdd: (NewVehicleInput) -> Unit,
    onUpdate: (EditVehicleInput) -> Unit,
    onMakePrimary: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onSold: (String, Double, Double?) -> Unit
) {
    var showTypeChooser by remember { mutableStateOf(false) }
    var addVehicleType by remember { mutableStateOf<VehicleType?>(null) }
    var addVehicleDirty by remember { mutableStateOf(false) }
    var confirmDiscardAdd by remember { mutableStateOf(false) }
    var editVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var editVehicleDirty by remember { mutableStateOf(false) }
    var confirmDiscardEdit by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filteredVehicles = remember(vehicles, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) vehicles else vehicles.filter { vehicle ->
            listOf(vehicle.displayName, vehicle.brand, vehicle.model, vehicle.plateNumber, vehicle.vin, vehicle.year.toString())
                .filterNotNull().joinToString(" ").lowercase().contains(q)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (vehicles.isEmpty()) {
            EmptyGarageState(onAdd = { showTypeChooser = true })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CMPremium.ScreenPadding, 10.dp, CMPremium.ScreenPadding, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GarageCockpitHeader(vehicles.size)
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text("بحث بالاسم أو الماركة أو رقم اللوحة...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "مسح") } }
                    )
                }
                if (filteredVehicles.isEmpty()) {
                    item { EmptyState("لا توجد مركبة مطابقة", "غيّر كلمة البحث أو امسحها لعرض كل المركبات.", Icons.Rounded.SearchOff) }
                }
                items(filteredVehicles, key = { it.vehicleId }) { vehicle ->
                    GarageVehicleCardV8(
                        vehicle = vehicle,
                        selected = vehicle.vehicleId == selectedVehicleId,
                        onSelect = { onSelect(vehicle.vehicleId) },
                        onEdit = { editVehicle = vehicle },
                        onMakePrimary = { onMakePrimary(vehicle.vehicleId) },
                        onArchive = { onArchive(vehicle.vehicleId) },
                        onRestore = { onRestore(vehicle.vehicleId) },
                        onSold = { km, price -> onSold(vehicle.vehicleId, km, price) }
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showTypeChooser = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                icon = { Icon(CMIcons.Add, null) },
                text = { Text("إضافة مركبة") }
            )
        }
    }

    if (showTypeChooser) {
        ModalBottomSheet(onDismissRequest = { showTypeChooser = false }) {
            VehicleTypeChooser(
                onCancel = { showTypeChooser = false },
                onSelected = {
                    showTypeChooser = false
                    addVehicleDirty = false
                    confirmDiscardAdd = false
                    addVehicleType = it
                }
            )
        }
    }

    addVehicleType?.let { type ->
        BackHandler(enabled = !confirmDiscardAdd) {
            if (addVehicleDirty) confirmDiscardAdd = true else addVehicleType = null
        }
        ModalBottomSheet(
            onDismissRequest = {
                if (addVehicleDirty) confirmDiscardAdd = true else addVehicleType = null
            }
        ) {
            AddVehicleForm(
                initialType = type,
                onCancel = {
                    if (addVehicleDirty) confirmDiscardAdd = true else addVehicleType = null
                },
                onDirtyChange = { addVehicleDirty = it },
                onSave = {
                    addVehicleDirty = false
                    onAdd(it)
                    addVehicleType = null
                }
            )
        }
    }

    if (confirmDiscardAdd && addVehicleType != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscardAdd = false },
            title = { Text("تجاهل بيانات المركبة؟") },
            text = { Text("لديك بيانات لم تُحفظ بعد. إذا خرجت الآن ستفقد التعديلات التي أدخلتها في معالج الإضافة.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscardAdd = false
                        addVehicleDirty = false
                        addVehicleType = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("تجاهل البيانات") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardAdd = false }) { Text("متابعة التعديل") } }
        )
    }

    editVehicle?.let { vehicle ->
        BackHandler(enabled = !confirmDiscardEdit) {
            if (editVehicleDirty) confirmDiscardEdit = true else editVehicle = null
        }
        ModalBottomSheet(
            onDismissRequest = {
                if (editVehicleDirty) confirmDiscardEdit = true else editVehicle = null
            }
        ) {
            EditVehicleForm(
                vehicle = vehicle,
                onCancel = {
                    if (editVehicleDirty) confirmDiscardEdit = true else editVehicle = null
                },
                onDirtyChange = { editVehicleDirty = it },
                onSave = {
                    editVehicleDirty = false
                    onUpdate(it)
                    editVehicle = null
                }
            )
        }
    }

    if (confirmDiscardEdit && editVehicle != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscardEdit = false },
            title = { Text("تجاهل التعديلات؟") },
            text = { Text("هناك تعديلات على ملف المركبة لم تُحفظ. يمكنك العودة للنموذج أو تجاهلها.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscardEdit = false
                        editVehicleDirty = false
                        editVehicle = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("تجاهل التعديلات") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardEdit = false }) { Text("متابعة التعديل") } }
        )
    }
}

@Composable
private fun EmptyGarageState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(CMIcons.Garage, null, modifier = Modifier.padding(14.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text("الجراج جاهز لأول مركبة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "أضف سيارة أو موتوسيكل أو أي مركبة أخرى، وسيكون لكل واحدة سجل مستقل داخل حسابك.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Button(onClick = onAdd) {
            Icon(CMIcons.Add, null)
            Spacer(Modifier.width(5.dp))
            Text("إضافة أول مركبة")
        }
    }
}

@Composable
private fun VehicleTypeChooser(onCancel: () -> Unit, onSelected: (VehicleType) -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text("ما نوع المركبة؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("سنُظهر الحقول والفحوصات المناسبة لهذا النوع.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        VehicleTypeChoice(VehicleType.CAR, CMIcons.Vehicle, "سيارة", "ملاكي، SUV، نقل خفيف وغيرها") { onSelected(VehicleType.CAR) }
        Spacer(Modifier.height(6.dp))
        VehicleTypeChoice(VehicleType.MOTORCYCLE, CMIcons.Motorcycle, "موتوسيكل", "موتوسيكل أو سكوتر") { onSelected(VehicleType.MOTORCYCLE) }
        Spacer(Modifier.height(6.dp))
        VehicleTypeChoice(VehicleType.OTHER, CMIcons.Category, "مركبة أخرى", "اكتب نوع المركبة أو صفتها بنفسك") { onSelected(VehicleType.OTHER) }
        TextButton(onClick = onCancel) { Text("إلغاء") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VehicleTypeChoice(type: VehicleType, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, modifier = Modifier.padding(8.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMakePrimary: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onSold: (Double, Double?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSale by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = if (selected) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                VehiclePhoto(vehicle, Modifier.size(width = 72.dp, height = 62.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        if (vehicle.isPrimary) {
                            Spacer(Modifier.width(4.dp))
                            CMStatusChip("أساسية", containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Text(
                        "${vehicle.vehicleType.arLabel(vehicle.customVehicleType)} • ${vehicle.year} • ${formatKm(vehicle.currentOdometerKm)} كم",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (vehicle.status == VehicleStatus.SOLD) { CMStatusChip("مباعة", containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) } else if (vehicle.status == VehicleStatus.ARCHIVED) { CMStatusChip("مؤرشفة") } else if (!vehicle.isPrimary) { CMStatusChip("إضافية") }; if (!vehicle.plateNumber.isNullOrBlank()) {
                        Text("لوحة ${vehicle.plateNumber}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) { Icon(CMIcons.More, "المزيد", Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (vehicle.status == VehicleStatus.ARCHIVED || vehicle.status == VehicleStatus.SOLD) {
                            DropdownMenuItem(
                                text = { Text("إعادة إلى الجراج") },
                                leadingIcon = { Icon(CMIcons.Restore, null) },
                                onClick = { menuExpanded = false; onRestore() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("أرشفة المركبة") },
                                leadingIcon = { Icon(CMIcons.Archive, null) },
                                onClick = { menuExpanded = false; showArchiveConfirm = true }
                            )
                            DropdownMenuItem(
                                text = { Text("تسجيل البيع") },
                                leadingIcon = { Icon(CMIcons.Sell, null) },
                                onClick = { menuExpanded = false; showSale = true }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!vehicle.isPrimary && vehicle.status != VehicleStatus.ARCHIVED && vehicle.status != VehicleStatus.SOLD) {
                    TextButton(onClick = onMakePrimary, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("جعلها الأساسية") }
                }
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                    Icon(CMIcons.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("تعديل")
                }
            }
        }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            icon = { Icon(CMIcons.Archive, null) },
            title = { Text("أرشفة المركبة؟") },
            text = { Text("لن تُحذف أي بيانات، ويمكن إعادتها إلى الجراج في أي وقت.") },
            confirmButton = { Button(onClick = { showArchiveConfirm = false; onArchive() }) { Text("أرشفة") } },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("إلغاء") } }
        )
    }

    if (showSale) {
        var km by remember(vehicle.vehicleId) { mutableStateOf(vehicle.currentOdometerKm.toString()) }
        var price by remember(vehicle.vehicleId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSale = false },
            icon = { Icon(CMIcons.Sell, null) },
            title = { Text("تسجيل بيع المركبة") },
            text = {
                Column(
                    Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("سيبقى تاريخ المركبة كاملًا داخل حسابك.", style = MaterialTheme.typography.bodySmall)
                    AppField(km, { km = numericInput(it) }, "عداد البيع (كم)", keyboardType = KeyboardType.Decimal)
                    AppField(price, { price = numericInput(it) }, "سعر البيع - اختياري", keyboardType = KeyboardType.Decimal)
                }
            },
            confirmButton = {
                Button(
                    enabled = km.toDoubleOrNull()?.let { it >= vehicle.currentOdometerKm } == true,
                    onClick = {
                        onSold(km.toDouble(), price.toDoubleOrNull())
                        showSale = false
                    }
                ) { Text("تسجيل البيع") }
            },
            dismissButton = { TextButton(onClick = { showSale = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun VehiclePhoto(vehicle: VehicleEntity, modifier: Modifier) {
    val shaped = modifier.clip(RoundedCornerShape(15.dp))
    if (!vehicle.vehiclePhotoUri.isNullOrBlank()) {
        AsyncImage(
            model = vehicle.vehiclePhotoUri,
            contentDescription = "صورة المركبة",
            modifier = shaped,
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(shaped, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) {
                Icon(vehicleTypeIcon(vehicle.vehicleType), null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun vehicleTypeIcon(type: VehicleType): ImageVector = when (type) {
    VehicleType.CAR -> CMIcons.Vehicle
    VehicleType.MOTORCYCLE -> CMIcons.Motorcycle
    VehicleType.OTHER -> CMIcons.Category
}

@Composable
private fun AddVehicleForm(initialType: VehicleType, onCancel: () -> Unit, onDirtyChange: (Boolean) -> Unit, onSave: (NewVehicleInput) -> Unit) {
    var state by remember(initialType) { mutableStateOf(VehicleFormState(vehicleType = initialType)) }
    var step by remember { mutableIntStateOf(0) }
    val stepTitles = listOf("بيانات السيارة", "المحرك والفتيس", "آخر الصيانات", "الإطارات والبطارية", "المصاريف والمراجعة")
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Android Photo Picker grants read access even when persistable permission is not exposed.
        }
        state = state.copy(photo = uri.toString())
    }

    val year = state.year.toIntOrNull()
    val tempVehicle = remember(state.brand, state.model, state.displayName, state.year, state.engine, state.engineName, state.engineCode, state.generationCode, state.transmission, state.transmissionName, state.transmissionCode) {
        year?.let {
            VehicleEntity(
                brand = state.brand.ifBlank { "?" }, model = state.model.ifBlank { "?" }, year = it,
                displayName = state.displayName.cleanOrNull(), engineCapacityCc = state.engine.toIntOrNull(),
                engineName = state.engineName.cleanOrNull(), engineCode = state.engineCode.cleanOrNull(), generationCode = state.generationCode.cleanOrNull(),
                transmissionType = state.transmission, transmissionName = state.transmissionName.cleanOrNull(), transmissionCode = state.transmissionCode.cleanOrNull(),
                passengerCapacity = state.passengerCapacity.toIntOrNull(), currentOdometerKm = state.odometer.toDoubleOrNull() ?: 0.0
            )
        }
    }
    val maintenanceTemplates = remember(tempVehicle) {
        tempVehicle?.let { vehicle ->
            MaintenanceGuidanceCatalog.templatesFor(vehicle, MaintenanceCatalog.presetFor(vehicle.brand, vehicle.model, vehicle.year, vehicle.displayName))
        }.orEmpty()
    }
    val baselineDrafts = remember { mutableStateMapOf<String, MaintenanceBaselineDraft>() }
    var editingTemplate by remember { mutableStateOf<MaintenanceTemplate?>(null) }

    var tireBrand by remember { mutableStateOf("") }
    var tireModel by remember { mutableStateOf("") }
    var tireInstallKm by remember { mutableStateOf("") }
    var tireTotalCost by remember { mutableStateOf("") }
    var tireInstallDate by remember { mutableStateOf<Long?>(null) }
    var batteryBrand by remember { mutableStateOf("") }
    var batteryModel by remember { mutableStateOf("") }
    var batteryCapacity by remember { mutableStateOf("") }
    var batteryInstallKm by remember { mutableStateOf("") }
    var batteryCost by remember { mutableStateOf("") }
    var batteryWarranty by remember { mutableStateOf("") }
    var batteryInstallDate by remember { mutableStateOf<Long?>(null) }

    val hasUnsavedChanges =
        state != VehicleFormState(vehicleType = initialType) ||
            baselineDrafts.isNotEmpty() ||
            listOf(
                tireBrand, tireModel, tireInstallKm, tireTotalCost,
                batteryBrand, batteryModel, batteryCapacity, batteryInstallKm, batteryCost, batteryWarranty
            ).any { it.isNotBlank() } ||
            tireInstallDate != null || batteryInstallDate != null
    LaunchedEffect(hasUnsavedChanges) { onDirtyChange(hasUnsavedChanges) }

    val maxVehicleYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1 }
    val yearValid = year != null && year in 1886..maxVehicleYear
    val basicValid = state.brand.isNotBlank() && state.model.isNotBlank() && yearValid &&
        (state.vehicleType != VehicleType.OTHER || state.customVehicleType.isNotBlank())
    val technicalSuggestions = remember(state.brand, state.model, state.year, state.trim, state.vin) {
        VehicleTechnicalSuggestionEngine.suggest(
            brand = state.brand,
            model = state.model,
            year = state.year.toIntOrNull(),
            trim = state.trim,
            vin = state.vin
        )
    }
    val trustedMarketSuggestion = technicalSuggestions.firstOrNull { suggestion ->
        suggestion.confidence >= 90 && (suggestion.transmissionType != null || suggestion.engineCapacityCc != null || suggestion.fuelType != null)
    }
    val vehicleIdentityKey = listOf(state.brand.trim(), state.model.trim(), state.year.trim(), state.trim.trim()).joinToString("|")
    var lastAutoAppliedIdentity by remember(initialType) { mutableStateOf<String?>(null) }
    var suggestionNotice by remember(initialType) { mutableStateOf<String?>(null) }
    LaunchedEffect(vehicleIdentityKey, trustedMarketSuggestion) {
        if (state.brand.isNotBlank() && state.model.isNotBlank() && yearValid &&
            trustedMarketSuggestion != null && lastAutoAppliedIdentity != vehicleIdentityKey
        ) {
            val updated = state.withTechnicalSuggestion(trustedMarketSuggestion)
            if (updated != state) {
                state = updated
                suggestionNotice = "تم ملء المواصفات الموثقة للسوق تلقائيًا ويمكنك مراجعتها قبل الحفظ."
            }
            lastAutoAppliedIdentity = vehicleIdentityKey
        }
    }
    val marketTransmissionIssue = trustedMarketSuggestion?.transmissionType
        ?.takeIf { expected -> expected != state.transmission }
        ?.let { expected ->
            "مواصفة السوق الموثقة لهذا الموديل/السنة هي ${expected.arLabel()} وليست ${state.transmission.arLabel()}. غيّر نوع الفتيس أو استخدم الاقتراح الموثق قبل المتابعة."
        }
    val profileValidationMessage = vehicleProfileValidationMessage(state, minimumOdometerKm = 0.0)
    val technicalValid = profileValidationMessage == null && marketTransmissionIssue == null
    val canContinue = when (step) {
        0 -> basicValid
        1 -> technicalValid
        else -> true
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.End
    ) {
        Text("معالج إعداد السيارة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("${step + 1} من ${stepTitles.size} • ${stepTitles[step]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (step + 1f) / stepTitles.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

        when (step) {
            0 -> {
                FormSection("نوع المركبة", vehicleTypeIcon(state.vehicleType)) {
                    EnumSelector("النوع", VehicleType.entries, state.vehicleType, { state = state.copy(vehicleType = it, customVehicleType = if (it == VehicleType.OTHER) state.customVehicleType else "") }) { it.arLabel() }
                    if (state.vehicleType == VehicleType.OTHER) AppField(state.customVehicleType, { state = state.copy(customVehicleType = it) }, "اسم أو صفة المركبة *")
                }
                FormSection("الصورة والبيانات الأساسية", CMIcons.Camera) {
                    if (state.photo != null) AsyncImage(model = state.photo, contentDescription = null, modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Icon(CMIcons.Camera, null); Spacer(Modifier.width(5.dp)); Text(if (state.photo == null) "اختيار من الصور" else "تغيير الصورة") }
                    if (state.photo != null) {
                        TextButton(onClick = { state = state.copy(photo = null) }, modifier = Modifier.align(Alignment.Start)) { Text("إزالة الصورة") }
                    }
                    AppField(state.displayName, { state = state.copy(displayName = it) }, "اسم مختصر - اختياري")
                    VehicleGuidedSelector(state = state, onState = { state = it })
                    if (state.year.isNotBlank() && !yearValid) {
                        Text("سنة الصنع يجب أن تكون بين 1886 و$maxVehicleYear.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    AppField(state.color, { state = state.copy(color = it) }, "اللون - اختياري")
                }
            }
            1 -> {
                TechnicalSuggestionsSection(
                    suggestions = technicalSuggestions,
                    onApply = { suggestion ->
                        state = state.withTechnicalSuggestion(suggestion)
                        lastAutoAppliedIdentity = vehicleIdentityKey
                        suggestionNotice = "تم تطبيق الاقتراح على الحقول الفنية بالأسفل."
                    }
                )
                suggestionNotice?.let { notice ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                    ) {
                        Text(
                            notice,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End
                        )
                    }
                }
                FormSection("العداد والهوية", CMIcons.Odometer) {
                    AppField(state.odometer, { state = state.copy(odometer = numericInput(it)) }, "العداد الحالي (كم)", keyboardType = KeyboardType.Decimal)
                    AppField(state.plate, { state = state.copy(plate = it) }, "رقم اللوحة - اختياري")
                    AppField(state.vin, { state = state.copy(vin = it) }, "VIN رقم الشاسيه - اختياري")
                    AppField(state.generationCode, { state = state.copy(generationCode = it) }, "كود الجيل / الشاسيه - مثال BD, N17")
                }
                FormSection("المحرك والفتيس", CMIcons.Settings) {
                    EnumSelector("الوقود / الطاقة", FuelType.entries, state.fuelType, { state = state.copy(fuelType = it) }) { it.arLabel() }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(state.engine, { state = state.copy(engine = digitsOnly(it)) }, "المحرك CC", Modifier.weight(1f), KeyboardType.Number)
                        AppField(state.engineName, { state = state.copy(engineName = it) }, "اسم/عائلة المحرك", Modifier.weight(1f))
                    }
                    AppField(state.engineCode, { state = state.copy(engineCode = it) }, "كود المحرك - إن وجد")
                    EnumSelector("نوع الفتيس", TransmissionType.entries, state.transmission, { state = state.copy(transmission = it) }) { it.arLabel() }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(state.transmissionName, { state = state.copy(transmissionName = it) }, "اسم الفتيس (6AT/IVT...)", Modifier.weight(1f))
                        AppField(state.transmissionCode, { state = state.copy(transmissionCode = it) }, "كود الفتيس", Modifier.weight(1f))
                    }
                    profileValidationMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    }
                    marketTransmissionIssue?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    }
                    AppField(state.engineNumber, { state = state.copy(engineNumber = it) }, "رقم المحرك - اختياري")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(state.tank, { state = state.copy(tank = numericInput(it)) }, "سعة الخزان لتر", Modifier.weight(1f), KeyboardType.Decimal)
                        AppField(state.passengerCapacity, { state = state.copy(passengerCapacity = digitsOnly(it)) }, "إجمالي المقاعد", Modifier.weight(1f), KeyboardType.Number)
                    }
                    Text("إجمالي المقاعد يشمل مقعد السائق ويُستخدم للتحقق من سعة رحلات العمل.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            2 -> {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                        Text("آخر الصيانات المعروفة", fontWeight = FontWeight.Black)
                        Text("لا نسجل أي صيانة افتراضية. اضغط فقط على البنود التي تعرف آخر تغيير لها؛ الباقي يظل «غير معروف».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                maintenanceTemplates.forEach { template ->
                    val draft = baselineDrafts[template.titleAr]
                    OutlinedCard(onClick = { editingTemplate = template }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EditCalendar, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(template.titleAr, fontWeight = FontWeight.Bold)
                                val text = buildList {
                                    draft?.lastServiceOdometerKm?.let { add("${formatKm(it)} كم") }
                                    draft?.lastServiceDate?.let { add(formatDate(it)) }
                                }.joinToString(" • ").ifBlank { "آخر تغيير غير معروف" }
                                Text(text, style = MaterialTheme.typography.labelSmall, color = if (draft == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            3 -> {
                FormSection("الإطارات", Icons.Rounded.TireRepair) {
                    Text("إن كانت الإطارات الحالية مركبة منذ قبل إضافة السيارة، سجّل بياناتها مرة واحدة وسينشئ البرنامج أربع سجلات مستقلة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(tireBrand, { tireBrand = it }, "الماركة", Modifier.weight(1f))
                        AppField(tireModel, { tireModel = it }, "الموديل", Modifier.weight(1f))
                    }
                    AppField(state.tireSize, { state = state.copy(tireSize = it) }, "المقاس")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(tireInstallKm, { tireInstallKm = numericInput(it) }, "عداد التركيب", Modifier.weight(1f), KeyboardType.Decimal)
                        AppField(tireTotalCost, { tireTotalCost = numericInput(it) }, "تكلفة الأربع", Modifier.weight(1f), KeyboardType.Decimal)
                    }
                    AppDateSelector("تاريخ التركيب", tireInstallDate, allowClear = true) { tireInstallDate = it }
                }
                FormSection("البطارية", Icons.Rounded.BatteryChargingFull) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(batteryBrand, { batteryBrand = it }, "الماركة", Modifier.weight(1f))
                        AppField(batteryModel, { batteryModel = it }, "الموديل", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(batteryCapacity, { batteryCapacity = numericInput(it) }, "السعة Ah", Modifier.weight(1f), KeyboardType.Decimal)
                        AppField(batteryWarranty, { batteryWarranty = digitsOnly(it) }, "الضمان/شهر", Modifier.weight(1f), KeyboardType.Number)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(batteryInstallKm, { batteryInstallKm = numericInput(it) }, "عداد التركيب", Modifier.weight(1f), KeyboardType.Decimal)
                        AppField(batteryCost, { batteryCost = numericInput(it) }, "التكلفة", Modifier.weight(1f), KeyboardType.Decimal)
                    }
                    AppDateSelector("تاريخ التركيب", batteryInstallDate, allowClear = true) { batteryInstallDate = it }
                }
            }
            else -> {
                FormSection("المصاريف السنوية والتكلفة الحقيقية", CMIcons.Expense) {
                    Text("هذه القيم اختيارية وتصبح الإعداد الافتراضي لحاسبة المشوار، مع إمكانية تشغيل كل مجموعة أو إيقافها لاحقًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(state.annualLicenseCost, { state = state.copy(annualLicenseCost = numericInput(it)) }, "الترخيص/سنة", Modifier.weight(1f), KeyboardType.Decimal)
                        AppField(state.annualInsuranceCost, { state = state.copy(annualInsuranceCost = numericInput(it)) }, "التأمين/سنة", Modifier.weight(1f), KeyboardType.Decimal)
                    }
                    AppField(state.annualOtherFixedCost, { state = state.copy(annualOtherFixedCost = numericInput(it)) }, "مصاريف سنوية أخرى", keyboardType = KeyboardType.Decimal)
                    AppField(state.annualDistanceKm, { state = state.copy(annualDistanceKm = numericInput(it)) }, "المسافة السنوية المتوقعة (كم)", keyboardType = KeyboardType.Decimal)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppField(state.currentMarketValue, { state = state.copy(currentMarketValue = numericInput(it)) }, "قيمة السيارة الحالية", Modifier.weight(1f), KeyboardType.Decimal)
                        AppField(state.depreciationAnnualPercent, { state = state.copy(depreciationAnnualPercent = numericInput(it)) }, "الإهلاك السنوي %", Modifier.weight(1f), KeyboardType.Decimal)
                    }
                    SettingSwitchRow("احتساب المصروفات السنوية في المشوار", state.includeAnnualFixedCosts) { state = state.copy(includeAnnualFixedCosts = it) }
                    SettingSwitchRow("احتساب الإهلاك في المشوار", state.includeDepreciation) { state = state.copy(includeDepreciation = it) }
                }
                Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                        Text("مراجعة سريعة", fontWeight = FontWeight.Black)
                        Text("${state.brand} ${state.model} ${state.year} • ${state.engine.ifBlank { "—" }} CC • ${state.transmission.arLabel()}")
                        Text("تم تعريف آخر صيانة لـ ${baselineDrafts.size} من ${maintenanceTemplates.size} بند", style = MaterialTheme.typography.bodySmall)
                        Text("الإطارات: ${if (tireBrand.isBlank() && state.tireSize.isBlank()) "غير مسجلة" else "جاهزة للتسجيل"} • البطارية: ${if (batteryBrand.isBlank()) "غير مسجلة" else "جاهزة للتسجيل"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (step == 0) onCancel() else step-- }, modifier = Modifier.weight(1f)) { Text(if (step == 0) "إلغاء" else "السابق") }
            if (step < stepTitles.lastIndex) {
                Button(onClick = { step++ }, enabled = canContinue, modifier = Modifier.weight(1f)) { Text("التالي") }
            } else {
                Button(
                    onClick = {
                        val tireDraft = if (tireBrand.isBlank() && tireModel.isBlank() && state.tireSize.isBlank() && tireInstallKm.isBlank() && tireInstallDate == null) null else InitialTireSetDraft(tireBrand.cleanOrNull(), tireModel.cleanOrNull(), state.tireSize.cleanOrNull(), tireInstallKm.toDoubleOrNull(), tireInstallDate, tireTotalCost.toDoubleOrNull())
                        val batteryDraft = if (batteryBrand.isBlank() && batteryModel.isBlank() && batteryCapacity.isBlank() && batteryInstallKm.isBlank() && batteryInstallDate == null) null else InitialBatteryDraft(batteryBrand.cleanOrNull(), batteryModel.cleanOrNull(), batteryCapacity.toDoubleOrNull(), batteryInstallKm.toDoubleOrNull(), batteryInstallDate, batteryCost.toDoubleOrNull(), batteryWarranty.toIntOrNull())
                        onDirtyChange(false)
                        onSave(
                            NewVehicleInput(
                                displayName = state.displayName.cleanOrNull(), brand = state.brand.trim(), model = state.model.trim(), trim = state.trim.cleanOrNull(), year = state.year.toInt(), color = state.color.cleanOrNull(),
                                fuelType = state.fuelType, transmissionType = state.transmission, engineName = state.engineName.cleanOrNull(), engineCode = state.engineCode.cleanOrNull(), engineCapacityCc = state.engine.toIntOrNull(), generationCode = state.generationCode.cleanOrNull(),
                                transmissionName = state.transmissionName.cleanOrNull(), transmissionCode = state.transmissionCode.cleanOrNull(), plateNumber = state.plate.cleanOrNull(), vin = state.vin.cleanOrNull(), engineNumber = state.engineNumber.cleanOrNull(), odometerKm = state.odometer.toDoubleOrNull() ?: 0.0,
                                photoUri = state.photo, tankCapacityLiters = state.tank.toDoubleOrNull(), tireSize = state.tireSize.cleanOrNull(), passengerCapacity = state.passengerCapacity.toIntOrNull(), purchasePrice = state.purchasePrice.toDoubleOrNull(), vehicleType = state.vehicleType, customVehicleType = state.customVehicleType.cleanOrNull(),
                                annualLicenseCost = state.annualLicenseCost.toDoubleOrNull(), annualInsuranceCost = state.annualInsuranceCost.toDoubleOrNull(), annualOtherFixedCost = state.annualOtherFixedCost.toDoubleOrNull(), annualDistanceKm = state.annualDistanceKm.toDoubleOrNull(),
                                currentMarketValue = state.currentMarketValue.toDoubleOrNull(), depreciationAnnualPercent = state.depreciationAnnualPercent.toDoubleOrNull(), includeAnnualFixedCostsInTripCost = state.includeAnnualFixedCosts, includeDepreciationInTripCost = state.includeDepreciation,
                                maintenanceBaseline = baselineDrafts.values.toList(), initialTireSet = tireDraft, initialBattery = batteryDraft
                            )
                        )
                    },
                    enabled = basicValid && technicalValid,
                    modifier = Modifier.weight(1f)
                ) { Text("حفظ الملف الكامل") }
            }
        }
        Spacer(Modifier.height(72.dp))
    }

    editingTemplate?.let { template ->
        var km by remember(template.titleAr) { mutableStateOf(baselineDrafts[template.titleAr]?.lastServiceOdometerKm?.let(::garageEditableNumber).orEmpty()) }
        var date by remember(template.titleAr) { mutableStateOf(baselineDrafts[template.titleAr]?.lastServiceDate) }
        val kmValue = km.toDoubleOrNull()
        val maxKm = state.odometer.toDoubleOrNull() ?: Double.MAX_VALUE
        AlertDialog(
            onDismissRequest = { editingTemplate = null },
            title = { Text(template.titleAr) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("سجّل ما تعرفه فقط. هذه القيمة تحدد موعد الاستحقاق القادم ولا تنشئ عملية صيانة وهمية.", style = MaterialTheme.typography.bodySmall)
                    AppField(km, { km = numericInput(it) }, "آخر تغيير عند عداد (كم)", keyboardType = KeyboardType.Decimal)
                    AppDateSelector("تاريخ آخر تغيير", date, allowClear = true) { date = it }
                    if (kmValue != null && kmValue > maxKm) Text("آخر تغيير لا يمكن أن يتجاوز العداد الحالي.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(enabled = (kmValue == null || kmValue <= maxKm) && (kmValue != null || date != null), onClick = {
                    baselineDrafts[template.titleAr] = MaintenanceBaselineDraft(template.titleAr, kmValue, date)
                    editingTemplate = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                Row {
                    if (baselineDrafts.containsKey(template.titleAr)) TextButton(onClick = { baselineDrafts.remove(template.titleAr); editingTemplate = null }) { Text("جعله غير معروف") }
                    TextButton(onClick = { editingTemplate = null }) { Text("إلغاء") }
                }
            }
        )
    }
}

@Composable
private fun EditVehicleForm(vehicle: VehicleEntity, onCancel: () -> Unit, onDirtyChange: (Boolean) -> Unit, onSave: (EditVehicleInput) -> Unit) {
    val originalState = remember(vehicle.vehicleId) { vehicle.toVehicleFormState() }
    var state by remember(vehicle.vehicleId) { mutableStateOf(originalState) }
    val editDirty = state != originalState
    LaunchedEffect(editDirty) { onDirtyChange(editDirty) }
    val editedOdometer = state.odometer.toDoubleOrNull()
    VehicleForm(
        title = "تعديل المركبة",
        subtitle = "رفع العداد هنا ينشئ قراءة عداد حقيقية في السجل؛ خفضه غير مسموح.",
        state = state,
        onState = { state = it },
        onCancel = onCancel,
        saveLabel = "حفظ التعديلات",
        showIdentityFields = true,
        minimumOdometerKm = vehicle.currentOdometerKm,
        canSave = state.brand.isNotBlank() && state.model.isNotBlank() &&
            vehicleProfileValidationMessage(state, vehicle.currentOdometerKm) == null &&
            editedOdometer?.let { it >= vehicle.currentOdometerKm } == true &&
            (state.vehicleType != VehicleType.OTHER || state.customVehicleType.isNotBlank()),
        onSave = {
            onSave(
                EditVehicleInput(
                    vehicleId = vehicle.vehicleId,
                    displayName = state.displayName.cleanOrNull(),
                    brand = state.brand.trim(),
                    model = state.model.trim(),
                    trim = state.trim.cleanOrNull(),
                    year = state.year.toInt(),
                    color = state.color.cleanOrNull(),
                    fuelType = state.fuelType,
                    transmissionType = state.transmission,
                    engineName = state.engineName.cleanOrNull(),
                    engineCode = state.engineCode.cleanOrNull(),
                    engineCapacityCc = state.engine.toIntOrNull(),
                    generationCode = state.generationCode.cleanOrNull(),
                    transmissionName = state.transmissionName.cleanOrNull(),
                    transmissionCode = state.transmissionCode.cleanOrNull(),
                    plateNumber = state.plate.cleanOrNull(),
                    licenseNumber = vehicle.licenseNumber,
                    odometerKm = editedOdometer,
                    photoUri = state.photo,
                    tankCapacityLiters = state.tank.toDoubleOrNull(),
                    tireSize = state.tireSize.cleanOrNull(),
                    passengerCapacity = state.passengerCapacity.toIntOrNull(),
                    vin = state.vin.cleanOrNull(),
                    engineNumber = state.engineNumber.cleanOrNull(),
                    purchasePrice = state.purchasePrice.toDoubleOrNull(),
                    purchaseDate = vehicle.purchaseDate,
                    vehicleType = state.vehicleType,
                    customVehicleType = state.customVehicleType.cleanOrNull(),
                    annualLicenseCost = state.annualLicenseCost.toDoubleOrNull(),
                    annualInsuranceCost = state.annualInsuranceCost.toDoubleOrNull(),
                    annualOtherFixedCost = state.annualOtherFixedCost.toDoubleOrNull(),
                    annualDistanceKm = state.annualDistanceKm.toDoubleOrNull(),
                    currentMarketValue = state.currentMarketValue.toDoubleOrNull(),
                    depreciationAnnualPercent = state.depreciationAnnualPercent.toDoubleOrNull(),
                    includeAnnualFixedCostsInTripCost = state.includeAnnualFixedCosts,
                    includeDepreciationInTripCost = state.includeDepreciation
                )
            )
        }
    )
}

private data class VehicleFormState(
    val photo: String? = null,
    val displayName: String = "",
    val brand: String = "",
    val model: String = "",
    val trim: String = "",
    val year: String = "",
    val color: String = "",
    val fuelType: FuelType = FuelType.GASOLINE_95,
    val transmission: TransmissionType = TransmissionType.AUTOMATIC,
    val engineName: String = "",
    val engineCode: String = "",
    val engine: String = "",
    val generationCode: String = "",
    val transmissionName: String = "",
    val transmissionCode: String = "",
    val plate: String = "",
    val odometer: String = "",
    val tank: String = "",
    val tireSize: String = "",
    val passengerCapacity: String = "",
    val vin: String = "",
    val engineNumber: String = "",
    val purchasePrice: String = "",
    val vehicleType: VehicleType = VehicleType.CAR,
    val customVehicleType: String = "",
    val annualLicenseCost: String = "",
    val annualInsuranceCost: String = "",
    val annualOtherFixedCost: String = "",
    val annualDistanceKm: String = "",
    val currentMarketValue: String = "",
    val depreciationAnnualPercent: String = "",
    val includeAnnualFixedCosts: Boolean = true,
    val includeDepreciation: Boolean = true
)

private fun VehicleFormState.withTechnicalSuggestion(suggestion: VehicleTechnicalSuggestion): VehicleFormState = copy(
    generationCode = suggestion.generationCode ?: generationCode,
    engine = suggestion.engineCapacityCc?.toString() ?: engine,
    engineName = suggestion.engineName ?: engineName,
    engineCode = suggestion.engineCode ?: engineCode,
    fuelType = suggestion.fuelType ?: fuelType,
    tank = suggestion.tankCapacityLiters?.let { value ->
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    } ?: tank,
    passengerCapacity = suggestion.passengerCapacity?.toString() ?: passengerCapacity,
    transmission = suggestion.transmissionType ?: transmission,
    transmissionName = suggestion.transmissionName ?: transmissionName,
    transmissionCode = suggestion.transmissionCode ?: transmissionCode
)

private fun VehicleEntity.toVehicleFormState(): VehicleFormState = VehicleFormState(
    photo = vehiclePhotoUri,
    displayName = displayName.orEmpty(),
    brand = brand,
    model = model,
    trim = trim.orEmpty(),
    year = year.toString(),
    color = color.orEmpty(),
    fuelType = fuelType,
    transmission = transmissionType,
    engineName = engineName.orEmpty(),
    engineCode = engineCode.orEmpty(),
    engine = engineCapacityCc?.toString().orEmpty(),
    generationCode = generationCode.orEmpty(),
    transmissionName = transmissionName.orEmpty(),
    transmissionCode = transmissionCode.orEmpty(),
    plate = plateNumber.orEmpty(),
    odometer = currentOdometerKm.toString(),
    tank = tankCapacityLiters?.toString().orEmpty(),
    tireSize = tireSize.orEmpty(),
    passengerCapacity = passengerCapacity?.toString().orEmpty(),
    vin = vin.orEmpty(),
    engineNumber = engineNumber.orEmpty(),
    purchasePrice = purchasePrice?.toString().orEmpty(),
    vehicleType = vehicleType,
    customVehicleType = customVehicleType.orEmpty(),
    annualLicenseCost = annualLicenseCost?.let(::garageEditableNumber).orEmpty(),
    annualInsuranceCost = annualInsuranceCost?.let(::garageEditableNumber).orEmpty(),
    annualOtherFixedCost = annualOtherFixedCost?.let(::garageEditableNumber).orEmpty(),
    annualDistanceKm = annualDistanceKm?.let(::garageEditableNumber).orEmpty(),
    currentMarketValue = currentMarketValue?.let(::garageEditableNumber).orEmpty(),
    depreciationAnnualPercent = depreciationAnnualPercent?.let(::garageEditableNumber).orEmpty(),
    includeAnnualFixedCosts = includeAnnualFixedCostsInTripCost,
    includeDepreciation = includeDepreciationInTripCost
)

private fun vehicleProfileValidationMessage(state: VehicleFormState, minimumOdometerKm: Double): String? {
    val maxYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1
    val year = state.year.toIntOrNull()
    if (year == null || year !in 1886..maxYear) return "سنة الصنع غير صحيحة."
    val odometer = state.odometer.toDoubleOrNull()
    if (state.odometer.isNotBlank() && (odometer == null || odometer < minimumOdometerKm)) {
        return "قراءة العداد غير صحيحة أو أقل من آخر عداد محفوظ."
    }
    state.passengerCapacity.toIntOrNull()?.let {
        if (it !in 1..100) return "إجمالي المقاعد يجب أن يكون رقمًا منطقيًا بين 1 و100."
    }
    state.tank.toDoubleOrNull()?.let {
        if (it <= 0.0 || it > 5000.0) return "سعة الخزان تبدو غير منطقية؛ راجع القيمة."
    }
    state.depreciationAnnualPercent.toDoubleOrNull()?.let {
        if (it < 0.0 || it > 100.0) return "نسبة الإهلاك السنوي يجب أن تكون بين 0% و100%."
    }
    state.annualDistanceKm.toDoubleOrNull()?.let {
        if (it <= 0.0 || it > 1_000_000.0) return "المسافة السنوية المتوقعة تبدو غير منطقية؛ راجع القيمة."
    }
    return VehicleTransmissionValidator.conflict(state.transmission, state.transmissionName, state.transmissionCode)
}

private data class VehiclePickerChoice(
    val key: String,
    val label: String,
    val searchText: String = label
)

@Composable
private fun SearchChoiceField(
    label: String,
    value: String,
    choices: List<VehiclePickerChoice>,
    enabled: Boolean = true,
    emptyHint: String = "لا توجد اختيارات جاهزة",
    onSelected: (VehiclePickerChoice) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    OutlinedButton(
        enabled = enabled,
        onClick = { query = ""; open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "اضغط للاختيار" }, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ArrowDropDown, null)
    }

    if (open) {
        val normalizedQuery = VehicleSelectionCatalog.normalize(query)
        val filtered = remember(choices, normalizedQuery) {
            if (normalizedQuery.isBlank()) choices else choices.filter {
                VehicleSelectionCatalog.normalize(it.searchText).contains(normalizedQuery) ||
                    VehicleSelectionCatalog.normalize(it.label).contains(normalizedQuery)
            }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("بحث...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        Text(emptyHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(filtered, key = { it.key }) { choice ->
                                OutlinedCard(
                                    onClick = { onSelected(choice); open = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(choice.label, Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun VehicleGuidedSelector(
    state: VehicleFormState,
    onState: (VehicleFormState) -> Unit
) {
    if (state.vehicleType != VehicleType.CAR) {
        AppField(state.brand, { onState(state.copy(brand = it)) }, "الشركة / الماركة *")
        AppField(state.model, { onState(state.copy(model = it)) }, "الموديل *")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppField(state.year, { onState(state.copy(year = digitsOnly(it).take(4))) }, "سنة الصنع *", Modifier.weight(1f), KeyboardType.Number)
            AppField(state.trim, { onState(state.copy(trim = it)) }, "الفئة", Modifier.weight(1f))
        }
        return
    }

    val context = LocalContext.current
    val catalogState by VehicleCatalogRuntimeStore.state.collectAsState()
    val catalogScope = rememberCoroutineScope()

    val make = remember(state.brand, catalogState.activeVersion, catalogState.status) { VehicleSelectionCatalog.findMake(state.brand) }
    val model = remember(make?.id, state.model) { VehicleSelectionCatalog.findModel(make, state.model) }
    val year = state.year.toIntOrNull()
    var manualMode by remember { mutableStateOf(state.brand.isNotBlank() && make == null) }
    var manualTrim by remember(state.brand, state.model) { mutableStateOf(false) }

    val matchingGenerations = remember(model?.id, year) { model?.generationsFor(year).orEmpty() }
    LaunchedEffect(model?.id, year) {
        val generation = matchingGenerations.singleOrNull()
        val code = generation?.code
        if (!code.isNullOrBlank() && !code.equals(state.generationCode, ignoreCase = true)) {
            onState(state.copy(generationCode = code))
        }
    }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f)
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("اختيار السيارة الذكي", fontWeight = FontWeight.Black)
                    Text(
                        "الماركة ← الموديل ← السنة/الجيل ← الفئة. الاختيار يغذي البحث وتوافق قطع الغيار تلقائيًا.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Rounded.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(7.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            catalogScope.launch {
                                (context.applicationContext as CarManagerApplication).container.vehicleCatalogUpdateManager.refreshIfNeeded(force = true)
                            }
                        },
                        enabled = catalogState.status != VehicleCatalogRuntimeState.Status.CHECKING,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (catalogState.status == VehicleCatalogRuntimeState.Status.CHECKING) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, "تحديث قاعدة السيارات", Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            if (catalogState.activeVersion > 0) "قاعدة السيارات • إصدار ${catalogState.activeVersion}" else "قاعدة السيارات المدمجة",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            buildString {
                                append(catalogState.message ?: "تعمل دون إنترنت وتتحقق تلقائيًا من تحديثات الماركات والموديلات والبيانات الفنية.")
                                if (catalogState.sourceCount > 0) append(" • ${catalogState.sourceCount} مصدر")
                                catalogState.lastCheckedAtEpochMs?.takeIf { it > 0L }?.let { append(" • آخر تحقق ${formatDate(it)}") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (catalogState.status == VehicleCatalogRuntimeState.Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (!manualMode) {
                SearchChoiceField(
                    label = "الماركة *",
                    value = make?.displayName ?: state.brand,
                    choices = VehicleSelectionCatalog.makes.map {
                        VehiclePickerChoice(it.id, it.displayName, "${it.arName} ${it.enName}")
                    }
                ) { choice ->
                    val selected = VehicleSelectionCatalog.makes.first { it.id == choice.key }
                    onState(state.copy(
                        brand = selected.canonicalName,
                        model = "",
                        trim = "",
                        year = "",
                        generationCode = "",
                        engineName = "",
                        engineCode = "",
                        engine = "",
                        transmissionName = "",
                        transmissionCode = ""
                    ))
                }
                Spacer(Modifier.height(6.dp))
                SearchChoiceField(
                    label = "الموديل *",
                    value = model?.displayName ?: state.model,
                    enabled = make != null,
                    choices = make?.models.orEmpty().map {
                        VehiclePickerChoice(it.id, it.displayName, "${it.arName} ${it.enName}")
                    }
                ) { choice ->
                    val selected = make?.models?.firstOrNull { it.id == choice.key } ?: return@SearchChoiceField
                    onState(state.copy(
                        model = selected.canonicalName,
                        trim = "",
                        year = "",
                        generationCode = "",
                        engineName = "",
                        engineCode = "",
                        engine = "",
                        transmissionName = "",
                        transmissionCode = ""
                    ))
                }
                Spacer(Modifier.height(6.dp))
                val years = model?.yearOptions().orEmpty()
                SearchChoiceField(
                    label = "سنة الصنع *",
                    value = state.year,
                    enabled = model != null,
                    choices = years.map { VehiclePickerChoice(it.toString(), it.toString()) }
                ) { choice ->
                    onState(state.copy(year = choice.key, generationCode = "", trim = ""))
                }

                if (matchingGenerations.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    if (matchingGenerations.size == 1) {
                        val generation = matchingGenerations.first()
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                                Text("الجيل المتعرف عليه", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(generation.displayName, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                                if (generation.evidence.isNotEmpty()) {
                                    Text(
                                        "ثقة ${generation.confidence}% • ${generation.evidence.take(2).joinToString(" + ") { it.title }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.End
                                    )
                                }
                                if (generation.marketNames.isNotEmpty()) {
                                    Text("أسماء أسواق مرتبطة: ${generation.marketNames.joinToString(" • ")}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                                }
                            }
                        }
                    } else {
                        SearchChoiceField(
                            label = "الجيل / كود الشاسيه",
                            value = matchingGenerations.firstOrNull { it.code.equals(state.generationCode, true) }?.displayName.orEmpty(),
                            choices = matchingGenerations.mapIndexed { index, gen ->
                                VehiclePickerChoice(gen.code ?: "generation_$index", gen.displayName, "${gen.label} ${gen.code.orEmpty()} ${gen.marketNames.joinToString(" ")}")
                            }
                        ) { choice ->
                            val selected = matchingGenerations.firstOrNull { (it.code ?: "") == choice.key }
                                ?: matchingGenerations.getOrNull(choice.key.removePrefix("generation_").toIntOrNull() ?: -1)
                            selected?.code?.let { onState(state.copy(generationCode = it, trim = "")) }
                        }
                    }
                }

                val generation = VehicleSelectionCatalog.generationFor(model, year, state.generationCode)
                val trimHints = (model?.trimHints.orEmpty() + generation?.trimHints.orEmpty()).distinct()
                Spacer(Modifier.height(6.dp))
                if (trimHints.isNotEmpty() && !manualTrim) {
                    SearchChoiceField(
                        label = "الفئة / التجهيز - اختياري",
                        value = state.trim,
                        choices = trimHints.map { VehiclePickerChoice(it, it) }
                    ) { choice -> onState(state.copy(trim = choice.label)) }
                    TextButton(onClick = { manualTrim = true }) { Text("الفئة غير موجودة؟ اكتبها يدويًا") }
                } else {
                    AppField(state.trim, { onState(state.copy(trim = it)) }, "الفئة / التجهيز - اختياري")
                    if (trimHints.isNotEmpty()) TextButton(onClick = { manualTrim = false }) { Text("العودة لقائمة الفئات") }
                }

                TextButton(onClick = { manualMode = true }, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("سيارتي غير موجودة — إدخال يدوي")
                }
            } else {
                AppField(state.brand, { onState(state.copy(brand = it)) }, "الماركة *")
                AppField(state.model, { onState(state.copy(model = it)) }, "الموديل *")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppField(state.year, { onState(state.copy(year = digitsOnly(it).take(4))) }, "سنة الصنع *", Modifier.weight(1f), KeyboardType.Number)
                    AppField(state.trim, { onState(state.copy(trim = it)) }, "الفئة", Modifier.weight(1f))
                }
                TextButton(onClick = { manualMode = false }, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("العودة للاختيار من القوائم")
                }
            }

            val previewYear = state.year.toIntOrNull()
            if (state.brand.isNotBlank() && state.model.isNotBlank() && previewYear != null) {
                val previewVehicle = remember(state.brand, state.model, state.trim, state.year, state.generationCode, state.engine, state.engineName, state.engineCode, state.transmission, state.transmissionName, state.transmissionCode) {
                    VehicleEntity(
                        brand = state.brand,
                        model = state.model,
                        trim = state.trim.cleanOrNull(),
                        year = previewYear,
                        generationCode = state.generationCode.cleanOrNull(),
                        engineCapacityCc = state.engine.toIntOrNull(),
                        engineName = state.engineName.cleanOrNull(),
                        engineCode = state.engineCode.cleanOrNull(),
                        transmissionType = state.transmission,
                        transmissionName = state.transmissionName.cleanOrNull(),
                        transmissionCode = state.transmissionCode.cleanOrNull()
                    )
                }
                val identity = remember(previewVehicle) { VehicleMarketIdentityResolver.resolve(previewVehicle) }
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                Text("هوية البحث التي ستُحفظ تلقائيًا", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    identity.aliasProfiles.take(4).joinToString(" • ") { it.name }.ifBlank { identity.canonicalName },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )
                Text("يتم تحديث المسميات العالمية/السوقية ودرجة العلاقة فور حفظ أو تعديل السيارة.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun VehicleForm(
    title: String,
    subtitle: String,
    state: VehicleFormState,
    onState: (VehicleFormState) -> Unit,
    onCancel: () -> Unit,
    saveLabel: String,
    canSave: Boolean,
    minimumOdometerKm: Double,
    showIdentityFields: Boolean = false,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val profileValidationMessage = remember(state, minimumOdometerKm) {
        vehicleProfileValidationMessage(state, minimumOdometerKm)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Android Photo Picker grants read access even when persistable permission is not exposed.
        }
        onState(state.copy(photo = uri.toString()))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.End
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))

        FormSection("نوع المركبة", vehicleTypeIcon(state.vehicleType)) {
            EnumSelector("النوع", VehicleType.entries, state.vehicleType, {
                onState(state.copy(vehicleType = it, customVehicleType = if (it == VehicleType.OTHER) state.customVehicleType else ""))
            }) { it.arLabel() }
            if (state.vehicleType == VehicleType.OTHER) {
                AppField(state.customVehicleType, { onState(state.copy(customVehicleType = it)) }, "اسم أو صفة المركبة *")
            }
        }

        FormSection("الصورة", CMIcons.Camera) {
            if (state.photo != null) {
                AsyncImage(
                    model = state.photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(5.dp))
            }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(CMIcons.Camera, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (state.photo == null) "اختيار من الصور" else "تغيير الصورة")
            }
            if (state.photo != null) {
                TextButton(onClick = { onState(state.copy(photo = null)) }, modifier = Modifier.align(Alignment.Start)) { Text("إزالة الصورة") }
            }
        }

        FormSection("البيانات الأساسية", vehicleTypeIcon(state.vehicleType)) {
            AppField(state.displayName, { onState(state.copy(displayName = it)) }, "اسم مختصر - اختياري")
            VehicleGuidedSelector(state = state, onState = onState)
            AppField(state.color, { onState(state.copy(color = it)) }, "اللون - اختياري")
        }

        FormSection("العداد والهوية", CMIcons.Odometer) {
            AppField(state.odometer, { onState(state.copy(odometer = numericInput(it))) }, "العداد الحالي (كم)", keyboardType = KeyboardType.Decimal)
            if (state.odometer.toDoubleOrNull()?.let { it < minimumOdometerKm } == true) {
                Text(
                    "لا يمكن خفض العداد عن ${formatKm(minimumOdometerKm)} كم.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            AppField(state.plate, { onState(state.copy(plate = it)) }, "رقم اللوحة - اختياري")
            if (showIdentityFields) {
                AppField(state.vin, { onState(state.copy(vin = it)) }, "رقم الشاسيه VIN - اختياري")
                AppField(state.engineNumber, { onState(state.copy(engineNumber = it)) }, "رقم المحرك - اختياري")
            }
        }

        val editTechnicalSuggestions = remember(state.brand, state.model, state.year, state.trim, state.vin) {
            VehicleTechnicalSuggestionEngine.suggest(
                brand = state.brand,
                model = state.model,
                year = state.year.toIntOrNull(),
                trim = state.trim,
                vin = state.vin
            )
        }
        TechnicalSuggestionsSection(
            suggestions = editTechnicalSuggestions,
            compact = true,
            onApply = { suggestion ->
                onState(state.withTechnicalSuggestion(suggestion))
            }
        )

        FormSection("المواصفات", CMIcons.Settings) {
            EnumSelector("نوع الوقود / الطاقة", FuelType.entries, state.fuelType, { onState(state.copy(fuelType = it)) }) { it.arLabel() }
            EnumSelector("ناقل الحركة", TransmissionType.entries, state.transmission, { onState(state.copy(transmission = it)) }) { it.arLabel() }
            AppField(state.generationCode, { onState(state.copy(generationCode = it)) }, "كود الجيل / الشاسيه - مثال BD, N17")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppField(state.engine, { onState(state.copy(engine = digitsOnly(it))) }, "المحرك CC", Modifier.weight(1f), KeyboardType.Number)
                AppField(state.engineName, { onState(state.copy(engineName = it)) }, "اسم/عائلة المحرك", Modifier.weight(1f))
            }
            AppField(state.engineCode, { onState(state.copy(engineCode = it)) }, "كود المحرك - إن وجد")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppField(state.transmissionName, { onState(state.copy(transmissionName = it)) }, "اسم الفتيس (6AT/IVT...)", Modifier.weight(1f))
                AppField(state.transmissionCode, { onState(state.copy(transmissionCode = it)) }, "كود الفتيس", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppField(state.tank, { onState(state.copy(tank = numericInput(it))) }, "الخزان لتر", Modifier.weight(1f), KeyboardType.Decimal)
                AppField(state.passengerCapacity, { onState(state.copy(passengerCapacity = digitsOnly(it))) }, "إجمالي المقاعد", Modifier.weight(1f), KeyboardType.Number)
            }
            Text("إجمالي المقاعد يشمل السائق ويُستخدم للتحقق من عدد الركاب في مساعد التسعير.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            profileValidationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            AppField(state.tireSize, { onState(state.copy(tireSize = it)) }, "مقاس الإطارات")
            AppField(state.purchasePrice, { onState(state.copy(purchasePrice = numericInput(it))) }, "سعر الشراء - اختياري", keyboardType = KeyboardType.Decimal)
        }

        FormSection("التكلفة الحقيقية السنوية", CMIcons.Expense) {
            Text("تُستخدم كإعدادات افتراضية لحاسبة المشوار ويمكن تشغيلها أو إيقافها لاحقًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppField(state.annualLicenseCost, { onState(state.copy(annualLicenseCost = numericInput(it))) }, "الترخيص/سنة", Modifier.weight(1f), KeyboardType.Decimal)
                AppField(state.annualInsuranceCost, { onState(state.copy(annualInsuranceCost = numericInput(it))) }, "التأمين/سنة", Modifier.weight(1f), KeyboardType.Decimal)
            }
            AppField(state.annualOtherFixedCost, { onState(state.copy(annualOtherFixedCost = numericInput(it))) }, "مصاريف سنوية أخرى", keyboardType = KeyboardType.Decimal)
            AppField(state.annualDistanceKm, { onState(state.copy(annualDistanceKm = numericInput(it))) }, "المسافة السنوية المتوقعة (كم)", keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppField(state.currentMarketValue, { onState(state.copy(currentMarketValue = numericInput(it))) }, "قيمة السيارة الحالية", Modifier.weight(1f), KeyboardType.Decimal)
                AppField(state.depreciationAnnualPercent, { onState(state.copy(depreciationAnnualPercent = numericInput(it))) }, "إهلاك سنوي %", Modifier.weight(1f), KeyboardType.Decimal)
            }
            SettingSwitchRow("احتساب المصروفات السنوية في المشوار", state.includeAnnualFixedCosts) { onState(state.copy(includeAnnualFixedCosts = it)) }
            SettingSwitchRow("احتساب الإهلاك في المشوار", state.includeDepreciation) { onState(state.copy(includeDepreciation = it)) }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(44.dp)) { Text("إلغاء") }
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f).height(44.dp)) { Text(saveLabel) }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun TechnicalSuggestionsSection(
    suggestions: List<VehicleTechnicalSuggestion>,
    compact: Boolean = false,
    onApply: (VehicleTechnicalSuggestion) -> Unit
) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f))
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("اقتراح ذكي للمحرك والفتيس والجيل", fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                    Text(
                        "لأن أغلب الملاك لا يعرفون الأكواد الفنية، يعرض CarManager اقتراحًا مع الثقة والمصدر قبل الحفظ ولا يخمن البيانات المجهولة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(7.dp))
            if (suggestions.isEmpty()) {
                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
                    Text(
                        "لا توجد مطابقة موثوقة كفاية لهذه السيارة في القاعدة الحالية. اترك الأكواد فارغة أو أدخل ما تعرفه فقط؛ عدم التخمين أفضل من بيانات خاطئة.",
                        modifier = Modifier.fillMaxWidth().padding(9.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            } else {
                suggestions.take(if (compact) 1 else 3).forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${suggestion.confidence}%") },
                            leadingIcon = { Icon(Icons.Rounded.Verified, null, Modifier.size(16.dp)) }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(suggestion.confidenceLabel, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    val details = buildList {
                        suggestion.generationCode?.let { add("الجيل: $it") }
                        suggestion.generationName?.let { add(it) }
                        suggestion.engineCapacityCc?.let { add("المحرك: ${it}cc") }
                        suggestion.engineName?.let { add(it) }
                        suggestion.engineCode?.let { add("كود المحرك: $it") }
                        suggestion.transmissionType?.let { add("الفتيس: ${it.arLabel()}") }
                        suggestion.transmissionName?.let { add(it) }
                        suggestion.transmissionCode?.let { add("كود الفتيس: $it") }
                    }
                    if (details.isNotEmpty()) Text(details.distinct().joinToString(" • "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    Text("سبب الاقتراح: ${suggestion.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    suggestion.sources.forEach { source ->
                        Text("المصدر: ${source.title} — ${source.detail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { onApply(suggestion) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("استخدام هذا الاقتراح")
                    }
                }
            }
        }
    }
}

@Composable
private fun FormSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(9.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Spacer(Modifier.width(5.dp))
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = {
                if (!focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)) {
                    focusManager.clearFocus()
                }
            },
            onDone = { focusManager.clearFocus() }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    scope.launch {
                        // The first request handles focus; the later requests follow the IME
                        // animation so the field cannot settle underneath the keyboard.
                        delay(120)
                        bringIntoViewRequester.bringIntoView()
                        delay(260)
                        bringIntoViewRequester.bringIntoView()
                        delay(320)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumSelector(label: String, values: List<T>, selected: T, onSelected: (T) -> Unit, text: (T) -> String) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(vertical = 2.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text(item)) },
                    onClick = { onSelected(item); expanded = false }
                )
            }
        }
    }
}

fun numericInput(value: String): String {
    val normalized = value.map { ch ->
        when (ch) {
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            '٫', ',' -> '.'
            else -> ch
        }
    }.joinToString("").filter { it.isDigit() || it == '.' }
    val first = normalized.indexOf('.')
    return if (first < 0) normalized else normalized.substring(0, first + 1) + normalized.substring(first + 1).replace(".", "")
}

private fun digitsOnly(value: String): String = value.map { ch ->
    when (ch) {
        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
        else -> ch
    }
}.joinToString("").filter(Char::isDigit)

private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun statusLabel(status: VehicleStatus) = when (status) {
    VehicleStatus.ACTIVE -> "الأساسية"
    VehicleStatus.SECONDARY -> "إضافية"
    VehicleStatus.SOLD -> "مباعة"
    VehicleStatus.ARCHIVED -> "مؤرشفة"
}

@Composable
private fun GarageCockpitHeader(vehicleCount: Int) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0B1A22), androidx.compose.ui.graphics.Color(0xFF143A47), androidx.compose.ui.graphics.Color(0xFF10232D))))
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .08f)) {
                Icon(CMIcons.Garage, null, Modifier.padding(14.dp).size(30.dp), tint = androidx.compose.ui.graphics.Color(0xFF67D7FF))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("الجراج", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
                Text("$vehicleCount مركبة • ملفات مستقلة وتاريخ محفوظ", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .65f))
            }
        }
    }
}

@Composable
private fun GarageVehicleCardV8(
    vehicle: VehicleEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMakePrimary: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onSold: (Double, Double?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSale by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val tone = when {
        vehicle.status == VehicleStatus.SOLD -> AutoTone.RED
        vehicle.status == VehicleStatus.ARCHIVED -> AutoTone.GRAPHITE
        selected || vehicle.isPrimary -> AutoTone.TEAL
        else -> AutoTone.BLUE
    }
    val c = autoToneColors(tone)

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) c.strong else MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                VehiclePhoto(vehicle, Modifier.size(width = 104.dp, height = 78.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vehicle.isPrimary) PremiumStatusPill("أساسية", kind = PremiumStatusKind.GOOD)
                        Spacer(Modifier.weight(1f))
                        Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, textAlign = TextAlign.End, maxLines = 1)
                    }
                    Text("${vehicle.brand} ${vehicle.model} • ${vehicle.year}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!vehicle.plateNumber.isNullOrBlank()) Text("لوحة ${vehicle.plateNumber}", style = MaterialTheme.typography.labelMedium, color = c.strong, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(15.dp), color = c.soft) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(CMIcons.Odometer, null, Modifier.size(19.dp), tint = c.strong)
                        Spacer(Modifier.height(4.dp))
                        Text("${formatKm(vehicle.currentOdometerKm)} كم", fontWeight = FontWeight.Black, maxLines = 1)
                        Text("العداد", style = MaterialTheme.typography.labelSmall, color = c.onSoft)
                    }
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(15.dp), color = autoToneColors(AutoTone.AMBER).soft) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(19.dp), tint = autoToneColors(AutoTone.AMBER).strong)
                        Spacer(Modifier.height(4.dp))
                        Text(vehicle.year.toString(), fontWeight = FontWeight.Black)
                        Text("السنة", style = MaterialTheme.typography.labelSmall, color = autoToneColors(AutoTone.AMBER).onSoft)
                    }
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(15.dp), color = autoToneColors(AutoTone.VIOLET).soft) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (vehicle.vehicleType == VehicleType.MOTORCYCLE) CMIcons.Motorcycle else CMIcons.Vehicle, null, Modifier.size(19.dp), tint = autoToneColors(AutoTone.VIOLET).strong)
                        Spacer(Modifier.height(4.dp))
                        Text(vehicle.vehicleType.arLabel(vehicle.customVehicleType), fontWeight = FontWeight.Black, maxLines = 1)
                        Text("النوع", style = MaterialTheme.typography.labelSmall, color = autoToneColors(AutoTone.VIOLET).onSoft)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) { Icon(CMIcons.More, "المزيد") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (vehicle.status == VehicleStatus.ARCHIVED || vehicle.status == VehicleStatus.SOLD) {
                            DropdownMenuItem(text = { Text("إعادة إلى الجراج") }, leadingIcon = { Icon(CMIcons.Restore, null) }, onClick = { menuExpanded = false; onRestore() })
                        } else {
                            DropdownMenuItem(text = { Text("أرشفة المركبة") }, leadingIcon = { Icon(CMIcons.Archive, null) }, onClick = { menuExpanded = false; showArchiveConfirm = true })
                            DropdownMenuItem(text = { Text("تسجيل البيع") }, leadingIcon = { Icon(CMIcons.Sell, null) }, onClick = { menuExpanded = false; showSale = true })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!vehicle.isPrimary && vehicle.status != VehicleStatus.ARCHIVED && vehicle.status != VehicleStatus.SOLD) {
                    TextButton(onClick = onMakePrimary) { Text("جعلها الأساسية") }
                }
                FilledTonalButton(onClick = onEdit, shape = RoundedCornerShape(14.dp)) {
                    Icon(CMIcons.Edit, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("تعديل")
                }
            }
        }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            icon = { Icon(CMIcons.Archive, null) },
            title = { Text("أرشفة المركبة؟") },
            text = { Text("لن تُحذف أي بيانات، ويمكن إعادتها إلى الجراج في أي وقت.") },
            confirmButton = { Button(onClick = { showArchiveConfirm = false; onArchive() }) { Text("أرشفة") } },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("إلغاء") } }
        )
    }
    if (showSale) {
        var km by remember(vehicle.vehicleId) { mutableStateOf(vehicle.currentOdometerKm.toString()) }
        var price by remember(vehicle.vehicleId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSale = false },
            title = { Text("تسجيل بيع المركبة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(km, { km = numericInput(it) }, "عداد البيع")
                    AppField(price, { price = numericInput(it) }, "سعر البيع (اختياري)")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsedKm = km.toDoubleOrNull()
                    if (parsedKm != null && parsedKm >= vehicle.currentOdometerKm) {
                        onSold(parsedKm, price.toDoubleOrNull())
                        showSale = false
                    }
                }) { Text("حفظ البيع") }
            },
            dismissButton = { TextButton(onClick = { showSale = false }) { Text("إلغاء") } }
        )
    }
}

private fun garageEditableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

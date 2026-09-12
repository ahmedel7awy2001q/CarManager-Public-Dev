from pathlib import Path
import subprocess


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one block in {path}, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


def replace_region(path: str, start_marker: str, end_marker: str, new_region: str) -> None:
    p = Path(path)
    text = p.read_text()
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    p.write_text(text[:start] + new_region + text[end:])


def commit(message: str, *paths: str) -> None:
    run("git", "add", *paths)
    result = subprocess.run(["git", "diff", "--cached", "--quiet"])
    if result.returncode == 0:
        raise SystemExit(f"No staged changes for {message}")
    run("git", "commit", "-m", message)


def phase_lifecycle_and_garage() -> None:
    Path("app/src/main/java/com/ahmed/carmanager/ui/VehicleLifecyclePolicy.kt").write_text('''package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus

internal object VehicleLifecyclePolicy {
    fun isOperational(vehicle: VehicleEntity): Boolean =
        !vehicle.isDeleted && (vehicle.status == VehicleStatus.ACTIVE || vehicle.status == VehicleStatus.SECONDARY)

    fun isHistorical(vehicle: VehicleEntity): Boolean =
        !vehicle.isDeleted && (vehicle.status == VehicleStatus.SOLD || vehicle.status == VehicleStatus.ARCHIVED)

    fun operational(vehicles: List<VehicleEntity>): List<VehicleEntity> = vehicles.filter(::isOperational)
    fun historical(vehicles: List<VehicleEntity>): List<VehicleEntity> = vehicles.filter(::isHistorical)
}
''')

    Path("app/src/main/java/com/ahmed/carmanager/ui/GarageHistorySheet.kt").write_text('''package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus

@Composable
internal fun GarageHistoryAccessCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("سجل المركبات", fontWeight = FontWeight.Bold)
                Text("$count مباعة أو مؤرشفة • لا تظهر في التشغيل اليومي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GarageHistorySheet(
    vehicles: List<VehicleEntity>,
    onRestore: (String) -> Unit,
    onSoftDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<VehicleEntity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("سجل المركبات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Text("المباعة والمؤرشفة محفوظة هنا ولا تدخل في GPS أو قطع الغيار أو العمليات اليومية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicles, key = { it.vehicleId }) { vehicle ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.End) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (vehicle.status == VehicleStatus.SOLD) "مباعة" else "مؤرشفة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", fontWeight = FontWeight.Bold)
                                    Text("${vehicle.year} • ${formatKm(vehicle.currentOdometerKm)} كم", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (vehicle.status == VehicleStatus.SOLD) {
                                val sale = buildList {
                                    vehicle.soldAt?.let { add("بيع ${formatDate(it)}") }
                                    vehicle.salePrice?.let { add(formatMoney(it)) }
                                }.joinToString(" • ")
                                if (sale.isNotBlank()) Text(sale, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pendingDelete = vehicle }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("إزالة")
                                }
                                Button(onClick = { onRestore(vehicle.vehicleId) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Restore, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("إعادة للجراج")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    pendingDelete?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("إزالة المركبة من الحساب؟") },
            text = { Text("سيتم إخفاء «${vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}"}» من قائمة الحساب. لن نحذف سجلات الصيانة أو الوقود أو الرحلات أو الملكية من قاعدة البيانات؛ الإزالة آمنة وقابلة للاسترداد تقنيًا من النسخ الاحتياطية.") },
            confirmButton = {
                TextButton(onClick = { onSoftDelete(vehicle.vehicleId); pendingDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("إزالة من الحساب") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
        )
    }
}
''')

    replace_once(
        "app/src/main/java/com/ahmed/carmanager/data/repository/VehicleRepository.kt",
        "    suspend fun markSold(request: SellVehicleRequest): VehicleRepositoryResult<Unit>\n    suspend fun saveInspectionTemplateConfig(vehicleId: String, config: String?): VehicleRepositoryResult<Unit>",
        "    suspend fun markSold(request: SellVehicleRequest): VehicleRepositoryResult<Unit>\n    suspend fun softDelete(vehicleId: String): VehicleRepositoryResult<Unit>\n    suspend fun saveInspectionTemplateConfig(vehicleId: String, config: String?): VehicleRepositoryResult<Unit>",
    )

    replace_once(
        "app/src/main/java/com/ahmed/carmanager/data/repository/RoomVehicleRepository.kt",
        "    override suspend fun saveInspectionTemplateConfig(vehicleId: String, config: String?): VehicleRepositoryResult<Unit> = safeOperation {",
        '''    override suspend fun softDelete(vehicleId: String): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا.")
        database.withTransaction {
            val vehicle = vehicleDao.getById(vehicleId, uid)
                ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
            if (vehicle.status != VehicleStatus.SOLD && vehicle.status != VehicleStatus.ARCHIVED) {
                return@withTransaction VehicleRepositoryResult.Error("انقل المركبة إلى الأرشيف أو سجّل البيع أولًا قبل إزالتها من القائمة.")
            }
            vehicleDao.softDelete(vehicleId, uid)
            promoteFallbackIfNeeded(uid)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun saveInspectionTemplateConfig(vehicleId: String, config: String?): VehicleRepositoryResult<Unit> = safeOperation {''',
    )

    vm = "app/src/main/java/com/ahmed/carmanager/ui/CarManagerViewModel.kt"
    replace_once(
        vm,
        "    val vehicles = vehiclesRepo.observeVehicles().stateIn(viewModelScope, coreSharing, emptyList())\n",
        '''    val vehicles = vehiclesRepo.observeVehicles().stateIn(viewModelScope, coreSharing, emptyList())
    val operationalVehicles = vehicles
        .map(VehicleLifecyclePolicy::operational)
        .stateIn(viewModelScope, coreSharing, emptyList())
    val historicalVehicles = vehicles
        .map(VehicleLifecyclePolicy::historical)
        .stateIn(viewModelScope, coreSharing, emptyList())
''',
    )
    replace_once(vm, "                list.filter { !it.isDeleted }.forEach { vehicle ->", "                list.filter(VehicleLifecyclePolicy::isOperational).forEach { vehicle ->")
    replace_once(
        vm,
        '''    val selectedVehicle: StateFlow<VehicleEntity?> = combine(vehicles, _selectedVehicleId) { list, id ->
        list.firstOrNull { it.vehicleId == id }
            ?: list.firstOrNull { it.isPrimary }
            ?: list.firstOrNull { it.status == VehicleStatus.ACTIVE || it.status == VehicleStatus.SECONDARY }
    }.stateIn(viewModelScope, coreSharing, null)''',
        '''    val selectedVehicle: StateFlow<VehicleEntity?> = combine(operationalVehicles, _selectedVehicleId) { list, id ->
        list.firstOrNull { it.vehicleId == id }
            ?: list.firstOrNull { it.isPrimary }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, coreSharing, null)''',
    )
    replace_once(vm, "    val vehicleContextDecision = combine(\n        vehicles,", "    val vehicleContextDecision = combine(\n        operationalVehicles,")
    replace_once(
        vm,
        '''        viewModelScope.launch {
            vehicles.collect { list ->
                val current = _selectedVehicleId.value
                if (current == null || list.none { it.vehicleId == current }) {
                    _selectedVehicleId.value = list.firstOrNull { it.isPrimary }?.vehicleId ?: list.firstOrNull()?.vehicleId
                }
            }
        }''',
        '''        viewModelScope.launch {
            operationalVehicles.collect { list ->
                val current = _selectedVehicleId.value
                if (current == null || list.none { it.vehicleId == current }) {
                    _selectedVehicleId.value = list.firstOrNull { it.isPrimary }?.vehicleId ?: list.firstOrNull()?.vehicleId
                }
            }
        }''',
    )
    replace_once(
        vm,
        "    fun markSold(vehicleId: String, odometerKm: Double, salePrice: Double?) = launchOperation { vehiclesRepo.markSold(SellVehicleRequest(vehicleId, odometerKm, salePrice)) }\n    fun saveInspectionTemplateConfig",
        "    fun markSold(vehicleId: String, odometerKm: Double, salePrice: Double?) = launchOperation { vehiclesRepo.markSold(SellVehicleRequest(vehicleId, odometerKm, salePrice)) }\n    fun softDeleteVehicle(vehicleId: String) = launchOperation { vehiclesRepo.softDelete(vehicleId) }\n    fun saveInspectionTemplateConfig",
    )

    host = "app/src/main/java/com/ahmed/carmanager/ui/CarManagerScreenHost.kt"
    replace_once(host, "            MainSection.HOME -> HomeDestination(selectedVehicle, vehicles.size, viewModel, actions)", "            MainSection.HOME -> HomeDestination(selectedVehicle, vehicles.count(VehicleLifecyclePolicy::isOperational), viewModel, actions)")
    replace_once(host, "            vehicles = vehicles,\n            viewModel = viewModel\n        )\n        MoreDestination.TIRES_BATTERY", "            vehicles = vehicles.filter(VehicleLifecyclePolicy::isOperational),\n            viewModel = viewModel\n        )\n        MoreDestination.TIRES_BATTERY")
    replace_once(host, "            onRestore = viewModel::restore,\n            onSold = viewModel::markSold\n        )", "            onRestore = viewModel::restore,\n            onSold = viewModel::markSold,\n            onSoftDelete = viewModel::softDeleteVehicle\n        )")

    garage = "app/src/main/java/com/ahmed/carmanager/ui/GarageScreen.kt"
    new_top = '''@OptIn(ExperimentalMaterial3Api::class)
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
    onSold: (String, Double, Double?) -> Unit,
    onSoftDelete: (String) -> Unit
) {
    var showTypeChooser by remember { mutableStateOf(false) }
    var addVehicleType by remember { mutableStateOf<VehicleType?>(null) }
    var addVehicleDirty by remember { mutableStateOf(false) }
    var confirmDiscardAdd by remember { mutableStateOf(false) }
    var editVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var editVehicleDirty by remember { mutableStateOf(false) }
    var confirmDiscardEdit by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val operationalVehicles = remember(vehicles) { VehicleLifecyclePolicy.operational(vehicles) }
    val historicalVehicles = remember(vehicles) { VehicleLifecyclePolicy.historical(vehicles) }
    val filteredVehicles = remember(operationalVehicles, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) operationalVehicles else operationalVehicles.filter { vehicle ->
            listOf(vehicle.displayName, vehicle.brand, vehicle.model, vehicle.plateNumber, vehicle.vin, vehicle.year.toString())
                .filterNotNull().joinToString(" ").lowercase().contains(q)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (operationalVehicles.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                if (historicalVehicles.isNotEmpty()) {
                    GarageHistoryAccessCard(
                        count = historicalVehicles.size,
                        onClick = { showHistory = true },
                        modifier = Modifier.padding(horizontal = CMPremium.ScreenPadding, vertical = 10.dp)
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { EmptyGarageState(onAdd = { showTypeChooser = true }) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CMPremium.ScreenPadding, 10.dp, CMPremium.ScreenPadding, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { GarageCockpitHeader(operationalVehicles.size) }
                if (historicalVehicles.isNotEmpty()) {
                    item { GarageHistoryAccessCard(count = historicalVehicles.size, onClick = { showHistory = true }) }
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
        val currentAddDirty by rememberUpdatedState(addVehicleDirty)
        val addSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> target != SheetValue.Hidden || !currentAddDirty }
        )
        BackHandler(enabled = !confirmDiscardAdd) {
            if (addVehicleDirty) confirmDiscardAdd = true else addVehicleType = null
        }
        ModalBottomSheet(
            onDismissRequest = { if (addVehicleDirty) confirmDiscardAdd = true else addVehicleType = null },
            sheetState = addSheetState
        ) {
            AddVehicleForm(
                initialType = type,
                onCancel = { if (addVehicleDirty) confirmDiscardAdd = true else addVehicleType = null },
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
        val currentEditDirty by rememberUpdatedState(editVehicleDirty)
        val editSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> target != SheetValue.Hidden || !currentEditDirty }
        )
        BackHandler(enabled = !confirmDiscardEdit) {
            if (editVehicleDirty) confirmDiscardEdit = true else editVehicle = null
        }
        ModalBottomSheet(
            onDismissRequest = { if (editVehicleDirty) confirmDiscardEdit = true else editVehicle = null },
            sheetState = editSheetState
        ) {
            EditVehicleForm(
                vehicle = vehicle,
                onCancel = { if (editVehicleDirty) confirmDiscardEdit = true else editVehicle = null },
                onDirtyChange = { editVehicleDirty = it },
                onSave = {
                    editVehicleDirty = false
                    onUpdate(it)
                    editVehicle = null
                }
            )
        }
    }

    if (showHistory) {
        GarageHistorySheet(
            vehicles = historicalVehicles,
            onRestore = onRestore,
            onSoftDelete = onSoftDelete,
            onDismiss = { showHistory = false }
        )
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

'''
    replace_region(garage, "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun GarageScreen(", "@Composable\nprivate fun EmptyGarageState", new_top)

    Path("app/src/test/java/com/ahmed/carmanager/ui/VehicleLifecyclePolicyTest.kt").write_text('''package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLifecyclePolicyTest {
    @Test
    fun operationalListExcludesSoldArchivedAndDeleted() {
        val active = VehicleEntity(brand = "Kia", model = "Cerato", year = 2021, status = VehicleStatus.ACTIVE)
        val secondary = VehicleEntity(brand = "Renault", model = "Logan", year = 2021, status = VehicleStatus.SECONDARY)
        val sold = VehicleEntity(brand = "Nissan", model = "Sunny", year = 2020, status = VehicleStatus.SOLD)
        val archived = VehicleEntity(brand = "Toyota", model = "Corolla", year = 2019, status = VehicleStatus.ARCHIVED)
        val deleted = active.copy(vehicleId = "deleted", isDeleted = true)
        val all = listOf(active, secondary, sold, archived, deleted)

        assertEquals(listOf(active, secondary), VehicleLifecyclePolicy.operational(all))
        assertEquals(listOf(sold, archived), VehicleLifecyclePolicy.historical(all))
        assertFalse(VehicleLifecyclePolicy.isOperational(sold))
        assertTrue(VehicleLifecyclePolicy.isHistorical(archived))
    }
}
''')

    commit(
        "[PRODUCT] Add safe vehicle lifecycle and preserve garage drafts",
        "app/src/main/java/com/ahmed/carmanager/data/repository/VehicleRepository.kt",
        "app/src/main/java/com/ahmed/carmanager/data/repository/RoomVehicleRepository.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/CarManagerViewModel.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/CarManagerScreenHost.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/GarageScreen.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/GarageHistorySheet.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/VehicleLifecyclePolicy.kt",
        "app/src/test/java/com/ahmed/carmanager/ui/VehicleLifecyclePolicyTest.kt",
    )


def phase_technical_coverage() -> None:
    Path("app/src/main/java/com/ahmed/carmanager/ui/VehicleTechnicalCoverage.kt").write_text('''package com.ahmed.carmanager.ui

internal enum class VehicleTechnicalCoverageState {
    TECHNICALLY_VERIFIED,
    IDENTITY_KNOWN_TECHNICAL_INCOMPLETE,
    UNKNOWN_IDENTITY
}

internal data class VehicleTechnicalCoverage(
    val state: VehicleTechnicalCoverageState,
    val title: String,
    val detail: String
)

internal object VehicleTechnicalCoverageEvaluator {
    fun evaluate(
        brand: String,
        model: String,
        year: Int?,
        suggestions: List<VehicleTechnicalSuggestion>
    ): VehicleTechnicalCoverage {
        val make = VehicleSelectionCatalog.findMake(brand)
        val knownModel = VehicleSelectionCatalog.findModel(make, model)
        val identityKnown = brand.isNotBlank() && model.isNotBlank() && year != null &&
            (knownModel != null || suggestions.isNotEmpty())

        val verifiedTechnical = suggestions.any { suggestion ->
            if (suggestion.confidence < 85 || suggestion.sources.isEmpty()) return@any false
            val detailCount = listOf(
                suggestion.engineCapacityCc,
                suggestion.engineName,
                suggestion.engineCode,
                suggestion.fuelType,
                suggestion.transmissionType,
                suggestion.transmissionName,
                suggestion.transmissionCode,
                suggestion.tankCapacityLiters
            ).count { it != null }
            detailCount >= 2
        }

        return when {
            verifiedTechnical -> VehicleTechnicalCoverage(
                VehicleTechnicalCoverageState.TECHNICALLY_VERIFIED,
                "بيانات فنية موثقة متاحة",
                "يمكن تطبيق المواصفات المقترحة ومراجعتها قبل الحفظ. لن يملأ CarManager أي كود غير موثق."
            )
            identityKnown -> VehicleTechnicalCoverage(
                VehicleTechnicalCoverageState.IDENTITY_KNOWN_TECHNICAL_INCOMPLETE,
                "هوية السيارة معروفة — التفاصيل الفنية غير مكتملة",
                "الماركة والموديل والسنة معروفون، لكن لا توجد أدلة كافية لملء المحرك أو الفتيس تلقائيًا. اترك الحقول غير المعروفة فارغة بدل التخمين."
            )
            else -> VehicleTechnicalCoverage(
                VehicleTechnicalCoverageState.UNKNOWN_IDENTITY,
                "السيارة غير مكتملة في الكتالوج الفني حاليًا",
                "يمكن حفظها يدويًا واستخدام بقية التطبيق. البيانات الفنية ستظل اختيارية ولن يتم اختراع مواصفات لها."
            )
        }
    }
}
''')

    Path("app/src/test/java/com/ahmed/carmanager/ui/VehicleTechnicalCoverageTest.kt").write_text('''package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.TransmissionType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTechnicalCoverageTest {
    @Test
    fun verifiedTechnicalSuggestionGetsVerifiedState() {
        val suggestion = VehicleTechnicalSuggestion(
            engineCapacityCc = 1600,
            engineName = "1.6 MPI",
            transmissionType = TransmissionType.AUTOMATIC,
            confidence = 92,
            reason = "test",
            sources = listOf(TechnicalSuggestionSource("source", "evidence"))
        )
        val result = VehicleTechnicalCoverageEvaluator.evaluate("Kia", "Cerato", 2021, listOf(suggestion))
        assertEquals(VehicleTechnicalCoverageState.TECHNICALLY_VERIFIED, result.state)
    }

    @Test
    fun generationOnlySuggestionDoesNotPretendPowertrainIsVerified() {
        val suggestion = VehicleTechnicalSuggestion(
            generationCode = "N17",
            generationName = "Sunny N17",
            confidence = 94,
            reason = "test",
            sources = listOf(TechnicalSuggestionSource("source", "evidence"))
        )
        val result = VehicleTechnicalCoverageEvaluator.evaluate("Nissan", "Sunny", 2021, listOf(suggestion))
        assertEquals(VehicleTechnicalCoverageState.IDENTITY_KNOWN_TECHNICAL_INCOMPLETE, result.state)
    }
}
''')

    garage = "app/src/main/java/com/ahmed/carmanager/ui/GarageScreen.kt"
    replace_once(
        garage,
        "    val trustedMarketSuggestion = technicalSuggestions.firstOrNull { suggestion ->",
        '''    val technicalCoverage = remember(state.brand, state.model, state.year, technicalSuggestions) {
        VehicleTechnicalCoverageEvaluator.evaluate(state.brand, state.model, state.year.toIntOrNull(), technicalSuggestions)
    }
    val trustedMarketSuggestion = technicalSuggestions.firstOrNull { suggestion ->''',
    )
    replace_once(garage, "            1 -> {\n                TechnicalSuggestionsSection(", "            1 -> {\n                TechnicalCoverageBanner(technicalCoverage)\n                TechnicalSuggestionsSection(")
    replace_once(
        garage,
        "                            color = if (catalogState.status == VehicleCatalogRuntimeState.Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,",
        '''                            color = if (
                                catalogState.status == VehicleCatalogRuntimeState.Status.FAILED ||
                                catalogState.message?.startsWith("تعذر تحديث") == true
                            ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,''',
    )
    replace_once(
        garage,
        '''        TechnicalSuggestionsSection(
            suggestions = editTechnicalSuggestions,
            compact = true,''',
        '''        val editTechnicalCoverage = remember(state.brand, state.model, state.year, editTechnicalSuggestions) {
            VehicleTechnicalCoverageEvaluator.evaluate(state.brand, state.model, state.year.toIntOrNull(), editTechnicalSuggestions)
        }
        TechnicalCoverageBanner(editTechnicalCoverage, compact = true)
        TechnicalSuggestionsSection(
            suggestions = editTechnicalSuggestions,
            compact = true,''',
    )
    replace_once(
        garage,
        "@Composable\nprivate fun TechnicalSuggestionsSection(",
        '''@Composable
private fun TechnicalCoverageBanner(coverage: VehicleTechnicalCoverage, compact: Boolean = false) {
    val colors = when (coverage.state) {
        VehicleTechnicalCoverageState.TECHNICALLY_VERIFIED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        VehicleTechnicalCoverageState.IDENTITY_KNOWN_TECHNICAL_INCOMPLETE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        VehicleTechnicalCoverageState.UNKNOWN_IDENTITY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        Modifier.fillMaxWidth().padding(bottom = if (compact) 5.dp else 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.first
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = if (compact) 7.dp else 9.dp), horizontalAlignment = Alignment.End) {
            Text(coverage.title, fontWeight = FontWeight.Bold, color = colors.second, textAlign = TextAlign.End)
            Text(coverage.detail, style = MaterialTheme.typography.labelSmall, color = colors.second, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TechnicalSuggestionsSection(''',
    )

    replace_once(
        "app/src/main/java/com/ahmed/carmanager/data/catalog/VehicleCatalogUpdateManager.kt",
        '        return "$reason. قاعدة السيارات المدمجة تعمل بالكامل ولم تتأثر بياناتك؛ سيحاول CarManager التحديث لاحقًا."',
        '        return "تعذر تحديث قاعدة السيارات الآن — السبب: $reason. قاعدة السيارات المدمجة/المحفوظة ما زالت فعالة ولم تُفقد أي بيانات. استخدم التطبيق بشكل طبيعي؛ سيحاول CarManager لاحقًا ويمكنك الضغط على تحديث للمحاولة يدويًا."',
    )

    commit(
        "[PRODUCT] Clarify technical catalog coverage and update failures",
        "app/src/main/java/com/ahmed/carmanager/data/catalog/VehicleCatalogUpdateManager.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/GarageScreen.kt",
        "app/src/main/java/com/ahmed/carmanager/ui/VehicleTechnicalCoverage.kt",
        "app/src/test/java/com/ahmed/carmanager/ui/VehicleTechnicalCoverageTest.kt",
    )


def phase_storefront_identity() -> None:
    path = "app/src/main/java/com/ahmed/carmanager/ui/VehicleMarketIdentity.kt"
    old = '''        val curatedProfiles = (VehicleAliasKnowledgeBase.profiles(vehicle) + VehicleSelectionCatalog.catalogAliasProfiles(
            brand = vehicle.brand,
            model = vehicle.model,
            year = vehicle.year,
            generationCode = vehicle.generationCode
        )).distinctBy { normalizeVehicleText(it.name) }.sortedByDescending { it.confidence }'''
    new = '''        val curatedProfiles = (
            VehicleAliasKnowledgeBase.profiles(vehicle) +
                VehicleSelectionCatalog.catalogAliasProfiles(
                    brand = vehicle.brand,
                    model = vehicle.model,
                    year = vehicle.year,
                    generationCode = vehicle.generationCode
                ) +
                StorefrontVehicleAliasCatalog.marketProfiles(vehicle)
            ).distinctBy { normalizeVehicleText(it.name) }.sortedByDescending { it.confidence }'''
    replace_once(path, old, new)
    commit("[PRODUCT] Use storefront aliases across unified parts sources", path)


if __name__ == "__main__":
    phase_lifecycle_and_garage()
    phase_technical_coverage()
    phase_storefront_identity()

package com.ahmed.carmanager.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.auth.AccountUser
import com.ahmed.carmanager.data.auth.AuthResult
import com.ahmed.carmanager.data.backup.BackupResult
import com.ahmed.carmanager.data.cloud.CloudDataResult
import com.ahmed.carmanager.data.gps.GpsSyncResult
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenancePreset
import com.ahmed.carmanager.data.maintenance.MaintenanceCatalog
import com.ahmed.carmanager.data.maintenance.MaintenanceGuidanceCatalog
import com.ahmed.carmanager.data.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

sealed interface AppUiEvent { data class Message(val textAr: String) : AppUiEvent }

@OptIn(ExperimentalCoroutinesApi::class)
class CarManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CarManagerApplication
    private val container = app.container
    private val authRepo = container.authRepository
    private val vehiclesRepo = container.vehicleRepository
    private val repo = container.operationsRepository

    // Vehicle/account identity is useful across every destination. Heavy operational lists are
    // screen-scoped and should stop shortly after navigation so the previous screen does not keep
    // Room observation/recomposition work alive for five seconds.
    private val coreSharing = SharingStarted.WhileSubscribed(5_000)
    private val screenSharing = SharingStarted.WhileSubscribed(1_000)

    val accountUser: StateFlow<AccountUser?> = authRepo.user
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepo.currentUser())

    private val _authBusy = MutableStateFlow(false)
    val authBusy = _authBusy.asStateFlow()

    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy = _cloudBusy.asStateFlow()

    private val _hasUnclaimedVehicles = MutableStateFlow(false)
    val hasUnclaimedVehicles = _hasUnclaimedVehicles.asStateFlow()

    val vehicles = vehiclesRepo.observeVehicles().stateIn(viewModelScope, coreSharing, emptyList())
    val operationalVehicles = vehicles
        .map(VehicleLifecyclePolicy::operational)
        .stateIn(viewModelScope, coreSharing, emptyList())
    val historicalVehicles = vehicles
        .map(VehicleLifecyclePolicy::historical)
        .stateIn(viewModelScope, coreSharing, emptyList())

    init {
        // Keep the spare-parts market identity in sync as soon as a vehicle is saved/edited.
        // VehicleMarketProfileStore has its own technical fingerprint fast-path, so ordinary
        // odometer/photo/status changes do not rewrite aliases or re-resolve the market identity.
        viewModelScope.launch {
            vehicles.collect { list ->
                list.filter(VehicleLifecyclePolicy::isOperational).forEach { vehicle ->
                    VehicleMarketProfileStore.sync(app, vehicle)
                }
            }
        }
    }

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId = _selectedVehicleId.asStateFlow()

    val selectedVehicle: StateFlow<VehicleEntity?> = combine(operationalVehicles, _selectedVehicleId) { list, id ->
        list.firstOrNull { it.vehicleId == id }
            ?: list.firstOrNull { it.isPrimary }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, coreSharing, null)

    private fun <T> selectedFlow(block: (String) -> Flow<List<T>>): Flow<List<T>> =
        selectedVehicle.filterNotNull().map { it.vehicleId }.distinctUntilChanged().flatMapLatest(block)

    val maintenancePlans = selectedFlow(repo::observeMaintenancePlans).stateIn(viewModelScope, screenSharing, emptyList())
    val allMaintenancePlans = selectedFlow(repo::observeAllMaintenancePlans).stateIn(viewModelScope, screenSharing, emptyList())
    val maintenanceHistory = selectedFlow(repo::observeMaintenanceHistory).stateIn(viewModelScope, screenSharing, emptyList())
    val odometerRecords = selectedFlow(repo::observeOdometer).stateIn(viewModelScope, screenSharing, emptyList())
    val fuelRecords = selectedFlow(repo::observeFuel).stateIn(viewModelScope, screenSharing, emptyList())
    val trips = selectedFlow(repo::observeTrips).stateIn(viewModelScope, screenSharing, emptyList())
    val tripQuotes = selectedFlow(repo::observeTripQuotes).stateIn(viewModelScope, screenSharing, emptyList())
    val expenses = selectedFlow(repo::observeExpenses).stateIn(viewModelScope, screenSharing, emptyList())
    val gpsDevices = selectedFlow(repo::observeGpsDevices).stateIn(viewModelScope, screenSharing, emptyList())
    val parts = selectedFlow(repo::observeParts).stateIn(viewModelScope, screenSharing, emptyList())
    val tires = selectedFlow(repo::observeTires).stateIn(viewModelScope, screenSharing, emptyList())
    val batteries = selectedFlow(repo::observeBatteries).stateIn(viewModelScope, screenSharing, emptyList())
    val faults = selectedFlow(repo::observeFaults).stateIn(viewModelScope, screenSharing, emptyList())
    val documents = selectedFlow(repo::observeDocuments).stateIn(viewModelScope, screenSharing, emptyList())
    val reminders = selectedFlow(repo::observeReminders).stateIn(viewModelScope, screenSharing, emptyList())

    val latestGps: StateFlow<GpsReadingEntity?> = selectedVehicle.filterNotNull().map { it.vehicleId }.distinctUntilChanged()
        .flatMapLatest(repo::observeLatestGps).stateIn(viewModelScope, screenSharing, null)

    private val androidAutoConnected =
        container.androidAutoConnectionMonitor
            .observeAndroidAutoConnected()
            .stateIn(
                viewModelScope,
                screenSharing,
                false
            )

    val vehicleContextDecision = combine(
        operationalVehicles,
        androidAutoConnected,
        container.vehicleContextStore.androidAutoVehicleIdFlow,
        container.trackerDeviceIdentityStore.roleFlow,
        container.vehicleContextStore.headUnitVehicleIdFlow
    ) { list, autoConnected, autoVehicleId, _, _ ->
        Triple(list, autoConnected, autoVehicleId)
    }.flatMapLatest { (list, autoConnected, autoVehicleId) ->
            if (list.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(
                    com.ahmed.carmanager.data.gps.VehicleContextDecision(
                        com.ahmed.carmanager.data.gps.VehicleContextState.NO_MATCH
                    )
                )
            } else {
                kotlinx.coroutines.flow.combine(
                    list.map { vehicle ->
                        kotlinx.coroutines.flow.combine(
                            container.trustedBluetoothConnectionMonitor
                                .observe(vehicle.vehicleId),
                            container.liveTrackerPresenceManager
                                .observeHeadUnitOnline(vehicle.vehicleId),
                            container.iTrackVehicleSignalManager
                                .observeConfirmed(vehicle.vehicleId)
                        ) { connected, headUnitOnline, itrackConfirmed ->
                                container.vehicleContextEngine.candidate(
                                    vehicleId = vehicle.vehicleId,
                                    trustedBluetoothConnected = connected,
                                    androidAutoConnected = autoConnected && autoVehicleId == vehicle.vehicleId,
                                    headUnitOnline = headUnitOnline,
                                    itrackVehicleConfirmed = itrackConfirmed
                                )
                            }
                    }
                ) { candidates ->
                    container.vehicleContextEngine.resolve(
                        candidates = candidates.toList()
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            screenSharing,
            com.ahmed.carmanager.data.gps.VehicleContextDecision(
                com.ahmed.carmanager.data.gps.VehicleContextState.NO_MATCH
            )
        )

    private val _events = MutableSharedFlow<AppUiEvent>(extraBufferCapacity = 24)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            operationalVehicles.collect { list ->
                val current = _selectedVehicleId.value
                if (current == null || list.none { it.vehicleId == current }) {
                    _selectedVehicleId.value = list.firstOrNull { it.isPrimary }?.vehicleId ?: list.firstOrNull()?.vehicleId
                }
            }
        }
        viewModelScope.launch {
            accountUser.collect { user ->
                _selectedVehicleId.value = null
                val hasLegacy = user != null && vehiclesRepo.hasUnclaimedVehicles()
                _hasUnclaimedVehicles.value = hasLegacy
                if (user != null && !hasLegacy) {
                    val result = container.cloudBackupManager.restoreIfLocalEmpty()
                    if (result is CloudDataResult.Success && result.changed) _events.emit(AppUiEvent.Message(result.messageAr))
                }
            }
        }
    }

    fun showMessage(message: String) { _events.tryEmit(AppUiEvent.Message(message)) }

    fun signInWithEmail(email: String, password: String) = launchAuth { authRepo.signInWithEmail(email, password) }
    fun createAccount(email: String, password: String) = launchAuth { authRepo.createAccount(email, password) }
    fun signInWithGoogle(idToken: String) = launchAuth { authRepo.signInWithGoogle(idToken) }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            showMessage("اكتب بريدك الإلكتروني أولًا.")
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            val result = authRepo.sendPasswordReset(email)
            _authBusy.value = false
            if (result.isSuccess) _events.emit(AppUiEvent.Message("تم إرسال رابط إعادة تعيين كلمة المرور إلى بريدك."))
            else _events.emit(AppUiEvent.Message("تعذر إرسال رابط إعادة التعيين الآن."))
        }
    }

    fun updateAccountDisplayName(name: String) {
        if (name.isBlank()) {
            showMessage("اكتب اسمًا صحيحًا.")
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            when (val result = authRepo.updateDisplayName(name)) {
                is AuthResult.Success -> _events.emit(AppUiEvent.Message("تم تحديث اسم الحساب بنجاح."))
                is AuthResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
            _authBusy.value = false
        }
    }

    fun sendVerificationEmail() = viewModelScope.launch {
        _authBusy.value = true
        val result = authRepo.sendEmailVerification()
        _authBusy.value = false
        if (result.isSuccess) _events.emit(AppUiEvent.Message("تم إرسال رسالة التحقق إلى بريدك."))
        else _events.emit(AppUiEvent.Message("تعذر إرسال رسالة التحقق الآن."))
    }

    fun refreshAccount() = viewModelScope.launch {
        _authBusy.value = true
        val result = authRepo.refreshCurrentUser()
        _authBusy.value = false
        result.onSuccess { refreshed ->
            _events.emit(AppUiEvent.Message(if (refreshed.emailVerified) "تم تأكيد البريد بنجاح." else "البريد لم يتم تأكيده بعد."))
        }.onFailure {
            _events.emit(AppUiEvent.Message("تعذر تحديث حالة الحساب الآن."))
        }
    }

    fun resetCurrentAccountPassword() {
        val email = accountUser.value?.email
        if (email.isNullOrBlank()) {
            showMessage("لا يوجد بريد إلكتروني مرتبط بهذا الحساب.")
            return
        }
        sendPasswordReset(email)
    }

    fun signOut() {
        viewModelScope.launch {
            if (authRepo.currentUid() != null) container.cloudBackupManager.uploadLatest()
            _selectedVehicleId.value = null
            authRepo.signOut()
        }
    }

    fun claimLegacyVehicles() = viewModelScope.launch {
        when (val result = vehiclesRepo.claimUnclaimedVehicles()) {
            is VehicleRepositoryResult.Success -> {
                _hasUnclaimedVehicles.value = false
                authRepo.currentUid()?.let { uid ->
                    container.gpsCredentialStore.migrateLegacyTo(uid, GpsProvider.ITRACK)
                    container.gpsCredentialStore.migrateLegacyTo(uid, GpsProvider.ETRACK)
                }
                _events.emit(AppUiEvent.Message(if (result.value > 0) "تم ربط ${result.value} سيارة محلية بحسابك بأمان." else "لا توجد بيانات محلية تحتاج للربط."))
                if (result.value > 0) uploadCloudQuietly()
            }
            is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
        }
    }

    fun syncCloudNow() = viewModelScope.launch {
        _cloudBusy.value = true
        when (val result = container.cloudBackupManager.uploadLatest()) {
            is CloudDataResult.Success -> _events.emit(AppUiEvent.Message(result.messageAr))
            is CloudDataResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
        }
        _cloudBusy.value = false
    }

    fun restoreCloudNow() = viewModelScope.launch {
        _cloudBusy.value = true
        when (val result = container.cloudBackupManager.restoreLatest()) {
            is CloudDataResult.Success -> {
                _selectedVehicleId.value = null
                _events.emit(AppUiEvent.Message(result.messageAr))
            }
            is CloudDataResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
        }
        _cloudBusy.value = false
    }

    private fun launchAuth(block: suspend () -> AuthResult) = viewModelScope.launch {
        _authBusy.value = true
        when (val result = block()) {
            is AuthResult.Success -> _events.emit(AppUiEvent.Message("مرحبًا ${result.user.displayName ?: result.user.email ?: "بك"}."))
            is AuthResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
        }
        _authBusy.value = false
    }

    private fun normalizeMaintenanceTitle(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun reminderRule(intervalKm: Double?, intervalMonths: Int?): ReminderRule = when {
        intervalKm != null && intervalMonths != null -> ReminderRule.WHICHEVER_COMES_FIRST
        intervalKm != null -> ReminderRule.ODOMETER_ONLY
        intervalMonths != null -> ReminderRule.DATE_ONLY
        else -> ReminderRule.WHICHEVER_COMES_FIRST
    }

    fun selectVehicle(vehicleId: String) { _selectedVehicleId.value = vehicleId }
    fun addVehicle(input: NewVehicleInput) = viewModelScope.launch {
        when (val result = vehiclesRepo.addVehicle(input.toCreateRequest())) {
            is VehicleRepositoryResult.Success -> {
                val vehicleId = result.value
                _selectedVehicleId.value = vehicleId

                // The comprehensive owner guide is read-only by default. Creating a vehicle must
                // not silently turn every guide row into an active reminder. Only baseline rows the
                // user explicitly filled in during the wizard become maintenance-plan rows.
                val savedVehicle = vehiclesRepo.observeVehicles().first()
                    .firstOrNull { it.vehicleId == vehicleId }
                val preset = MaintenanceCatalog.presetFor(
                    brand = input.brand,
                    model = input.model,
                    year = input.year,
                    displayName = input.displayName
                )
                val templates = savedVehicle
                    ?.let { MaintenanceGuidanceCatalog.templatesFor(it, preset) }
                    .orEmpty()

                var baselineAdded = 0
                var baselineFailed = 0
                input.maintenanceBaseline.forEach { draft ->
                    val template = templates.firstOrNull {
                        normalizeMaintenanceTitle(it.titleAr) == normalizeMaintenanceTitle(draft.titleAr)
                    }
                    val planInput = MaintenancePlanInput(
                        titleAr = template?.titleAr ?: draft.titleAr,
                        category = template?.category ?: "صيانة",
                        intervalKm = template?.intervalKm,
                        intervalMonths = template?.intervalMonths,
                        reminderRule = reminderRule(template?.intervalKm, template?.intervalMonths),
                        estimatedCost = null,
                        warningBeforeKm = template?.warningBeforeKm ?: 1_000.0,
                        warningBeforeDays = template?.warningBeforeDays ?: 30,
                        notes = template?.notes,
                        lastServiceOdometerKm = draft.lastServiceOdometerKm,
                        lastServiceDate = draft.lastServiceDate,
                        priority = template?.priority ?: 0
                    )
                    when (repo.addMaintenancePlan(vehicleId, planInput)) {
                        is VehicleRepositoryResult.Success -> baselineAdded++
                        is VehicleRepositoryResult.Error -> baselineFailed++
                    }
                }

                input.initialTireSet?.let { tires ->
                    val positions = listOf(TirePosition.FRONT_LEFT, TirePosition.FRONT_RIGHT, TirePosition.REAR_LEFT, TirePosition.REAR_RIGHT)
                    positions.forEach { position ->
                        repo.addTire(vehicleId, TireInput(tires.brand, tires.model, tires.size, position, tires.installOdometerKm, tires.totalCost?.div(4.0), installDate = tires.installDate))
                    }
                }
                input.initialBattery?.let { battery ->
                    repo.addBattery(vehicleId, BatteryInput(battery.brand, battery.model, battery.capacityAh, battery.installOdometerKm, battery.cost, battery.warrantyMonths, installDate = battery.installDate))
                }

                val message = when {
                    baselineAdded > 0 && baselineFailed == 0 ->
                        "تم حفظ المركبة. الدليل الكامل متاح بشكل مستقل، وتمت إضافة $baselineAdded بند صيانة فقط من البيانات التي أدخلت آخر تغيير لها."
                    baselineAdded > 0 ->
                        "تم حفظ المركبة وإضافة $baselineAdded بند صيانة معروف. تعذر إضافة $baselineFailed بند ويمكن إضافته لاحقًا من الدليل."
                    baselineFailed > 0 ->
                        "تم حفظ المركبة. الدليل الكامل متاح بشكل مستقل، وتعذر تجهيز البنود المعروفة الآن ويمكن إضافتها لاحقًا."
                    else ->
                        "تم حفظ المركبة. دليل السيارة متاح بشكل مستقل ولن يتحول تلقائيًا إلى تنبيهات صيانة."
                }
                _events.emit(AppUiEvent.Message(message))
                uploadCloudQuietly()
            }
            is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
        }
    }
    fun updateVehicle(input: EditVehicleInput) = launchOperation { vehiclesRepo.updateVehicle(input.toUpdateRequest()) }
    fun makePrimary(vehicleId: String) = launchOperation { vehiclesRepo.makePrimary(vehicleId) }
    fun archive(vehicleId: String) = launchOperation { vehiclesRepo.archive(vehicleId) }
    fun restore(vehicleId: String) = launchOperation { vehiclesRepo.restore(vehicleId) }
    fun markSold(vehicleId: String, odometerKm: Double, salePrice: Double?) = launchOperation { vehiclesRepo.markSold(SellVehicleRequest(vehicleId, odometerKm, salePrice)) }
    fun softDeleteVehicle(vehicleId: String) = launchOperation { vehiclesRepo.softDelete(vehicleId) }
    fun saveInspectionTemplateConfig(config: String?) = withVehicle { vehiclesRepo.saveInspectionTemplateConfig(it, config) }

    fun setOdometer(value: Double) = withVehicle { repo.setOdometer(it, value) }
    fun addMaintenancePlan(input: MaintenancePlanInput) = withVehicle { repo.addMaintenancePlan(it, input) }
    fun updateMaintenancePlan(planId: String, input: MaintenancePlanInput) = withVehicle { repo.updateMaintenancePlan(it, planId, input) }
    fun setMaintenancePlanActive(planId: String, active: Boolean) = withVehicle { repo.setMaintenancePlanActive(it, planId, active) }
    fun deleteMaintenancePlan(planId: String) {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run { showMessage("اختر مركبة أولًا."); return }
        viewModelScope.launch {
            when (val result = repo.deleteMaintenancePlan(vehicleId, planId)) {
                is VehicleRepositoryResult.Success -> {
                    _events.emit(AppUiEvent.Message("تم حذف بند الصيانة من الخطة مع الاحتفاظ بسجل الصيانة التاريخي بالكامل."))
                    uploadCloudQuietly()
                }
                is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }
    fun installMaintenancePreset(preset: MaintenancePreset) {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run { showMessage("اختر سيارة أولًا."); return }
        viewModelScope.launch {
            when (val result = repo.installMaintenancePreset(vehicleId, preset)) {
                is VehicleRepositoryResult.Success -> {
                    _events.emit(AppUiEvent.Message(if (result.value > 0) "تمت إضافة ${result.value} بند صيانة إلى القائمة الذكية." else "القائمة محدثة بالفعل ولا توجد بنود ناقصة."))
                    uploadCloudQuietly()
                }
                is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }
    fun addMaintenanceRecord(input: MaintenanceRecordInput) = withVehicle { repo.addMaintenanceRecord(it, input) }
    fun addFuel(input: FuelInput) = withVehicle { repo.addFuel(it, input) }
    fun addTrip(input: TripInput) = withVehicle { repo.addTrip(it, input) }
    fun deleteTripRecord(trip: TripEntity) {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run {
            showMessage("اختر مركبة أولًا.")
            return
        }
        if (trip.vehicleId != vehicleId) {
            showMessage("تعذر حذف المشوار لأن المركبة الحالية لا تطابق السجل.")
            return
        }
        viewModelScope.launch {
            when (val result = repo.deleteTrip(vehicleId, trip.id)) {
                is VehicleRepositoryResult.Success -> {
                    _events.emit(AppUiEvent.Message("تم حذف المشوار بأمان. لم يتم تغيير عداد المركبة الحالي أو حذف قراءات GPS الخام."))
                    uploadCloudQuietly()
                }
                is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }
    fun saveTripQuote(input: TripQuoteInput) {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run { showMessage("اختر مركبة أولًا."); return }
        viewModelScope.launch {
            when (val result = repo.addTripQuote(vehicleId, input)) {
                is VehicleRepositoryResult.Success -> {
                    _events.emit(AppUiEvent.Message("تم حفظ عرض السعر ويمكن الرجوع إليه أو تحويله لاحقًا إلى مشوار منفذ."))
                    uploadCloudQuietly()
                }
                is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }

    fun deleteTripQuote(quote: TripQuoteEntity) {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run { showMessage("اختر مركبة أولًا."); return }
        viewModelScope.launch {
            when (val result = repo.deleteTripQuote(vehicleId, quote.id)) {
                is VehicleRepositoryResult.Success -> {
                    _events.emit(AppUiEvent.Message("تم حذف عرض السعر من القائمة."))
                    uploadCloudQuietly()
                }
                is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }

    fun convertTripQuoteToTrip(quote: TripQuoteEntity) {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run { showMessage("اختر مركبة أولًا."); return }
        viewModelScope.launch {
            when (val result = repo.convertTripQuoteToTrip(vehicleId, quote.id)) {
                is VehicleRepositoryResult.Success -> {
                    _events.emit(AppUiEvent.Message("تم تسجيل عرض السعر كمشوار عمل منفذ وتحديث العداد بالمسافة المحفوظة."))
                    uploadCloudQuietly()
                }
                is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }

    fun addExpense(input: ExpenseInput) = withVehicle { repo.addExpense(it, input) }
    fun addGpsDevice(input: GpsDeviceInput) = withVehicle { repo.addGpsDevice(it, input) }
    fun calibrateGps(deviceId: String, gpsMileageKm: Double, vehicleOdometerKm: Double) = withVehicle { repo.calibrateGps(it, deviceId, gpsMileageKm, vehicleOdometerKm) }
    fun addPart(input: PartInput) = withVehicle { repo.addPart(it, input) }
    fun addTire(input: TireInput) = withVehicle { repo.addTire(it, input) }
    fun addBattery(input: BatteryInput) = withVehicle { repo.addBattery(it, input) }
    fun addFault(input: FaultInput) = withVehicle { repo.addFault(it, input) }
    fun addDocument(input: DocumentInput) = withVehicle { repo.addDocument(it, input) }
    fun addReminder(input: ReminderInput) = withVehicle { repo.addReminder(it, input) }
    fun completeReminder(reminder: ReminderEntity) = launchOperation { repo.completeReminder(reminder) }

    fun gpsAccount(provider: GpsProvider): String? = authRepo.currentUid()?.let { uid -> container.gpsCredentialStore.get(uid, provider)?.account }
    fun hasGpsCredentials(provider: GpsProvider): Boolean = authRepo.currentUid()?.let { uid -> container.gpsCredentialStore.has(uid, provider) } ?: false

    fun saveGpsCredentials(provider: GpsProvider, account: String, password: String) {
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUid() ?: run {
                    _events.emit(AppUiEvent.Message("سجل الدخول أولًا.")); return@launch
                }
                if (provider != GpsProvider.ITRACK && provider != GpsProvider.ETRACK) {
                    _events.emit(AppUiEvent.Message("هذا المزود لا يحتاج بيانات دخول مباشرة.")); return@launch
                }
                if (account.isBlank() || password.isBlank()) {
                    _events.emit(AppUiEvent.Message("أدخل اسم الحساب وكلمة المرور.")); return@launch
                }
                container.gpsCredentialStore.save(uid, provider, account, password)
                _events.emit(AppUiEvent.Message("تم حفظ بيانات ${if (provider == GpsProvider.ITRACK) "iTrack" else "eTrack"} مشفرة ومرتبطة بحسابك فقط."))
            } catch (_: Throwable) {
                _events.emit(AppUiEvent.Message("تعذر حفظ بيانات GPS بأمان."))
            }
        }
    }

    fun syncGpsNow() {
        val vehicleId = selectedVehicle.value?.vehicleId ?: run { showMessage("اختر سيارة أولًا."); return }
        viewModelScope.launch {
            when (val result = container.gpsSyncManager.syncVehicle(vehicleId)) {
                is GpsSyncResult.Success -> {
                    _events.emit(AppUiEvent.Message("تم تحديث GPS والعداد من ${result.updatedDevices} جهاز."))
                    uploadCloudQuietly()
                }
                is GpsSyncResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            when (val result = container.backupManager.export(app, uri)) {
                is BackupResult.Success -> _events.emit(AppUiEvent.Message(result.messageAr))
                is BackupResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            when (val result = container.backupManager.import(app, uri)) {
                is BackupResult.Success -> {
                    _selectedVehicleId.value = null
                    _hasUnclaimedVehicles.value = vehiclesRepo.hasUnclaimedVehicles()
                    _events.emit(AppUiEvent.Message(result.messageAr))
                }
                is BackupResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
            }
        }
    }

    private fun withVehicle(block: suspend (String) -> VehicleRepositoryResult<*>) {
        val id = selectedVehicle.value?.vehicleId
        if (id == null) { showMessage("أضف سيارة أو اختر سيارة أولًا."); return }
        launchOperation { block(id) }
    }

    private fun launchOperation(block: suspend () -> VehicleRepositoryResult<*>) = viewModelScope.launch {
        when (val result = block()) {
            is VehicleRepositoryResult.Success -> uploadCloudQuietly()
            is VehicleRepositoryResult.Error -> _events.emit(AppUiEvent.Message(result.messageAr))
        }
    }

    private fun uploadCloudQuietly() {
        if (authRepo.currentUid() == null) return
        viewModelScope.launch { container.cloudBackupManager.uploadLatest() }
    }
}

package com.ahmed.carmanager.data.repository

import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenanceGuidanceCatalog
import com.ahmed.carmanager.data.maintenance.MaintenancePreset
import java.util.Calendar
import kotlin.math.max

class RoomCarOperationsRepository(
    private val database: CarDatabase,
    private val authRepository: AuthRepository
) : CarOperationsRepository {
    private val vehicleDao = database.vehicleDao()
    private val maintenanceDao = database.maintenanceDao()
    private val fuelDao = database.fuelDao()
    private val tripDao = database.tripDao()
    private val tripQuoteDao = database.tripQuoteDao()
    private val expenseDao = database.expenseDao()
    private val odometerDao = database.odometerDao()
    private val gpsDao = database.gpsDao()
    private val assetDao = database.assetDao()
    private val supportDao = database.supportDao()

    override fun observeMaintenancePlans(vehicleId: String) = maintenanceDao.observePlans(vehicleId)
    override fun observeAllMaintenancePlans(vehicleId: String) = maintenanceDao.observeAllPlans(vehicleId)
    override fun observeMaintenanceHistory(vehicleId: String) = maintenanceDao.observeHistory(vehicleId)
    override fun observeOdometer(vehicleId: String) = odometerDao.observeByVehicle(vehicleId)
    override fun observeFuel(vehicleId: String) = fuelDao.observeByVehicle(vehicleId)
    override fun observeTrips(vehicleId: String) = tripDao.observeByVehicle(vehicleId)
    override fun observeTripQuotes(vehicleId: String) = tripQuoteDao.observeByVehicle(vehicleId)
    override fun observeExpenses(vehicleId: String) = expenseDao.observeByVehicle(vehicleId)
    override fun observeGpsDevices(vehicleId: String) = gpsDao.observeDevices(vehicleId)
    override fun observeLatestGps(vehicleId: String) = gpsDao.observeLatestReading(vehicleId)
    override fun observeParts(vehicleId: String) = assetDao.observeParts(vehicleId)
    override fun observeTires(vehicleId: String) = assetDao.observeTires(vehicleId)
    override fun observeBatteries(vehicleId: String) = assetDao.observeBatteries(vehicleId)
    override fun observeFaults(vehicleId: String) = supportDao.observeFaults(vehicleId)
    override fun observeDocuments(vehicleId: String) = supportDao.observeDocuments(vehicleId)
    override fun observeReminders(vehicleId: String) = supportDao.observeOpenReminders(vehicleId)

    override suspend fun addMaintenancePlan(vehicleId: String, input: MaintenancePlanInput) = safeOperation {
        validatePlan(input)?.let { return@safeOperation VehicleRepositoryResult.Error(it) }
        val vehicle = requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")

        val lastKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else input.lastServiceOdometerKm
        val lastDate = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.lastServiceDate
        val nextKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else {
            input.nextDueOdometerKm ?: if (lastKm != null && input.intervalKm != null) lastKm + input.intervalKm else null
        }
        val nextDate = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else {
            input.nextDueDate ?: if (lastDate != null && input.intervalMonths != null) addMonths(lastDate, input.intervalMonths) else null
        }

        maintenanceDao.insertPlan(
            MaintenancePlanEntity(
                vehicleId = vehicleId,
                titleAr = input.titleAr.trim(),
                category = input.category.trim().ifBlank { "دورية" },
                intervalKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else input.intervalKm,
                intervalMonths = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.intervalMonths,
                reminderRule = input.reminderRule,
                warningBeforeKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else input.warningBeforeKm,
                warningBeforeDays = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.warningBeforeDays,
                estimatedCost = input.estimatedCost,
                lastServiceOdometerKm = lastKm,
                lastServiceDate = lastDate,
                nextDueOdometerKm = nextKm,
                nextDueDate = nextDate,
                status = statusFor(vehicle.currentOdometerKm, nextKm, nextDate, input.reminderRule, input.warningBeforeKm, input.warningBeforeDays),
                priority = input.priority,
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun updateMaintenancePlan(vehicleId: String, planId: String, input: MaintenancePlanInput) = safeOperation {
        validatePlan(input)?.let { return@safeOperation VehicleRepositoryResult.Error(it) }
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            val current = maintenanceDao.getPlan(vehicleId, planId)
                ?: return@withTransaction VehicleRepositoryResult.Error("بند الصيانة غير موجود.")

            val lastKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else input.lastServiceOdometerKm
            val lastDate = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.lastServiceDate
            val nextKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else {
                input.nextDueOdometerKm ?: if (lastKm != null && input.intervalKm != null) lastKm + input.intervalKm else null
            }
            val nextDate = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else {
                input.nextDueDate ?: if (lastDate != null && input.intervalMonths != null) addMonths(lastDate, input.intervalMonths) else null
            }

            maintenanceDao.updatePlan(
                current.copy(
                    titleAr = input.titleAr.trim(),
                    category = input.category.trim().ifBlank { "دورية" },
                    intervalKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else input.intervalKm,
                    intervalMonths = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.intervalMonths,
                    reminderRule = input.reminderRule,
                    warningBeforeKm = if (input.reminderRule == ReminderRule.DATE_ONLY) null else input.warningBeforeKm,
                    warningBeforeDays = if (input.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.warningBeforeDays,
                    estimatedCost = input.estimatedCost,
                    lastServiceOdometerKm = lastKm,
                    lastServiceDate = lastDate,
                    nextDueOdometerKm = nextKm,
                    nextDueDate = nextDate,
                    status = statusFor(vehicle.currentOdometerKm, nextKm, nextDate, input.reminderRule, input.warningBeforeKm, input.warningBeforeDays),
                    priority = input.priority,
                    notes = input.notes.cleanOrNull(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun setMaintenancePlanActive(vehicleId: String, planId: String, active: Boolean) = safeOperation {
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        val plan = maintenanceDao.getPlan(vehicleId, planId)
            ?: return@safeOperation VehicleRepositoryResult.Error("بند الصيانة غير موجود.")
        maintenanceDao.updatePlan(plan.copy(isActive = active, updatedAt = System.currentTimeMillis()))
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun deleteMaintenancePlan(vehicleId: String, planId: String) = safeOperation {
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")
        val plan = maintenanceDao.getPlan(vehicleId, planId)
            ?: return@safeOperation VehicleRepositoryResult.Error("بند الصيانة غير موجود أو تم حذفه بالفعل.")

        // Safe deletion is a tombstone only. Historical maintenance records keep their original
        // maintenancePlanId, invoices and attachments because no maintenance record row is deleted.
        maintenanceDao.updatePlan(
            plan.copy(
                isActive = false,
                isDeleted = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun installMaintenancePreset(vehicleId: String, preset: MaintenancePreset) = safeOperation {
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")

            val currentPlans = maintenanceDao.getAllPlans(vehicleId).filter { !it.isDeleted }
            val grouped = currentPlans.groupBy { maintenancePrimaryIdentity(it.titleAr) }
            val retained = linkedMapOf<String, MaintenancePlanEntity>()
            var changed = 0

            grouped.forEach { (identity, group) ->
                val keep = group.maxWithOrNull(
                    compareBy<MaintenancePlanEntity> { maintenancePlanSignalScore(it) }
                        .thenBy { it.updatedAt }
                ) ?: return@forEach
                retained[identity] = keep
                group.filter { it.id != keep.id }.forEach { duplicate ->
                    maintenanceDao.updatePlan(
                        duplicate.copy(
                            isActive = false,
                            isDeleted = true,
                            notes = duplicate.notes ?: "تم إخفاء هذا البند تلقائيًا لأنه مكرر دلاليًا؛ سجل التنفيذ التاريخي لم يُحذف.",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    changed++
                }
            }

            fun allRetainedPlans(): List<MaintenancePlanEntity> = retained.values.toList()

            MaintenanceGuidanceCatalog.templatesFor(vehicle, preset).forEach { template ->
                val templateConcepts = maintenanceConcepts(template.titleAr)
                val existing = allRetainedPlans().firstOrNull { plan ->
                    val existingConcepts = maintenanceConcepts(plan.titleAr)
                    existingConcepts.containsAll(templateConcepts) ||
                        maintenancePrimaryIdentity(plan.titleAr) == maintenancePrimaryIdentity(template.titleAr)
                }

                val templateRule = when {
                    template.intervalKm != null && template.intervalMonths != null -> ReminderRule.WHICHEVER_COMES_FIRST
                    template.intervalKm != null -> ReminderRule.ODOMETER_ONLY
                    template.intervalMonths != null -> ReminderRule.DATE_ONLY
                    else -> ReminderRule.WHICHEVER_COMES_FIRST
                }

                if (existing != null) {
                    // Preserve every explicit user value. Only fill fields that were previously unknown.
                    val resolvedKm = existing.intervalKm ?: template.intervalKm
                    val resolvedMonths = existing.intervalMonths ?: template.intervalMonths
                    val resolvedRule = if (existing.intervalKm == null && existing.intervalMonths == null) templateRule else existing.reminderRule
                    val enriched = existing.copy(
                        intervalKm = resolvedKm,
                        intervalMonths = resolvedMonths,
                        reminderRule = resolvedRule,
                        warningBeforeKm = existing.warningBeforeKm ?: template.warningBeforeKm,
                        warningBeforeDays = existing.warningBeforeDays ?: template.warningBeforeDays,
                        notes = existing.notes?.takeIf { it.isNotBlank() } ?: template.notes,
                        priority = maxOf(existing.priority, template.priority),
                        updatedAt = if (resolvedKm != existing.intervalKm || resolvedMonths != existing.intervalMonths || existing.notes.isNullOrBlank() && !template.notes.isNullOrBlank()) System.currentTimeMillis() else existing.updatedAt
                    )
                    if (enriched != existing) {
                        maintenanceDao.updatePlan(enriched)
                        retained[maintenancePrimaryIdentity(existing.titleAr)] = enriched
                        changed++
                    }
                    return@forEach
                }

                val inserted = MaintenancePlanEntity(
                    vehicleId = vehicleId,
                    titleAr = template.titleAr,
                    category = template.category,
                    intervalKm = template.intervalKm,
                    intervalMonths = template.intervalMonths,
                    reminderRule = templateRule,
                    warningBeforeKm = if (templateRule == ReminderRule.DATE_ONLY) null else template.warningBeforeKm,
                    warningBeforeDays = if (templateRule == ReminderRule.ODOMETER_ONLY) null else template.warningBeforeDays,
                    estimatedCost = null,
                    lastServiceOdometerKm = null,
                    lastServiceDate = null,
                    nextDueOdometerKm = null,
                    nextDueDate = null,
                    status = MaintenanceStatus.UPCOMING,
                    priority = template.priority,
                    notes = template.notes
                )
                maintenanceDao.insertPlan(inserted)
                retained[maintenancePrimaryIdentity(inserted.titleAr)] = inserted
                changed++
            }
            VehicleRepositoryResult.Success(changed)
        }
    }

    override suspend fun addMaintenanceRecord(vehicleId: String, input: MaintenanceRecordInput) = safeOperation {
        if (input.titleAr.isBlank()) return@safeOperation VehicleRepositoryResult.Error("أدخل اسم الصيانة.")
        if (input.odometerKm < 0 || input.totalCost < 0) return@safeOperation VehicleRepositoryResult.Error("راجع قراءة العداد والتكلفة.")
        if (input.laborCost != null && input.laborCost < 0) return@safeOperation VehicleRepositoryResult.Error("تكلفة المصنعية غير صحيحة.")
        if (input.partsCost != null && input.partsCost < 0) return@safeOperation VehicleRepositoryResult.Error("تكلفة القطع غير صحيحة.")
        if (input.date > System.currentTimeMillis() + DAY_MS) return@safeOperation VehicleRepositoryResult.Error("تاريخ الصيانة المنفذة لا يمكن أن يكون في المستقبل.")

        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            val plan = input.planId?.let { maintenanceDao.getPlan(vehicleId, it) }
            val latestLinkedRecord = plan?.let { maintenanceDao.latestRecordForPlan(vehicleId, it.id) }
            val nextKm = if (plan?.reminderRule == ReminderRule.DATE_ONLY) null else plan?.intervalKm?.let { input.odometerKm + it }
            val nextDate = if (plan?.reminderRule == ReminderRule.ODOMETER_ONLY) null else plan?.intervalMonths?.let { addMonths(input.date, it) }

            maintenanceDao.insertRecord(
                MaintenanceRecordEntity(
                    vehicleId = vehicleId,
                    maintenancePlanId = input.planId,
                    titleAr = input.titleAr.trim(),
                    category = input.category.trim().ifBlank { "صيانة" },
                    serviceDate = input.date,
                    odometerKm = input.odometerKm,
                    totalCost = input.totalCost,
                    laborCost = input.laborCost,
                    partsCost = input.partsCost,
                    serviceCenter = input.serviceCenter.cleanOrNull(),
                    technician = input.technician.cleanOrNull(),
                    invoiceNumber = input.invoiceNumber.cleanOrNull(),
                    warrantyUntil = input.warrantyUntil,
                    nextDueOdometerKm = nextKm,
                    nextDueDate = nextDate,
                    notes = input.notes.cleanOrNull()
                )
            )

            if (plan != null && shouldPromoteServiceAnchor(latestLinkedRecord, input)) {
                val effectiveCurrentKm = max(vehicle.currentOdometerKm, input.odometerKm)
                maintenanceDao.updatePlan(
                    plan.copy(
                        lastServiceOdometerKm = if (plan.reminderRule == ReminderRule.DATE_ONLY) null else input.odometerKm,
                        lastServiceDate = if (plan.reminderRule == ReminderRule.ODOMETER_ONLY) null else input.date,
                        nextDueOdometerKm = nextKm,
                        nextDueDate = nextDate,
                        estimatedCost = input.totalCost.takeIf { it > 0.0 } ?: plan.estimatedCost,
                        status = statusFor(effectiveCurrentKm, nextKm, nextDate, plan.reminderRule, plan.warningBeforeKm, plan.warningBeforeDays),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            recordOdometerIfNewer(vehicle, input.odometerKm, OdometerSource.SERVICE, "صيانة: ${input.titleAr}", input.date)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun addFuel(vehicleId: String, input: FuelInput) = safeOperation {
        if (input.amountPaid <= 0 || input.pricePerLiter <= 0) return@safeOperation VehicleRepositoryResult.Error("أدخل مبلغًا وسعر لتر صحيحين.")
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            if (input.odometerKm < vehicle.currentOdometerKm) return@withTransaction VehicleRepositoryResult.Error("قراءة العداد أقل من آخر قراءة مسجلة.")

            val liters = input.amountPaid / input.pricePerLiter
            val previous = fuelDao.latest(vehicleId)
            val previousFull = if (input.isFullTank) fuelDao.latestFullTank(vehicleId) else null
            val distance = previous?.let { input.odometerKm - it.odometerKm }?.takeIf { it >= 0 }
            val fullDistance = previousFull?.let { input.odometerKm - it.odometerKm }?.takeIf { it > 0 }
            val consumption = if (input.isFullTank && fullDistance != null) liters / fullDistance * 100.0 else null
            val kmPerLiter = consumption?.takeIf { it > 0 }?.let { 100.0 / it }
            val costPerKm = fullDistance?.takeIf { it > 0 }?.let { input.amountPaid / it }
            val expectedConsumption = consumption ?: input.expectedConsumptionL100
            val estimatedRange = expectedConsumption?.takeIf { it > 0 }?.let { liters / it * 100.0 }

            fuelDao.insert(
                FuelRecordEntity(
                    vehicleId = vehicleId,
                    fuelDate = input.date,
                    odometerKm = input.odometerKm,
                    fuelType = input.fuelType,
                    stationName = input.stationName.cleanOrNull(),
                    pricePerLiter = input.pricePerLiter,
                    amountPaid = input.amountPaid,
                    liters = liters,
                    isFullTank = input.isFullTank,
                    distanceSincePreviousKm = distance,
                    consumptionLitersPer100Km = consumption,
                    kmPerLiter = kmPerLiter,
                    costPerKm = costPerKm,
                    estimatedRangeKm = estimatedRange,
                    notes = input.notes.cleanOrNull()
                )
            )
            recordOdometerIfNewer(vehicle, input.odometerKm, OdometerSource.FUEL, "تسجيل بنزين", input.date)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun addTrip(vehicleId: String, input: TripInput) = safeOperation {
        if (input.distanceKm < 0) return@safeOperation VehicleRepositoryResult.Error("المسافة غير صحيحة.")
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            val startKm = input.startOdometerKm ?: vehicle.currentOdometerKm
            val endKm = input.endOdometerKm ?: (startKm + input.distanceKm)
            if (endKm < vehicle.currentOdometerKm) return@withTransaction VehicleRepositoryResult.Error("عداد نهاية الرحلة أقل من آخر عداد.")
            tripDao.insert(
                TripEntity(
                    vehicleId = vehicleId,
                    tripType = input.tripType,
                    startTime = input.date,
                    endTime = input.date,
                    startOdometerKm = startKm,
                    endOdometerKm = endKm,
                    distanceKm = input.distanceKm,
                    startAddress = input.startAddress.cleanOrNull(),
                    endAddress = input.endAddress.cleanOrNull(),
                    fuelCost = input.fuelCost,
                    estimatedOperatingCost = input.operatingCost,
                    notes = input.notes.cleanOrNull()
                )
            )
            recordOdometerIfNewer(vehicle, endKm, OdometerSource.TRIP, "نهاية رحلة", input.date)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun deleteTrip(vehicleId: String, tripId: String) = safeOperation {
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")
        val trip = tripDao.getById(tripId)
            ?: return@safeOperation VehicleRepositoryResult.Error("الرحلة غير موجودة أو تم حذفها بالفعل.")
        if (trip.vehicleId != vehicleId) {
            return@safeOperation VehicleRepositoryResult.Error("تعذر حذف رحلة تخص مركبة أخرى.")
        }
        tripDao.update(trip.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addTripQuote(vehicleId: String, input: TripQuoteInput) = safeOperation {
        if (input.oneWayDistanceKm <= 0.0 || input.totalDistanceKm <= 0.0) {
            return@safeOperation VehicleRepositoryResult.Error("أدخل مسافة صحيحة قبل حفظ عرض السعر.")
        }
        if (input.trueTripCost < 0.0 || input.suggestedQuote < 0.0) {
            return@safeOperation VehicleRepositoryResult.Error("راجع قيم التكلفة والتسعير قبل الحفظ.")
        }
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")
        val quote = TripQuoteEntity(
            vehicleId = vehicleId,
            startAddress = input.startAddress.cleanOrNull(),
            endAddress = input.endAddress.cleanOrNull(),
            startLatitude = input.startLatitude,
            startLongitude = input.startLongitude,
            endLatitude = input.endLatitude,
            endLongitude = input.endLongitude,
            routeProvider = input.routeProvider.cleanOrNull(),
            oneWayDistanceKm = input.oneWayDistanceKm,
            roundTrip = input.roundTrip,
            totalDistanceKm = input.totalDistanceKm,
            passengerCount = input.passengerCount.coerceAtLeast(0),
            totalSeatCapacity = input.totalSeatCapacity,
            waitingHours = input.waitingHours.coerceAtLeast(0.0),
            waitingRatePerHour = input.waitingRatePerHour.coerceAtLeast(0.0),
            tolls = input.tolls.coerceAtLeast(0.0),
            driverExpense = input.driverExpense.coerceAtLeast(0.0),
            consumptionLitersPer100Km = input.consumptionLitersPer100Km,
            fuelPricePerLiter = input.fuelPricePerLiter,
            maintenancePerKm = input.maintenancePerKm.coerceAtLeast(0.0),
            includeMaintenance = input.includeMaintenance,
            annualFixedPerKm = input.annualFixedPerKm.coerceAtLeast(0.0),
            includeAnnualFixed = input.includeAnnualFixed,
            depreciationPerKm = input.depreciationPerKm.coerceAtLeast(0.0),
            includeDepreciation = input.includeDepreciation,
            profitMarginPercent = input.profitMarginPercent.coerceIn(0.0, 500.0),
            marketOffer = input.marketOffer,
            estimatedFuelLiters = input.estimatedFuelLiters,
            fuelCost = input.fuelCost,
            trueTripCost = input.trueTripCost,
            suggestedQuote = input.suggestedQuote,
            perPassengerQuote = input.perPassengerQuote,
            readinessPercent = input.readinessPercent.coerceIn(0, 100),
            notes = input.notes.cleanOrNull()
        )
        tripQuoteDao.insert(quote)
        VehicleRepositoryResult.Success(quote.id)
    }

    override suspend fun deleteTripQuote(vehicleId: String, quoteId: String) = safeOperation {
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")
        val quote = tripQuoteDao.getById(vehicleId, quoteId)
            ?: return@safeOperation VehicleRepositoryResult.Error("عرض السعر غير موجود أو تم حذفه بالفعل.")
        tripQuoteDao.update(quote.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun convertTripQuoteToTrip(vehicleId: String, quoteId: String) = safeOperation {
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة أو لا تخص الحساب الحالي.")
            val quote = tripQuoteDao.getById(vehicleId, quoteId)
                ?: return@withTransaction VehicleRepositoryResult.Error("عرض السعر غير موجود.")
            if (!quote.convertedTripId.isNullOrBlank()) {
                return@withTransaction VehicleRepositoryResult.Error("تم تسجيل هذا العرض كمشوار بالفعل.")
            }
            if (quote.totalDistanceKm <= 0.0) {
                return@withTransaction VehicleRepositoryResult.Error("مسافة عرض السعر غير صالحة لتسجيل مشوار.")
            }
            val now = System.currentTimeMillis()
            val startKm = vehicle.currentOdometerKm
            val endKm = startKm + quote.totalDistanceKm
            val trip = TripEntity(
                vehicleId = vehicleId,
                tripType = TripType.WORK,
                startTime = now,
                endTime = now,
                startOdometerKm = startKm,
                endOdometerKm = endKm,
                distanceKm = quote.totalDistanceKm,
                startLatitude = quote.startLatitude,
                startLongitude = quote.startLongitude,
                endLatitude = quote.endLatitude,
                endLongitude = quote.endLongitude,
                startAddress = quote.startAddress,
                endAddress = quote.endAddress,
                estimatedFuelLiters = quote.estimatedFuelLiters,
                fuelCost = quote.fuelCost,
                estimatedOperatingCost = quote.trueTripCost,
                notes = buildString {
                    append("تم تسجيل المشوار من عرض سعر محفوظ. السعر المقترح: ${quote.suggestedQuote} ج.م")
                    quote.marketOffer?.let { append(" • عرض العميل: $it ج.م") }
                    quote.notes?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                }
            )
            tripDao.insert(trip)
            recordOdometerIfNewer(vehicle, endKm, OdometerSource.TRIP, "مشوار عمل من عرض سعر محفوظ", now)
            tripQuoteDao.update(quote.copy(convertedTripId = trip.id, updatedAt = now))
            VehicleRepositoryResult.Success(trip.id)
        }
    }

    override suspend fun addExpense(vehicleId: String, input: ExpenseInput) = safeOperation {
        if (input.amount <= 0 || input.descriptionAr.isBlank()) return@safeOperation VehicleRepositoryResult.Error("أدخل وصفًا ومبلغًا صحيحًا.")
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        expenseDao.insert(
            ExpenseEntity(
                vehicleId = vehicleId,
                expenseDate = input.date,
                category = input.category,
                amount = input.amount,
                odometerKm = input.odometerKm,
                descriptionAr = input.descriptionAr.trim(),
                merchant = input.merchant.cleanOrNull(),
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addGpsDevice(vehicleId: String, input: GpsDeviceInput) = safeOperation {
        if (input.deviceIdentifier.isBlank()) return@safeOperation VehicleRepositoryResult.Error("أدخل معرف جهاز GPS.")
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        gpsDao.insertDevice(
            GpsDeviceEntity(
                vehicleId = vehicleId,
                provider = input.provider,
                deviceName = input.deviceName.cleanOrNull(),
                imei = input.imei.cleanOrNull(),
                deviceIdentifier = input.deviceIdentifier.trim(),
                installedDate = System.currentTimeMillis(),
                installedOdometerKm = input.installedOdometerKm,
                providerMileageAtInstallKm = input.providerMileageAtInstallKm,
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun calibrateGps(vehicleId: String, deviceId: String, gpsMileageKm: Double, vehicleOdometerKm: Double) = safeOperation {
        if (gpsMileageKm < 0 || vehicleOdometerKm < 0) return@safeOperation VehicleRepositoryResult.Error("راجع قيم المعايرة.")
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            gpsDao.insertCalibration(
                GpsCalibrationEntity(
                    vehicleId = vehicleId,
                    gpsDeviceId = deviceId,
                    calibrationDate = System.currentTimeMillis(),
                    gpsMileageKm = gpsMileageKm,
                    vehicleOdometerKm = vehicleOdometerKm,
                    offsetKm = vehicleOdometerKm - gpsMileageKm,
                    notes = "معايرة عداد GPS مع عداد السيارة"
                )
            )
            recordOdometerIfNewer(vehicle, max(vehicle.currentOdometerKm, vehicleOdometerKm), OdometerSource.CALIBRATION, "معايرة GPS")
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun addPart(vehicleId: String, input: PartInput) = safeOperation {
        if (input.nameAr.isBlank()) return@safeOperation VehicleRepositoryResult.Error("أدخل اسم قطعة الغيار.")
        val vehicle = requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        if (input.installOdometerKm != null && (input.installOdometerKm < 0 || input.installOdometerKm > vehicle.currentOdometerKm)) {
            return@safeOperation VehicleRepositoryResult.Error("عداد تركيب القطعة يجب أن يكون بين صفر والعداد الحالي.")
        }
        if (input.installDate != null && input.installDate > System.currentTimeMillis() + DAY_MS) {
            return@safeOperation VehicleRepositoryResult.Error("تاريخ تركيب القطعة لا يمكن أن يكون في المستقبل.")
        }
        assetDao.insertPart(
            PartEntity(
                vehicleId = vehicleId,
                nameAr = input.nameAr.trim(),
                category = input.category.trim().ifBlank { "قطع غيار" },
                brand = input.brand.cleanOrNull(),
                partNumber = input.partNumber.cleanOrNull(),
                purchaseDate = input.purchaseDate,
                installDate = input.installDate,
                installOdometerKm = input.installOdometerKm,
                cost = input.cost,
                expectedLifeKm = input.expectedLifeKm,
                expectedLifeMonths = input.expectedLifeMonths,
                warrantyUntil = input.warrantyUntil,
                supplier = input.supplier.cleanOrNull(),
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addTire(vehicleId: String, input: TireInput) = safeOperation {
        val vehicle = requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        if (input.installOdometerKm != null && (input.installOdometerKm < 0 || input.installOdometerKm > vehicle.currentOdometerKm)) {
            return@safeOperation VehicleRepositoryResult.Error("عداد تركيب الإطار يجب أن يكون بين صفر والعداد الحالي.")
        }
        if (input.installDate != null && input.installDate > System.currentTimeMillis() + DAY_MS) {
            return@safeOperation VehicleRepositoryResult.Error("تاريخ تركيب الإطار لا يمكن أن يكون في المستقبل.")
        }
        assetDao.insertTire(
            TireEntity(
                vehicleId = vehicleId,
                brand = input.brand.cleanOrNull(),
                model = input.model.cleanOrNull(),
                size = input.size.cleanOrNull(),
                serialNumber = input.serialNumber.cleanOrNull(),
                manufactureDateText = input.manufactureDateText.cleanOrNull(),
                installDate = input.installDate,
                installOdometerKm = input.installOdometerKm,
                position = input.position,
                cost = input.cost,
                recommendedPressurePsi = input.pressurePsi,
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addBattery(vehicleId: String, input: BatteryInput) = safeOperation {
        val vehicle = requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        if (input.installOdometerKm != null && (input.installOdometerKm < 0 || input.installOdometerKm > vehicle.currentOdometerKm)) {
            return@safeOperation VehicleRepositoryResult.Error("عداد تركيب البطارية يجب أن يكون بين صفر والعداد الحالي.")
        }
        if (input.installDate != null && input.installDate > System.currentTimeMillis() + DAY_MS) {
            return@safeOperation VehicleRepositoryResult.Error("تاريخ تركيب البطارية لا يمكن أن يكون في المستقبل.")
        }
        assetDao.insertBattery(
            BatteryRecordEntity(
                vehicleId = vehicleId,
                brand = input.brand.cleanOrNull(),
                model = input.model.cleanOrNull(),
                capacityAh = input.capacityAh,
                purchaseDate = input.purchaseDate,
                installDate = input.installDate,
                installOdometerKm = input.installOdometerKm,
                cost = input.cost,
                warrantyMonths = input.warrantyMonths,
                expectedLifeMonths = input.expectedLifeMonths,
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addFault(vehicleId: String, input: FaultInput) = safeOperation {
        if (input.symptomAr.isBlank()) return@safeOperation VehicleRepositoryResult.Error("اكتب وصف العطل.")
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        supportDao.insertFault(
            FaultRecordEntity(
                vehicleId = vehicleId,
                reportedDate = System.currentTimeMillis(),
                odometerKm = input.odometerKm,
                symptomAr = input.symptomAr.trim(),
                diagnosisAr = input.diagnosisAr.cleanOrNull(),
                severity = input.severity,
                repairCost = input.repairCost,
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addDocument(vehicleId: String, input: DocumentInput) = safeOperation {
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        supportDao.insertDocument(
            VehicleDocumentEntity(
                vehicleId = vehicleId,
                documentType = input.type,
                documentNumber = input.number.cleanOrNull(),
                issueDate = input.issueDate,
                expiryDate = input.expiryDate,
                fileUri = input.fileUri.cleanOrNull(),
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun addReminder(vehicleId: String, input: ReminderInput) = safeOperation {
        if (input.titleAr.isBlank()) return@safeOperation VehicleRepositoryResult.Error("أدخل عنوان التنبيه.")
        requireVehicle(vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
        supportDao.insertReminder(
            ReminderEntity(
                vehicleId = vehicleId,
                titleAr = input.titleAr.trim(),
                rule = input.rule,
                dueDate = input.dueDate,
                dueOdometerKm = input.dueOdometerKm,
                warningBeforeDays = input.warningBeforeDays,
                warningBeforeKm = input.warningBeforeKm,
                priority = input.priority,
                notes = input.notes.cleanOrNull()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun completeReminder(reminder: ReminderEntity) = safeOperation {
        requireVehicle(reminder.vehicleId)
            ?: return@safeOperation VehicleRepositoryResult.Error("التنبيه لا يخص الحساب الحالي.")
        supportDao.updateReminder(
            reminder.copy(
                isCompleted = true,
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    override suspend fun setOdometer(vehicleId: String, odometerKm: Double, source: OdometerSource, note: String?) = safeOperation {
        database.withTransaction {
            val vehicle = requireVehicle(vehicleId)
                ?: return@withTransaction VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            if (odometerKm < vehicle.currentOdometerKm) return@withTransaction VehicleRepositoryResult.Error("العداد الجديد أقل من آخر قراءة مسجلة.")
            recordOdometerIfNewer(vehicle, odometerKm, source, note)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    private suspend fun requireVehicle(vehicleId: String): VehicleEntity? {
        val uid = authRepository.currentUid() ?: return null
        return vehicleDao.getById(vehicleId, uid)
    }

    private suspend fun recordOdometerIfNewer(
        vehicle: VehicleEntity,
        odometerKm: Double,
        source: OdometerSource,
        note: String? = null,
        date: Long = System.currentTimeMillis()
    ) {
        if (odometerKm > vehicle.currentOdometerKm) {
            vehicleDao.update(vehicle.copy(currentOdometerKm = odometerKm, updatedAt = System.currentTimeMillis()))
        }
        odometerDao.insert(
            OdometerRecordEntity(
                vehicleId = vehicle.vehicleId,
                odometerKm = odometerKm,
                source = source,
                recordedAt = date,
                notes = note.cleanOrNull()
            )
        )
    }

    private fun shouldPromoteServiceAnchor(latest: MaintenanceRecordEntity?, input: MaintenanceRecordInput): Boolean {
        if (latest == null) return true
        return input.date > latest.serviceDate ||
            (input.date == latest.serviceDate && input.odometerKm >= latest.odometerKm)
    }

    private fun validatePlan(input: MaintenancePlanInput): String? {
        if (input.titleAr.isBlank()) return "أدخل اسم بند الصيانة."
        when (input.reminderRule) {
            ReminderRule.ODOMETER_ONLY -> if ((input.intervalKm ?: 0.0) <= 0.0) return "حدد فترة الصيانة بالكيلومتر."
            ReminderRule.DATE_ONLY -> if ((input.intervalMonths ?: 0) <= 0) return "حدد فترة الصيانة بالشهور."
            ReminderRule.WHICHEVER_COMES_FIRST -> if ((input.intervalKm ?: 0.0) <= 0.0 && (input.intervalMonths ?: 0) <= 0) return "حدد فترة الصيانة بالكيلومتر أو بالشهور."
        }
        if (input.intervalKm != null && input.intervalKm <= 0.0) return "فترة الكيلومترات غير صحيحة."
        if (input.intervalMonths != null && input.intervalMonths <= 0) return "فترة الشهور غير صحيحة."
        if (input.estimatedCost != null && input.estimatedCost < 0.0) return "التكلفة المتوقعة غير صحيحة."
        if (input.warningBeforeKm != null && input.warningBeforeKm < 0.0) return "تنبيه الكيلومترات غير صحيح."
        if (input.warningBeforeDays != null && input.warningBeforeDays < 0) return "تنبيه الأيام غير صحيح."
        if (input.lastServiceOdometerKm != null && input.lastServiceOdometerKm < 0.0) return "عداد آخر صيانة غير صحيح."
        if (input.nextDueOdometerKm != null && input.nextDueOdometerKm < 0.0) return "عداد الصيانة القادمة غير صحيح."
        if (input.lastServiceDate != null && input.lastServiceDate > System.currentTimeMillis() + DAY_MS) return "تاريخ آخر صيانة لا يمكن أن يكون في المستقبل."
        return null
    }

    private fun statusFor(
        currentKm: Double,
        nextKm: Double?,
        nextDate: Long?,
        rule: ReminderRule,
        warningKm: Double?,
        warningDays: Int?
    ): MaintenanceStatus {
        val now = System.currentTimeMillis()
        val kmOverdue = nextKm?.let { it <= currentKm } == true
        val dateOverdue = nextDate?.let { it <= now } == true
        val kmSoon = nextKm?.let { it - currentKm <= (warningKm ?: 1_000.0) } == true
        val dateSoon = nextDate?.let { it - now <= (warningDays ?: 30).toLong() * DAY_MS } == true
        val overdue = when (rule) {
            ReminderRule.ODOMETER_ONLY -> kmOverdue
            ReminderRule.DATE_ONLY -> dateOverdue
            ReminderRule.WHICHEVER_COMES_FIRST -> kmOverdue || dateOverdue
        }
        val dueSoon = when (rule) {
            ReminderRule.ODOMETER_ONLY -> kmSoon
            ReminderRule.DATE_ONLY -> dateSoon
            ReminderRule.WHICHEVER_COMES_FIRST -> kmSoon || dateSoon
        }
        return when {
            overdue -> MaintenanceStatus.OVERDUE
            dueSoon -> MaintenanceStatus.DUE_SOON
            else -> MaintenanceStatus.UPCOMING
        }
    }

    private fun addMonths(time: Long, months: Int): Long = Calendar.getInstance().run {
        timeInMillis = time
        add(Calendar.MONTH, months)
        timeInMillis
    }

    private suspend fun <T> safeOperation(block: suspend () -> VehicleRepositoryResult<T>): VehicleRepositoryResult<T> = try {
        block()
    } catch (t: Throwable) {
        VehicleRepositoryResult.Error("تعذر حفظ العملية. لم يتم حذف أو فقد أي بيانات.", t)
    }

    private fun normalizeTitle(value: String): String = value.trim().lowercase()
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ى", "ي")
        .replace(Regex("[^a-z0-9\u0600-\u06ff]+"), "")

    private fun maintenanceConcepts(title: String): Set<String> {
        val n = normalizeTitle(title)
        val concepts = linkedSetOf<String>()
        if (("زيتالمحرك" in n || "engineoil" in n) && ("فلترالزيت" in n || "oilfilter" in n)) concepts += "engine_oil_service"
        if ("فلترالتكييف" in n || "فلترالمقصوره" in n || "cabinfilter" in n) concepts += "cabin_filter"
        if ("فلترهواء" in n || "airfilter" in n) concepts += "engine_air_filter"
        if ("فلترالوقود" in n || "fuelfilter" in n) concepts += "fuel_filter"
        if ("بوجيه" in n || "شمعاتالاشعال" in n || "sparkplug" in n) concepts += "spark_plugs"
        if (("زيت" in n || "سائل" in n) && ("ناقلالحركه" in n || "الفتيس" in n || "transmission" in n || "cvt" in n)) concepts += "transmission_fluid"
        if ("سائلالفرامل" in n || "brakefluid" in n) concepts += "brake_fluid"
        if (("سائلالتبريد" in n || "تبريدالمحرك" in n || "coolant" in n) && "فحصدوره" !in n) concepts += "coolant"
        if ("تيلالفرامل" in n && ("امامي" in n || "الامامي" in n)) concepts += "front_brake_pads"
        if ("فراملالخلف" in n || "فراملالمحورالخلفي" in n || "تيلالفراملالخلف" in n) concepts += "rear_brakes"
        if ("تدويرالاطارات" in n) concepts += "tire_rotation"
        if (("فحصالاطارات" in n || "ضغطوالنقشه" in n) && "تدوير" !in n) concepts += "tire_inspection"
        if ("زوايا" in n || "ترصيص" in n) concepts += "alignment_balance"
        if ("بطاريه" in n && ("فحص" in n || "الشحن" in n)) concepts += "battery_check"
        if ("سيرالمجموعه" in n || "السيورالخارجيه" in n) concepts += "accessory_belt"
        if ("التوقيت" in n || "سيرالكاتينه" in n) concepts += "timing_system"
        if ("العفشه" in n || "المساعدين" in n) concepts += "suspension_check"
        if ("الكبالن" in n) concepts += "cv_joint_check"
        if ("التوجيه" in n) concepts += "steering_check"
        if ("pcv" in n || "الفاكيوم" in n) concepts += "pcv_vacuum"
        if (concepts.isEmpty()) concepts += "raw:${normalizeTitle(title)}"
        return concepts
    }

    private fun maintenancePrimaryIdentity(title: String): String {
        val concepts = maintenanceConcepts(title)
        return when {
            "engine_oil_service" in concepts -> "engine_oil_service"
            "transmission_fluid" in concepts -> "transmission_fluid"
            "coolant" in concepts && ("اولاستبدال" in normalizeTitle(title) || "بعداولاستبدال" in normalizeTitle(title)) -> "coolant:${normalizeTitle(title)}"
            else -> concepts.sorted().joinToString("+")
        }
    }

    private fun maintenancePlanSignalScore(plan: MaintenancePlanEntity): Int =
        (if (plan.lastServiceOdometerKm != null) 8 else 0) +
            (if (plan.lastServiceDate != null) 8 else 0) +
            (if (plan.nextDueOdometerKm != null) 4 else 0) +
            (if (plan.nextDueDate != null) 4 else 0) +
            (if (plan.estimatedCost != null) 2 else 0) +
            (if (plan.notes?.isNotBlank() == true) 1 else 0) +
            (if (plan.isActive) 1 else 0)

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

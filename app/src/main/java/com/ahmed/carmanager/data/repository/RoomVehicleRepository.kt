package com.ahmed.carmanager.data.repository

import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class RoomVehicleRepository(
    private val database: CarDatabase,
    private val authRepository: AuthRepository
) : VehicleRepository {
    private val vehicleDao = database.vehicleDao()
    private val odometerDao = database.odometerDao()
    private val supportDao = database.supportDao()

    override fun observeVehicles(): Flow<List<VehicleEntity>> = authRepository.user
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else vehicleDao.observeAll(uid) }

    override suspend fun getPrimaryVehicle(): VehicleEntity? {
        val uid = authRepository.currentUid() ?: return null
        return vehicleDao.getPrimary(uid)
    }

    override suspend fun hasUnclaimedVehicles(): Boolean = vehicleDao.countUnclaimed() > 0

    override suspend fun claimUnclaimedVehicles(): VehicleRepositoryResult<Int> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا لربط البيانات المحلية بحسابك.")
        database.withTransaction {
            val count = vehicleDao.countUnclaimed()
            if (count > 0) {
                vehicleDao.claimUnclaimed(uid)
                if (vehicleDao.getPrimary(uid) == null) {
                    vehicleDao.getFirstEligibleForPrimary(uid)?.let { vehicleDao.makePrimary(it.vehicleId, uid) }
                }
            }
            VehicleRepositoryResult.Success(count)
        }
    }

    override suspend fun addVehicle(request: CreateVehicleRequest): VehicleRepositoryResult<String> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا لإضافة مركبة إلى حسابك.")
        validateVehicle(request.vehicleType, request.customVehicleType, request.brand, request.model, request.year, request.odometerKm, request.engineCapacityCc, request.tankCapacityLiters, request.passengerCapacity)?.let {
            return@safeOperation VehicleRepositoryResult.Error(it)
        }
        database.withTransaction {
            val firstVehicle = vehicleDao.countVisible(uid) == 0
            val now = System.currentTimeMillis()
            val vehicle = VehicleEntity(
                ownerUserId = uid,
                displayName = request.displayName.cleanOrNull(),
                brand = request.brand.trim(), model = request.model.trim(), trim = request.trim.cleanOrNull(), year = request.year,
                color = request.color.cleanOrNull(), fuelType = request.fuelType, transmissionType = request.transmissionType,
                engineName = request.engineName.cleanOrNull(), engineCode = request.engineCode.cleanOrNull(), engineCapacityCc = request.engineCapacityCc,
                generationCode = request.generationCode.cleanOrNull(), transmissionName = request.transmissionName.cleanOrNull(), transmissionCode = request.transmissionCode.cleanOrNull(), plateNumber = request.plateNumber.cleanOrNull(), licenseNumber = request.licenseNumber.cleanOrNull(), vin = request.vin.cleanOrNull(), engineNumber = request.engineNumber.cleanOrNull(),
                tankCapacityLiters = request.tankCapacityLiters, tireSize = request.tireSize.cleanOrNull(), passengerCapacity = request.passengerCapacity,
                purchaseDate = request.purchaseDate, purchaseOdometerKm = request.odometerKm.takeIf { it > 0 }, purchasePrice = request.purchasePrice,
                currentOdometerKm = request.odometerKm, vehiclePhotoUri = request.photoUri.cleanOrNull(),
                status = if (firstVehicle) VehicleStatus.ACTIVE else VehicleStatus.SECONDARY, isPrimary = firstVehicle,
                vehicleType = request.vehicleType,
                customVehicleType = request.customVehicleType.cleanOrNull().takeIf { request.vehicleType == VehicleType.OTHER },
                annualLicenseCost = request.annualLicenseCost,
                annualInsuranceCost = request.annualInsuranceCost,
                annualOtherFixedCost = request.annualOtherFixedCost,
                annualDistanceKm = request.annualDistanceKm,
                currentMarketValue = request.currentMarketValue,
                depreciationAnnualPercent = request.depreciationAnnualPercent,
                includeAnnualFixedCostsInTripCost = request.includeAnnualFixedCostsInTripCost,
                includeDepreciationInTripCost = request.includeDepreciationInTripCost
            )
            vehicleDao.insert(vehicle)
            if (request.odometerKm > 0.0) {
                odometerDao.insert(
                    OdometerRecordEntity(
                        vehicleId = vehicle.vehicleId,
                        odometerKm = request.odometerKm,
                        source = OdometerSource.MANUAL,
                        notes = "قراءة العداد عند إضافة المركبة"
                    )
                )
            }
            if (request.purchaseDate != null || request.purchasePrice != null) {
                supportDao.insertOwnership(
                    OwnershipRecordEntity(
                        vehicleId = vehicle.vehicleId,
                        eventType = OwnershipEventType.PURCHASE,
                        eventDate = request.purchaseDate ?: now,
                        odometerKm = request.odometerKm,
                        price = request.purchasePrice,
                        notes = "بيانات شراء المركبة"
                    )
                )
            }
            VehicleRepositoryResult.Success(vehicle.vehicleId)
        }
    }

    override suspend fun updateVehicle(request: UpdateVehicleRequest): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("انتهت جلسة الحساب. سجّل الدخول مرة أخرى.")
        validateVehicle(request.vehicleType, request.customVehicleType, request.brand, request.model, request.year, request.odometerKm ?: 0.0, request.engineCapacityCc, request.tankCapacityLiters, request.passengerCapacity)?.let {
            return@safeOperation VehicleRepositoryResult.Error(it)
        }
        database.withTransaction {
            val current = vehicleDao.getById(request.vehicleId, uid)
                ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
            val requestedOdometer = request.odometerKm ?: current.currentOdometerKm
            if (requestedOdometer < current.currentOdometerKm) {
                return@withTransaction VehicleRepositoryResult.Error("العداد المعدل لا يمكن أن يكون أقل من آخر قراءة مسجلة.")
            }
            val odometerChanged = requestedOdometer > current.currentOdometerKm
            val now = System.currentTimeMillis()
            vehicleDao.update(
                current.copy(
                    displayName = request.displayName.cleanOrNull(), brand = request.brand.trim(), model = request.model.trim(), trim = request.trim.cleanOrNull(),
                    year = request.year, color = request.color.cleanOrNull(), fuelType = request.fuelType, transmissionType = request.transmissionType,
                    engineName = request.engineName.cleanOrNull(), engineCode = request.engineCode.cleanOrNull(), engineCapacityCc = request.engineCapacityCc,
                    generationCode = request.generationCode.cleanOrNull(), transmissionName = request.transmissionName.cleanOrNull(), transmissionCode = request.transmissionCode.cleanOrNull(), plateNumber = request.plateNumber.cleanOrNull(),
                    licenseNumber = request.licenseNumber.cleanOrNull() ?: current.licenseNumber,
                    vin = request.vin.cleanOrNull(), engineNumber = request.engineNumber.cleanOrNull(), tankCapacityLiters = request.tankCapacityLiters,
                    tireSize = request.tireSize.cleanOrNull(), passengerCapacity = request.passengerCapacity, vehiclePhotoUri = request.photoUri.cleanOrNull(), purchasePrice = request.purchasePrice,
                    purchaseDate = request.purchaseDate,
                    currentOdometerKm = requestedOdometer,
                    vehicleType = request.vehicleType,
                    customVehicleType = request.customVehicleType.cleanOrNull().takeIf { request.vehicleType == VehicleType.OTHER },
                    annualLicenseCost = request.annualLicenseCost,
                    annualInsuranceCost = request.annualInsuranceCost,
                    annualOtherFixedCost = request.annualOtherFixedCost,
                    annualDistanceKm = request.annualDistanceKm,
                    currentMarketValue = request.currentMarketValue,
                    depreciationAnnualPercent = request.depreciationAnnualPercent,
                    includeAnnualFixedCostsInTripCost = request.includeAnnualFixedCostsInTripCost,
                    includeDepreciationInTripCost = request.includeDepreciationInTripCost,
                    updatedAt = now
                )
            )
            if (odometerChanged) {
                odometerDao.insert(
                    OdometerRecordEntity(
                        vehicleId = current.vehicleId,
                        odometerKm = requestedOdometer,
                        source = OdometerSource.MANUAL,
                        recordedAt = now,
                        notes = "تحديث العداد من تعديل بيانات المركبة"
                    )
                )
            }
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun makePrimary(vehicleId: String): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا.")
        database.withTransaction {
            val vehicle = vehicleDao.getById(vehicleId, uid) ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
            if (vehicle.status == VehicleStatus.SOLD || vehicle.status == VehicleStatus.ARCHIVED) return@withTransaction VehicleRepositoryResult.Error("أعد المركبة إلى الجراج أولًا قبل جعلها المركبة الحالية.")
            vehicleDao.makePrimary(vehicleId, uid)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun archive(vehicleId: String): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا.")
        database.withTransaction {
            val vehicle = vehicleDao.getById(vehicleId, uid) ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
            if (vehicle.status != VehicleStatus.ARCHIVED) vehicleDao.archive(vehicleId, uid)
            promoteFallbackIfNeeded(uid)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun restore(vehicleId: String): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا.")
        database.withTransaction {
            vehicleDao.getById(vehicleId, uid) ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
            vehicleDao.restore(vehicleId, uid)
            if (vehicleDao.getPrimary(uid) == null) vehicleDao.makePrimary(vehicleId, uid)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun markSold(request: SellVehicleRequest): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا.")
        database.withTransaction {
            val vehicle = vehicleDao.getById(request.vehicleId, uid) ?: return@withTransaction VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
            if (request.odometerKm < vehicle.currentOdometerKm) return@withTransaction VehicleRepositoryResult.Error("عداد البيع لا يمكن أن يكون أقل من آخر عداد مسجل.")
            if (request.salePrice != null && request.salePrice < 0.0) return@withTransaction VehicleRepositoryResult.Error("سعر البيع غير صحيح.")
            vehicleDao.markSold(request.vehicleId, uid, request.soldAt, request.odometerKm, request.salePrice)
            odometerDao.insert(OdometerRecordEntity(vehicleId = request.vehicleId, odometerKm = request.odometerKm, source = OdometerSource.MANUAL, recordedAt = request.soldAt, notes = "قراءة العداد عند بيع المركبة"))
            supportDao.insertOwnership(OwnershipRecordEntity(vehicleId = request.vehicleId, eventType = OwnershipEventType.SALE, eventDate = request.soldAt, odometerKm = request.odometerKm, price = request.salePrice, notes = "تم تسجيل البيع من داخل التطبيق"))
            promoteFallbackIfNeeded(uid)
            VehicleRepositoryResult.Success(Unit)
        }
    }

    override suspend fun softDelete(vehicleId: String): VehicleRepositoryResult<Unit> = safeOperation {
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

    override suspend fun saveInspectionTemplateConfig(vehicleId: String, config: String?): VehicleRepositoryResult<Unit> = safeOperation {
        val uid = authRepository.currentUid() ?: return@safeOperation VehicleRepositoryResult.Error("سجّل الدخول أولًا.")
        val vehicle = vehicleDao.getById(vehicleId, uid)
            ?: return@safeOperation VehicleRepositoryResult.Error("المركبة غير موجودة في هذا الحساب.")
        vehicleDao.update(
            vehicle.copy(
                inspectionTemplateConfig = config?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
        )
        VehicleRepositoryResult.Success(Unit)
    }

    private suspend fun promoteFallbackIfNeeded(uid: String) {
        if (vehicleDao.getPrimary(uid) == null) vehicleDao.getFirstEligibleForPrimary(uid)?.let { vehicleDao.makePrimary(it.vehicleId, uid) }
    }

    private fun validateVehicle(
        vehicleType: VehicleType,
        customVehicleType: String?,
        brand: String,
        model: String,
        year: Int,
        odometerKm: Double,
        engineCapacityCc: Int?,
        tankCapacityLiters: Double?,
        passengerCapacity: Int?
    ): String? {
        if (vehicleType == VehicleType.OTHER && customVehicleType.isNullOrBlank()) return "اكتب نوع المركبة أو صفتها."
        if (brand.isBlank()) return "أدخل الماركة أو الشركة المصنعة."
        if (model.isBlank()) return "أدخل موديل المركبة."
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (year !in 1886..(currentYear + 1)) return "سنة صنع المركبة غير صحيحة."
        if (!odometerKm.isFinite() || odometerKm < 0.0) return "قراءة العداد غير صحيحة."
        if (engineCapacityCc != null && engineCapacityCc <= 0) return "سعة المحرك غير صحيحة."
        if (tankCapacityLiters != null && (!tankCapacityLiters.isFinite() || tankCapacityLiters <= 0.0 || tankCapacityLiters > 5000.0)) {
            return "سعة خزان الوقود غير صحيحة."
        }
        if (passengerCapacity != null && passengerCapacity !in 1..100) return "عدد المقاعد غير صحيح."
        return null
    }

    private suspend fun <T> safeOperation(block: suspend () -> VehicleRepositoryResult<T>): VehicleRepositoryResult<T> = try { block() } catch (t: Throwable) {
        VehicleRepositoryResult.Error("تعذر حفظ التغيير. لم يتم فقد أي بيانات، حاول مرة أخرى.", t)
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

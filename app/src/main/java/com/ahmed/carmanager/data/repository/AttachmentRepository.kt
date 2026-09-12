package com.ahmed.carmanager.data.repository

import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.AttachmentEntity
import com.ahmed.carmanager.data.local.model.AttachmentType
import com.ahmed.carmanager.data.local.model.EntityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class AttachmentRepository(
    database: CarDatabase,
    private val authRepository: AuthRepository
) {
    private val vehicleDao = database.vehicleDao()
    private val supportDao = database.supportDao()

    fun observe(
        vehicleId: String,
        entityType: EntityType,
        entityId: String
    ): Flow<List<AttachmentEntity>> = flow {
        val uid = authRepository.currentUid()
        if (uid == null || vehicleDao.getById(vehicleId, uid) == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(supportDao.observeAttachments(vehicleId, entityType, entityId))
    }

    suspend fun add(
        vehicleId: String,
        entityType: EntityType,
        entityId: String,
        attachmentType: AttachmentType,
        fileUri: String,
        captionAr: String? = null
    ): VehicleRepositoryResult<Unit> {
        return try {
            val uid = authRepository.currentUid()
                ?: return VehicleRepositoryResult.Error("سجل الدخول أولًا.")
            val vehicle = vehicleDao.getById(vehicleId, uid)
                ?: return VehicleRepositoryResult.Error("السيارة غير موجودة أو لا تخص الحساب الحالي.")
            if (entityId.isBlank() || fileUri.isBlank()) {
                return VehicleRepositoryResult.Error("الملف أو السجل المرتبط غير صحيح.")
            }

            supportDao.insertAttachment(
                AttachmentEntity(
                    vehicleId = vehicle.vehicleId,
                    entityType = entityType,
                    entityId = entityId,
                    attachmentType = attachmentType,
                    fileUri = fileUri.trim(),
                    captionAr = captionAr?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            VehicleRepositoryResult.Success(Unit)
        } catch (t: Throwable) {
            VehicleRepositoryResult.Error("تعذر حفظ المرفق. لم يتم حذف أي بيانات.", t)
        }
    }
}

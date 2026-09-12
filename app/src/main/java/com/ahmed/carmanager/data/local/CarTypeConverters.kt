package com.ahmed.carmanager.data.local

import androidx.room.TypeConverter
import com.ahmed.carmanager.data.local.model.*

class CarTypeConverters {
    @TypeConverter fun fromVehicleType(v: VehicleType?) = v?.name
    @TypeConverter fun toVehicleType(v: String?) = v?.let(VehicleType::valueOf)
    @TypeConverter fun fromVehicleStatus(v: VehicleStatus?) = v?.name
    @TypeConverter fun toVehicleStatus(v: String?) = v?.let(VehicleStatus::valueOf)
    @TypeConverter fun fromFuelType(v: FuelType?) = v?.name
    @TypeConverter fun toFuelType(v: String?) = v?.let(FuelType::valueOf)
    @TypeConverter fun fromTransmission(v: TransmissionType?) = v?.name
    @TypeConverter fun toTransmission(v: String?) = v?.let(TransmissionType::valueOf)
    @TypeConverter fun fromOdometerSource(v: OdometerSource?) = v?.name
    @TypeConverter fun toOdometerSource(v: String?) = v?.let(OdometerSource::valueOf)
    @TypeConverter fun fromMaintenanceStatus(v: MaintenanceStatus?) = v?.name
    @TypeConverter fun toMaintenanceStatus(v: String?) = v?.let(MaintenanceStatus::valueOf)
    @TypeConverter fun fromReminderRule(v: ReminderRule?) = v?.name
    @TypeConverter fun toReminderRule(v: String?) = v?.let(ReminderRule::valueOf)
    @TypeConverter fun fromTripType(v: TripType?) = v?.name
    @TypeConverter fun toTripType(v: String?) = v?.let(TripType::valueOf)
    @TypeConverter fun fromGpsProvider(v: GpsProvider?) = v?.name
    @TypeConverter fun toGpsProvider(v: String?) = v?.let(GpsProvider::valueOf)
    @TypeConverter fun fromGpsConnectionStatus(v: GpsConnectionStatus?) = v?.name
    @TypeConverter fun toGpsConnectionStatus(v: String?) = v?.let(GpsConnectionStatus::valueOf)
    @TypeConverter fun fromAccStatus(v: AccStatus?) = v?.name
    @TypeConverter fun toAccStatus(v: String?) = v?.let(AccStatus::valueOf)
    @TypeConverter fun fromTirePosition(v: TirePosition?) = v?.name
    @TypeConverter fun toTirePosition(v: String?) = v?.let(TirePosition::valueOf)
    @TypeConverter fun fromItemStatus(v: ItemStatus?) = v?.name
    @TypeConverter fun toItemStatus(v: String?) = v?.let(ItemStatus::valueOf)
    @TypeConverter fun fromFaultSeverity(v: FaultSeverity?) = v?.name
    @TypeConverter fun toFaultSeverity(v: String?) = v?.let(FaultSeverity::valueOf)
    @TypeConverter fun fromFaultStatus(v: FaultStatus?) = v?.name
    @TypeConverter fun toFaultStatus(v: String?) = v?.let(FaultStatus::valueOf)
    @TypeConverter fun fromExpenseCategory(v: ExpenseCategory?) = v?.name
    @TypeConverter fun toExpenseCategory(v: String?) = v?.let(ExpenseCategory::valueOf)
    @TypeConverter fun fromDocumentType(v: DocumentType?) = v?.name
    @TypeConverter fun toDocumentType(v: String?) = v?.let(DocumentType::valueOf)
    @TypeConverter fun fromAttachmentType(v: AttachmentType?) = v?.name
    @TypeConverter fun toAttachmentType(v: String?) = v?.let(AttachmentType::valueOf)
    @TypeConverter fun fromEntityType(v: EntityType?) = v?.name
    @TypeConverter fun toEntityType(v: String?) = v?.let(EntityType::valueOf)
    @TypeConverter fun fromOwnershipEventType(v: OwnershipEventType?) = v?.name
    @TypeConverter fun toOwnershipEventType(v: String?) = v?.let(OwnershipEventType::valueOf)
}

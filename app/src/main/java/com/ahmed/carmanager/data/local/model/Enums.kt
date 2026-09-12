package com.ahmed.carmanager.data.local.model

enum class VehicleType { CAR, MOTORCYCLE, OTHER }
enum class VehicleStatus { ACTIVE, SECONDARY, SOLD, ARCHIVED }
enum class FuelType { GASOLINE_80, GASOLINE_92, GASOLINE_95, DIESEL, ELECTRIC, HYBRID, OTHER }
enum class TransmissionType { MANUAL, AUTOMATIC, CVT, DCT, OTHER }
enum class OdometerSource { MANUAL, GPS, SERVICE, FUEL, TRIP, IMPORT, CALIBRATION }
enum class MaintenanceStatus { UPCOMING, DUE_SOON, OVERDUE, COMPLETED, SKIPPED }
enum class ReminderRule { DATE_ONLY, ODOMETER_ONLY, WHICHEVER_COMES_FIRST }
enum class TripType { PERSONAL, WORK, TRAVEL, SERVICE, OTHER }
enum class GpsProvider { ITRACK, ETRACK, PHONE, HEAD_UNIT, GENERIC, NONE }
enum class GpsConnectionStatus { ONLINE, OFFLINE, UNKNOWN }
enum class AccStatus { ON, OFF, UNKNOWN }
enum class TirePosition { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, SPARE, UNASSIGNED }
enum class ItemStatus { ACTIVE, REPLACED, RETIRED, DAMAGED, UNKNOWN }
enum class FaultSeverity { LOW, MEDIUM, HIGH, CRITICAL }
enum class FaultStatus { OPEN, DIAGNOSED, IN_REPAIR, RESOLVED, CLOSED }
enum class ExpenseCategory { LICENSE, INSURANCE, WASH, PARKING, TOLL, FINE, ACCESSORY, TOWING, INSPECTION, REPAIR, OTHER }
enum class DocumentType { VEHICLE_LICENSE, INSURANCE, INSPECTION, CONTRACT, RECEIPT, OTHER }
enum class AttachmentType { IMAGE, PDF, DOCUMENT, OTHER }
enum class EntityType { VEHICLE, MAINTENANCE, FUEL, TRIP, EXPENSE, PART, TIRE, BATTERY, FAULT, DOCUMENT, OWNERSHIP, GPS }
enum class OwnershipEventType { PURCHASE, SALE, TRANSFER_IN, TRANSFER_OUT, OTHER }

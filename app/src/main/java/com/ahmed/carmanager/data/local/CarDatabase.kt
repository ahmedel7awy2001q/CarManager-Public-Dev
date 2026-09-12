package com.ahmed.carmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ahmed.carmanager.data.local.dao.*
import com.ahmed.carmanager.data.local.model.*

@Database(
    entities = [
        VehicleEntity::class,
        OwnershipRecordEntity::class,
        OdometerRecordEntity::class,
        MaintenancePlanEntity::class,
        MaintenanceRecordEntity::class,
        FuelRecordEntity::class,
        TripEntity::class,
        TripQuoteEntity::class,
        ExpenseEntity::class,
        GpsDeviceEntity::class,
        GpsCalibrationEntity::class,
        GpsReadingEntity::class,
        PartEntity::class,
        TireEntity::class,
        TireRotationEntity::class,
        BatteryRecordEntity::class,
        FaultRecordEntity::class,
        VehicleDocumentEntity::class,
        ReminderEntity::class,
        AttachmentEntity::class,
        MonthlySnapshotEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(CarTypeConverters::class)
abstract class CarDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun fuelDao(): FuelDao
    abstract fun tripDao(): TripDao
    abstract fun tripQuoteDao(): TripQuoteDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun odometerDao(): OdometerDao
    abstract fun gpsDao(): GpsDao
    abstract fun assetDao(): AssetDao
    abstract fun supportDao(): SupportDao

    companion object {
        const val DATABASE_NAME = "car_manager.db"
        const val SCHEMA_VERSION = 8

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing v0.2.x vehicles deliberately remain unclaimed until the user signs in
                // and explicitly links the local data to their Firebase account.
                db.execSQL("ALTER TABLE vehicles ADD COLUMN ownerUserId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_ownerUserId ON vehicles(ownerUserId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing data is a passenger-car dataset, so CAR is the safest non-destructive default.
                db.execSQL("ALTER TABLE vehicles ADD COLUMN vehicleType TEXT NOT NULL DEFAULT 'CAR'")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN customVehicleType TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Optional per-vehicle inspection-template overrides. Existing vehicles keep defaults.
                db.execSQL("ALTER TABLE vehicles ADD COLUMN inspectionTemplateConfig TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Vehicle technical identity + true-cost defaults. All additions are nullable or
                // have safe defaults so existing users keep every row without fabrication.
                db.execSQL("ALTER TABLE vehicles ADD COLUMN engineName TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN engineCode TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN generationCode TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN transmissionName TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN transmissionCode TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN annualLicenseCost REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN annualInsuranceCost REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN annualOtherFixedCost REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN annualDistanceKm REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN currentMarketValue REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN depreciationAnnualPercent REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN includeAnnualFixedCostsInTripCost INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN includeDepreciationInTripCost INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Optional total seat count (including the driver). Null keeps old vehicles honest
                // until the user or catalog confirms the legal seating capacity.
                db.execSQL("ALTER TABLE vehicles ADD COLUMN passengerCapacity INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Quotes are estimates, deliberately stored separately from executed trips so
                // creating or editing a quote never changes the odometer or trip history.
                db.execSQL("""CREATE TABLE IF NOT EXISTS `trip_quotes` (`id` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `startAddress` TEXT, `endAddress` TEXT, `startLatitude` REAL, `startLongitude` REAL, `endLatitude` REAL, `endLongitude` REAL, `routeProvider` TEXT, `oneWayDistanceKm` REAL NOT NULL, `roundTrip` INTEGER NOT NULL, `totalDistanceKm` REAL NOT NULL, `passengerCount` INTEGER NOT NULL, `totalSeatCapacity` INTEGER, `waitingHours` REAL NOT NULL, `waitingRatePerHour` REAL NOT NULL, `tolls` REAL NOT NULL, `driverExpense` REAL NOT NULL, `consumptionLitersPer100Km` REAL, `fuelPricePerLiter` REAL, `maintenancePerKm` REAL NOT NULL, `includeMaintenance` INTEGER NOT NULL, `annualFixedPerKm` REAL NOT NULL, `includeAnnualFixed` INTEGER NOT NULL, `depreciationPerKm` REAL NOT NULL, `includeDepreciation` INTEGER NOT NULL, `profitMarginPercent` REAL NOT NULL, `marketOffer` REAL, `estimatedFuelLiters` REAL, `fuelCost` REAL, `trueTripCost` REAL NOT NULL, `suggestedQuote` REAL NOT NULL, `perPassengerQuote` REAL, `readinessPercent` INTEGER NOT NULL, `notes` TEXT, `convertedTripId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE CASCADE ON DELETE RESTRICT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_quotes_vehicleId` ON `trip_quotes` (`vehicleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_quotes_vehicleId_createdAt` ON `trip_quotes` (`vehicleId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_quotes_convertedTripId` ON `trip_quotes` (`convertedTripId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // GPS readings can become the largest table in normal use. These are additive
                // indexes only: no rows are rewritten, removed or recreated.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_devices_vehicleId_provider_updatedAt` ON `gps_devices` (`vehicleId`, `provider`, `updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_devices_vehicleId_deviceIdentifier_updatedAt` ON `gps_devices` (`vehicleId`, `deviceIdentifier`, `updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_calibrations_vehicleId_gpsDeviceId_calibrationDate` ON `gps_calibrations` (`vehicleId`, `gpsDeviceId`, `calibrationDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_readings_vehicleId_timestamp` ON `gps_readings` (`vehicleId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_readings_vehicleId_gpsDeviceId_timestamp` ON `gps_readings` (`vehicleId`, `gpsDeviceId`, `timestamp`)")
            }
        }

        @Volatile private var INSTANCE: CarDatabase? = null

        fun getInstance(context: Context): CarDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                CarDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8
                )
                .build()
                .also { INSTANCE = it }
        }
    }
}

package com.ahmed.carmanager.data.cloud

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface CloudDataResult {
    data class Success(val messageAr: String, val changed: Boolean = true) : CloudDataResult
    data class Error(val messageAr: String, val cause: Throwable? = null) : CloudDataResult
}

/**
 * Account-scoped portable cloud snapshot.
 * Room remains the offline source of truth; Firebase Storage stores the latest complete snapshot
 * for this UID, while Firestore only stores light sync metadata.
 */
class CloudAccountBackupManager(
    private val database: CarDatabase,
    private val authRepository: AuthRepository,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val schemaVersion = CarDatabase.SCHEMA_VERSION
    private val maxDownloadBytes = 25L * 1024L * 1024L
    private val tables = listOf(
        "vehicles",
        "ownership_records",
        "odometer_records",
        "maintenance_plans",
        "maintenance_records",
        "fuel_records",
        "trips",
        "trip_quotes",
        "expenses",
        "gps_devices",
        "gps_calibrations",
        "gps_readings",
        "parts",
        "tires",
        "tire_rotations",
        "battery_records",
        "fault_records",
        "vehicle_documents",
        "reminders",
        "attachments",
        "monthly_snapshots"
    )

    suspend fun uploadLatest(): CloudDataResult = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUid() ?: return@withContext CloudDataResult.Error("لا يوجد حساب مسجل للدخول.")
        runCatching {
            val snapshot = buildSnapshot(uid)
            val bytes = snapshot.toString().toByteArray(Charsets.UTF_8)
            storage.reference.child("users/$uid/backups/latest.json").putBytes(bytes).await()
            runCatching {
                firestore.collection("users").document(uid).collection("meta").document("cloud")
                    .set(
                        mapOf(
                            "lastBackupAt" to FieldValue.serverTimestamp(),
                            "databaseSchemaVersion" to schemaVersion,
                            "sizeBytes" to bytes.size,
                            "vehicleCount" to database.vehicleDao().countVisible(uid)
                        ),
                        SetOptions.merge()
                    ).await()
            }
            CloudDataResult.Success("تم حفظ بيانات الحساب على السحابة.")
        }.getOrElse { CloudDataResult.Error("تعذر رفع النسخة السحابية الآن. ستظل بيانات الهاتف محفوظة محليًا.", it) }
    }

    suspend fun restoreIfLocalEmpty(): CloudDataResult = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUid() ?: return@withContext CloudDataResult.Error("لا يوجد حساب مسجل للدخول.")
        if (database.vehicleDao().countVisible(uid) > 0) return@withContext CloudDataResult.Success("البيانات المحلية موجودة بالفعل.", changed = false)
        restoreLatestInternal(uid, silentMissing = true)
    }

    suspend fun restoreLatest(): CloudDataResult = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUid() ?: return@withContext CloudDataResult.Error("لا يوجد حساب مسجل للدخول.")
        restoreLatestInternal(uid, silentMissing = false)
    }

    private suspend fun restoreLatestInternal(uid: String, silentMissing: Boolean): CloudDataResult = try {
        val bytes = storage.reference.child("users/$uid/backups/latest.json").getBytes(maxDownloadBytes).await()
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "CarManagerCloudBackup") { "صيغة النسخة السحابية غير مدعومة." }
        require(root.optInt("formatVersion", -1) == 1) { "إصدار تنسيق النسخة السحابية غير مدعوم." }

        val backupSchemaVersion = root.optInt("databaseSchemaVersion", -1)
        require(backupSchemaVersion in 1..schemaVersion) { "النسخة السحابية أحدث من إصدار التطبيق الحالي أو غير صالحة." }

        val backupOwner = root.optString("ownerUserId").trim()
        require(backupOwner.isBlank() || backupOwner == uid) { "النسخة السحابية تخص حسابًا آخر." }

        val data = root.optJSONObject("tables") ?: error("النسخة السحابية لا تحتوي جداول بيانات.")
        val incoming = tables.associateWith { data.optJSONArray(it) ?: JSONArray() }
        validateIncomingSnapshot(uid, incoming)
        val safetyCopyCreated = createPreRestoreSafetyCopy(uid)

        // No local row is deleted until the complete downloaded snapshot has passed validation.
        // Room's transaction guarantees rollback if any insert fails.
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            val currentVehicleIds = queryOwnedVehicleIds(uid)
            for (table in tables.asReversed()) {
                if (table == "vehicles") continue
                if (!hasColumn(table, "vehicleId")) continue
                currentVehicleIds.forEach { vehicleId ->
                    db.execSQL("DELETE FROM `$table` WHERE vehicleId = ?", arrayOf(vehicleId))
                }
            }
            db.execSQL("DELETE FROM vehicles WHERE ownerUserId = ?", arrayOf(uid))

            for (table in tables) {
                val rows = incoming.getValue(table)
                if (table != "vehicles" && !hasColumn(table, "vehicleId")) continue
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    if (table == "vehicles") forceOwner(row, uid)
                    insertRow(db, table, row)
                }
            }
        }
        CloudDataResult.Success(if (safetyCopyCreated) "تمت الاستعادة بنجاح، وتم حفظ نسخة أمان من بيانات الهاتف قبل الاستعادة." else "تمت استعادة مركباتك وبياناتها من حسابك السحابي.")
    } catch (t: Throwable) {
        val missing = t.message.orEmpty().contains("Object does not exist", ignoreCase = true) ||
            t.message.orEmpty().contains("404")
        if (silentMissing && missing) {
            CloudDataResult.Success("لا توجد نسخة سحابية سابقة لهذا الحساب.", changed = false)
        } else {
            CloudDataResult.Error("تعذر استعادة النسخة السحابية بأمان؛ لم يتم تغيير البيانات المحلية.", t)
        }
    }

    private suspend fun createPreRestoreSafetyCopy(uid: String): Boolean {
        if (database.vehicleDao().countVisible(uid) <= 0) return false
        val snapshot = buildSnapshot(uid)
        val bytes = snapshot.toString().toByteArray(Charsets.UTF_8)
        val stamp = System.currentTimeMillis()
        storage.reference.child("users/$uid/backups/safety/pre-restore-$stamp.json")
            .putBytes(bytes)
            .await()
        return true
    }

    private fun validateIncomingSnapshot(uid: String, incoming: Map<String, JSONArray>) {
        val vehicles = incoming.getValue("vehicles")
        require(vehicles.length() > 0) { "النسخة السحابية لا تحتوي أي مركبة؛ لن نستبدل البيانات المحلية بنسخة فارغة." }

        val vehicleIds = linkedSetOf<String>()
        for (i in 0 until vehicles.length()) {
            val row = vehicles.getJSONObject(i)
            validateEncodedRow(row)
            val vehicleId = encodedString(row, "vehicleId")?.trim().orEmpty()
            require(vehicleId.isNotBlank()) { "مركبة بلا معرف في النسخة السحابية." }
            require(vehicleIds.add(vehicleId)) { "معرف مركبة مكرر في النسخة السحابية." }
            val owner = encodedString(row, "ownerUserId")?.trim()
            require(owner.isNullOrBlank() || owner == uid) { "توجد مركبة في النسخة تخص حسابًا آخر." }
        }

        incoming.forEach { (table, rows) ->
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                validateEncodedRow(row)
                if (table == "vehicles") continue
                val vehicleId = encodedString(row, "vehicleId")?.trim()
                if (!vehicleId.isNullOrBlank()) {
                    require(vehicleId in vehicleIds) {
                        "الجدول $table يحتوي سجلًا مرتبطًا بمركبة غير موجودة في النسخة."
                    }
                }
            }
        }
    }

    private fun validateEncodedRow(row: JSONObject) {
        val keys = row.keys()
        while (keys.hasNext()) {
            val column = keys.next()
            val cell = row.optJSONObject(column) ?: error("تنسيق الحقل $column غير صالح.")
            require(cell.has("t") && cell.has("v")) { "الحقل $column ناقص في النسخة السحابية." }
            require(cell.optInt("t", -1) in Cursor.FIELD_TYPE_NULL..Cursor.FIELD_TYPE_BLOB) {
                "نوع الحقل $column غير صالح في النسخة السحابية."
            }
        }
    }

    private fun encodedString(row: JSONObject, column: String): String? {
        val cell = row.optJSONObject(column) ?: return null
        if (cell.optInt("t", Cursor.FIELD_TYPE_NULL) == Cursor.FIELD_TYPE_NULL || cell.isNull("v")) return null
        return cell.opt("v")?.toString()
    }

    private fun buildSnapshot(uid: String): JSONObject {
        val root = JSONObject()
        root.put("format", "CarManagerCloudBackup")
        root.put("formatVersion", 1)
        root.put("databaseSchemaVersion", schemaVersion)
        root.put("ownerUserId", uid)
        root.put("createdAt", System.currentTimeMillis())
        val data = JSONObject()
        val db = database.openHelper.readableDatabase
        for (table in tables) {
            val cursor = when {
                table == "vehicles" -> db.query("SELECT * FROM vehicles WHERE ownerUserId = ?", arrayOf(uid))
                hasColumn(table, "vehicleId") -> db.query(
                    "SELECT * FROM `$table` WHERE vehicleId IN (SELECT vehicleId FROM vehicles WHERE ownerUserId = ?)",
                    arrayOf(uid)
                )
                else -> null
            }
            data.put(table, cursor?.let(::exportTable) ?: JSONArray())
        }
        root.put("tables", data)
        return root
    }

    private fun queryOwnedVehicleIds(uid: String): List<String> {
        val result = mutableListOf<String>()
        database.openHelper.readableDatabase.query("SELECT vehicleId FROM vehicles WHERE ownerUserId = ?", arrayOf(uid)).use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0)
        }
        return result
    }

    private fun hasColumn(table: String, column: String): Boolean {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return true
        }
        return false
    }

    private fun forceOwner(row: JSONObject, uid: String) {
        val cell = JSONObject().put("t", Cursor.FIELD_TYPE_STRING).put("v", uid)
        row.put("ownerUserId", cell)
    }

    private fun exportTable(cursor: Cursor): JSONArray {
        val rows = JSONArray()
        cursor.use {
            while (it.moveToNext()) {
                val row = JSONObject()
                for (index in 0 until it.columnCount) {
                    val cell = JSONObject()
                    val type = it.getType(index)
                    cell.put("t", type)
                    when (type) {
                        Cursor.FIELD_TYPE_NULL -> cell.put("v", JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> cell.put("v", it.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> cell.put("v", it.getDouble(index))
                        Cursor.FIELD_TYPE_STRING -> cell.put("v", it.getString(index))
                        Cursor.FIELD_TYPE_BLOB -> cell.put("v", Base64.encodeToString(it.getBlob(index), Base64.NO_WRAP))
                    }
                    row.put(it.getColumnName(index), cell)
                }
                rows.put(row)
            }
        }
        return rows
    }

    private fun insertRow(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, row: JSONObject) {
        val values = ContentValues()
        val keys = row.keys()
        while (keys.hasNext()) {
            val column = keys.next()
            val cell = row.getJSONObject(column)
            when (cell.getInt("t")) {
                Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                Cursor.FIELD_TYPE_INTEGER -> values.put(column, cell.getLong("v"))
                Cursor.FIELD_TYPE_FLOAT -> values.put(column, cell.getDouble("v"))
                Cursor.FIELD_TYPE_STRING -> values.put(column, cell.getString("v"))
                Cursor.FIELD_TYPE_BLOB -> values.put(column, Base64.decode(cell.getString("v"), Base64.NO_WRAP))
                else -> error("نوع بيانات غير معروف في النسخة السحابية.")
            }
        }
        if (db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) == -1L) error("فشل استعادة جدول $table")
    }
}

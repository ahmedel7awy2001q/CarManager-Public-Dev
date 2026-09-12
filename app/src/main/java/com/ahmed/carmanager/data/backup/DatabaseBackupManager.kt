package com.ahmed.carmanager.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface BackupResult {
    data class Success(val messageAr: String) : BackupResult
    data class Error(val messageAr: String, val cause: Throwable? = null) : BackupResult
}

/** Portable account-scoped backup for all Room tables. */
class DatabaseBackupManager(
    private val database: CarDatabase,
    private val authRepository: AuthRepository
) {
    private val schemaVersion = CarDatabase.SCHEMA_VERSION
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

    suspend fun export(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUid() ?: return@withContext BackupResult.Error("سجّل الدخول أولًا لإنشاء نسخة خاصة بحسابك.")
        runCatching {
            val root = JSONObject()
            root.put("format", "CarManagerBackup")
            root.put("formatVersion", 2)
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
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("تعذر فتح ملف النسخة الاحتياطية.")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString()) }
            BackupResult.Success("تم إنشاء نسخة احتياطية خاصة بحسابك.")
        }.getOrElse { BackupResult.Error("تعذر إنشاء النسخة الاحتياطية.", it) }
    }

    suspend fun import(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUid() ?: return@withContext BackupResult.Error("سجّل الدخول أولًا لاستعادة البيانات إلى حسابك.")
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("تعذر قراءة ملف النسخة الاحتياطية.")
            val root = JSONObject(text)
            require(root.optString("format") == "CarManagerBackup") { "صيغة الملف غير مدعومة." }
            val sourceSchema = root.optInt("databaseSchemaVersion", 1)
            require(sourceSchema in 1..schemaVersion) { "نسخة قاعدة البيانات غير متوافقة مع هذا الإصدار." }
            val data = root.getJSONObject("tables")
            require(data.has("vehicles")) { "النسخة الاحتياطية لا تحتوي على بيانات المركبات." }
            val parsed = tables.associateWith { table -> data.optJSONArray(table) ?: JSONArray() }

            database.withTransaction {
                val db = database.openHelper.writableDatabase
                val currentIds = ownedVehicleIds(uid)
                tables.asReversed().forEach { table ->
                    if (table == "vehicles" || !hasColumn(table, "vehicleId")) return@forEach
                    currentIds.forEach { id -> db.execSQL("DELETE FROM `$table` WHERE vehicleId = ?", arrayOf(id)) }
                }
                db.execSQL("DELETE FROM vehicles WHERE ownerUserId = ?", arrayOf(uid))

                tables.forEach { table ->
                    if (table != "vehicles" && !hasColumn(table, "vehicleId")) return@forEach
                    val rows = parsed.getValue(table)
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        if (table == "vehicles") forceOwner(row, uid)
                        insertRow(db, table, row)
                    }
                }
            }
            BackupResult.Success("تمت استعادة النسخة إلى حسابك دون التأثير على الحسابات الأخرى.")
        }.getOrElse { BackupResult.Error(it.message ?: "تعذر استعادة النسخة الاحتياطية.", it) }
    }

    private fun ownedVehicleIds(uid: String): List<String> {
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
        row.put("ownerUserId", JSONObject().put("t", Cursor.FIELD_TYPE_STRING).put("v", uid))
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
            if (table == "vehicles" && column == "ownerUserId" && !row.has(column)) continue
            val cell = row.getJSONObject(column)
            when (cell.getInt("t")) {
                Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                Cursor.FIELD_TYPE_INTEGER -> values.put(column, cell.getLong("v"))
                Cursor.FIELD_TYPE_FLOAT -> values.put(column, cell.getDouble("v"))
                Cursor.FIELD_TYPE_STRING -> values.put(column, cell.getString("v"))
                Cursor.FIELD_TYPE_BLOB -> values.put(column, Base64.decode(cell.getString("v"), Base64.NO_WRAP))
                else -> error("نوع بيانات غير معروف داخل النسخة الاحتياطية.")
            }
        }
        if (db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values) == -1L) error("فشل استعادة جدول $table")
    }
}

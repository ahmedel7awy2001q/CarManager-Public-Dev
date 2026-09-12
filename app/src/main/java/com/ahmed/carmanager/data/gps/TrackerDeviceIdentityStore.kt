package com.ahmed.carmanager.data.gps

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.ahmed.carmanager.data.local.model.GpsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

enum class TrackerDeviceRole {
    PHONE,
    HEAD_UNIT
}

data class TrackerDeviceIdentity(
    val deviceIdentifier: String,
    val displayName: String,
    val role: TrackerDeviceRole,
    val provider: GpsProvider
)

class TrackerDeviceIdentityStore(
    context: Context
) {
    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _role = MutableStateFlow(
        readSavedRole()
    )

    val roleFlow: StateFlow<TrackerDeviceRole> =
        _role.asStateFlow()

    fun role(): TrackerDeviceRole =
        _role.value

    fun setRole(role: TrackerDeviceRole) {
        if (_role.value == role) return

        prefs.edit()
            .putString(KEY_DEVICE_ROLE, role.name)
            .apply()

        _role.value = role
    }

    private fun readSavedRole(): TrackerDeviceRole {
        val saved = prefs.getString(
            KEY_DEVICE_ROLE,
            null
        )

        return runCatching {
            saved?.let(TrackerDeviceRole::valueOf)
        }.getOrNull() ?: TrackerDeviceRole.PHONE
    }

    fun identity(): TrackerDeviceIdentity {
        val role = role()

        return TrackerDeviceIdentity(
            deviceIdentifier = buildDeviceIdentifier(),
            displayName = buildDisplayName(),
            role = role,
            provider = when (role) {
                TrackerDeviceRole.PHONE -> GpsProvider.PHONE
                TrackerDeviceRole.HEAD_UNIT -> GpsProvider.HEAD_UNIT
            }
        )
    }

    private fun buildDeviceIdentifier(): String {
        val source = androidId()
            ?: installationId()

        val fingerprint = sha256(source)
            .take(24)

        return "tracker-$fingerprint"
    }

    private fun buildDisplayName(): String {
        val manufacturer = Build.MANUFACTURER
            ?.trim()
            .orEmpty()

        val model = Build.MODEL
            ?.trim()
            .orEmpty()

        val name = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(" ")
            .trim()

        return name.ifBlank { "Android Device" }
    }

    private fun androidId(): String? {
        return runCatching {
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        }.getOrNull()
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    it != INVALID_ANDROID_ID
            }
    }

    private fun installationId(): String {
        prefs.getString(
            KEY_INSTALLATION_ID,
            null
        )
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val generated = UUID.randomUUID()
            .toString()

        prefs.edit()
            .putString(
                KEY_INSTALLATION_ID,
                generated
            )
            .apply()

        return generated
    }

    private fun sha256(value: String): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString("") { byte ->
                "%02x".format(
                    byte.toInt() and 0xff
                )
            }
    }

    private companion object {
        const val PREFS_NAME =
            "carmanager_tracker_device"

        const val KEY_INSTALLATION_ID =
            "installation_id"

        const val KEY_DEVICE_ROLE =
            "device_role"

        const val INVALID_ANDROID_ID =
            "9774d56d682e549c"
    }
}




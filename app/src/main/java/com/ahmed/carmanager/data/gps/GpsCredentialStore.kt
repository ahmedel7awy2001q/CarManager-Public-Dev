package com.ahmed.carmanager.data.gps

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ahmed.carmanager.data.local.model.GpsProvider
import com.google.firebase.auth.FirebaseAuth
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GpsCredentials(val account: String, val password: String)

/** Stores GPS credentials encrypted by Android Keystore and isolated by Firebase UID. */
class GpsCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("gps_secure_credentials", Context.MODE_PRIVATE)
    private val keyAlias = "carmanager_gps_credentials_v1"

    fun save(ownerUserId: String, provider: GpsProvider, account: String, password: String) {
        require(ownerUserId.isNotBlank())
        require(provider == GpsProvider.ITRACK || provider == GpsProvider.ETRACK) { "Unsupported provider" }
        require(account.isNotBlank() && password.isNotBlank())
        val plain = "${account.trim()}\u0000$password".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(storageKey(ownerUserId, provider), payload).apply()
    }

    fun get(ownerUserId: String, provider: GpsProvider): GpsCredentials? {
        if (ownerUserId.isBlank()) return null
        val payload = prefs.getString(storageKey(ownerUserId, provider), null) ?: return null
        return decrypt(payload)
    }

    fun has(ownerUserId: String, provider: GpsProvider): Boolean = get(ownerUserId, provider) != null
    fun remove(ownerUserId: String, provider: GpsProvider) { prefs.edit().remove(storageKey(ownerUserId, provider)).apply() }

    /** Account-aware compatibility helpers used by UI code. */
    fun save(provider: GpsProvider, account: String, password: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("No signed-in Firebase user")
        migrateLegacyTo(uid, provider)
        save(uid, provider, account, password)
    }

    fun get(provider: GpsProvider): GpsCredentials? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        migrateLegacyTo(uid, provider)
        return get(uid, provider)
    }

    fun has(provider: GpsProvider): Boolean = get(provider) != null
    fun remove(provider: GpsProvider) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        remove(uid, provider)
    }

    /** Moves a pre-account credential into the first signed-in account once, then deletes the legacy key. */
    fun migrateLegacyTo(ownerUserId: String, provider: GpsProvider): Boolean {
        if (has(ownerUserId, provider)) return false
        val legacyPayload = prefs.getString(provider.name, null) ?: return false
        val credentials = decrypt(legacyPayload) ?: return false
        save(ownerUserId, provider, credentials.account, credentials.password)
        prefs.edit().remove(provider.name).apply()
        return true
    }

    private fun storageKey(ownerUserId: String, provider: GpsProvider) = "u:${ownerUserId}:${provider.name}"

    private fun decrypt(payload: String): GpsCredentials? = runCatching {
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return@runCatching null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        val separator = plain.indexOf('\u0000')
        if (separator <= 0) null else GpsCredentials(plain.substring(0, separator), plain.substring(separator + 1))
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

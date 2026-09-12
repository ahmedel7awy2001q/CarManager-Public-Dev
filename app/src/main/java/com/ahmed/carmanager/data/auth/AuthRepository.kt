package com.ahmed.carmanager.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class AccountUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val emailVerified: Boolean
)

sealed interface AuthResult {
    data class Success(val user: AccountUser) : AuthResult
    data class Error(val messageAr: String, val cause: Throwable? = null) : AuthResult
}

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val _user = MutableStateFlow(auth.currentUser?.toAccountUser())
    val user: StateFlow<AccountUser?> = _user.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _user.value = firebaseAuth.currentUser?.toAccountUser()
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    fun currentUser(): AccountUser? = auth.currentUser?.toAccountUser()
    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun signInWithEmail(email: String, password: String): AuthResult = runAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        requireNotNull(auth.currentUser)
    }

    suspend fun createAccount(email: String, password: String): AuthResult = runAuth {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        auth.currentUser?.sendEmailVerification()?.await()
        requireNotNull(auth.currentUser)
    }

    suspend fun signInWithGoogle(idToken: String): AuthResult = runAuth {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        requireNotNull(auth.currentUser)
    }

    suspend fun updateDisplayName(displayName: String): AuthResult = runAuth {
        val current = requireNotNull(auth.currentUser)
        val cleanName = displayName.trim().takeIf { it.isNotEmpty() }
        current.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(cleanName)
                .build()
        ).await()
        current.reload().await()
        requireNotNull(auth.currentUser)
    }

    suspend fun sendEmailVerification(): Result<Unit> = runCatching {
        val current = requireNotNull(auth.currentUser)
        if (!current.isEmailVerified) current.sendEmailVerification().await()
        Unit
    }

    suspend fun refreshCurrentUser(): Result<AccountUser> = runCatching {
        val current = requireNotNull(auth.currentUser)
        current.reload().await()
        val refreshed = requireNotNull(auth.currentUser).toAccountUser()
        _user.value = refreshed
        refreshed
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }

    private suspend fun runAuth(block: suspend () -> com.google.firebase.auth.FirebaseUser): AuthResult = try {
        val firebaseUser = block()
        runCatching { ensureProfile(firebaseUser) }
        val accountUser = firebaseUser.toAccountUser()
        _user.value = accountUser
        AuthResult.Success(accountUser)
    } catch (t: Throwable) {
        AuthResult.Error(t.toArabicAuthMessage(), t)
    }

    private suspend fun ensureProfile(user: com.google.firebase.auth.FirebaseUser) {
        val profile = mapOf(
            "uid" to user.uid,
            "email" to user.email,
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl?.toString(),
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "app" to "CarManager"
        )
        firestore.collection("users").document(user.uid)
            .set(profile, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }
}

private fun com.google.firebase.auth.FirebaseUser.toAccountUser() = AccountUser(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    emailVerified = isEmailVerified
)

private fun Throwable.toArabicAuthMessage(): String {
    val message = message.orEmpty().lowercase()
    return when {
        "password" in message && "invalid" in message -> "كلمة المرور غير صحيحة."
        "credential" in message || "invalid-login" in message -> "بيانات تسجيل الدخول غير صحيحة."
        "email" in message && "already" in message -> "هذا البريد مرتبط بحساب موجود بالفعل."
        "network" in message -> "تعذر الاتصال بالإنترنت. تحقق من الشبكة وحاول مرة أخرى."
        "too many" in message -> "تمت محاولات كثيرة. انتظر قليلًا ثم حاول مرة أخرى."
        else -> "تعذر إكمال العملية على الحساب الآن. حاول مرة أخرى."
    }
}

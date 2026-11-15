package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleUserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

sealed interface GoogleAuthUiState {
    data object SignedOut : GoogleAuthUiState
    data object SigningIn : GoogleAuthUiState
    data class SignedIn(val user: GoogleUserProfile) : GoogleAuthUiState
    data class Error(val message: String) : GoogleAuthUiState
}

sealed interface DriveAuthorizationOutcome {
    data class Authorized(val accessToken: String) : DriveAuthorizationOutcome
    data class ResolutionRequired(val intentSender: IntentSender) : DriveAuthorizationOutcome
    data class Failed(val message: String, val isAuthRequired: Boolean = true) : DriveAuthorizationOutcome
}

interface CloudAuthProvider {
    fun currentUser(): GoogleUserProfile?
    suspend fun authorizeDriveAppData(
        context: Context,
        interactiveAllowed: Boolean
    ): DriveAuthorizationOutcome
}

class GoogleAuthManager(private val appContext: Context) : CloudAuthProvider {

    companion object {
        private const val TAG = "GoogleAuthManager"
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }

    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager: CredentialManager = CredentialManager.create(appContext)

    private val _authState = MutableStateFlow<GoogleAuthUiState>(
        auth.currentUser?.toProfile()?.let { GoogleAuthUiState.SignedIn(it) } ?: GoogleAuthUiState.SignedOut
    )
    val authState: StateFlow<GoogleAuthUiState> = _authState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser?.toProfile()
        _authState.value = if (user != null) {
            GoogleAuthUiState.SignedIn(user)
        } else {
            GoogleAuthUiState.SignedOut
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun dispose() {
        auth.removeAuthStateListener(authListener)
    }

    override fun currentUser(): GoogleUserProfile? = auth.currentUser?.toProfile()

    private fun resolveWebClientId(): String? {
        return try {
            val id = appContext.getString(R.string.default_web_client_id)
            id.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "default_web_client_id resource missing", e)
            null
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
        )
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Silent auto sign-in on startup when an authorized Google account exists on device.
     * Uses GetGoogleIdOption in a single-option GetCredentialRequest.
     */
    suspend fun attemptSilentSignIn(activityContext: Context): Boolean {
        if (auth.currentUser != null) {
            _authState.value = GoogleAuthUiState.SignedIn(auth.currentUser!!.toProfile())
            return true
        }
        val clientId = resolveWebClientId() ?: return false

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val targetCtx = findActivity(activityContext) ?: activityContext
            val result = credentialManager.getCredential(targetCtx, request)
            handleCredentialResponse(result.credential)
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No previously authorized Google account for silent sign-in")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In unavailable: ${e.message}")
            false
        }
    }

    /**
     * Interactive Continue / Sign in with Google flow via Jetpack Credential Manager.
     * Uses GetSignInWithGoogleOption exclusively (one option per request).
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<GoogleUserProfile> {
        val clientId = resolveWebClientId()
            ?: return Result.failure(IllegalStateException("Google Sign-In is not configured (default_web_client_id missing)."))

        _authState.value = GoogleAuthUiState.SigningIn

        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .setNonce(generateNonce())
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val targetCtx = findActivity(activityContext) ?: activityContext
            val result = credentialManager.getCredential(targetCtx, request)
            val signedIn = handleCredentialResponse(result.credential)
            val profile = auth.currentUser?.toProfile()
            if (signedIn && profile != null) {
                _authState.value = GoogleAuthUiState.SignedIn(profile)
                Result.success(profile)
            } else {
                val msg = "Unable to complete Google authentication."
                _authState.value = GoogleAuthUiState.Error(msg)
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            val existing = auth.currentUser?.toProfile()
            _authState.value = if (existing != null) GoogleAuthUiState.SignedIn(existing) else GoogleAuthUiState.SignedOut
            Result.failure(AuthCancelledException("Sign-in cancelled"))
        } catch (e: NoCredentialException) {
            val msg = "No Google account found on this device. Add a Google account in system Settings to continue."
            Log.w(TAG, msg, e)
            _authState.value = GoogleAuthUiState.Error(msg)
            Result.failure(IllegalStateException(msg))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Google Sign-In failed."
            Log.e(TAG, "Google Sign-In error: $msg", e)
            _authState.value = GoogleAuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Clears current credential state and opens account chooser for switching accounts.
     */
    suspend fun switchAccount(activityContext: Context): Result<GoogleUserProfile> {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Credential state clear before switch failed: ${e.message}")
            }
            auth.signOut()
        }
        return signInWithGoogle(activityContext)
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear Credential Manager state: ${e.message}")
            } finally {
                auth.signOut()
                _authState.value = GoogleAuthUiState.SignedOut
            }
        }
    }

    private suspend fun handleCredentialResponse(credential: androidx.credentials.Credential): Boolean {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            auth.signInWithCredential(authCredential).awaitTask()
            return auth.currentUser != null
        }
        return false
    }

    /**
     * Requests least-privilege Google Drive AppDataFolder authorization via Google Identity AuthorizationClient.
     * - When already consented on this device, silently mints a fresh short-lived OAuth 2.0 access token.
     * - When consent is required, returns ResolutionRequired(intentSender) so UI launches system consent sheet.
     */
    override suspend fun authorizeDriveAppData(
        context: Context,
        interactiveAllowed: Boolean
    ): DriveAuthorizationOutcome {
        val currentUser = auth.currentUser
            ?: return DriveAuthorizationOutcome.Failed("Sign in with Google first to authorize Google Drive.", isAuthRequired = true)

        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_APPDATA)))
                .build()

            val client = Identity.getAuthorizationClient(findActivity(context) ?: context)
            val authResult = client.authorize(request).awaitTask()

            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (interactiveAllowed && pendingIntent != null) {
                    DriveAuthorizationOutcome.ResolutionRequired(pendingIntent.intentSender)
                } else {
                    DriveAuthorizationOutcome.Failed(
                        "Google Drive permission required. Open Backups to grant Drive AppData access.",
                        isAuthRequired = true
                    )
                }
            } else {
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    DriveAuthorizationOutcome.Authorized(token)
                } else {
                    DriveAuthorizationOutcome.Failed("Google Drive did not return an access token.", isAuthRequired = true)
                }
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Drive authorization ApiException status=${e.statusCode}", e)
            DriveAuthorizationOutcome.Failed(
                e.localizedMessage ?: "Google Drive authorization error (${e.statusCode})",
                isAuthRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization failed: ${e.message}", e)
            DriveAuthorizationOutcome.Failed(e.localizedMessage ?: "Failed to authorize Google Drive.", isAuthRequired = false)
        }
    }

    fun extractTokenFromIntentResult(context: Context, data: Intent?): Result<String> {
        return try {
            val result = Identity.getAuthorizationClient(findActivity(context) ?: context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Google Drive authorization was not completed."))
            }
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FirebaseUser.toProfile(): GoogleUserProfile {
        return GoogleUserProfile(
            uid = uid,
            email = email.orEmpty(),
            displayName = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "Google User",
            photoUrl = photoUrl?.toString()
        )
    }
}

class AuthCancelledException(message: String) : Exception(message)

suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

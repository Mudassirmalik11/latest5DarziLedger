package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AuthUser(
  val uid: String,
  val displayName: String,
  val email: String,
)

sealed interface GoogleSignInResult {
  data class Success(val user: AuthUser) : GoogleSignInResult
  data object Cancelled : GoogleSignInResult
  data class Error(val message: String) : GoogleSignInResult
}

sealed interface EmailAuthResult {
  data class Success(val user: AuthUser) : EmailAuthResult
  data class Error(val message: String) : EmailAuthResult
}

sealed interface CreateStaffAccountResult {
  data class Success(val user: AuthUser) : CreateStaffAccountResult
  data class Error(val message: String) : CreateStaffAccountResult
}

interface AuthSessionProvider {
  val currentUser: AuthUser?
  val authStateFlow: Flow<AuthUser?>

  suspend fun attemptAutoSignIn(context: Context): AuthUser?
  suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult
  suspend fun signInWithEmailPassword(email: String, password: String): EmailAuthResult
  suspend fun signUpWithEmailPassword(
    displayName: String,
    email: String,
    password: String,
  ): EmailAuthResult
  suspend fun createStaffAccount(
    displayName: String,
    email: String,
    password: String,
    persistMembership: suspend (AuthUser) -> Result<Unit>,
  ): CreateStaffAccountResult
  suspend fun sendPasswordResetEmail(email: String): Result<Unit>
  suspend fun signOut(context: Context)
}

class FirebaseAuthSessionProvider(
  private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthSessionProvider {

  override val currentUser: AuthUser?
    get() = firebaseAuth.currentUser?.toAuthUser()

  override val authStateFlow: Flow<AuthUser?> = callbackFlow {
    trySend(firebaseAuth.currentUser?.toAuthUser())
    val listener = FirebaseAuth.AuthStateListener { auth ->
      trySend(auth.currentUser?.toAuthUser())
    }
    firebaseAuth.addAuthStateListener(listener)
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
  }

  override suspend fun attemptAutoSignIn(context: Context): AuthUser? {
    val existing = firebaseAuth.currentUser?.toAuthUser()
    if (existing != null) return existing

    val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
      ?: return null

    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    return try {
      val result = credentialManager.getCredential(context, request)
      val cred = result.credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        val authResult = firebaseAuth
          .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
          .await()
        authResult.user?.toAuthUser()
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
    val clientId = try {
      activityContext.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
      return GoogleSignInResult.Error(
        "Google Sign-In configuration missing: default_web_client_id not found.",
      )
    }

    val activity = activityContext as? Activity
      ?: return GoogleSignInResult.Error("Sign-In requires an active screen context.")

    val credentialManager = CredentialManager.create(activityContext)
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(signInOption)
      .build()

    return try {
      val cred = credentialManager.getCredential(activity, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        val authResult = firebaseAuth
          .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
          .await()
        val user = authResult.user?.toAuthUser()
        if (user != null) {
          GoogleSignInResult.Success(user)
        } else {
          GoogleSignInResult.Error("Sign-in succeeded but no user profile was returned.")
        }
      } else {
        GoogleSignInResult.Error("Unexpected credential type returned by Google Sign-In.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      GoogleSignInResult.Cancelled
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Google Sign-In failed", e)
      GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed. Check network and retry.")
    }
  }

  override suspend fun signInWithEmailPassword(email: String, password: String): EmailAuthResult {
    val trimmedEmail = email.trim()
    if (!isValidEmail(trimmedEmail)) {
      return EmailAuthResult.Error("Enter a valid email address.")
    }
    if (password.isEmpty()) {
      return EmailAuthResult.Error("Enter your password.")
    }

    return try {
      val authResult = firebaseAuth
        .signInWithEmailAndPassword(trimmedEmail, password)
        .await()
      val user = authResult.user?.toAuthUser()
      if (user != null) {
        EmailAuthResult.Success(user)
      } else {
        EmailAuthResult.Error("Sign-in succeeded but no user profile was returned.")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Email sign-in failed", e)
      EmailAuthResult.Error(mapFirebaseEmailAuthError(e, isSignUp = false))
    }
  }

  override suspend fun signUpWithEmailPassword(
    displayName: String,
    email: String,
    password: String,
  ): EmailAuthResult {
    val trimmedName = displayName.trim()
    val trimmedEmail = email.trim()
    if (!isValidEmail(trimmedEmail)) {
      return EmailAuthResult.Error("Enter a valid email address.")
    }
    if (password.length < 6) {
      return EmailAuthResult.Error("Password must be at least 6 characters.")
    }

    return try {
      val authResult = firebaseAuth
        .createUserWithEmailAndPassword(trimmedEmail, password)
        .await()
      val firebaseUser = authResult.user
        ?: return EmailAuthResult.Error("Account created but user session could not be loaded.")

      if (trimmedName.isNotEmpty()) {
        runCatching {
          val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(trimmedName)
            .build()
          firebaseUser.updateProfile(profileUpdates).await()
          firebaseUser.reload().await()
        }
      }

      val resolvedUser = (firebaseAuth.currentUser ?: firebaseUser).toAuthUser().let { user ->
        if (trimmedName.isNotEmpty() && user.displayName == "Shop Owner") {
          user.copy(displayName = trimmedName)
        } else {
          user
        }
      }
      EmailAuthResult.Success(resolvedUser)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Email sign-up failed", e)
      EmailAuthResult.Error(mapFirebaseEmailAuthError(e, isSignUp = true))
    }
  }

  override suspend fun createStaffAccount(
    displayName: String,
    email: String,
    password: String,
    persistMembership: suspend (AuthUser) -> Result<Unit>,
  ): CreateStaffAccountResult {
    val trimmedName = displayName.trim()
    val trimmedEmail = email.trim()
    if (trimmedName.isEmpty()) {
      return CreateStaffAccountResult.Error("Enter the user's name.")
    }
    if (!isValidEmail(trimmedEmail)) {
      return CreateStaffAccountResult.Error("Enter a valid unique email address.")
    }
    if (password.length < 6) {
      return CreateStaffAccountResult.Error("Password must be at least 6 characters.")
    }

    var secondaryAuth: FirebaseAuth? = null
    return try {
      val primaryApp = FirebaseApp.getInstance()
      val appContext = primaryApp.applicationContext
      val secondaryApp = FirebaseApp.getApps(appContext)
        .firstOrNull { it.name == STAFF_PROVISIONING_APP_NAME }
        ?: FirebaseApp.initializeApp(appContext, primaryApp.options, STAFF_PROVISIONING_APP_NAME)
      val staffAuth = FirebaseAuth.getInstance(secondaryApp)
      secondaryAuth = staffAuth

      val authResult = staffAuth
        .createUserWithEmailAndPassword(trimmedEmail, password)
        .await()
      val createdUser = authResult.user
        ?: return CreateStaffAccountResult.Error("Could not create user account.")

      runCatching {
        val profileUpdates = UserProfileChangeRequest.Builder()
          .setDisplayName(trimmedName)
          .build()
        createdUser.updateProfile(profileUpdates).await()
      }

      val staffAuthUser = AuthUser(
        uid = createdUser.uid,
        displayName = trimmedName,
        email = createdUser.email ?: trimmedEmail,
      )

      val persistResult = persistMembership(staffAuthUser)
      if (persistResult.isFailure) {
        withContext(NonCancellable) {
          runCatching { createdUser.delete().await() }
        }
        val err = persistResult.exceptionOrNull()
        return CreateStaffAccountResult.Error(
          err?.localizedMessage ?: "Created user login but failed to link to shop. Reverted cleanly.",
        )
      }

      CreateStaffAccountResult.Success(staffAuthUser)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Staff account creation failed", e)
      val errorCode = (e as? FirebaseAuthException)?.errorCode.orEmpty()
      val message = if (errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
        "This email is already in use. Choose a unique email address for this user."
      } else {
        mapFirebaseEmailAuthError(e, isSignUp = true)
      }
      CreateStaffAccountResult.Error(message)
    } finally {
      withContext(NonCancellable) {
        runCatching { secondaryAuth?.signOut() }
      }
    }
  }

  override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
    val trimmedEmail = email.trim()
    if (!isValidEmail(trimmedEmail)) {
      return Result.failure(IllegalArgumentException("Enter a valid email address to reset your password."))
    }
    return try {
      firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Password reset failed", e)
      Result.failure(IllegalStateException(mapFirebaseEmailAuthError(e, isSignUp = false), e))
    }
  }

  override suspend fun signOut(context: Context) {
    withContext(NonCancellable) {
      try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to clear credential state: ${e.message}")
      } finally {
        firebaseAuth.signOut()
      }
    }
  }

  private fun FirebaseUser.toAuthUser(): AuthUser {
    val resolvedName = displayName?.takeIf { it.isNotBlank() }
      ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
      ?: "Shop Owner"
    return AuthUser(
      uid = uid,
      displayName = resolvedName,
      email = email.orEmpty(),
    )
  }

  private fun mapFirebaseEmailAuthError(e: Exception, isSignUp: Boolean): String {
    val errorCode = (e as? FirebaseAuthException)?.errorCode.orEmpty()
    val rawMessage = e.localizedMessage.orEmpty()
    return when {
      errorCode == "ERROR_OPERATION_NOT_ALLOWED" ||
        rawMessage.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
        "Email/Password sign-in is not enabled in Firebase Console. Enable Authentication > Sign-in method > Email/Password, or use Google Sign-In."
      errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ->
        "An account with this email already exists. Switch to Sign In."
      errorCode == "ERROR_WEAK_PASSWORD" ->
        "Password is too weak. Use at least 6 characters."
      errorCode == "ERROR_INVALID_EMAIL" ->
        "Please enter a valid email address."
      errorCode == "ERROR_USER_NOT_FOUND" ->
        "No shop account found for this email. Switch to Sign Up to create one."
      errorCode == "ERROR_WRONG_PASSWORD" || errorCode == "ERROR_INVALID_CREDENTIAL" ->
        "Incorrect email or password."
      errorCode == "ERROR_USER_DISABLED" ->
        "This account has been disabled."
      errorCode == "ERROR_TOO_MANY_REQUESTS" ->
        "Too many attempts. Please wait a moment and try again."
      errorCode == "ERROR_NETWORK_REQUEST_FAILED" ->
        "Network error. Connect to the internet to ${if (isSignUp) "create your account" else "sign in"}."
      else -> rawMessage.ifBlank {
        if (isSignUp) "Sign-up failed. Please try again." else "Sign-in failed. Please try again."
      }
    }
  }

  companion object {
    private const val TAG = "Auth"
    private const val STAFF_PROVISIONING_APP_NAME = "staff_provisioning_app"
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())
  }
}

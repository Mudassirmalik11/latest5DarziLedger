package com.example.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.app.DefaultAppContainer
import com.example.app.TailorApp
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.ShopUserRole
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.ShopPreferences
import com.example.core.preferences.ShopPreferencesRepository
import com.example.core.preferences.ThemeMode
import com.example.data.local.database.DarziDatabase
import com.example.data.remote.AuthSessionProvider
import com.example.data.remote.AuthUser
import com.example.data.remote.CreateStaffAccountResult
import com.example.data.remote.EmailAuthResult
import com.example.data.remote.GoogleSignInResult
import com.example.data.sync.ConnectivityMonitor
import com.example.features.printing.ThermalPrinterClient
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

class InMemoryShopPreferencesRepository(
  initial: ShopPreferences = ShopPreferences(),
) : ShopPreferencesRepository {
  private val state = MutableStateFlow(initial)
  override val preferencesFlow: Flow<ShopPreferences> = state.asStateFlow()

  override suspend fun updateShopProfile(shopName: String, shopContact: String) {
    val trimmedName = shopName.trim().ifEmpty { "Darzi Ledger" }
    val trimmedContact = shopContact.trim()
    state.update { it.copy(shopName = trimmedName, shopContact = trimmedContact) }
  }

  override suspend fun updatePaperWidth(paperWidthMm: Int) {
    val normalized = if (paperWidthMm == 80) 80 else 58
    state.update { it.copy(paperWidthMm = normalized) }
  }

  override suspend fun updateThemeMode(themeMode: ThemeMode) {
    state.update { it.copy(themeMode = themeMode) }
  }

  override suspend fun updateLanguage(language: AppLanguage) {
    state.update { it.copy(language = language) }
  }
}

class FakeConnectivityMonitor(
  initialOnline: Boolean = false,
) : ConnectivityMonitor {
  private val state = MutableStateFlow(initialOnline)
  override val isOnline: StateFlow<Boolean> = state.asStateFlow()

  fun setOnline(online: Boolean) {
    state.value = online
  }
}

class FakeAuthSessionProvider(
  initialUser: AuthUser? = null,
) : AuthSessionProvider {
  private val state = MutableStateFlow(initialUser)
  private val emailAccounts = mutableMapOf<String, Pair<String, AuthUser>>()

  override val currentUser: AuthUser?
    get() = state.value

  override val authStateFlow: Flow<AuthUser?>
    get() = state

  var nextInteractiveUser: AuthUser = AuthUser(
    uid = "test_shop_uid",
    displayName = "Al-Noor Owner",
    email = "owner@alnoor.pk",
  )

  fun setSignedInUser(user: AuthUser?) {
    state.value = user
  }

  fun registerFakeEmailAccount(email: String, password: String, user: AuthUser) {
    emailAccounts[email.trim().lowercase()] = password to user
  }

  override suspend fun attemptAutoSignIn(context: Context): AuthUser? = state.value

  override suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
    state.value = nextInteractiveUser
    return GoogleSignInResult.Success(nextInteractiveUser)
  }

  override suspend fun signInWithEmailPassword(email: String, password: String): EmailAuthResult {
    val key = email.trim().lowercase()
    val entry = emailAccounts[key]
      ?: return EmailAuthResult.Error("No shop account found for this email.")
    if (entry.first != password) {
      return EmailAuthResult.Error("Incorrect email or password.")
    }
    state.value = entry.second
    return EmailAuthResult.Success(entry.second)
  }

  override suspend fun signUpWithEmailPassword(
    displayName: String,
    email: String,
    password: String,
  ): EmailAuthResult {
    val key = email.trim().lowercase()
    if (emailAccounts.containsKey(key)) {
      return EmailAuthResult.Error("An account with this email already exists.")
    }
    val user = nextInteractiveUser.copy(
      displayName = displayName.trim().ifEmpty { key.substringBefore('@') },
      email = email.trim(),
    )
    emailAccounts[key] = password to user
    state.value = user
    return EmailAuthResult.Success(user)
  }

  override suspend fun createStaffAccount(
    displayName: String,
    email: String,
    password: String,
    persistMembership: suspend (AuthUser) -> Result<Unit>,
  ): CreateStaffAccountResult {
    val cleanName = displayName.trim()
    val cleanEmail = email.trim()
    val key = cleanEmail.lowercase()
    if (cleanName.isEmpty()) {
      return CreateStaffAccountResult.Error("Enter the user's name.")
    }
    if (emailAccounts.containsKey(key)) {
      return CreateStaffAccountResult.Error("This email is already in use. Choose a unique email address for this user.")
    }
    if (password.length < 6) {
      return CreateStaffAccountResult.Error("Password must be at least 6 characters.")
    }
    val staffUser = AuthUser(
      uid = "staff_${emailAccounts.size + 1}_${key.substringBefore('@')}",
      displayName = cleanName,
      email = cleanEmail,
    )
    val persisted = persistMembership(staffUser)
    if (persisted.isFailure) {
      return CreateStaffAccountResult.Error(
        persisted.exceptionOrNull()?.localizedMessage ?: "Failed to link staff user to shop.",
      )
    }
    emailAccounts[key] = password to staffUser
    return CreateStaffAccountResult.Success(staffUser)
  }

  override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)

  override suspend fun signOut(context: Context) {
    state.value = null
  }
}

class AuthorizedShopTestRule(
  private val initialSignedIn: Boolean = true,
  private val authorizeLocalDevice: Boolean = true,
  private val initialOnline: Boolean = false,
  private val thermalPrinterClientOverride: ThermalPrinterClient? = null,
  private val initialUserRole: ShopUserRole = ShopUserRole.OWNER,
  private val initialLanguage: AppLanguage? = AppLanguage.ENGLISH,
) : ExternalResource() {

  lateinit var fakeAuth: FakeAuthSessionProvider
    private set

  lateinit var fakeConnectivity: FakeConnectivityMonitor
    private set

  override fun before() {
    java.util.Locale.setDefault(java.util.Locale.ENGLISH)
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    if (FirebaseApp.getApps(app).isEmpty()) {
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setProjectId("demo-no-project")
          .setApplicationId("1:1234567890:android:test")
          .setApiKey("test-api-key")
          .build(),
      )
    }
    val testUser = if (initialUserRole == ShopUserRole.OWNER) {
      AuthUser("test_shop_uid", "Al-Noor Owner", "owner@alnoor.pk")
    } else {
      AuthUser("test_staff_uid", "Ali Staff", "staff@alnoor.pk")
    }
    fakeAuth = FakeAuthSessionProvider(if (initialSignedIn) testUser else null)
    fakeConnectivity = FakeConnectivityMonitor(initialOnline = initialOnline)
    runBlocking(Dispatchers.IO) {
      DarziDatabase.getInstance(app).clearAllTables()
    }
    app.container = DefaultAppContainer(
      applicationContext = app,
      authSessionProviderOverride = fakeAuth,
      connectivityMonitorOverride = fakeConnectivity,
      thermalPrinterClientOverride = thermalPrinterClientOverride,
      shopPreferencesRepositoryOverride = InMemoryShopPreferencesRepository(
        initial = if (initialLanguage != null) {
          ShopPreferences(language = initialLanguage)
        } else {
          ShopPreferences()
        },
      ),
    )
    if (initialSignedIn && authorizeLocalDevice) {
      runBlocking {
        val device = app.container.deviceSyncRepository.ensureLocalDeviceConfigForUser(
          userUid = testUser.uid,
          shopId = "test_shop_uid",
          userRole = initialUserRole,
        )
        app.container.deviceSyncRepository.updateDeviceAuthorization(
          deviceId = device.deviceId,
          shopId = "test_shop_uid",
          deviceName = "Test Phone",
          deviceRole = "PHONE",
          status = DeviceRegistrationStatus.AUTHORIZED,
          registeredAtMillis = System.currentTimeMillis(),
          userUid = testUser.uid,
          userRole = initialUserRole.name,
        )
      }
    }
  }
}

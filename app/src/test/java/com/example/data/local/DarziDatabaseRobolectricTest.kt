package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.DamanStyle
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.ShopUserRole
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.SnapshotJson
import com.example.core.model.StyleSnapshot
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncStatus
import com.example.data.local.database.DarziDatabase
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.DuplicateCustomerException
import com.example.data.local.repository.OrderRepository
import com.example.data.local.repository.ShopRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DarziDatabaseRobolectricTest {

  private lateinit var context: Context
  private lateinit var db: DarziDatabase
  private lateinit var shopRepo: ShopRepository
  private lateinit var customerRepo: CustomerRepository
  private lateinit var orderRepo: OrderRepository
  private lateinit var deviceSyncRepo: DeviceSyncRepository

  private val sampleMeasurements = MeasurementSnapshot(
    kameezLambai = 42.0,
    tera = 18.5,
    bazu = 24.5,
    chati = 43.0,
    kamar = 39.0,
    gheraDaman = 44.0,
    galaCollar = 16.5,
    cuff = 9.5,
    shalwarLambai = 40.0,
    paincha = 7.5,
    gheraAsan = 16.5,
    notes = "Loose fitting sleeves",
  )

  private val sampleStyle = StyleSnapshot(
    neckStyle = NeckStyle.BAN,
    damanStyle = DamanStyle.GOL,
    pocketStyle = PocketStyle.TWO_SIDE,
    sleeveStyle = SleeveStyle.SINGLE_CUFF,
    silaiStyle = SilaiStyle.DOUBLE,
  )

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, DarziDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    shopRepo = ShopRepository(db)
    customerRepo = CustomerRepository(db, shopRepo)
    orderRepo = OrderRepository(db, shopRepo)
    deviceSyncRepo = DeviceSyncRepository(db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `customer CRUD, duplicate phone guard, and offline search work`(): Unit = runBlocking {
    val created = customerRepo.createCustomer(
      name = "Tariq Mahmood",
      rawPhone = "+92 300 5551234",
      notes = "Prefers starched collar",
      deviceId = "phone-1",
    ).getOrThrow()

    assertEquals("0300-5551234", created.phone)
    assertEquals("03005551234", created.normalizedPhone)

    val duplicateResult = customerRepo.createCustomer(
      name = "Tariq Duplicate",
      rawPhone = "03005551234",
      deviceId = "phone-1",
    )
    assertTrue(duplicateResult.exceptionOrNull() is DuplicateCustomerException)

    val byName = customerRepo.searchCustomers("tariq").first()
    assertEquals(1, byName.size)

    val byPhone = customerRepo.searchCustomers("0300-555").first()
    assertEquals(1, byPhone.size)

    val updated = customerRepo.updateCustomer(
      customerId = created.customerId,
      name = "Tariq Mahmood Khan",
      rawPhone = "03005551234",
      notes = "VIP",
      deviceId = "tablet-1",
    ).getOrThrow()
    assertEquals(2L, updated.version)

    val outboxEvents = db.syncOutboxDao().getAllEvents()
    assertEquals(2, outboxEvents.size)
    assertTrue(outboxEvents.all { it.syncStatus == SyncStatus.PENDING })
  }

  @Test
  fun `measurements preserve history and order snapshots remain immutable`(): Unit = runBlocking {
    val customer = customerRepo.createCustomer(
      name = "Bilal Ahmed",
      rawPhone = "0321-7778899",
      deviceId = "phone-1",
    ).getOrThrow()

    val m1 = customerRepo.saveMeasurement(
      customerId = customer.customerId,
      snapshot = sampleMeasurements,
      neckStyle = sampleStyle.neckStyle,
      damanStyle = sampleStyle.damanStyle,
      pocketStyle = sampleStyle.pocketStyle,
      sleeveStyle = sampleStyle.sleeveStyle,
      silaiStyle = sampleStyle.silaiStyle,
      deviceId = "phone-1",
      nowMillis = 1000L,
    ).getOrThrow()

    deviceSyncRepo.addOrderNumberReservation(
      deviceId = "phone-1",
      startNumber = 1001,
      endNumber = 1002,
    )

    val order = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 2,
      deliveryDateMillis = 50000L,
      totalAmountPkr = 7000L,
      initialAdvancePkr = 2000L,
      measurementSnapshot = m1.toMeasurementSnapshot(),
      styleSnapshot = m1.toStyleSnapshot(),
      deviceId = "phone-1",
      nowMillis = 2000L,
    ).getOrThrow()

    assertEquals(1001, order.orderNumber)
    assertEquals(5000L, order.remainingAmountPkr)
    assertEquals(PaymentStatus.PARTIAL, order.paymentStatus)

    // Update customer measurements later
    customerRepo.saveMeasurement(
      customerId = customer.customerId,
      snapshot = sampleMeasurements.copy(kameezLambai = 44.5),
      neckStyle = NeckStyle.COLLAR,
      damanStyle = DamanStyle.CHORASI,
      pocketStyle = PocketStyle.FRONT,
      sleeveStyle = SleeveStyle.PLAIN,
      silaiStyle = SilaiStyle.SINGLE,
      deviceId = "phone-1",
      nowMillis = 3000L,
    ).getOrThrow()

    val history = customerRepo.observeMeasurementHistory(customer.customerId).first()
    assertEquals(2, history.size)
    assertEquals(44.5, customerRepo.getCurrentMeasurement(customer.customerId)?.kameezLambai ?: 0.0, 0.001)

    // Saved order snapshot MUST retain original 42.0 and BAN neck!
    val savedOrder = db.orderDao().getById(order.orderId)!!
    val orderMeas = SnapshotJson.decodeMeasurement(savedOrder.measurementSnapshotJson)
    val orderStyle = SnapshotJson.decodeStyle(savedOrder.styleSnapshotJson)
    assertEquals(42.0, orderMeas.kameezLambai, 0.001)
    assertEquals(NeckStyle.BAN, orderStyle.neckStyle)
  }

  @Test
  fun `order numbers stay unique, handle offline exhaustion, and assign when new reservation arrives`(): Unit = runBlocking {
    val customer = customerRepo.createCustomer(
      name = "Usman Raza",
      rawPhone = "0333-1122334",
      deviceId = "phone-1",
    ).getOrThrow()

    deviceSyncRepo.addOrderNumberReservation(
      deviceId = "phone-1",
      startNumber = 1001,
      endNumber = 1001,
    )

    val firstOrder = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 1,
      deliveryDateMillis = 90000L,
      totalAmountPkr = 3500L,
      initialAdvancePkr = 0L,
      measurementSnapshot = sampleMeasurements,
      styleSnapshot = sampleStyle,
      deviceId = "phone-1",
      nowMillis = 1000L,
    ).getOrThrow()
    assertEquals(1001, firstOrder.orderNumber)

    // Reservation exhausted: second order created offline has null orderNumber
    val pendingNumberOrder = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 1,
      deliveryDateMillis = 95000L,
      totalAmountPkr = 3500L,
      initialAdvancePkr = 500L,
      measurementSnapshot = sampleMeasurements,
      styleSnapshot = sampleStyle,
      deviceId = "phone-1",
      nowMillis = 2000L,
    ).getOrThrow()
    assertNull(pendingNumberOrder.orderNumber)

    // New reservation arrives when back online
    deviceSyncRepo.addOrderNumberReservation(
      deviceId = "phone-1",
      startNumber = 1051,
      endNumber = 1100,
    )
    val assigned = orderRepo.assignPendingOrderNumbers("phone-1", nowMillis = 3000L)
    assertEquals(1, assigned)
    assertEquals(1051, db.orderDao().getById(pendingNumberOrder.orderId)?.orderNumber)
  }

  @Test
  fun `partial payments, remaining balance settlement, and independent order statuses work`(): Unit = runBlocking {
    val customer = customerRepo.createCustomer(
      name = "Kamran Ali",
      rawPhone = "0345-9988776",
      deviceId = "tablet-1",
    ).getOrThrow()

    val order = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 1,
      deliveryDateMillis = 80000L,
      totalAmountPkr = 4000L,
      initialAdvancePkr = 1000L,
      measurementSnapshot = sampleMeasurements,
      styleSnapshot = sampleStyle,
      deviceId = "tablet-1",
    ).getOrThrow()

    // Order can be delivered while balance still remains
    val delivered = orderRepo.updateOrderStatus(order.orderId, OrderStatus.READY, "tablet-1")
      .flatMap { orderRepo.updateOrderStatus(order.orderId, OrderStatus.DELIVERED, "tablet-1") }
      .getOrThrow()
    assertEquals(OrderStatus.DELIVERED, delivered.orderStatus)
    assertEquals(3000L, delivered.remainingAmountPkr)

    // Over-payment rejected
    val overpayment = orderRepo.recordPayment(order.orderId, 3500L, PaymentMethod.CASH, "", "tablet-1")
    assertTrue(overpayment.isFailure)

    // One-tap remaining settlement
    val settled = orderRepo.recordRemainingBalancePayment(order.orderId, PaymentMethod.JAZZCASH, "tablet-1").getOrThrow()
    assertEquals(4000L, settled.paidAmountPkr)
    assertEquals(0L, settled.remainingAmountPkr)
    assertEquals(PaymentStatus.PAID, settled.paymentStatus)

    val payments = orderRepo.observePaymentsForOrder(order.orderId).first()
    assertEquals(2, payments.size)
    assertEquals(1000L, payments[0].amountPkr)
    assertEquals(3000L, payments[1].amountPkr)
  }

  @Test
  fun `data persists across database close and reopen`(): Unit = runBlocking {
    val dbFile = File(context.cacheDir, "test_persist_darzi.db")
    if (dbFile.exists()) dbFile.delete()

    val db1 = Room.databaseBuilder(context, DarziDatabase::class.java, dbFile.absolutePath)
      .allowMainThreadQueries()
      .build()
    val customerRepo1 = CustomerRepository(db1, ShopRepository(db1))
    val created = customerRepo1.createCustomer("Faisal Iqbal", "0301-2233445", deviceId = "phone-1").getOrThrow()
    db1.close()

    val db2 = Room.databaseBuilder(context, DarziDatabase::class.java, dbFile.absolutePath)
      .allowMainThreadQueries()
      .build()
    val reloaded = db2.customerDao().getById(created.customerId)
    assertNotNull(reloaded)
    assertEquals("Faisal Iqbal", reloaded?.name)
    val outbox = db2.syncOutboxDao().getAllEvents()
    assertEquals(1, outbox.size)
    assertEquals(SyncEntityType.CUSTOMER, outbox.first().entityType)
    db2.close()
    dbFile.delete()
  }

  @Test
  fun `migration from v1 to v2 adds sync_conflicts table cleanly`() {
    val dbFile = File(context.cacheDir, "test_migration_v1_v2.db")
    if (dbFile.exists()) dbFile.delete()

    // Create minimal v1 database matching all 10 initial tables
    val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    sqlite.execSQL("PRAGMA user_version = 1")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `shops` (`shopId` TEXT NOT NULL, `shopName` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `email` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`shopId`))")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`customerId` TEXT NOT NULL, `shopId` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `normalizedPhone` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `lastModifiedByDevice` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`customerId`), FOREIGN KEY(`shopId`) REFERENCES `shops`(`shopId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_shopId` ON `customers` (`shopId`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_normalizedPhone` ON `customers` (`normalizedPhone`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_updatedAt` ON `customers` (`updatedAt`)")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `measurements` (`measurementId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `label` TEXT NOT NULL, `isCurrent` INTEGER NOT NULL, `kameezLambai` REAL NOT NULL, `tera` REAL NOT NULL, `bazu` REAL NOT NULL, `chati` REAL NOT NULL, `kamar` REAL NOT NULL, `gheraDaman` REAL NOT NULL, `galaCollar` REAL NOT NULL, `cuff` REAL NOT NULL, `shalwarLambai` REAL NOT NULL, `paincha` REAL NOT NULL, `gheraAsan` REAL NOT NULL, `neckStyle` TEXT NOT NULL, `damanStyle` TEXT NOT NULL, `pocketStyle` TEXT NOT NULL, `sleeveStyle` TEXT NOT NULL, `silaiStyle` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `lastModifiedByDevice` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`measurementId`), FOREIGN KEY(`customerId`) REFERENCES `customers`(`customerId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_measurements_customerId` ON `measurements` (`customerId`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_measurements_customerId_isCurrent` ON `measurements` (`customerId`, `isCurrent`)")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `orders` (`orderId` TEXT NOT NULL, `shopId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `orderNumber` INTEGER, `suitCount` INTEGER NOT NULL, `deliveryDate` INTEGER NOT NULL, `totalAmountPkr` INTEGER NOT NULL, `paidAmountPkr` INTEGER NOT NULL, `remainingAmountPkr` INTEGER NOT NULL, `orderStatus` TEXT NOT NULL, `paymentStatus` TEXT NOT NULL, `measurementSnapshotJson` TEXT NOT NULL, `styleSnapshotJson` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `lastModifiedByDevice` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`orderId`), FOREIGN KEY(`shopId`) REFERENCES `shops`(`shopId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`customerId`) REFERENCES `customers`(`customerId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_orders_orderNumber` ON `orders` (`orderNumber`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_shopId` ON `orders` (`shopId`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_customerId` ON `orders` (`customerId`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_orderStatus` ON `orders` (`orderStatus`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_deliveryDate` ON `orders` (`deliveryDate`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_createdAt` ON `orders` (`createdAt`)")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `payments` (`paymentId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `amountPkr` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`paymentId`), FOREIGN KEY(`orderId`) REFERENCES `orders`(`orderId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_orderId` ON `payments` (`orderId`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_createdAt` ON `payments` (`createdAt`)")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`eventId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `nextRetryAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `lastError` TEXT, PRIMARY KEY(`eventId`))")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_syncStatus` ON `sync_outbox` (`syncStatus`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` ON `sync_outbox` (`createdAt`)")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId` ON `sync_outbox` (`entityType`, `entityId`)")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `sync_metadata` (`deviceId` TEXT NOT NULL, `lastSuccessfulSyncAt` INTEGER, `lastSyncAttemptAt` INTEGER, `syncStatus` TEXT NOT NULL, `lastError` TEXT, PRIMARY KEY(`deviceId`))")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `device_config` (`deviceId` TEXT NOT NULL, `shopId` TEXT NOT NULL, `deviceName` TEXT NOT NULL, `deviceRole` TEXT NOT NULL, `registrationStatus` TEXT NOT NULL, `registeredAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`deviceId`))")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `order_number_reservations` (`reservationId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `startNumber` INTEGER NOT NULL, `endNumber` INTEGER NOT NULL, `nextAvailableNumber` INTEGER NOT NULL, `isExhausted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`reservationId`))")
    sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_order_number_reservations_deviceId_isExhausted` ON `order_number_reservations` (`deviceId`, `isExhausted`)")
    sqlite.execSQL("CREATE TABLE IF NOT EXISTS `printer_config` (`printerId` TEXT NOT NULL, `printerAddress` TEXT NOT NULL, `printerName` TEXT NOT NULL, `paperWidthMm` INTEGER NOT NULL, `connectionSettingsJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`printerId`))")
    sqlite.close()

    val migratedDb = Room.databaseBuilder(context, DarziDatabase::class.java, dbFile.absolutePath)
      .addMigrations(
        DarziDatabase.MIGRATION_1_2,
        DarziDatabase.MIGRATION_2_3,
        DarziDatabase.MIGRATION_3_4,
      )
      .allowMainThreadQueries()
      .build()

    runBlocking {
      val conflicts = migratedDb.deviceSyncDao().observeUnresolvedConflicts().first()
      assertTrue(conflicts.isEmpty())
      val customers = migratedDb.customerDao().observeAllActive().first()
      assertTrue(customers.isEmpty())
      val device = DeviceSyncRepository(migratedDb).ensureLocalDeviceConfigForUser(
        userUid = "owner_uid_1",
        shopId = "owner_uid_1",
        userRole = ShopUserRole.OWNER,
      )
      assertEquals("owner_uid_1", device.userUid)
      assertEquals("OWNER", device.userRole)
    }
    migratedDb.close()
    dbFile.delete()
  }

  @Test
  fun `per-user device configs isolate device ids and shop switch clears stale data`(): Unit = runBlocking {
    val ownerDevice = deviceSyncRepo.ensureLocalDeviceConfigForUser(
      userUid = "owner_uid",
      shopId = "owner_uid",
      userRole = ShopUserRole.OWNER,
    )
    deviceSyncRepo.updateDeviceAuthorization(
      deviceId = ownerDevice.deviceId,
      shopId = "owner_uid",
      deviceName = "Owner Phone",
      deviceRole = "PHONE",
      status = DeviceRegistrationStatus.AUTHORIZED,
      userUid = "owner_uid",
      userRole = ShopUserRole.OWNER.name,
    )

    val staffDevice = deviceSyncRepo.ensureLocalDeviceConfigForUser(
      userUid = "staff_uid_1",
      shopId = "owner_uid",
      userRole = ShopUserRole.STAFF,
    )
    assertTrue(staffDevice.deviceId != ownerDevice.deviceId)
    assertEquals("owner_uid", staffDevice.shopId)
    assertEquals("STAFF", staffDevice.userRole)

    // Create a customer under owner_uid shop, confirm same-shop switch does NOT clear data
    customerRepo.createCustomer(
      name = "Shared Customer",
      rawPhone = "0300-9998877",
      deviceId = ownerDevice.deviceId,
    ).getOrThrow()
    val clearedSameShop = deviceSyncRepo.clearShopDataIfSwitchingShops("owner_uid")
    assertEquals(false, clearedSameShop)
    assertEquals(1, customerRepo.observeAllCustomers().first().size)

    // Switching to a completely different shop clears stale local business records
    val clearedOtherShop = deviceSyncRepo.clearShopDataIfSwitchingShops("other_owner_uid")
    assertEquals(true, clearedOtherShop)
    assertEquals(0, customerRepo.observeAllCustomers().first().size)
  }

  @Test
  fun `upserting shop, customer, and order with existing children preserves foreign keys and child rows`(): Unit = runBlocking {
    val customer = customerRepo.createCustomer(
      name = "Asir Malik",
      rawPhone = "0300-1234567",
      deviceId = "phone-1",
      nowMillis = 1000L,
    ).getOrThrow()

    val measurement = customerRepo.saveMeasurement(
      customerId = customer.customerId,
      snapshot = sampleMeasurements,
      neckStyle = sampleStyle.neckStyle,
      damanStyle = sampleStyle.damanStyle,
      pocketStyle = sampleStyle.pocketStyle,
      sleeveStyle = sampleStyle.sleeveStyle,
      silaiStyle = sampleStyle.silaiStyle,
      deviceId = "phone-1",
      nowMillis = 2000L,
    ).getOrThrow()

    val order = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 1,
      deliveryDateMillis = 90_000L,
      totalAmountPkr = 5000L,
      initialAdvancePkr = 1500L,
      initialPaymentMethod = PaymentMethod.CASH,
      measurementSnapshot = measurement.toMeasurementSnapshot(),
      styleSnapshot = measurement.toStyleSnapshot(),
      deviceId = "phone-1",
      nowMillis = 3000L,
    ).getOrThrow()

    // 1. Syncing shop from remote & updating shop profile must NOT trigger ON DELETE RESTRICT on customers/orders
    val syncedShop = shopRepo.syncFromRemote(
      shopId = ShopRepository.DEFAULT_SHOP_ID,
      shopName = "Malik Bespoke Tailors",
      contactNumber = "0300-1234567",
      email = "mdasirmalik11@gmail.com",
      version = 4L,
      nowMillis = 4000L,
    )
    assertEquals("Malik Bespoke Tailors", syncedShop.shopName)

    val updatedShop = shopRepo.updateShopProfile(
      shopName = "Malik Royal Tailors",
      contactNumber = "0300-1234567",
      email = "mdasirmalik11@gmail.com",
      nowMillis = 5000L,
    )
    assertEquals("Malik Royal Tailors", updatedShop.shopName)
    assertEquals(5L, updatedShop.version)

    // 2. Upserting customer (as SyncEngine/Backup does) must NOT fail RESTRICT on orders or CASCADE-wipe measurements
    db.customerDao().insert(
      customer.copy(name = "Asir Malik Cloud", version = 3L, updatedAt = 6000L),
    )
    assertEquals("Asir Malik Cloud", db.customerDao().getById(customer.customerId)?.name)
    assertNotNull(db.measurementDao().getById(measurement.measurementId))

    // 3. Upserting order (as SyncEngine/Backup does) must NOT fail RESTRICT on payments
    db.orderDao().insert(
      order.copy(orderStatus = OrderStatus.READY, version = 3L, updatedAt = 7000L),
    )
    assertEquals(OrderStatus.READY, db.orderDao().getById(order.orderId)?.orderStatus)
    val payments = db.paymentDao().getPaymentsForOrder(order.orderId)
    assertEquals(1, payments.size)
    assertEquals(1500L, payments.first().amountPkr)
  }
}

private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
  fold(onSuccess = transform, onFailure = { Result.failure(it) })

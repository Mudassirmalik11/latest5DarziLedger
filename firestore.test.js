const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shop_owner";
const BOB_UID = "bob_other_tailor";
const STAFF_UID = "staff_karigar_01";
const STAFF2_UID = "staff_karigar_02";
const PHONE_ID = "device_phone_01";
const TABLET_ID = "device_tablet_02";
const REVOKED_ID = "device_revoked_03";
const STAFF_PHONE_ID = "staff_phone_01";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function shopPayload(uid = ALICE_UID) {
  const ts = pastDate(10000);
  return {
    ownerUid: uid,
    shopName: "Al-Noor Tailors",
    contactNumber: "+923001234567",
    email: "owner@alnoor.pk",
    nextOrderNumber: 1001,
    version: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

function devicePayload(
  uid = ALICE_UID,
  role = "PHONE",
  status = "AUTHORIZED",
  userUid = ALICE_UID,
  userEmail = "owner@alnoor.pk",
  userRole = "OWNER"
) {
  const ts = pastDate(10000);
  return {
    ownerUid: uid,
    userUid,
    userEmail,
    userRole,
    deviceName: role === "PHONE" ? "Shop Phone" : "Shop Tablet",
    deviceRole: role,
    status,
    appVersion: "1.0",
    registeredAt: ts,
    lastSeenAt: ts,
    updatedAt: ts,
  };
}

function staffUserPayload(ownerUid = ALICE_UID, staffUid = STAFF_UID, email = "staff1@alnoor.pk", status = "ACTIVE") {
  const ts = pastDate(10000);
  return {
    ownerUid,
    staffUid,
    displayName: "Master Usman",
    email,
    role: "STAFF",
    status,
    createdAt: ts,
    updatedAt: ts,
  };
}

function staffLinkPayload(ownerUid = ALICE_UID, staffUid = STAFF_UID, email = "staff1@alnoor.pk", status = "ACTIVE") {
  const ts = pastDate(10000);
  return {
    staffUid,
    ownerUid,
    displayName: "Master Usman",
    email,
    status,
    createdAt: ts,
    updatedAt: ts,
  };
}

function customerPayload(uid = ALICE_UID, deviceId = PHONE_ID) {
  const ts = pastDate(8000);
  return {
    ownerUid: uid,
    name: "Tariq Mahmood",
    phone: "+923001112233",
    normalizedPhone: "+923001112233",
    notes: "Regular VIP client",
    version: 1,
    lastModifiedByDevice: deviceId,
    deleted: false,
    createdAtEpochMillis: Date.now() - 8000,
    updatedAtEpochMillis: Date.now() - 8000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function measurementPayload(uid = ALICE_UID, customerId = "cust_1", deviceId = PHONE_ID) {
  const ts = pastDate(7000);
  return {
    ownerUid: uid,
    customerId,
    label: "Eid Suit 2026",
    currentFlag: true,
    kameezLambai: 41.5,
    tera: 18.0,
    bazu: 24.5,
    chati: 42.0,
    kamar: 38.0,
    gheraDaman: 44.0,
    galaCollar: 16.0,
    cuff: 9.5,
    shalwarLambai: 40.0,
    paincha: 7.5,
    gheraAsan: 16.0,
    neckStyle: "BAN",
    damanStyle: "GOL",
    pocketStyle: "TWO_SIDE",
    sleeveStyle: "SINGLE_CUFF",
    silaiStyle: "DOUBLE",
    notes: "Soft canvas collar",
    version: 1,
    lastModifiedByDevice: deviceId,
    deleted: false,
    createdAtEpochMillis: Date.now() - 7000,
    updatedAtEpochMillis: Date.now() - 7000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function orderPayload(uid = ALICE_UID, customerId = "cust_1", deviceId = PHONE_ID, orderNumber = 1001) {
  const ts = pastDate(6000);
  const payload = {
    ownerUid: uid,
    customerId,
    suitCount: 2,
    deliveryDateMillis: Date.now() + 86400000 * 3,
    totalAmountPkr: 5000,
    paidAmountPkr: 1500,
    remainingAmountPkr: 3500,
    orderStatus: "PENDING",
    paymentStatus: "PARTIAL",
    measurementSnapshot: {
      sourceMeasurementId: "meas_1",
      kameezLambai: 41.5,
      tera: 18.0,
      bazu: 24.5,
      chati: 42.0,
      kamar: 38.0,
      gheraDaman: 44.0,
      galaCollar: 16.0,
      cuff: 0,
      shalwarLambai: 40.0,
      paincha: 7.5,
      gheraAsan: 16.0,
      notes: "Soft canvas collar",
    },
    styleSnapshot: {
      neckStyle: "BAN",
      damanStyle: "GOL",
      pocketStyle: "TWO_SIDE",
      sleeveStyle: "SINGLE_CUFF",
      silaiStyle: "DOUBLE",
    },
    notes: "Urgent stitch",
    version: 1,
    lastModifiedByDevice: deviceId,
    deleted: false,
    createdAtEpochMillis: Date.now() - 6000,
    updatedAtEpochMillis: Date.now() - 6000,
    createdAt: ts,
    updatedAt: ts,
  };
  if (orderNumber !== null) {
    payload.orderNumber = orderNumber;
  }
  return payload;
}

function paymentPayload(uid = ALICE_UID, orderId = "order_1", deviceId = PHONE_ID) {
  const ts = pastDate(5000);
  return {
    ownerUid: uid,
    orderId,
    amountPkr: 1500,
    paymentMethod: "CASH",
    note: "Advance",
    deviceId,
    createdAtEpochMillis: Date.now() - 5000,
    createdAt: ts,
  };
}

async function seedShopAndDevices() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID));
    await db.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED"));
    await db.collection("shops").doc(ALICE_UID).collection("devices").doc(REVOKED_ID).set(devicePayload(ALICE_UID, "TABLET", "REVOKED"));
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read, list, or write any shop data", async () => {
  await seedShopAndDevices();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("shops").get());
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).collection("devices").get());
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).collection("customers").get());
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID)));
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's shop, devices, and records", async () => {
  await seedShopAndDevices();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertFails(bobDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("shops").get());
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID)));
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(BOB_UID, PHONE_ID)));
});

test("Shop owner can create/update shop profile, register devices, and revoke a device", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Blanket list on /shops forbidden even for authenticated users
  await assertFails(aliceDb.collection("shops").get());

  // Create shop
  await assertSucceeds(aliceDb.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID)));

  // Reject future timestamp or ghost field
  await assertFails(
    aliceDb.collection("shops").doc(BOB_UID).set({
      ...shopPayload(ALICE_UID),
    })
  );
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).update({
      shopName: "Updated",
      isAdmin: true,
      updatedAt: pastDate(1000),
    })
  );

  // Valid shop profile update
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).update({
      shopName: "Al-Noor Bespoke",
      version: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Register Phone & Tablet
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED"))
  );
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(TABLET_ID).set(devicePayload(ALICE_UID, "TABLET", "AUTHORIZED"))
  );

  // List devices succeeds for shop owner
  await assertSucceeds(aliceDb.collection("shops").doc(ALICE_UID).collection("devices").get());

  // Revoke tablet
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(TABLET_ID).update({
      status: "REVOKED",
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Device authorization gate blocks writes from revoked or unregistered devices", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Revoked device write fails
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, REVOKED_ID))
  );

  // Unknown device write fails
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, "unknown_device"))
  );

  // Authorized device write succeeds
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID))
  );
});

test("Full domain relational chain: customer -> measurement -> order -> append-only payment & terminal state locking", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const shopRef = aliceDb.collection("shops").doc(ALICE_UID);

  // Orphan measurement without customer fails
  await assertFails(
    shopRef.collection("measurements").doc("meas_1").set(measurementPayload(ALICE_UID, "missing_cust", PHONE_ID))
  );

  // Create customer (reject @DocumentId ghost 'id' field)
  await assertFails(
    shopRef.collection("customers").doc("cust_bad").set({
      id: "cust_bad",
      ...customerPayload(ALICE_UID, PHONE_ID),
    })
  );
  await assertSucceeds(
    shopRef.collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID))
  );

  // Create measurement
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_1").set(measurementPayload(ALICE_UID, "cust_1", PHONE_ID))
  );

  // Create order & list orders
  await assertSucceeds(
    shopRef.collection("orders").doc("order_1").set(orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1001))
  );
  await assertSucceeds(shopRef.collection("orders").get());

  // Cannot tamper with measurementSnapshot or mismatch remainingAmountPkr
  await assertFails(
    shopRef.collection("orders").doc("order_1").update({
      paidAmountPkr: 5000,
      remainingAmountPkr: 500, // invalid math!
      paymentStatus: "PAID",
      version: 2,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(500),
    })
  );

  // Valid payment + order balance settlement
  await assertSucceeds(
    shopRef.collection("payments").doc("pay_1").set(paymentPayload(ALICE_UID, "order_1", PHONE_ID))
  );

  // Payments are strictly append-only (no update or delete)
  await assertFails(
    shopRef.collection("payments").doc("pay_1").update({ amountPkr: 9999 })
  );
  await assertFails(
    shopRef.collection("payments").doc("pay_1").delete()
  );

  // Order transition PENDING -> READY -> DELIVERED
  await assertSucceeds(
    shopRef.collection("orders").doc("order_1").update({
      orderStatus: "READY",
      version: 2,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    shopRef.collection("orders").doc("order_1").update({
      orderStatus: "DELIVERED",
      version: 3,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(300),
    })
  );

  // Terminal state lock: DELIVERED cannot transition back to PENDING
  await assertFails(
    shopRef.collection("orders").doc("order_1").update({
      orderStatus: "PENDING",
      version: 4,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(200),
    })
  );

  // Order number reservation creation succeeds for authorized device
  await assertSucceeds(
    shopRef.collection("order_reservations").doc("res_1").set({
      ownerUid: ALICE_UID,
      deviceId: PHONE_ID,
      startNumber: 1001,
      endNumber: 1050,
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
});

test("Owner can provision staff user atomically; staff can sync on own device but cannot escalate privileges or use owner's device", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Single-document staff_users creation without matching staff_links fails atomic rule
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID)
      .set(staffUserPayload(ALICE_UID, STAFF_UID))
  );

  // Outsider Bob cannot create a staff link pointing into Alice's shop
  await assertFails(
    outsiderDb.collection("staff_links").doc(STAFF_UID)
      .set(staffLinkPayload(ALICE_UID, STAFF_UID))
  );

  // Owner atomically creates staff_users + staff_links in a single batch
  const batch = aliceDb.batch();
  batch.set(
    aliceDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID),
    staffUserPayload(ALICE_UID, STAFF_UID)
  );
  batch.set(
    aliceDb.collection("staff_links").doc(STAFF_UID),
    staffLinkPayload(ALICE_UID, STAFF_UID)
  );
  await assertSucceeds(batch.commit());

  // Staff reads own staff_link and shop profile
  await assertSucceeds(staffDb.collection("staff_links").doc(STAFF_UID).get());
  await assertSucceeds(staffDb.collection("shops").doc(ALICE_UID).get());

  // Staff cannot edit shop profile
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).update({
      shopName: "Hijacked Tailors",
      version: 2,
      updatedAt: pastDate(500),
    })
  );

  // Staff cannot list or create other staff users
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("staff_users").get());
  const rogueBatch = staffDb.batch();
  rogueBatch.set(
    staffDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF2_UID),
    staffUserPayload(ALICE_UID, STAFF2_UID, "staff2@alnoor.pk")
  );
  rogueBatch.set(
    staffDb.collection("staff_links").doc(STAFF2_UID),
    staffLinkPayload(ALICE_UID, STAFF2_UID, "staff2@alnoor.pk")
  );
  await assertFails(rogueBatch.commit());

  // Staff registers their own device (STAFF role + own UID)
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "OWNER"))
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "STAFF"))
  );

  // Staff can query their own devices, but cannot query all devices or revoke Owner's device
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices")
      .where("userUid", "==", STAFF_UID).get()
  );
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("devices").get());
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).update({
      status: "REVOKED",
      lastSeenAt: pastDate(300),
      updatedAt: pastDate(300),
    })
  );

  // Staff can create customers using their own authorized device, NOT owner's deviceId
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_spoof")
      .set(customerPayload(ALICE_UID, PHONE_ID))
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_staff")
      .set(customerPayload(ALICE_UID, STAFF_PHONE_ID))
  );

  // Owner disables Staff user -> Staff is immediately blocked from reading/writing shop data
  const revokeBatch = aliceDb.batch();
  revokeBatch.update(
    aliceDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID),
    { status: "REVOKED", updatedAt: pastDate(200) }
  );
  revokeBatch.update(
    aliceDb.collection("staff_links").doc(STAFF_UID),
    { status: "REVOKED", updatedAt: pastDate(200) }
  );
  await assertSucceeds(revokeBatch.commit());

  await assertFails(staffDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("customers").get());
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_after_revoke")
      .set(customerPayload(ALICE_UID, STAFF_PHONE_ID))
  );
});

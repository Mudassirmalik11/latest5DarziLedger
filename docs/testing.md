# Testing Guide — Darzi Ledger

All business logic, Room persistence, UI CUJs, synchronization, ESC/POS printing, backup/restore, and Firestore security rules are verified locally without requiring an emulator or ADB.

## 1. Android Unit & Robolectric Tests
Run the complete local JVM + Robolectric test suite (`29` tests across 8 suites):

```bash
gradle :app:testDebugUnitTest
```

### Covered Suites
- `DarziDatabaseTest`: Room schema creation, `MIGRATION_1_2`, foreign keys, transactional order creation, PKR payment balances, and measurement history integrity.
- `CustomerAndMeasurementFlowTest`: Customer creation, Pakistani mobile duplicate guard, measurement history, and reuse flows.
- `OrdersPaymentsAndDashboardTest`: Order creation with immutable snapshots, partial/one-tap PKR payments, status transitions, and live Dashboard metrics.
- `AuthAndDeviceGateTest`: Unauthenticated cold-start gate (`AuthScreen` isolation), Google Sign-In flow, Email/Password Sign-Up -> mismatch validation -> Sign-Out -> Sign-In, and Settings Sign-Out.
- `SyncEngineAndConflictTest`: Exponential backoff, two-way push/pull ordering, offline `#1001+` order number reservation assignment, and interactive `Keep Local` / `Use Cloud` conflict resolution UI.
- `ThermalPrintingTest`: `58mm` (32-col) and `80mm` (48-col) receipt/karigar wrapping, ESC/POS byte framing (`ESC @` .. `GS V B 0`), Settings printer selection, test print dispatch, and Order Detail Karigar preview dialog.
- `ShopBackupRepositoryTest`: Full JSON export/restore round-trip, outbox re-enqueueing, malformed/unsupported-version/orphan-FK rollback safety, and Settings Backup UI visibility.

## 2. Firestore Security Rules Emulator Tests
Run security rule tests against the local Firestore emulator (port `8085`) targeting the named database in `firebase.json`:

```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

## 3. Manual Physical Device Verification
- **Bluetooth Thermal Printing**: Pair a `58mm` or `80mm` ESC/POS printer (e.g., RPP02N / Xprinter), grant `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` permissions, select it in **Settings -> Select Bluetooth Printer**, and run **Test Print** + an Order **Karigar Slip**.
- **Two-Device Live Sync**: Install on one Android Phone and one Android Tablet signed into the same shop account, register both devices (`1/2`, `2/2`), create an order offline on Device A, reconnect, and verify live sync on Device B.

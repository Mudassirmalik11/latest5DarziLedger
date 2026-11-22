# Darzi Ledger — Tailor Shop Management System

Offline-first Native Android application (Kotlin + Jetpack Compose) for a single Pakistani men's tailoring shop managing Gents Shalwar Kameez measurements, customers, orders, PKR payments, Bluetooth thermal printing, two-device synchronization, and local JSON backup/restore.

## Technology Stack (Native Android)

- **UI:** Jetpack Compose + Material Design 3 (Adaptive `NavigationBar` / `NavigationRail` + List-Detail layouts)
- **Language:** Kotlin + Coroutines / Flow
- **Navigation:** Navigation Compose (`@Serializable` type-safe routes)
- **Local Preferences:** Jetpack DataStore
- **Local Database:** SQLite with Room (`DarziDatabase` v2 + transactional outbox)
- **Cloud Sync & Auth:** Firebase Firestore + Firebase Auth (Google Sign-In via Credential Manager + Email/Password Sign-Up/Sign-In)
- **Thermal Printing:** Android Bluetooth SPP/RFCOMM + ESC/POS formatter (`58mm` / `80mm`)
- **Backup & Restore:** Zero-permission Storage Access Framework (SAF) JSON export & transactional restore

## Documentation

- [`docs/functional-requirements.md`](docs/functional-requirements.md) — Business workflows, PKR & inch constraints, and 2-device rules
- [`docs/architecture.md`](docs/architecture.md) — Layered MVVM/repository architecture and adaptive window layout strategy
- [`docs/database-schema.md`](docs/database-schema.md) — Room tables, indices, foreign keys, and snapshot serialization
- [`docs/synchronization.md`](docs/synchronization.md) — Outbox engine, backoff, `#1001+` number blocks, and conflict resolution
- [`docs/printing.md`](docs/printing.md) — Bluetooth Classic permissions, ESC/POS receipts, and Karigar workshop slips
- [`docs/testing.md`](docs/testing.md) — Robolectric, JVM unit, Firestore emulator, and hardware testing instructions
- [`docs/deployment.md`](docs/deployment.md) — Firebase Console setup (Google + Email/Password), release signing, and Play Store compliance

## Milestone Progress

- [x] **Milestone 1: Project Foundation** — App identity, adaptive Material 3 theme, custom typography (`Playfair Display`, `Plus Jakarta Sans`, `JetBrains Mono`), adaptive launcher icon, responsive NavigationBar/NavigationRail shell, DataStore shop settings.
- [x] **Milestone 2: Local Database** — Room SQLite schema (`DarziDatabase` v2 with `MIGRATION_1_2`), 10 core tables + `sync_conflicts`, DAOs, transactional outbox repositories, and Robolectric database tests.
- [x] **Milestone 3: Customer & Measurements** — Offline Customer CRUD, Pakistani mobile formatting & duplicate guard, adaptive List-Detail view, Gents Shalwar Kameez measurement & style editor, and measurement history.
- [x] **Milestone 4: Orders, Payments & Dashboard** — Quick order creation with immutable snapshots, integer-PKR billing, partial & one-tap payments, validated order status transitions, and live shop dashboard.
- [x] **Milestone 5: Firebase Integration** — Provisioned Firestore & Firebase Auth (Google Sign-In via Credential Manager + Email/Password Sign-Up/Sign-In/Reset), single-shop 2-device authorization & revocation, hardened zero-trust `firestore.rules` verified with local emulator tests (`firestore.test.js`) and deployed via `DeployRules`.
- [x] **Milestone 6: Synchronization Engine** — Connectivity monitor, dependency-ordered outbox pusher with exponential backoff (5s–15m), atomic Firestore `#1001+` 50-number block reservations, real-time two-way `Query.snapshots()` pull, interactive `Keep Local` / `Use Cloud` conflict resolution (`SyncStatusScreen`), live top-bar sync pill, and Robolectric sync/conflict tests (`docs/synchronization.md`).
- [x] **Milestone 7: Bluetooth Printing** — Least-privilege Bluetooth Classic permissions (`BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` `neverForLocation`), paired & nearby printer picker in Settings, RFCOMM/SPP ESC/POS client (`AndroidBluetoothPrinterClient`), `58mm` (32-col) & `80mm` (48-col) Customer Receipt & Karigar Workshop Slip formatters, in-app slip preview modal, and Robolectric formatting/UI tests (`docs/printing.md`).
- [x] **Milestone 8: Finalization** — Zero-permission SAF JSON Backup Export & non-corrupting transactional Restore with summary confirmation (`ShopBackupRepository`), Urdu-ready string externalization & Play-compliant manifest audit, complete documentation (`docs/functional-requirements.md`, `docs/testing.md`, `docs/deployment.md`), and full build/test verification.

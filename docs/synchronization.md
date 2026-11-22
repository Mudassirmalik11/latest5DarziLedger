# Synchronization Architecture — Darzi Ledger

Darzi Ledger uses an **offline-first, two-way synchronization architecture** built over local Room SQLite storage (`DarziDatabase`) and Firebase Firestore (`shops/{ownerUid}/...`).

## 1. Local Outbox & Dependency Ordering

Every offline or online mutation (`Customer`, `Measurement`, `Order`, `Payment`, `Shop`) runs inside a single `Room` transaction that:
1. Inserts or updates the local entity and increments its monotonic `version`.
2. Records `lastModifiedByDevice = localDeviceId`.
3. Appends an event to `sync_outbox` with `SyncStatus.PENDING`.

When `SyncEngine` runs (`syncNow()`), ready outbox events are drained in strict dependency order so parent documents always exist in Firestore before child documents:
`SHOP` → `CUSTOMER` → `MEASUREMENT` → `ORDER` → `PAYMENT`.

An event is marked `SYNCED` **only after** the Firestore write `.await()` completes cleanly.

## 2. Retry & Exponential Backoff

Failed pushes increment `retryCount` and compute `nextRetryAt` using exponential backoff in `ConflictResolver.computeBackoffMillis`:
- Base delay: `5s` (`5s → 15s → 45s → 135s → 405s → 900s`)
- Maximum cap: `15 minutes` (`900,000 ms`)

Changes stay queued locally across app restarts and automatically flush when `ConnectivityMonitor.isOnline` transitions back to `true`.

## 3. Two-Way Real-Time Pull & Echo Prevention

When signed in and authorized (`DeviceRegistrationStatus.AUTHORIZED`), `SyncEngine.startRealtimeSync` subscribes to Firestore `Query.snapshots()` listeners on `customers`, `measurements`, `orders`, `payments`, and `order_reservations` using `DocumentSnapshot.ServerTimestampBehavior.ESTIMATE`.

Remote updates are written directly into Room DAOs **without touching `sync_outbox`**, eliminating infinite push/pull sync loops. Any local entity with an active pending/conflicted outbox item is skipped during pull until pushed or resolved.

## 4. Safe Multi-Device Conflict Resolution

Before updating an existing remote document (`CUSTOMER`, `MEASUREMENT`, `ORDER`), `SyncEngine` compares versions:
- `baseVersion = local.version - 1`
- If `remote.lastModifiedByDevice != localDeviceId` and `remote.version > baseVersion`, a **concurrent edit conflict** occurred.
- Rather than silently overwriting either device's edit, `SyncEngine` stores both payloads in `sync_conflicts` and marks the outbox event `CONFLICT`.
- Shop staff inspect conflicts in **Cloud Sync & Conflicts** (`SyncStatusScreen`) and choose:
  - **Keep Local**: bumps `version = max(localVersion, remoteVersion) + 1`, updates local Room, and re-queues the outbox item to `PENDING`.
  - **Use Cloud**: applies the remote entity to Room and marks the outbox item `SYNCED`.

Payments (`payments`) are strictly **append-only** immutable ledger entries and never overwrite prior payments.

## 5. Sequential `#1001+` Order Number Block Reservations

To guarantee unique sequential order numbers across Phone and Tablet even while offline:
- Each authorized device reserves non-overlapping blocks of 50 numbers (`ORDER_BLOCK_SIZE = 50`) via an atomic Firestore transaction incrementing `shops/{ownerUid}.nextOrderNumber` and writing `order_reservations/{reservationId}`.
- When local remaining numbers drop below `MIN_NUMBER_BUFFER = 5`, `SyncEngine` automatically tops up the device's block online.
- Orders created offline after an exhausted block temporarily display `Number Pending` (`orderNumber = null`) and automatically receive sequential numbers via `OrderRepository.assignPendingOrderNumbers()` on the next online sync.

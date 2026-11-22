# Local Database Schema — Darzi Ledger

SQLite (via Room `DarziDatabase`, `version = 2`) is the primary offline-first source of truth.

## Tables

1. **`shops`** — Single tailoring shop profile (`shopId`, `shopName`, `contactNumber`, `email`, `createdAt`, `updatedAt`, `version`).
2. **`customers`** — Customer records keyed by UUID `customerId`, FK to `shops`, indexed on `shopId`, `name`, `normalizedPhone`, `updatedAt`, with `isDeleted`/`deletedAt` sync tombstones.
3. **`measurements`** — Gents Shalwar Kameez measurements in inches (`kameezLambai`, `tera`, `bazu`, `chati`, `kamar`, `gheraDaman`, `galaCollar`, `cuff`, `shalwarLambai`, `paincha`, `gheraAsan`) + style enums (`neckStyle`, `damanStyle`, `pocketStyle`, `sleeveStyle`, `silaiStyle`) + `isCurrent` flag preserving historical records.
4. **`orders`** — Tailoring orders keyed by UUID `orderId`, unique nullable `orderNumber` (`#1001+` or `NULL` when offline reservation is exhausted), integer PKR amounts (`totalAmountPkr`, `paidAmountPkr`, `remainingAmountPkr`), independent `orderStatus` and `paymentStatus`, and immutable `measurementSnapshotJson` / `styleSnapshotJson`.
5. **`payments`** — Immutable append-only PKR payment ledger (`paymentId`, `orderId`, `amountPkr`, `paymentMethod`, `createdAt`, `deviceId`).
6. **`sync_outbox`** — Transactional outbox events written in the same SQLite transaction as every business mutation (`eventId`, `entityType`, `entityId`, `operation`, `payloadJson`, `retryCount`, `nextRetryAt`, `syncStatus`).
7. **`sync_metadata`** — Per-device sync timestamps and status.
8. **`device_config`** — Registered device identity, role (`PHONE`/`TABLET`), and authorization status.
9. **`order_number_reservations`** — Device-scoped sequential order number blocks (`startNumber`, `endNumber`, `nextAvailableNumber`, `isExhausted`).
10. **`printer_config`** — Saved Bluetooth printer MAC address, name, and `58mm`/`80mm` width.
11. **`sync_conflicts`** *(added in `MIGRATION_1_2`)* — Preserves conflicting local and remote payloads/versions for manual resolution.

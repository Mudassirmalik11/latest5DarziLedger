# Functional Requirements — Darzi Ledger

Darzi Ledger is an offline-first Android application built for a single Pakistani men's tailoring shop managing Gents Shalwar Kameez orders, measurements, PKR payments, Bluetooth thermal printing, and two-device synchronization.

## 1. Authentication & Device Limits
- **Single Shop Account**: Supports both **Email/Password** (Sign Up, Sign In, Password Reset) and **Google Sign-In** (Jetpack `CredentialManager`).
- **2-Device Authorization**: Maximum of **2 active authorized devices** (e.g., 1 Phone + 1 Tablet) enforced both in-app and via Firestore security rules. Owners can revoke or re-authorize devices from Settings.
- **Offline Continuity**: Once authorized, the local Room database remains readable and writable offline without requiring internet connectivity.

## 2. Customers & Shalwar Kameez Measurements (Inches)
- Normalized Pakistani mobile formatting (`03XX-XXXXXXX`) and duplicate mobile guard on creation.
- Live search by customer name or phone digits.
- **11 Body Measurements (Inches)**:
  - Kameez: `Lambai`, `Tera`, `Bazu`, `Chati`, `Kamar`, `Ghera / Daman`, `Gala / Collar`, `Cuff`.
  - Shalwar: `Shalwar Lambai`, `Paincha`, `Ghera / Asan`.
- **5 Style Preferences**: Neck (`Collar`, `Ban`, `Gol Gala`, `V-Neck`), Daman (`Gol`, `Chorasi`), Pockets (`Two Side`, `Front + Side`, `Single`), Sleeves (`Plain`, `Single Cuff`, `Double Cuff`), Silai (`Single`, `Double`, `Chamak`).
- Versioned measurement history allowing old records to be cloned ("Reuse as New") without losing history.

## 3. Orders, Snapshots & PKR Payments
- **Sequential Order Numbers (`#1001+`)**: Assigned offline from pre-reserved 50-number device blocks (`NUMBER PENDING` shown if block is exhausted offline, auto-assigned upon next sync).
- **Immutable Snapshots**: Measurements and styles are captured inside the order as immutable JSON snapshots (`measurementSnapshotJson`, `styleSnapshotJson`) so subsequent customer edits never alter active cutting/stitching instructions.
- **Integer PKR Ledger**: Total bill, advance payment, partial payments, and one-tap remaining settlement stored in exact whole Rupees (`Long`), preventing floating-point rounding drift.
- **Validated Status Workflow**: `PENDING -> READY -> DELIVERED` (or `CANCELLED`, preserving full ledger history).

## 4. Daily Dashboard, Thermal Printing & Backup
- **Dashboard**: Live counts for Pending Suits, Due Today, Ready for Pickup, Outstanding PKR, Today's Collection PKR, and quick search.
- **Bluetooth Thermal Printing (`58mm` / `80mm`)**: Customer Receipt & Karigar Workshop Slip formatting in ESC/POS with in-app monospace preview.
- **Data Portability**: Zero-permission Storage Access Framework (SAF) JSON Backup Export & transactional validation/restore.

# Bluetooth Thermal Printing — Darzi Ledger

Darzi Ledger supports classic **Bluetooth SPP / RFCOMM (`00001101-0000-1000-8000-00805F9B34FB`)** ESC/POS thermal receipt and workshop slip printing on `58mm` (`32` columns) and `80mm` (`48` columns) paper rolls.

## 1. Least-Privilege Android Permissions

Declared in `AndroidManifest.xml`:
- Android 11 and lower (`maxSdkVersion="30"`): `BLUETOOTH`, `BLUETOOTH_ADMIN`
- Android 12+ (API 31+): `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"`
- Optional hardware declaration: `<uses-feature android:name="android.hardware.bluetooth" android:required="false" />`

No location permissions (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) are requested. Runtime Bluetooth permissions are prompted in Jetpack Compose (`ActivityResultContracts.RequestMultiplePermissions`) before listing paired devices or scanning nearby printers.

## 2. Supported Print Documents

1. **Customer Receipt (`formatCustomerReceiptBytes`)**
   - Shop name and contact header
   - Sequential `#1001+` order number (or `NUMBER PENDING` if offline reservation block exhausted)
   - Customer name, mobile number, suits count, created & delivery dates, order status
   - Integer-PKR financial summary (`Total Bill`, `Paid`, `REMAINING`, `Pay Status`) and itemized payments
2. **Karigar Workshop Slip (`formatKarigarSlipBytes`)**
   - Formatted strictly from the order's **immutable** `measurementSnapshot` and `styleSnapshot`
   - All 11 Kameez & Shalwar measurements in inches (2-column layout on `80mm`, single-column key-value on `58mm`)
   - All 5 style preferences (`Neck`, `Daman`, `Pockets`, `Sleeves`, `Silai`) and fitting notes
3. **Test Print & Monospace Preview**
   - Test slip from **Settings** (`Print Test Slip`)
   - In-app monospace preview modal (`Preview Customer` / `Preview Karigar`) matching `32` or `48` columns prior to printing

## 3. Verification Limits in Cloud Environments

- ESC/POS byte encoding (`ESC @`, `ESC a`, `ESC E`, `GS !`, `GS V B 0`), word wrapping, `58mm`/`80mm` column alignment, printer configuration persistence, and Order Detail slip previews are verified with automated JVM/Robolectric tests (`ThermalPrintingTest.kt`).
- Physical RFCOMM socket transmission requires a paired Bluetooth thermal printer on real Android hardware (`AndroidBluetoothPrinterClient` never simulates fake print success when hardware is absent or powered off).

# Architecture Overview — Darzi Ledger

## Package Layout

```text
app/src/main/java/com/example/
├── app/
│   ├── AppContainer.kt        # Manual constructor DI container
│   ├── AppNavigation.kt       # @Serializable type-safe Navigation Compose routes
│   ├── DarziLedgerApp.kt      # Adaptive NavigationBar / NavigationRail + NavHost
│   └── TailorApp.kt           # Application entry point
├── core/
│   ├── preferences/           # DataStore-backed local shop & printer preferences
│   └── ui/                    # WindowWidthClass adaptive helpers & shared ledger cards
├── features/
│   ├── dashboard/             # Daily shop overview & quick navigation
│   ├── customers/             # Customer directory & Shalwar Kameez measurements
│   ├── orders/                # Order management, snapshots & PKR billing
│   └── settings/              # Shop profile, 58mm/80mm printer width, theme mode
└── ui/theme/                  # Color, Shape, Type (bundled TTF fonts), Theme
```

## Adaptive Layout Strategy

- Uses `BoxWithConstraints` (`widthClassFor(maxWidth)`) rather than static screen dimensions so multi-window and foldable configurations adapt seamlessly:
  - **Compact (`< 600.dp`)**: Bottom `NavigationBar` + single/two-tile rows.
  - **Medium (`600.dp..839.dp`) & Expanded (`>= 840.dp`)**: Start `NavigationRail` + 4-column metric rows and wider workspace containers (`widthIn(max = 980.dp)`).

## Milestone 1 Verification

- Verified compilation via Gradle (`compile_applet`).
- Verified app identity and top-level Navigation Compose switching via Robolectric (`gradle :app:testDebugUnitTest`).

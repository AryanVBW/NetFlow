# AGENTS.md — NetFlow Predict

Guidelines for agentic coding agents working in this repository.

---

## Project Overview

**NetFlow Predict** is a privacy-first Android network monitor with on-device AI traffic
prediction. It captures real device traffic via a local VPN (TUN interface), logs DNS queries,
tracks per-app connections, aggregates stats into a Room database, and runs periodic risk analysis.

- Package: `com.netflow.predict`
- Min SDK 26 · Target/Compile SDK 35 · Java 17 · Kotlin 2.0.21
- Build system: Gradle 8.7 with Kotlin DSL (`*.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`)
- DI: Hilt with **KSP** (never KAPT)
- Single `:app` module, single-activity architecture

---

## Build / Lint / Test Commands

Run all commands from the repo root.

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (R8 minification enabled)
./gradlew :app:assembleRelease

# All unit tests
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "com.netflow.predict.ui.viewmodel.HomeViewModelTest"

# Single test method
./gradlew :app:testDebugUnitTest --tests "com.netflow.predict.ui.viewmodel.HomeViewModelTest.initial vpn state is disconnected"

# Instrumented tests (requires running emulator/device)
./gradlew :app:connectedDebugAndroidTest

# Android Lint (report: app/build/reports/lint-results-debug.html)
./gradlew :app:lintDebug

# Clean
./gradlew clean
```

---

## Architecture

```
app/src/main/java/com/netflow/predict/
├── NetFlowApp.kt / MainActivity.kt   Application & single-activity entry
├── data/
│   ├── model/Models.kt                All data classes and enums
│   ├── local/                         Room: Entities.kt, Daos.kt, NetFlowDatabase.kt
│   └── repository/Repositories.kt     VpnRepository, TrafficRepository, SettingsRepository
├── di/AppModule.kt                    Hilt @Module — DB, DAOs, WorkManager
├── engine/                            PacketParser, FlowTracker, AppResolver,
│                                      DomainClassifier, VpnPacketLoop
├── service/                           NetFlowVpnService, BootReceiver
├── worker/                            PredictionWorker, DataRetentionWorker
└── ui/                                theme/, navigation/, components/,
                                       viewmodel/ViewModels.kt, screens/
```

Pattern: **MVVM**. `@HiltViewModel` → repositories → Room/DataStore. Screens collect
`StateFlow` via `collectAsState()`. Navigation via `NavHost`; screens never import each other.

---

## Code Style

### Kotlin / General

- **Kotlin idioms**: prefer `when`, `let`, `also`, `apply`, `copy()`, scope functions.
- Prefer `val` over `var`; only use `var` when mutation is genuinely required.
- Named arguments for calls with ≥ 3 parameters.
- `when` expressions must be exhaustive; use `else` only when truly necessary.
- No `!!` — use `?: return`, `?.let {}`, or safe unwrapping.
- `try/catch` only at service/repository boundaries; use `catch (_: Exception)` for safe enum parsing.
- Section banners in large files: `// ── Section Name ──────────────`

### Naming

| Element | Convention | Example |
|---|---|---|
| Classes / Objects | `PascalCase` | `TrafficRepository` |
| Functions / vars | `camelCase` | `formatBytes()` |
| Constants (top-level `val`) | `PascalCase` | `Primary`, `ErrorColor` |
| Enums | `PascalCase` class / `SCREAMING_SNAKE` entries | `RiskLevel.HIGH` |
| Composables | `PascalCase`, noun or noun-phrase | `RiskBadge`, `SectionCard` |
| ViewModel backing fields | `_camelCase` + public `val` | `_isLoading` / `isLoading` |
| Screen routes | `lowercase_snake` | `"app_detail/{packageName}"` |

### Imports

- **Wildcard** for tightly-coupled packages: `androidx.compose.foundation.layout.*`,
  `androidx.compose.material3.*`, `androidx.compose.runtime.*`, `kotlinx.coroutines.*`,
  `kotlinx.coroutines.flow.*`, `com.netflow.predict.data.model.*`,
  `com.netflow.predict.data.local.dao.*`, `com.netflow.predict.data.local.entity.*`,
  `com.netflow.predict.ui.theme.*`, `com.netflow.predict.ui.components.*`.
- **Explicit** for individual utilities: `androidx.compose.ui.Alignment`, `androidx.compose.ui.unit.dp`.
- **Never** use platform wildcard imports (`java.io.*`).
- Order: Android/Kotlin stdlib → AndroidX → third-party → project-local.

### Jetpack Compose

- Screen composables accept navigation lambdas (`onBack`, `onNavigateTo*`), never `NavController` directly — exception: `HomeScreen` receives `NavController` for bottom bar.
- State hoisting: hoist to nearest composable that needs it; don't pass ViewModels down.
- `hiltViewModel()` at screen call-site only, never in child composables.
- `@OptIn` annotations at function level, not file level.
- `Modifier` chains: receiver-first, one modifier per line for long chains.
- `remember { mutableStateOf(...) }` for ephemeral UI state.

### Color & Theme

- Named tokens from `ui/theme/Color.kt` for custom colors (`Primary`, `ErrorColor`, etc.).
- `MaterialTheme.colorScheme.*` for standard Material roles.
- Never hard-code hex values inline.
- `riskColor(riskLevel)` from `RiskBadge.kt` for risk-level colors.

### Data Layer

- Repositories use `@Singleton` + `@Inject constructor()` with direct constructor injection.
- Repositories return `Flow<T>` for queries; `suspend` functions for mutations.
- `AppModule` provides Database, DAOs, and WorkManager — not repositories.
- DataStore is defined as `internal val Context.dataStore` in `Repositories.kt` and shared by `BootReceiver.kt`.

### ViewModels

- `@HiltViewModel` with `@Inject constructor`.
- Expose `StateFlow` / `SharedFlow` only (no `LiveData`).
- `stateIn(viewModelScope, SharingStarted.Lazily, initialValue)` for derived flows.
- Side-effects in `viewModelScope.launch {}`.
- Colocated ViewModels (e.g. `AppDetailViewModel` in `AppDetailScreen.kt`, `SettingsViewModel` in `SettingsScreen.kt`) are acceptable for detail/settings screens.

### Service Layer

- `NetFlowVpnService` uses `@AndroidEntryPoint` and injects DAOs via Hilt.
- It exposes static `activeFlowTracker` and `isRunning` for cross-component access.
- `BootReceiver` checks DataStore `auto_start_vpn` preference before starting.
- Use `PendingIntent.FLAG_IMMUTABLE` for all `PendingIntent` instances.

### Testing

- Unit tests: `app/src/test/`, JUnit 4, `kotlinx-coroutines-test`, `UnconfinedTestDispatcher`.
- Instrumented tests: `app/src/androidTest/`, Compose UI testing with `createComposeRule()`.
- Backtick method names: `` `descriptive name of behavior`() ``.
- Setup/teardown: `Dispatchers.setMain(testDispatcher)` in `@Before`, `resetMain()` in `@After`.
- Test class mirrors source class name with `Test` suffix: `HomeViewModelTest`.

---

## Adding Dependencies

1. Add version to `[versions]` in `gradle/libs.versions.toml`.
2. Add library alias to `[libraries]`.
3. Reference as `libs.<alias>` in `app/build.gradle.kts`.
4. Annotation processors use `ksp(libs.xxx.compiler)` — **never KAPT**.

---

## What Does Not Exist Yet

- Real AI/ML model integration (predictions use heuristic scoring, not a trained model)
- Cloud sync / remote API integration
- Room migration strategy (currently uses `fallbackToDestructiveMigration()`)

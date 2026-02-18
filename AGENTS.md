# AGENTS.md — NetFlow Predict

Guidelines for agentic coding agents working in this repository.

---

## Project Overview

**NetFlow Predict** is a privacy-first Android network monitor with on-device AI traffic
prediction. Stack: Kotlin · Jetpack Compose · Material 3 · Hilt · Room · Kotlin Coroutines/Flow.

- Package: `com.netflow.predict`
- Min SDK 26 · Target/Compile SDK 35 · Java 17 · Kotlin 2.0.21
- Build system: Gradle 8.7 with Kotlin DSL (`*.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`)
- DI: Hilt (KSP, **not** KAPT)

---

## Build Commands

All commands must be run from the repo root: `/Volumes/DATA_vivek/GITHUB/NetFlow ` (note the trailing space — always quote the path).

```bash
# Assemble debug APK
./gradlew :app:assembleDebug

# Assemble release APK (minification enabled)
./gradlew :app:assembleRelease

# Run all unit tests
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.netflow.predict.ExampleUnitTest"

# Run a single test method
./gradlew :app:testDebugUnitTest --tests "com.netflow.predict.ExampleUnitTest.addition_isCorrect"

# Run instrumented (on-device) tests
./gradlew :app:connectedDebugAndroidTest

# Run Android Lint
./gradlew :app:lintDebug
# Lint report written to: app/build/reports/lint-results-debug.html

# Check for dependency updates (if version-catalog plugin added later)
./gradlew dependencyUpdates

# Clean build
./gradlew clean
```

> There is currently no `gradlew` shell script in the repo — generate it by running
> `gradle wrapper --gradle-version 8.7` once, or copy a standard wrapper from another project.
> The `gradle/wrapper/gradle-wrapper.properties` file is already present and points to Gradle 8.7-bin.

---

## Architecture

```
app/src/main/java/com/netflow/predict/
├── NetFlowApp.kt               @HiltAndroidApp Application class
├── MainActivity.kt             @AndroidEntryPoint, single-activity
├── data/
│   ├── model/Models.kt         All data classes and enums (single file)
│   └── repository/             VpnRepository, TrafficRepository (stub flows)
├── di/AppModule.kt             Hilt @Module — singleton repository bindings
├── service/
│   ├── NetFlowVpnService.kt    Foreground VpnService stub
│   └── BootReceiver.kt         BOOT_COMPLETED → auto-start VPN
└── ui/
    ├── theme/                  Color.kt, Type.kt, Shape.kt, Theme.kt
    ├── navigation/             Screen.kt (sealed), AppNavigation.kt, BottomNavBar.kt
    ├── components/             Shimmer, RiskBadge, Charts (Canvas), ShieldLogo
    ├── viewmodel/ViewModels.kt All shared ViewModels (split later as needed)
    └── screens/
        ├── splash / onboarding / permissions
        ├── home / live / apps / predictions / settings / connection
        └── apps/AppDetailScreen.kt  (AppDetailViewModel colocated in same file)
```

Pattern: **MVVM** — `@HiltViewModel` classes inject repositories; screens collect
`StateFlow` via `collectAsState()`. Navigation is handled by `AppNavigation.kt` (NavHost);
screens never import each other directly.

---

## Code Style

### Kotlin / General

- **Kotlin idioms first**: use `when`, `let`, `also`, `apply`, scope functions, data-class `copy()`.
- Prefer `val` over `var`. Only use `var` when mutation is genuinely required.
- Use named arguments for calls with ≥ 3 parameters.
- `when` expressions must be exhaustive; add `else` only when truly needed.
- No `!!` — use `?: return`, `?.let {}`, or safe unwrapping patterns.
- `TODO()` is acceptable for stub/placeholder logic; prefix with `// TODO:` in comments.
- No checked exceptions; use `runCatching` / `try/catch` only at service/repo boundaries.

### Naming

| Element | Convention | Example |
|---|---|---|
| Classes / Objects | `PascalCase` | `TrafficRepository` |
| Functions / vars | `camelCase` | `formatBytes()` |
| Constants (top-level `val`) | `PascalCase` | `Primary`, `ErrorColor` |
| Enums & entries | `PascalCase` / `SCREAMING_SNAKE` | `RiskLevel.HIGH` |
| Composables | `PascalCase`, noun or noun-phrase | `RiskBadge`, `SectionCard` |
| Private composables | `PascalCase` with `private fun` | `InfoRow` |
| ViewModel state | `_backing` + public `val` | `_isLoading` / `isLoading` |
| Routes (Screen) | `lowercase_snake` string | `"app_detail/{packageName}"` |

### Imports

- Use wildcard imports for tightly-coupled packages: `androidx.compose.foundation.layout.*`,
  `androidx.compose.material3.*`, `androidx.compose.runtime.*`, `com.netflow.predict.data.model.*`,
  `com.netflow.predict.ui.theme.*`, `com.netflow.predict.ui.components.*`.
- Use explicit imports for individual utilities: `androidx.compose.ui.Alignment`,
  `androidx.compose.ui.unit.dp`, `androidx.compose.ui.graphics.Color`.
- Never use platform wildcard imports (`java.io.*`).
- Import order (enforced by IDE): Android/Kotlin stdlib → AndroidX → third-party → project-local.

### Jetpack Compose

- Every `@Composable` that is a full screen must accept navigation lambdas (`onBack`, `onNavigateTo*`), never a `NavController` directly — exception: screens that host a `BottomBar` (e.g. `HomeScreen`) receive `NavController` only for the bottom bar.
- State hoisting: hoist state to the nearest `@Composable` that needs it; avoid passing `ViewModel` instances down the tree.
- Use `hiltViewModel()` at the screen call-site only, never inside child composables.
- `LaunchedEffect(key)` for one-shot side-effects tied to a key.
- `remember { mutableStateOf(...) }` for ephemeral UI state (dialogs, sheet visibility).
- `Modifier` chains: always receiver-first and chained with `.`, one modifier per line for long chains.
- All `@OptIn` annotations must be at the function level, not file level.
- Prefer `Card` with `BorderStroke(1.dp, ...)` over plain `Box` with `border()` for elevated surfaces.

### Color & Theme Tokens

- Always reference named tokens from `ui/theme/Color.kt` (`Primary`, `ErrorColor`, `SurfaceVariant`, etc.) for **non-Material** custom colors.
- Reference `MaterialTheme.colorScheme.*` for standard Material roles (`onBackground`, `surface`, `outline`, etc.).
- Never hard-code hex values inline in composables.
- `riskColor(riskLevel)` from `RiskBadge.kt` is the canonical function for risk-level colors.

### Data Layer

- Repositories return `Flow<T>` — never suspend functions at the repo boundary.
- All fake/stub data lives in `Repositories.kt`; screens must not contain hardcoded fake data (minor static stubs in Timeline tab are acceptable temporarily).
- `@Singleton` + `@Inject constructor()` on repositories; `AppModule` provides them explicitly for test-replaceability.

### ViewModels

- `@HiltViewModel` with `@Inject constructor`.
- Expose only `StateFlow` / `SharedFlow` (no `LiveData`).
- `stateIn(viewModelScope, SharingStarted.Lazily, initialValue)` for derived flows.
- Side-effects (service calls, DB writes) go in `viewModelScope.launch {}`.
- Colocated ViewModels (e.g. `AppDetailViewModel` in `AppDetailScreen.kt`) are acceptable for detail screens; shared ViewModels live in `ui/viewmodel/ViewModels.kt`.

### Service Layer

- `NetFlowVpnService` is a stub — packet-parsing logic is marked `TODO` and must not be added without a corresponding Room entity + DAO.
- `BootReceiver` must check a user preference before starting the service.
- Use `PendingIntent.FLAG_IMMUTABLE` for all `PendingIntent` instances (Android 12+ requirement).

---

## Adding Dependencies

1. Add version to `gradle/libs.versions.toml` `[versions]` block.
2. Add library alias to `[libraries]` block.
3. Reference via `libs.<alias>` in `app/build.gradle.kts`.
4. Use **KSP** for annotation processors (`ksp(libs.xxx.compiler)`) — never KAPT.

---

## What Does Not Exist Yet

The following are stubs awaiting real implementation:

- Room database (entities, DAOs, database class)
- Actual VPN packet parsing in `NetFlowVpnService`
- DataStore persistence for `AppSettings`
- Real AI/ML model integration (currently all predictions are fake)
- Unit and instrumented tests (no test files exist yet)

When implementing any of the above, add the corresponding test file alongside the
production file under `app/src/test/` (unit) or `app/src/androidTest/` (instrumented).

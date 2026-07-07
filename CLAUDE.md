# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Climber+" (`com.hazzus.karooclimber`) — a floating climb-overlay extension for
Hammerhead Karoo 2/3 bike computers, built on the karoo-ext API. Single Android
app module (`app/`), Kotlin, minSdk 26.

## Commands

Java is not on PATH — every gradle/adb-signing invocation needs:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

- Build:      `./gradlew assembleDebug` → `app/build/outputs/apk/debug/karoo-climber-plus-debug.apk`
- Tests:      `./gradlew testDebugUnitTest` (JVM-only; all logic tests live here)
- Single test: `./gradlew testDebugUnitTest --tests "com.hazzus.karooclimber.ClimbEngineTest"`
- Release:    `./gradlew assembleRelease` → signed `karoo-climber-plus.apk`; signing reads
  gitignored `keystore.properties` (keystore at `~/keystores/karoo-climber-plus.jks`)
- Install:    `adb install -r app/build/outputs/apk/debug/karoo-climber-plus-debug.apk`
  (debug and release have different signatures — switching requires uninstall)

`karoo-ext` resolves via JitPack (`com.github.hammerheadnav:karoo-ext`) — no GitHub PAT,
despite what upstream docs say.

## Device workflow (no emulator path)

karoo-ext binds to Karoo OS services, so integration only works on a real device —
that's why all math is unit-tested off-device. On-desk verification uses **Demo mode**
(settings toggle) which feeds a canned 5 km two-climb route (`debug/DemoData.kt`) with
synthetic progress looping at 15 m/s (`ClimberExtension`).

- Crash logs: `adb logcat -d | grep -A 30 "FATAL EXCEPTION"`
- Extension binding check: logcat lines `HHApp: Extensions: ... connected` and
  `ClimberExtension: Karoo system connected=true`
- Screenshots for visual checks: `adb exec-out screencap -p > file.png`
  (wake first: `adb shell input keyevent KEYCODE_WAKEUP`)

## Architecture

Data flows one way: karoo-ext events → repos → pure engine → overlay window.

- **`ClimberExtension`** (KarooExtension service, entry point): owns the
  `KarooSystemService` connection, wires everything, combines flows
  (route, distance-to-destination, settings, ride state, demo progress) at ~1 Hz and
  drives `OverlayController.show/hide`. Also reconciles `OnStreamState` consumers for
  whichever system data fields are configured (`reconcileSysStreams`).
- **`data/`** — `NavigationRepo` normalizes `OnNavigationState` (route or destination
  navigation) into `RouteData`; `ProgressRepo` streams `DISTANCE_TO_DESTINATION`;
  progress along route = `totalDistance − distanceToDestination`.
  `ElevationProfile` decodes Karoo's elevation polyline — **pairs of
  (distance, elevation), Google polyline, precision 1 (factor 10)**, unlike the route
  polyline (precision 5). `ClimbEngine` is pure logic: always returns
  `ClimbUiState.Shown` when a route exists (`active` set only inside the climb's
  trigger window), with GPS-noise hysteresis around the trigger boundary.
- **`overlay/`** — `OverlayController` owns the `TYPE_APPLICATION_OVERLAY` window
  (ki2 pattern: SYSTEM_ALERT_WINDOW permission + foreground service keepalive) and
  sizes it per `ViewModeMachine.PanelSize` (CHIP / EXPANDED drawer / FULL 92%).
  `ClimbOverlayView` is a single Canvas view rendering all states: chip variants,
  climb profile (cached per-climb geometry + fixed 100 m color chunks; silhouette
  `Path` rebuilt only when the visible window changes — battery matters on Karoo 2),
  sliding next-500 m chunk strip, climbs list, 2×2 data-field grid.
  `ViewModeMachine` holds display mode (Base/Alt/Preview round-robin) and panel size;
  gestures: tap = expand chip / cycle modes, swipe up/down = resize, drag scrolls the
  full-screen list.
- **`settings/`** — DataStore-backed `SettingsRepo`; `ClimbField` enum doubles as
  climb-derived fields (no `dataTypeId`) and Karoo system stream fields (with one).
  Compose UI only in `SettingsActivity` — the overlay deliberately avoids Compose.
- **`palette/GradePalettes`** — band values copied from Barberfish (Apache-2.0,
  attributed in NOTICE); palettes without negative bands fall back to
  `descentColor` for downhill chunks.

## Conventions / gotchas

- UI text on the overlay is unitless by design (native-Climber parity): distances as
  `1.4` (km) or `400` (m), grades bare numbers, arrows (`→`, `↗`) instead of unit labels.
- `OverlayController.show()` must not trigger `updateViewLayout` before `addView` —
  the relayout hook is attached only after the window exists (past crash).
- WindowManager calls are main-thread; the extension scope runs on
  `Dispatchers.Main.immediate`.
- `ClimbEngineTest` uses its own route fixture on purpose — keep it decoupled from
  `DemoData`, which changes freely for demo UX.
- Visual/native-design references live in the README screenshot table (`docs/img/`).

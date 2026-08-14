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
- Single test: `./gradlew testDebugUnitTest --tests "com.hazzus.karooclimber.ClimbStateBuilderTest"`
- Release:    `./gradlew assembleRelease` → signed `karoo-climber-plus.apk`; signing reads
  gitignored `keystore.properties` (keystore at `~/keystores/karoo-climber-plus.jks`)
- Publish:    push tag `v<versionName>` (must match `app/build.gradle.kts`) →
  `.github/workflows/release.yml` tests, builds and attaches the APK +
  karoo-ext `manifest.json` to a GitHub release (secrets: `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Release asset names feed
  `MANIFEST_URL` in the AndroidManifest — keep them stable.
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

Data flows one way: karoo-ext events → repos → pure state builder → overlay window.

- **`ClimberExtension`** (KarooExtension service, entry point): owns the
  `KarooSystemService` connection, wires everything, combines flows
  (route, distance-to-destination, settings, ride state, demo progress) at ~1 Hz and
  drives `OverlayController.show/hide`. Also reconciles `OnStreamState` consumers for
  whichever system data fields are configured (`reconcileSysStreams`).
- **`data/`** — `NavigationRepo` normalizes `OnNavigationState` (route or destination
  navigation) into `RouteData`, and sets `RouteData.rerouting` when Karoo sends a
  `rejoinDistance` (active reroute). `ProgressRepo` streams `DISTANCE_TO_DESTINATION`;
  progress along route = `totalDistance − distanceToDestination`. `ClimbStreamRepo`
  streams Karoo's native `CLIMB` / `CLIMB_NUMBER` data into `ClimbInfo` — the **same
  source the stock climber uses**, so on-climb detection matches it by construction. (Note:
  the stream's climb is one aggregate span, distinct from and coarser than the nav `climbs`
  list, which is the "climbs ahead" set.) `ElevationProfile` decodes Karoo's elevation
  polyline — **pairs of (distance, elevation), Google polyline, precision 1 (factor 10)**,
  unlike the route polyline (precision 5).
  `ClimbStateBuilder` is the pure state machine fusing these into `ClimbUiState`:
  - **Detection = the CLIMB stream.** A non-null `ClimbInfo` means a climb has started; on
    that transition the builder snapshots the climb's route span **once** from
    `progress − distanceFromBottom` and freezes it (stable silhouette; no per-frame DTD
    trust), then places the rider from the stream's `distanceFromBottom`/`distanceToTop`.
    Upcoming climbs + the approach state come from the nav `climbs` list + `progress`
    (trigger window). Topped climbs are recorded and persisted (`RideHistoryRepo`).
  - **Route-less** (live CLIMB stream, no route): a "predicted" climb in climb-relative
    coords with a null profile → the view draws a constant-grade **wedge**.
  - **Reroute** (`RouteData.rerouting`): the nav `climbs` list is re-based to distances
    **ahead of the point where Karoo computed the reroute** (not absolute route positions),
    and the fixed elevation profile no longer lines up — karoo-ext exposes **no reroute
    elevation polyline** (only `rejoinPolyline` geometry + `rejoinDistance`). So the builder
    switches to a rider-relative frame and progresses distance itself: the list is static
    between Karoo recomputes, but `progress` still advances 1:1 with the rider (routeDistance
    fixed, DTD falls), so `moved = progress − progressAtAnchor`, re-anchored only when Karoo
    re-bases the list (a new start value) — not on a prefix-drop as the rider passes a climb.
    Every climb draws as a **wedge**. On a climb *during* the reroute, the frame-independent
    CLIMB stream drives it via the route-less path so it's never skipped. Absolute logic +
    the real silhouette resume automatically on rejoin.
- **`overlay/`** — `OverlayController` owns the `TYPE_APPLICATION_OVERLAY` window
  (ki2 pattern: SYSTEM_ALERT_WINDOW permission + foreground service keepalive) and
  sizes it per `ViewModeMachine.PanelSize` (CHIP / EXPANDED drawer / FULL 92%).
  `ClimbOverlayView` is a single Canvas view rendering all states: chip variants,
  climb profile (cached per-climb geometry + fixed 100 m color chunks; silhouette
  `Path` rebuilt only when the visible window changes — battery matters on Karoo 2) —
  or, when the state carries a null profile (route-less / reroute), a constant-grade
  **wedge** built from the climb's grade/length in `rebuildClimbCache` instead of a
  polyline slice — sliding next-500 m chunk strip, climbs list, 2×2 data-field grid.
  `ViewModeMachine` holds display mode (Base/Alt/Preview round-robin) and panel size;
  gestures: tap = expand chip / cycle modes, swipe up/down = resize, drag scrolls the
  full-screen list. Hardware buttons: the window is key-focusable except in CHIP;
  bottom-left (KEYCODE_BACK) = shrink one step. The other three (page nav) can't be
  handed to Karoo while the overlay holds focus — replaying the press via
  `PerformHardwareAction` just loops the injected key back into the focused overlay.
  So on those presses the overlay drops focus (`forcedNonFocusable`), replays the
  press once RideActivity is focused (`onWindowFocusChanged(false)`), then re-takes
  focus after `REFOCUS_DELAY_MS` — long enough for the injected key to land first,
  short enough that bottom-left still collapses. Replays re-enter as virtual keys and
  are filtered by `deviceId` so they don't start another hand-off.
- **`settings/`** — DataStore-backed `SettingsRepo`; `ClimbField` enum doubles as
  climb-derived fields (no `dataTypeId`) and Karoo system stream fields (with one).
  Compose UI only in `SettingsActivity` — the overlay deliberately avoids Compose.
- **`palette/GradePalettes`** — band values copied from Barberfish (Apache-2.0,
  attributed in NOTICE); palettes without negative bands fall back to
  `descentColor` for downhill chunks.

## Conventions / gotchas

- UI text on the overlay is unitless by design (native-Climber parity): distances as
  `1.4` (km) or `400` (m), grades bare numbers, Hammerhead glyphs (`ic_dist_to`,
  `ic_elev_gain`, `ic_done` vector drawables) instead of unit labels. Overlay text
  uses the Karoo's native IBM Plex Sans loaded from `/system/fonts`.
- `OverlayController.show()` must not trigger `updateViewLayout` before `addView` —
  the relayout hook is attached only after the window exists (past crash).
- WindowManager calls are main-thread; the extension scope runs on
  `Dispatchers.Main.immediate`.
- `ClimbStateBuilderTest` uses its own route fixture on purpose — keep it decoupled from
  `DemoData`, which changes freely for demo UX.
- Visual/native-design references live in the README screenshot table (`docs/img/`).

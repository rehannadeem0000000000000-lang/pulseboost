# PulseBoost Pro — Android app/game booster

A booster that actually does something: every privileged action runs through
the **Shizuku API** (`dev.rikka.shizuku:api:13.1.5` + `:provider:13.1.5`),
which gives this app a shell/root-grade process to issue real `am`, `pm`
and `settings` commands with. UI-built purely with framework widgets — no
support libraries — so the source stays small and auditable.

## What works, honestly

✓ **Force-stop chosen apps** (`am force-stop <pkg>`) — requires Shizuku
✓ **Clear other apps' caches/data** (`pm clear <pkg>`, confirmed in a dialog
  because it wipes data, plus `pm trim-caches 999G` for safe system-wide
  cache reclaim) — requires Shizuku
✓ **Trim memory of a specific package** (`am send-trim-memory <pkg> 80`) —
  requires Shizuku
✓ **Kill background processes of a chosen app** (`am kill <pkg>`) and of the
  whole device (`am kill-all`) — requires Shizuku
✓ **Toggle Battery Saver** (`settings put global low_power_mode 0/1`) —
  requires Shizuku; the switch re-reads the setting after writing
✓ **Toggle Do Not Disturb** (`NotificationManager.setInterruptionFilter`)
  after the user grants Notification Policy Access
✓ **Set screen refresh rate to the panel's maximum** via
  `WindowManager.LayoutParams.preferredDisplayModeId` + `Display.Mode`
  (API 23+, wrapped in try/catch; outcome is reported, not assumed)
✓ **Real telemetry**: `ActivityManager.MemoryInfo` (RAM, refreshed every 1
  s), `/proc/stat` jiffy deltas (per-core CPU %), `BatteryManager` sticky
  intent (level, temperature, voltage, charge state, health)
✓ **Game Mode API** (`android.app.GameManager`) on Android 12 (API 31)+ —
  the section is hidden on older releases and errors such as
  `SecurityException` on locked-down devices are shown verbatim
✓ **FPS overlay**: foreground service + `TYPE_APPLICATION_OVERLAY`,
  `Choreographer.FrameCallback` measures real frame delivery, draggable and
  dismissible, shows the live foreground package from `UsageStatsManager`
✓ **Per-app auto-boost profiles**: toggle any launcher app; a lightweight
  monitor service detects it entering the foreground via UsageStats and runs
  the boost sequence (records RAM delta into Settings)
✓ **Home screen widget**: live free-RAM readout refreshed on a 30-second
  self-rescheduling alarm plus a one-tap BOOST button
✓ **Signed release APK** built by `.github/workflows/build-apk.yml`,
  verified with `apksigner verify --verbose`, structurally gated
  (≥ 40 KB, `classes.dex`, `resources.arsc`, ZIP magic), then embedded into
  `src/data/apkPayload.ts` by `scripts/embed-apk.mjs`

✗ **Cannot change the CPU governor or clock speeds** — that needs root
  (`/sys/devices/system/cpu/cpu*/cpufreq`), which Shizuku does not grant
✗ **Cannot unlock 120 Hz on a 60 Hz panel** — `Display.Mode` only exposes
  what the hardware/driver supports
✗ **Requires the Shizuku app installed and started** — from the Play Store
  or GitHub (`moe.shizuku.privileged.api`), started once via root, or via
  `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
  (needed again after every reboot on the ADB path)
✗ **Widget cadence is subject to Doze** — the 30 s alarm chain can be
  stretched when the device is deeply idle; RAM re-reads resume on wake

## Layout

```
app/src/main/java/dev/pulseboost/pro/
  MainActivity.kt          five tabs: BOOST / STATS / APPS / OVERLAY / SETTINGS
  ShizukuManager.kt        permission flow + Shizuku.newProcess exec wrappers
  StatsReader.kt           MemoryInfo, /proc/stat, BatteryManager sampling
  GameModeHelper.kt        GameManager wrapper (API 31+), hidden below
  UsageDetect.kt           UsageStatsManager foreground detection
  BoostOverlayService.kt   Choreographer FPS overlay (draggable, dismissible)
  BoostMonitorService.kt   per-app profile engine (auto-boost on launch)
  BoostWidgetProvider.kt   home screen widget, 30 s refresh, boost button
  ProfileStore.kt          SharedPreferences persistence
app/src/main/res/
  layout/activity_main.xml · overlay_fps.xml · widget_boost.xml
  drawable/…               icons + overlay/widget backgrounds
  xml/boost_widget_info.xml
```

## Build

```bash
# needs JDK 17 + Android SDK 34 + build-tools 34.0.0
keytool -genkeypair -keystore release.keystore -alias pulseboost \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass pulseboost -keypass pulseboost \
  -dname "CN=PulseBoost Pro, OU=Mobile, O=PulseBoost, C=US"
./gradlew :app:assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/pulseboost-pro-1.0.0.apk
```

Keystore location/passwords can be overridden with the `PULSEBOOST_KEYSTORE`,
`PULSEBOOST_STORE_PASS`, `PULSEBOOST_KEY_ALIAS`, `PULSEBOOST_KEY_PASS`
environment variables (see `app/build.gradle`).

## Permissions declared

`SYSTEM_ALERT_WINDOW` · `FOREGROUND_SERVICE` ·
`FOREGROUND_SERVICE_SPECIAL_USE` · `POST_NOTIFICATIONS` ·
`QUERY_ALL_PACKAGES` · `PACKAGE_USAGE_STATS` ·
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` · `WRITE_SETTINGS` ·
`moe.shizuku.manager.permission.API_V23`

# Undertow

An Android anti-doomscrolling app: it watches for continuous scrolling in feed
apps and throws up a full-screen "take a breath" pause when a session runs long.
Friction, not a hard block — research on habit interventions says interrupts you
can dismiss get kept, hard blocks get uninstalled.

## How it works

- **`ScrollWatcherService`** — an `AccessibilityService` receiving
  `TYPE_VIEW_SCROLLED` events system-wide, filtered to the watched apps
  (Instagram, Facebook, TikTok, YouTube). For YouTube, only scrolling inside the
  Shorts surface counts (detected via `reel*` view resource ids), so normal
  videos and subscriptions are free.
- **`SessionTracker`** — pure-Kotlin state machine (unit-tested). A "doom
  session" accumulates while scroll events keep arriving in one app; a 60s gap
  or an app switch resets it. At the threshold (default 5 min) it fires an
  interrupt. Snoozes shrink each time: 60s → 30s → 15s.
- **`FrictionOverlay`** — full-screen pause drawn with
  `TYPE_ACCESSIBILITY_OVERLAY` (no extra permission): breathing circle, session
  length, and two exits — *"I'm done — take me out"* (goes Home, counts as a
  win) or *"A little longer"* (snooze).
- **`StatsStore`** — per-day per-app counters (minutes scrolled, interrupts,
  times you walked away) in SharedPreferences, 30-day retention, local only.
- **`MainActivity`** — Compose UI: permission onboarding, threshold slider,
  per-app toggles, today's stats.

Nothing scrolled past is read or stored; only scroll *events* and the app they
happened in are counted, entirely on-device.

## Build / test / install

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug testDebugUnitTest
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then open Undertow → "Open accessibility settings" → enable **Undertow scroll
watcher**. (Sideloaded apps may need "Allow restricted settings" under
App info ⋮ menu on Android 13+ before the toggle unlocks.)

## Known limitations / v2 ideas

- Instagram/Facebook count *all* scrolling, not just Reels — per-surface
  detection like YouTube's is possible with their view ids if wanted.
- TikTok is registered under both `com.zhiliaoapp.musically` and
  `com.ss.android.ugc.trill` (regional builds).
- Stats screen only shows today; 30 days of history are already stored, so a
  trend chart is an easy add.
- No "strict mode" (making the snooze button harder to hit over time within a
  session, or requiring a typed sentence).

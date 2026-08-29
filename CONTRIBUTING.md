# Contributing to Tapio

Thanks for helping out. Tapio is being built **one module at a time** — check the
roadmap in the [README](README.md#roadmap) for what's in flight.

## Ground rules

- **Respect the module boundary.** `core-nfc` handles the handshake only; it must
  never gain file-transfer code. File bytes travel over Wi-Fi Direct in
  `core-transfer`, never over NFC.
- **Keep the core framework-free.** Domain types, the wire codec and the APDU
  logic live in plain Kotlin so they can be unit-tested on the JVM. Android
  imports belong in `…/android/` packages.
- **New content types** (text, links, contacts) should slot in via
  `SharedContent` in `core-common` — no changes to the handshake or transfer code.
- **Prefer readability over cleverness.** Small named functions, KDoc on public
  APIs.
- **No heavy dependencies.** Open an issue to discuss before adding one.

## Before you open a PR

```bash
./gradlew detekt test
./gradlew :app:assembleDebug
```

- Add unit tests for any framework-free logic you touch (JUnit + MockK).
- Add KDoc to new public classes and functions.
- Keep commits focused; write why, not just what.

## Setup

JDK 17+, Android SDK (compileSdk 35). The Gradle wrapper is committed — use `./gradlew`.

CI (GitHub Actions) runs detekt, the unit tests and a debug build on every PR.

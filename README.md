# Tapio

**Share a photo or a video by touching two phones together.**

Tapio is a native Android app. You tap your phone against a friend's, a quick
animation plays, and the file lands on their device — with a clear "Save this?"
prompt on the other end. No accounts, no cloud, no QR codes.

> Status: early development. The NFC handshake layer is in place; the Wi-Fi Direct
> transfer and the UI are next. See the [roadmap](#roadmap).

---

## How it works

NFC is only fast enough to exchange a few hundred bytes, so Tapio uses it purely
as a **handshake**:

```
        ┌─────────────┐   1. tap (NFC / HCE)          ┌─────────────┐
        │   Sender    │ ───────────────────────────▶  │  Receiver   │
        │             │   SessionToken:               │             │
        │  has file   │   { sessionId, wifiDirectMac, │  wants file │
        │             │     deviceName, role, ... }   │             │
        │             │                               │             │
        │             │   2. Wi-Fi Direct connect     │             │
        │             │ ◀───────────────────────────▶ │             │
        │             │   3. file stream + checksum   │             │
        │             │ ═══════════════════════════▶  │             │
        └─────────────┘                               └─────────────┘
```

1. **Tap.** The sending phone acts as an NFC tag (Host Card Emulation); the
   receiving phone reads it in reader mode. They exchange a tiny `SessionToken`.
2. **Connect.** The token carries the sender's Wi-Fi Direct address, so the
   receiver joins the group directly — no discovery UI.
3. **Transfer.** The file streams over Wi-Fi Direct with live progress and a
   checksum check on arrival.

The NFC layer **never** carries file bytes — only the handshake. This is enforced
by the module boundary: `core-nfc` has no file-transfer code.

> **A note on "Android Beam".** True NFC peer-to-peer (`android.nfc.NdefPush`) was
> deprecated in Android 10 and removed later. Tapio uses the modern equivalent:
> Host Card Emulation on the sender + reader mode on the receiver. Same tap, same
> feel, supported path.

---

## Modules

| Module        | Type              | Responsibility |
|---------------|-------------------|----------------|
| `core-common` | Kotlin/JVM library | Shared domain types. `SharedContent` is the extension point for future content kinds (text, links, contacts). |
| `core-nfc`    | Android library    | The tap. Session-token model, wire codec, APDU dialect, HCE service, reader-mode scanner, and testable interfaces (`NfcTokenAdvertiser` / `NfcTokenScanner`). |
| `core-transfer` | *(planned)*      | Wi-Fi Direct discovery, connection, chunked file transfer, checksum. |
| `app`         | Android application | Jetpack Compose UI, MVVM, permission flows. Currently a thin shell showing NFC status. |

Dependency direction is strictly one-way: `app → core-transfer → core-nfc → core-common`.

### `core-nfc` at a glance

```
domain/          SessionToken, HandshakeRole, NfcAvailability, HandshakeError, HandshakeOutcome
SessionTokenCodec   pure encode/decode of the NFC payload  (unit-tested)
apdu/ApduProtocol   the ISO 7816-4 dialect the HCE service speaks  (unit-tested)
NfcTokenAdvertiser / NfcTokenScanner   the two sides of the handshake (interfaces)
HandshakeCoordinator   availability guard + outcome stream for the UI  (unit-tested)
testing/FakeNfcHandshake   in-memory implementation for tests, previews, non-NFC devices
android/         AndroidNfcAvailabilityChecker, HceTokenAdvertiser, TapioHostApduService,
                 ReaderModeTokenScanner   (the platform glue)
```

---

## Building

**Requirements:** JDK 17+, Android SDK (compileSdk 35). Gradle 8.11.1 comes via the
committed wrapper — just use `./gradlew`.

```bash
./gradlew test              # all unit tests (JVM, no emulator)
./gradlew :app:assembleDebug
./gradlew detekt            # static analysis (gates CI)
```

Point Gradle at your SDK with a `local.properties` file (auto-created by Android
Studio) or the `ANDROID_HOME` environment variable.

---

## Testing

Unit tests run on the JVM — no emulator needed. The framework-free core is where
the logic lives:

- `SessionTokenCodecTest` — round-trips, and every malformed-payload branch.
- `ApduProtocolTest` — APDU building/parsing.
- `HandshakeCoordinatorTest` — availability guarding (JUnit + MockK).

```bash
./gradlew test
```

---

## Roadmap

- [x] **1 · Project setup** — modules, version catalog, CI, detekt, this README.
- [x] **2 · `core-nfc`** — contact detection + session-token exchange, unit-tested.
- [ ] **3 · `core-transfer`** — Wi-Fi Direct connect, progress via `Flow`, checksum.
- [ ] **4 · UI/UX** — file picker, the tap animation, receiver arrival + save dialog, haptics.
- [ ] **5 · Permissions & resilience** — runtime permission flows, no-NFC fallback, timeouts.
- [ ] **6 · Ship** — instrumented tests, detekt gating in CI, KDoc, screenshots/GIFs, Play listing.
- [ ] Later content kinds: text, links, contacts (via `SharedContent`).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: one module at a time, keep the
NFC/transfer boundary clean, add tests for the framework-free code, run `detekt`.

## License

[Apache 2.0](LICENSE).

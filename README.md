# Tapio

**Share a photo, a video or a phone number by touching two phones together.**

Tapio is a native Android app. You tap your phone against a friend's, a quick
animation plays, and the item lands on their device — behind a clear "Accept?"
prompt. The other phone only needs Tapio *installed*: the tap wakes it. No
accounts, no cloud, no QR codes.

> Status: early development. The full pipeline (NFC handshake → Wi-Fi Direct
> transfer → accept → save) is implemented and unit-tested against in-memory
> fakes; the real NFC + Wi-Fi Direct backend needs on-device validation. See the
> [roadmap](#roadmap).

---

## How it works

NFC is only fast enough to exchange a few hundred bytes, so Tapio uses it purely
as a **handshake** — and the payload is never delivered until the receiver accepts:

```
        ┌─────────────┐   1. tap → NDEF (HCE)          ┌─────────────┐
        │   Sender    │ ───────────────────────────▶   │  Receiver   │
        │             │   SessionToken:                │  (app may   │
        │ has content │   { wifiDirectMac, deviceName, │   be closed │
        │             │     payloadSummary, ... }      │   — tap     │
        │             │                                │   wakes it) │
        │             │   2. "Marie veut partager      │             │
        │             │       · Un contact"  →  Accept │             │
        │             │   3. Wi-Fi Direct connect      │             │
        │             │ ◀────────────────────────────▶ │             │
        │             │   4. stream + SHA-256 trailer  │             │
        │             │ ═════════════════════════════▶ │             │
        │             │   5. save to gallery / contacts│             │
        └─────────────┘                                └─────────────┘
```

1. **Tap.** The sender emulates an **NDEF Type-4 tag** (Host Card Emulation).
   Android's built-in NDEF dispatch reads it and **launches Tapio on the receiver
   even if it was closed** (`NDEF_DISCOVERED` intent filter + Application Record).
   The two phones exchange a tiny `SessionToken` carrying a one-line summary of
   what is on offer — never the payload.
2. **Accept.** The receiver sees a prominent prompt (or a full-screen notification
   when the phone is locked): *"{name} veut partager · {summary}"* → Accept / Refuse.
3. **Connect.** The token carries the sender's Wi-Fi Direct address, so the
   receiver joins the group directly — no discovery UI.
4. **Transfer.** The content streams over Wi-Fi Direct in one pass with a SHA-256
   trailer both sides verify. A file lands in a staging area; a contact card is
   parsed in memory.
5. **Save.** File → "Save this file?" → MediaStore. Contact → "Save this contact?"
   → the system *add contact* screen, pre-filled with the name the sender chose.

The NFC layer **never** carries payload bytes — only the handshake. Even a
100-byte contact card travels over Wi-Fi Direct, so the accept-then-receive order
holds for everything. Enforced by the module boundary: `core-nfc` has no
transfer code.

> **A note on "Android Beam".** True NFC peer-to-peer (`android.nfc.NdefPush`) was
> deprecated in Android 10 and removed. Tapio uses the supported combination:
> HCE / NDEF Type-4 emulation on the sender, the platform NDEF dispatch on the
> receiver, and Wi-Fi Direct for the bytes.

---

## Modules

| Module        | Type              | Responsibility |
|---------------|-------------------|----------------|
| `core-common` | Kotlin/JVM library | Shared domain types. `SharedContent` (`File`, `ContactCard`) is the extension point for new content kinds; `ContactCardCodec`. |
| `core-nfc`    | Android library    | The tap. Session-token model + codec, APDU dialect, HCE `TapioHostApduService`, **NDEF Type-4 emulation** (`NdefHostApduService` / `TapioNdef`) so a tap wakes a closed app, reader-mode scanner, testable interfaces. |
| `core-transfer` | Android library  | The transfer. Wi-Fi Direct connect off the NFC token, single-pass streaming with `Flow` progress, SHA-256 verification. Handles files (MediaStore staging) and contact cards (parsed in memory) → `IncomingContent`. |
| `app`         | Android application | Jetpack Compose UI, MVVM. Home / Send (type chooser + contact form) / Receive (accept prompt → save dialog). Animations, haptics, incoming-transfer notification, `ContactSaver`. Runs end-to-end on one device via `FakeTransferBackend`. |

Dependency direction is strictly one-way: `app → core-transfer → core-nfc → core-common`.

### `core-nfc` at a glance

```
domain/          SessionToken (+ payloadSummary), NfcAvailability, HandshakeError, HandshakeOutcome
SessionTokenCodec   pure encode/decode of the NFC payload  (unit-tested)
apdu/ApduProtocol   the ISO 7816-4 dialect the custom HCE path speaks  (unit-tested)
NfcTokenAdvertiser / NfcTokenScanner   the two sides of the handshake (interfaces)
HandshakeCoordinator   availability guard + outcome stream for the UI  (unit-tested)
testing/FakeNfcHandshake   in-memory implementation for tests, previews, non-NFC devices
android/         StagedHandshake, TapioHostApduService (custom APDU, in-app fast path),
                 NdefHostApduService + TapioNdef (NDEF Type-4 → wakes a closed app),
                 ReaderModeTokenScanner, HceTokenAdvertiser
```

### `core-transfer` at a glance

```
domain/          TransferState, TransferProgress, TransferResult, TransferError, ContentHeader (+ kind), Checksum
wire/            ContentHeaderCodec, TransferFraming (len-prefixed header + bytes + SHA-256 trailer), Sha256  (pure, tested)
FileSender / FileReceiver   the orchestrators — Flow<TransferState>, single streaming pass  (unit-tested)
IncomingContent  File (staged, save()/discard()) | Contact (parsed card) — awaiting the user's accept
WifiDirectConnector / FileSource / FileSink   the ports (interfaces)
testing/         InMemoryTransferChannel, FakeWifiDirectConnector, InMemoryFileSource/Sink
android/         WifiP2pConnector, ContentResolverFileSource, MediaStoreFileSink   (the platform glue)
```

On the wire (`TXFER2` header):

```
┌────────────┬──────────────┬────────────────────┬──────────────────────┐
│ int32 len  │ header (len) │ payload  (size)    │ SHA-256 trailer (32) │
│            │ kind|name|…  │ file OR contact TLV │                      │
└────────────┴──────────────┴────────────────────┴──────────────────────┘
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

### Trying it on one device

Install the debug APK and open the app. Because there is no second phone (and no
real NFC/Wi-Fi Direct backend yet), each screen has **"▶ Simuler…"** buttons that
stand in for the other device: on *Envoyer*, simulate a peer picking up the
content; on *Recevoir*, simulate an incoming **photo** or an incoming **contact** —
both play the accept prompt, the arrival animation, and the matching save dialog.

Point Gradle at your SDK with a `local.properties` file (auto-created by Android
Studio) or the `ANDROID_HOME` environment variable.

---

## Testing

Unit tests run on the JVM — no emulator needed. The framework-free core is where
the logic lives:

- `core-common` — `ContactCardCodecTest`.
- `core-nfc` — `SessionTokenCodecTest` (round-trips + every malformed branch),
  `ApduProtocolTest`, `HandshakeCoordinatorTest` (availability guarding, JUnit + MockK).
- `core-transfer` — `ContentHeaderCodecTest`, `TransferFramingTest`, `Sha256Test`,
  `FileSenderTest` / `FileReceiverTest` (files **and** contacts, failure paths),
  `TransferRoundTripTest` (sender output → receiver input, byte-for-byte).
- `app` — `SendViewModelTest` / `ReceiveViewModelTest` (type choice, contact entry,
  the accept-then-receive flow, save / decline).

```bash
./gradlew test
```

---

## Roadmap

- [x] **1 · Project setup** — modules, version catalog, CI, detekt, this README.
- [x] **2 · `core-nfc`** — contact detection + session-token exchange, unit-tested.
- [x] **3 · `core-transfer`** — streaming engine, `Flow<TransferState>`, SHA-256
      verification, receiver staging, in-memory fakes, unit-tested. Wi-Fi Direct
      `WifiP2pConnector` written but still needs on-device validation.
- [x] **4 · UI/UX** — brand identity (the Tapio mark, adaptive icon, splash screen +
      animated logo intro), Compose screens redesigned in a radial "Quick Share"
      idiom, MVVM ViewModels (tested), `RippleBeacon` + `RadialTransfer` animations,
      cross-faded states, the save dialog, haptics, single-device demo mode.
- [x] **5a · Content kinds + accept flow** — phone numbers (`SharedContent.ContactCard`)
      alongside photos/video; every transfer gated by an **accept prompt**; a tap
      wakes a closed app (NDEF Type-4 emulation + intent filter); incoming-transfer
      notification; contacts saved via the system *add contact* screen.
- [ ] **5b · Permissions & real backend** — runtime permission flows, the real NFC +
      Wi-Fi Direct backend wired into `TapioApplication`, no-NFC fallback, timeouts,
      on-device validation of `WifiP2pConnector` and `NdefHostApduService`.
- [ ] **6 · Ship** — instrumented tests, KDoc pass, screenshots/GIFs, Play listing.
- [ ] Later content kinds: text, links (via `SharedContent`).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: one module at a time, keep the
NFC/transfer boundary clean, add tests for the framework-free code, run `detekt`.

## License

[Apache 2.0](LICENSE).

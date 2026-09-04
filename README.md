# Tapio

**Share a photo, a video or a phone number by touching two phones together.**

Tapio is a native Android app. You tap your phone against a friend's; a card
slides up on their screen asking whether to accept, and the item lands on their
device. The other phone only needs Tapio *installed* — it never has to be open,
and Tapio never takes over their screen. No accounts, no cloud, no QR codes.

> Status: working end-to-end between two real phones (Galaxy S21 / A51) — tap,
> accept, transfer, verify, save. Rough edges remain: the first tap is sometimes
> missed and needs a second one, and Wi-Fi Direct timing is OEM-dependent. See
> the [roadmap](#roadmap).

---

## How it works

NFC is only fast enough to exchange a few hundred bytes, so Tapio uses it purely
as a **handshake** — and the payload is never delivered until the receiver accepts:

```
        ┌─────────────┐   1. tap → NDEF (HCE)          ┌─────────────┐
        │   Sender    │ ───────────────────────────▶   │  Receiver   │
        │             │   SessionToken:                │  (app may   │
        │ has content │   { wifiSsid, wifiPassphrase,  │   be closed │
        │             │     deviceName, summary, ... } │   — the tap │
        │             │                                │   wakes it) │
        │             │   2. Wi-Fi Direct group join   │             │
        │             │ ◀────────────────────────────▶ │             │
        │             │   3. preview: name, size,      │             │
        │             │      thumbnail             ──▶ │             │
        │             │   4. Accept / Refuse        ◀── │             │
        │             │   5. bytes + SHA-256 trailer   │             │
        │             │ ═════════════════════════════▶ │             │
        │             │   6. receipt ack            ◀── │             │
        │             │   7. save to gallery / contacts│             │
        └─────────────┘                                └─────────────┘
```

1. **Tap.** The sender emulates an **NDEF Type-4 tag** (Host Card Emulation).
   Android's tag dispatch launches Tapio on the receiver **even if it was closed**
   — via `NDEF_DISCOVERED` (mime `application/vnd.tapio.handshake`), falling back
   to `TECH_DISCOVERED` and an ISO-DEP read when the platform reserves the NDEF
   AID. Either way only a tiny `SessionToken` crosses, never the payload.
2. **Connect.** The sender's phone *is* the network: it creates a **non-persistent
   Wi-Fi Direct group** with credentials it picked itself, and the token carries
   the SSID + passphrase, so the receiver joins directly with no discovery UI. No
   router, no internet, no shared Wi-Fi — the two phones can even be on different
   mobile networks.
3. **Preview.** The sender streams a small preview first: real name, size, and a
   JPEG thumbnail for photos and videos.
4. **Decide.** A card appears over whatever the receiver was doing: *"{name} veut
   vous partager · {summary}"* → Accept / Refuse. This is Tapio's own popup
   activity, not a system notification, and the screen behind it keeps working.
5. **Transfer.** The content streams in one pass with a SHA-256 trailer both sides
   verify. A file lands in a staging area; a contact card is parsed in memory.
6. **Confirm.** The receiver acknowledges receipt before the sender reports success
   and tears the link down — "sent" always means "received and verified". The group
   is removed on every outcome; the next transfer needs a fresh tap.
7. **Save.** File → "Enregistrer ce fichier ?" → MediaStore. Contact → "Ajouter ce
   contact ?" → the system *add contact* screen, pre-filled with the name the
   sender chose.

The NFC layer **never** carries payload bytes — only the handshake. Even a
100-byte contact card travels over Wi-Fi Direct, so the accept-then-receive order
holds for everything. Enforced by the module boundary: `core-nfc` has no
transfer code.

**One transfer at a time.** A phone is committed to a single Wi-Fi Direct group,
so while a transfer runs every other tap is turned away — a second phone, or the
same tap redelivered by the platform. The claim is released when the session ends.

> **A note on "Android Beam".** True NFC peer-to-peer (`android.nfc.NdefPush`) was
> deprecated in Android 10 and removed. Tapio uses the supported combination:
> HCE / NDEF Type-4 emulation on the sender, the platform tag dispatch on the
> receiver, and Wi-Fi Direct for the bytes.

---

## Modules

| Module        | Type              | Responsibility |
|---------------|-------------------|----------------|
| `core-common` | Kotlin/JVM library | Shared domain types. `SharedContent` (`File`, `ContactCard`) is the extension point for new content kinds; `ContactCardCodec`. |
| `core-nfc`    | Android library    | The tap. Session-token model + codec, APDU dialect, HCE `TapioHostApduService`, **NDEF Type-4 emulation** (`NdefHostApduService` / `TapioNdef`) so a tap wakes a closed app, `TapioTagReader` for the ISO-DEP read. |
| `core-transfer` | Android library  | The transfer. Wi-Fi Direct group host/client off the NFC token, preview + accept handshake, single-pass streaming with `Flow` progress, SHA-256 verification and receipt ack. Files (MediaStore staging) and contact cards (parsed in memory) → `IncomingContent`. |
| `app`         | Android application | Jetpack Compose UI, MVVM. Home / Send (media, or a contact from the address book) plus the incoming-transfer popup. Animations, haptics, permission gate, one-session-at-a-time lock, `ContactSaver`. Runs end-to-end on one device via `FakeTransferBackend`. |

Dependency direction is strictly one-way: `app → core-transfer → core-nfc → core-common`.

### `core-nfc` at a glance

```
domain/          SessionToken (v3: ssid + passphrase + payloadSummary), NfcAvailability,
                 HandshakeError, HandshakeOutcome
SessionTokenCodec   pure encode/decode of the NFC payload  (unit-tested)
apdu/ApduProtocol   the ISO 7816-4 dialect the custom HCE path speaks  (unit-tested)
android/         StagedHandshake        the one token currently on offer
                 TapioHostApduService   custom AID, read by TapioTagReader
                 NdefHostApduService    NDEF Type-4 (+ TapioNdef) → wakes a closed app
                 TapioTagReader         SELECT + READ over ISO-DEP, retried on a lost field
                 HceTokenAdvertiser     stages the token; isAdvertising marks us the sender
```

### `core-transfer` at a glance

```
domain/          TransferState, TransferProgress, TransferResult, TransferError,
                 ContentPreview, ContentHeader, Checksum
wire/            PreviewCodec (TPREV1), ContentHeaderCodec (TXFER2), TransferFraming,
                 Sha256   (pure, tested)
FileSender / FileReceiver   the orchestrators — Flow<TransferState>, two-phase  (unit-tested)
IncomingContent  File (staged, save()/discard()) | Contact (parsed card)
WifiDirectConnector / FileSource / FileSink   the ports (interfaces)
testing/         InMemoryTransferChannel, FakeWifiDirectConnector, InMemoryFileSource/Sink
android/         WifiP2pTransport (WifiP2pHost + WifiP2pClient), SocketTransferChannel,
                 ContentResolverFileSource, MediaStoreFileSink
```

On the wire: one TCP socket to the group owner (`192.168.49.1:8988`), two phases.

```
  phase 1 — ask
  sender ──▶   int32 len │ preview   TPREV1|name|mime|size|b64(jpeg)
         ◀──   1 byte    │ 0x06 accept · 0x18 refuse

  phase 2 — send (only if accepted)
  sender ──▶   int32 len │ header    TXFER2|kind|name|mime|size
               size      │ payload   file bytes OR contact TLV
               32 bytes  │ SHA-256 trailer
         ◀──   1 byte    │ 0x06 receipt ack
```

---

## Building

**Requirements:** JDK 17+, Android SDK (compileSdk 35, minSdk 26). Gradle 8.11.1
comes via the committed wrapper — just use `./gradlew`.

```bash
./gradlew test              # all unit tests (JVM, no emulator)
./gradlew :app:assembleDebug
./gradlew detekt            # static analysis (gates CI)
```

Point Gradle at your SDK with a `local.properties` file (auto-created by Android
Studio) or the `ANDROID_HOME` environment variable.

**On a real pair of phones**, both need Tapio installed, NFC on, and Wi-Fi
switched on — it does *not* need to be connected to anything, mobile data is
fine. The real backend needs **Android 10+** for Wi-Fi Direct connect-by-
credentials; `TapioApplication` falls back to the in-process backend below that.
Tapio asks for `NEARBY_WIFI_DEVICES` (location before Android 13) and, when you
share a number, `READ_CONTACTS`.

### Trying it on one device

Install the debug APK and open the app. With no second phone, the home screen
offers **"▶ Simuler…"** buttons that stand in for the other device: simulate an
incoming **photo** or an incoming **contact** to play the popup, the accept, the
arrival animation and the save step; on *Envoyer*, simulate a peer picking up the
content.

### Watching a real transfer

Both sides log their half of the handshake:

```bash
adb logcat -s TapioReceive TapioHce TapioWifiP2p TapioWire
```

`TapioWire` traces every frame — preview, decision, header, bytes, receipt — so a
failure says which step broke rather than just "connection lost".

---

## Testing

Unit tests run on the JVM — no emulator needed. The framework-free core is where
the logic lives:

- `core-common` — `ContactCardCodecTest`, `SharedContentTest`.
- `core-nfc` — `SessionTokenCodecTest` (round-trips + every malformed branch),
  `ApduProtocolTest`.
- `core-transfer` — `PreviewCodecTest`, `ContentHeaderCodecTest`,
  `TransferFramingTest`, `Sha256Test`, `FileSenderTest` / `FileReceiverTest`
  (files **and** contacts, decline and failure paths), `TransferRoundTripTest`
  (sender output → receiver input, byte-for-byte).
- `app` — `SendViewModelTest` (type choice, the send flow through to Sent),
  `ActiveTransferTest` (one transfer at a time).

```bash
./gradlew test
```

---

## Roadmap

- [x] **1 · Project setup** — modules, version catalog, CI, detekt, this README.
- [x] **2 · `core-nfc`** — contact detection + session-token exchange, unit-tested.
- [x] **3 · `core-transfer`** — streaming engine, `Flow<TransferState>`, SHA-256
      verification, receiver staging, in-memory fakes, unit-tested.
- [x] **4 · UI/UX** — brand identity (the Tapio mark, adaptive icon, splash screen +
      animated logo intro), Compose screens in a radial "Quick Share" idiom, MVVM
      ViewModels (tested), `RippleBeacon` + `RadialTransfer` animations, cross-faded
      states, haptics, single-device demo mode.
- [x] **5a · Content kinds + accept flow** — phone numbers (`SharedContent.ContactCard`)
      picked from the address book alongside photos/video; every transfer gated by an
      accept prompt; contacts saved via the system *add contact* screen.
- [x] **5b · Real backend** — `AndroidTransferBackend`: HCE advertiser, a sender-created
      non-persistent Wi-Fi Direct group whose credentials ride in `SessionToken` v3, and
      a receiver joining by those credentials. Runs between two real phones.
- [x] **5c · Receive without the app** — the Receive screen is gone. A tap wakes Tapio
      in the background and it shows its own animated popup over whatever is on screen
      (`TransferPromptActivity`), with a thumbnail preview before accepting.
- [x] **5d · Resilience** — preview/decision/receipt-ack protocol so "sent" means
      "verified"; per-stage timeouts and retries around Wi-Fi Direct; stale P2P state
      cleared before every join; full session teardown on every outcome so retrying
      works; one transfer at a time; frame-level logging.
- [ ] **6 · Ship** — make the first tap reliable (the NDEF read still falls back to
      `TECH_DISCOVERED` on some devices), instrumented tests, KDoc pass,
      screenshots/GIFs, Play listing.
- [ ] Later content kinds: text, links (via `SharedContent`).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: one module at a time, keep the
NFC/transfer boundary clean, add tests for the framework-free code, run `detekt`.

## License

[Apache 2.0](LICENSE).

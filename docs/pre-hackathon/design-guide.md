# BobCar: Complete Build Guide (corrected)

> **Revision 9, 2026-10-26.** This is a corrected, de-duplicated rewrite of `car-app-complete-guide.md`, which merged 19 source documents.
>
> - Every section now agrees with every other section, and superseded material was removed instead of kept beside its replacement.
> - Android platform claims were re-checked against developer.android.com and AOSP source on 2026-10-02.
> - **Revision 3 moves weather, the "Hey Bob" hotword, and "Choose your AI" out of V1 into release 1.1** (Phase 5).
> - **Revision 4 settles the open decisions.** "Go home" uses the Home saved in Google Maps, so BobCar stores no address. Weather asks for location first, then a ZIP by voice if the user says no.
> - **Revision 5 makes BobCar work in any car, with or without Bluetooth or an adapter:**
>   - Support for cars without Bluetooth and for calls-only Bluetooth cars.
>   - Four OBD adapter connection types.
>   - VIN decoding.
>   - Extended data for every make, from OBDb.
>   - Clearing trouble codes moved to the wishlist.
> - **Revision 6 adds VIN typing, barcode scanning, and decoding to release 1.1** (Phase 5f).
> - **Revision 7 applies the pre-build review** (Appendix A.12):
>   - Supported vehicles are those from 1996 and newer, a soft limit.
>   - Fixes for audio, call handling, and cars without Bluetooth.
>   - New debug tools and release engineering.
> - **Revision 8 adds the data-sharing rule (§8.4):**
>   - Nothing leaves the phone unless a feature needs it.
>   - Every flow that does is minimized, disclosed first, and linked to where the user can ask for removal.
> - **Revision 9 adds the Android Auto choice and plans an iPhone release** (Appendix A.14):
>   - In an Android Auto car, the driver picks Android Auto or BobCar, once per car (M1.6).
>   - BobCar will also launch on iPhone, with the same per-car choice for CarPlay (Part 6).
>   - Phase 0 keeps the shared logic in a pure-Kotlin `:core` module, so the iPhone app can reuse it.
>
> [Appendix A](#appendix-a-corrections-and-decisions-log) lists every correction, plus every decision made on the owner's behalf so it can be reversed.

**Owner:** Timothy · **Working name:** BobCar · **Package (placeholder):** `dev.timothy.carvoice`
**Test device:** ASUS Zenfone 8 (Android 13, API 33). The iPhone version (Part 6) needs an iPhone on iOS 17 or newer.
**Test vehicles:**
- 2009 Ford Taurus: Ford SYNC Gen 1 Bluetooth, CAN.
- 2004 Ford F-350: no factory Bluetooth, J1850 PWM. Used for OBD and no-Bluetooth testing.

BobCar itself isn't tied to any make.

## How to use this guide

For the implementing agent (Claude Code):

1. Read Part 1 and Part 2 before writing code. Part 2 holds the **only** definitions of the data model ([§7](#7-data-model-canonical)), permissions and manifest ([§8](#8-permissions-and-manifest-canonical)), and the command grammar (M2.5). Never invent a field, key, or status value. If one is missing, add it to §7 first and use it from there.
2. Build in the phase order of [Part 3](#part-3-build-plan). Each phase names the mechanic sections (Part 4) it implements.
3. **Build V1 only (Phases 0–4) until it's live on Play.**
   - Sections marked **release 1.1** are specified now but built later: weather (M9.1–M9.2), the hotword (M10), "Choose your AI" (M12), driving detection for cars without Bluetooth (M1.10), and VIN entry and scan (M13.6). Release 1.1 is Phase 5, sub-phases 5a–5f.
   - M14, M15, and Part 5 are post-V1 and hardware-gated. V1 contains only the stubs described in [§6.5](#65-v1-stubs-for-hardware-gated-features).
   - Part 6 (iPhone) is planned but not scheduled. Its only V1 work is Phase 0's `:core` module.
4. Two labels mark facts that need checking:
   - **Verify in the car:** an assumption about a test vehicle (the Taurus with SYNC Gen 1, or the F-350). The in-car test plans must confirm it.
   - **Verify at build time:** a third-party fact (price, free tier, library version, model name) that changes over time. Check it when you build.

## Contents

- [Part 1: Product](#part-1-product)
  - [1. Concept and V1 definition of done](#1-concept-and-v1-definition-of-done)
  - [2. Scope by version](#2-scope-by-version)
  - [3. Known limits](#3-known-limits)
  - [4. Open decisions for the owner](#4-open-decisions-for-the-owner)
- [Part 2: Technical foundation](#part-2-technical-foundation)
  - [5. Tech stack](#5-tech-stack)
  - [6. Architecture](#6-architecture)
  - [7. Data model (canonical)](#7-data-model-canonical)
  - [8. Permissions and manifest (canonical)](#8-permissions-and-manifest-canonical)
  - [9. UX flows](#9-ux-flows)
- [Part 3: Build plan](#part-3-build-plan)
  - [10. Hard constraints](#10-hard-constraints)
  - [11. V1 phases](#11-v1-phases)
  - [12. Release 1.1 phases](#12-release-11-phases)
  - [13. Post-V1 phases (hardware-gated)](#13-post-v1-phases-hardware-gated)
  - [14. Phase ordering and what can slip](#14-phase-ordering-and-what-can-slip)
- [Part 4: Mechanics](#part-4-mechanics)
  - [M1. Bluetooth auto-launch](#m1-bluetooth-auto-launch)
  - [M2. Voice loop and command parser](#m2-voice-loop-and-command-parser)
  - [M3. Capability check](#m3-capability-check)
  - [M4. Car UI](#m4-car-ui)
  - [M5. Command: calls](#m5-command-calls)
  - [M6. Command: texting](#m6-command-texting)
  - [M7. Command: music control](#m7-command-music-control)
  - [M8. Command: navigation](#m8-command-navigation)
  - [M9. Command: weather and time](#m9-command-weather-and-time)
  - [M10. Hotword activation](#m10-hotword-activation)
  - [M11. Steering-wheel voice button](#m11-steering-wheel-voice-button)
  - [M12. AI provider choice](#m12-ai-provider-choice)
  - [M13. Vehicle profiles and settings screens](#m13-vehicle-profiles-and-settings-screens)
  - [M14. OBD-II diagnostics (post-V1)](#m14-obd-ii-diagnostics-post-v1)
  - [M15. Make-specific extended data (post-V1)](#m15-make-specific-extended-data-post-v1)
- [Part 5: Hardware wishlist](#part-5-hardware-wishlist)
- [Part 6: iPhone release (planned)](#part-6-iphone-release-planned)
- [Appendix A: Corrections and decisions log](#appendix-a-corrections-and-decisions-log)

---

## Part 1: Product

### 1. Concept and V1 definition of done

BobCar is a free, native Android app (an iPhone version is planned, Part 6) that turns the phone into a **universal car companion: voice control now, car diagnostics later, for anyone, in any car that can support it, with or without an adapter.** It isn't tied to any make. The app checks each car and stores a per-vehicle profile of what that car actually supports.

**Supported vehicles (soft limit): 1996 and newer.**
- That's when US law required the standard diagnostics port (OBD-II). The phone can't sense the port, so BobCar decides from the model year.
- An older vehicle gets "This vehicle isn't supported. BobCar works with cars from 1996 and newer." The user can still continue.
- Outside the US, 1996–2003 vehicles get "may not be fully supported" (§9.1, M13.1).
- User-facing text never says "OBD-II" (M4.8).

The owner's test vehicles:
- **2009 Ford Taurus:** SYNC Gen 1 Bluetooth, CAN.
- **2004 Ford F-350:** no factory Bluetooth, J1850 PWM.

Two pillars:

1. **Voice control (V1, phone-side, any car).** BobCar brings up a big-button, voice-first car screen that handles calls, texts, music transport, navigation, and time. Weather arrives in release 1.1.
   - **With Bluetooth,** car mode comes up automatically when the phone connects.
   - **Without Bluetooth,** the user opens car mode from the launcher. Release 1.1 adds driving detection to offer it automatically.
   - BobCar works around a car's built-in voice system (such as SYNC) without touching it.
   - Ways to start listening:
     - Tap-to-talk, always available.
     - The steering-wheel voice button, on cars that forward it to the phone (the Taurus doesn't).
     - Release 1.1: an optional "Hey Bob" hotword, on-device and free.
2. **Diagnostics (post-V1, needs an OBD-II adapter).** Most gas and diesel cars since 1996 in the US (later elsewhere) speak OBD-II. With an adapter that connects by classic Bluetooth, Bluetooth Low Energy (BLE), Wi-Fi, or USB, the app:
   - Reads trouble codes.
   - Shows live sensor data.
   - Reads and decodes the VIN.
   - Checks emissions readiness.

   Extended data for any make comes from the open OBDb community database. **Clearing codes is a wishlist item, not planned** (M14.1).

**V1 definition of done:**

- **Auto-launch.** The phone is paired to the Taurus. Turning the ignition on brings up car mode by itself, within 5 s of the `ACTION_ACL_CONNECTED` broadcast:
  - If "Display over other apps" is allowed, the car screen opens.
  - Otherwise a "Tap to open car mode" notification appears (Android 10+ blocks background screen launches; see M1).
  - From ignition to launch takes at most 30 s, because SYNC Gen 1's own Bluetooth handshake dominates.
- **Disconnect.** Car mode exits 8 s after the Bluetooth link drops (the delay absorbs SYNC reconnect blips), with a spoken "Disconnected".
- **Cars without Bluetooth.** First run offers "My car has no Bluetooth". That vehicle's car mode opens from the launcher, and everything else works on the phone's own mic and speaker.
- **Calls-only Bluetooth cars.** BobCar checks whether the car plays phone audio. If it can't, BobCar speaks from the phone speaker and says so in the capability report.
- **Android Auto cars.** The first time Android Auto runs in a car, BobCar asks once, by notification, whether to use Android Auto or BobCar in that car. The answer is saved per car and can be changed on the vehicle's screen (M1.6).
- **Make guess.** BobCar guesses the make from the car's Bluetooth name, and the user confirms or changes it.
- **Support check.** Setup asks for the model year. A pre-1996 vehicle sees the soft "isn't supported" message.
- **Tap-to-talk.** The Talk button starts listening. Every response is spoken and shown as large text.
- **Voice-started commands.** These work end-to-end from one utterance:
  - "call [contact]": opens the dialer pre-filled; the driver taps Call.
  - "text [contact] [message]": opens the messaging app pre-filled; the driver taps Send.
  - "play", "pause", "next", "previous".
  - "navigate to [place]".
  - "go home": hands Google Maps its own saved Home.
  - "what time is it".
  - "help".
  - "check engine": gives an honest "needs an adapter" reply in V1.
  - "what's the weather": replies "Weather is coming in a later update." in V1.
- **Car UI.** Usable at arm's length with huge buttons and high contrast. It has day and night themes, keeps the screen on, and has no text entry anywhere in car mode.
- **Capability report.** A green/amber/red/gray report exists for each vehicle. For the Taurus:
  - Wheel button: red, "not supported by this car".
  - OBD: amber, "needs adapter (wishlist)".
  - Probes never run silently in a way that changes results, and the app never nags.
- **Free and self-contained.** The app ships with zero API keys and zero paid services, and has no servers of its own. V1 makes no network calls of its own and doesn't declare internet access. It needs no extra hardware.
- **Private by default.**
  - No user data goes to a third party unless a feature needs it.
  - Every flow that does is minimized, disclosed before the first send, and linked to where the user can ask that party to remove it (§8.4).
  - There are no analytics, crash-reporting, advertising, or tracking SDKs, and no cloud backup.
- **Released.** It passes Google Play review and publishes under the owner's existing developer account.

**Release 1.1 adds** (each feature is off until the user sets it up):
- **Weather** for the driver's area.
- **The "Hey Bob" hotword.** It uses the phone mic by default; the car mic is an experimental per-car option.
- **"Choose your AI":** conversational answers using the user's own provider key.
- **Driving detection** for cars without Bluetooth: a "Driving? Tap for car mode" notification.
- **Car-speaker voice on calls-only cars:** BobCar can speak through the car's call channel.
- **VIN entry and scan:** type the VIN or scan its barcode (scanning happens on the phone). BobCar decodes the exact make, model, and year for the user to confirm, so the Bluetooth-name guess becomes a fallback.

### 2. Scope by version

| Feature | Release | Spec |
|---|---|---|
| Bluetooth auto-launch (screen or one-tap notification) | V1 | M1 |
| Android Auto cars: per-car choice of Android Auto or BobCar | V1 | M1.6 |
| Cars without Bluetooth: manual car mode | V1 | M1.10 |
| Cars without Bluetooth: driving detection | **1.1** (Phase 5e) | M1.10 |
| Calls-only Bluetooth cars: audio check, phone-speaker fallback | V1; car call-channel voice in **1.1** (Phase 5b) | M3.3 |
| Make guess from the Bluetooth name, user-confirmed | V1 (the fallback from 1.1 on) | M13.1 |
| VIN typing, barcode scan, and decoding | **1.1** (Phase 5f) | M13.6 |
| Tap-to-talk voice loop and command parser | V1 | M2 |
| Calls, texts, music, navigation, time | V1 | M5–M8, M9.3 |
| Capability check and compatibility report | V1 | M3 |
| Car UI | V1 | M4 |
| Vehicle profiles and settings | V1 | §7, M13 |
| Steering-wheel button, on cars that forward it | V1 | M11 (the Taurus doesn't forward it) |
| Weather | **1.1** (Phase 5a). V1 replies "coming in a later update" | M9.1–M9.2 |
| "Hey Bob" hotword | **1.1** (Phase 5b), off by default | M10 |
| Choose your AI (bring your own key) | **1.1** (Phase 5c), off by default | M12 |
| OBD-II diagnostics (read-only), adapters over classic Bluetooth, BLE, Wi-Fi, or USB, VIN read from the car | Post-V1, Phase 6 (needs an adapter) | M14 |
| Extended data for any make (OBDb) | Post-V1, Phase 7 | M15 |
| Clearing trouble codes | **Wishlist, not planned**: risk of misuse (M14.1) | — |
| Raw CAN interface, purpose-built companion device | Future R&D | Part 5 |
| iPhone app, with a per-car CarPlay-or-BobCar choice | Planned; timing decided when Android V1 ships | Part 6 |

### 3. Known limits

- **The built-in brain is a keyword parser, not a chatbot.** It matches a fixed command grammar (M2.5). Open-ended questions get the help prompt. From release 1.1, the user can turn on "Choose your AI" with their own key (M12).
- **V1 has no weather and no hands-free wake phrase.** Weather and "Hey Bob" arrive in release 1.1. On the Taurus, V1 means the app opens by itself and the driver taps Talk.
- **Calls and texts are voice-started, not hands-free.**
  - `ACTION_DIAL` opens the dialer and the driver taps Call.
  - `ACTION_SENDTO` pre-fills the messaging app and the driver taps Send.
  - Direct dialing needs `CALL_PHONE`. Automatic texting needs `SEND_SMS` plus default-SMS-app status. Both mean much harder Play review, so both are deliberately out.
- **Speech recognition usually needs internet.** `SpeechRecognizer` uses Google's servers unless the on-device language pack is installed. `EXTRA_PREFER_OFFLINE` is a preference, not a guarantee. In dead zones voice input degrades, but the big buttons keep working.
- **The app can't always open its screen by itself.** Android 10+ blocks background activity launches. BobCar opens the car screen automatically only if the user allows "Display over other apps", which is a documented exception. Otherwise it posts a one-tap notification. The capability report shows which mode is active.
- **The Taurus steering-wheel voice button can't launch BobCar.** This was settled by research on 2026-10-01:
  - The press is consumed inside the SYNC APIM. No Bluetooth `AT+BVRA` packet reaches the phone (that arrived with SYNC Gen 2 v3.8+, 2011+), no intent, no AppLink (which needed SYNC v4/v5, 2010+).
  - The only documented workaround is hardware: replace the head unit with an Android Auto/CarPlay unit, cut the SWI 2 wire, and route the button through an iDatalink Maestro or ASWC-1. That costs about $500–1,200+ and sacrifices factory SYNC.
  - On the Taurus, Bluetooth auto-launch replaces the button. M11 supports the button on cars that forward it.
- **The car mic pauses car-speaker music (release 1.1).** Listening through the car's microphone means holding the Bluetooth hands-free (SCO) audio link open. While SCO is open, Android suspends Bluetooth media audio (A2DP), and some head units show a call screen. That's why the hotword uses the phone mic by default and the car mic is an opt-in experiment (M10).
- **The hotword costs battery and needs a custom model (release 1.1).** The pretrained openWakeWord wake-word models are CC BY-NC-SA 4.0 (non-commercial), so a custom-trained "Hey Bob" model is mandatory for a Play Store release. It also needs on-car tuning.
- **BobCar can't control SYNC.** It can't remap SYNC, change its behavior, or read its state. SYNC stays paired for phone and media audio, and BobCar is the thing the driver talks to.
- **Bluetooth timing depends on the phone.** Some phone stacks delay or batch `ACL_CONNECTED`, and SYNC Gen 1's handshake can take 10–30 s.
- **The head unit may need its Bluetooth audio source selected (verify in the car).** Many older head units only play phone media audio when their source is set to Bluetooth Audio. If the radio is selected, spoken responses may be inaudible. On-screen text is always the fallback, and the in-car tests check this.
- **Diagnostics need hardware the owner doesn't have yet.** That's an OBD-II adapter (Part 5). Nothing in V1 assumes it exists.
- **Diagnostics don't reach every vehicle.**
  - Pre-1996 cars (OBD-I), heavy trucks (J1939), and motorcycles aren't supported.
  - EVs answer only part of the standard set.
  - Some 2018+ cars have security gateways that refuse manufacturer requests.
  - Older heavy pickups such as the F-350 may skip some standard readings (M14.1).
- **Cars without Bluetooth can add it.** A 12 V Bluetooth FM transmitter or aux adapter plays the phone's audio through the car radio, and many also carry calls. BobCar then treats the car as a Bluetooth car, including automatic launch.
- **A locked phone slows handoffs.** The dialer, messaging app, and Maps need the phone unlocked. First run suggests Android's trusted-device unlock with the car's Bluetooth (§9.1).
- **V1 is US-centric:** English, Fahrenheit, US ZIP codes, and US VIN decoding. Pick Play Console countries accordingly (§4).
- **The car's own USB ports can't connect BobCar to the car.** They only charge, play media, or run Android Auto. USB *OBD adapters* plugged into the phone do work (M14.3).
- **BobCar can't appear on the Android Auto screen.** Android Auto only accepts certain kinds of apps (navigation, places, smart-home, weather, media, and messaging; verify at build time), and a voice-command app isn't one of them. In an Android Auto car the driver picks Android Auto or BobCar, once per car (M1.6). Picking BobCar runs it on the phone's screen while Android Auto keeps the car's screen, and the wheel button still goes to Google's assistant (M11.3).

### 4. Open decisions for the owner

**Nothing blocks V1.**

**Decided (2026-10-03):**
- **Scope:** V1 is Phases 0–4. Weather, the hotword, and "Choose your AI" ship in release 1.1.
- **"Display over other apps":** ask the user during first run, with a notification fallback (§9.1).
- **"Go home":** hand Google Maps its own saved Home. BobCar stores no address (M8.2).
- **Weather location (1.1):** ask for approximate location the first time. If the user says no, ask for a ZIP by voice (M9.2).
- **Any car:** BobCar isn't tied to any make. It supports cars with or without Bluetooth, and with or without an adapter.
- **Clearing codes:** wishlist only (M14.1).
- **Make guess:** guess from the Bluetooth name, but the user always confirms or changes it.
- **VIN (1.1):** typing and barcode scanning give the exact vehicle, and the guess becomes a fallback.
- **Supported vehicles:** 1996 and newer, as a soft limit. Older vehicles see a message but aren't blocked.

**Decided (2026-10-26):**
- **Android Auto cars:** the driver picks Android Auto or BobCar, once per car (M1.6).
- **iPhone:** BobCar will also launch on iPhone, with the same choice for CarPlay (Part 6).

**Due later (not decisions now):**
- **Package name:** set the final `applicationId` before the first Play Console upload (Phase 4). It's one line in `build.gradle` until then and permanent after.
- **Build order after V1:** release 1.1 or diagnostics first. Decide when V1 ships.
- **iPhone timing and code sharing:** when to start Part 6, and whether to share code through Kotlin Multiplatform (recommended) or write a separate Swift app. Phase 0's `:core` module keeps both options open (Part 6, §2).
- **CarPlay screen:** whether to request Apple's voice-app entitlement (Part 6, §4).
- **OBD adapter:** see Part 5. For the F-350, buy a brand-name adapter that explicitly supports J1850 PWM.
- **Countries:** V1 is US-centric (see §3). The recommendation is to release in the US first.
- **App name:** search Play and the trademark records for "BobCar" before the listing (Phase 4).

---

## Part 2: Technical foundation

### 5. Tech stack

All choices are free, need no account, and need no API key.

**Versions:** use the versions Android Studio's New Project wizard generates, or the latest stable at build time, and record them in `gradle/libs.versions.toml`. This guide deliberately pins no versions (verify at build time).

| Layer | Choice | Notes |
|---|---|---|
| Language / IDE | Kotlin, Android Studio | |
| Shared logic | Gradle module `:core`: pure Kotlin, no `android.*` imports | §6.2. Can become a Kotlin Multiplatform module for the iPhone app (Part 6) |
| UI | Jetpack Compose + Material 3 (Compose BOM) | Big-button car UI; no XML layouts |
| Async | Kotlin coroutines + Flow | `StateFlow` for all UI state |
| Local database | Room (with the KSP compiler) | Vehicle profiles; DTC cache added in Phase 6 |
| Simple preferences | Jetpack DataStore (Preferences) | `settings` (§7.7); release 1.1 adds `secrets` (§7.8) |
| Tests | JUnit 4/5, kotlinx-coroutines-test, Turbine (Flow), Compose UI test (`ui-test-junit4`) | Unit tests for parsers and decoders; Compose UI tests for the car screen (no-text-field audit) |
| JSON | kotlinx.serialization (+ Kotlin serialization Gradle plugin) | Room TypeConverter and HTTP bodies; no reflection |
| Voice input | Framework `android.speech.SpeechRecognizer` | No dependency |
| Voice output | Framework `android.speech.tts.TextToSpeech` | No dependency |
| Car link | Framework `CompanionDeviceManager` | M1. Lets the car-mode service start from the background |
| Android Auto detection | `androidx.car.app:app`, **`CarConnection` only** | Detection only. **No** `CarAppService` is declared, so no automotive review track |
| HTTP (**release 1.1**) | OkHttp | Weather and AI providers; raw HTTPS, no vendor SDKs. V1 has no HTTP client |
| Location (**release 1.1**) | `play-services-location` (`FusedLocationProviderClient`) | Weather only; approximate (coarse) location; one-shot reads |
| Hotword (**release 1.1**) | ONNX Runtime for Android (`com.microsoft.onnxruntime:onnxruntime-android`) + openWakeWord models | M10. Custom-trained "Hey Bob" model |
| Secret storage (**release 1.1**) | Framework Android Keystore (AES-256-GCM) | §7.8, for AI keys. `androidx.security:security-crypto` was deprecated in 2025, so don't use it |
| Driving detection (**release 1.1**) | Google Play services Activity Recognition (Transition API) | M1.10. Cars without Bluetooth |
| OBD (post-V1) | `eltonvs/kotlin-obd-api` via JitPack; `mik3y/usb-serial-for-android` for USB adapters | M14 |
| VIN decoding (**release 1.1**) | NHTSA vPIC web service (free, no key) | M13.6; reused by M14.5 |
| VIN scanning (**release 1.1**) | ZXing on-device scanner (`com.journeyapps:zxing-android-embedded`) | M13.6. Runs entirely on the phone and sends nothing. Needs the camera permission, asked only on **Scan** |
| Extended vehicle data (post-V1) | OBDb community signal sets (CC BY-SA 4.0), a snapshot bundled in the app | M15. No per-vehicle downloads, so the car model is never revealed |

**Deliberately not used:**

- **Porcupine/Picovoice.** The free tier caps commercial use, and custom Android wake words need an enterprise license (verify at build time).
- **PocketSphinx.** Weaker accuracy and stale packaging.
- **`EncryptedSharedPreferences`.** Deprecated.
- **Vendor AI SDKs.** Raw HTTPS behind one interface is enough.
- **Google Maps SDK and Directions API.** They need a key and billing.
- **`PACKAGE_USAGE_STATS`.**
- **Analytics, crash-reporting, advertising, attribution, or tracking SDKs** (Firebase Analytics, Crashlytics, and so on). See §8.4.
- **Google code scanner (ML Kit).** ML Kit sends usage metrics to Google.

**SDK levels:**

- `minSdk 26` (Android 8.0). `java.time` is native from 26, so no desugaring is needed.
- `targetSdk`: whatever Play requires at submission time. It's a hard gate, so check Play Console.
- `compileSdk`: the latest available.
- Consequences of targeting 34+:
  - Every foreground service must declare a type, and the matching `FOREGROUND_SERVICE_*` permission.
  - Starting a microphone-type foreground service from the background throws `SecurityException`.
- Consequence of targeting 35+: edge-to-edge is enforced. Use `Scaffold`/`WindowInsets` so the status line never sits under the system bars.

### 6. Architecture

**One app, no backend, and no network services of its own.**

- **V1 makes no network calls of its own and doesn't declare `INTERNET`.** Speech recognition runs in the phone's system recognizer, which may use Google's servers on its own.
- **Release 1.1 adds three outbound destinations:**
  - Open-Meteo, for weather and ZIP geocoding.
  - The user's chosen AI provider, only when AI mode is on and consented.
  - NHTSA vPIC, for VIN decoding (the model part of the VIN only).
- **Phase 7 adds none.** OBDb data is bundled in the app.
- **Every outbound flow is listed** in the third-party register (§8.4).

#### 6.1 Component diagram

```
 TRIGGERS                         CAR LIFECYCLE                               UI
 ACL_CONNECTED ──► CarConnectionReceiver (manifest) ──► CarConnectionHandler ──┬─► CarModeActivity (if overlay allowed)
 ACL_DISCONNECTED                                         │                    └─► "Tap to open car mode" notification
 No-Bluetooth car: launcher tap (V1) · 1.1: DrivingDetector ──► "Driving? Tap for car mode" notification
                                                          ▼
                                     CarModeService (FGS, type connectedDevice) — owns the drive session
                                     CarSession (process singleton: active vehicle, connection, voice state)

 Talk button ──────────────────────────────────────┐
 "Hey Bob" (1.1: HotwordService, FGS microphone) ──┼──► VoiceController ──► VoiceEngine (SpeechRecognizer)
 Wheel button (VoiceCommandActivity) ──────────────┘        │                     │ transcript
                                                            │                     ▼
                                                            │               CommandParser ──► CommandExecutor ──► actions/*
                                                            │                     │ Unknown + AI mode on (1.1)
                                                            │                     ▼
                                                            │                  AiBrain ──► AiProvider (user's key)   [1.1]
                                                            ▼
                                                       TtsEngine + UiState.message (large text)

 DATA: AppDatabase (Room: vehicle_profiles; dtc_readings from Phase 6) · SettingsStore (DataStore "settings")
       1.1: KeyVault (DataStore "secrets", Keystore-encrypted)

 1.1: VinDecoder (NHTSA vPIC) · POST-V1, HARDWARE: ObdManager + 4 transports (classic BT, BLE, Wi-Fi, USB),
       OBDb signal sets; future: raw-CAN interface, companion device
```

#### 6.2 Package layout

```
dev/timothy/carvoice/
  BobCarApp.kt                 # Application: builds singletons (CarSession, repositories, AndroidAutoDetector)
  ui/
    MainActivity.kt            # launcher; NavHost: first-run, vehicles, vehicle/{id}, compat/{id}, settings, ai
    CarModeActivity.kt         # the car screen only (singleTask, portrait, showWhenLocked)
    VoiceCommandActivity.kt    # transparent trampoline for ACTION_VOICE_COMMAND (M11)
    screens/                   # Composables: CarScreen, MoreSheet, CompatReport, VehicleList, VehicleDetail, Settings, Permissions, PrivacyData, FirstRun; 1.1: AiSettings, VinScreen
    PermissionCopy.kt          # the only source of §8.3 permission texts
    ThirdPartyRegister.kt      # the only source of §8.4 third-party flows and their privacy/removal links
    theme/                     # CarTheme (day/night), typography minimums
  car/
    CarConnectionReceiver.kt   # manifest receiver for ACL_CONNECTED / ACL_DISCONNECTED
    CarConnectionHandler.kt    # match MAC → profile; start service; open screen or post notification
    CarModeService.kt          # foreground service (connectedDevice) for the duration of a drive
    CarSession.kt              # process-wide state: activeVehicle, connected, voice controller
    CompanionLink.kt           # CompanionDeviceManager association per vehicle
    AndroidAutoDetector.kt     # androidx.car.app CarConnection → isProjecting
    MakeGuesser.kt             # make guess from the Bluetooth name (M13.1); always user-confirmed
    SupportPolicy.kt           # 1996+ soft support rule from model year and country (M13.1)
    CallStateWatcher.kt        # V1: detects phone calls without READ_PHONE_STATE (M2.4); used by TTS and, in 1.1, the hotword
    AudioFocus.kt              # duck music while BobCar speaks; pause it while listening (M2.4)
    Notifications.kt           # channels + tap-to-open, action, and driving notifications
    DrivingDetector.kt         # RELEASE 1.1 (Phase 5e): activity-recognition trigger for cars without Bluetooth (M1.10)
  capability/
    CapabilityChecker.kt       # quick probe + full check (M3)
    probes/                    # VoiceProbe, AutoLaunchProbe, AudioRouteProbe, WheelButtonProbe, CarMicProbe, ObdPresenceProbe
    KnownVehicleNotes.kt       # bundled vehicle-specific notes (e.g. SYNC Gen 1 wheel button)
  voice/
    VoiceController.kt         # state machine + trigger arbitration
    VoiceEngine.kt             # SpeechRecognizer wrapper
    TtsEngine.kt               # TextToSpeech wrapper
    CommandParser.kt           # pure Kotlin grammar (M2.5) → Command
    Command.kt                 # sealed class
    CommandExecutor.kt         # Command → actions/* → SpokenResult
  hotword/                     # RELEASE 1.1 (Phase 5b)
    HotwordService.kt          # foreground service (microphone)
    HotwordEngine.kt           # interface
    OpenWakeWordEngine.kt      # ONNX: melspectrogram → embedding → wake-word model
    HotwordAudioSource.kt      # PHONE_MIC (default) | CAR_SCO (experimental)
    ScoLink.kt                 # SCO open/close (setCommunicationDevice on 31+, legacy below)
  actions/
    ContactResolver.kt  CallAction.kt  TextAction.kt  MediaAction.kt  NavAction.kt
    HomeAction.kt  TimeAction.kt  HelpAction.kt  DiagnoseAction.kt
    WeatherAction.kt           # V1: stub reply; real implementation in release 1.1 (Phase 5a)
    ExternalLauncher.kt        # starts other apps' activities; handles background-launch limits (M2.7)
  ai/                          # RELEASE 1.1 (Phase 5c)
    AiBrain.kt  AiProvider.kt  providers/  ConsentStore.kt  KeyVault.kt  UsageCounter.kt
  obd/
    ObdManager.kt              # V1: isAvailable() = false, no Bluetooth code (§6.5)
  data/
    AppDatabase.kt  VehicleProfile.kt  VehicleProfileDao.kt  Converters.kt  SettingsStore.kt  VehicleRepository.kt
    VinValidator.kt  VinDecoder.kt   # RELEASE 1.1 (Phase 5f): check digit + NHTSA vPIC; reused by Phase 6
```

The Phase 6 and 7 additions (`obd/…`, `obd/ext/…`, `DtcReading`) are listed in M14 and M15. Debug-only code lives in the `src/debug/` source set (§6.6).

**Shared `:core` module.** These files live in a separate Gradle module, `:core`, under the same package names: `CommandParser`, `Command`, `SupportPolicy`, `MakeGuesser`, and `VinValidator` (with the masked-VIN builder), and later the OBD response decoders, the DTC parser, and OBDb signal decoding (M14, M15).
- `:core` is pure Kotlin with no Android SDK, so an `android.*` import fails to compile. Anything platform-specific (the country code, the clock, storage) is passed in.
- The app module depends on `:core`. This costs almost nothing in V1 and lets the iPhone app (Part 6) reuse the same logic.

#### 6.3 Call graph

- **`CarConnectionReceiver.onReceive()`** calls `goAsync()`, then `CarConnectionHandler.onAclConnected(device)` or `onAclDisconnected(device)` on a coroutine, and calls `finish()` in `finally`.
- **`CarConnectionHandler.onAclConnected()`:**
  1. `VehicleRepository.findByMac(device.address)`. No match means it does nothing, or offers to add the car if `unknown_car_prompt` is on.
  2. If `AndroidAutoDetector.isProjecting`, it follows the car's `projectionChoice` (M1.6): it stops here for `"android_auto"` or `"ask"` (posting the question for `"ask"`), and continues for `"bobcar"`.
  3. `CarSession.enter(vehicle)`.
  4. It starts `CarModeService`, then opens `CarModeActivity` or posts the tap-to-open notification (M1.4).
- **`CarModeService.onStartCommand(ENTER)`:**
  1. `startForeground(…, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`.
  2. The TTS greeting.
  3. `CapabilityChecker.quickProbe(vehicle)` (M3).
  4. Registers a dynamic receiver for `BluetoothAdapter.ACTION_STATE_CHANGED`.
- **`CarModeActivity.onStart()` (release 1.1):** if `vehicle.hotwordEnabled` and the global hotword switch is on, it starts `HotwordService`. This must happen while the activity is visible; see M10.4.
- **All triggers** call `CarSession.voice.startListening()`: the Talk button, `VoiceCommandActivity`, and from 1.1 a `HotwordService` detection.
- **`VoiceController`** runs this sequence: `VoiceEngine` result → `CommandParser.parse()` → `CommandExecutor.execute(command)`. From 1.1, `Command.Unknown` with AI mode on goes to `AiBrain.handle()` instead. The result goes to `TtsEngine.speak()` plus `UiState.message`.
- **`CommandExecutor`** calls exactly one action class per command. Actions that open other apps go through `ExternalLauncher` (M2.7).
- **`CapabilityChecker`** writes `capabilityCheckResults` and the derived flags in one Room transaction (§7.6).

#### 6.4 Key interfaces

- `HotwordEngine { fun start(source: HotwordAudioSource); fun stop(); fun setThreshold(t: Float); var onDetection: () -> Unit }` (release 1.1). Porcupine could replace `OpenWakeWordEngine` later with one class swap (M10).
- `AiProvider` (release 1.1): see M12.3.
- `ObdTransport` (Phase 6): see M14.3. There are four implementations: classic Bluetooth, BLE, Wi-Fi, and USB.
- `SignalSource`, `SignalSet`, and `Signal` (Phase 7): defined once, in M15.3. V1 registers no signal source.
- `ObdManager.isAvailable(): Boolean`. This is the single gate. Everything diagnostics-shaped checks it first.

#### 6.5 V1 stubs for hardware-gated features

- `ObdManager.isAvailable()` always returns `false` in V1. **V1 contains no RFCOMM or ELM327 code.** Phase 6 adds it. This keeps the V1 build free of unreachable hardware code.
- `DiagnoseAction` speaks and shows: "Engine diagnostics need a small adapter that plugs in under the dashboard. That arrives in a later version."
- The `obd_link` probe in V1 only scans **bonded** device names (M3.3). It never opens a connection.
- The `extended_pids` probe in V1 always reports `NOT_TESTED` with the note "Needs a diagnostics adapter."
- The vehicle-detail screen shows a "Pair diagnostics adapter" row. In V1 it only lists bonded classic Bluetooth devices and saves `obdAdapterAddress` with `obdTransport = "spp"`, with the note "Diagnostics arrive in a later version."

#### 6.6 Development, debug, and logging (debug builds only)

The emulator has no Bluetooth, and nobody can test from a moving car all day. Everything in this section lives in the `src/debug/` source set, so none of it ships in release builds.

- **Debug screen** (`MainActivity` route `debug`, debug builds only):
  - Raw capability results.
  - The redacted log tail.
  - 1.1: hotword battery %/hour, the detection log, and AI key status (last 4 characters only).
- **Fake car:**
  - **Simulate connect** and **Simulate disconnect** for any profile. These call `CarConnectionHandler` through a test seam that takes a MAC instead of a `BluetoothDevice`.
  - **Simulate A2DP connected / calls-only** for the `audio_route` probe.
- **Fake speech.** A text box sends a typed transcript straight to `CommandParser`, skipping the recognizer. Text entry is allowed here because this isn't car mode.
- **Fake call.** Toggles the `CallStateWatcher` state.
- **OBD without a car (Phase 6).** Run an open-source ELM327 emulator on a PC (for example the Python `ELM327-emulator` project; verify at build time) and connect over the Wi-Fi transport using `host:port`.
- **Logging policy:**
  - Release builds never log contacts, phone numbers, what the user said, VINs, locations, or keys.
  - Debug builds redact them by default, with a debug-only switch to show them.

### 7. Data model (canonical)

This section is the only place these names, types, and defaults are defined.

#### 7.1 Room vs DataStore

- **Room:** `VehicleProfile`, and from Phase 6, `DtcReading`. These are structured and multi-row, they need migrations as fields are added, and `capabilityCheckResults` needs a TypeConverter.
- **DataStore (Preferences):** simple global key-value settings, in the file `settings`.
- **DataStore, encrypted values (release 1.1):** AI keys and tokens, in a separate file `secrets` (§7.8) that is excluded from backup.

#### 7.2 `VehicleProfile` (Room entity, table `vehicle_profiles`)

```kotlin
@Entity(
    tableName = "vehicle_profiles",
    indices = [Index(value = ["btMac"], unique = true)]
)
data class VehicleProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val btMac: String?,                         // car/head-unit Bluetooth MAC, UPPERCASE "AA:BB:CC:DD:EE:FF"; null = car without Bluetooth (M1.10)
    val btName: String,                         // bonded-device name at pairing time (e.g. "SYNC"), or the user's name for a no-Bluetooth car
    val make: String = "",                      // guessed from btName or the VIN; the user confirms or edits it
    val model: String = "",
    val year: Int = 0,                          // 0 = unknown
    val makeConfirmed: Boolean = false,         // true once the user confirms make/model/year; gates KnownVehicleNotes and OBDb matching
    val vin: String? = null,                    // V1: optional manual entry. 1.1: typed or scanned and decoded via vPIC (M13.6). Phase 6: read via OBD. Saved after the user confirms
    val vinDecodePending: Boolean = false,      // RELEASE 1.1: a VIN was saved offline and still needs decoding (M13.6)
    val cdmAssociationId: Int? = null,          // CompanionDeviceManager association; null = not associated (M1.3)
    val projectionChoice: String = "ask",       // "ask" | "android_auto" | "bobcar": what to do when Android Auto (or CarPlay, Part 6) runs in this car (M1.6)
    val projectionAsked: Int = 0,               // drives where the M1.6 question went unanswered; at 3, projectionChoice becomes "android_auto"
    val obdTransport: String = "none",          // "none" | "spp" | "ble" | "wifi" | "usb" (M14.3)
    val obdAdapterAddress: String? = null,      // MAC (spp/ble), "host:port" (wifi), or "vendorId:productId" (usb); never triggers car mode
    val obdProtocol: String = "not-tested",     // "not-tested" | "none" | raw ELM327 ATDP reply, e.g. "SAE J1850 PWM", "ISO 15765-4 (CAN 11/500)"
    val speakOverCallChannel: Boolean = false,  // RELEASE 1.1: on calls-only cars, speak through the car's call channel (M3.3)
    val wheelButtonSupported: Boolean = false,  // true only after the empirical test passes (M11)
    val wheelButtonEnabled: Boolean = false,    // the user's per-vehicle "Use steering-wheel button" switch (M11.4)
    val scoHoldsReliably: Boolean? = null,      // null = not tested; set by the car-mic test or the 3-strike drop rule (M10)
    val hotwordEnabled: Boolean = false,        // per vehicle; ANDed with settings key hotword_enabled_global
    val hotwordUseCarMic: Boolean = false,      // experimental; pauses car-speaker music while armed (M10)
    val wakePhrase: String = "Hey Bob",         // one of the bundled models: "Hey Bob" | "Hey Car" | "OK Bob" | imported custom
    val hotwordThreshold: Float = 0.5f,         // detection score threshold: 0.7 = low sensitivity, 0.5 = medium, 0.3 = high
    val capabilityCheckResults: Map<String, CheckResult> = emptyMap(),  // keys in §7.3
    val isDefaultVehicle: Boolean = false,      // exactly one profile is true; used for manual launch
    val createdAt: Long = System.currentTimeMillis()
)
```

Rules:

- **Normalize MAC addresses** to uppercase before writing or comparing. DAO lookups also use `COLLATE NOCASE`.
- **`btMac` may be null** for cars without Bluetooth. SQLite's unique index allows any number of nulls.
- **Never act on an unconfirmed guess.** Known-vehicle notes and OBDb matching use make, model, and year only when `makeConfirmed` is true.
- **`obdProtocol`** stores the raw `ATDP` text. Derive the transport with a helper: `fun isCan(p: String) = "15765" in p || "CAN" in p`.
- **Don't store "is default assistant".** M11 no longer needs the assistant role. Check the current `VOICE_COMMAND` handler at runtime instead (M11.4).
- **Support status is derived, not stored.** `SupportPolicy` computes it from `year` (and the phone's country): supported, not supported, may not be fully supported, or unknown (year 0). See M13.1.
- **The 1.1 fields are part of the V1 schema** (`scoHoldsReliably`, `hotwordEnabled`, `hotwordUseCarMic`, `wakePhrase`, `hotwordThreshold`, `speakOverCallChannel`, `vinDecodePending`). V1 never reads them and they keep their defaults, so release 1.1 needs no Room migration.

#### 7.3 `CheckResult`, `CheckStatus`, and probe keys

```kotlin
@Serializable enum class CheckStatus { PASS, WARN, FAIL, NOT_TESTED }

@Serializable data class CheckResult(
    val status: CheckStatus,
    val note: String,      // one-line, user-facing, honest; TTS and screen use the same string
    val checkedAt: Long    // epoch millis; 0 when NOT_TESTED
)
```

| Status | Dot | Meaning | Rule |
|---|---|---|---|
| `PASS` | green | Works on this car and phone | Only after an actual probe on this install |
| `WARN` | amber | **Needs X:** fixable by the user (permission, setting, adapter, VIN) | Must name the fix and offer a button for it |
| `FAIL` | red | **Not supported by this car** (or this phone) | Must say so plainly; no action button needed |
| `NOT_TESTED` | gray | Never probed | Note says "Not tested yet" plus how to test |

A missing adapter is `WARN`, never `FAIL`. Never show a `PASS` the app didn't measure.

**Probe keys (stable, snake_case).** These are the only valid keys in `capabilityCheckResults`:

| Key | Version | Written by | Drives flag |
|---|---|---|---|
| `voice` | V1 | M3 quick probe and full check | — |
| `auto_launch` | V1 | M3 (CDM association, overlay permission, notification permission; for cars without Bluetooth, manual or driving detection) | — |
| `audio_route` | V1 | M3 (does the car play phone audio, or only calls?) | — |
| `wheel_button` | V1 | M11 test (via M3 or settings) | `wheelButtonSupported` |
| `car_mic` | Release 1.1 (Phase 5b); hidden in V1 | M10 car-mic test | `scoHoldsReliably` |
| `obd_link` | V1: bonded-name scan only. Phase 6: real link | M3 / M14 | `obdProtocol` |
| `obd_headers` | Phase 6 | M14 | — |
| `obd_vin` | Phase 6 | M14 | `vin`, `make`, `model`, `year`, `makeConfirmed` (after the user confirms) |
| `obd_freeze_frame` | Phase 6 | M14 | — |
| `extended_pids` | V1: always `NOT_TESTED`. Phase 7: real | M15 | `supportedSignals` (M15.6) |

#### 7.4 Known-vehicle notes

`capability/KnownVehicleNotes.kt` holds a tiny bundled table of verified facts that pre-seed results when a profile matches. That way the owner never has to run a test that is known to fail.

| Match | Pre-seeded result |
|---|---|
| `makeConfirmed`, `make == "Ford"`, `year in 2008..2010`, `btName` starts with `"SYNC"` | `wheel_button = FAIL("SYNC Gen 1 does not forward the steering-wheel voice button to the phone (verified Oct 2026).")`, `wheelButtonSupported = false` |

Rules:

- Pre-seeded rows can still be re-tested manually; the button is labeled "Re-verify".
- Add an entry only when it has been verified on a real vehicle.

#### 7.5 TypeConverter

```kotlin
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    @TypeConverter fun fromResults(v: Map<String, CheckResult>): String = json.encodeToString(v)
    @TypeConverter fun toResults(s: String): Map<String, CheckResult> =
        if (s.isBlank()) emptyMap() else json.decodeFromString(s)
    // Phase 7 (database version 3): supportedSignals
    @TypeConverter fun fromStrings(v: List<String>): String = json.encodeToString(v)
    @TypeConverter fun toStrings(s: String): List<String> = if (s.isBlank()) emptyList() else json.decodeFromString(s)
}
```

Use kotlinx.serialization everywhere. Don't mix in Gson.

#### 7.6 DAO and transactions

```kotlin
@Dao interface VehicleProfileDao {
    @Query("SELECT * FROM vehicle_profiles ORDER BY createdAt") fun observeAll(): Flow<List<VehicleProfile>>
    @Query("SELECT * FROM vehicle_profiles WHERE id = :id") suspend fun get(id: Long): VehicleProfile?
    @Query("SELECT * FROM vehicle_profiles WHERE btMac = :mac COLLATE NOCASE LIMIT 1") suspend fun findByMac(mac: String): VehicleProfile?
    @Query("SELECT * FROM vehicle_profiles WHERE isDefaultVehicle = 1 LIMIT 1") suspend fun getDefault(): VehicleProfile?
    @Query("SELECT * FROM vehicle_profiles WHERE btMac IS NULL ORDER BY createdAt") suspend fun noBluetoothVehicles(): List<VehicleProfile>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(p: VehicleProfile): Long
    @Update suspend fun update(p: VehicleProfile)
    @Delete suspend fun delete(p: VehicleProfile)
    @Query("UPDATE vehicle_profiles SET isDefaultVehicle = 0") suspend fun clearDefault()
    @Query("UPDATE vehicle_profiles SET isDefaultVehicle = 1 WHERE id = :id") suspend fun setDefaultById(id: Long)
    @Transaction suspend fun setDefault(id: Long) { clearDefault(); setDefaultById(id) }
}
```

- **`CapabilityChecker` writes atomically.** It writes the `capabilityCheckResults` map and its derived flags (`wheelButtonSupported`, `scoHoldsReliably`, `obdProtocol`) in one `@Transaction` update.
- **The VIN is never written by a probe.** `vin`, `make`, `model`, `year`, and `makeConfirmed` change only when the user taps **Use** or **Save** (M13.6, M14.5), in their own transaction.
- **Destructive migration is acceptable for V1.** `fallbackToDestructiveMigration()` is fine before any public release. From the first Play release on, write real migrations. Phase 6 adds `dtc_readings` as database version 2.

#### 7.7 DataStore keys (file `settings`)

DataStore keys need no migration, so the 1.1 keys are simply added when that release ships.

| Key | Type | Default | Release | Used by |
|---|---|---|---|---|
| `first_run_done` | Boolean | `false` | V1 | `MainActivity` routing |
| `cloud_speech_allowed` | Boolean | `false` | V1 | M2.3; the user chose Google's cloud speech because on-device recognition isn't available |
| `whats_new_seen` | Int | `0` | 1.1 | Last release whose "What's new" card was shown |
| `tts_enabled` | Boolean | `true` | V1 | M2; screen text always shows |
| `tts_rate` | Float | `1.0f` | V1 | `TtsEngine.setSpeechRate` (slider 0.5–2.0) |
| `day_night_pref` | String | `"auto"` | V1 | M4; `"auto"`, `"day"`, or `"night"` |
| `unknown_car_prompt` | Boolean | `true` | V1 | M1; offer to add an unrecognized Bluetooth car |
| `stay_open_on_disconnect` | Boolean | `false` | V1 | M1; keep the car screen after disconnect |
| `last_vehicle_id` | Long | `-1` | V1 | Pre-select the vehicle on manual launch |
| `wheel_fallback_assistant_pkg` | String | `""` | V1 | M11; where button presses go outside car mode |
| `weather_location_asked` | Boolean | `false` | 1.1 | M9.2; the location prompt has been shown once |
| `weather_zip_fallback` | String | `""` | 1.1 | M9; empty means use live location |
| `weather_zip_geo` | String (JSON) | `""` | 1.1 | M9; geocoded `{lat, lon, place}` for the ZIP, cached once |
| `weather_cache` | String (JSON) | `""` | 1.1 | M9; `{ts, lat1, lon1, tempF, code, place}` (30-minute cache; coordinates rounded to 0.1°) |
| `hotword_enabled_global` | Boolean | `false` | 1.1 | M10 master switch |
| `driving_detection_enabled` | Boolean | `false` | 1.1 | M1.10; offer car mode when driving is detected (cars without Bluetooth) |
| `ai_provider_id` | String | `"none"` | 1.1 | M12; `none`, `gemini`, `openai`, `anthropic`, `openrouter`, `groq`, or `nano` |
| `ai_mode_enabled` | Boolean | `false` | 1.1 | M12 |
| `ai_model_<providerId>` | String | `""` | 1.1 | M12 |
| `ai_consent_<providerId>` | Int | `0` | 1.1 | M12; version of the consent note accepted (0 = none) |
| `ai_usage_<providerId>_<yyyyMM>` | Int | `0` | 1.1 | M12 local usage counter |

#### 7.8 Secrets (file `secrets`, `KeyVault`; release 1.1)

This is used only by "Choose your AI" (Phase 5c). V1 has no secrets file.

`androidx.security:security-crypto` (`EncryptedSharedPreferences`, `MasterKey`) was deprecated in 2025 in favor of using the Android Keystore directly. `KeyVault`:

- **Key generation.** Generates (once) an AES-256 key in `AndroidKeyStore` with alias `bobcar_secrets_v1`, `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, and `setRandomizedEncryptionRequired(true)`.
- **Storage format.** Stores each secret as Base64(`iv` ‖ `ciphertext`) under:
  - `api_key_<providerId>`, with status `api_key_status_<providerId>` set to `"valid"`, `"unverified"`, or `"invalid"`.
  - `oauth_access_<id>` and `oauth_refresh_<id>` for future OAuth tokens.
- **Backup.** Cloud backup is off for the whole app (§8.4). The `secrets` file is also excluded from device-to-device transfer through `dataExtractionRules`, because Keystore keys don't migrate and a transferred ciphertext could never be decrypted.
- **Lost key.** If decryption fails (key invalidated, data restored somehow), delete the entry, set AI mode off, and show "Your AI key needs to be re-entered."
- **Never expose a key.** Never log a key, never put one in a crash report, and show only the last 4 characters in the UI.

#### 7.9 Post-V1 schema additions (defined here, built later)

| Version | Change | Spec |
|---|---|---|
| Database version 2 (Phase 6) | New entity `DtcReading`, table `dtc_readings` | M14.9 |
| Database version 3 (Phase 7) | New `VehicleProfile` column `supportedSignals: List<String> = emptyList()` (OBDb signal IDs, JSON through the TypeConverter) | M15.6 |

From the first public release on, every version bump ships a real Room `Migration`.

### 8. Permissions and manifest (canonical)

#### 8.1 Permissions

**V1:**

| Permission | Why | Runtime? | Notes |
|---|---|---|---|
| `RECORD_AUDIO` | Voice input | Yes | Show the §8.3 text before the dialog |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion="30"`) | Bonded devices on API 26–30 | No | Legacy only |
| `BLUETOOTH_CONNECT` | Bonded device names and MACs on API 31+; also the prerequisite for the `connectedDevice` FGS type | Yes (31+) | |
| `READ_CONTACTS` | Resolve "call Mom" and "text Mom" | Yes | Play sensitive permission: in-app disclosure plus Data safety entry |
| `POST_NOTIFICATIONS` | FGS notification and the tap-to-open notification | Yes (33+) | If denied on 33+ without the overlay permission, auto-launch can't show anything; M3 reports `auto_launch` as `WARN` |
| `FOREGROUND_SERVICE` | Foreground services | No | |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | `CarModeService` type `connectedDevice` (API 34+) | No | Prerequisite: `BLUETOOTH_CONNECT` granted |
| `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` | Start `CarModeService` from the ACL receiver once associated (API 31+) | No | The documented FGS exemption for apps that use CompanionDeviceManager |
| `SYSTEM_ALERT_WINDOW` | "Display over other apps": the documented exception that lets BobCar open the car screen from the background | Special access (Settings) | Optional; asked once during first run with a plain explanation |

**Added in release 1.1** (only for the sub-phases that ship):

| Permission | Phase | Why | Runtime? | Notes |
|---|---|---|---|---|
| `INTERNET` | 5a, 5c, or 5f | Open-Meteo weather; AI providers; VIN decoding | No | V1 has no network access of its own |
| `ACCESS_COARSE_LOCATION` | 5a | Weather for the current area | Yes | **Coarse only.** Weather needs about a 1 km fix; navigation doesn't use BobCar's location. Asked once, on the first weather request; if denied, BobCar asks for a ZIP by voice (M9.2) |
| `FOREGROUND_SERVICE_MICROPHONE` | 5b | `HotwordService` type `microphone` (API 34+) | No | Prerequisite: `RECORD_AUDIO` granted |
| `MODIFY_AUDIO_SETTINGS` | 5b | SCO routing, `setCommunicationDevice`, `setMode` (car-mic hotword only) | No | |
| `REQUEST_COMPANION_RUN_IN_BACKGROUND` | 5b | Associated apps run in the background, and on AOSP this adds the app to the power-save allowlist, which keeps the hotword alive in Doze | No | Confirm with `PowerManager.isIgnoringBatteryOptimizations()` after association (M10.7) |
| `ACTIVITY_RECOGNITION` (API 29+; `com.google.android.gms.permission.ACTIVITY_RECOGNITION` on 26–28) | 5e | Notice when driving starts, for cars without Bluetooth (M1.10) | Yes (29+) | Asked only when the user turns on driving detection |
| `CAMERA` | 5f | Scan the VIN barcode on the phone (ZXing) | Yes | Asked only when the user taps **Scan**; frames are never saved or sent |

**Added in Phase 6 (OBD):**

| Permission / feature | Why | Notes |
|---|---|---|
| `INTERNET` | VIN decoding, if 5f didn't ship | Already present if 1.1 shipped |
| `<uses-feature android:name="android.hardware.usb.host" android:required="false"/>` | USB OBD adapters | Android shows its own per-device prompt; no manifest permission |
| `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE` | Wi-Fi OBD adapters | Other Wi-Fi permissions depend on API level (verify at build time) |
| BLE adapters | Picked through the CompanionDeviceManager dialog | Avoids `BLUETOOTH_SCAN` and location |

**Deliberately not requested:**

| Permission | Instead |
|---|---|
| `CALL_PHONE` | `ACTION_DIAL` |
| `SEND_SMS`, `READ_SMS` | `ACTION_SENDTO` with `smsto:` |
| `BIND_NOTIFICATION_LISTENER` | `AudioManager.dispatchMediaKeyEvent` |
| `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `BLUETOOTH_SCAN` | Not needed; BLE adapters are picked through CompanionDeviceManager |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | The CompanionDeviceManager allowlist, or the no-permission settings list (M10.7) |
| `PACKAGE_USAGE_STATS` | `CarConnection` |
| `RECEIVE_BOOT_COMPLETED` | Not needed: the ACL receiver works from cold |
| `USE_FULL_SCREEN_INTENT` | Restricted on Android 14+ to calling and alarm apps |

#### 8.2 Manifest skeleton (V1)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.bluetooth" android:required="true"/>
    <uses-feature android:name="android.hardware.microphone" android:required="true"/>
    <uses-feature android:name="android.software.companion_device_setup" android:required="false"/>

    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
    <uses-permission android:name="android.permission.READ_CONTACTS"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
    <uses-permission android:name="android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND"/>
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

    <!-- Android 11+ package visibility: needed for isRecognitionAvailable(), TTS engine binding,
         and resolving the current ACTION_VOICE_COMMAND handler (M11). -->
    <queries>
        <intent><action android:name="android.speech.RecognitionService"/></intent>
        <intent><action android:name="android.intent.action.TTS_SERVICE"/></intent>
        <intent><action android:name="android.intent.action.VOICE_COMMAND"/></intent>
    </queries>

    <!-- §8.4: no cloud backup at all. data_extraction_rules excludes every domain from <cloud-backup> and allows
         <device-transfer> between the user's own phones (1.1 also excludes datastore/secrets.preferences_pb). -->
    <application android:name=".BobCarApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules">

        <activity android:name=".ui.MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <!-- showWhenLocked/turnScreenOn are API 27+; on API 26 set the equivalent window flags in onCreate(). -->
        <activity android:name=".ui.CarModeActivity"
            android:exported="false"
            android:launchMode="singleTask"
            android:screenOrientation="portrait"
            android:showWhenLocked="true"
            android:turnScreenOn="true"/>

        <!-- Disabled until the user turns on the steering-wheel button (M11). -->
        <activity android:name=".ui.VoiceCommandActivity"
            android:exported="true"
            android:enabled="false"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@style/Theme.BobCar.Transparent">
            <intent-filter>
                <action android:name="android.intent.action.VOICE_COMMAND"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <!-- ACL_CONNECTED/DISCONNECTED are on Android's implicit-broadcast exemption list, so this
             manifest receiver fires even when the app isn't running. They are protected broadcasts:
             only the system can send them. -->
        <receiver android:name=".car.CarConnectionReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_CONNECTED"/>
                <action android:name="android.bluetooth.device.action.ACL_DISCONNECTED"/>
            </intent-filter>
        </receiver>

        <service android:name=".car.CarModeService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice"/>
    </application>
</manifest>
```

**Release 1.1 additions** (only for the sub-phases that ship):

```xml
    <!-- 5a weather / 5c AI -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <!-- 5a weather -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
    <!-- 5b hotword -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
    <uses-permission android:name="android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND"/>
    <!-- 5e driving detection -->
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION"/>
    <uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" android:maxSdkVersion="28"/>
    <!-- 5f VIN scan (on-device ZXing) -->
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-feature android:name="android.hardware.camera" android:required="false"/>

    <!-- 5c AI: data_extraction_rules also excludes datastore/secrets.preferences_pb from device transfer (§7.8) -->
    <application …>

        <!-- 5b hotword -->
        <service android:name=".hotword.HotwordService"
            android:exported="false"
            android:foregroundServiceType="microphone"/>
        <!-- 5e driving detection: explicit receiver for the activity-transition PendingIntent -->
        <receiver android:name=".car.DrivingTransitionReceiver" android:exported="false"/>
    </application>
```

**Phase 6 additions:**

```xml
    <uses-feature android:name="android.hardware.usb.host" android:required="false"/>
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <!-- plus INTERNET if 1.1 didn't add it; other Wi-Fi permissions per API level (verify at build time) -->
```

**Play Console declarations:**

- **V1:**
  - Foreground service type `connectedDevice` (App content → Foreground service permissions, required for apps targeting 34+): "keeps car mode running while connected to the car." Include a short video.
  - Sensitive permission `READ_CONTACTS`: a core-functionality justification and a short demo video.
  - Data safety form: see Phase 4.
- **Release 1.1:**
  - Foreground service type `microphone`: "listens for the user's wake phrase while car mode is on." Include a short video.
  - Location: a core-functionality justification (weather).
  - Physical activity (5e): a justification (offering car mode in cars without Bluetooth).
  - Data safety updates, including VIN decoding (the VIN is sent to NHTSA vPIC): see Phase 5d.

#### 8.3 Permission disclosure rule (canonical)

**Every permission, special access, or link request tells the user what it's for and every use of it, before the system dialog appears.**

- **Where the text appears:**
  - On screen before the system dialog. It's also spoken if the request happens in car mode.
  - In Settings → **Permissions**, which lists each item with its status and a **Manage** button.
- **Single source.** All the wording lives in one file, `PermissionCopy.kt`, so the prompt and the settings screen always match.
- **Keep it current.** If a release adds a new use, update the text, the privacy policy, and the Data safety form in the same change. Show the updated text the first time the new feature is turned on.

| Item | Text (current uses only) | Added later |
|---|---|---|
| Microphone (`RECORD_AUDIO`) | "Microphone: to hear your voice commands when you tap Talk. BobCar uses your phone's on-device speech recognition when it's available, and doesn't record or keep audio." | 1.1: "…and to listen for 'Hey Bob' while car mode is open, if you turn it on." |
| Nearby devices (`BLUETOOTH_CONNECT`) | "Nearby devices: to recognize your car's Bluetooth so car mode can open when you connect, and to check whether your car can play BobCar's voice. BobCar only reads devices already paired to your phone." | 1.1: "…and to use your car's microphone, if you turn that on." Phase 6: "…and to talk to your diagnostics adapter." |
| Cloud speech (V1, only if on-device recognition isn't available) | "This phone can't recognize speech on its own, so your voice commands would be sent to Google's speech service to be turned into text." Links: **Google privacy policy**, **Manage or delete your Google activity**. Buttons: **Use Google speech** / **Buttons only** (M2.3, §8.4) | — |
| Physical activity (`ACTIVITY_RECOGNITION`, 1.1) | — | "Physical activity: only to notice when you start driving, so BobCar can offer car mode in a car without Bluetooth. Used for nothing else." |
| USB device (Phase 6) | — | Before Android's prompt: "BobCar will use this USB device only to read your car's diagnostics." |
| Wi-Fi adapter (Phase 6) | — | "Wi-Fi: only to connect to your Wi-Fi diagnostics adapter." |
| VIN lookup (1.1) | — | "To look up your vehicle, BobCar sends the model part of your VIN, not the serial number, to the U.S. government's free VIN decoder (NHTSA). Nothing that identifies your specific vehicle is sent." With a **Request removal / privacy** link (M13.6, §8.4) |
| Camera (`CAMERA`, 1.1) | — | "Camera: only to read your VIN barcode. Scanning happens on this phone, and pictures are never saved or sent anywhere." (M13.6) |
| Notifications (`POST_NOTIFICATIONS`) | "Notifications: to show that car mode is running, and to let you open it with one tap when your car connects." | — |
| Contacts (`READ_CONTACTS`) | "Contacts: to find the number when you say 'call' or 'text' someone. Contacts stay on your phone and are never sent anywhere." | — |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | "Display over other apps: only so car mode can open by itself when your car connects. BobCar doesn't draw over other apps." | — |
| Link car (CompanionDeviceManager) | "Link your car: lets BobCar start car mode in the background when this car connects. Used for nothing else." | — |
| Location (`ACCESS_COARSE_LOCATION`, 1.1) | — | "Approximate location: only for weather, and only sent to the weather service." (M9.2) |
| ZIP code (asked by voice, 1.1) | — | "It's only used for weather." (M9.2) |
| Battery settings (1.1) | — | "Battery: so 'Hey Bob' keeps listening during long drives. Used for nothing else." (M10.7) |
| AI provider (1.1) | — | Per-provider consent screen (M12.7) |

#### 8.4 Data-sharing rule and third-party register (canonical)

**Rule: user data never leaves the phone unless a feature can't work without it.** When something must be sent:
1. **Minimize** it to the least that works.
2. **Disclose** it before the first send: what is sent, to whom, and why, using the §8.3 pattern.
3. **Link** to where the user can ask that third party to remove it (its deletion or privacy-request page). If the party offers no removal route, link its privacy policy and say plainly that it doesn't offer one.
4. **List** it in this register, the in-app **Privacy & data** screen, the privacy policy, and the Play Data safety form, all in the same change.

**Never add** analytics, crash-reporting, advertising, attribution, or tracking SDKs.

**No cloud backup.** Set `allowBackup="false"`, and have `data_extraction_rules` exclude every domain from cloud backup. Device-to-device transfer between the user's own phones is allowed, except the 1.1 secrets file.

**Register.** All links live in one file, `ThirdPartyRegister.kt`; verify every URL at build time.

| Flow | Release | What's sent | How it's minimized | Removal / privacy link |
|---|---|---|---|---|
| Cloud speech recognition (Google) | V1, **only if** on-device recognition isn't available and the user chooses it | Voice audio of each command | The on-device recognizer is preferred (M2.3); cloud only after an explicit choice | Google privacy policy and activity controls (myactivity.google.com) |
| Weather (Open-Meteo) | 1.1 | Approximate location rounded to 0.1° (about 11 km), or the ZIP | Rounded coordinates; no identifiers | Open-Meteo privacy/terms page and contact |
| VIN lookup (NHTSA vPIC) | 1.1 | The model part of the VIN: characters 1–8, 10, and 11. The check digit and serial are sent as `*` | Nothing identifies the specific vehicle | NHTSA privacy policy page |
| AI provider (the user's choice) | 1.1, after consent | The words of an unrecognized command, plus the system prompt | Only `Unknown` utterances; never contacts, location, or vehicle data | The chosen provider's privacy or data-request page (Google, OpenAI, Anthropic, OpenRouter, Groq) |
| Handoffs to the user's own apps (dialer, messaging, Maps, "Send to an AI app") | V1 (AI-app share in 1.1) | The number, message, destination, or transcript, passed to the app the user chose | Only on a voice command or tap | That app's own privacy settings; for Google Maps, Google's activity controls |

**Device services BobCar doesn't control.** These are disclosed on the Privacy & data screen, but BobCar doesn't send this data:
- **Phone location service** (1.1 weather): Google Play services computes the location, and Google's own location settings apply.
- **Activity Recognition** (1.1 driving detection): runs in Google Play services under Google's terms. Verify at build time what, if anything, it sends.
- **Android and Google Play crash reports:** collected under the user's own device settings. BobCar adds nothing.

**Privacy & data screen** (Settings, V1):
- Lists every active flow from the register: what's sent, when, and the **Privacy policy** and **Request removal** links.
- Holds the cloud-speech choice.
- Has **Delete all BobCar data on this phone**. It clears Room, DataStore, and secrets, removes CompanionDeviceManager associations, and returns to first run.

### 9. UX flows

#### 9.1 First run

1. **Welcome.** One paragraph: "BobCar is a voice-first car mode for any car. It works best over Bluetooth, but your car doesn't need it. No extra hardware is needed." Button: **Set up my car**.
2. **Permissions in sequence.** Ask for `RECORD_AUDIO`, `POST_NOTIFICATIONS` (33+), then `READ_CONTACTS`.
   - `BLUETOOTH_CONNECT` is asked in step 3, only for Bluetooth cars.
   - Location arrives with weather in 1.1 and is asked on the first weather request (M9.2).
   - Show the §8.3 text before each system dialog.
   - Declines aren't fatal: the affected features show as `WARN` in the capability report.
   - **Speech check.** If on-device recognition isn't available (M2.3), show the §8.3 cloud-speech disclosure with **Use Google speech** and **Buttons only**. The choice is saved as `cloud_speech_allowed` and can be changed in Privacy & data.
3. **Pick car.** First ask "Does your car have Bluetooth for calls or music?"
   - **Yes:** ask `BLUETOOTH_CONNECT` (31+), then list bonded devices (`BluetoothAdapter.bondedDevices`), excluding devices whose major class is audio/video headphones or headset (`BluetoothClass.Device.Major.AUDIO_VIDEO` with a headset or headphone minor class). A 12 V Bluetooth adapter counts as the car's Bluetooth.
   - **The user taps the car's entry** ("SYNC"). That creates a `VehicleProfile` with:
     - `btMac` (uppercase) and `btName`.
     - `isDefaultVehicle = true` if it's the first car.
   - **No:** that creates a profile with `btMac = null` and a name the user picks. No Bluetooth permission is asked. Skip steps 4–5. Car mode opens from the launcher; release 1.1 adds driving detection (M1.10).
   - **Confirm the vehicle.** `MakeGuesser` suggests a make from the Bluetooth name (M13.1); with no Bluetooth there's nothing to guess.
     - The screen asks "Is this a Ford?" with **Yes** and **Change**, plus model and **year** fields.
     - Saving sets `makeConfirmed = true`. Any `KnownVehicleNotes` pre-seeds apply only after that.
     - **Skip for now** is allowed: the guess stays unconfirmed and isn't used.
   - **Support check (soft).** `SupportPolicy` uses the year (M13.1).
     - Before 1996: "This vehicle isn't supported. BobCar works with cars from 1996 and newer." The buttons are **Continue anyway** and **Change year**.
     - Year skipped: no message now; the vehicle shows "Add your car's year" in the report.
4. **Link the car (CompanionDeviceManager; Bluetooth cars only).** Show the §8.3 "Link your car" text, then run the system association dialog for that MAC (M1.3).
   - Do this while the phone is connected to the car. Location Services must be on during association.
   - If association fails, auto-launch still works, in notification mode (M1.4). Record `auto_launch` = `WARN` with a **Link car** retry button.
5. **"Open car mode automatically?"** (Bluetooth cars only.) Explain the choice, ending with the §8.3 "Display over other apps" text:
   - "To open car mode by itself when you start the car, Android needs 'Display over other apps'.
   - Without it, you'll get a notification to tap instead."

   The buttons are **Allow** (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with `package:` URI) and **Use a notification**.

   Then a one-line tip with an **Open settings** link: "Tip: add your car as a trusted device in your phone's security settings, so the phone stays unlocked while connected. Calls, texts, and Maps then open without unlocking." Device support varies (Smart Lock or Extend Unlock), so never require it.
6. **Capability check.** TTS says "Let's check what your car can do." The quick probe runs, then the compatibility report opens once (§9.3) with a one-line spoken summary, for example: "Everything's set. Your car doesn't send the steering-wheel button to the phone, so tap Talk when you want something."
7. **Finish.** Set `first_run_done = true`. "Go home" needs no setup: it uses the Home saved in Google Maps (M8.2).

**Release 1.1:** existing users get a one-time "What's new" card in `MainActivity` (`whats_new_seen`). It offers weather setup (M9.2), the "Hey Bob" setup (M10.9), "Choose your AI" (§9.6), **Add your VIN** (M13.6), and, for cars without Bluetooth, driving detection (M1.10). New installs see the same items as optional first-run steps after step 6.

#### 9.2 Daily drive

1. **Ignition on.** The phone connects to SYNC and `ACTION_ACL_CONNECTED` fires. `CarConnectionReceiver` matches the MAC to a profile (M1.4). An unknown MAC does nothing, or offers "Add this car?" if `unknown_car_prompt` is on. The OBD adapter's MAC never matches a profile `btMac` (M1.7).
2. **Service starts.** `CarModeService` starts as a `connectedDevice` foreground service with the notification "BobCar · connected to Taurus" and a **Quit car mode** action.
3. **Screen or notification.** The car screen opens if "Display over other apps" is allowed. Otherwise the notification's tap action opens it.
4. **Quick probe.** It runs in the background without blocking the screen. Cached results render in under 500 ms. A **regression** since the last drive (`PASS` → anything else) gets one spoken and shown line; unchanged state is never re-announced.
5. **Car screen visible.** (Release 1.1) If the hotword is enabled for this vehicle and globally, `CarModeActivity` starts `HotwordService` on the phone mic, or on the car mic if `hotwordUseCarMic` is on.
6. **Triggers.** All of these call the same `startListening()`:
   - The Talk button, always available.
   - The wheel button, if `wheelButtonSupported` and `wheelButtonEnabled` for this vehicle.
   - Release 1.1: "Hey Bob", if armed.
7. **Drive.** The voice loop runs as described in M2. (Release 1.1) During a phone call the hotword pauses and releases the mic. It resumes within 5 s after the call ends.
8. **Ignition off.** `ACTION_ACL_DISCONNECTED` starts the 8 s debounce. If the car doesn't reconnect, the app:
   - Says "Disconnected".
   - (Release 1.1) Stops `HotwordService`, and SCO if it was open.
   - Stops `CarModeService`.
   - Finishes the car screen, unless `stay_open_on_disconnect` is on.

#### 9.3 Compatibility report

- **Entry points.** Settings → vehicle → **Compatibility report**, or the "ⓘ Systems" chip on the car screen. It also opens automatically once after first run.
- **Layout.** A vehicle header ("2009 Ford Taurus · SYNC"), then one row per probe key from §7.3 in this order:
  1. Voice
  2. Auto-launch
  3. Car audio
  4. Wheel button
  5. Car mic (release 1.1 only)
  6. OBD adapter
  7. Extended data

  Each row has a status dot, a title, the one-line note, a relative time ("checked 2 h ago", or "never checked"), and an action button when the status is `WARN` or `NOT_TESTED`.
- **Example rows for the Taurus in V1:**

  | Row | Status | Note | Action |
  |---|---|---|---|
  | Voice | `PASS` | "Speech recognition and voice output are ready." | — |
  | Auto-launch | `PASS` if associated and overlay allowed; otherwise `WARN` | `WARN`: "Car mode opens from a notification. Allow 'Display over other apps' to open it automatically." | **Allow** |
  | Car audio | `PASS` | "Your car plays BobCar's voice and music over Bluetooth." | — |
  | Wheel button | `FAIL` (pre-seeded) | "SYNC Gen 1 does not forward the steering-wheel voice button to the phone (verified Oct 2026)." | **Re-verify** |
  | Car mic (1.1) | `NOT_TESTED`, or the test result | | **Run car-mic test** |
  | Diagnostics adapter | `WARN` | "Engine diagnostics need a small adapter that plugs in under the dashboard (wishlist)." | **View hardware wishlist** |
  | Extended data | `NOT_TESTED` | "Needs a diagnostics adapter." | — |

  **Car without Bluetooth** (the F-350):
  - Auto-launch shows `FAIL` "This car has no Bluetooth, so car mode opens from the launcher." In 1.1 it becomes `WARN` with a **Turn on driving detection** button.
  - Car audio shows `FAIL` "This car has no Bluetooth, so BobCar speaks from the phone." The note mentions 12 V Bluetooth adapters.

  **Pre-1996 vehicle:** a banner at the top says "This vehicle isn't supported. BobCar works with cars from 1996 and newer," and the two diagnostics rows show `FAIL` "Not supported by this vehicle (pre-1996)." With no year, the diagnostics rows show `WARN` "Add your car's year" with an **Add year** button.

- **Re-check.** Re-runs the full check (M3.2).
- **Copy summary.** Copies a plain-text summary to the clipboard, which is useful for support questions.

#### 9.4 Wheel-button test

The full specification is in M11.5. In short:

1. Show "Press your steering-wheel voice button now."
2. Run a 10 s countdown while `VoiceCommandActivity` is temporarily enabled.
3. If the activity receives `ACTION_VOICE_COMMAND`, set `PASS` and `wheelButtonSupported = true`. Say "Steering-wheel button linked."
4. If the countdown times out, set `FAIL`, "No signal from the wheel button. That's normal on older cars; auto-launch and tap-to-talk still work." The test never auto-retries.
5. If another app opened instead, set `WARN` with the fix (M11.5).

#### 9.5 Car-mic test (release 1.1; only offered when the user turns on "Use car mic")

The full specification is in M10.6. In short:

1. Warn first: "Music will pause for a few seconds."
2. Open SCO (timeout 10 s).
3. Capture 3 s and check the level is above the silence floor.
4. If a wake model is loaded, ask the user to say "Hey Bob" three times.
5. The result is `PASS`, `WARN`, or `FAIL`, written to `car_mic` and `scoHoldsReliably`.

#### 9.6 "Choose your AI" (release 1.1)

1. **Entry.** The "What's new in 1.1" card (§9.1), or Settings → **AI assistant**. Explain in plain language: "BobCar understands commands out of the box. For open questions, you can connect an AI you already use. It runs on your account, not ours."
2. **Rows.** Each row has a status line. The full table is in M12.4.
   - Built-in commands (default, selected).
   - Google Gemini key.
   - ChatGPT key, plus a disabled "Sign in with ChatGPT" tab.
   - Claude key.
   - OpenRouter key and Groq key.
   - On-device Gemini Nano, grayed out on the Zenfone 8 with "Not available on this phone".
   - The "Open in …" share hatch.
3. **Consent.** Selecting a provider shows its consent screen (M12.7) before anything is sent. The row activates only after **"I understand, enable"**.
4. **Active provider.** It shows the model picker (a live list), "Requests this month: N (counted on this phone)", key management (re-validate, delete), and the AI-mode switch, which defaults to off.
5. **AI mode on.** Commands the parser understands always run through the parser. Only `Unknown` utterances go to the AI, with answers of about 30 spoken words or fewer.
6. **Failure.** Any failure produces one spoken line plus the parser's help prompt. The drive never dead-ends.

#### 9.7 Diagnostics (post-V1, Phase 6)

- **V1.** "check engine," or **More → Diagnostics** on the car screen, says: "Engine diagnostics need a small adapter that plugs in under the dashboard. That arrives in a later version."
- **Phase 6 flow (read-only):**
  1. Connect to the adapter over its transport (classic Bluetooth, BLE, Wi-Fi, or USB; M14.3).
  2. Run the ELM327 init (M14.4).
  3. Read stored (mode 03) and pending (mode 07) codes.
  4. Look up descriptions in the bundled offline table and explain them (screen and voice rules in M14.13), for example: "P0301: cylinder 1 misfire. Usually a spark plug or coil. This is stored, not necessarily current. I'm reading codes, not a mechanic."
  5. **No clearing.** BobCar never clears codes; that's a wishlist item (M14.1). The screen says "Have your mechanic clear codes after the repair."
  6. If the adapter disconnects, show "Adapter disconnected; reconnect and retry", mark readings stale, and never delete them.

---

## Part 3: Build plan

**Status:** executable plan. Build exactly what is specified and don't add scope.

### 10. Hard constraints

- **V1 is software only and works in any car.** It uses the car's Bluetooth when there is one, and the phone alone when there isn't. It needs no adapter, no head-unit changes, and no extra hardware.
- **Not tied to any make.** Ford appears only as test vehicles and as verified data entries.
- **Everything is free.** No paid services, no servers, and no licensing costs at Play Store scale.
  - The app ships with no API keys.
  - Release 1.1's "Choose your AI" lets the user optionally paste **their own** key. That's their account, their bill, and their choice, and the app works fully without it.
- **V1 makes no network calls of its own and doesn't declare `INTERNET`.** Release 1.1 adds internet access for weather and AI.
- **Package:** `dev.timothy.carvoice` is a placeholder. Set the final `applicationId` before the first Play upload (Phase 4). The code `namespace` can stay as it is.
- **SDK levels:** `minSdk 26`. `targetSdk` is Play's current requirement. Test on the Zenfone 8 (API 33).
- **No steering-wheel button interception on the Taurus.** This is settled (§3). The Taurus profile is pre-seeded `wheelButtonSupported = false`, and other vehicles get an empirical on-demand test (M11).
- **The built-in brain is the keyword parser.** AI mode (release 1.1) is optional, off by default, and the parser handles every known command first (M12).

| Phase | Title | Release | Mechanics |
|---|---|---|---|
| 0 | Scaffold | V1 | — |
| 1 | Auto-launch, voice loop, capability-check foundation | V1 | M1, M2 (loop), M3, M13 (data) |
| 2 | Phone-side commands | V1 | M2.5, M5–M8, M9 (time only) |
| 3 | Car UI polish, wheel button | V1 | M4, M11 |
| 4 | Play Store release | V1 | — |
| 5a | Weather | **1.1** | M9 (weather) |
| 5b | Hotword activation | **1.1** | M10 |
| 5c | Choose your AI | **1.1** | M12 |
| 5e | Driving detection (cars without Bluetooth) | **1.1** | M1.10 |
| 5f | VIN entry, scan, and decoding | **1.1** | M13.6 |
| 5d | Play Store update (ships after 5a–5c, 5e, and 5f) | **1.1** | — |
| 6a | OBD-II diagnostics (read-only): one real adapter type + the PC emulator over Wi-Fi, VIN from the car, diagnostics screen | **Post-V1, hardware-gated** | M14 |
| 6b | The remaining adapter types (BLE, USB, Wi-Fi hardware) | **Post-V1, hardware-gated** | M14.3 |
| 7 | Make-specific extended data (all makes, OBDb) | **Post-V1, hardware-gated** | M15 |

### 11. V1 phases

#### Phase 0: Scaffold

**Goal:** a clean Kotlin + Compose project that builds and installs on the Zenfone 8.

**Build items:**
- [ ] Android Studio → New Project → "Empty Activity" (Compose), Kotlin, package `dev.timothy.carvoice`.
- [ ] Set SDK levels: `minSdk 26`, `targetSdk` = Play's current requirement, `compileSdk` = latest.
- [ ] Use a version catalog (`libs.versions.toml`) with Compose BOM, Room + KSP, DataStore, kotlinx.serialization, and coroutines. OkHttp, `play-services-location`, and ONNX Runtime come in release 1.1.
- [ ] Create the package layout from §6.2. Packages can stay empty, and the 1.1 packages (`hotword/`, `ai/`) aren't created yet.
- [ ] Create the `:core` Gradle module (Kotlin/JVM, no Android SDK) and make `:app` depend on it. The shared files listed in §6.2 go there as they're written.
- [ ] `MainActivity` shows a placeholder `NavHost`.
- [ ] Android `.gitignore`. No secrets and no API keys anywhere in the repo.
- [ ] Test setup: JUnit, kotlinx-coroutines-test, Turbine, and Compose UI test. One passing unit test and one passing UI test.
- [ ] A `src/debug/` source set with an empty debug screen route (§6.6).
- [ ] R8 enabled for the release build type from day one, with keep rules for kotlinx.serialization. A release build must run on the phone, not just compile.

**Done criteria:**
- [ ] `./gradlew assembleDebug` succeeds.
- [ ] The debug APK installs and launches on the Zenfone 8 without crashing.
- [ ] The merged manifest shows `minSdk 26`.
- [ ] `./gradlew :core:test` runs on the PC without an emulator or phone.

**In-car test:** none. Install at home and confirm the app launches.

#### Phase 1: Auto-launch, voice loop, capability-check foundation

**Goal:**
- Car mode comes up when a Bluetooth car (the Taurus) connects and exits on disconnect.
- Car mode opens from the launcher in a car without Bluetooth (the F-350).
- Tap-to-talk works with spoken responses.
- The per-vehicle capability check and its report exist.

**Dependencies:** Phase 0.

**Build items:**
- [ ] Data layer: `AppDatabase`, `VehicleProfile`, `VehicleProfileDao`, `Converters`, `SettingsStore`, `KnownVehicleNotes`, and `SupportPolicy`, exactly as in §7 and M13.1.
- [ ] `CallStateWatcher` and `AudioFocus` (M2.4). TTS stays silent during calls and ducks music while speaking.
- [ ] Privacy (§8.4):
  - On-device speech recognition first, with the cloud-speech choice (M2.3).
  - A local TTS voice only (M2.4).
  - `allowBackup="false"` plus `data_extraction_rules`.
  - The **Privacy & data** screen with **Delete all BobCar data on this phone**.
- [ ] Debug tools from §6.6: fake car connect and disconnect, fake speech, fake call, and the redacted log tail. The 1.1 fields exist in the schema with their defaults even though V1 doesn't use them, so 1.1 needs no migration.
- [ ] First-run flow (§9.1) steps 1–7:
  - Permissions, with the §8.3 text.
  - Bluetooth picker, or "My car has no Bluetooth". This **creates the `VehicleProfile` in Room**.
  - `MakeGuesser` suggestion with user confirmation (M13.1).
  - CompanionDeviceManager association (M1.3) and the overlay-permission choice, for Bluetooth cars.
  - The quick probe.
- [ ] `CarConnectionReceiver` (manifest) and `CarConnectionHandler`, `CarModeService` (`connectedDevice`), `CarSession`, and `AndroidAutoDetector`, per M1.
- [ ] The tap-to-open notification fallback and the 8 s disconnect debounce (M1.4, M1.5).
- [ ] Manual car mode: launcher → "Enter car mode" with the default vehicle, or a vehicle picker if there are several. This is the V1 trigger for cars without Bluetooth (M1.10).
- [ ] `TtsEngine`, `VoiceEngine`, and `VoiceController` (M2.2–M2.4). `onResults` only echoes the transcript back through TTS for now, because the parser lands in Phase 2.
- [ ] A basic `CarModeActivity`: big Talk button, status line, Quit button. Polish comes in Phase 3.
- [ ] `CapabilityChecker` with the V1 probes (M3):
  - `voice` and `auto_launch`.
  - `audio_route`, with the phone-speaker fallback.
  - `wheel_button`: pre-seed only; the interactive test comes in Phase 3.
  - `obd_link`: bonded-name scan only.
- [ ] Compatibility-report screen skeleton (§9.3).

**Done criteria:**
- [ ] Car on: the car screen, or the tap-to-open notification when the overlay permission is off, appears within 5 s of the `ACL_CONNECTED` broadcast. Measure from the logcat timestamp.
- [ ] Car off: car mode exits within 8 s plus 1 s of the disconnect, and TTS says "Disconnected".
- [ ] Cold start works: force-stop the app, then turn the car on, and car mode still launches. This proves the manifest receiver works.
- [ ] Tap-to-talk transcribes a sentence and speaks it back, and the transcript appears on screen.
- [ ] The Taurus compatibility report shows `voice` = `PASS`, `auto_launch` = `PASS`/`WARN` (matching reality), `audio_route` = `PASS`, `wheel_button` = `FAIL` (pre-seeded note), and `obd_link` = `WARN` "Needs adapter (wishlist)".
- [ ] A no-Bluetooth profile (the F-350) opens car mode from the launcher, and every V1 command works on the phone's own mic and speaker.
- [ ] The make guess is shown for confirmation and can be changed. Nothing make-dependent runs until it's confirmed.
- [ ] The Zenfone 8 test records whether on-device recognition is available. If it isn't, the cloud-speech choice appears once, and **Buttons only** disables Talk while every button keeps working.
- [ ] The merged manifest has `allowBackup="false"`. `./gradlew :app:dependencies` shows no analytics, crash-reporting, or ads SDK.
- [ ] **Delete all BobCar data on this phone** returns the app to first run with no profiles or settings left.
- [ ] A 1995 vehicle shows "This vehicle isn't supported…" with **Continue anyway**, and setup can finish.
- [ ] The Android Auto choice passes M1.8 test 7 on the Desktop Head Unit.
- [ ] Music ducks while BobCar speaks and pauses while it listens, then resumes. During a phone call, BobCar shows text but doesn't speak.
- [ ] `audio_route` doesn't report "calls only" for the Taurus, even though Bluetooth music audio connects a few seconds after the car links up.
- [ ] No foreground service runs while the phone isn't connected to the car. Check with `adb shell dumpsys activity services dev.timothy.carvoice`.

**In-car test plan (Taurus, Zenfone 8):**
1. Pair the phone to SYNC if it isn't already. Run first run and pick the SYNC entry.
2. Ignition on → car mode opens and TTS says "Connected to Taurus". Ignition off → after about 8 s TTS says "Disconnected" and car mode exits. Repeat 3 times and record each `ACL_CONNECTED` → visible delay.
3. Repeat step 2 with the overlay permission **off**: the notification appears and a tap opens car mode.
4. Force-stop the app, wait 1 minute, ignition on → car mode still launches.
5. Parked, engine off, then engine on: tap Talk and say "what time is it". The transcript appears and the app speaks it back. Note transcription quality with engine noise.
6. With the **radio** selected on SYNC (not Bluetooth Audio), trigger the greeting. Note whether TTS is audible through the car speakers (§3, "verify in the car").
7. Open the compatibility report and confirm the rows above.
8. Manual override: with no car connected, open car mode from the launcher and confirm the UI works.
9. **F-350 (no Bluetooth):**
   - Add it as "My car has no Bluetooth" and open car mode from the launcher.
   - With the engine running, run "what time is it" and "navigate to …" on the phone's mic and speaker.
   - Confirm nothing tries to connect to Bluetooth.

#### Phase 2: Phone-side commands

**Goal:** tap-to-talk does useful things: calls, texts, music transport, navigation, go home, time, help, and the honest V1 replies for "check engine" and "what's the weather".

**Dependencies:** Phase 1.

**Build items:**
- [ ] `CommandParser`, exactly the grammar in M2.5, with unit tests for every row and every "must not match" example.
- [ ] `CommandExecutor` and the action classes:
  - M5 (calls), M6 (texts), M7 (music), M8 (navigation, go home), M9.3 (time).
  - `HelpAction`, using the V1 help text from M2.5.
  - `DiagnoseAction` and the `WeatherAction` stub, both with their V1 replies.
  - `ExternalLauncher` (M2.7).
- [ ] `ContactResolver`, shared by calls and texts.
- [ ] The `READ_CONTACTS` runtime permission flow with in-app disclosure.
- [ ] "Go home" through Google Maps' saved Home (M8.2). No address setting.

**Done criteria:**
- [ ] Every V1 command in M2.5 runs end-to-end from one tap-to-talk utterance, including the aliases, a spoken phone number, and "cancel".
- [ ] Contact disambiguation works for 0, 1, and 2+ matches, and for contacts with several numbers.
- [ ] Navigation opens Google Maps turn-by-turn. Without Maps, the `geo:` chooser appears.
- [ ] "What's the weather" replies "Weather is coming in a later update." with no network activity.
- [ ] The merged manifest has no `CALL_PHONE`, `SEND_SMS`, `READ_SMS`, `INTERNET`, or location permission.

**In-car test plan (Taurus, Zenfone 8):**
1. Parked: run each command once.
2. "Call Mom" → the dialer opens with the right number. Cancel at the dialer.
3. "Text Mom I'll be home at six" → the messaging app opens pre-filled. Confirm you must tap Send.
4. "Navigate to Walmart Sioux Falls" → Maps starts turn-by-turn.
5. "Go home" with a Home saved in Google Maps → Maps navigates to that Home, not to a business with "Home" in its name. **Verify in the car.** If Maps searches instead, record what it does and stop: an address fallback needs a new decision (M8.2).
6. Drive a 10-minute loop. Repeat "next", "what time is it", and "navigate to …" with road noise, and log transcription failures.

#### Phase 3: Car UI polish, wheel button

**Goal:** car mode is genuinely usable at arm's length, day and night. The wheel-button feature works on cars that forward the button.

**Dependencies:** Phases 1 and 2, so the UI can surface every state honestly.

**Build items:**
- [ ] The final car-screen layout, theme, typography, and no-text-entry rule (M4). The hotword pill comes in 1.1.
- [ ] The polished compatibility report (§9.3).
- [ ] The TTS on/off setting. Screen text always shows.
- [ ] The wheel-button feature and test (M11): `VoiceCommandActivity`, the enable switch, the 10 s test, and fallback forwarding outside car mode.
- [ ] The driver-distraction posture: no video, no scrolling feeds, no typing. Document it for the Play listing.

**Done criteria:**
- [ ] All buttons can be hit at arm's length with a glance. Fonts meet the minimums: status 32sp or more, buttons and body 24sp or more.
- [ ] The day theme is legible in direct sunlight and the night theme has no glare in a dark cabin.
- [ ] The screen stays on for a full 1-hour drive in car mode.
- [ ] Automated UI inspection finds no text field anywhere in car mode.
- [ ] The Taurus wheel-button test times out to `FAIL` with the verified note, and nothing nags afterwards.

**In-car test plan:** run the M4 acceptance tests, then the M11 Case B test on the Taurus. Run M11 Case A whenever a BVRA-capable car or head unit is available.

#### Phase 4: Play Store release (V1)

**Goal:** pass Play review and publish under the owner's existing developer account, internal testing track first.

**Dependencies:** Phases 0–3 complete and tested in the car.

**Release checklist:**
- [ ] **Final `applicationId`** in `build.gradle`, replacing `dev.timothy.carvoice`. It becomes permanent at the first upload, internal track included. The code `namespace` can stay.
- [ ] **Name check:** search Google Play and the US trademark database (USPTO) for "BobCar" before creating the listing.
- [ ] **Signing:**
  - Create an upload key and enroll in Play App Signing.
  - Store the upload keystore and passwords outside the repo, with an offline backup. A lost upload key needs a Play support reset.
- [ ] **Build format:** upload an Android App Bundle (`./gradlew bundleRelease`), not an APK.
- [ ] **Versioning:** `versionCode` is an integer that increases by 1 with every upload. `versionName` follows `1.0.0`.
- [ ] **Countries:** pick them in Play Console. V1 is US-centric (§3); the recommendation is the US first.
- [ ] **targetSdk** set to the current Play requirement (Play Console → Policy status).
- [ ] **Category:** Auto & Vehicles. Do **not** declare Android Auto or Automotive OS support, and declare no `CarAppService`. This is a phone app with a car mode; declaring automotive support triggers the automotive review track.
- [ ] **Foreground service declaration** (App content → Foreground service permissions): `connectedDevice`, "keeps car mode running while the phone is connected to the car", with a short video.
- [ ] **Sensitive permissions:** `READ_CONTACTS` needs a core-functionality justification and a short demo video of "call" and "text" using it. `RECORD_AUDIO` is standard.
- [ ] **Data safety form:**
  - Contacts are read on-device for calling and texting and never transmitted.
  - Voice input uses on-device recognition when available. If the user chose Google's cloud speech (§8.4), declare it.
  - BobCar itself doesn't store or transmit audio. **Verify at build time** how Play's Data safety guidance treats audio handled by the system recognizer, which may use Google's servers, and declare it accordingly.
  - BobCar collects and shares nothing and has no servers. V1 doesn't declare internet access.
  - The form must match the privacy policy; review them together.
- [ ] **Privacy policy:** a one-page policy, hosted free on GitHub Pages, linked in the listing. It's required because the app requests sensitive permissions. It lists every §8.4 register flow with the same removal links, and states that BobCar has no servers, no analytics, and no cloud backup.
- [ ] **Store listing:**
  - Assets: a 512 px icon, a 1024×500 feature graphic, and at least 2 screenshots.
  - The short description says "Works with your car's Bluetooth, or with no Bluetooth at all. For cars from 1996 and newer."
  - **Never name carmakers or head-unit brands** (SYNC, Uconnect, and so on) in the listing, to avoid trademark and impersonation problems.
  - Driver-distraction note: "Designed for voice-first use; big buttons, no typing while driving."
- [ ] **Rollout:** internal testing track → install on the Zenfone 8 → full in-car regression → closed track → production.

**Done criteria:**
- [ ] The app passes Play review. Target: no policy rejection on the first submission.
- [ ] The internal-track build passes the full in-car regression: auto-launch (both modes), every Phase 2 command, and the wheel-button test.
- [ ] The privacy policy URL is live, and the Data safety form is submitted and accurate.
- [ ] The release build has no debug logs, no debug screens, and no test UI reachable.

### 12. Release 1.1 phases

> **Build after V1 is live on Play.** Phases 5a, 5b, 5c, 5e, and 5f are independent of each other. Ship them together as 1.1 through Phase 5d. If one drags, the others can ship without it and it follows in a later point release.

#### Phase 5a: Weather

**Goal:** "what's the weather" speaks current conditions for the driver's area, free and with no API key.

**Dependencies:** V1 (Phase 2's `WeatherAction` stub and grammar rule).

**Build items:**
- [ ] Add OkHttp and `play-services-location` to the version catalog.
- [ ] Add the `INTERNET` and `ACCESS_COARSE_LOCATION` permissions (§8.1, release 1.1 rows).
- [ ] Replace the `WeatherAction` stub with M9.1–M9.2: Open-Meteo, the 30-minute cache, the WMO phrase table, and location resolution with the ZIP fallback.
- [ ] Update the help text to the 1.1 version in M2.5, which mentions weather.
- [ ] The first-ask flow (M9.2): ask for approximate location once; if the user says no, ask for a ZIP by voice.
- [ ] Settings: a **Weather location** row to grant location later, plus an editable ZIP field.
- [ ] A one-time "What's new in 1.1" card in `MainActivity` that offers weather setup to existing users.
- [ ] Open-Meteo attribution on the About screen.

**Done criteria:**
- [ ] Weather speaks current conditions. Within 30 minutes the cache answers without a network call. Offline with a cache, the reply includes "Data may be stale".
- [ ] The first weather request asks for location. Saying no leads to a spoken ZIP prompt, and the spoken ZIP works.
- [ ] Neither prompt repeats on later requests.
- [ ] The merged manifest has `ACCESS_COARSE_LOCATION` and not `ACCESS_FINE_LOCATION`.

**In-car test plan:** on a fresh install, ask "what's the weather", deny location, and say a ZIP. Then grant location in settings and ask again. Test parked, while driving, and in airplane mode with and without a cache. Run the M9.5 acceptance tests.

#### Phase 5b: Hotword activation

**Goal:** a hands-free "Hey Bob" trigger, on-device, off by default, using the phone mic by default and the car mic as an experiment. Tap-to-talk stays the always-available trigger.

**Dependencies:** V1. Its in-car results tell you how noisy the cabin is for speech, which feeds the hotword tuning.

**Build items:**
- [ ] Add ONNX Runtime and the permissions `FOREGROUND_SERVICE_MICROPHONE`, `MODIFY_AUDIO_SETTINGS`, and `REQUEST_COMPANION_RUN_IN_BACKGROUND`. Declare `HotwordService` (§8.2, release 1.1 additions).
- [ ] Train the custom wake-word models **before coding the UI** ("Hey Bob" required; "Hey Car" and "OK Bob" optional) with the upstream openWakeWord training notebook. Bundle the two openWakeWord feature models as well (M10.3).
- [ ] `HotwordEngine` interface and `OpenWakeWordEngine` (ONNX Runtime), with a Kotlin port of openWakeWord's feature pipeline that is unit-tested against the Python reference output (M10.3).
- [ ] `HotwordService` (`microphone` FGS), started only by a visible `CarModeActivity` (M10.4).
- [ ] `HotwordAudioSource`: `PHONE_MIC` by default; `CAR_SCO` only if `hotwordUseCarMic` (M10.5). `ScoLink` uses `setCommunicationDevice` on API 31+ and the legacy calls below that.
- [ ] Enable-time warning dialog. Per-vehicle enable, wake-phrase choice, and sensitivity (Low/Medium/High → threshold 0.7/0.5/0.3). The hotword pill on the car screen (M4.3).
- [ ] The car-mic test (M10.6), the 3-strike SCO-drop rule, and the `car_mic` row in the compatibility report.
- [ ] **Calls-only cars:** a "Speak through car speakers (call channel)" option (`speakOverCallChannel`, M3.3). It reuses `ScoLink` to open SCO only while BobCar is speaking.
- [ ] Yield to phone calls (M10.8).
- [ ] A battery-allowlist check (M10.7) and a debug screen that shows the battery %/hour estimate.
- [ ] Stop the hotword while Android Auto is projecting, unless the car's answer is BobCar (M1.6).

**Done criteria:**
- [ ] Parked, engine off, phone mic: "Hey Bob" detected at least 45 times out of 50 from the driver's seat.
- [ ] A detection starts the voice loop within 2 s, the same as tap-to-talk.
- [ ] Highway at about 65 mph: detection rate recorded, target at least 80%.
- [ ] A 4-hour drive with the radio on produces at most 2 false activations.
- [ ] Incoming and outgoing calls work normally while armed, and the hotword resumes within 5 s of hang-up.
- [ ] The hotword is never armed when car mode isn't active. After disconnect, `dumpsys` shows no `HotwordService`.
- [ ] Car-mic experiment: `scoHoldsReliably` and `car_mic` are recorded for the Taurus, and the music-pause behavior is documented from observation.
- [ ] The battery %/hour delta compared to hotword-off is measured and shown on the debug screen.

**In-car test plan (Taurus, Zenfone 8):**
1. **Phone mic, parked:** with the engine off, say "Hey Bob" 50 times and count detections. Repeat at idle.
2. **Phone mic, driving:** 50 utterances at highway speed. If detection is below 80%, retrain with in-car noise samples.
3. **False triggers:** a 4-hour drive with talk radio and music. Review the local detection log.
4. **Car mic (experimental):** enable "Use car mic" and run the car-mic test. Then drive 30 minutes and observe:
   - Does SYNC show a call screen?
   - Is music over A2DP paused?
   - How many times does SCO drop? Three or more drops should cause a fallback to the phone mic, `scoHoldsReliably = false`, and one notice.
5. **Calls:** with the hotword armed, place and receive a call. Ringing must be heard, call audio must be normal, and the hotword must resume within 5 s.
6. **Bluetooth off mid-drive:** car mode exits after the debounce and the hotword stops. Nothing keeps listening.
7. **Battery:** compare a 2-hour drive with the hotword on against one with it off. If the delta exceeds 8%/hour, ship with a stronger warning and a "pause when screen is off" option.

#### Phase 5c: Choose your AI

**Goal:** an optional conversational mode using the user's own provider account. The parser stays the default brain and handles every known command, and every AI failure falls back to it.

**Dependencies:** V1 (the parser). If Phase 5a hasn't shipped, this phase adds OkHttp and `INTERNET` itself.

**Build items:**
- [ ] `ai/` module per M12:
  - `AiProvider` and its implementations: Gemini, OpenAI, Anthropic, OpenRouter, Groq, Nano, and None.
  - `AiBrain` (M12.6).
  - `KeyVault` (§7.8), `ConsentStore`, and `UsageCounter`.
- [ ] A bundled `SYSTEM_PROMPT` asset.
- [ ] The "Choose your AI" screen (§9.6), reachable from Settings and from the "What's new in 1.1" card. It has key entry with validation states (`valid`, `unverified`, `invalid`), a live model picker, a usage counter, the consent screen, and a paid-provider warning.
- [ ] Nano row gated by `checkFeatureStatus()`, and the disabled "Sign in with ChatGPT" tab.
- [ ] The `ACTION_SEND` "Open in …" hatch.
- [ ] Backup exclusion for the `secrets` DataStore file (§7.8, §8.2).
- [ ] Timeouts: 3 s connect, 6 s overall; on 429, one retry after 1 s, then fail-soft.

**Done criteria:**
- [ ] After updating from V1, AI is off, the provider is "none", and the AI module makes no network calls (verify with a proxy).
- [ ] With a Gemini key and consent, "why is the sky blue" is answered within 6 s in about 30 words or fewer, spoken and shown.
- [ ] With AI on, "call Mom" opens the dialer through the parser, and the LLM isn't called.
- [ ] Airplane mode gives the fail-soft line plus parser help. A bad key gives the invalid notice. Declined consent means zero transmissions.
- [ ] Deleting the key wipes it and turns off AI mode.
- [ ] The Nano row is grayed out on the Zenfone 8, and the ChatGPT sign-in tab is disabled.

**Test plan:** run the 12 acceptance tests in M12.10.

#### Phase 5e: Driving detection (cars without Bluetooth)

**Goal:** in a car without Bluetooth, BobCar notices that the user started driving and offers car mode with one tap.

**Dependencies:** V1 (no-Bluetooth profiles and manual car mode). It uses `play-services-location`, which Phase 5a adds; add it here if 5a didn't ship.

**Build items:**
- [ ] `DrivingDetector` per M1.10.
- [ ] The `ACTIVITY_RECOGNITION` permission (on API 26–28, the Play services equivalent), asked only when the user turns on driving detection, with the §8.3 text.
- [ ] A per-app setting (`driving_detection_enabled`), offered in the "What's new" card and on no-Bluetooth vehicle profiles.

**Done criteria:**
- [ ] **F-350:** within a few minutes of starting to drive, a "Driving? Tap for car mode" notification appears. A tap opens car mode for the right vehicle.
- [ ] Riding as a passenger or on a bus produces only the notification, never an automatic launch.
- [ ] When the phone is also connected to a known Bluetooth car, Bluetooth wins and no driving notification appears.
- [ ] With the setting off, no activity updates are registered (`dumpsys activity`).

**In-car test plan:** three drives in the F-350 with detection on. Record how long until the notification appears and whether any false notifications show up at home.

#### Phase 5f: VIN entry, scan, and decoding

**Goal:** identify the car exactly without an adapter. The user types or scans the VIN, BobCar decodes it, and the user confirms.

**Dependencies:** V1. It needs internet, which 5a or 5c adds; add `INTERNET` here if neither shipped.

**Build items:**
- [ ] `VinValidator` (format plus the North American check digit) and `VinDecoder` (NHTSA vPIC). Phase 6 reuses both.
- [ ] The VIN screen per M13.6: typing, **Scan** with the on-device ZXing scanner (`CAMERA` asked only on tap; §8.3), where-to-find help, and confirmation.
- [ ] Entry points: vehicle detail, the "What's new" card, and an optional first-run step for new installs.
- [ ] The §8.3 VIN text before the first lookup.

**Done criteria:**
- [ ] Scanning and typing both produce a valid VIN on the Taurus and the F-350.
- [ ] Decoding suggests make, model, and year, and only **Use** saves them with `makeConfirmed = true`.
- [ ] Offline entry is saved and decoded later.
- [ ] A proxy shows vPIC receives only the masked VIN: position 9 and characters 12–17 are `*`.
- [ ] Denying the camera permission leaves typing fully usable.

**Test plan:** the M13.6 acceptance tests on both vehicles.

#### Phase 5d: Play Store update (1.1)

**Release checklist:**
- [ ] **New permissions.** `INTERNET`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_MICROPHONE`, `MODIFY_AUDIO_SETTINGS`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`, and `ACTIVITY_RECOGNITION`, and `CAMERA`, only for the sub-phases that ship.
- [ ] **Physical activity:** a justification for 5e ("offers car mode when you start driving a car without Bluetooth") and the Data safety entry.
- [ ] **Foreground service declaration** for `microphone` (if 5b ships): "listens for the user's wake phrase while car mode is on", with a short video.
- [ ] **Sensitive permissions:** location needs a core-functionality justification (weather) and a short demo video.
- [ ] **Data safety form updates:**
  - Approximate location is sent to Open-Meteo for weather (5a).
  - If AI ships (5c): transcript text is sent to the provider the user chooses, under the user's own account, only after consent. API keys are stored on-device only.
  - If VIN lookup ships (5f): only the model part of the VIN (no serial number) is sent to NHTSA vPIC. Scanning happens on the phone and sends nothing.
  - Update the §8.4 register, the Privacy & data screen, and the privacy policy for every sub-phase that ships.
- [ ] **Privacy policy** updated to name the same flows.
- [ ] **Attribution:** "Weather data by Open-Meteo.com" on the About screen. The free API is for non-commercial use. If the app ever gets ads or paid features, switch to Open-Meteo's commercial plan (verify at build time).
- [ ] **Release notes** listing what's new.
- [ ] **Rollout:** internal → full in-car regression of every V1 feature plus the new ones → closed → production.

**Done criteria:**
- [ ] The update passes review.
- [ ] The Data safety form and privacy policy match the shipped sub-phases.
- [ ] Existing V1 users keep their vehicle profiles and settings after updating, with no Room migration needed.

### 13. Post-V1 phases (hardware-gated)

> **Don't build these before V1 ships.** They need an OBD-II adapter (Part 5). V1 contains only the stubs in §6.5. The plan is to build them after 1.1; the order gets decided when V1 ships (§4).
>
> **Test vehicles (verify in each vehicle):**
> - The owner's **2004 Ford F-350**, expected to use SAE J1850 PWM, Ford's pre-CAN protocol. It's a heavy-duty pickup, a weight class the 1996 diagnostics rule didn't cover, so it may answer only part of the standard set (verify in the vehicle).
> - The **2009 Ford Taurus**, which uses ISO 15765-4 CAN.
>
> Together they cover the old and new protocol families. Nothing in these phases is Ford-specific.

#### Phase 6: OBD-II diagnostics (any car with an OBD-II port)

**Goal:** with any supported adapter, read trouble codes, show live data, read and decode the VIN, read freeze-frame data, and record the protocol per vehicle. **Read-only: no clearing codes** (wishlist, M14.1).

Phase 6 is split so it doesn't wait on buying four adapters:
- **6a** builds everything below, using one real adapter type (classic Bluetooth recommended) plus the Wi-Fi transport for the PC ELM327 emulator (§6.6).
- **6b** adds the remaining transports (BLE, USB, and Wi-Fi hardware adapters) as those adapters become available.

**Build items:** everything in M14. In summary:
- `ObdTransport` (M14.3): 6a builds classic Bluetooth and Wi-Fi; 6b builds BLE and USB.
- The diagnostics screen and voice rules (M14.13).
- The init sequence (M14.4).
- Modes 01, 02, 03, 07, and 09. **No mode 04.**
- The DTC parser, which handles both CAN and legacy framing, and offline code descriptions.
- Reading the VIN from the car, decoded with the 1.1 `VinDecoder` and offered for the user to confirm (M14.5).
- The live data poller.
- `DtcReading` (database version 2).
- The diagnostics screen and the `obd_*` probes.
- `INTERNET`, if release 1.1 didn't already add it.

**Done criteria:**
- [ ] **F-350:** init succeeds over J1850 PWM (expected `ATDP` reply "SAE J1850 PWM"; verify). Codes, live data, and VIN work wherever the truck answers. The capability check records which readings it doesn't support.
- [ ] **Taurus:** init succeeds over CAN (expected "ISO 15765-4 (CAN 11/500)"). RPM, speed, and coolant temperature each update at 2 Hz or faster while driving.
- [ ] Code descriptions resolve in airplane mode.
- [ ] The VIN decodes through vPIC, and the user can accept or edit the suggested make, model, and year.
- [ ] **6a:** the classic Bluetooth adapter works on both test vehicles, and the Wi-Fi transport works against the PC emulator.
- [ ] **6b:** each remaining transport (BLE, USB, Wi-Fi hardware) works with at least one real adapter.
- [ ] While the car reports a speed above 0, the diagnostics screen shows only the spoken-summary view (M14.13).
- [ ] A unit test proves no code path can send mode 04.

**In-car test plan (F-350 and Taurus):**
1. Plug in the adapter (under the dash, driver's side). Pair a classic Bluetooth adapter in Android Settings first; the PIN is usually 1234 or 0000. BLE, Wi-Fi, and USB adapters are picked inside BobCar.
2. Vehicle detail → **Pair diagnostics adapter**. The `obd_link` probe turns `PASS` with the protocol.
3. Engine running: read codes in airplane mode.
4. Read the VIN and accept the decoded make, model, and year.
5. Drive: watch live data and record the polling rate for each vehicle.
6. View the freeze frame if a code is stored.
7. Unplug the adapter mid-poll. "Link lost" appears, with no crash.

#### Phase 7: Make-specific extended data (all makes, from OBDb)

**Goal:** for any make the community has documented, read the extra values generic OBD doesn't expose (for example transmission temperature, oil life, or EV battery level), and record per-vehicle support honestly.

**Build items:** everything in M15.

**Done criteria:**
- [ ] The confirmed vehicle selects the right OBDb signal set, or reports "No community data for this vehicle yet".
- [ ] On each test vehicle, at least one extended value passes the sanity gate, or the capability check records exactly what didn't answer.
- [ ] When data needs hardware the adapter lacks (for example Ford's second data bus, MS-CAN), the UI says so instead of showing wrong data.
- [ ] OBDb is credited on the About screen.

**In-car test plan:** on both test vehicles, open the extended data tab. Check that values are plausible (transmission temperature near ambient on a cold start) and record what's refused.

### 14. Phase ordering and what can slip

**Why this order:**

1. **Phase 0 first.** Nothing builds without the scaffold, and the `targetSdk` decision is cheapest at project creation.
2. **Phase 1 before commands.** The car-mode lifecycle, voice loop, and capability check are the platform everything plugs into.
3. **Phase 2 before polish.** The polished UI has to surface every command's states, so the commands must exist first.
4. **Phase 3 before release.** Reviewers look at driver-distraction risk, so the day/night, keep-screen-on, and no-typing posture must be in the reviewed build.
5. **V1 ships without weather, the hotword, and AI.** That makes it the smallest app Play can review: no internet access, no location, no microphone service, and no third-party data flow. It also gets the core product into the car soonest, and its in-car results (cabin noise, recognizer accuracy) inform 1.1.
6. **In 1.1, weather goes first.** It's the simplest, and it adds internet and location. The hotword is second, because it benefits from V1's in-car data. AI is third: it has the largest policy surface and depends only on the parser. Driving detection (5e) can go any time, since it reuses 5a's location library. VIN entry and scan (5f) also stand alone. All are independent.
7. **Phases 6 and 7 are hardware-gated.** No adapter exists yet, and every day spent on OBD is a day the voice product isn't improving.
8. **Phase 8 (iPhone) comes after Android V1.** It mirrors phases already proven on Android. Its timing is decided when V1 ships (§4, Part 6).

**What can slip in V1, in order:**

1. **Phase 3 extras.** The manual day/night override and the TTS on/off setting can slip.
   - **Don't slip** `FLAG_KEEP_SCREEN_ON`, the minimum font sizes, or the no-text-entry rule.
2. **Phase 2 trims.** "Go home" can slip.
   - **Don't slip** call, text, navigate, media transport, or time.
3. **Phase 1 copy.** Explanatory text in the report can be terse.
   - **Don't slip** the report itself, the pre-seeded Taurus wheel-button row, the car-audio check, or the OBD presence row.

**Never slip in V1:** "My car has no Bluetooth" with manual car mode. It's what makes V1 usable in any car.

**What can slip within 1.1:** any of 5a, 5b, 5c, 5e, or 5f can ship alone. The car-mic experiment can slip separately from the phone-mic hotword.

**Never slip:**
- Phase 0 build health.
- Phase 1 auto-launch reliability, including the notification fallback.
- The Phase 2 core commands.
- The explicit fallbacks: tap-to-talk always available, and `ACTION_DIAL`/`ACTION_SENDTO` handoffs instead of privileged permissions.
- In 1.1, a foreground service plus notification for anything that listens.

---

## Part 4: Mechanics

Each mechanic is a build-ready spec. Field names, keys, and status values always come from §7.

### M1. Bluetooth auto-launch

#### M1.1 Purpose

Detect when the phone connects to, or disconnects from, a known car's Bluetooth, and enter or exit car mode automatically. On the Taurus this is the **primary trigger**, replacing the steering-wheel button, which can't be intercepted (§3).

This mechanic must not touch wheel-button logic (M11) or the hotword engine (M10).

#### M1.2 Inputs and outputs

| Input | Source |
|---|---|
| `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` (with `EXTRA_DEVICE`) | System broadcast to a **manifest** receiver. These actions are on Android's implicit-broadcast exemption list, so they arrive even when the app isn't running. |
| `BluetoothAdapter.ACTION_STATE_CHANGED` | Registered dynamically by `CarModeService` while in car mode |
| Vehicle profiles (`btMac`, `cdmAssociationId`, `isDefaultVehicle`, `projectionChoice`, `projectionAsked`) | Room (§7.2) |
| `unknown_car_prompt`, `stay_open_on_disconnect` | DataStore (§7.7) |
| `Settings.canDrawOverlays()`, notification permission | System |

| Output | Destination |
|---|---|
| `CarModeService` started or stopped | This mechanic |
| `CarModeActivity` opened, or the "Tap to open car mode" notification | M4 |
| TTS "Connected to <btName>" / "Disconnected" | M2 (`TtsEngine`) |
| Quick probe | M3 |
| `CarSession` state (`activeVehicle`, `connected`) | Everything that observes the drive |

#### M1.3 Companion device association (`CompanionLink`)

**Why:** on Android 12+ (API 31+) an app can't start a foreground service from the background. One documented exemption is an app that uses CompanionDeviceManager and declares `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` (or `REQUEST_COMPANION_RUN_IN_BACKGROUND`). Associating each car is what lets the ACL receiver start `CarModeService`. Association doesn't need any location permission, though Location Services must be on while the system dialog scans.

**When:** during first run, after the car is picked (§9.1 step 4), and from vehicle detail → **Link car** if it failed.

```kotlin
fun associate(activity: ComponentActivity, vehicle: VehicleProfile, launcher: ActivityResultLauncher<IntentSenderRequest>) {
    val cdm = activity.getSystemService(CompanionDeviceManager::class.java)
    val filter = BluetoothDeviceFilter.Builder().setAddress(vehicle.btMac).build()
    val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
    if (Build.VERSION.SDK_INT >= 33) {
        cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(sender: IntentSender) =
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            override fun onAssociationCreated(info: AssociationInfo) =
                repo.setCdmAssociationId(vehicle.id, info.id)
            override fun onFailure(error: CharSequence?) = repo.recordAutoLaunchWarn(vehicle.id, error)
        })
    } else {
        @Suppress("DEPRECATION")
        cdm.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(sender: IntentSender) =
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            override fun onFailure(error: CharSequence?) = repo.recordAutoLaunchWarn(vehicle.id, error)
        }, null)
        // On RESULT_OK: confirm via (deprecated) cdm.associations containing vehicle.btMac; store a sentinel id (0).
    }
}
```

**Rules:**
- **Check association status at runtime.** On API 33+ use `cdm.myAssociations`; below that use `cdm.associations`. The stored `cdmAssociationId` is a cache, not the source of truth.
- **The phone should be connected to the car during association.** Whether the system dialog lists an already-bonded, connected head unit is device-dependent (verify in the car). If association fails, record `auto_launch` = `WARN` and use notification mode (M1.4).
- **Don't use CompanionDeviceService presence observation.** That API changed in Android 16. The manifest ACL receiver already covers connect and disconnect.

#### M1.4 Connect handling

**`CarConnectionReceiver`** (manifest, `exported="true"`; the actions are protected system broadcasts):

```kotlin
override fun onReceive(ctx: Context, intent: Intent) {
    val device: BluetoothDevice = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
    val pending = goAsync()                                   // Room lookup is async; keep the receiver alive
    appScope.launch {
        try {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> CarConnectionHandler.onAclConnected(ctx, device)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> CarConnectionHandler.onAclDisconnected(ctx, device)
            }
        } finally { pending.finish() }
    }
}
```

**`CarConnectionHandler.onAclConnected(ctx, device)`:**

1. **Match the car.** `val mac = device.address.uppercase()`, then `repo.findByMac(mac)`. If there's no match:
   - If `unknown_car_prompt` is on **and** the device's Bluetooth class is car audio (`BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO` or `AUDIO_VIDEO_HANDSFREE`), post a low-priority "Add <name> as a car?" notification.
   - Otherwise return. OBD adapters and headsets never match (M1.7).
2. **Android Auto check.** If `AndroidAutoDetector.isProjecting` is true, follow the vehicle's `projectionChoice` (M1.6):
   - `"android_auto"`: log the event, post "Android Auto is running, so BobCar is off for this drive", and return.
   - `"ask"`: post the M1.6 question and return.
   - `"bobcar"`: continue.
3. **Enter the session.** Call `CarSession.enter(vehicle)`, which sets `activeVehicle` and `connected = true` and cancels any pending disconnect debounce (M1.5).
4. **Start the foreground service:**
   ```kotlin
   try {
       ContextCompat.startForegroundService(ctx, CarModeService.enterIntent(ctx, vehicle.id))
   } catch (e: IllegalStateException) {   // ForegroundServiceStartNotAllowedException on 31+ (car not associated)
       // Fall through: the notification below opens the activity, which then starts the service while visible.
   }
   ```
5. **Open the car screen.** This is subject to Android's background-activity-launch rules (Android 10+):
   ```kotlin
   val canOpenDirectly = Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(ctx)
   if (canOpenDirectly) {
       ctx.startActivity(Intent(ctx, CarModeActivity::class.java)
           .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
           .putExtra(EXTRA_VEHICLE_ID, vehicle.id))
   } else {
       Notifications.postTapToOpen(ctx, vehicle)   // high-importance; contentIntent = PendingIntent.getActivity(CarModeActivity)
   }
   ```
   - `SYSTEM_ALERT_WINDOW` granted by the user is a documented exception to the background-launch block. A notification tap is another.
   - Don't rely on any undocumented exemption.
   - When the service is running, the tap-to-open action lives on its foreground notification. The separate high-importance notification is only for when the service couldn't start.

**`CarModeService`:**
- In `onStartCommand(ENTER)`, call `ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)` immediately, inside the 5 s window.
  - Notification text: "BobCar · connected to <btName>".
  - Actions: **Open car mode** and **Quit car mode**.
- Then:
  - Speak the TTS greeting "Connected to <btName>".
  - Run `CapabilityChecker.quickProbe(vehicle)`.
  - Register a dynamic receiver for `BluetoothAdapter.ACTION_STATE_CHANGED`.
  - Register for `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` so `audio_route` can finish its check (M3.3).
- **The service runs only while connected to a Bluetooth car.** It never runs for cars without Bluetooth, for manual sessions with no car connected, at boot, or around the clock. `CallStateWatcher` and `AudioFocus` belong to `CarSession` and work without the service (M2.4).
- `onDestroy()` unregisters receivers and calls `CarSession.exit()`.

**`CarModeActivity`**, when opened from the tap-to-open notification for a **connected Bluetooth car**: if `CarModeService` isn't running (for example, the start from background was blocked), it starts the service from `onStart()`. That's allowed because the app is visible.
- In every other case, including manual sessions and cars without Bluetooth, car mode runs with the activity alone and no service.
- Every car-mode entry runs the quick probe (M3.2).

#### M1.5 Disconnect handling

- On `ACTION_ACL_DISCONNECTED` for the **active** vehicle's MAC, start an **8 s debounce**. SYNC Gen 1 sometimes drops and re-establishes the link briefly on ignition cycles.
  - The debounce runs in `CarModeService`'s scope. If the service isn't running, just cancel the tap-to-open notification.
- If the same MAC reconnects within 8 s, cancel the debounce. Nothing happens: no greeting, no relaunch.
- When the debounce expires:
  1. Speak "Disconnected". From release 1.1, also stop `HotwordService` (M10).
  2. Clear `CarSession.connected`.
  3. Stop `CarModeService`.
  4. Finish `CarModeActivity` through the shared `CarSession` flow, unless `stay_open_on_disconnect` is on.
- `BluetoothAdapter.ACTION_STATE_CHANGED` → `STATE_OFF` while in car mode is treated as a disconnect, with no debounce.

#### M1.6 Android Auto: the driver's choice

- `AndroidAutoDetector` observes `androidx.car.app.connection.CarConnection(context).type`. `CONNECTION_TYPE_PROJECTION` means projecting.
  - This is detection only. BobCar declares no `CarAppService`, so it isn't a car app and doesn't enter the automotive review track. It can't appear on the Android Auto screen (§3).
- **Each car keeps its own answer** in `VehicleProfile.projectionChoice`: `"ask"` (the default), `"android_auto"`, or `"bobcar"`.
- **Projection can start at connect or during car mode.** Android Auto often starts a few seconds after Bluetooth connects, so check at both points and apply the same rule.
- **`"ask"`** (the first time Android Auto runs in a car): BobCar posts one notification, "Android Auto is running. Which do you want in this car?", with two buttons, **Android Auto** and **BobCar**. It doesn't speak the question or open a screen for it. Until the driver answers, the drive behaves as `"android_auto"`.
  - A tap saves the answer for that car and applies it right away. **BobCar** opens car mode (a notification tap is allowed to open a screen, M1.4).
  - With no tap, the question comes back on the next drive. After three unanswered drives (`projectionAsked` = 3), BobCar saves `"android_auto"` and stops asking, because the app never nags (§1).
  - The vehicle screen's **In Android Auto, use** row changes the answer at any time (M13.1).
- **`"android_auto"`:** BobCar steps aside.
  - Exit car mode quietly: no TTS, and (release 1.1) stop the hotword.
  - Leave the app usable from the launcher for a passenger.
- **`"bobcar"`:** car mode runs as usual on the phone's screen while Android Auto keeps the car's screen.
  - The wheel button goes to Google's assistant inside Android Auto, not to BobCar (M11.3). Tap-to-talk works, and "Hey Bob" works from release 1.1.
  - Android Auto carries the phone's audio, so BobCar's voice should play through the car speakers. Handoffs to the dialer, messaging, and Maps behave as they normally do while Android Auto runs. Verify both on the Desktop Head Unit and in an Android Auto car.
  - If projection stops mid-drive, nothing changes.

#### M1.7 Edge cases

- **Car Bluetooth and the OBD adapter are different devices with different MACs.** Auto-launch matches **only** `btMac`. The adapter's MAC lives in `obdAdapterAddress`, and `VehicleRepository` refuses to save a profile whose `btMac` equals any `obdAdapterAddress`. Add a regression test: an ACL connect from an OBD adapter never launches car mode.
- **Delayed or batched ACL broadcasts.** Some stacks delay `ACL_CONNECTED` until profiles connect. React to ACL only; don't add profile-state listeners as a second trigger (double-launch risk). `singleTask` deduplicates accidental double opens.
- **Multiple cars.** Match by `btMac`. The matching profile becomes the active vehicle for the session, and its flags drive behavior.
- **`BLUETOOTH_CONNECT` revoked mid-drive.** Reading the device's name throws `SecurityException`. Catch it, keep car mode running on the last-known active vehicle, and show a one-time re-grant prompt.
- **Phone locked.** `CarModeActivity` shows over the lock screen (`showWhenLocked`/`turnScreenOn`). Launching another app from the locked car screen (dialer, Maps) calls `KeyguardManager.requestDismissKeyguard()` first (M2.7).
- **Notifications denied (API 33+) and no overlay permission.** Auto-launch can't surface anything. M3 reports `auto_launch` = `WARN` with an **Open settings** button.
- **User force-stops the app.** Android doesn't deliver broadcasts to a force-stopped app until the user opens it again. The app can't detect this state, so the auto-launch row's help text always says: "If you force-stop BobCar, open it once to turn auto-launch back on."

#### M1.8 Acceptance tests (Taurus, Zenfone 8)

1. **Cold launch:** reboot the phone and don't open the app. Start the car. The car screen, or the notification when there's no overlay permission, appears within 5 s of the logged `ACL_CONNECTED`, and at most 30 s after ignition. TTS says "Connected to Taurus".
2. **Debounce:** turn the ignition off and on within 5 s. Car mode doesn't exit and doesn't re-greet.
3. **Disconnect:** turn the car off. Within 8–9 s, TTS says "Disconnected", the activity finishes, and `dumpsys` shows no `CarModeService`.
4. **No overlay permission:** revoke it and start the car. The notification appears, and a tap opens car mode with the service running.
5. **Not associated (API 31+ device):** remove the association and start the car. There's no crash and the high-importance notification appears. Tap it, and car mode plus the service start.
6. **OBD adapter exclusion:** bond a Bluetooth OBD adapter (or a stand-in device) and connect it. Car mode doesn't launch and a log line confirms the exclusion.
7. **Android Auto choice** (Desktop Head Unit, or simulated projection):
   - With the car's answer on `"ask"`, connect. There's no car screen and one notification with **Android Auto** and **BobCar**.
   - Tap **BobCar**. Car mode opens, and the next connect skips the question.
   - Change the answer to Android Auto on the vehicle screen and connect again. There's no car screen and one notification.
   - With `"android_auto"`, start projection during car mode. Car mode exits quietly.
   - Leave the question unanswered for three connects. The answer becomes `"android_auto"` and the question stops.
8. **Multi-car:** bond a second car, or a Bluetooth speaker with an added profile. Connecting switches the active vehicle and the greeting uses its name.
9. **Manual override:** force-stop the app, then open car mode from the launcher with no car connected. It works, `dumpsys` shows no `CarModeService`, and Quit returns to the launcher.

#### M1.9 Non-goals

- No pairing, unpairing, or PIN entry. The user pairs in Android Settings.
- No steering-wheel button handling (M11) and no hotword or SCO handling (M10).
- No Android Auto or head-unit UI.
- No foreground service while not connected to a car.

#### M1.10 Cars without Bluetooth

A vehicle profile with `btMac = null` is a car without Bluetooth, such as the F-350. Everything except the automatic Bluetooth trigger works the same: the phone's own mic and speaker carry the voice loop.

**12 V adapter tip.** A 12 V Bluetooth FM transmitter or aux adapter plays the phone's audio through the car radio, and many also carry calls. If the user pairs one, add it as the car's Bluetooth (**Add Bluetooth** on the vehicle). The car then gets automatic launch and car-speaker audio. The `audio_route` note mentions this.

**V1: manual car mode.**
- The launcher's **Enter car mode** opens car mode for the default vehicle, or shows a vehicle picker if there are several.
- **Car mode runs with `CarModeActivity` alone, with no foreground service.** The `connectedDevice` service type is for a connected Bluetooth device, and it requires the Bluetooth permission on Android 14+. Neither applies here.
- Car mode ends with **Quit**, the back button, or (1.1) the driving-detection exit rule below. If the user switches to Maps or the dialer, car mode is simply in the background until they come back.
- `auto_launch` = `FAIL("This car has no Bluetooth, so car mode opens from the launcher.")`, meaning not supported by this car. From 1.1 it's `WARN` with **Turn on driving detection**.

**Release 1.1 (Phase 5e): driving detection (`DrivingDetector`).**
- **Registration.** Uses the Google Play services Activity Recognition Transition API. When `driving_detection_enabled` is on and at least one no-Bluetooth profile exists, it registers for `IN_VEHICLE` `ENTER`/`EXIT` transitions with a `PendingIntent` to an explicit receiver.
  - It needs `ACTIVITY_RECOGNITION` (API 29+; the Play services permission on 26–28), with the §8.3 text.
  - It only posts a notification; it never starts a service. A tap opens car mode as a manual session.
- **On `ENTER`:** if no known Bluetooth car is connected, post a "Driving? Tap for car mode" notification for the default no-Bluetooth vehicle.
  - **Never open car mode automatically.** Detection can't tell your car from a bus or a friend's car.
  - Don't post again within 30 minutes of a dismissed notification. Keep that timestamp in memory; after process death, one extra notification is acceptable.
- **On `EXIT`:** if car mode is open for a no-Bluetooth vehicle, end it after 2 minutes unless the user is still interacting.
- **Bluetooth wins.** If a known Bluetooth car connects, ignore driving detection for that drive.
- **No connection at all.** Detection works offline, on the phone alone, and uses no location.

### M2. Voice loop and command parser

#### M2.1 Purpose

This is the single voice pipeline every trigger feeds into: the Talk button, the wheel button (M11), and from release 1.1 the hotword (M10). It listens, transcribes with live partial results, routes the text through the command parser, and speaks and displays the result. From 1.1, unknown utterances go to `AiBrain` when AI mode is on.

Triggers only ever call `CarSession.voice.startListening()`.

#### M2.2 `VoiceController` (process singleton, held by `CarSession`)

- **State machine:** `IDLE → LISTENING → PROCESSING → SPEAKING → IDLE`, exposed as `StateFlow<VoiceUiState>`.
- **Trigger arbitration.** `startListening()` is a no-op (logged) unless the state is `IDLE`. Two triggers within 300 ms (Talk tap, wheel button, or from 1.1 a hotword detection) collapse to one session.
  - A Talk tap while `SPEAKING` stops TTS and starts listening.
  - Release 1.1: while `SPEAKING`, hotword detections are ignored, plus a 500 ms cooldown afterwards, so BobCar's own voice can't trigger the mic.
- **Before listening:**
  1. `tts.stop()`.
  2. `AudioFocus.forListening()` (M2.4) so music pauses and doesn't drown out the driver.
  3. Release 1.1: ask `HotwordService` to pause capture (`CarSession.hotword.pause()`), because two simultaneous recordings conflict and Android silences one. In V1, `CarSession.hotword` is a no-op object.
  4. Wait 150 ms, then call `VoiceEngine.start()`.
- **After returning to `IDLE`:** release audio focus so music resumes, and call `CarSession.hotword.resume()` (a no-op in V1).
- **Cancel.** The `Cancel` command (M2.5), or a Talk tap during a pending follow-up or disambiguation, clears the pending state. Speak "Okay."
- Tap-to-talk works with or without `CarModeService`, for example in a manual car-mode session.

#### M2.3 `VoiceEngine` (wraps `SpeechRecognizer`; main thread only)

```kotlin
val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
recognizer.setRecognitionListener(listener)
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)   // preference only; network used if no offline pack
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
}
recognizer.startListening(intent)
```

- **On-device first (§8.4).**
  - On API 31+, if `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` is true, create the recognizer with `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)`. Audio then never leaves the phone.
  - On API 33+, if the language pack is missing, offer `triggerModelDownload(intent)` from settings.
  - Otherwise (older Android, or no on-device service), use `createSpeechRecognizer` only when `cloud_speech_allowed` is true. If it's false, Talk shows "Voice needs Google speech on this phone. Turn it on in Settings → Privacy & data," and every button keeps working.
  - Verify on the Zenfone 8 which path it takes.
- **Pre-flight.** `SpeechRecognizer.isRecognitionAvailable(context)`. This needs the `RecognitionService` `<queries>` entry (§8.2) on Android 11+, or it always returns false. If it's false, show "Voice input unavailable on this phone; the buttons still work."
- **`onPartialResults`:** emit `Listening(partialText)` and show it live as "Heard: …".
- **`onResults`:** take the best result, set the state to `PROCESSING`, then run `CommandParser.parse()` and `CommandExecutor` (M2.6). Speak the result.
- **`onError` mapping:**

  | Error | Spoken and shown |
  |---|---|
  | `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT` | "Didn't catch that. Try again." After 3 in a row: "It's noisy. Try the big buttons." Then stop auto-prompting. |
  | `ERROR_NETWORK`, `ERROR_NETWORK_TIMEOUT` | "No connection. Try again, or use the buttons." |
  | `ERROR_RECOGNIZER_BUSY`, `ERROR_CLIENT` | Destroy and recreate the recognizer, then retry once silently |
  | `ERROR_INSUFFICIENT_PERMISSIONS` | Route to the `RECORD_AUDIO` rationale |
  | Anything else | "Something went wrong. Try again." |

- **Lifecycle.** Recreate the recognizer every 20 sessions (some OEMs leak), and `destroy()` it when the session ends.
- **Offline-pack status (API 33+).** Use `recognizer.checkRecognitionSupport(intent, executor, callback)` to show "Offline voice: installed / not installed" in settings, with a link to the system voice-input settings. Below API 33, hide that line.

#### M2.4 `TtsEngine` (wraps `TextToSpeech`)

- **Init:** `TextToSpeech(appContext) { status -> … }`. This needs the `TTS_SERVICE` `<queries>` entry on Android 11+.
  - Call `setLanguage(Locale.US)`. On `LANG_MISSING_DATA` or `LANG_NOT_SUPPORTED`, try `Locale.getDefault()`.
  - If that also fails, set "Voice output unavailable" and fall back to text only.
- **Local voice only (§8.4).** After init, pick a voice for the locale with `!voice.isNetworkConnectionRequired` (from `tts.voices`) and `setVoice()` it. Spoken text such as contact names and messages then never goes to a cloud voice service. If no local voice exists, use text only and say so in settings.
- **Audio attributes:** `setAudioAttributes(USAGE_ASSISTANT, CONTENT_TYPE_SPEECH)`. Apply `tts_rate`.
- **Routing:** when the car plays phone audio (A2DP), speech goes to the car speakers automatically. Otherwise it plays from the phone speaker.
  - For calls-only cars and cars without Bluetooth, that's the V1 behavior.
  - Release 1.1 can speak through the car's call channel instead (`speakOverCallChannel`, M3.3).
- **`speak(text)`:** `tts.speak(text, QUEUE_FLUSH, null, utteranceId)`. `UtteranceProgressListener.onDone` moves the state from `SPEAKING` to `IDLE`.
- **Settings:** if `tts_enabled` is false, skip speaking (text always shows) and go straight to `IDLE`.
- **Audio focus (`AudioFocus`, V1):**
  - **Speaking:** before `speak()`, request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with an `AudioFocusRequest` (`USAGE_ASSISTANT`), so music ducks. Abandon focus in `onDone`/`onError`.
  - **Listening:** request `AUDIOFOCUS_GAIN_TRANSIENT` while `LISTENING`, so most players pause, and abandon it when recognition ends so they resume.
  - If focus is denied (for example during a call), don't speak; show the text.
- **Phone calls (`CallStateWatcher`, V1):** never speak during a call; show the text only. BobCar doesn't request `READ_PHONE_STATE`, so detection is:
  - **API 31+:** `AudioManager.addOnModeChangedListener`. `MODE_RINGTONE` or `MODE_IN_CALL` means a call is active.
  - **API 26–30:** `PhoneStateListener` with `LISTEN_CALL_STATE`, which needs no permission on those levels.
  - `CarSession` owns the watcher while car mode is open. The 1.1 hotword (M10.8) reuses it and adds its own recording-silenced check.
- **Shutdown:** `shutdown()` when the session ends.

#### M2.5 Command grammar (canonical)

`CommandParser` is pure Kotlin with no Android dependencies, and it's unit-tested.

**Normalization** (for matching only):
1. Lowercase.
2. Replace `’` with `'`.
3. Strip `. , ! ?`.
4. Collapse whitespace and trim.

**Extract arguments from the original transcript.** Contact names, message bodies, and destinations keep their original case and punctuation; only the prefix length is taken from the normalized copy.

Rules are tried **in this order**. The first match wins.

| # | Matches (normalized transcript) | Command |
|---|---|---|
| 0 | Release 1.1: strip a leading wake phrase ("hey bob", "ok bob", "hey car") before matching | — |
| 1 | Exactly `help`, `what can you say`, `what can you do` | `Help` |
| 1a | Exactly `cancel`, `never mind`, `nevermind`, `forget it` | `Cancel`: clear any pending follow-up or choice, then say "Okay." |
| 2 | Starts with `call `, `dial `, `phone ` | `Call(query = rest)`. If `rest` is a phone number (7–15 digits after removing spaces, dashes, dots, and parentheses), dial it directly with no contact lookup. If `rest` is blank, say "Who should I call?" |
| 3 | Starts with `text `, `message `, `send a text to `, `send a message to `. Token 1 is the contact, the rest is the body, minus a leading `saying ` or `that ` | `Text(query, body)`. A phone number works as the contact, like rule 2. Empty body triggers the follow-up (M6) |
| 4 | Exactly `go home`, `take me home`, `navigate home`, `navigate to home` | `GoHome` |
| 5 | Starts with `navigate to `, `directions to `, `go to `, `take me to `. Or exactly `navigate` / `navigate to` (empty destination) | `Navigate(destination = rest)` |
| 6 | Exactly `play`, `pause`, `resume`, `play music`, `pause music`, `resume music` | `MediaToggle` |
| 7 | Exactly `next`, `skip`, `next song`, `skip song` | `MediaNext` |
| 8 | Exactly `previous`, `back`, `previous song`, `go back` | `MediaPrevious` |
| 9 | Exactly `weather`, `weather today`, `what's the weather`, `whats the weather`, `how's the weather`, `is it raining`; or starts with `what's the weather` | `Weather`. V1 replies "Weather is coming in a later update." Release 1.1 runs M9 |
| 10 | Exactly `time`, `what time is it`, `what's the time`, `whats the time`, `current time` | `Time` |
| 11 | Exactly `check engine`, `check engine light`, `read codes`, `diagnostics` | `Diagnose` |
| 12 | Anything else | `Unknown(raw)` |

**Must not match:**

| Utterance | Why it must not match |
|---|---|
| "callback", "dialog" | No `call `/`dial ` prefix with a space. ("call me" does match rule 2 and harmlessly finds nobody: "I couldn't find 'me'.") |
| "textbook", "texting while driving", "messages" | No `text `/`message ` prefix with a space |
| "go home screen" | Rule 4 is exact-match only |
| "stop" | Ambiguous with stopping navigation; not a V1 command |
| "weather channel", "timer", "what time does Walmart open" | Rules 9 and 10 are exact-match only, so these fall to `Unknown` |
| "navigation settings" | No rule-5 prefix |

**`Unknown` and `Help`** speak the help text for the current release:
- **V1:** "I can call, text, play music, navigate, or tell you the time. Say 'help' for more."
- **Release 1.1 (once weather ships):** "I can call, text, play music, navigate, or tell you the weather or time. Say 'help' for more."
- **Release 1.1, AI mode on:** `Unknown` utterances go to `AiBrain` instead (M12).

#### M2.6 `CommandExecutor` and `SpokenResult`

- `suspend fun execute(c: Command): SpokenResult` dispatches to exactly one action class.
  - `SpokenResult(text: String, display: String = text, buttons: List<UiChoice> = emptyList())`.
  - TTS and the big status line use the same string unless `display` adds detail, such as a formatted number.
- **Choices.** Actions that need a choice (contact disambiguation) return up to 3 `UiChoice` buttons. Tapping one calls back into the action. In V1 there's no spoken selection ("number two").
- **Pending follow-ups.** `TextAction` (missing body), `NavAction` (missing destination), and in 1.1 `WeatherAction` (ZIP) can set a pending state. The next utterance fills it, unless it's `Cancel`. It expires after 60 s or when car mode exits.

#### M2.7 `ExternalLauncher` (opening the dialer, messaging, Maps)

Android blocks activity launches from the background (Android 10+). When the hotword fires while Maps is in front, BobCar is in the background.

```kotlin
fun launch(intent: Intent, label: String): LaunchResult {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val allowed = CarSession.carScreenResumed || Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(ctx)
    if (!allowed) { Notifications.postAction(ctx, intent, label); return LaunchResult.NEEDS_TAP }
    if (keyguard.isKeyguardLocked && CarSession.carScreenResumed) {
        keyguard.requestDismissKeyguard(carActivity, callbackThatStarts(intent)); return LaunchResult.LAUNCHED
    }
    return try { ctx.startActivity(intent); LaunchResult.LAUNCHED }
           catch (e: ActivityNotFoundException) { LaunchResult.NO_APP }
}
```

`NEEDS_TAP` speaks: "Tap the notification to finish." The notification label says, for example, "Call Mom" or "Navigate to Walmart".

In V1 this path is rare, because commands start from a visible car screen (Talk) or a visible trampoline activity (wheel button). It becomes common in release 1.1, when "Hey Bob" can fire while Maps is in front. Build it in V1 anyway, so the behavior is already tested.

#### M2.8 Edge cases

- **No `SpeechRecognizer`** (no Google app or GMS): the Talk button shows a disabled state with an explanation. TTS and every other button still work. Never crash on `createSpeechRecognizer`.
- **Dead zones:** the `ERROR_NETWORK` prompt says so, and the last transcript stays visible.
- **No TTS engine:** text-only output, with a one-time settings hint.
- **Locale:** the recognizer follows the system locale, and TTS is pinned to `Locale.US` with a fallback. Non-English locales are untested in V1; say so in the store listing.

#### M2.9 Acceptance tests (Zenfone 8, parked Taurus, engine on, fan on medium)

1. Tap Talk and say "what time is it". The partial transcript appears live, then the time is spoken and shown. Total time is 4 s or less.
2. Speak a slow 6-word phrase. The status line updates at least twice before the final result.
3. Force each `onError` path (airplane mode, silence, revoked microphone). The prompt matches the table, and the state returns to `IDLE` with no stuck spinner.
4. Say "tell me a joke". The V1 help prompt is spoken and shown. "What's the weather" gives the V1 "coming in a later update" reply.
5. Double-tap Talk quickly (and in 1.1, tap Talk then immediately trigger the hotword). Exactly one session runs and one result is processed.
6. Disable the Google app. Talk shows the unavailable state, every other button works, and nothing crashes.
7. The greeting is audible through the Taurus speakers at normal volume with SYNC on Bluetooth Audio. Also record what happens with the radio source selected.
8. Parser unit tests cover every row in M2.5 and every must-not-match example.

#### M2.10 Non-goals

- **No hands-free SMS send** (M6) and no direct dialing (M5).
- **No listening outside car mode.** The loop is only armed from car-mode surfaces, plus the manual "Enter car mode" path.
- **No conversational AI in V1.** That's M12, opt-in, in release 1.1.

### M3. Capability check

#### M3.1 Purpose

One place that answers "what does this car (and this phone) support?" for each vehicle. It stores results in `capabilityCheckResults` (§7.3) and renders the compatibility report (§9.3). It never blocks car-mode launch, and it never nags.

#### M3.2 Triggers (exactly three)

1. **Quick probe: automatic on every car-mode entry.** A Bluetooth connect starts it from `CarModeService`; a manual or driving-detection entry starts it from `CarModeActivity`. It runs `voice`, `auto_launch`, and `audio_route` only, then posts the cached report to the car screen's status chip.
   - Total time under 3 s on `Dispatchers.IO`.
   - It's non-interactive: no SCO, no OBD connection, no wheel test.
2. **Full check: on demand.** Report → **Re-check**, or Settings → vehicle → **Run systems check**.
   - It runs every non-interactive probe for the build: `voice`, `auto_launch`, `audio_route`, `obd_link`, and `extended_pids`.
   - The interactive tests (`wheel_button` M11.5, `car_mic` M10.6) have their own buttons on their rows. They never run as a side effect of Re-check.
3. **Post-V1 only: OBD re-probe** when the user selects an adapter, or when a bonded device matching the adapter-name pattern changes to `BOND_BONDED` while the app is running. It runs the `obd_*` and `extended_pids` probes with a transient "Checking OBD…" indicator.

**Not triggered by:** app updates, schedules, or background timers. A failed probe is never retried in the background.

#### M3.3 Probes

**`voice`:**
- Uses `SpeechRecognizer.isRecognitionAvailable()` and a `TextToSpeech` init with `SUCCESS`. Call `shutdown()` on the test engine afterwards.
- `RECORD_AUDIO` must be granted.
- `PASS`: "Speech recognition and voice output are ready."
- `WARN`: "Microphone permission is off." with a **Fix** button.
- `FAIL`: "This phone has no speech recognizer" or "This phone has no text-to-speech engine." These are phone limitations, and the note says so.

**`auto_launch`:**

| Condition | Status | Note | Button |
|---|---|---|---|
| Associated (or API < 31), overlay allowed (or API < 29), notifications OK | `PASS` | "Car mode opens automatically when you start the car." | — |
| Overlay not allowed | `WARN` | "Car mode opens from a notification. Allow 'Display over other apps' to open it automatically." | **Allow** |
| API 31+ and not associated | `WARN` | "Link your car so BobCar can start in the background." | **Link car** |
| Notifications denied (33+) and no overlay | `WARN` | "Turn on notifications so BobCar can tell you car mode is ready." | **Open settings** |
| Car without Bluetooth (`btMac == null`) | V1: `FAIL`. 1.1: `WARN` | "This car has no Bluetooth, so car mode opens from the launcher." From 1.1: "…or turn on driving detection." | 1.1: **Turn on driving detection** |

**`audio_route` (does the car play phone audio?):**
- **How it checks.** Get profile proxies with `BluetoothAdapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)` and `BluetoothProfile.HEADSET`. Then check the car device with `getConnectionState(device)`. This needs `BLUETOOTH_CONNECT` (31+); close the proxies afterwards.
- **Timing.** The car's music audio (A2DP) connects a few seconds after the first link (ACL), so a check at connect time is often too early.
  - If A2DP is already connected, record `PASS` immediately.
  - Otherwise wait up to 15 s for `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` → `STATE_CONNECTED` for this device.
  - Record "calls only" only if A2DP never connects during the drive. Check again when the drive ends, and keep the previous result until then.
- **The quick probe doesn't block on this wait.** It shows the cached result and updates when the wait finishes.

| Result | Status | Note | Button |
|---|---|---|---|
| A2DP connected | `PASS` | "Your car plays BobCar's voice and music over Bluetooth." | — |
| Hands-free only for the whole drive (calls-only car) | `WARN` | "Your car's Bluetooth only handles calls, so BobCar speaks from the phone." | 1.1: **Use car speakers (call channel)** |
| Car without Bluetooth | `FAIL` | "This car has no Bluetooth, so BobCar speaks from the phone. A 12 V Bluetooth adapter can add car-speaker audio." | — |

- **Call-channel voice (release 1.1).** Turning on `speakOverCallChannel` makes BobCar open SCO (`ScoLink`, M10.5) only while speaking, then close it.
  - The option's text warns: "Your car may show a call screen while BobCar talks, and music pauses."
  - The car's microphone isn't used unless the car-mic hotword is also on.

**`wheel_button`:** written only by the M11 test or a `KnownVehicleNotes` pre-seed. The default is `NOT_TESTED`: "Not tested yet. Press 'Test' while connected to the car."

**`car_mic` (release 1.1):** written only by the M10.6 test or the 3-strike rule. The default is `NOT_TESTED`. The row is hidden in V1.

**`obd_link`:**
- **V1:**
  1. Scan `bondedDevices`, excluding every profile's `btMac`.
  2. Match names case-insensitively against `OBD|ELM|V-?LINK|VEEPEAK|BAFX|VGATE|OBDLINK`.
  3. **Never open a connection.**
  - **Pre-1996 vehicle** (`SupportPolicy`): `FAIL`, "Not supported by this vehicle (pre-1996)." This check comes first.
  - **Year unknown:** `WARN`, "Add your car's year so BobCar can check support." with **Add year**.
  - No match: `WARN`, "Engine diagnostics need a small adapter that plugs in under the dashboard (wishlist)." with **View hardware wishlist**.
  - Match: `WARN`, "Adapter found (<name>). Diagnostics arrive in a later version." with **Use this adapter**, which sets `obdAdapterAddress` and `obdTransport = "spp"`.
- **Phase 6:** see M14.8. The pre-1996 rule still comes first.

**`extended_pids`:**
- **V1:** `NOT_TESTED`, "Needs a diagnostics adapter." A pre-1996 vehicle gets `FAIL`, as for `obd_link`.
- **Phase 7:** see M15.6.

#### M3.4 Storage and rendering

- Results and derived flags are written in one Room transaction (§7.6).
- **Regression rule:** after a quick probe, if any key went from `PASS` to something else since the last stored result, speak and show one line, for example "Car mode will open from a notification now; 'Display over other apps' was turned off." Never re-announce unchanged state.
- **Render targets:** the cached report renders within 500 ms of the connect broadcast. The status chip shows "Systems ✓" or "1 item needs attention".

#### M3.5 Acceptance tests

1. **Taurus, V1, fresh profile:** `voice` = `PASS`, `auto_launch` matches reality, `audio_route` = `PASS` (after the A2DP wait), `wheel_button` = `FAIL` (pre-seeded note), `obd_link` = `WARN` "…plugs in under the dashboard (wishlist)", `extended_pids` = `NOT_TESTED`. No `car_mic` row (it arrives in 1.1).
7. **F-350 (no Bluetooth):** `auto_launch` = `FAIL`, `audio_route` = `FAIL` with the 12 V adapter note, and no foreground service runs.
8. **A 1995 test profile:** the "isn't supported" banner appears, the diagnostics rows are `FAIL` (pre-1996), and car mode still works.
2. **Next-day connect:** the cached report renders in under 500 ms. There are no progress UI and no prompts, and no OBD or SCO activity appears in the logs.
3. **Revoke the overlay permission, then connect:** exactly one regression line. On the following drive, nothing is announced.
4. **Re-check doesn't run interactive tests:** no countdown and no SCO.
5. **No launch delay:** the time from `ACL_CONNECTED` to `CarModeActivity.onResume` is unchanged (within ±100 ms) with and without the quick probe.
6. **Bond any device named "OBDII":** `obd_link` = `WARN` "Adapter found…", and the logs show no RFCOMM connection attempt.

#### M3.6 Non-goals

- Not a continuous monitor.
- Never writes vehicle configuration, never sends OBD mode 04 or 08 (clear or control commands), and never retries in the background.
- Results are per vehicle: one car's results say nothing about another.

### M4. Car UI

#### M4.1 Purpose

The driver-facing surface: a big-button, high-contrast, voice-first car screen. It must be legible at arm's length day and night, operable by thumb with a glance, and have **zero text entry**.

#### M4.2 Structure

- **`CarModeActivity`** hosts only the car screen and its bottom sheets (compatibility report, disambiguation choices).
  - `singleTask`, portrait, `showWhenLocked`, `turnScreenOn`.
  - `FLAG_KEEP_SCREEN_ON` while visible.
- **`MainActivity`** hosts everything else in one `NavHost`: first run, vehicles, `vehicle/{id}`, `compat/{id}`, settings, and AI.
  - Settings never shows over the lock screen.
  - Settings is the only place with text fields.
- **Hotword start (release 1.1).** `CarModeActivity.onStart()` starts `HotwordService` when it's enabled (M10.4). The activity tracks `CarSession.carScreenResumed` for M2.7 and M12.

#### M4.3 Car screen layout (Compose)

- **Status line (top):**
  - Minimum 32sp, `maxLines = 2`, and a polite live region for TalkBack.
  - It shows "Heard: …" while listening and the response while speaking. Otherwise it shows "<vehicle> · Connected", or "Not connected" for a manual session.
- **Main grid (center):** 2×2 buttons, each at least 96 dp tall with labels of 24sp or more.
  - **Talk** (microphone icon): `startListening()`.
  - **Call**: starts listening with the prompt "Who should I call?". There's no recents list in V1.
  - **Navigate**: starts listening with "Where to?", plus a secondary **Go home** chip.
  - **Music**: a tap toggles play/pause. An adjacent **Next** button is at least 72 dp.
- **Bottom row:** a day/night toggle icon, the "ⓘ Systems" chip, a **More** button, and **Quit car mode** (24sp or more).
- **More sheet** (a bottom sheet of full-width buttons at least 72 dp tall, with no scrolling):
  - **Diagnostics.** V1: the "needs an adapter" reply. Phase 6: the diagnostics screen (M14.13).
  - **Systems:** the compatibility report.
  - **Send to an AI app** (release 1.1, M12.8).
  - **Close.**
- **Hotword pill (release 1.1).** When armed, a pill under the status line reads "Listening for 'Hey Bob'" with a pulsing dot. For the car mic it reads "Listening for 'Hey Bob' (car mic)". It's hidden entirely when the hotword is off.
- **Disambiguation.** Up to 3 full-width buttons ("Mom · Mobile (507) 555-0143") in a bottom sheet.
- **No text fields, no scrolling feeds, no web views, no video** anywhere in car mode.

#### M4.4 Theme

- **`day_night_pref`:**
  - `auto`: night from 20:00 to 06:00 local time (a simple clock rule for V1).
  - `day` / `night`: force the theme.
  - Switch instantly through a `CompositionLocal<CarTheme>`.
- **Night:** near-black background `#0A0A0A` with amber or white text.
- **Day:** a white background with black text.
- **Contrast:** text contrast at least 4.5:1, and button and icon contrast at least 3:1.
- **Font scale:** respect the system font scale up to 1.3×, then clamp, because the fixed grid would break beyond that. Document the clamp.
- **Insets:** use `Scaffold` and window insets (edge-to-edge is enforced for targetSdk 35).

#### M4.5 Edge cases

- **Split-screen:** if `isInMultiWindowMode`, show the banner "Exit split-screen for car mode" and keep the buttons usable.
- **Screen forced off by the user:** TTS continues, because `TtsEngine` is owned by the session, not the activity. The status text is still there on wake.
- **Another app in front (Maps, dialer):** car mode doesn't fight for the foreground. When the user comes back, the car screen shows idle.
- **TalkBack:** every button has a content description.

#### M4.6 Acceptance tests (Zenfone 8 in a dash mount, about 70 cm away)

1. **Daylight:** all labels and the status line are readable without leaning in. Take a photo for the record.
2. **Night:** the dark theme is active with no white flash on launch, and the manual toggle overrides auto.
3. **Thumb test:** press each of the 4 main buttons 10 times without looking. At least 38 of 40 presses hit the right button.
4. **No-text-entry audit:** an automated traversal of car mode finds no `TextField` or `EditText`.
5. **1-hour drive:** the screen never sleeps, and the UI never freezes or restarts.
6. **Settings rotation:** rotate on each settings screen. Nothing crashes, and state is preserved (`rememberSaveable`).

#### M4.7 Non-goals

- Not an Android Auto or Automotive OS UI. There's no `CarAppService`.
- Not a media browser. Music is transport controls only.
- Not the data layer (§7) or the test implementations (M10, M11).

#### M4.8 Plain-language rule (all user-facing text)

Never show technical terms in the app, notifications, or TTS:

| Don't say | Say instead |
|---|---|
| OBD-II, OBD | "diagnostics" / "a small adapter that plugs in under the dashboard" |
| ELM327 | "diagnostics adapter" |
| A2DP | "music over Bluetooth" / "car speakers" |
| HFP, SCO | "the car's call channel" / "car microphone" |
| VIN decode | "look up your vehicle" |

Technical terms are fine in logs, the debug screen, and this guide.

### M5. Command: calls

**Class:** `actions/CallAction.kt` (uses `ContactResolver`) · **V1, core**

#### M5.1 Purpose

The driver says "call Mom". BobCar resolves the name to a number, opens the phone's dialer with the number pre-filled, and confirms by voice. **The driver taps the dialer's Call button.** That's one deliberate tap, and it needs no permission.

#### M5.2 Contact resolution (`ContactResolver`, shared with M6; needs `READ_CONTACTS`)

```kotlin
val projection = arrayOf(Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL)
val selection = "${Phone.DISPLAY_NAME} LIKE ?"
val args = arrayOf("%" + query.replace("%", "").replace("_", "").trim() + "%")   // strip LIKE wildcards from user input
contentResolver.query(Phone.CONTENT_URI, projection, selection, args, "${Phone.DISPLAY_NAME} ASC")
```

- **Query only `ContactsContract.CommonDataKinds.Phone` rows.** Contacts without a number are excluded automatically.
- **De-duplicate** candidates by `PhoneNumberUtils.normalizeNumber(number)`, because synced accounts create duplicates.
- **Type labels** come from `Phone.getTypeLabel(resources, type, label)` ("Mobile", "Home", "Work").
- **Matches:**
  - 0: "I couldn't find '<query>' in your contacts." Launch nothing.
  - 1: proceed.
  - 2 or more, including one contact with several numbers: TTS "I found several. Which one?" Show up to 3 buttons, `Name · Type (formatted number)`.
  - Never pick a number silently. Calling the wrong number from a car is a real hazard.
- **Spoken numbers.** If the query is a phone number (7–15 digits once spaces, dashes, dots, and parentheses are removed; M2.5 rule 2), skip the contact lookup and dial it.
  - Say "Calling" plus the digits read in groups, for example "507 555 0143. Tap call."
  - This needs no `READ_CONTACTS`.

#### M5.3 Launch the dialer

```kotlin
val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null))   // no setPackage: honor the default dialer
when (ExternalLauncher.launch(intent, label = "Call $name")) {
    LAUNCHED -> SpokenResult("Calling $name. Tap call.", display = "Calling $name…\n${PhoneNumberUtils.formatNumber(number, Locale.getDefault().country)}")
    NEEDS_TAP -> SpokenResult("Tap the notification to call $name.")
    NO_APP -> SpokenResult("No phone app found.")
}
```

**Why `ACTION_DIAL` and not `ACTION_CALL`:**
- `ACTION_CALL` needs `CALL_PHONE`, a sensitive permission that brings a Play declaration, a demo video, and a harder review.
- `ACTION_DIAL` needs no permission, and the extra tap makes a wrong-number mistake recoverable.
- Put this rationale in the Play listing's safety notes.

#### M5.4 Edge cases

- **`READ_CONTACTS` denied:** say "Contacts permission is off, so I can't look up names." Show a button that opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for this package.
- **Call audio:** once the call is placed, Android routes call audio through the car's hands-free profile. **BobCar does nothing here.**
  - Never call `setMode()` or open SCO for telephony.
  - Release 1.1: if BobCar's car-mic hotword is holding SCO, it yields first (M10.8).
- **Car mode backgrounded by the dialer:** fine. The call belongs to the phone app.
- **Emergency numbers:** allowed with no special handling.

#### M5.5 Acceptance tests

1. One "Mom" in contacts: "call Mom" opens the dialer with Mom's number, TTS says "Calling Mom. Tap call.", and the status line shows the formatted number.
2. Two matches, or one contact with Mobile and Home: TTS asks "Which one?" and three or fewer buttons appear. A tap opens the dialer.
3. "call Zxqwerty": nothing opens, and TTS says "I couldn't find 'Zxqwerty' in your contacts."
4. `READ_CONTACTS` denied: no crash, the explanation is spoken, and the settings button works.
5. Manifest check: no `CALL_PHONE`.
6. A real call over the car (parked): audio plays through the Taurus speakers and the phone mic works. Hanging up returns to car mode without a crash.
7. Airplane mode: on-device contacts still resolve.
8. "call 507 555 0143" and "dial 5075550143": the dialer opens with that number and no contact lookup happens.
9. "phone Mom" works like "call Mom". "Call Mom", then "cancel" at the disambiguation step: the choices close and TTS says "Okay."

#### M5.6 Non-goals

- No `ACTION_CALL`.
- No call-state UI and no automatic return after hang-up.
- No spoken disambiguation ("number two") in V1.
- No recents or favorites list in V1.

### M6. Command: texting

**Class:** `actions/TextAction.kt` · **V1, core**

#### M6.1 Purpose

"text Mom I'm running late" resolves Mom's number and opens the default messaging app with the message pre-filled. **The driver taps Send.** That's a deliberate handoff, not a hands-free send.

#### M6.2 Parsing

- Rule 3 of M2.5 applies. After the prefix (`text `, `message `, `send a text to `, `send a message to `), token 1 is the contact query and the rest is the body, taken from the original-case transcript.
  - A leading `saying ` or `that ` is dropped from the body: "send a text to Mom saying I'm late" gives the body "I'm late".
  - A phone number works as the contact.
- **Multi-word names mis-parse.** "text Mary Ann I'll be late" gives the query "Mary" and the body "Ann I'll be late".
  - The `LIKE %Mary%` lookup still finds "Mary Ann" if she's the only Mary.
  - Document this in the help text: "Single-word contact names work best."
  - A greedy match against contacts is a v2 improvement.

#### M6.3 Compose the handoff

Contact resolution and disambiguation are identical to M5.2. Then:

```kotlin
val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number.trim(), null)).apply {
    putExtra("sms_body", body)            // the long-standing key every major SMS app reads
}                                          // no setPackage: honor the default messaging app
ExternalLauncher.launch(intent, label = "Text $name")
```

- **TTS** says "Ready to send. Tap send." It speaks the name only, not the body.
- **Status line:** `To: <name> (<number>)\n"<body>"\nTap Send in your messaging app.`

#### M6.4 Why not send automatically (read this before "improving" it)

Fully hands-free sending would require **both**:

- `SEND_SMS`, a Play-restricted permission that's allowed only for apps that are the default SMS handler, or a narrow set of exceptions. A car companion isn't an allowed use case.
- Default-SMS-app status, which means a complete messaging client (receiver, MMS, provider writes, UI).

The handoff needs zero SMS permissions and passes review cleanly.

#### M6.5 Edge cases

- **Empty body ("text Mom"):**
  1. Say "What should the text say?"
  2. Store `pendingText = (name, number)`.
  3. The next utterance becomes the body. This expires after 60 s or on car-mode exit.
  4. Never open an empty compose screen.
- **Long messages:** hand them off unchanged; the messaging app splits multi-part SMS. Never truncate.
- **Landline or short code:** hand it off anyway. The carrier or app surfaces the failure. The help text says texts go to mobile numbers.
- **Dual SIM:** the messaging app picks the SIM.
- **`READ_CONTACTS` denied:** same as M5.4.

#### M6.6 Acceptance tests

1. "text Mom I'm running late": the messaging app opens with the recipient and body pre-filled (body case preserved), TTS says "Ready to send. Tap send.", and the status line shows the quoted body.
2. "text Mom", then "I'm running late": the message is pre-filled. Saying something unrelated 61 s later sends nothing.
3. Two Johns: the disambiguation buttons appear first.
4. `READ_CONTACTS` denied: no crash, with a deep link to settings.
5. Parked, over the car: Google Messages pre-fills, Send delivers, and the manifest and pre-launch report show no SMS permission.
6. A 400-character body is handed off intact.
7. A body with quotes, emoji, or newlines pre-fills exactly.
8. "send a text to Mom saying I'm late" and "message Mom I'm late" both pre-fill the body "I'm late".
9. "text Mom", then "cancel": nothing opens and the pending text is cleared.

#### M6.7 Non-goals

- No automatic sending, no `READ_SMS`, and no "read my texts".
- No delivery tracking, no MMS or group threads, and no multi-word name parsing in V1.

### M7. Command: music control

**Class:** `actions/MediaAction.kt` · **V1, core**

#### M7.1 Purpose

Transport control for whatever is playing: play, pause, next, previous. It needs no permission, no player SDK, and no per-app integration.

#### M7.2 API

```kotlin
fun sendMediaKey(keyCode: Int) {
    val now = SystemClock.uptimeMillis()
    audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
    audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
}
// MediaToggle → KEYCODE_MEDIA_PLAY_PAUSE · MediaNext → KEYCODE_MEDIA_NEXT · MediaPrevious → KEYCODE_MEDIA_PREVIOUS
```

- **`AudioManager.dispatchMediaKeyEvent`** (API 19+) injects into the system media-key path, the same path a headset button uses.
  - Any app can call it with no permission.
  - Don't confuse it with `MediaSessionManager.dispatchMediaKeyEvent`, which is system-only.
- **Always send `ACTION_DOWN` and then `ACTION_UP`** with the same key code.
- **The target is the active or most recent media session.** BobCar never names a package.
- **TTS:** "Okay." for the toggle, because V1 can't know the playback state, so it never claims a direction. "Next." and "Previous." for skips.

#### M7.3 Edge cases

- **Nothing playing:** the system usually resumes the last player. If nothing handles the key, it's dropped silently. That's acceptable; don't fake success detection.
- **Players that ignore media keys:** nothing to do at this layer.
- **During a phone call:** telephony consumes media keys. That's expected behavior.
- **Car-mic hotword armed (release 1.1):** Android suspends A2DP while SCO is open, so "play" may resume playback that the car speakers can't output until SCO closes. The M10 warning covers this.
- **Long-press:** not supported. Send discrete presses only.

#### M7.4 Acceptance tests

1. With Spotify (or any player) on the Taurus over A2DP: "pause" pauses within about 1 s and "play" resumes. Repeat 5 times with no missed events.
2. "next" and "previous": the track changes (the player decides whether "previous" restarts the track).
3. No player open, then "play": no crash. Record what happens on the Zenfone 8.
4. Two players, one playing: the active one responds.
5. "next" three times quickly: three skips with no stuck key.
6. Manifest check: no media or notification-listener permissions.

#### M7.5 Non-goals

- No now-playing metadata and no playback-state detection. A v2 could use `AudioManager.isMusicActive()` for smarter confirmations.
- No "play <song>", no volume control, and no `MediaSession` of BobCar's own.

### M8. Command: navigation

**Class:** `actions/NavAction.kt`, `actions/HomeAction.kt` · **V1, core**

#### M8.1 Purpose

Speak a destination and Google Maps opens directly in turn-by-turn mode. "Go home" uses the Home saved in Google Maps. BobCar never builds map or routing UI; Maps owns the drive.

#### M8.2 Intents

```kotlin
val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
    .setPackage("com.google.android.apps.maps")               // no chooser mid-drive
when (ExternalLauncher.launch(nav, label = "Navigate to $destination")) {
    LAUNCHED -> SpokenResult("Navigating to $destination.")
    NEEDS_TAP -> SpokenResult("Tap the notification to start navigation.")
    NO_APP -> {   // Maps missing or disabled: generic map search (not turn-by-turn), user picks the app
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
        if (ExternalLauncher.launch(geo, "Open $destination") == NO_APP) SpokenResult("No map app found on this phone.")
        else SpokenResult("Opening $destination in your map app.")
    }
}
```

- `Uri.encode` is mandatory. Never concatenate raw user text into a URI.
- **"Go home":** fire the same Google Maps intent with the destination `Home` (`google.navigation:q=Home`). Maps resolves it to the Home saved in the user's Google account, so **BobCar stores no address.**
  - TTS says "Navigating home."
  - **Verify in the car** that Maps navigates to the saved Home rather than searching for a business. If it doesn't, stop and get a decision on a fallback; don't add an address field silently.
  - If the user has no Home saved in Maps, Maps handles it with its own prompt or search. The help text and About screen say "Go home uses the Home saved in Google Maps."
  - Without Google Maps installed: the `geo:` fallback can't know Home, so say "Go home needs Google Maps." and fire nothing.
- **Empty destination ("navigate to"):** say "Where to?" and hold a 60 s pending state (M2.6).

#### M8.3 Location

**`NavAction` never reads location.** The navigation intent carries only the destination, and Maps supplies the origin. The settings screen says: "Location is used for weather only. Navigation always works."

#### M8.4 Edge cases

- **Ambiguous query:** Maps picks its best match. The driver sees Maps' UI and can correct it there. That's accepted V1 behavior.
- **Offline:** Maps handles offline maps itself. Voice input needs a network anyway (M2).
- **Maps in the foreground:** car mode goes to the background. Don't fight Maps for the foreground.

#### M8.5 Acceptance tests

1. "navigate to 1600 Amphitheatre Parkway Mountain View CA": Maps opens in navigation mode, and TTS says "Navigating to …".
2. "go home" with a Home saved in Google Maps: Maps navigates to it, not to a search result. With Maps disabled: "Go home needs Google Maps." and nothing fires.
3. Maps disabled on a test device: the `geo:` chooser appears and nothing crashes.
4. "navigate to", then "Casey's in Jackson": navigation starts.
5. Location denied: navigation still works identically.
6. "navigate to O'Brien's Pub & Grill": the full query reaches Maps.
7. Release 1.1: the hotword fires while Maps is in front and there's no overlay permission. The tap notification appears (M2.7).

#### M8.6 Non-goals

- No in-app maps, routes, ETA, Directions API, or destination picker.
- No "navigate to <contact's address>" in V1.

### M9. Command: weather and time

**Classes:** `actions/WeatherAction.kt`, `actions/TimeAction.kt`
- **Time (M9.3): V1, core.**
- **Weather (M9.1–M9.2, M9.4): release 1.1 (Phase 5a).** In V1, `WeatherAction` is a stub that says "Weather is coming in a later update." with no network access and no location permission.

#### M9.1 Weather: Open-Meteo (free, no key, no signup; release 1.1)

```kotlin
val url = "https://api.open-meteo.com/v1/forecast" +
    "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&temperature_unit=fahrenheit"
```

- **Threading:** run on `Dispatchers.IO`. Parse `current.temperature_2m` (Double) and `current.weather_code` (Int) defensively.
- **Minimize (§8.4):** round latitude and longitude to 1 decimal (about 11 km) **before** sending; weather doesn't need more.
- **Cache:** store `weather_cache` = `{ts, lat1, lon1, tempF, code, place}`, using the same rounded values that were sent.
  - A request within 30 min for the same rounded location is served from the cache.
- **Attribution:** "Weather data by Open-Meteo.com" on the About screen. The free API is for non-commercial use, which this app is (Phase 5d).

**WMO `weather_code` to spoken phrase:**

| Code | Phrase | Code | Phrase |
|---|---|---|---|
| 0 | clear | 66, 67 | freezing rain |
| 1 | mostly clear | 71 | light snow |
| 2 | partly cloudy | 73 | snowy |
| 3 | overcast | 75 | heavy snow |
| 45, 48 | foggy | 77 | snow grains |
| 51, 53, 55 | drizzly | 80, 81, 82 | rain showers |
| 56, 57 | freezing drizzle | 85, 86 | snow showers |
| 61 | light rain | 95 | thunderstorms |
| 63 | rainy | 96, 99 | thunderstorms with hail |
| 65 | heavy rain | other or missing | (omit the phrase) |

- **Spoken:** `"It's ${temp} and ${phrase} in ${place}."`, for example "It's 58 and clear in Lakefield." With no phrase: "It's 58 in Lakefield; conditions unavailable."
- **Display:** `58°F\nClear · Lakefield\nUpdated 12:04 PM`.

#### M9.2 Location resolution (release 1.1)

**Every prompt states the purpose and every use (§8.3).** Today location and ZIP are used **only for weather**: they go to Open-Meteo and nowhere else.

**First weather request (or the "What's new" card): ask once.**

1. If `weather_location_asked` is false and approximate location isn't granted:
   - Say "To tell you the weather, I need your approximate location. It's only used for weather and only sent to the weather service. Tap Allow on the screen." Show the same text on screen.
   - Show the system permission dialog, and set `weather_location_asked = true`. It's one tap, like the dialer and messaging handoffs.
   - If the car screen isn't visible (for example, the hotword fired while Maps is in front), post a notification for this step instead of the dialog.
2. **Allowed:** fetch using location (below).
3. **Denied**, or Android won't show the dialog again:
   - Say "No problem. What's your ZIP code? It's only used for weather." and listen, using the 60 s pending follow-up from M2.6.
   - Strip spaces from the transcript and accept exactly 5 digits.
   - Geocode once with `https://geocoding-api.open-meteo.com/v1/search?name={zip}&count=1&countryCode=US`. Store `weather_zip_fallback` and `weather_zip_geo` (`{lat, lon, place}`).
   - Confirm and answer in one line: "Got it, Lakefield. It's 58 and clear."
   - If the answer isn't 5 digits, or the ZIP isn't found, ask once more. Then say "You can add your ZIP in settings." and stop.
4. **Never ask again automatically.** Later changes happen in Settings → Weather location.

**Every request after that:**

1. **Approximate location granted:** use `FusedLocationProviderClient.lastLocation`.
   - If that's null, call `getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken)` with a 10 s timeout.
   - The spoken place is "your area".
2. **No location, ZIP stored:** use `weather_zip_geo`. The spoken place is the geocoded name ("Lakefield").
3. **Neither:** say "I don't know where you are. Add a ZIP code in settings for weather." Never guess.

#### M9.3 Time (V1)

```kotlin
val spoken = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))   // device zone
SpokenResult("It's $spoken.")
```

`java.time` is native on API 26+. Use the device's time zone, so a driver who crosses zones hears local time. Time works offline.

#### M9.4 Edge cases (weather: release 1.1)

- **No network:** serve the cache and append "Data may be stale; no connection.", with the timestamp prominent. With no cache: "Can't reach the weather service right now."
- **HTTP error or malformed JSON:** same as no network. Log the code and never speak it.
- **Location revoked mid-drive:** silently fall back to the stored ZIP, or say the "Neither" line. Never re-ask automatically.
- **Units:** Fahrenheit in V1. A Celsius setting is a later one-line change.

#### M9.5 Acceptance tests

Test 7 (time) and test 10 (V1 weather stub) run in V1. Tests 1–6, 8, and 9 run in release 1.1.

1. Location granted: "what's the weather" speaks "It's NN and <phrase> in your area." within about 3 s.
2. Again 5 min later: no HTTP call (check logcat).
3. Airplane mode with a fresh cache: the reply includes "Data may be stale".
4. Airplane mode with no cache: a clean failure line.
5. Fresh install: ask for weather, deny location, and say "56136". The reply is "Got it, Lakefield. It's NN and <phrase>." The next request answers with no prompts.
6. Deny location, then give an invalid ZIP twice. The reply is "You can add your ZIP in settings.", and later requests don't prompt again.
7. "what time is it" matches the phone clock and works in airplane mode.
8. A mocked HTTP 500: the stale-or-failure path runs.
9. Unit test: every code from 0 to 99 maps to a phrase or the omit path, without crashing.
10. V1: "what's the weather" says "Weather is coming in a later update." The app makes no network request (logcat) and requests no location permission.

#### M9.6 Non-goals

- No forecasts, alerts, radar, icons, Celsius toggle, or automatic announcements in V1.

### M10. Hotword activation

**Package:** `hotword/` · **Release 1.1 (Phase 5b)**, off by default. Not in V1.

#### M10.1 Purpose

A hands-free trigger for cars where the wheel button can't be used, starting with the Taurus. The user says a wake phrase (default "Hey Bob") and the voice loop starts, exactly like tap-to-talk.

Rules:
- On cars with `wheelButtonSupported = true`, the hotword defaults off to avoid double activation.
- The hotword is **only ever armed while car mode is active.** It never listens outside a car session.

#### M10.2 Engine choice

| Engine | License / cost | Verdict |
|---|---|---|
| **openWakeWord** | Code Apache-2.0. The **pretrained wake-word models are CC BY-NC-SA 4.0 (non-commercial)** | **Use it**, with custom-trained models. Free at Play scale, with no keys and no user caps |
| Porcupine (Picovoice) | Needs an AccessKey. The free tier caps commercial use, and custom Android models need an enterprise license (verify at build time) | No. It's the documented upgrade path if the owner ever accepts a paid license, and `HotwordEngine` keeps that a one-class swap |
| Vosk keyword spotting | Apache-2.0 | Fallback only. It's a full speech-to-text engine, which is heavier than needed |
| PocketSphinx, Mycroft Precise | Open source | No. Weaker accuracy and stale or dead projects |

#### M10.3 openWakeWord pipeline (on Android)

openWakeWord isn't a single model. A custom wake-word model consumes **features produced by two shared openWakeWord models**, so all three must be bundled:

```
16 kHz mono PCM16, 80 ms chunks (1280 samples)
  → melspectrogram.onnx        (openWakeWord feature model #1; output scaled exactly as upstream: x/10 + 2)
  → embedding_model.onnx       (feature model #2; sliding window over 76 mel frames, stride 8 → one 96-dim embedding per 80 ms)
  → hey_bob.onnx               (custom wake-word classifier; input = last 16 embeddings; output = score 0..1)
  → score ≥ hotwordThreshold → detection; 2 s refractory period
```

- **Port the feature pipeline from upstream `openwakeword/utils.py` (`AudioFeatures`) exactly.**
  - Window sizes, strides, and the mel scaling must match the Python reference.
  - Add a unit test that feeds a checked-in WAV through the Kotlin pipeline and compares embeddings and scores against values exported from the Python reference, within a small tolerance.
  - A hand-rolled "pre-emphasis + framing" front end won't produce compatible input.
- **Assets:** `assets/hotword/melspectrogram.onnx`, `assets/hotword/embedding_model.onnx`, `assets/hotword/hey_bob.onnx`, and optionally `hey_car.onnx` and `ok_bob.onnx`.
- **Licensing (verify at build time).** The two feature models' license terms must be checked before shipping, just as the pretrained wake-word models were. Ship only custom-trained wake-word classifiers, trained with openWakeWord's training notebook. Export with standard ONNX operators so ONNX Runtime Mobile can load them.
- **Runtime:** `com.microsoft.onnxruntime:onnxruntime-android`. Inference runs on a dedicated low-priority thread. Only one wake-word model is loaded at a time.
- **Custom phrases:** the bundled models are "Hey Bob", "Hey Car", and "OK Bob". Settings offers "Import your own model (.onnx)" with written instructions for the training notebook. There's no one-tap training.
- **`HotwordEngine` interface:** `start(source)`, `stop()`, `setThreshold(t)`, `onDetection`.

#### M10.4 Service and start rules

- **`HotwordService`:** a foreground service with `android:foregroundServiceType="microphone"`, `FOREGROUND_SERVICE_MICROPHONE`, and `RECORD_AUDIO` granted. Its persistent notification reads "Listening for 'Hey Bob'".
- **Start only from a visible `CarModeActivity`** (in `onStart`), and only when `hotword_enabled_global && vehicle.hotwordEnabled`.
  - With targetSdk 34+, creating a microphone-type foreground service while the app is in the background throws `SecurityException`.
  - On Android 11+ a microphone service started from the background can't capture audio anyway.
  - Once started while visible, the service keeps capturing when Maps or the dialer comes to the front.
- **Stop:** when car mode exits (M1.5), on Android Auto projection (unless the car's answer is BobCar, M1.6), when the user turns it off, or on `RECORD_AUDIO` revocation.
- **Car mode opened only from a notification:** if the user hasn't tapped it yet, the hotword isn't armed. Document this in the enable dialog: "Hey Bob starts listening once the car screen is open."
- **Android 12+ shows the green microphone indicator.** That's expected. Mention it in the enable dialog.

#### M10.5 Audio source

| Source | When | How |
|---|---|---|
| `PHONE_MIC` (**default**) | Always available | `AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, bufferSize)` |
| `CAR_SCO` (**experimental, opt-in per vehicle:** `hotwordUseCarMic`) | Only while connected to that vehicle and SCO is up | `ScoLink.open()`, then `AudioRecord(VOICE_COMMUNICATION, 16000, …)` |

**Why the phone mic is the default:**
- The car mic only reaches the phone over the hands-free SCO link.
- While SCO is open, Android suspends A2DP media audio, so **music from the phone stops playing through the car speakers for as long as the car mic is armed.**
- Some head units also show a call screen or drop SCO on a timeout.
- SCO on older head units is narrowband (8 kHz CVSD), which lowers accuracy.

The opt-in dialog states all of this plainly.

**`ScoLink`:**

```kotlin
fun open(): Boolean {
    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION            // most devices need this for app SCO
    return if (Build.VERSION.SDK_INT >= 31) {
        val sco = audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        sco != null && audioManager.setCommunicationDevice(sco)        // confirm via addOnCommunicationDeviceChangedListener
    } else {
        @Suppress("DEPRECATION") audioManager.startBluetoothSco()       // then wait for ACTION_SCO_AUDIO_STATE_UPDATED == CONNECTED
        true                                                            // (and setBluetoothScoOn(true)) before starting AudioRecord
    }
}
fun close() {
    if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
    else { @Suppress("DEPRECATION") audioManager.stopBluetoothSco() }
    audioManager.mode = AudioManager.MODE_NORMAL
}
```

- **Wait until the link is actually up** before starting `AudioRecord`. Capturing before SCO is ready yields silence.
- **Timeout:** 10 s. If the link doesn't come up, fall back to `PHONE_MIC` for this drive.
- **3-strike rule:** if SCO drops during a drive, re-open with backoff (1 s, 2 s, 4 s). On the third drop:
  - Switch to `PHONE_MIC` for the rest of the drive.
  - Set `scoHoldsReliably = false` and `car_mic = WARN("Your car drops the mic link, so I'll use the phone mic.")`.
  - Show exactly one notice.

#### M10.6 Car-mic test (`car_mic` probe; on demand only)

1. **Precondition:** connected to the vehicle. Show "Brief audio check: music will pause for a few seconds."
2. **Open SCO** (M10.5) with a 10 s timeout. If it fails: `scoHoldsReliably = false`, `FAIL("This car didn't open its mic link to the phone; the phone mic will be used.")`.
3. **Level check.** Capture 3 s from `VOICE_COMMUNICATION` with the prompt "Say something". If the RMS is 200 or less on 16-bit samples, set `WARN("The car mic link opened but no sound came through.")`.
4. **Wake test** (if a model is loaded). TTS over SCO: "Say 'Hey Bob' three times." With at least 2 of 3 detections, set `scoHoldsReliably = true` and `PASS("Car mic works with 'Hey Bob'.")`. Otherwise set `WARN("Car mic is unreliable for 'Hey Bob' on this car; the phone mic will be used.")`.
5. **Close SCO** (`ScoLink.close()`) and release `AudioRecord`. Persist the result. The test never runs automatically.

#### M10.7 Battery and Doze

- **Allowlist check.** After CompanionDeviceManager association, an app that declares `REQUEST_COMPANION_RUN_IN_BACKGROUND` is placed on the system power-save allowlist on AOSP builds. Verify with `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
- **If it returns false** (no association, or OEM behavior), explain why the hotword may pause in Doze. Offer **Open battery settings** with `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, which needs no permission.
  - **Don't** use `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. It requires the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission, which Play restricts, and BobCar doesn't declare it.
- **ASUS behavior.** ASUS has historically been aggressive about background apps, so the acceptance tests include a soak test.
- **Measure** the battery %/hour delta compared to hotword-off, and show it on the debug screen.

#### M10.8 Phone calls (yield and resume)

The hotword reuses V1's `CallStateWatcher` (M2.4), which needs no `READ_PHONE_STATE`. It adds one check of its own:
- `AudioManager.registerAudioRecordingCallback` (API 29+): treat `AudioRecordingConfiguration.isClientSilenced()` for BobCar's own recording as "yield". That also covers VoIP calls the mode listener can miss.
- **On call start:** pause inference, close SCO immediately (the call owns the channel), and suppress TTS (show text only).
- **On call end** (mode back to `MODE_NORMAL` and not silenced): resume within 5 s, if the hotword is still enabled and the car is still connected.

#### M10.9 UX

- **Enable toggle (default off)** with a warning dialog **before** activation. It covers:
  - Continuous mic use.
  - Battery cost.
  - The green mic indicator.
  - "Listens only while car mode is open."
- **Car-mic opt-in** has its own dialog: "Music through the car speakers pauses while the car mic is listening. Some cars show a call screen."
- **Sensitivity:**

  | Setting | Threshold | Effect |
  |---|---|---|
  | Low | 0.7 | Fewer false triggers, more misses |
  | Medium | 0.5 (default) | |
  | High | 0.3 | Catches more, triggers more falsely |

  Stored per vehicle as `hotwordThreshold`.
- **On detection:** a short chime, then the same listening UI as tap-to-talk.

#### M10.10 Acceptance tests (Taurus, Zenfone 8)

1. Phone mic, parked, engine off: 50 utterances produce at least 45 detections.
2. Phone mic, 65 mph: record the rate; the target is at least 80%. If it's lower, retrain with in-car noise.
3. A 4-hour drive with the radio on gives at most 2 false activations. Every detection is logged with a timestamp to a local debug file.
4. Detection starts the voice loop within 2 s.
5. Calls work normally while armed, and the hotword resumes within 5 s.
6. After disconnect (debounce elapsed), no `HotwordService` is running and the mic indicator is off.
7. A 2-hour parked soak in car mode with the screen off: the hotword still detects afterwards.
8. Car-mic experiment: the car-mic test result is recorded, and music behavior is documented. The 3-strike rule gives exactly one notice.
9. Battery: if the delta exceeds 8%/hour, ship with the stronger warning and a "pause when screen is off" option.

#### M10.11 Non-goals

- No cloud inference and no pretrained openWakeWord wake-word models in the Play build.
- No Porcupine in V1.
- No listening without a foreground service and notification, and no listening outside car mode.
- No one-tap custom wake-phrase training.

### M11. Steering-wheel voice button

**Classes:** `ui/VoiceCommandActivity.kt`, `capability/probes/WheelButtonProbe.kt` · **V1, for cars that forward the button**

#### M11.1 Purpose

Trigger the voice loop from the car's steering-wheel voice button **where the car sends it to the phone**. Detect support per vehicle, enable the feature only where it works, and fall back gracefully everywhere else.

**Verdict for the owner's car:** the 2009 Taurus (SYNC Gen 1) **can't** do this (§3). Its profile is pre-seeded `FAIL` (§7.4).

#### M11.2 How the button reaches the phone (verified against AOSP)

- On head units that support it (for example SYNC Gen 2 v3.8+, many newer cars, and aftermarket units in plain Bluetooth mode), the voice button makes the car send the hands-free command `AT+BVRA=1`.
- Android's Bluetooth stack handles that in `HeadsetSystemInterface.activateVoiceRecognition()` by calling **`startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(FLAG_ACTIVITY_NEW_TASK))`**.
  - It's an **activity** intent, not a broadcast, so a `BroadcastReceiver` never sees it.
  - It's resolved like any implicit activity intent: to the user's chosen handler, or a chooser if several apps handle it (the Google app usually does).
- Because the system's Bluetooth service starts the activity, background-launch limits don't apply.

**So BobCar doesn't need to be the default assistant.** That's fortunate, because Android's `ROLE_ASSISTANT` is declared `requestable="false"` in AOSP, and apps can't request it through `RoleManager`.

Instead, BobCar declares `VoiceCommandActivity` with an intent filter for `android.intent.action.VOICE_COMMAND` (§8.2). The user picks BobCar ("Always") in the chooser the first time.

#### M11.3 Android Auto head units

During an Android Auto session, the wheel button goes to Google's assistant inside Android Auto, not to `ACTION_VOICE_COMMAND` for BobCar. BobCar can still run on the phone's screen if the driver picks it for that car (M1.6), but the wheel button on Android Auto units is **not** a supported path. Aftermarket units used as plain Bluetooth hands-free devices behave like M11.2.

#### M11.4 `VoiceCommandActivity`

- **Transparent, no history, excluded from recents.** It's **disabled in the manifest by default**.
  - It's enabled with `PackageManager.setComponentEnabledSetting(…, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)` while **any** vehicle has `wheelButtonEnabled = true` (the per-vehicle switch in vehicle detail), and temporarily during the test (M11.5). Otherwise it's disabled.
- **In `onCreate`:**
  - If car mode is active and the active vehicle has `wheelButtonSupported && wheelButtonEnabled`: call `CarSession.voice.startListening()`, bring `CarModeActivity` forward, and `finish()`.
  - If car mode is active for a vehicle without both flags, treat the press like the outside-car-mode case below.
  - If car mode isn't active, the press came from earbuds or another device outside the car. Forward it to `wheel_fallback_assistant_pkg` with an explicit `Intent(ACTION_VOICE_COMMAND).setPackage(pkg)`, which prevents loops, then finish.
    - That setting defaults to the Google app (`com.google.android.googlequicksearchbox`) if it's installed.
    - If no fallback is installed, show a one-line toast "BobCar handles the voice button in car mode only."
- **Check the current handler at runtime:**
  ```kotlin
  val ri = packageManager.resolveActivity(Intent(Intent.ACTION_VOICE_COMMAND), PackageManager.MATCH_DEFAULT_ONLY)
  // ri.activityInfo.packageName == our package → we're the chosen handler
  // "android" (ResolverActivity) → chooser will appear; any other package → that app is set as default
  ```
  This needs the `VOICE_COMMAND` `<queries>` entry (§8.2).

#### M11.5 Empirical test (per vehicle, on demand)

There's no way to ask a car "do you send AT+BVRA?", so detection is empirical.

1. **Precondition:** connected to the vehicle, with the car screen in front.
2. **Enable `VoiceCommandActivity`** for the duration of the test.
3. **Check the current handler** (M11.4). If another app is set as default, show a `WARN` first:
   - "Your phone sends the voice button to <app>. Clear its defaults (Settings → Apps → <app> → Open by default), then run the test again."
   - Include a button that opens that app's details page.
4. **Run the test.** Show "Press your steering-wheel voice button now." with a 10 s countdown.
   - **Our activity receives the intent** (directly, or after the user picks BobCar in the chooser): `wheelButtonSupported = true`, `wheel_button = PASS("Your car sends the voice button to BobCar.")`. TTS says "Steering-wheel button linked." Set `wheelButtonEnabled = true` for this vehicle.
   - **Timeout:** `wheelButtonSupported = false` and `wheel_button = FAIL("No signal from the wheel button. That's normal on older cars; auto-launch and tap-to-talk still work.")`.
5. **If the test failed, disable the activity again,** unless another vehicle has `wheelButtonEnabled`.
6. **Never auto-retry.** The row offers **Re-verify**.

#### M11.6 Edge cases

- **The user later sets another app as the `VOICE_COMMAND` default.** Presses stop reaching BobCar. On the next car-mode entry for a vehicle with `wheelButtonSupported`, run the handler check. If BobCar isn't the handler, show one notice per vehicle with the fix, and never nag again.
- **Earbuds or headset voice buttons** use the same intent. The outside-car-mode forwarding in M11.4 keeps their behavior unchanged.
- **Multiple vehicles:** the flag is per vehicle. The Taurus stays `false` while a newer car can be `true`.

#### M11.7 Acceptance tests

**Case A: the button works** (a newer SYNC 3 Ford, or an aftermarket unit in Bluetooth mode):
1. Run the test and press the button. If the chooser appears, pick BobCar → Always. The voice loop starts within 2 s and the row shows `PASS`.
2. With car mode in the background, press the button. The car screen comes forward and listening starts.
3. Outside car mode, press a headset's voice button. The fallback assistant opens and BobCar doesn't.
4. Set the Google app as the default handler, then enter car mode. Exactly one notice appears with the fix.

**Case B: the button doesn't work** (2009 Taurus):
1. The row is pre-seeded `FAIL` with the verified note. **Re-verify** times out after 10 s and the row stays `FAIL`.
2. The drive flow is unaffected and nothing prompts again.

#### M11.8 Non-goals

- No interception or remapping on the Taurus; that's impossible in software.
- No HFP implementation and no AT commands sent to the car.
- No `VoiceInteractionService` and no `ROLE_ASSISTANT`.
- No vehicle SDKs (no AppLink).

### M12. AI provider choice

**Package:** `ai/` · **Release 1.1 (Phase 5c)**, off by default. Not in V1.

#### M12.1 Purpose

This is an optional conversational upgrade on top of the keyword parser. The user picks which AI answers open questions, and authenticates with **their own** key or, later, their provider account. BobCar ships with **no** provider keys, no backend, and no paid services. The parser stays the default brain and always handles known commands first.

**Why it exists:** the owner doesn't want users forced to download another assistant. Many people already pay for, or have free access to, Claude, Gemini, or ChatGPT.

**Hard truth up front:** there is **no** way to send a prompt into the Claude, Gemini, or ChatGPT *phone apps* and get the answer back. None of them exposes a public intent, deep link, or API for that (researched 2026-10-01). The only app-to-app path is a one-way `ACTION_SEND` share (M12.8). Real integration means calling the provider's **API** with the user's own credentials.

#### M12.2 Inputs and outputs

| Input | Source |
|---|---|
| `Unknown` transcript | `VoiceController`, only when `ai_mode_enabled && ai_provider_id != "none"` (M12.6) |
| API key | "Choose your AI" screen → `KeyVault` (§7.8) |
| OAuth token (future) | Custom Tabs OAuth → `KeyVault` |
| `SYSTEM_PROMPT` | Bundled asset |

| Output | Destination |
|---|---|
| Answer text (about 30 words or fewer) | `TtsEngine` and the status line |
| Fail-soft line plus parser help | Same |
| Usage count | `ai_usage_<providerId>_<yyyyMM>` (§7.7) |

#### M12.3 `AiProvider` interface

```kotlin
interface AiProvider {
    val id: String              // "gemini" | "openai" | "anthropic" | "openrouter" | "groq" | "nano"
    val displayName: String     // "Google Gemini", "ChatGPT", "Claude", …
    val authKind: AuthKind      // KEY | OAUTH | ON_DEVICE
    suspend fun validateKey(key: String): Result<Unit>             // cheap authenticated probe; 401/403 → AiError.InvalidKey
    suspend fun listModels(): Result<List<String>>                 // live list — never hardcode model IDs
    suspend fun generate(prompt: String, systemPrompt: String, model: String): Result<String>
}
sealed class AiError : Exception() {
    object Offline : AiError(); object Timeout : AiError(); object RateLimited : AiError()
    object InvalidKey : AiError(); object Refused : AiError(); object Unavailable : AiError()
}
```

- `NoAiProvider` is the `"none"` default.
- Every implementation uses raw HTTPS through the shared OkHttp client:
  - connect timeout 3 s
  - call timeout 6 s
  - `max_tokens` (or the provider's equivalent) about 120
- Provider code never logs request bodies or keys.

#### M12.4 Providers (as researched 2026-10-01; verify every row at build time)

| Provider | Endpoint (generate) | Auth headers | Response text | Free tier (verify at build time) |
|---|---|---|---|---|
| **Gemini** (recommended default) | `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, body `{systemInstruction:{parts:[{text}]}, contents:[{role:"user", parts:[{text}]}]}` | `x-goog-api-key: KEY` | `candidates[0].content.parts[0].text` | Genuine free tier, no card. The free tier may use content to improve products. A key comes from aistudio.google.com |
| **OpenAI** | `POST https://api.openai.com/v1/responses`, body `{model, instructions, input}` | `Authorization: Bearer KEY` | The `output_text` content items in `output[]` | Paid only. $5 minimum prepaid credit |
| **Anthropic** | `POST https://api.anthropic.com/v1/messages`, body `{model, max_tokens, system, messages:[{role:"user", content}]}` | `x-api-key: KEY`, `anthropic-version: 2023-06-01` | `content[0].text` | No permanent free tier. Small trial credit, then pay as you go |
| **OpenRouter** (fallback) | `POST https://openrouter.ai/api/v1/chat/completions` (OpenAI-compatible) | `Authorization: Bearer KEY` | `choices[0].message.content` | `:free` models with low rate limits |
| **Groq** (fallback) | `POST https://api.groq.com/openai/v1/chat/completions` (OpenAI-compatible) | `Authorization: Bearer KEY` | `choices[0].message.content` | Free plan with rate limits |

**Validation probes and model lists:**

| Provider | Validate and list with | Notes |
|---|---|---|
| Gemini | `GET /v1beta/models` | Keep models whose `supportedGenerationMethods` contains `generateContent` |
| OpenAI | `GET https://api.openai.com/v1/models` | Hide ids containing `embedding`, `tts`, `whisper`, `dall-e`, `moderation`, `image`, `audio`, `realtime` |
| Anthropic | `GET https://api.anthropic.com/v1/models` | Same headers as generate |
| Groq | `GET https://api.groq.com/openai/v1/models` | |
| OpenRouter | Its authenticated key-info endpoint | OpenRouter's model list is public, so it can't validate a key. Check the API reference for the current path (verify at build time) |

**Validation results:**
- `200`: "Key accepted." Status `valid`.
- `401`/`403`: "That key didn't work. Check for typos." Status `invalid`.
- Network failure: "Couldn't reach <provider>. Key saved and will be checked later." Status `unverified`.

**Unverified (don't state as fact in the UI).** One 2026 source said new AI Studio users may be required to prepay. The UI says "Check aistudio.google.com for current terms." Never assert this.

#### M12.5 Model picker and cost transparency

- **Model picker:**
  - A dropdown filled by `listModels()`, with a **Refresh** button. The choice is saved as `ai_model_<id>`.
  - If the saved model disappears from the list, fall back to the first listed model and show one line: "Your model was retired; switched to <name>."
  - Never ship a hardcoded model ID; model names change often.
- **Usage screen, per provider:**
  - A dated free-tier note: "Free tier (Oct 2026, verify in your provider dashboard): …".
  - "Requests this month: N (counted on this phone only). BobCar can't see your provider bill."
- **Paid-only providers** (OpenAI, Anthropic after trial credit): the first enable shows "This provider has no free tier. Usage bills to *your* account, not BobCar. Continue?" The user must explicitly accept.

#### M12.6 `AiBrain` (corrected)

```kotlin
class AiBrain(
    private val parser: CommandParser,
    private val executor: CommandExecutor,
    private val providers: Map<String, AiProvider>,   // cloud providers by id
    private val nano: AiProvider?,                     // null unless checkFeatureStatus() reports AVAILABLE
    private val settings: SettingsStore,
    private val consent: ConsentStore,
    private val usage: UsageCounter,
    private val keyVault: KeyVault,
) {
    suspend fun handle(transcript: String): SpokenResult {
        // 1. Known commands always use the parser. Deterministic actions never pass through an LLM.
        val command = parser.parse(transcript)
        if (command !is Command.Unknown) return executor.execute(command)

        // 2. AI mode off or no provider: unchanged V1 behavior (help prompt).
        val providerId = settings.aiProviderId.first()
        if (!settings.aiModeEnabled.first() || providerId == "none") return executor.execute(command)

        // 3. On-device tier first when available: free, private, offline. Foreground only.
        if (nano != null && CarSession.carScreenResumed) {
            nano.generate(transcript, SYSTEM_PROMPT, model = "").getOrNull()?.let { return SpokenResult(it) }
        }
        if (providerId == "nano") return failSoft(AiError.Unavailable, command)

        // 4. Cloud provider: consent-gated, one retry on 429, hard 6 s budget.
        val provider = providers[providerId] ?: return executor.execute(command)
        if (!consent.isCurrent(providerId)) return SpokenResult("AI mode needs your OK first. Check settings.")
        val model = settings.aiModel(providerId).first()
        val result = withTimeoutOrNull(6_000) {
            val first = provider.generate(transcript, SYSTEM_PROMPT, model)
            if (first.exceptionOrNull() is AiError.RateLimited) {
                delay(1_000); provider.generate(transcript, SYSTEM_PROMPT, model)
            } else first
        } ?: Result.failure(AiError.Timeout)
        usage.increment(providerId)
        return result.fold(onSuccess = { SpokenResult(it) }, onFailure = { e -> failSoft(e, command) })
    }

    // Says why, then the parser's help, so the driver always hears both. The drive never dead-ends.
    private suspend fun failSoft(e: Throwable, unknown: Command.Unknown): SpokenResult {
        if (e is AiError.InvalidKey) keyVault.markInvalid(settings.aiProviderId.first())
        return SpokenResult("${failSoftLine(e)} ${executor.execute(unknown).text}")
    }
}
```

**`failSoftLine` wording:**

| Error | Line |
|---|---|
| `Offline` | "No connection, so I'm using basic commands." (once per drive; later failures skip this line) |
| `RateLimited` | "AI limit reached, so I'm using basic commands." |
| `Timeout` | "The AI took too long, so I'm using basic commands." |
| `InvalidKey` | "Your <Provider> key stopped working. Check settings." |
| `Refused`, `Unavailable` | "The AI couldn't answer that, so I'm using basic commands." |

**`SYSTEM_PROMPT` (bundled asset):** "You are BobCar, a voice companion for a driver. Keep answers under 30 words, plain language, no lists, no markdown: they will be spoken aloud. If the user asks you to call, text, navigate, or play music, say briefly that they can just say the command."

#### M12.7 Consent and Play policy

Play's User Data policy covers third-party AI integrations. The developer remains responsible for disclosure and consent **even though the user supplies the key and BobCar has no backend.**

**Consent screen, per provider, before the first transmission:**
- **Title:** "Let BobCar use <Provider>?"
- **Body:**
  - What is sent: "the text of what you say, nothing else".
  - To whom: "<Provider>'s API, under the key you added", with a link to the provider's terms.
  - The free-tier training note where it applies (Gemini).
  - "Your key stays on this phone."
  - A **Request removal** link to that provider's privacy or data-request page, from `ThirdPartyRegister` (§8.4).
- **Buttons:** **"I understand, enable"** / "Not now". Declining leaves the provider configured but disabled, and the parser stays the brain.
- **Storage:** consent is saved as `ai_consent_<providerId>` = the accepted note version. If the note text changes, raise its version so consent is asked again.

**Privacy policy and Data safety (Phase 5d):**
- Name this flow: "If you turn on conversational AI, the text of what you say is sent to the AI provider you choose, under your own account. BobCar has no servers and never sees your prompts."
- Declare that transcript text is sent off-device and that keys are stored on-device only.

#### M12.8 Other tiers

- **"Sign in with ChatGPT" (future).** Reported as a limited preview as of 2026-10-01 (verify at build time).
  - Ship the tab **disabled** with "Not yet open to new apps."
  - If enrollment opens, build it with `androidx.browser` Custom Tabs + OAuth 2.0 with PKCE. Store access and refresh tokens in `KeyVault` and use the same `AiProvider` with `authKind = OAUTH`. Add a "Disconnect" action that revokes and wipes the tokens.
  - Never fake a working flow.
- **On-device Gemini Nano (AICore, ML Kit GenAI Prompt API).** Check developers.google.com/ml-kit/genai for the current artifact (verify at build time).
  - It's free, private, and offline. **Foreground only:** background calls return `BACKGROUND_USE_BLOCKED`, and a foreground service doesn't exempt it.
  - Per-app quotas return `BUSY`, and safety filters can refuse. Treat both as failures and fail soft.
  - Supported flagships only. **Always** gate on `checkFeatureStatus()`.
  - On the Zenfone 8 the row is grayed out with "Not available on this phone."
  - Link the ML Kit GenAI Additional Terms in the row.
- **"Open in …" hatch** (always available in 1.1, no setup). The car screen's **More** sheet (M4.3) has **Send to an AI app**:
  - It sends `Intent.ACTION_SEND` with `EXTRA_TEXT` = the transcript, through the share sheet.
  - The UI says: "Opens in their app; you'll finish there, and nothing comes back."

#### M12.9 Edge cases

- **No provider configured:** the AI toggle is disabled with "Pick a provider first."
- **401 mid-drive:** mark the key `invalid` and speak the fail-soft line. Don't retry until the user re-validates.
- **No network during setup:** the key is saved as `unverified`. The consent screen still gates the first transmission.
- **Dead zones:** never queue AI requests for later. A stale answer is worse than a fast parser reply.
- **Consent revoked:** the provider is disabled immediately. Ask whether to also delete the key.
- **Several providers configured:** exactly one is active. Switching doesn't re-ask consent that's already current.
- **"Thinking" state:** shown on the status line. Tapping Talk cancels the request.

#### M12.10 Acceptance tests (Zenfone 8; Gemini free tier for live tests)

1. **Fresh install, or update from V1:** AI is off and the provider is "none". "tell me a joke" gives the help prompt, and a proxy shows no AI network calls.
2. **Consent gate:** configure a Gemini key and turn AI on. The consent screen appears before any request. Declining sends nothing, and the parser still works.
3. **Key validation:** a bad key gives "didn't work". A good key is accepted. In airplane mode, the key is saved as unverified with the honest message.
4. **Answer:** "why is the sky blue" gives a spoken answer of about 30 words or fewer within 6 s.
5. **Parser first:** with AI on, "call Mom" opens the dialer and no LLM request is made (check the proxy). The same for "navigate to Walmart".
6. **Fail-soft:** airplane mode mid-session, then "why is the sky blue". TTS says "No connection, so I'm using basic commands." followed by the help prompt. No hang.
7. **429 (mocked):** one retry after 1 s, then the fail-soft line, all within 6 s. The local counter increments.
8. **Model churn (mocked list):** the saved model is missing, so the app falls back to the first model with a one-line notice.
9. **Key deletion:** the entry is wiped, AI mode turns off, and the debug screen shows "no key stored".
10. **Nano on the Zenfone 8:** grayed out and not selectable.
11. **Share hatch:** "Send to Claude" opens the share sheet with the transcript, and nothing returns.
12. **ChatGPT sign-in tab:** disabled with its note, and can't be tapped into a flow.

#### M12.11 Non-goals

- Never ship a BobCar API key.
- No app-to-app prompting; it's impossible.
- No `ROLE_ASSISTANT`.
- Never send anything but the transcript and the system prompt. No contacts, location, or vehicle data go out unless the user said them aloud.
- No background Nano, and no silent spending.

### M13. Vehicle profiles and settings screens

The schema is in §7. This section covers behavior and screens. The rendering rules are in M4.

#### M13.1 Vehicle list and detail (`MainActivity` routes `vehicles`, `vehicle/{id}`)

- **List:** every profile shows `btName`, a year/make/model subtitle, a default badge, and badges for state: an amber "Not paired" badge if the MAC is no longer bonded, and "No Bluetooth" for cars without it.
- **Add:** the Bluetooth picker from §9.1 step 3, or "My car has no Bluetooth".
  - A new MAC creates a profile. A MAC that already has a profile opens that profile instead (enforced by the unique index).
- **Make guess (`MakeGuesser`): suggest, never decide.**
  1. Match the Bluetooth name, case-insensitively, against a bundled list of make names and well-known head-unit names. For example, a name containing "Toyota" suggests Toyota, and "SYNC" suggests Ford.
  2. If nothing matches, leave the make blank. A VIN replaces the guess with an exact answer: typed or scanned from release 1.1 (M13.6), or read by an adapter in Phase 6.
  3. Every suggestion shows as "Is this a Ford?" with **Yes** and **Change**. Saving sets `makeConfirmed = true`.
  4. Until it's confirmed, nothing make-dependent runs (`KnownVehicleNotes`, OBDb).
  5. Add head-unit names to the list only after seeing them on a real car.
- **Support policy (`SupportPolicy`, soft).** The phone can't sense the diagnostics port, so support is decided from the model year and the phone's country (`Locale.getDefault().country`; no permission needed).

  | Year | Country | Result | What the user sees |
  |---|---|---|---|
  | 0 (unknown) | any | Unknown | Diagnostics rows: `WARN` "Add your car's year" |
  | Before 1996 | any | **Not supported** | "This vehicle isn't supported. BobCar works with cars from 1996 and newer." with **Continue anyway** and **Change year**. A "Not supported" badge in the vehicle list; diagnostics rows `FAIL` (pre-1996) |
  | 1996–2003 | not US | May not be fully supported | Diagnostics rows: `WARN` "Diagnostics may not work on this vehicle" |
  | 1996 and newer (US), 2004 and newer (elsewhere) | — | Supported | Nothing extra |

  - **Never blocking.** Voice car mode works for every vehicle, supported or not.
  - **Plain language.** The words "OBD-II" never appear in this flow (M4.8).
  - **The adapter is the final word.** From Phase 6, if a real adapter connects and the car answers, that result overrides the year rule.
- **Detail screen:**
  - Editable make, model, and year (changing them keeps `makeConfirmed = true`), plus the VIN. The VIN field accepts 17 characters, uppercase, with no I, O, or Q. From release 1.1, an **Add VIN** row offers typing or scanning, then decoding (M13.6).
  - Read-only `btMac`, or "No Bluetooth" with an **Add Bluetooth** action if the car is paired later.
  - Rows: **Link car** (M1.3), **Compatibility report**, **Test steering-wheel button** (M11.5), **Use steering-wheel button** switch, **In Android Auto, use** (Android Auto or BobCar; shown once Android Auto has run in this car, M1.6), **Hey Bob** (release 1.1: per-vehicle enable, wake phrase, sensitivity, the **Use car mic (experimental)** switch, **Run car-mic test**), **Pair diagnostics adapter** (V1: bonded classic Bluetooth adapters only, saved with the note "Diagnostics arrive in a later version". Phase 6: picks classic Bluetooth, BLE, Wi-Fi, or USB adapters; M14.3), **Driving detection** (release 1.1, no-Bluetooth cars only), **Set as default vehicle**, **Delete vehicle** (with confirmation).
- **Delete:** deleting the default or active vehicle makes the oldest remaining profile the default. If the deleted vehicle has a CompanionDeviceManager association, remove it (`cdm.disassociate(id)` on 33+).
- **Re-link:** after the user unpairs and re-pairs the car, **Re-link** re-runs the picker and updates `btMac`/`btName` on the **existing** profile, keeping its flags and results. Then it re-runs **Link car**.

#### M13.2 Global settings (`MainActivity` route `settings`)

**V1 controls:**
- A TTS on/off switch and a rate slider (0.5–2.0).
- Day/night preference.
- The unknown-car prompt and "stay open on disconnect". The Android Auto choice is per car (M13.1).
- Offline voice status (M2.3).
- **Permissions:** every permission and access BobCar uses, with the §8.3 text, its status, and a **Manage** button.
- **Privacy & data (§8.4):**
  - Every active third-party flow, with **Privacy policy** and **Request removal** links.
  - The cloud-speech choice.
  - **Delete all BobCar data on this phone**.
- About: licenses, the privacy policy link, and "Go home uses the Home saved in Google Maps."

**Added in release 1.1:**
- **Weather location:** grant approximate location later, and an editable ZIP field. The row states: "Used only for weather. Sent only to Open-Meteo." Settings is the only place with text entry, alongside the VIN field (5a).
- The global hotword switch and the battery settings shortcut (M10.7) (5b).
- "AI assistant" (M12) (5c).
- The Open-Meteo attribution on the About screen (5a).

#### M13.3 Edge cases

- **MAC randomization:** Android randomizes the *phone's* BLE address, not bonded classic device addresses. `btMac` and `obdAdapterAddress` are stable. Note it in a code comment so nobody "fixes" it. A MAC is a matching key, not proof of identity.
- **`scoHoldsReliably == null`** means "not tested", never "false". Only the car-mic test or the 3-strike rule sets it.
- **Year 0 or blank make:** allowed. The UI shows `btName`.
- **Wrong guess:** the user changes it in the detail screen. Pre-seeded notes that no longer match the confirmed vehicle are removed.
- **Unpaired car:** the profile remains and `findByMac` never matches until re-linked.

#### M13.4 Acceptance tests

1. **Fresh install, create the Taurus profile:** `wheelButtonSupported == false` with the pre-seeded `FAIL`, `scoHoldsReliably == null`, `obdProtocol == "not-tested"`, and `isDefaultVehicle == true`.
2. **Two cars:** connect each in turn. The active vehicle, greeting name, and flags follow the matched profile, and the default survives a restart.
3. **Atomic writes:** kill the app mid-check. The database inspector shows no half-written state.
4. **Re-link after unpair:** flags and results are preserved.
5. **Duplicate add:** opens the existing profile, with no duplicate row.
6. **Settings round-trip:** change every setting, force-stop, and relaunch. Every value persists, and `tts_rate` audibly changes the speech rate.
7. **No-Bluetooth car:** create the F-350 profile with no Bluetooth. `btMac == null`, the list shows the "No Bluetooth" badge, and car mode opens from the launcher.
8. **Make guess:** a Bluetooth name containing "SYNC" suggests Ford. **Change** to another make saves the user's choice, and no pre-seeded notes appear before confirmation.
9. **Support policy:**
   - Year 1995: the soft message, **Continue anyway** works, and a "Not supported" badge appears.
   - Year 2001 with a non-US phone locale: the "may not work" note.
   - Year 2004 (F-350): no message.
   - Unit tests cover every row of the policy table.
10. **Wheel button per vehicle:** turning **Use steering-wheel button** on for one vehicle doesn't affect another.

#### M13.5 Non-goals

- No cloud sync, backup, or accounts. Losing the phone loses the profiles. JSON export/import is a v2 item; keep the schema stable so it's easy later.
- The `secrets` DataStore file (release 1.1) is excluded from backup (§7.8, §8.2).

#### M13.6 VIN entry, scan, and decoding (release 1.1, Phase 5f)

This identifies the car exactly without an adapter. The Bluetooth-name guess (M13.1) becomes the fallback for users who skip it.

- **Entry points:** vehicle detail → **Add VIN**, the "What's new in 1.1" card, and an optional first-run step for new installs. Never in car mode.
- **Where to find it,** shown on screen: "Look at the driver-side corner of the windshield (read it from outside), the sticker on the driver's door jamb (usually with a barcode), or your registration or insurance card."
- **Scanning:**
  - Use the on-device **ZXing** scanner (`com.journeyapps:zxing-android-embedded`; verify the version and license at build time). It sends nothing anywhere. Google's code scanner was rejected because ML Kit sends usage metrics to Google (§8.4).
  - Ask for `CAMERA` only when the user taps **Scan**, with the §8.3 text. Frames are never saved. Scan for Code 39, Code 128, Data Matrix, and QR.
  - Clean the result: uppercase it and drop spaces. If it's 18 characters starting with "I", drop the "I"; some import labels add one.
  - If the camera permission is denied, typing stays fully usable.
- **Typing:** a 17-character field that uppercases as you type and rejects I, O, and Q.
- **Validation:**
  - The VIN must be 17 characters matching `[A-HJ-NPR-Z0-9]`.
  - For North American VINs (first character 1–5), check the check digit in position 9.
  - If it fails, say "That VIN doesn't look right. Check it and try again." Allow saving only after a second confirmation, because some older and imported VINs don't follow the rule.
- **Decoding:**
  - **`VinDecoder` sends a masked VIN (§8.4).**
    - Keep characters 1–8, 10, and 11, and replace position 9 (the check digit) and 12–17 (the serial) with `*`, for example `1FAFP53U*9A******`. vPIC supports partial VINs with `*`.
    - Call `GET https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/{maskedVin}?format=json`.
    - If the masked decode loses make or year in testing, send position 9 too, since it's a computed digit. Never send 12–17.
    - The full VIN stays on the phone. It uses `Make`, `Model`, `ModelYear`, `EngineModel`, and `FuelTypePrimary`. M14.5 uses the same class.
  - Before the first lookup, show the §8.3 VIN text.
  - Ask "This looks like a 2009 Ford Taurus. Use this?" with **Use** and **Edit**. **Use** writes `vin`, `make`, `model`, and `year`, and sets `makeConfirmed = true`.
  - **Offline:** save the VIN and set `vinDecodePending = true`. No background jobs are used.
    - Whenever `MainActivity` opens or car mode exits, if any vehicle has `vinDecodePending` and the phone is online (`ConnectivityManager` reports validated internet), decode it.
    - Then show the **Use** / **Edit** prompt as a card in `MainActivity` (never in car mode) and clear the flag.
  - **Partial or empty result** (common for cars not sold in the US): keep the VIN and let the user fill in make, model, and year.
- **Acceptance tests:**
  1. Scan the Taurus door-jamb barcode. The VIN is correct and decodes to a 2009 Ford Taurus.
  2. Type the F-350's VIN. It decodes, and **Edit** can correct anything vPIC got wrong.
  3. Type a VIN with a wrong check digit. The warning appears, and saving needs a second confirmation.
  4. Scan an 18-character code starting with "I". It's saved as the 17-character VIN.
  5. In airplane mode, the VIN is saved. It decodes and asks for confirmation once the phone is back online.
  6. Deny the camera permission. Typing still works and nothing breaks.
  7. A proxy shows vPIC received only the masked VIN, with characters 12–17 as `*`.

### M14. OBD-II diagnostics (post-V1)

> **Hardware-gated. Build in Phase 6, after V1 ships.** This needs an OBD-II adapter (Part 5). It works on any car with an OBD-II port and nothing in it is make-specific.

#### M14.1 Scope

**Does (read-only):**
- Connect to an ELM327-compatible adapter over classic Bluetooth, BLE, Wi-Fi, or USB. Auto-detect the protocol and record it per vehicle.
- Read stored (mode 03) and pending (mode 07) codes, with offline descriptions.
- Poll live data (mode 01) while the diagnostics screen is visible.
- Read the VIN (mode 09), decode it with the 1.1 `VinDecoder` (NHTSA vPIC), and offer the result for the user to confirm.
- Read freeze-frame data (mode 02).

**Doesn't:**
- **Clear codes (mode 04). That's a wishlist item, not planned.**
  - Clearing erases freeze-frame data a mechanic needs.
  - It resets emissions readiness, so the car can fail inspection until the monitors run again.
  - It can hide a real fault from the driver.
  - Offering it invites misuse and blame. If it's ever added, it needs its own design and review.
- Write anything to the vehicle: no coding, flashing, key programming, or control commands (mode 08).
- Read ABS, airbag, or transmission modules beyond what M15's community data covers.

**Coverage (verify per vehicle):**

| Vehicle | Generic OBD-II |
|---|---|
| Gas/diesel: US 1996+, Canada 1998+, EU 2001+ (gas) / 2004+ (diesel), most other markets by the late 2000s | Yes, over one of the 5 standard protocols |
| Heavy pickups over 8,500 lb such as the F-350, especially older diesels | Usually, but some standard readings may be missing; the probe records what answers |
| Hybrids | Yes |
| EVs | Partial: many standard readings are missing. Battery data needs M15 |
| 2018+ cars with security gateways | Reading usually works |
| Pre-1996 (OBD-I), heavy trucks (J1939), motorcycles | Not supported |

#### M14.2 Libraries

- **`eltonvs/kotlin-obd-api` via JitPack** (`maven("https://jitpack.io")`, `com.github.eltonvs:kotlin-obd-api:<latest>`). It handles AT commands, DTCs, VIN, and live-data parsing. It takes an `InputStream`/`OutputStream` pair, so it works over every transport. Verify the version and license at build time.
- **`mik3y/usb-serial-for-android` via JitPack** for USB adapters. It has drivers for FTDI, CP210x, CH34x, PL2303, and CDC-ACM serial chips. Verify the version and license at build time.
- BLE and Wi-Fi use framework APIs only.
- **Don't use `pires/obd-java-api`.** It's abandoned and has a blocking Java API.

#### M14.3 Transports

```kotlin
interface ObdTransport {
    val kind: String                     // "spp" | "ble" | "wifi" | "usb"  (stored as VehicleProfile.obdTransport)
    suspend fun open(): Boolean          // Dispatchers.IO; 3 attempts, 2 s apart
    val input: InputStream               // BLE/Wi-Fi/USB wrap their callbacks as streams, so kotlin-obd-api
    val output: OutputStream             // and the raw fallback (M14.4) work unchanged
    fun close()
}
```

| Transport | Picking the adapter | Connecting | Permissions | Notes |
|---|---|---|---|---|
| Classic Bluetooth | The user pairs it in Android Settings, then picks it from BobCar's bonded-device list | `createRfcommSocketToServiceRecord(00001101-0000-1000-8000-00805F9B34FB)`, then `connect()` | `BLUETOOTH_CONNECT` (31+) | The most common cheap adapters |
| BLE | The CompanionDeviceManager dialog with a `BluetoothLeDeviceFilter`. This needs no location or scan permission | `connectGatt`, then find a write characteristic and a notify characteristic (common UART services: `FFF0`, `FFE0`, Nordic UART `6E400001-…`) and enable notifications | `BLUETOOTH_CONNECT` (31+) | Common on newer adapters. Writes are often limited to 20 bytes, so split them |
| Wi-Fi | The adapter runs its own Wi-Fi network (often named "WiFi_OBDII"). On API 29+, request it with `WifiNetworkSpecifier`; below 29, the user joins it in Wi-Fi settings | TCP socket to the adapter (usually `192.168.0.10:35000`, editable), bound to that `Network` | `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`; others depend on API level (verify at build time) | Below API 29 the phone loses internet while connected |
| USB | The user connects a USB OBD adapter to the phone with a USB OTG cable. Android asks "Allow BobCar to access <device>?" | `usb-serial-for-android`; try 38400 baud, then 115200, then 9600 | No manifest permission; a per-device system prompt. `<uses-feature android:name="android.hardware.usb.host" android:required="false"/>` | The most reliable link. **The car's own USB ports can't be used:** they only charge, play media, or run Android Auto |

**Rules:**
- **Address format.** `obdAdapterAddress` stores a MAC (classic or BLE), `host:port` (Wi-Fi), or `vendorId:productId` (USB).
- **Keep the car and the adapter separate.** `CarConnectionReceiver` matches only `btMac`, and the adapter never triggers car mode (M1.7).
- **Short-lived connections.** The connection lives only while the diagnostics screen is open. It must not disturb the car's Bluetooth audio.
- **Retries.** After 3 failed attempts, show "Couldn't reach the diagnostics adapter. Is it plugged in?" Never retry forever.

#### M14.4 ELM327 init sequence (canonical)

Run once per connection on `Dispatchers.IO`. Terminate each command with `\r` and read until the `>` prompt.

| # | Command | Purpose | Expected | If unexpected |
|---|---|---|---|---|
| 1 | `ATZ` | Reset | `ELM327 v1.x/v2.x`, then `>` | Wait 1 s; some clones need a second `ATZ` |
| 2 | `ATE0` | Echo off | `OK` | Retry once; otherwise strip echoed lines in the parser |
| 3 | `ATL0` | Linefeeds off | `OK` | On `?`, use `ATL1` and strip linefeeds |
| 4 | `ATS0` | Spaces off | `OK` | On `?`, use `ATS1` and strip spaces |
| 5 | `ATH1` | Headers on (needed to tell which ECU answered) | `OK` | On failure, set `obd_headers = WARN` and disable multi-ECU attribution |
| 6 | `ATSP0` | Auto protocol | `OK` | — |
| 7 | `0100` | Supported PIDs 00–1F; forces the protocol search | `41 00 …` with a header | `NO DATA` / `UNABLE TO CONNECT` → "turn the key to ON" (M14.10) |
| 8 | `ATDP` | Describe protocol | e.g. `AUTO, SAE J1850 PWM` or `AUTO, ISO 15765-4 (CAN 11/500)` | Store the raw text (minus `AUTO, `) in `obdProtocol` |
| 9 | `ATDPN` | Protocol number | e.g. `A1` (J1850 PWM), `A6` (CAN 11/500) | Log for debugging |

**Timeouts and retries:**
- Per-command read timeout: 2000 ms.
- Leave the adapter's own timeout at the default (`ATST32` ≈ 200 ms; units are 4 ms). For slow ECUs, try `ATST64` (≈ 400 ms) or `ATST96` (≈ 600 ms).
- Each command gets 3 attempts with 500 ms backoff. After that, send `ATZ` once and restart from step 2. After 2 full init failures, abort and tell the user.
- These adapter replies are **errors, never data:** `?`, `NO DATA`, `UNABLE TO CONNECT`, `BUS INIT: …ERROR`, `BUFFER FULL`, `CAN ERROR`, `STOPPED`.

**Test vehicles (verify in each vehicle):**

| Vehicle | Expected protocol | First protocol search |
|---|---|---|
| 2009 Taurus | `ISO 15765-4 (CAN 11/500)`. US law required CAN from model year 2008 | About 1–2 s |
| 2004 F-350 | `SAE J1850 PWM`, Ford's pre-CAN protocol | 5–20 s, like other legacy protocols |

**Many cheap clone adapters don't really support J1850 PWM.** The F-350 needs an adapter that does (Part 5).

#### M14.5 OBD modes

Examples below are shown **with spaces for readability**; with `ATS0` the adapter omits them. The parser strips the header, the CAN length byte (CAN only), and the mode echo (`request + 0x40`) before decoding.

- Headers differ by protocol: `7E8` on 11-bit CAN, or three bytes such as `41 6B 10` on J1850.
- Use the transport-independent parser for all of them.

**Mode 01, live data (must-implement set):**

| PID | Name | Bytes | Formula | Unit |
|---|---|---|---|---|
| `0C` | Engine RPM | A, B | `(256A + B) / 4` | rpm |
| `0D` | Vehicle speed | A | `A` | km/h (convert for display) |
| `05` | Coolant temperature | A | `A − 40` | °C |
| `0F` | Intake air temperature | A | `A − 40` | °C |
| `11` | Throttle position | A | `100A / 255` | % |
| `2F` | Fuel level | A | `100A / 255` | % |
| `21` | Distance with MIL on | A, B | `256A + B` | km |
| `31` | Distance since codes cleared | A, B | `256A + B` | km |

- **Example:** A = `0x1A` (26) and B = `0xF8` (248) give (26×256 + 248)/4 = 1726 rpm.
- **Query `0100`, `0120`, and `0140` first.** The bitmasks list the PIDs the ECU supports; poll only those.

**Mode 03 (stored codes) and mode 07 (pending codes):**
- On **CAN**, the reply is `43 <count> <hi lo> …`. The byte after `43` (or `47`) is the number of codes.
- On **legacy protocols** (the F-350) there's **no count byte**: each frame carries 3 codes, padded with `00 00`.
- The parser branches on `isCan(obdProtocol)`. With headers on, each ECU replies separately; merge and de-duplicate.
- **DTC bit math:**
  - Bits 15–14 select the letter: `00` = P, `01` = C, `10` = B, `11` = U.
  - Bits 13–12 are the first digit (0–3). Bits 11–8, 7–4, and 3–0 are the next three hex digits.
  - `0x01 0x33` → **P0133**. `0xC1 0x33` → U0133.

**Mode 04 (clear codes): not implemented** (M14.1). `ObdCommandSet` has no way to send it, and a unit test asserts that.

**Mode 09 PID 02 (VIN), then decoding:**
1. **Read it.** On CAN the reply is a multi-frame message (`49 02 01` + 17 ASCII bytes) that must be reassembled. Legacy protocols return several `49 02` lines. Decode as ASCII and drop padding. The result must match `[A-HJ-NPR-Z0-9]{17}`. Otherwise retry once, then `obd_vin = WARN("VIN unreadable; enter it in settings")`.
2. **Decode it** with the `VinDecoder` from release 1.1 (NHTSA vPIC with the masked VIN; M13.6). If the VIN read from the car matches the one already confirmed, skip the prompt.
3. **Confirm it.** Ask "This looks like a 2004 Ford F-350. Use this?" with **Use** and **Edit**.
   - Only after confirmation, write `vin`, `make`, `model`, and `year`, and set `makeConfirmed = true`.
   - The confirmed vehicle selects the M15 signal set.
4. **Limits.**
   - vPIC covers vehicles sold in the US best. Elsewhere results may be partial, so keep the user's own entries.
   - With no connection, keep the VIN and decode it the next time the phone is online.

**Mode 02 (freeze frame):**
- `02 00 00` returns the supported PIDs for frame 0.
- `02 02 00` returns the code that triggered the freeze frame.
- Then read the snapshot PIDs with the mode-01 formulas.
- `NO DATA` means "No freeze-frame data stored."

#### M14.6 Code descriptions (offline)

- Bundle `assets/dtc_codes.json`, mapping `"P0133"` to its description.
- Build it from an **openly licensed** generic-code list and credit it on the About screen. SAE J2012 itself is a paid standard, so don't copy its text (verify at build time).
- Manufacturer-specific codes show the raw code plus "Manufacturer-specific code"; M15 may supply a description.
- No network lookups for descriptions, ever.

#### M14.7 Live polling

- Runs only while the diagnostics screen is visible (lifecycle-aware `LaunchedEffect`). There's no foreground service.
- Round-robin over the supported must-implement PIDs:
  - **CAN:** RPM, speed, and coolant temperature at 2 Hz or more each, the rest at about 1 Hz.
  - **Legacy protocols (the F-350):** about 1 Hz each. Those buses are slower.
- Stop on screen exit, link close, or 3 consecutive failures, then show "Link lost" with **Reconnect**.
- Values flow through a `StateFlow` to big-number tiles.

#### M14.8 Capability probes

| Key | `PASS` | `WARN` |
|---|---|---|
| `obd_link` | Init and `0100` succeeded. Note: "Connected · <obdProtocol> · <transport>" | Paired but the car didn't answer: "Is the key ON?" Or not paired: "Needs adapter (wishlist)…" |
| `obd_headers` | `ATH1` returned `OK` | "This adapter can't show headers; multi-ECU details are off." |
| `obd_vin` | Mode 09 returned a valid VIN | "VIN unreadable; enter it in settings." |
| `obd_freeze_frame` | `02 00 00` answered | "No freeze-frame support reported." |

Fallbacks that worked (spaces, linefeeds, headers, USB baud rate) are remembered per vehicle, so reconnects skip the trial and error.

#### M14.9 `DtcReading` (Room, added in database version 2)

```kotlin
@Entity(tableName = "dtc_readings",
    foreignKeys = [ForeignKey(entity = VehicleProfile::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")])
data class DtcReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val code: String,          // "P0301"
    val description: String,   // from dtc_codes.json, or "No description available offline"
    val ecuHeader: String?,    // e.g. "7E8"; null without headers
    val readAt: Long,          // epoch millis
    val kind: String           // "stored" (mode 03) | "pending" (mode 07)
)
```

Readings are a cache, not a log. Replace them on each read. Readings older than 7 days show a "stale" badge until they're read again.

#### M14.10 Edge cases

- **Ignition off:** the adapter is powered by the always-on OBD pin, so `ATZ` works but `0100` fails. Say "Adapter connected, but the car isn't responding. Turn the key to ON; the engine can stay off."
- **Multi-ECU disagreement:** show the engine computer's value and note the conflict in a details expander.
- **Clone adapters:** fall back step by step (M14.4) and record what worked. A clone that claims J1850 PWM support but never answers on the F-350 is the adapter's fault; the message says so.
- **BLE or Wi-Fi drops while driving:** treat it as "Link lost". Never auto-reconnect in a loop.
- **USB cable unplugged:** `UsbManager.ACTION_USB_DEVICE_DETACHED` → "Link lost".
- **Adapter battery drain:** the hardware notes remind the user to unplug the adapter if the vehicle sits for weeks.

#### M14.11 Acceptance tests (adapter required, key ON)

1. **F-350:** init completes and `obdProtocol` reads J1850 PWM (verify). `obd_link` is `PASS`. Codes and live data work, and anything the truck refuses is recorded rather than shown as zero.
2. **Taurus:** init completes on CAN. While driving, each must-have PID updates at 2 Hz or faster.
3. **Codes:** mode 03 returns the list (empty is fine). Descriptions resolve in airplane mode.
4. **VIN:** read, decode, and confirm. The profile updates only after **Use**.
5. **Transports:** 6a covers classic Bluetooth and Wi-Fi (PC emulator); 6b covers BLE, USB, and a Wi-Fi hardware adapter. Connect once each.
6. **Unplug mid-poll:** "Link lost", with no crash and no zombie coroutine.
7. **Unit tests** (no hardware):
   - P0133 and U0133 decoding.
   - The RPM formula.
   - CAN vs legacy mode-03 framing.
   - VIN reassembly.
   - Reply stripping with headers and spaces on and off.
   - A test asserting that mode 04 can't be sent.

#### M14.12 File and class map

```
obd/
  ObdManager.kt            # isAvailable(): true when an adapter is set and a link succeeded this session
  transport/
    ObdTransport.kt  SppTransport.kt  BleTransport.kt  WifiTransport.kt  UsbTransport.kt
  ObdInitSequence.kt       # M14.4; writes obdProtocol + obd_link/obd_headers
  ObdCommandSet.kt         # modes 01/02/03/07/09 only — no mode 04
  DtcParser.kt             # CAN vs legacy framing
  DtcDatabase.kt           # assets/dtc_codes.json
  VinReader.kt             # mode 09 reassembly; decoding reuses VinDecoder from 1.1 (M13.6)
  FreezeFrameReader.kt
  LivePidPoller.kt
ui/screens/DiagnosticsScreen.kt              # M14.13
data/DtcReading.kt, data/DtcReadingDao.kt   # database version 2
```

#### M14.13 Diagnostics screen and voice (Phase 6)

Entry points: **More → Diagnostics** on the car screen, or the voice commands "check engine", "read codes", or "diagnostics" (M2.5 rule 11).

**Driving rule.** While an adapter is connected, BobCar knows the car's speed from PID `0D`.
- **Speed above 0 (moving):** no lists and no live-data grid. The screen shows one big line ("2 stored codes") and a **Read them to me** button.
- **Speed 0 (parked):** the full screen.
- **No adapter speed available:** treat the car as moving.

**Voice (`DiagnoseAction`, Phase 6):**
- **No adapter set:** the V1 line (§6.5).
- **Adapter set but not reachable:** "I can't reach the diagnostics adapter. Is it plugged in?"
- **Codes found:** "<n> stored code(s)." Then the first one in plain words: "P0301, cylinder 1 misfire. Usually a spark plug or coil. I'm reading codes, not a mechanic."
  - With more codes: "Want the next one?" A pending follow-up accepts "yes", "next", or "cancel" (60 s).
- **No codes:** "No trouble codes are stored."
- **Never** offer to clear codes (M14.1).

**Parked layout** (big text, no scrolling past 3 items per page, with page buttons instead):
1. A **Codes** card: stored and pending, each as code, plain description, and "stored" or "pending". Tapping a code shows its freeze frame if there is one.
2. A **Live data** card: big tiles for RPM, speed, coolant temperature, and fuel level, shown only for supported PIDs. Polling runs only while this card is visible (M14.7).
3. A **Vehicle** card: decoded VIN, protocol, and adapter, with a "Copy summary" action.
4. The footer: "Have your mechanic clear codes after the repair."

**Acceptance tests:**
- While moving (simulated speed > 0 via the PC emulator), only the summary line and **Read them to me** appear.
- Parked, all cards render, and live tiles update only while visible.
- "check engine" with 2 codes speaks the count and the first code, and "next" reads the second.
- No screen or voice path offers clearing.

### M15. Make-specific extended data (post-V1)

> **Hardware-gated. Build in Phase 7, after M14 works.** It works for any make that OBDb documents; nothing here is Ford-specific.

#### M15.1 Scope

**Does:**
- Pick the signal set for the user-confirmed vehicle (M14.5) and read the manufacturer values it defines (mostly mode 22).
- Record per-vehicle support honestly.

**Doesn't:**
- Write anything.
- Reach data buses the adapter isn't wired for (M15.4).

#### M15.2 Data source: OBDb

- **What it is.** OBDb ([obdb.community](https://obdb.community), github.com/OBDb) is an open, community-maintained database of vehicle signal definitions. It has one repository per make and model (745+). Each has JSON signal sets in `signalsets/v3/`: `default.json` plus year-range overrides such as `2012-2020.json`.
- **License: CC BY-SA 4.0.**
  - Credit OBDb on the About screen.
  - Any change BobCar makes to the data must be shared under the same license; the best way is to contribute fixes upstream.
  - BobCar's own code isn't affected.
- **Delivery: bundled, never downloaded per vehicle (§8.4).**
  - A per-vehicle download would tell a third party which car the user drives.
  - Instead, a snapshot of the OBDb signal sets ships inside the app (`assets/obdb/`) and is refreshed with each app update.
  - Keep it small by including only the JSON signal sets, and check the size at build time.
- **Format.** Parse the v3 schema exactly as OBDb documents it (verify at build time). Never hand-edit cached files.

#### M15.3 Model (the only definition)

```kotlin
data class Signal(
    val id: String,                        // OBDb signal id
    val name: String,
    val unit: String,
    val header: String,                    // request header from the signal set, e.g. "7E0" (CAN)
    val command: String,                   // e.g. "221674"
    val decode: (ByteArray) -> Double?,    // built from the set's bit offset / length / scale / offset
    val validRange: ClosedRange<Double>?   // from the set when provided
)
data class SignalSet(val vehicleKey: String, val signals: List<Signal>)

interface SignalSource {                   // OBDbSignalSource is the only implementation
    suspend fun forVehicle(make: String, model: String, year: Int): SignalSet?
}
```

V1 registers no signal source.

#### M15.4 Requests, headers, and buses

- **Mode 22** ("read data by identifier") requests `22 <idHi> <idLo>` and gets `62 <idHi> <idLo> <data…>`. Example with headers and spaces on, on CAN: `7E8 05 62 16 74 8A 12` (length `05`, ID `0x1674`, data `8A 12`).
- **Headers.** Set the signal's header with `ATSH` before the request, then restore the protocol's default.
  - If unsure how to restore it, re-run init steps 2–6.
  - A stale header breaks generic polling in confusing ways.
- **Second data buses.** Some makes put body data on a bus a basic adapter can't reach. Ford's MS-CAN, for example, is on OBD pins 3/11, while basic adapters wire only 6/14.
  - When a signal needs such a bus and the adapter lacks it, show "Needs an adapter that can reach this data" rather than spinning on `NO DATA`.
- **EVs.** Battery level, temperatures, and charging data are manufacturer values. They're available only if OBDb documents the vehicle.
- **Security gateways.** Some 2018+ vehicles refuse manufacturer requests; record them as unsupported.

#### M15.5 Vehicle matching

- Use the **confirmed** make, model, and year (`makeConfirmed = true`), which come from the vPIC decode or the user's own edits.
- Pick the OBDb repository for that make and model, then the year-range override that contains the year, falling back to `default.json`.
- **No match:** show "No community data for this vehicle yet", plus a link explaining how to contribute to OBDb. This is a normal state, not an error.

#### M15.6 Capability probe `extended_pids` and storage

1. Send each signal's request once, with its header.
2. A signal is **supported** if the reply echoes the request, `decode()` is non-null, **and** the value is inside `validRange` (when given).
3. Write the results:
   - `extended_pids = PASS("<n> extra values available for this vehicle")` if at least one signal is supported.
   - `WARN("This vehicle or adapter didn't answer the extended requests")` if none are.
   - `WARN("No community data for this vehicle yet")` if there's no signal set.
4. **Storage.** Phase 7 adds `supportedSignals: List<String>` (signal IDs) to `VehicleProfile` (database version 3). Reconnects use it and skip re-probing. **Re-check** probes again.

#### M15.7 Edge cases and limits

- **Community data can be wrong for a given model year.** Validation on the vehicle is mandatory. Log raw replies at debug level only.
- **Read-only,** with the same boundary as M14.

#### M15.8 Acceptance tests (adapter required, key ON)

1. On the Taurus and the F-350, the confirmed vehicle selects an OBDb signal set, or reports "No community data".
2. On each test vehicle, at least one extended value passes with a plausible reading, or the refusals are recorded.
3. A simulated vehicle with no OBDb data shows the "No community data" state and no extended section.
4. A known-bad command renders nothing.
5. After a header change, generic polling resumes.
6. In airplane mode, extended data still works because the signal sets are bundled, and a proxy shows no OBDb request.

#### M15.9 File map

```
obd/ext/
  SignalSource.kt  OBDbSignalSource.kt   # loads the bundled snapshot + year-range selection
  SignalSet.kt  SignalDecoder.kt         # v3 schema → Signal.decode
  ExtendedProber.kt                      # M15.6
  ModuleHeader.kt                        # ATSH set/restore (try/finally)
```

Unit tests: year-range selection, decoder output against values captured from the test vehicles (checked in as fixtures), and header restore.

---

## Part 5: Hardware wishlist

**Rule: none of this is needed for V1.** V1 ships with the phone plus the car's factory Bluetooth, and it works without Bluetooth too. This part lists every hardware dependency so that future purchases are informed.

### 1. OBD-II adapter (ELM327-compatible): buy this first

**What it is:** a dongle in the OBD-II port (under the dash, driver's side) that relays the diagnostic bus to the phone. Phase 6 supports all four connection types:
- Classic Bluetooth.
- Bluetooth Low Energy.
- Wi-Fi.
- USB, through a USB OTG cable to the phone.

**What it unlocks:**
- **M14:** codes, live data, VIN, readiness, and freeze frame on any OBD-II car.
- **M15:** extended data for any make that OBDb documents.

**Cost:** about $10–20 for a generic clone; about $25–100 for a brand-name unit (OBDLink, Veepeak, Vgate).

**Which one for the owner's testing:**
- **The 2004 F-350 uses J1850 PWM**, and many cheap clones don't really support it. Buy a brand-name adapter whose listing explicitly supports J1850 PWM, along with the other standard protocols.
- **For the iPhone version (Part 6), buy BLE or Wi-Fi.** iPhones can't use most classic Bluetooth adapters. A BLE adapter that supports J1850 PWM works with both phones and both test vehicles.
- An HS/MS-CAN switchable adapter (such as the OBDLink MX+) matters only for Ford body data on newer Fords.
- One adapter is enough to start. Testing every transport (Phase 6) eventually needs at least one adapter of each type, or a borrowed one.

**Software difficulty once you have one: medium.** The ELM327 command set is well documented. The work is the four transports, the parsers, and the data tables.

**Gotchas:**
- **Clone quality varies.** Many "v2.1" clones are v1.5 firmware with incomplete command sets.
- **Pairing differs by type.** A classic Bluetooth adapter is paired in Android Settings (PIN usually 1234 or 0000). BLE, Wi-Fi, and USB adapters are picked inside BobCar.
- **Wi-Fi adapters** can cut the phone's internet on Android 9 and older while connected.
- **Standby drain.** The OBD port is always powered and the draw is tens of mA. Unplug the adapter if the vehicle sits for weeks.

### 2. Raw CAN-bus interface (comma.ai panda-style)

**What it is:** a CAN transceiver plus microcontroller board (comma.ai panda, CANable, or an ESP32-based board) on the OBD port. It gives raw frame access instead of the ELM327's request/response protocol.

**What it unlocks:**
- Faster, parallel data access for M15.
- Future passive sniffing of body signals (doors, steering angle), and possibly wheel-button frames on cars where the press is visible on a reachable bus.

**Cost:** about $50–200 (CANable-style $50–80, panda about $200).

**Difficulty: medium.** Capture is easy. Decoding is per-make, per-model reverse engineering; comma.ai's opendbc helps, but coverage varies.

**Gotchas:**
- **No universal frame map.**
- **Ford buses.** HS-CAN is on OBD pins 6/14 and MS-CAN is on pins 3/11. Some frames of interest may only appear on one bus, or behind a module gateway, so expect wiring research per vehicle.
- **Read-only sniffing is safe; transmitting isn't.** Transmitting on the bus is out of scope for this project, because sent frames can interfere with ABS, airbags, or steering.

### 3. Purpose-built companion device (concept and R&D; not V1 or V2)

**What it is:** the owner's long-term concept. A small device powered by the 12 V socket or the OBD port bridges car networks (CAN, steering-wheel-control lines, cameras, sensors) to BobCar over Bluetooth or Wi-Fi.

**What it could unlock:**
- A hardware path to things software can't reach, for example intercepting the Taurus's steering-wheel line before the SYNC module.
- Cameras and extra sensors.

**Cost:** unknown. It's a design-and-build project; the parts are cheap, but the engineering and per-car validation aren't.

**Difficulty: hard.**
- Embedded firmware (real-time CAN, wireless bridging, power management), plus a phone-side device-management layer.
- Effectively a second product.

**Gotchas:**
- **Automotive-grade power design.** Load-dump transients kill unprotected boards.
- **Per-car wiring research.**
- **A mandatory safety review.** Never transmit on safety-critical buses, and the device must fail open.
- **It must not gate anything currently planned.**

### Buy order

If the owner buys **one** thing, it's **item 1**: a brand-name adapter that supports J1850 PWM, for testing on the F-350 and the Taurus. It's the only purchase that unlocks planned mechanics (M14, M15), and it works on any OBD-II car. Item 2 is for when M15 hits ELM327 limits, and item 3 is a concept, never a prerequisite.

## Part 6: iPhone release (planned)

**Status: planned, not scheduled.** BobCar will also launch on iPhone. When to start, and how to share code, are decided when Android V1 ships (§4). Nothing here changes the Android build, except that Phase 0 keeps the shared logic in the pure-Kotlin `:core` module (§6.2).

The product rules carry over unchanged:
- Free, with no account and no servers of its own.
- The data-sharing rule (§8.4).
- Every permission says what it's for and every use (§8.3).
- No text entry in car mode.
- 1996 and newer, as a soft limit.

### 1. What the iPhone version needs

- **A Mac.** Building and signing iPhone apps needs macOS and Xcode, so the owner's Windows PC isn't enough. A rented cloud Mac also works.
- **An iPhone on iOS 17 or newer.** iOS 17 is the minimum because it's the first version whose Shortcuts automations run without asking (§3).
- **An Apple Developer Program membership,** about $99 a year (verify at build time). This is the owner's cost; the app stays free for users.
- **The same test vehicles:** the Taurus and the F-350.

### 2. Code sharing

- **Recommended: Kotlin Multiplatform (KMP).** `:core` becomes a KMP module built for both Android and iOS. The iPhone app is native Swift and SwiftUI, and it calls `:core` for:
  - The command grammar.
  - The support policy and make guess.
  - VIN checks and the masked VIN.
  - With diagnostics, the OBD decoders and OBDb signal sets.
- **Alternative: a separate Swift app** that rewrites `:core`. It's simpler to set up, but it doubles the parser and decoder work, and the two copies can drift apart.
- **Either way, the iPhone screens are native.** Cross-platform UI toolkits don't help here, because nearly all of BobCar is platform services: Bluetooth, speech, audio, and handing off to other apps.

### 3. Feature by feature

| Feature | Android | iPhone | iPhone notes |
|---|---|---|---|
| Opening when the car connects | Automatic (M1) | **One-time setup by the user** | iOS doesn't let apps open themselves. Setup walks the driver through a Shortcuts automation: **When <car> connects** (Bluetooth trigger) runs BobCar's **Start car mode** action, set to **Run Immediately** (iOS 17+). BobCar provides that action through App Intents, so it knows the automation ran and the capability report can show `PASS`. If the iPhone is locked, BobCar may only appear after unlock (verify) |
| Cars without Bluetooth | Launcher icon; driving detection (1.1) | Home-screen icon; a **When Driving Focus turns on** automation | The Driving Focus can turn on by itself when the iPhone senses driving, so BobCar needs no motion permission (verify that this trigger runs immediately) |
| Leaving car mode | 8 s after Bluetooth drops (M1.5) | When the car's Bluetooth audio disappears while BobCar is open, plus a "disconnects" automation if the trigger offers one (verify) | |
| Listening | On-device recognizer first (M2.3) | Apple's on-device speech recognition | `SpeechAnalyzer` (iOS 26+) or `SFSpeechRecognizer` with `requiresOnDeviceRecognition` (verify at build time). Apple's server recognition is a third-party flow (§8.4), used only if the user chooses it, like `cloud_speech_allowed` |
| Speaking | Local TTS voices | `AVSpeechSynthesizer`, local voices | |
| "Hey Bob" (1.1) | Microphone service during car mode (M10) | **Only while BobCar is on screen** | iOS allows background listening only in narrow cases, and a continuous background listener is an App Store review risk. Same openWakeWord model, through ONNX Runtime for iOS |
| Steering-wheel button | Cars that forward it (M11) | **Not available** | The iPhone always sends it to Siri. Instead, App Shortcuts let the driver say phrases such as "Hey Siri, check my engine with BobCar" (each phrase must include the app's name) |
| Calls | Dialer pre-filled; driver taps Call | `tel:` link; iOS asks "Call <name>?"; driver taps Call | Contacts permission, as on Android |
| Texts | Messaging app pre-filled; driver taps Send | Message composer pre-filled (`MFMessageComposeViewController`); driver taps Send | |
| Music | Play, pause, next, and previous in any app (M7) | **Apple Music and the phone's music library only** | iOS gives one app no way to control another app's playback. For Spotify and others, BobCar says to use the car's buttons or Siri |
| Navigation | Google Maps | Apple Maps by default; Google Maps if it's installed | Maps links only; no SDK and no key |
| "Go home" | Google Maps' saved Home (M8.2) | The driver's own contact card, picked once | iOS gives apps no access to the Maps Home. BobCar keeps only the card's identifier and reads the address when asked, so it still stores no address. Check whether a Google Maps "Home" link works on iPhone, which would be simpler (verify at build time) |
| Weather (1.1) | Open-Meteo, approximate location | Same, with approximate location from Core Location | Same rounding, ZIP fallback, and disclosure |
| Choose your AI (1.1) | User's key, in the Keystore (§7.8) | Same, with the key in the Keychain (this device only, no iCloud sync) | |
| VIN scan (1.1) | ZXing, on the phone | Apple's on-device barcode reader (AVFoundation): Code 39, Code 128, Data Matrix, and QR | No third party. Camera permission asked only on **Scan** |
| VIN decoding (1.1) | NHTSA vPIC, masked VIN | Same | |
| Diagnostics adapters | Classic Bluetooth, BLE, Wi-Fi, USB (M14.3) | **BLE and Wi-Fi only** | iPhones only talk to classic Bluetooth adapters that are Apple-certified (MFi), and using one needs the adapter maker's approval. iPhones have no USB serial. See Part 5 |
| Extended data (OBDb) | Bundled | Bundled, same `:core` decoders | |
| Clearing codes | Wishlist | Wishlist | |
| Android Auto / CarPlay | Per-car choice (M1.6) | **Per-car choice: CarPlay or BobCar** | See §4 |

### 4. CarPlay

- **The choice.** When CarPlay runs in a car, BobCar asks once per car, as in M1.6:
  - **CarPlay:** BobCar steps aside.
  - **BobCar:** car mode runs on the iPhone's screen while CarPlay keeps the car's screen.
  - The answer is saved in the same `projectionChoice` field.
  - Detect CarPlay from the audio route (`AVAudioSession` port type `carAudio`). That needs no CarPlay entitlement (verify at build time).
- **Optional: BobCar on the CarPlay screen.** Since iOS 26.4, CarPlay accepts *voice-based conversational* apps through a separate entitlement that Apple must approve. Reported limits (verify against Apple's CarPlay Developer Guide):
  - Voice is the main way to use the app, on Apple's voice control screen.
  - The app **can't control iPhone or car functions,** so calls, texts, music, and navigation stay on the phone. Questions, weather, time, AI answers, and a spoken diagnostics summary could fit.
  - There's no wake word. The driver opens the app on the car's screen.
- **Request the entitlement only after the iPhone app works.** It's an add-on, never a dependency.

### 5. Permissions (Info.plist usage texts)

Each text follows §8.3: what it's for and every use, in plain language. Each is asked only when its feature is first used.

| Key | Asked when | Used for |
|---|---|---|
| `NSMicrophoneUsageDescription` | First Talk tap | Hearing commands |
| `NSSpeechRecognitionUsageDescription` | First Talk tap | Turning speech into text on the phone |
| `NSContactsUsageDescription` | First call or text command | Finding the person to call or text, and reading the driver's own card for "Go home" |
| `NSAppleMusicUsageDescription` | First music command (verify whether play and pause need it) | Play, pause, and skip in Apple Music and the music library |
| `NSLocationWhenInUseUsageDescription` (1.1) | First weather question | Approximate location for weather. Set `NSLocationDefaultAccuracyReduced`. The ZIP fallback still applies |
| `NSCameraUsageDescription` (1.1) | **Scan** | Reading the VIN barcode on the phone |
| `NSBluetoothAlwaysUsageDescription` | Pairing a BLE adapter | Talking to the diagnostics adapter |
| `NSLocalNetworkUsageDescription` | Pairing a Wi-Fi adapter | Talking to the diagnostics adapter over its Wi-Fi |

There's no tracking prompt, because BobCar doesn't track. The App Store privacy label must match the §8.4 register.

### 6. Build plan: Phase 8

Phase 8 starts after Android V1 ships and mirrors phases already proven on Android.

- **8a: voice car mode.**
  - KMP `:core` for iOS.
  - The SwiftUI car screen, speech, TTS, calls, texts, Apple Music, Maps, time, help, and the capability report.
  - The Shortcuts setup flow and the CarPlay choice.
  - App Store category: Navigation or Utilities (verify at build time).
  - **Done when:** a Taurus drive works end to end through the Shortcuts automation, and the F-350 works from the icon.
- **8b: release 1.1 features.** Weather, on-screen "Hey Bob", Choose your AI, and VIN typing and scanning.
- **8c: diagnostics.** BLE and Wi-Fi transports, after Phase 6 has proven the decoders on Android.
- **8d (optional): BobCar on the CarPlay screen,** once Apple grants the entitlement.

### 7. iPhone limits to state in the store listing and help

- Opening by itself needs a one-time Shortcuts setup.
- No steering-wheel button; use "Hey Siri, … with BobCar" instead.
- Music control covers Apple Music and the music library only.
- "Hey Bob" works only while BobCar is on screen.
- Diagnostics need a BLE or Wi-Fi adapter.

## Appendix A: Corrections and decisions log

This is everything that changed compared to `car-app-complete-guide.md`.

### A.1 Android platform corrections

| # | Previous claim | Correction | Where now |
|---|---|---|---|
| 1 | `ACL_CONNECTED`/`DISCONNECTED` aren't exempt from Android 8 implicit-broadcast limits, so the app needs a boot receiver and an always-on foreground service | They **are** on the exemption list (developer.android.com, checked 2026-10-02). A manifest receiver works from cold. The boot receiver, 24/7 service, and `RECEIVE_BOOT_COMPLETED` were removed | M1.2, M1.4, §8 |
| 2 | The receiver/service can `startActivity(CarModeActivity)` | Android 10+ blocks background activity starts. Documented exceptions include `SYSTEM_ALERT_WINDOW` granted by the user and notification taps; CompanionDeviceManager is **not** listed for activity starts. Design: overlay permission (opt-in) or a tap-to-open notification | M1.4, M2.7, §9.1 |
| 3 | Starting the foreground service from the ACL receiver just works | API 31+ blocks background FGS starts. The documented exemption used is CompanionDeviceManager association plus `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` / `REQUEST_COMPANION_RUN_IN_BACKGROUND`. Fallback: the activity starts the service when visible | M1.3, M1.4 |
| 4 | `CarModeService` needs "no special type"; the manifest used `connectedDevice` without its permission | FGS types are mandatory for targetSdk 34+. `connectedDevice` with `FOREGROUND_SERVICE_CONNECTED_DEVICE` is declared | §8, M1.4 |
| 5 | `FOREGROUND_SERVICE_MIC` | The permission is `FOREGROUND_SERVICE_MICROPHONE` | §8 |
| 6 | The hotword service can start whenever car mode starts | A microphone-type FGS can't be created from the background (targetSdk 34+); it now starts from the visible `CarModeActivity` | M10.4 |
| 7 | `RoleManager.requestRole(ROLE_ASSISTANT)` is "the supported path" | `ROLE_ASSISTANT` is `requestable="false"` in AOSP roles.xml (checked), and the method is `createRequestRoleIntent`. Removed `VoiceInteractionService`, `AssistService`, and `defaultAssistantGranted` | M11, §7.2 |
| 8 | `VOICE_COMMAND` is a broadcast delivered to the default assistant (`onHandleVoiceAction`) | AOSP `HeadsetSystemInterface.activateVoiceRecognition()` calls `startActivity(ACTION_VOICE_COMMAND)` (checked). A normal activity intent filter receives it; no assistant role is needed. `onHandleVoiceAction` isn't an API | M11.2, M11.4 |
| 9 | `android:showOnLockScreen` | `android:showWhenLocked` and `android:turnScreenOn` (API 27+; window flags on 26) | §8.2 |
| 10 | Battery exemption through `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` while not declaring its permission | That action requires `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (restricted on Play). Now: the CompanionDeviceManager allowlist check, plus `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (no permission) | M10.7 |
| 11 | `startBluetoothSco()` / `setBluetoothScoOn()` | Deprecated in API 31. Now `setCommunicationDevice()` on 31+, legacy below, with `MODIFY_AUDIO_SETTINGS` added | M10.5, §8 |
| 12 | `TelephonyCallback`/`PhoneStateListener` call-state detection with no permission | On API 31+ call-state callbacks need `READ_PHONE_STATE`. Now: `AudioManager` mode listener plus a recording-silenced callback on 31+; `PhoneStateListener` on 26–30 | M10.8 |
| 13 | `EncryptedSharedPreferences` (`security-crypto`) for keys | Deprecated in 2025 (androidx release notes, checked). Now Keystore AES-GCM in a backup-excluded DataStore file | §7.8 |
| 14 | No `<queries>` | Android 11+ needs `<queries>` for `isRecognitionAvailable()`, TTS engines, and resolving `VOICE_COMMAND` | §8.2 |
| 15 | `INTERNET` not listed | Added | §8 |
| 16 | `ACCESS_FINE_LOCATION` for weather | Coarse is enough; navigation doesn't use BobCar's location | §8, M9.2 |
| 17 | `uses-permission-sdk-23` note on `BLUETOOTH_CONNECT` | Removed (meaningless) | — |
| 18 | `goAsync()` "unnecessary" in the receiver | Required, because the Room lookup is asynchronous | M1.4 |
| 19 | Android Auto detection through `UsageStatsManager` (`PACKAGE_USAGE_STATS`) | `androidx.car.app` `CarConnection` (detection only, no `CarAppService`) | M1.6 |
| 20 | Hotword falls back to the phone mic after leaving the car, "seamless for a user walking into the house" | The hotword only ever runs during car mode (a privacy fix) | M10.1, M10.4 |
| 21 | Play Console foreground-service declarations not mentioned | Added (`connectedDevice` in V1, `microphone` in 1.1) | §8.2, Phases 4 and 5d |
| 22 | `cancelDiscovery()` before RFCOMM connect | Removed: it needs `BLUETOOTH_SCAN` and the app never starts discovery | M14.3 |

### A.2 Vehicle and OBD corrections

| # | Previous claim | Correction |
|---|---|---|
| 1 | The 2009 Taurus uses SAE J1850 PWM (stated in about 13 places) | US law required ISO 15765-4 CAN from MY2008. Expect `ISO 15765-4 (CAN 11/500)`. The 1 Hz polling cap and "mode 22 may not work on J1850" caveats were removed (M14.4, M15.4) |
| 2 | Mode 03: "the first byte after 43 is the code count" (always) | True on CAN only. Legacy protocols pad 3 codes per frame with no count (M14.5) |
| 3 | `ATST64` ≈ 25.6 s | `ATST` units are 4 ms: `ATST32` ≈ 200 ms, `ATST64` ≈ 400 ms, `ATST96` ≈ 600 ms (M14.4) |
| 4 | Mode-22 example length byte `06` | `05` (M15.4) |
| 5 | Ford MS-CAN "on different pins or behind the radio harness" | MS-CAN is on OBD pins 3/11; a basic ELM327 only wires 6/14 (M15.4, Part 5) |
| 6 | Four different ELM327 init sequences; `ATDP` vs `ATDPN` | One canonical sequence including both (M14.4) |
| 7 | Live-data rate "1–2 Hz" vs "≥ 2 Hz each" | ≥ 2 Hz each for 3 must-have PIDs on CAN; about 1 Hz on legacy buses (M14.7) |
| 8 | DTC table "compiled from SAE J2012" | Build it from an openly licensed list (J2012 is a paid standard) (M14.6) |

### A.3 AI corrections

| # | Previous | Correction |
|---|---|---|
| 1 | `AiBrain` sample: no parser pre-routing, no Nano tier, fail-soft line discarded, `parser.execute()` | Rewritten (M12.6). Commands go to the parser first, Nano is tried first when available, and the fail-soft line plus help is spoken |
| 2 | "Anthropic has no list-models endpoint" | `GET /v1/models` exists. The model picker works like the other providers (M12.4) |
| 3 | 429 backoff 1/2/4 s with 3 retries, plus an 8 s connect timeout, inside a "6 s latency budget" | These couldn't all hold. Now a 3 s connect timeout, 6 s overall, one retry after 1 s (M12.3, M12.6) |
| 4 | Validate OpenRouter keys through `/models` | That endpoint is public. Use the authenticated key-info endpoint (M12.4) |
| 5 | Long Gemini Nano supported-device list | Removed (it churns). Gate on `checkFeatureStatus()` only (M12.8) |

### A.4 Contradictions resolved

| Topic | Versions found | Chosen |
|---|---|---|
| Is AI in V1? | "Not in V1 / zero API keys / no cloud LLM" vs Phase 3.5 = V1 | Release 1.1, optional and off by default, ships with no keys (decided 2026-10-03; §2, §12) |
| Hotword engine and phrase | PocketSphinx "hey car" vs openWakeWord "Hey Bob" | openWakeWord, "Hey Bob" default (M10) |
| Hotword default on the Taurus | "Default off" vs "Taurus on" | Off everywhere until the user enables it (M10.9) |
| `VehicleProfile` | Four schemas (different primary keys, table names, nullability) | One schema (§7.2) |
| Status values | green/amber/red/gray, `PASS`/`WARN`/`FAIL`, `pass`/`warn`/`fail` | `CheckStatus { PASS, WARN, FAIL, NOT_TESTED }` mapped to green/amber/red/gray (§7.3) |
| Probe keys | Three different sets, plus Booleans stored in a `CheckResult` map | One key table (§7.3) |
| What runs on connect | Full parallel probes incl. RFCOMM vs cache read vs subset | Quick probe: `voice` and `auto_launch` plus cached results (M3.2) |
| OBD in V1 | "No connection" vs "connect if paired" vs "unreachable code" | No Bluetooth code in V1; bonded-name scan only (§6.5) |
| Car-mic test | 3× "Hey Bob" (15 s) vs RMS (10 s) vs multi-drive drop count | One test: 10 s SCO, RMS, optional wake test; plus the 3-strike rule (M10.5, M10.6) |
| Launch timing | ~5 s vs ≤30 s/target 10 s; immediate vs 8 s debounce | 5 s from the ACL broadcast, ≤30 s from ignition, 8 s disconnect debounce (§1, M1) |
| VIN | "Manual only, never auto-read" vs read through mode 09 | V1 manual; Phase 6 reads and saves after confirmation (§7.2, M14.5) |
| `MakePlugin` | Two incompatible interfaces | One (M15.3); replaced by OBDb signal sets in revision 5 |
| Parser | "stop" matched vs not; "go to" alias only in one doc; `contains("go home")` matching "go home screen" | One grammar table with must-not-match tests (M2.5) |
| DataStore keys | `weather_zip` vs `weather_zip_fallback`; `disable_when_android_auto` vs `suppress_when_android_auto` | One key table (§7.7) |
| OBD storage | Room vs "DataStore record" | Room only (§7, M14.9, M15.6) |
| UI shell | Single-activity `NavHost` vs Settings/FirstRun/BluetoothPicker activities | `MainActivity` (`NavHost`) + `CarModeActivity` + `VoiceCommandActivity` (§6.2, M4.2) |
| Coordinator | `CarModeViewModel` "scoped to the service connection" vs `VoiceController` | `VoiceController` in the process-wide `CarSession` (M2.2) |
| Package layout | `capability/` vs `car/` | §6.2 |
| Night hours | 7pm–7am vs 20:00–06:00 | 20:00–06:00 (M4.4) |
| Sensitivity | Slider "low/med/high → 0.3/0.5/0.7" while "higher = fewer triggers" | Field renamed `hotwordThreshold`: Low = 0.7, Medium = 0.5, High = 0.3 (§7.2, M10.9) |
| Usage counter | Room vs unspecified | DataStore key per month (§7.7) |

### A.5 Decisions made on the owner's behalf (reversible)

1. **Car mic is experimental and opt-in; the phone mic is the default for the hotword.** Reason: SCO suspends car-speaker music.
2. **Auto-launch asks for "Display over other apps"**, with a notification fallback. The owner confirmed this on 2026-10-03 (§4).
3. **V1 contains no OBD Bluetooth code** (§6.5).
4. **Interactive tests (wheel button, car mic) never run as part of Re-check.** Each has its own button.
5. **Spoken call confirmation is "Calling Mom. Tap call."**, so the driver knows a tap is needed.
6. **Weather example phrase** changed from "sunny" to "clear", to match the WMO table.
7. **The wheel-button handler forwards presses outside car mode** to the user's previous assistant, so earbuds keep working.

### A.6 Removed

- The original Build Plan §5 phases, which were superseded by the production plan.
- Build Plan §9 "Phase 2 LLM brain", superseded by M12.
- The doc map pointing to `~/workspace/your_files/…`.
- Duplicate "Known limitations" lists (merged into §3).
- The answered open question ("hotword in V1?").
- Pinned library versions (2024-era).
- `RECEIVE_BOOT_COMPLETED`, the boot receiver, `PACKAGE_USAGE_STATS`, the `VoiceInteractionService` design, and `defaultAssistantGranted`.

### A.7 Kept but flagged "verify at build time"

- AI provider free tiers and prices.
- The AI Studio prepay rumor.
- "Sign in with ChatGPT" availability.
- The ML Kit GenAI artifact name.
- Porcupine terms.
- The kotlin-obd-api version and license.
- The openWakeWord feature-model licenses.
- The OpenRouter key-info endpoint path.
- The Open-Meteo commercial-use terms.
- The DTC description source license.
- Ford PID `221674` validity on the 6F50 transaxle.
- Whether CompanionDeviceManager lists an already-connected SYNC head unit.

### A.8 Revision 3 (2026-10-03): scope change by the owner

Weather, the "Hey Bob" hotword, and "Choose your AI" moved from V1 to **release 1.1**.

**Phase renumbering:**

| Previous phase | Now |
|---|---|
| 3 (hotword) | 5b |
| 3.5 (AI) | 5c |
| 4 (car UI) | 3 |
| 5 (release) | 4 |
| Weather (from Phase 2) | 5a |
| — | 5d, the 1.1 Play update |

The OBD phases keep their numbers (6, 7).

**Consequences for V1:**
- No `INTERNET`, no location permission, no microphone foreground service, no OkHttp, no `play-services-location`, no ONNX Runtime, and no secrets file.
- The Play declarations shrink to `connectedDevice` and `READ_CONTACTS`.

**Kept in V1 so 1.1 needs no migration:**
- The hotword fields in `VehicleProfile`.
- The `Weather` grammar rule, which gives an honest "coming in a later update" reply.
- The `CarSession.hotword` no-op hook.
- `ExternalLauncher`'s notification path.

**Help text:** the V1 help text drops weather; 1.1 adds it back.

**§4 decisions renumbered.** The V1-scope decision was removed as decided, and "build order after V1" was added.

**New in 1.1:**
- A "What's new" card (`whats_new_seen`) offers weather location, "Hey Bob", and AI setup to existing users.
- Location is asked once, on the first weather request (changed in revision 4; see A.9).

### A.9 Revision 4 (2026-10-03): open decisions settled by the owner

- **"Go home":** sends Google Maps `google.navigation:q=Home`, so Maps uses its own saved Home.
  - Removed: the `home_address` key, the first-run home-address step, and the settings field.
  - The in-car test must confirm that Maps resolves the saved Home (M8.2).
- **Weather (1.1):**
  - The first request asks for approximate location. If the user says no, BobCar asks for a ZIP by voice and geocodes it. Neither prompt repeats automatically.
  - Added the `weather_location_asked` key. This replaces revision 3's "never ask in car mode" rule.
  - Both prompts, and the settings row, say the data is used only for weather and sent only to the weather service.
- **Permission disclosure rule (§8.3),** extended to every permission at the owner's request:
  - Each request states its purpose and every use, before the system dialog.
  - The same text lives in Settings → Permissions, all from `PermissionCopy.kt`.
  - New uses must update the text, the privacy policy, and the Data safety form together.
- **Weather from the phone's weather app:** not possible. Android apps can't read another app's weather data. Opening the app on screen gives no spoken answer.
- **§4 rewritten:**
  - Decided: display-over-apps (ask the user), "go home", and weather location.
  - Due later: the final `applicationId` (Phase 4), build order after V1, and adapter choice (see Part 5).

### A.10 Revision 5 (2026-10-03): any car, with or without an adapter

Owner's direction: BobCar is for anyone, in any car that can support it, with or without an adapter, and it isn't Ford-specific.

**Test vehicles:**
- Added the owner's 2004 Ford F-350: no factory Bluetooth, expected J1850 PWM (verify).
- Together with the Taurus (Bluetooth, CAN), it covers both protocol families and the no-Bluetooth path.

**Cars without Bluetooth:**
- V1: first run offers "My car has no Bluetooth" (`btMac` is now nullable), and car mode opens from the launcher (M1.10).
- 1.1 (Phase 5e): driving detection posts a "Driving? Tap for car mode" notification and never launches by itself. It adds `ACTIVITY_RECOGNITION`.

**Calls-only Bluetooth cars:**
- V1: a new `audio_route` probe, with a phone-speaker fallback.
- 1.1: optional speech through the car's call channel (`speakOverCallChannel`).

**Make guess:**
- `MakeGuesser` suggests a make from the Bluetooth name (and later the VIN). The user always confirms or changes it.
- New `makeConfirmed` field. Nothing make-dependent runs before confirmation.

**OBD (Phase 6):**
- Four adapter transports: classic Bluetooth, BLE (picked through CompanionDeviceManager, so no scan or location permission), Wi-Fi, and USB through an OTG cable with `usb-serial-for-android`.
- New `obdTransport` field; `obdAdapterMac` was renamed to `obdAdapterAddress`.
- The car's own USB ports can't be used; they only charge, play media, or run Android Auto.

**Clearing codes:** moved to the wishlist and not planned (misuse risk). There is no mode 04 code path, and a unit test asserts that.

**VIN decoding:**
- NHTSA vPIC, which is free with no key. The internet comes from 1.1, or Phase 6 adds it.
- The result is offered for confirmation, and an undecoded VIN is kept and decoded the next time the phone is online.

**Extended data (Phase 7):**
- The hand-built Ford plugin is replaced by OBDb community signal sets for any make (CC BY-SA 4.0, with credit on the About screen).
- `supportedExtendedPids` became `supportedSignals`.

**Known limits added:** OBD coverage by vehicle type, the car's own USB ports, and Android Auto's app categories.

**§8.3 texts added:** physical activity, USB device, Wi-Fi adapter, and VIN lookup.

### A.11 Revision 6 (2026-10-03): VIN entry and scanning in 1.1

- **New Phase 5f and spec M13.6:** type the VIN or scan its barcode, then decode it with NHTSA vPIC and confirm. The Bluetooth-name guess becomes the fallback.
- **Scanning uses the Google code scanner,** which runs in Google Play services, so there's no camera permission. It reads Code 39, Code 128, Data Matrix, and QR. A leading "I" is dropped from 18-character import labels. Scan is hidden without Play services.
- **Validation:** 17 characters, no I, O, or Q, and the North American check digit. A failed check is a warning that needs a second confirmation, not a block.
- **VIN decoding moved from Phase 6 to 1.1.** Phase 6 reuses `VinDecoder` for VINs read from the car.
- **Disclosure and declarations:** added §8.3 texts for the VIN lookup and the scan, plus the Data safety entry in Phase 5d.

### A.12 Revision 7 (2026-10-03): pre-build review fixes

**Owner decision: supported vehicles are 1996 and newer, as a soft limit.**
- `SupportPolicy` decides from the model year and the phone's country. There's no user-facing "OBD-II" anywhere.
- Older vehicles see "This vehicle isn't supported" but can continue.
- Diagnostics rows show `FAIL` for pre-1996 vehicles.
- See M13.1.

**Errors fixed:**
1. **Cars without Bluetooth no longer start the `connectedDevice` service,** which would have crashed or broken Play's rules for that service type. Car mode runs with the screen alone (M1.10, M1.4).
2. **`audio_route` waits for the A2DP connection** (up to 15 s, then the whole drive) before reporting "calls only" (M3.3).
3. **`CallStateWatcher` moved into V1** so TTS stays silent during calls; the hotword reuses it (M2.4, M10.8).
4. **Audio focus added:** music ducks while BobCar speaks and pauses while it listens (M2.2, M2.4).
5. **The store listing no longer names carmakers or head units** (Phase 4).
6. **Data safety treatment of system speech recognition** is flagged as "verify at build time" (Phase 4).

**Contradictions fixed:**
- The wheel button is now a per-vehicle `wheelButtonEnabled` field, replacing the global DataStore key.
- Statuses for cars without Bluetooth use `FAIL` (not supported by this car), not amber or gray.
- First run asks for location on the first weather request, not from Settings.
- Probes never write the VIN.
- A car-screen **More** sheet now exists for Diagnostics and "Send to an AI app".
- The quick probe runs on every car-mode entry.
- "Use this adapter" records the transport.
- Stale header, diagram, tech-stack, and manifest entries were updated (1.1 activity permission and receiver; Phase 6 manifest block).

**Gaps filled:**
- **M14.13:** the diagnostics screen and voice, with a speed-based driving rule.
- **Offline VIN decoding:** a `vinDecodePending` flag, retried when `MainActivity` opens or car mode exits.
- **Debug screen, tools, and logging policy:** §6.6.
- **First run** asks "Does your car have Bluetooth?" before the Bluetooth permission, and asks the year for the support check.
- **List converter** for `supportedSignals`.
- **Package layout:** now includes `PermissionCopy`, `Notifications`, `CallStateWatcher`, `AudioFocus`, and `SupportPolicy`.
- **Manual car-mode end rule** simplified: Quit, back, or the 1.1 driving exit.

**Missing items added:**
- **Developing without a car:** fake connect, speech, and call, plus a PC ELM327 emulator over Wi-Fi (§6.6).
- **Release engineering:** upload key and Play App Signing, AAB builds, `versionCode` rules, R8 from Phase 0, and a name and trademark check (Phases 0 and 4).
- **Test tooling** chosen (§5).
- **Locked phone:** a trusted-device tip in first run, plus a known limit.
- **Grammar:** `dial`/`phone` aliases, spoken phone numbers, `message`/`send a text to` aliases with "saying", `cancel`/`never mind`, and wake-phrase stripping in 1.1 (M2.5, M5, M6).
- **US-centric limit** stated, with a country choice in Play Console.
- **12 V Bluetooth adapter path** for cars without Bluetooth (M1.10, §3).
- **Phase 6 split:** 6a (one real adapter plus the PC emulator) and 6b (the remaining transports).
- **Plain-language rule** for user-facing text (M4.8).

### A.13 Revision 8 (2026-10-03): data-sharing rule

**Owner rule:** user data is never sent to a third party unless it has to be. Anything that is sent gets a disclosure and a link to where the user can request removal.

- **New §8.4:** the rule itself, a third-party register (`ThirdPartyRegister.kt`), a Privacy & data screen, **Delete all BobCar data on this phone**, a ban on analytics, crash, ad, and tracking SDKs, and no cloud backup (`allowBackup="false"`, with device-to-device transfer kept).
- **Speech:** the on-device recognizer comes first (`createOnDeviceSpeechRecognizer`, API 31+). Google's cloud speech is used only after an explicit, disclosed choice (`cloud_speech_allowed`). TTS uses local voices only.
- **VIN lookup:** NHTSA receives a masked VIN with no check digit and no serial number. vPIC's documented partial-VIN support with `*` makes this possible.
- **VIN scanning:** the Google code scanner was replaced with on-device ZXing. ML Kit sends usage metrics to Google, which the rule forbids. ZXing needs the camera permission, asked only on **Scan**, which reverses revision 6's "no camera permission".
- **Weather:** coordinates are rounded to 0.1° before sending.
- **OBDb:** the data is bundled in the app instead of downloaded per vehicle, so no third party learns which car the user drives.
- **AI consent screens:** each one now links to the provider's removal page.
- **Disclosures:** §8.3 gained the cloud-speech and camera texts and a rewritten VIN text. The privacy policy and Data safety form must mirror §8.4.

### A.14 Revision 9 (2026-10-26): Android Auto choice and iPhone plan

**Owner decisions:** in an Android Auto car, the driver picks Android Auto or BobCar. BobCar will also launch on iPhone.

- **Android Auto (M1.6):**
  - The global `suppress_when_android_auto` setting is replaced by two per-car fields, `projectionChoice` (`"ask"`, `"android_auto"`, or `"bobcar"`) and `projectionAsked`.
  - The first projection in a car posts a two-button question. Three unanswered drives save `"android_auto"`.
  - Android Auto often starts after Bluetooth connects, so the choice applies both at connect and during car mode.
  - Updated to match: §3, §6.3, §7.2, §7.7, M1.2, M1.4, M1.8 test 7, M10, M11.3, M13.1, M13.2, Phase 1, and Phase 5b.
- **iPhone (new Part 6):** a feature-by-feature plan, CarPlay, permissions, and Phase 8. The main iPhone limits:
  - Opening by itself needs a one-time Shortcuts automation (iOS 17+).
  - No steering-wheel button; Siri phrases instead.
  - Music control covers Apple Music and the music library only.
  - "Hey Bob" works only while BobCar is on screen.
  - Diagnostics work over BLE or Wi-Fi only.
  - Building needs a Mac.
- **CarPlay:** the same per-car choice. BobCar on the CarPlay screen is optional. It needs Apple's voice-app entitlement (iOS 26.4+), and such apps can't control calls, texts, music, or navigation.
- **Phase 0:** shared logic goes in a pure-Kotlin `:core` module, so the iPhone app can reuse it through Kotlin Multiplatform.
- **F-350:** noted as a heavy-duty pickup that may answer only part of the standard diagnostics.
- **Part 5:** for iPhone, buy a BLE or Wi-Fi adapter.
- **Not changed:** the pitch diagram shows every feature as V1, but this build plan keeps its phases. Clearing codes stays on the wishlist.

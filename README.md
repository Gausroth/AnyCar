# AnyCar

**The universal vehicle assistant.** AnyCar turns an Android phone into a voice-first companion for any car from 1996 on, with or without Bluetooth, and with or without an OBD-II adapter.

Built for the [Meta Global AI Developer Hackathon](https://cerebralvalley.ai/e/meta-ai-developer-hackathon) (Indie Developer track, *Build for Your World*).

## Built during the hackathon

| | |
|---|---|
| **Pre-existing** | A written design document and a one-page overview for the app (working name "BobCar"), both in [docs/pre-hackathon](docs/pre-hackathon/). **No code existed before the hackathon.** |
| **Built Oct 26 – Nov 4, 2026** | Everything in this repository: all code, the Meta model integration, and the assets. |

The commit history shows when each piece was built.

## What it does

- **Opens on its own.** When the phone connects to the car's Bluetooth, the car screen opens by itself. Cars without Bluetooth open it from the launcher.
- **Voice commands.** Tap to talk for calls, texts, music, navigation, and the time. Calls and texts always need a tap to confirm.
- **Meta-powered assistant.** Meta models understand chained requests in one sentence and can identify dashboard warning lights from a photo taken while parked.
- **Big-button car UI.** Usable at arm's length, with day and night themes and no typing while driving.

## Meta models

| Model | Use in AnyCar |
|---|---|
| Muse Spark | Agent that turns a spoken request into app actions; dashboard photo understanding |
| Muse Voice Transcribe | Speech recognition in road noise |
| Muse Glimmer / Muse Spark Open | Open-weight model, on the phone or through partner inference |

## Tech stack

Kotlin, Jetpack Compose (Material 3), coroutines and Flow, Room, and DataStore. `minSdk 26`.

## Status

In progress. Built so far:

- Car profiles, first-run setup, and Bluetooth auto-launch with an 8-second disconnect grace period.
- Tap-to-talk with on-device speech recognition and spoken replies.
- Commands: call, text, navigate, go home, play/pause/next/previous, time, help, and cancel. Several commands in one sentence ("text Mom I'm late and navigate to Walmart") are split on the phone.
- Meta AI (off until you turn it on in the app): Muse Spark handles anything the basic commands don't understand, scans dashboard warning lights from a photo, and Muse Voice Transcribe can replace the phone's speech recognition.

To build with Meta AI, add `META_API_KEY=<your key>` to `local.properties`. The key is compiled into the APK, so only sideload these builds.

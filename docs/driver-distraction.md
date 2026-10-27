# Driver distraction

How AnyCar keeps the driver's eyes on the road. This is the text for the Play listing's safety notes, and the rules the car screen is built to.

## The car screen

- **Voice first.** Every command works by voice. Replies are spoken and shown in large text, so a glance is enough.
- **Big targets.** Buttons are at least 72 dp tall, the main ones 110–150 dp. Text is at least 24 sp, and the status line 34 sp. The system font size is honored up to 1.3×.
- **No typing.** Car mode has no text fields. An automated test checks this.
- **Nothing to read or watch.** No video, no web pages, no scrolling feeds, and no message reading.
- **Day and night.** A white day theme for sunlight and a near-black night theme with amber text for dark cabins. It switches automatically by the clock, or by hand.
- **Stays out of the way.** When Maps, the dialer, or messaging opens, AnyCar steps back and doesn't fight for the screen.

## Every action needs the driver's tap

- **Calls** open the dialer with the number filled in. The driver taps Call. AnyCar never places a call.
- **Texts** open the messaging app with the message filled in. The driver taps Send. AnyCar never sends a message. Replies speak the name only, never the message.
- **Contacts:** when several match, AnyCar shows up to three buttons and never picks one by itself.

## Parked-only features

- **Scan a warning light** uses the camera. It's for when the car is parked, and it lives under More, not on the main screen.
- **Setup and settings** are outside car mode. They're the only screens with text fields.

## Audio

- Music gets quieter while AnyCar talks and pauses while it listens.
- With **Car speakers** on, AnyCar's voice plays over the car's call channel so the car speakers play it whatever source is on. AnyCar never uses that channel during a call.

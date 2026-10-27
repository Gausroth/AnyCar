package dev.timothy.anycar.ui

/** The only source of permission and link texts (§8.3), so prompts and settings always match. */
object PermissionCopy {
    const val MICROPHONE = "Microphone: to hear your voice commands when you tap Talk. AnyCar uses your phone's " +
        "on-device speech recognition when it's available, and doesn't record or keep audio."
    const val NEARBY_DEVICES = "Nearby devices: to recognize your car's Bluetooth so car mode can open when you " +
        "connect, and to check whether your car can play AnyCar's voice. AnyCar only reads devices already paired to your phone."
    const val NOTIFICATIONS = "Notifications: to show that car mode is running, and to let you open it with one " +
        "tap when your car connects."
    const val CLOUD_SPEECH = "This phone can't recognize speech on its own, so your voice commands would be sent " +
        "to Google's speech service to be turned into text."
    const val OVERLAY = "Display over other apps: only so car mode can open by itself when your car connects. " +
        "AnyCar doesn't draw over other apps."
    const val CONTACTS = "Contacts: to find the number when you say \"call Mom\" or \"text Mom\". Names are looked " +
        "up on this phone and are never sent anywhere."
    const val META_AI = "Meta AI (Muse Spark): when you say something AnyCar's basic commands don't understand, " +
        "the words you said are sent to Meta to work out what you meant. When you scan a warning light, the photo " +
        "and your car's make, model, and year are sent. Contacts and location are never sent."
    const val META_SPEECH = "Meta speech (Muse Voice Transcribe): the audio of each command is sent to Meta to be " +
        "turned into text, instead of using your phone's own speech recognition. AnyCar doesn't keep the audio."
    const val META_PRIVACY_URL = "https://www.facebook.com/privacy/policy/"
    const val LINK_CAR ="Link your car: lets AnyCar start car mode in the background when this car connects. " +
        "Used for nothing else."
}

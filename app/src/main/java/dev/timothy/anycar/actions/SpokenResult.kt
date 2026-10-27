package dev.timothy.anycar.actions

/** What a command says and shows (M2.6). TTS and the status line share [text] unless [display] adds detail. */
data class SpokenResult(
    val text: String,
    val display: String = text,
    val choices: List<UiChoice> = emptyList(),
)

/**
 * A big button on the car screen, such as one contact of several. Tapping it runs [run].
 * With [needsContactsPermission], the screen asks for READ_CONTACTS first and runs [run] only if it's granted.
 */
data class UiChoice(
    val label: String,
    val needsContactsPermission: Boolean = false,
    val run: suspend () -> SpokenResult,
)

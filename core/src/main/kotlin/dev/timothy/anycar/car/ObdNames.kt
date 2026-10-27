package dev.timothy.anycar.car

/** Bonded-device names that look like a diagnostics adapter (M3.3 `obd_link`). Matching never opens a connection. */
object ObdNames {
    private val PATTERN = Regex("OBD|ELM|V-?LINK|VEEPEAK|BAFX|VGATE|OBDLINK", RegexOption.IGNORE_CASE)

    fun looksLikeAdapter(name: String?): Boolean = name != null && PATTERN.containsMatchIn(name)
}

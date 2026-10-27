package dev.timothy.anycar.car

/** The car screen's day/night rule (M4.4): a plain clock rule, so it works offline and needs no location. */
object DayNight {
    /** [pref] is "auto", "day", or "night". [hour] is the local hour, 0–23. */
    fun isNight(pref: String, hour: Int): Boolean = when (pref) {
        "day" -> false
        "night" -> true
        else -> hour >= 20 || hour < 6
    }

    /** The next manual choice for the car screen's toggle: auto → the opposite of now → the other → auto. */
    fun next(pref: String, hour: Int): String = when (pref) {
        "auto" -> if (isNight("auto", hour)) "day" else "night"
        "day" -> "night"
        else -> "auto"
    }
}

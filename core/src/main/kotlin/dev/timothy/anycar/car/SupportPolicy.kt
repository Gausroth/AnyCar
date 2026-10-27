package dev.timothy.anycar.car

/** How well a vehicle is likely to be supported, derived from its model year and the phone's country (M13.1). */
enum class SupportStatus { SUPPORTED, MAY_NOT_BE_FULLY_SUPPORTED, NOT_SUPPORTED, UNKNOWN }

/**
 * The soft 1996+ support rule. The phone can't sense the diagnostics port, so support is decided from the year.
 * Never blocking: voice car mode works for every vehicle whatever this returns.
 */
object SupportPolicy {
    const val US_FIRST_YEAR = 1996
    const val ELSEWHERE_FULL_YEAR = 2004

    /** [year] 0 means unknown. [countryCode] is an ISO 3166 alpha-2 code such as "US"; pass it in from the platform. */
    fun status(year: Int, countryCode: String): SupportStatus = when {
        year == 0 -> SupportStatus.UNKNOWN
        year < US_FIRST_YEAR -> SupportStatus.NOT_SUPPORTED
        countryCode.equals("US", ignoreCase = true) -> SupportStatus.SUPPORTED
        year < ELSEWHERE_FULL_YEAR -> SupportStatus.MAY_NOT_BE_FULLY_SUPPORTED
        else -> SupportStatus.SUPPORTED
    }
}

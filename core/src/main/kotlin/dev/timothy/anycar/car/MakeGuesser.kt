package dev.timothy.anycar.car

/**
 * Suggests a make from a car's Bluetooth name (M13.1). It only suggests: the user always confirms or changes it,
 * and nothing make-dependent runs until they do.
 */
object MakeGuesser {
    private val makes = listOf(
        "Acura", "Audi", "BMW", "Buick", "Cadillac", "Chevrolet", "Chrysler", "Dodge", "Fiat", "Ford", "GMC",
        "Honda", "Hyundai", "Infiniti", "Jaguar", "Jeep", "Kia", "Land Rover", "Lexus", "Lincoln", "Mazda",
        "Mercedes", "Mini", "Mitsubishi", "Nissan", "Porsche", "Ram", "Subaru", "Tesla", "Toyota", "Volkswagen",
        "Volvo",
    )

    // Head-unit names map to a make. Add one only after seeing it on a real car.
    private val headUnits = mapOf("SYNC" to "Ford")

    /** Returns the suggested make, or "" when nothing matches. */
    fun guess(btName: String): String {
        headUnits.entries.firstOrNull { (unit, _) -> btName.contains(unit, ignoreCase = true) }?.let { return it.value }
        // Whole-word match, so "Ram" doesn't fire on "Ramona's phone".
        return makes.firstOrNull { make ->
            Regex("\\b${Regex.escape(make)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(btName)
        } ?: ""
    }
}

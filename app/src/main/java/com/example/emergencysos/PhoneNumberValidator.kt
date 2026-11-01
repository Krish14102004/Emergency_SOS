package com.example.emergencysos

object PhoneNumberValidator {
    private val allowedFormat = Regex("^\\+?[1-9][0-9]{6,14}$")

    fun normalize(input: String): String? {
        val compact = input.trim().replace(Regex("[\\s().-]"), "")
        val normalized = if (compact.startsWith("00")) "+${compact.drop(2)}" else compact
        return normalized.takeIf { allowedFormat.matches(it) }
    }

    fun normalizeAll(inputs: List<String>): Result<List<String>> {
        val numbers = inputs.map { it.trim() }.filter { it.isNotEmpty() }
        if (numbers.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter at least one phone number."))
        }
        if (numbers.size > MAX_CONTACTS) {
            return Result.failure(IllegalArgumentException("For this prototype, enter no more than $MAX_CONTACTS numbers."))
        }

        val normalized = numbers.map { input ->
            normalize(input) ?: return Result.failure(
                IllegalArgumentException("Check this phone number: $input. Include the country code, for example +14155550123.")
            )
        }
        if (normalized.distinct().size != normalized.size) {
            return Result.failure(IllegalArgumentException("Each emergency phone number must be unique."))
        }
        return Result.success(normalized)
    }

    private const val MAX_CONTACTS = 5
}

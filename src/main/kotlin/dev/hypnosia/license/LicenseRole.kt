package dev.hypnosia.license

enum class LicenseRole {
    USER,
    PREMIUM,
    BETA,
    ADMIN,
    OWNER;

    companion object {
        fun parse(value: String?): LicenseRole? {
            if (value.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.name == value.trim().uppercase() }
        }
    }
}

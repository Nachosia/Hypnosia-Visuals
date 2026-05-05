package dev.hypnosia.license

data class LicenseRole(val name: String) {
    override fun toString(): String = name

    companion object {
        val USER = LicenseRole("USER")
        val SPONSOR = LicenseRole("SPONSOR")
        val QA = LicenseRole("QA")
        val ADMIN = LicenseRole("ADMIN")
        val OWNER = LicenseRole("OWNER")

        private val roleRegex = Regex("^[A-Z][A-Z0-9_]{1,31}$")

        fun parse(value: String?): LicenseRole? {
            val normalized = value?.trim()?.uppercase().orEmpty()
            if (!roleRegex.matches(normalized)) return null
            return LicenseRole(normalized)
        }

        fun iconFor(role: LicenseRole): String {
            return when (role.name) {
                "OWNER" -> "role_owner.png"
                "ADMIN" -> "role_admin.png"
                "QA" -> "role_qa.png"
                "SPONSOR" -> "role_sponsor.png"
                "USER" -> "role_user.png"
                else -> "role_custom.png"
            }
        }
    }
}

package me.rhunk.snapenhance.common.scripting.type

data class ModuleInfo(
    val name: String,
    val version: String,
    val displayName: String? = null,
    val description: String? = null,
    val author: String? = null,
    val minSnapchatVersion: Long? = null,
    val minSEVersion: Long? = null,
    val grantedPermissions: List<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is ModuleInfo) return false
        if (other === this) return true
        return name == other.name &&
                version == other.version &&
                displayName == other.displayName &&
                description == other.description &&
                author == other.author
    }

    fun ensurePermissionGranted(permission: Permissions) {
        if (!grantedPermissions.contains(permission.key)) {
            throw AssertionError("Permission $permission is not granted")
        }
    }
}
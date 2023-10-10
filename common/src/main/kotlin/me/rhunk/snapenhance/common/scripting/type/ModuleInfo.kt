package me.rhunk.snapenhance.common.scripting.type

data class ModuleInfo(
    val name: String,
    val version: String,
    val description: String? = null,
    val author: String? = null,
    val minSnapchatVersion: Long? = null,
    val minSEVersion: Long? = null,
    val grantPermissions: List<String>? = null,
)
package me.rhunk.snapenhance.common.scripting.bindings

enum class BindingSide(
    val key: String
) {
    COMMON("common"),
    CORE("core"),
    MANAGER("manager");

    companion object {
        fun fromKey(key: String): BindingSide {
            return entries.firstOrNull { it.key == key } ?: COMMON
        }
    }
}
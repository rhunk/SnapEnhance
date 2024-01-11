package me.rhunk.snapenhance.mapper

import com.android.tools.smali.dexlib2.iface.ClassDef

class MapperContext(
    private val classMap: Map<String, ClassDef>
) {
    val classes: Collection<ClassDef>
        get() = classMap.values

    fun getClass(name: String?): ClassDef? {
        if (name == null) return null
        return classMap[name]
    }

    fun getClass(name: CharSequence?): ClassDef? {
        if (name == null) return null
        return classMap[name.toString()]
    }
}

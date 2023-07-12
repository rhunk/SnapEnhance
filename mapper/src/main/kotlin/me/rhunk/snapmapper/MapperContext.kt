package me.rhunk.snapmapper

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.jf.dexlib2.dexbacked.DexBackedClassDef

class MapperContext(
    private val classMap: Map<String, DexBackedClassDef>
) {
    val classes: Collection<DexBackedClassDef>
        get() = classMap.values

    fun getClass(name: String?): DexBackedClassDef? {
        if (name == null) return null
        return classMap[name]
    }

    private val mappings = mutableMapOf<String, Any>()

    fun addMapping(key: String, vararg array: Pair<String, Any>) {
        mappings[key] = array.toMap()
    }

    fun addMapping(key: String, value: String) {
        mappings[key] = value
    }

    fun getStringMapping(key: String): String? {
        return mappings[key] as? String
    }

    fun getMapMapping(key: String): Map<*, *>? {
        return mappings[key] as? Map<*, *>
    }

    fun exportToJson(): JsonObject {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = JsonObject()
        for ((key, value) in mappings) {
            when (value) {
                is String -> json.addProperty(key, value)
                is Map<*, *> -> {
                    val obj = JsonObject()
                    for ((k, v) in value) {
                        obj.add(k.toString(), gson.toJsonTree(v))
                    }
                    json.add(key, obj)
                }
            }
        }
        return json
    }
}
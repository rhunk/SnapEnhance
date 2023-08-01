package me.rhunk.snapenhance.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

object DataProcessors {
    enum class Type {
        STRING,
        BOOLEAN,
        INTEGER,
        FLOAT,
        STRING_MULTIPLE_SELECTION,
        STRING_UNIQUE_SELECTION,
        CONTAINER,
    }

    data class PropertyDataProcessor<T>
    internal constructor(
        val type: Type,
        private val serialize: (T) -> JsonElement,
        private val deserialize: (JsonElement) -> T
    ) {
        @Suppress("UNCHECKED_CAST")
        fun serializeAny(value: Any) = serialize(value as T)
        fun deserializeAny(value: JsonElement) = deserialize(value)
    }

    val STRING = PropertyDataProcessor(
        type = Type.STRING,
        serialize = {
            if (it != null) JsonPrimitive(it)
            else JsonNull.INSTANCE
        },
        deserialize = {
            if (it.isJsonNull) null
            else it.asString
        },
    )

    val BOOLEAN = PropertyDataProcessor(
        type = Type.BOOLEAN,
        serialize = {
            if (it) JsonPrimitive(true)
            else JsonPrimitive(false)
        },
        deserialize = { it.asBoolean },
    )

    val INTEGER = PropertyDataProcessor(
        type = Type.INTEGER,
        serialize = { JsonPrimitive(it) },
        deserialize = { it.asInt },
    )

    val FLOAT = PropertyDataProcessor(
        type = Type.FLOAT,
        serialize = { JsonPrimitive(it) },
        deserialize = { it.asFloat },
    )

    val STRING_MULTIPLE_SELECTION = PropertyDataProcessor(
        type = Type.STRING_MULTIPLE_SELECTION,
        serialize = { JsonArray().apply { it.forEach { add(it) } } },
        deserialize = { obj ->
            obj.asJsonArray.map { it.asString }.toMutableList()
        },
    )

    val STRING_UNIQUE_SELECTION = PropertyDataProcessor(
        type = Type.STRING_UNIQUE_SELECTION,
        serialize = { JsonPrimitive(it) },
        deserialize = { obj -> obj.takeIf { !it.isJsonNull }?.asString }
    )

    fun <T : ConfigContainer> container(container: T) = PropertyDataProcessor(
        type = Type.CONTAINER,
        serialize = {
            JsonObject().apply {
                addProperty("state", it.globalState)
                add("properties", it.toJson())
            }
        },
        deserialize = { obj ->
            val jsonObject = obj.asJsonObject
            container.apply {
                globalState = jsonObject["state"].takeIf { !it.isJsonNull }?.asBoolean
                fromJson(jsonObject["properties"].asJsonObject)
            }
        },
    )
}
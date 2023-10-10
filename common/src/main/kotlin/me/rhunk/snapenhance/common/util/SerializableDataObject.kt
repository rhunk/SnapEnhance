package me.rhunk.snapenhance.common.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder

open class SerializableDataObject {
    companion object {
        val gson: Gson = GsonBuilder().create()

        inline fun <reified T : SerializableDataObject> fromJson(json: String): T {
            return gson.fromJson(json, T::class.java)
        }

        inline fun <reified T : SerializableDataObject> fromJson(json: String, type: Class<T>): T {
            return gson.fromJson(json, type)
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }
}
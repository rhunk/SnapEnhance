package me.rhunk.snapenhance.core.features.impl

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OpenParams
import me.rhunk.snapenhance.common.util.ktx.getBlobOrNull
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams

class COFOverride : Feature("Cof Override", loadParams = FeatureLoadParams.INIT_ASYNC) {
    override fun asyncInit() {
        val coreDatabaseFile = context.androidContext.getDatabasePath("core.db")
        if (!coreDatabaseFile.exists()) return
        SQLiteDatabase.openDatabase(coreDatabaseFile, OpenParams.Builder().apply {
            setOpenFlags(SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
        }.build()).use { db ->
            fun setProperty(configId: String, value: Any) {
                runCatching {
                    db.rawQuery("SELECT config_result FROM ConfigRule WHERE config_id = ?", arrayOf(configId)).use { cursor ->
                        if (!cursor.moveToFirst()) {
                            context.log.warn("Failed to find $configId in ConfigRule")
                            return
                        }
                        val configResult = cursor.getBlobOrNull("config_result")?.let {
                            ProtoEditor(it).apply {
                                edit(2) {
                                    clear()
                                    when (value) {
                                        is Int -> addVarInt(1, value)
                                        is Long -> addVarInt(2, value)
                                        is Float -> addFixed32(3, value)
                                        is Boolean -> addVarInt(4, if (value) 1 else 0)
                                        is String -> addString(5, value)
                                        is ByteArray -> addBuffer(6, value)
                                        is Double -> addFixed64(7, value.toLong())
                                        else -> return@edit
                                    }
                                }
                            }.toByteArray()
                        } ?: return
                        db.execSQL("UPDATE ConfigRule SET config_result = ? WHERE config_id = ?", arrayOf(configResult, configId))
                    }
                }
            }

            setProperty("ANDROID_ACTION_MENU_V2", context.config.experimental.newChatActionMenu.get())
        }
    }
}
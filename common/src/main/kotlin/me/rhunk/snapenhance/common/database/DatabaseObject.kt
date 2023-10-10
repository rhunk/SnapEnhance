package me.rhunk.snapenhance.common.database

import android.database.Cursor

interface DatabaseObject {
    fun write(cursor: Cursor)
}

package me.rhunk.snapenhance.core.database

import android.database.Cursor

interface DatabaseObject {
    fun write(cursor: Cursor)
}

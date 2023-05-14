package me.rhunk.snapenhance.database

import android.database.Cursor

interface DatabaseObject {
    fun write(cursor: Cursor)
}

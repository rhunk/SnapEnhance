package me.rhunk.snapenhance.common.util.ktx

import android.database.Cursor

fun Cursor.getStringOrNull(columnName: String): String? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex == -1) null else getString(columnIndex)
}

fun Cursor.getIntOrNull(columnName: String): Int? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex == -1) null else getInt(columnIndex)
}

fun Cursor.getInteger(columnName: String) = getIntOrNull(columnName) ?: throw NullPointerException("Column $columnName is null")
fun Cursor.getLong(columnName: String) = getLongOrNull(columnName) ?: throw NullPointerException("Column $columnName is null")

fun Cursor.getBlobOrNull(columnName: String): ByteArray? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex == -1) null else getBlob(columnIndex)
}


fun Cursor.getLongOrNull(columnName: String): Long? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex == -1) null else getLong(columnIndex)
}

fun Cursor.getDoubleOrNull(columnName: String): Double? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex == -1) null else getDouble(columnIndex)
}

fun Cursor.getFloatOrNull(columnName: String): Float? {
    val columnIndex = getColumnIndex(columnName)
    return if (columnIndex == -1) null else getFloat(columnIndex)
}
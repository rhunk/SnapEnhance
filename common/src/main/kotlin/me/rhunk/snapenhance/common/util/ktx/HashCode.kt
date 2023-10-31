package me.rhunk.snapenhance.common.util.ktx

fun String.longHashCode(): Long {
    var h = 1125899906842597L
    for (element in this) h = 31 * h + element.code.toLong()
    return h
}
package me.rhunk.snapenhance.mapper.ext

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef

fun ClassDef.isEnum(): Boolean = accessFlags and AccessFlags.ENUM.value != 0
fun ClassDef.isAbstract(): Boolean = accessFlags and AccessFlags.ABSTRACT.value != 0
fun ClassDef.isInterface(): Boolean = accessFlags and AccessFlags.INTERFACE.value != 0
fun ClassDef.isFinal(): Boolean = accessFlags and AccessFlags.FINAL.value != 0

fun ClassDef.hasStaticConstructorString(string: String): Boolean = methods.any {
    it.name == "<clinit>" && it.implementation?.findConstString(string) == true
}

fun ClassDef.hasConstructorString(string: String): Boolean = methods.any {
    it.name == "<init>" && it.implementation?.findConstString(string) == true
}

fun ClassDef.getStaticConstructor() = methods.firstOrNull {
    it.name == "<clinit>"
}

fun ClassDef.getClassName() = type.replaceFirst("L", "").replaceFirst(";", "")
fun ClassDef.getSuperClassName() = superclass?.replaceFirst("L", "")?.replaceFirst(";", "")

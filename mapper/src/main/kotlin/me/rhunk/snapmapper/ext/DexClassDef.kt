package me.rhunk.snapmapper.ext

import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.dexbacked.DexBackedClassDef

fun DexBackedClassDef.isEnum(): Boolean = accessFlags and AccessFlags.ENUM.value != 0
fun DexBackedClassDef.isAbstract(): Boolean = accessFlags and AccessFlags.ABSTRACT.value != 0
fun DexBackedClassDef.isFinal(): Boolean = accessFlags and AccessFlags.FINAL.value != 0

fun DexBackedClassDef.hasStaticConstructorString(string: String): Boolean = methods.any {
    it.name == "<clinit>" && it.implementation?.findConstString(string) == true
}

fun DexBackedClassDef.hasConstructorString(string: String): Boolean = methods.any {
    it.name == "<init>" && it.implementation?.findConstString(string) == true
}

fun DexBackedClassDef.getStaticConstructor() = methods.firstOrNull {
    it.name == "<clinit>"
}

fun DexBackedClassDef.getClassName() = type.replace("L", "").replace(";", "")
fun DexBackedClassDef.getSuperClassName() = superclass?.replace("L", "")?.replace(";", "")

package me.rhunk.snapmapper.ext

import org.jf.dexlib2.dexbacked.DexBackedMethodImplementation
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction21c
import org.jf.dexlib2.dexbacked.reference.DexBackedStringReference

fun DexBackedMethodImplementation.findConstString(string: String, contains: Boolean = false): Boolean = instructions.any {
    it is DexBackedInstruction21c && (it.reference as? DexBackedStringReference)?.string?.let { str ->
        if (contains) {
            str.contains(string)
        } else {
            str == string
        }
    } == true
}

fun DexBackedMethodImplementation.getAllConstStrings(): List<String> = instructions.filterIsInstance<DexBackedInstruction21c>().mapNotNull {
    it.reference as? DexBackedStringReference
}.map {
    it.string
}
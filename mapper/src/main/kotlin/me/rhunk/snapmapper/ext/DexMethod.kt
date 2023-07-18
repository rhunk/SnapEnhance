package me.rhunk.snapmapper.ext

import org.jf.dexlib2.dexbacked.DexBackedMethodImplementation
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction21c
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction22c
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference
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

fun DexBackedMethodImplementation.searchNextFieldReference(constString: String, contains: Boolean = false): DexBackedFieldReference? = this.instructions.let {
    var found = false
    for (instruction in it) {
        if (instruction is DexBackedInstruction21c && instruction.reference is DexBackedStringReference) {
            val str = (instruction.reference as DexBackedStringReference).string
            if (if (contains) str.contains(constString) else str == constString) {
                found = true
            }
        }

        if (!found) continue

        if (instruction is DexBackedInstruction22c &&
            instruction.reference is DexBackedFieldReference
        ) {
            return@let (instruction.reference as DexBackedFieldReference)
        }
    }
    null
}

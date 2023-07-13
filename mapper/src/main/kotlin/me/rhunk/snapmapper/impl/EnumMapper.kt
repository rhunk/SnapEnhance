package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.getClassName
import me.rhunk.snapmapper.ext.getStaticConstructor
import me.rhunk.snapmapper.ext.hasStaticConstructorString
import me.rhunk.snapmapper.ext.isEnum
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.dexbacked.DexBackedMethod
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction21c
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference
import org.jf.dexlib2.dexbacked.reference.DexBackedStringReference

class EnumMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        var enumQualityLevel : String? = null
        val enums = mutableListOf<Pair<String, String>>()

        for (enumClass in context.classes) {
            if (!enumClass.isEnum()) continue

            if (enumQualityLevel == null && enumClass.hasStaticConstructorString("LEVEL_MAX")) {
                enumQualityLevel = enumClass.getClassName()
            }

            if (enumClass.interfaces.isEmpty()) continue

            //check if it's a config enum
            val serializableInterfaceClass = context.getClass(enumClass.interfaces.first()) ?: continue
            if (serializableInterfaceClass.methods.none {it.name == "getName" && it.returnType == "Ljava/lang/String;" }) continue

            //find the method which returns the enum name
            val getEnumMethod = enumClass.virtualMethods.firstOrNull { context.getClass(it.returnType)?.isEnum() == true } ?: continue

            //search for constant field instruction sget-object

            fun getFirstFieldReference21c(opcode: Opcode, method: DexBackedMethod) = method.implementation!!.instructions.firstOrNull {
                it.opcode == opcode && it is DexBackedInstruction21c
            }.let { it as? DexBackedInstruction21c }?.let {
                it.reference as? DexBackedFieldReference
            }

            val fieldReference = getFirstFieldReference21c(Opcode.SGET_OBJECT, getEnumMethod) ?:
                getFirstFieldReference21c(Opcode.SGET_OBJECT,enumClass.directMethods.first { it.name == "<init>" }) ?: continue

            //search field name in the <clinit> class
            val enumClassListEnum = context.getClass(fieldReference.definingClass) ?: continue

            enumClassListEnum.getStaticConstructor()?.let { constructor ->
                var lastEnumClassName = ""
                constructor.implementation!!.instructions.forEach {
                    if (it.opcode == Opcode.CONST_STRING) {
                        lastEnumClassName = ((it as DexBackedInstruction21c).reference as DexBackedStringReference).string
                        return@forEach
                    }

                    if (it.opcode == Opcode.SPUT_OBJECT && it is DexBackedInstruction21c) {
                        val field = it.reference as? DexBackedFieldReference ?: return@forEach
                        if (field.name != fieldReference.name || field.type != fieldReference.type) return@forEach

                        enums.add(lastEnumClassName to enumClass.getClassName())
                    }
                }
            }
        }

        context.addMapping("EnumQualityLevel", enumQualityLevel!!)

        context.addMapping("enums", *enums.sortedBy { it.first }.toTypedArray())
    }
}
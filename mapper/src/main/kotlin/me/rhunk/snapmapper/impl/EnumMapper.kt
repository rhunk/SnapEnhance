package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.getClassName
import me.rhunk.snapmapper.ext.getStaticConstructor
import me.rhunk.snapmapper.ext.hasStaticConstructorString
import me.rhunk.snapmapper.ext.isEnum
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.formats.Instruction21c
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.StringReference

class EnumMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        lateinit var enumQualityLevel : String

        for (enumClass in context.classes) {
            if (!enumClass.isEnum()) continue

            if (enumClass.hasStaticConstructorString("LEVEL_MAX")) {
                enumQualityLevel = enumClass.getClassName()
                break;
            }
        }

        context.addMapping("EnumQualityLevel", enumQualityLevel)
    }
}
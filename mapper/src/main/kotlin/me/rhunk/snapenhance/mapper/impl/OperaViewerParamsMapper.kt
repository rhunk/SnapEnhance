package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

class OperaViewerParamsMapper : AbstractClassMapper("OperaViewerParams") {
    val classReference = classReference("class")
    val putMethod = string("putMethod")

    init {
        mapper {
            for (classDef in classes) {
                classDef.fields.firstOrNull { it.type == "Ljava/util/concurrent/ConcurrentHashMap;" } ?: continue
                if (classDef.methods.firstOrNull { it.name == "toString" }?.implementation?.findConstString("Params") != true) continue

                val putDexMethod = classDef.methods.firstOrNull { method ->
                    method.implementation?.instructions?.any {
                        val instruction = it as? Instruction35c ?: return@any false
                        val reference = instruction.reference as? MethodReference ?: return@any false
                        reference.name == "put" && reference.definingClass == "Ljava/util/concurrent/ConcurrentHashMap;"
                    } == true
                } ?: return@mapper

                classReference.set(classDef.getClassName())
                putMethod.set(putDexMethod.name)

                return@mapper
            }
        }
    }
}

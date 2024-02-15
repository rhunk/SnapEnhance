package me.rhunk.snapenhance.mapper.impl

import com.android.tools.smali.dexlib2.iface.Method
import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

class OperaViewerParamsMapper : AbstractClassMapper("OperaViewerParams") {
    val classReference = classReference("class")
    val getMethod = string("getMethod")
    val getOrDefaultMethod = string("getOrDefaultMethod")

    private fun Method.hasHashMapReference(methodName: String) = implementation?.instructions?.any {
        val instruction = it as? Instruction35c ?: return@any false
        val reference = instruction.reference as? MethodReference ?: return@any false
        reference.name == methodName && reference.definingClass == "Ljava/util/concurrent/ConcurrentHashMap;"
    } == true

    init {
        mapper {
            for (classDef in classes) {
                classDef.fields.firstOrNull { it.type == "Ljava/util/concurrent/ConcurrentHashMap;" } ?: continue
                if (classDef.methods.firstOrNull { it.name == "toString" }?.implementation?.findConstString("Params") != true) continue

                val getOrDefaultDexMethod = classDef.methods.firstOrNull { method ->
                    method.returnType == "Ljava/lang/Object;" &&
                    method.parameters.size == 2 &&
                    method.parameterTypes[1] == "Ljava/lang/Object;" &&
                    method.hasHashMapReference("get")
                } ?: return@mapper

                val getDexMethod = classDef.methods.firstOrNull { method ->
                    method.returnType == "Ljava/lang/Object;" &&
                    method.parameters.size == 1 &&
                    method.parameterTypes[0] == getOrDefaultDexMethod.parameterTypes[0] &&
                    method.hasHashMapReference("get")
                } ?: return@mapper

                getMethod.set(getDexMethod.name)
                getOrDefaultMethod.set(getOrDefaultDexMethod.name)
                classReference.set(classDef.getClassName())
                return@mapper
            }
        }
    }
}

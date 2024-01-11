package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.isAbstract

class DefaultMediaItemMapper : AbstractClassMapper("DefaultMediaItem") {
    val cameraRollMediaId = classReference("cameraRollMediaIdClass")
    val durationMsField = string("durationMsField")
    val defaultMediaItem = classReference("defaultMediaItemClass")

    init {
        mapper {
            for (clazz in classes) {
                if (clazz.methods.find { it.name == "toString" }?.implementation?.findConstString("CameraRollMediaId", contains = true) != true) {
                    continue
                }
                val durationMsDexField = clazz.fields.firstOrNull { it.type == "J" } ?: continue

                cameraRollMediaId.set(clazz.getClassName())
                durationMsField.set(durationMsDexField.name)
                return@mapper
            }
        }

        mapper {
            for (clazz in classes) {
                val superClass = getClass(clazz.superclass) ?: continue

                if (!superClass.isAbstract() || superClass.interfaces.isEmpty() || superClass.interfaces[0] != "Ljava/lang/Comparable;") continue
                if (clazz.methods.none { it.returnType == "Landroid/net/Uri;" }) continue

                val constructorParameters = clazz.directMethods.firstOrNull { it.name == "<init>" }?.parameterTypes ?: continue
                if (constructorParameters.size < 6 || constructorParameters[5] != "J") continue

                defaultMediaItem.set(clazz.getClassName())
                return@mapper
            }
        }
    }
}
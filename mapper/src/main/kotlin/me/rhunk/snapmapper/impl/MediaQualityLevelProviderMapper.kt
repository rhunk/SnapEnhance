package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.isAbstract
import org.jf.dexlib2.AccessFlags

class MediaQualityLevelProviderMapper : AbstractClassMapper(EnumMapper::class) {
    override fun run(context: MapperContext) {
        val mediaQualityLevelClass = context.getStringMapping("EnumQualityLevel") ?: return

        for (clazz in context.classes) {
            if (!clazz.isAbstract()) continue
            if (clazz.fields.none { it.accessFlags and AccessFlags.TRANSIENT.value != 0 }) continue

            clazz.methods.firstOrNull { it.returnType == "L$mediaQualityLevelClass;" }?.let {
                context.addMapping("MediaQualityLevelProvider",
                    "class" to clazz.type.replace("L", "").replace(";", ""),
                    "method" to it.name
                )
            }
        }
    }
}
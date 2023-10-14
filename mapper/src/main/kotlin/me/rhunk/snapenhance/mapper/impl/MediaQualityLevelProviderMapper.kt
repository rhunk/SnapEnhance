package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.MapperContext
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.isAbstract
import org.jf.dexlib2.AccessFlags

class MediaQualityLevelProviderMapper : AbstractClassMapper(EnumMapper::class) {
    override fun run(context: MapperContext) {
        val mediaQualityLevelClass = context.getStringMapping("EnumQualityLevel") ?: return

        for (clazz in context.classes) {
            if (!clazz.isAbstract()) continue
            if (clazz.fields.none { it.accessFlags and AccessFlags.TRANSIENT.value != 0 }) continue

            clazz.methods.firstOrNull { it.returnType == "L$mediaQualityLevelClass;" }?.let {
                context.addMapping("MediaQualityLevelProvider",
                    "class" to clazz.getClassName(),
                    "method" to it.name
                )
                return
            }
        }
    }
}
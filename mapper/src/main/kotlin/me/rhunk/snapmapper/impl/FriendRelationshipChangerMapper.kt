package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString
import me.rhunk.snapmapper.ext.getClassName
import me.rhunk.snapmapper.ext.isEnum

class FriendRelationshipChangerMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (classDef in context.classes) {
            classDef.methods.firstOrNull { it.name == "<init>" }?.implementation?.findConstString("FriendRelationshipChangerImpl")?.takeIf { it } ?: continue
            val addFriendMethod = classDef.methods.first {
                it.parameterTypes.size > 4 &&
                context.getClass(it.parameterTypes[1])?.isEnum() == true &&
                context.getClass(it.parameterTypes[2])?.isEnum() == true &&
                context.getClass(it.parameterTypes[3])?.isEnum() == true &&
                it.parameters[4].type == "Ljava/lang/String;"
            }

            context.addMapping("FriendRelationshipChanger",
                "class" to classDef.getClassName(),
                "addFriendMethod" to addFriendMethod.name
            )
        }
    }
}
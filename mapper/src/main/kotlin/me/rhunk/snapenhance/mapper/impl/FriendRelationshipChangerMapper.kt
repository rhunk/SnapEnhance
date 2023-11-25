package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.isEnum

class FriendRelationshipChangerMapper : AbstractClassMapper() {
    init {
        mapper {
            for (classDef in classes) {
                classDef.methods.firstOrNull { it.name == "<init>" }?.implementation?.findConstString("FriendRelationshipChangerImpl")?.takeIf { it } ?: continue
                val addFriendMethod = classDef.methods.first {
                    it.parameterTypes.size > 4 &&
                            getClass(it.parameterTypes[1])?.isEnum() == true &&
                            getClass(it.parameterTypes[2])?.isEnum() == true &&
                            getClass(it.parameterTypes[3])?.isEnum() == true &&
                            it.parameters[4].type == "Ljava/lang/String;"
                }

                val removeFriendMethod = classDef.methods.first {
                    it.parameterTypes.size == 5 &&
                    it.parameterTypes[0] == "Ljava/lang/String;" &&
                    getClass(it.parameterTypes[1])?.isEnum() == true &&
                    it.parameterTypes[2] == "Ljava/lang/String;" &&
                    it.parameterTypes[3] == "Ljava/lang/String;"
                }

                addMapping("FriendRelationshipChanger",
                    "class" to classDef.getClassName(),
                    "addFriendMethod" to addFriendMethod.name,
                    "removeFriendMethod" to removeFriendMethod.name
                )
                return@mapper
            }
        }
    }
}
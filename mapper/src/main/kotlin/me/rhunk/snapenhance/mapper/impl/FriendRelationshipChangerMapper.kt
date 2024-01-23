package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.isAbstract
import me.rhunk.snapenhance.mapper.ext.isEnum
import java.lang.reflect.Modifier

class FriendRelationshipChangerMapper : AbstractClassMapper("FriendRelationshipChanger") {
    val classReference = classReference("class")
    val addFriendMethod = string("addFriendMethod")

    val removeFriendClass = classReference("removeFriendClass")
    val removeFriendMethod = string("removeFriendMethod")

    init {
        mapper {
            for (classDef in classes) {
                classDef.methods.firstOrNull { it.name == "<init>" }?.implementation?.findConstString("FriendRelationshipChangerImpl")?.takeIf { it } ?: continue
                val addFriendDexMethod = classDef.methods.first {
                    it.parameterTypes.size > 4 &&
                            getClass(it.parameterTypes[1])?.isEnum() == true &&
                            getClass(it.parameterTypes[2])?.isEnum() == true &&
                            getClass(it.parameterTypes[3])?.isEnum() == true &&
                            it.parameters[4].type == "Ljava/lang/String;"
                }

                this@FriendRelationshipChangerMapper.apply {
                    classReference.set(classDef.getClassName())
                    addFriendMethod.set(addFriendDexMethod.name)
                }

                return@mapper
            }
        }
        mapper {
            for (classDef in classes) {
                if (!classDef.isAbstract()) continue
                val removeFriendDexMethod = classDef.methods.firstOrNull {
                    Modifier.isStatic(it.accessFlags) &&
                    it.parameterTypes.size == 5 &&
                    it.returnType.contains("io/reactivex/rxjava3") &&
                    getClass(it.parameterTypes[2])?.isEnum() == true &&
                    getClass(it.parameterTypes[3])?.getClassName()?.endsWith("InteractionPlacementInfo") == true
                } ?: continue

                removeFriendClass.set(classDef.getClassName())
                removeFriendMethod.set(removeFriendDexMethod.name)
                return@mapper
            }
        }
    }
}
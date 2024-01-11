package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName

class ScoreUpdateMapper : AbstractClassMapper("ScoreUpdate") {
    val classReference = classReference("class")

    init {
        mapper {
            for (classDef in classes) {
                classDef.methods.firstOrNull {
                    it.name == "<init>" &&
                            it.parameterTypes.size > 4 &&
                            it.parameterTypes[1] == "Ljava/lang/Long;" &&
                            it.parameterTypes[3] == "Ljava/util/Collection;"
                } ?: continue
                if (classDef.methods.firstOrNull {
                        it.name == "toString"
                    }?.implementation?.findConstString("Friend.sq:selectFriendUserScoresNeedToUpdate") != true) continue

                classReference.set(classDef.getClassName())
                return@mapper
            }
        }
    }
}
package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.searchNextFieldReference

class FriendingDataSourcesMapper: AbstractClassMapper("FriendingDataSources") {
    val classReference = classReference("class")
    val quickAddSourceListField = string("quickAddSourceListField")

    init {
        mapper {
            for (classDef in classes) {
                val constructor = classDef.methods.firstOrNull { it.name == "<init>" } ?: continue
                if (constructor.parameterTypes.size < 4 || (0..3).any { constructor.parameterTypes[it] != "Ljava/util/List;" }) continue

                val toStringMethod = classDef.methods.firstOrNull { it.name == "toString" } ?: continue
                if (toStringMethod.implementation?.findConstString("quickaddSource", contains = true) != true) continue

                val quickAddSourceListDexField = toStringMethod.implementation?.searchNextFieldReference("quickaddSource", contains = true)
                    ?: continue

                classReference.set(classDef.getClassName())
                quickAddSourceListField.set(quickAddSourceListDexField.name)
                return@mapper
            }
        }
    }
}
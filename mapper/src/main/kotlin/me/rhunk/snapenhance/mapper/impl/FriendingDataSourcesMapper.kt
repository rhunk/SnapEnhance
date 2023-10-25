package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.searchNextFieldReference

class FriendingDataSourcesMapper: AbstractClassMapper() {
    init {
        mapper {
            for (classDef in classes) {
                val constructor = classDef.methods.firstOrNull { it.name == "<init>" } ?: continue
                if (constructor.parameterTypes.size < 4 || (0..3).any { constructor.parameterTypes[it] != "Ljava/util/List;" }) continue

                val toStringMethod = classDef.methods.firstOrNull { it.name == "toString" } ?: continue
                if (toStringMethod.implementation?.findConstString("quickaddSource", contains = true) != true) continue

                val quickAddSourceListField = toStringMethod.implementation?.searchNextFieldReference("quickaddSource", contains = true)
                    ?: continue

                addMapping("FriendingDataSources",
                    "class" to classDef.getClassName(),
                    "quickAddSourceListField" to quickAddSourceListField.name
                )
                return@mapper
            }
        }
    }
}
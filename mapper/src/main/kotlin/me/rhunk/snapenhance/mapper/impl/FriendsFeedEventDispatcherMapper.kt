package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName


class FriendsFeedEventDispatcherMapper : AbstractClassMapper("FriendsFeedEventDispatcher") {
    val classReference = classReference("class")
    val viewModelField = string("viewModelField")

    init {
        mapper {
            for (clazz in classes) {
                if (clazz.methods.count { it.name == "onClickFeed" || it.name == "onItemLongPress" } != 2) continue
                val onItemLongPress = clazz.methods.first { it.name == "onItemLongPress" }
                val viewHolderContainerClass = getClass(onItemLongPress.parameterTypes[0]) ?: continue

                val viewModelDexField = viewHolderContainerClass.fields.firstOrNull { field ->
                    val typeClass = getClass(field.type) ?: return@firstOrNull false
                    typeClass.methods.firstOrNull {it.name == "toString"}?.implementation?.findConstString("FriendFeedItemViewModel", contains = true) == true
                }?.name ?: continue

                classReference.set(clazz.getClassName())
                viewModelField.set(viewModelDexField)
                return@mapper
            }
        }
    }
}
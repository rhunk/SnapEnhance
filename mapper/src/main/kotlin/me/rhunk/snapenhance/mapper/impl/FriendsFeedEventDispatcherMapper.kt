package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.MapperContext
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName


class FriendsFeedEventDispatcherMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            if (clazz.methods.count { it.name == "onClickFeed" || it.name == "onItemLongPress" } != 2) continue
            val onItemLongPress = clazz.methods.first { it.name == "onItemLongPress" }
            val viewHolderContainerClass = context.getClass(onItemLongPress.parameterTypes[0]) ?: continue

            val viewModelField = viewHolderContainerClass.fields.firstOrNull { field ->
                val typeClass = context.getClass(field.type) ?: return@firstOrNull false
                typeClass.methods.firstOrNull {it.name == "toString"}?.implementation?.findConstString("FriendFeedItemViewModel", contains = true) == true
            }?.name ?: continue

            context.addMapping("FriendsFeedEventDispatcher",
                "class" to clazz.getClassName(),
                "viewModelField" to viewModelField
            )
            return
        }
    }
}
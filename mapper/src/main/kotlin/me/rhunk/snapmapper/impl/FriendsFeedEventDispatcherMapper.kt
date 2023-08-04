package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString
import me.rhunk.snapmapper.ext.getClassName


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
        }
    }
}
package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName

class MemoriesPresenterMapper : AbstractClassMapper("MemoriesPresenter") {
    val classReference = classReference("class")
    val onNavigationEventMethod = string("onNavigationEventMethod")

    init {
        mapper {
            for (clazz in classes) {
                if (clazz.interfaces.size != 1) continue
                val getNameMethod = clazz.methods.firstOrNull { it.name == "getName" } ?: continue
                if (getNameMethod.implementation?.findConstString("MemoriesAsyncPresenterFragmentSubscriber") != true) continue

                val onNavigationEvent = clazz.methods.firstOrNull { it.implementation?.findConstString("Memories") == true } ?: continue

                classReference.set(clazz.getClassName())
                onNavigationEventMethod.set(onNavigationEvent.name)
            }
        }
    }
}
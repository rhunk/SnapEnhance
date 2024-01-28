package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.MapperContext
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName

class PlusSubscriptionMapper : AbstractClassMapper(){
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            if (clazz.directMethods.filter { it.name == "<init>" }.none {
                it.parameters.size >= 4 &&
                it.parameterTypes[0] == "I" &&
                it.parameterTypes[1] == "I" &&
                it.parameterTypes[2] == "J" &&
                it.parameterTypes[3] == "J"
            }) continue

            val isPlusSubscriptionInfoClass = clazz.virtualMethods.firstOrNull { it.name == "toString" }?.implementation?.let {
                it.findConstString("SubscriptionInfo", contains = true) && it.findConstString("expirationTimeMillis", contains = true)
            }

            if (isPlusSubscriptionInfoClass == true) {
                context.addMapping("SubscriptionInfoClass", clazz.getClassName())
                return
            }
        }
    }
}
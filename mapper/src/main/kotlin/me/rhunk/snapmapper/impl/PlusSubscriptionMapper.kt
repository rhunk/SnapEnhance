package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString

class PlusSubscriptionMapper : AbstractClassMapper(){
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            if (clazz.directMethods.filter { it.name == "<init>" }.none {
                it.parameters.size == 4 &&
                it.parameterTypes[0] == "I" &&
                it.parameterTypes[1] == "I" &&
                it.parameterTypes[2] == "J" &&
                it.parameterTypes[3] == "J"
            }) continue

            val isPlusSubscriptionInfoClass = clazz.virtualMethods.firstOrNull { it.name == "toString" }?.implementation?.let {
                it.findConstString("SubscriptionInfo", contains = true) && it.findConstString("expirationTimeMillis", contains = true)
            }

            if (isPlusSubscriptionInfoClass == true) {
                context.addMapping("SubscriptionInfoClass", clazz.type.replace("L", "").replace(";", ""))
                return
            }
        }
    }
}
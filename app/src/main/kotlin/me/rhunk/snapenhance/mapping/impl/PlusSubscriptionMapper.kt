package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Field
import java.lang.reflect.Method


class PlusSubscriptionMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        //find a method that contains annotations with isSubscribed
        val loadSubscriptionMethod = context.classCache.composerLocalSubscriptionStore.declaredMethods.first { method: Method ->
            val returnType = method.returnType
            returnType.declaredFields.any { field: Field ->
                field.declaredAnnotations.any { annotation: Annotation ->
                    annotation.toString().contains("isSubscribed")
                }
            }
        }
        /*
        //get the first param of the method which is the PlusSubscriptionState class
        val plusSubscriptionStateClass = loadSubscriptionMethod.parameterTypes[0]
        //get the first param of the constructor of PlusSubscriptionState which is the SubscriptionInfo class
        val subscriptionInfoClass = plusSubscriptionStateClass.constructors[0].parameterTypes[0]
        */
        mappings["SubscriptionInfoClass"] = loadSubscriptionMethod.returnType.name

        val members = mutableMapOf<String, Any>()
        loadSubscriptionMethod.returnType.declaredFields.forEach { field ->
            val serializedNameAnnotation = field.declaredAnnotations.first()
            val propertyName = serializedNameAnnotation.annotationClass.members.first { it.name == "name" }.call(serializedNameAnnotation) as String
            members[propertyName] = field.name
        }

        mappings["SubscriptionInfoClassMembers"] = members
    }
}
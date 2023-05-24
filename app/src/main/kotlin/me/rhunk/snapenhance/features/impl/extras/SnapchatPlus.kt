package me.rhunk.snapenhance.features.impl.extras

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class SnapchatPlus: Feature("SnapchatPlus", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        if (!context.config.bool(ConfigProperty.SNAPCHAT_PLUS)) return

        val subscriptionInfoMembers = context.mappings.getMappedMap("SubscriptionInfoClassMembers")

        Hooker.hookConstructor(context.mappings.getMappedClass("SubscriptionInfoClass"), HookStage.AFTER) { param ->
            val getField = { key: String ->  param.thisObject<Any>().javaClass.declaredFields.first {it.name == (subscriptionInfoMembers[key] as String)}.also { it.isAccessible = true }}

            val subscriptionStatusField = getField("status")
            val isSubscribedField = getField("isSubscribed")
            val startTimeMsField = getField("startTimeMs")
            val expireTimeMsField = getField("expireTimeMs")

            //check if the user is already premium
            if ((subscriptionStatusField[param.thisObject()] as Double).toInt() == 2) {
                return@hookConstructor
            }

            isSubscribedField.set(param.thisObject(), true)
            startTimeMsField.set(param.thisObject(), (System.currentTimeMillis() - 7776000000L).toDouble())
            expireTimeMsField.set(param.thisObject(), (System.currentTimeMillis() + 15552000000L).toDouble())
            subscriptionStatusField.set(param.thisObject(), 2.toDouble())
        }
    }
}
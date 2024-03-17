package me.rhunk.snapenhance.core.features.impl.messaging

import me.rhunk.snapenhance.core.event.events.impl.BuildMessageEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams

class BypassMessageActionRestrictions : Feature("Bypass Message Action Restrictions", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    override fun onActivityCreate() {
        if (!context.config.messaging.bypassMessageActionRestrictions.get()) return
        context.event.subscribe(BuildMessageEvent::class, priority = 102) { event ->
            event.message.messageMetadata?.apply {
                isSaveable = true
                isReactable = true
            }
        }
    }
}
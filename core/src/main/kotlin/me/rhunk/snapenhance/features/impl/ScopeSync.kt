package me.rhunk.snapenhance.features.impl

import kotlinx.coroutines.*
import me.rhunk.snapenhance.core.eventbus.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.messaging.SocialScope
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams

class ScopeSync : Feature("Scope Sync", loadParams = FeatureLoadParams.INIT_SYNC) {
    companion object {
        private const val DELAY_BEFORE_SYNC = 2000L
    }

    private val updateJobs = mutableMapOf<String, Job>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private fun sync(conversationId: String) {
        context.database.getDMOtherParticipant(conversationId)?.also { participant ->
            context.bridgeClient.triggerSync(SocialScope.FRIEND, participant)
        } ?: run {
            context.bridgeClient.triggerSync(SocialScope.GROUP, conversationId)
        }
    }

    override fun init() {
        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            if (event.messageContent.contentType != ContentType.SNAP) return@subscribe

            event.addCallbackResult("onSuccess") {
                event.destinations.conversations.map { it.toString() }.forEach { conversationId ->
                    updateJobs[conversationId]?.also { it.cancel() }

                    updateJobs[conversationId] = (coroutineScope.launch {
                        delay(DELAY_BEFORE_SYNC)
                        sync(conversationId)
                    })
                }
            }
        }
    }
}
package me.rhunk.snapenhance

import me.rhunk.snapenhance.bridge.logger.TrackerInterface
import me.rhunk.snapenhance.common.data.TrackerEventsResult
import me.rhunk.snapenhance.common.data.TrackerRule
import me.rhunk.snapenhance.common.data.TrackerRuleEvent
import me.rhunk.snapenhance.common.util.toSerialized


class RemoteTracker(
    private val context: RemoteSideContext
): TrackerInterface.Stub() {
    fun init() {
        /*TrackerEventType.entries.forEach { eventType ->
            val ruleId = context.modDatabase.addTrackerRule(TrackerFlags.TRACK or TrackerFlags.LOG or TrackerFlags.NOTIFY, null, null)
            context.modDatabase.addTrackerRuleEvent(ruleId, TrackerFlags.TRACK or TrackerFlags.LOG or TrackerFlags.NOTIFY, eventType.key)
        }*/
    }

    override fun getTrackedEvents(eventType: String): String? {
        val events = mutableMapOf<TrackerRule, MutableList<TrackerRuleEvent>>()

        context.modDatabase.getTrackerEvents(eventType).forEach { (event, rule) ->
            events.getOrPut(rule) { mutableListOf() }.add(event)
        }

        return TrackerEventsResult(events).toSerialized()
    }
}
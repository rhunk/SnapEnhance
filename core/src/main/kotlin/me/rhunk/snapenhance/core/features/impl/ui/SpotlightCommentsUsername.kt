package me.rhunk.snapenhance.core.features.impl.ui

import android.annotation.SuppressLint
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.util.EvictingMap
import me.rhunk.snapenhance.core.util.ktx.getId

class SpotlightCommentsUsername : Feature("SpotlightCommentsUsername", loadParams =  FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val usernameCache = EvictingMap<String, String>(150)

    @SuppressLint("SetTextI18n")
    override fun onActivityCreate() {
        if (!context.config.global.spotlightCommentsUsername.get()) return

        val messaging = context.feature(Messaging::class)
        val commentsCreatorBadgeTimestampId = context.resources.getId("comments_creator_badge_timestamp")

        context.event.subscribe(BindViewEvent::class) { event ->
            val commentsCreatorBadgeTimestamp = event.view.findViewById<TextView>(commentsCreatorBadgeTimestampId) ?: return@subscribe

            val posterUserId = event.prevModel.toString().takeIf { it.startsWith("Comment") }
                ?.substringAfter("posterUserId=")?.substringBefore(",")?.substringBefore(")") ?: return@subscribe

            fun setUsername(username: String) {
                usernameCache[posterUserId] = username
                commentsCreatorBadgeTimestamp.text = " (${username})" + commentsCreatorBadgeTimestamp.text.toString()
            }

            usernameCache[posterUserId]?.let {
                setUsername(it)
                return@subscribe
            }

            context.coroutineScope.launch {
                val username = runCatching {
                    messaging.fetchSnapchatterInfos(listOf(posterUserId)).firstOrNull()
                }.onFailure {
                    context.log.error("Failed to fetch snapchatter info for user $posterUserId", it)
                }.getOrNull()?.username ?: return@launch

                withContext(Dispatchers.Main) {
                    setUsername(username)
                }
            }
        }
    }
}
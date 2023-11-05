package me.rhunk.snapenhance.messaging

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.bridge.ForceStartActivity
import me.rhunk.snapenhance.common.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.ui.util.ImageRequestHelper
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class StreaksReminder(
    private val remoteSideContext: RemoteSideContext? = null
): BroadcastReceiver() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "streaks"
    }

    private fun getNotificationManager(context: Context) = context.getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Streaks",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val remoteSideContext = this.remoteSideContext ?: SharedContextHolder.remote(ctx)
        val streaksReminderConfig = remoteSideContext.config.root.streaksReminder
        val sharedPreferences = remoteSideContext.sharedPreferences

        if (streaksReminderConfig.globalState != true) return

        val interval = streaksReminderConfig.interval.get()
        val remainingHours = streaksReminderConfig.remainingHours.get()

        if (sharedPreferences.getLong("lastStreaksReminder", 0).milliseconds + interval.hours - 10.minutes > System.currentTimeMillis().milliseconds) return
        sharedPreferences.edit().putLong("lastStreaksReminder", System.currentTimeMillis()).apply()

        remoteSideContext.androidContext.getSystemService(AlarmManager::class.java).setRepeating(
            AlarmManager.RTC_WAKEUP, 5000, interval.toLong() * 60 * 60 * 1000,
            PendingIntent.getBroadcast(remoteSideContext.androidContext, 0, Intent(remoteSideContext.androidContext, StreaksReminder::class.java),
                PendingIntent.FLAG_IMMUTABLE)
        )

        val notifyFriendList = remoteSideContext.modDatabase.getFriends()
            .associateBy { remoteSideContext.modDatabase.getFriendStreaks(it.userId) }
            .filter { (streaks, _) -> streaks != null && streaks.notify && streaks.isAboutToExpire(remainingHours) }

        val notificationManager = getNotificationManager(ctx)
        val streaksReminderTranslation = remoteSideContext.translation.getCategory("streaks_reminder")

        if (streaksReminderConfig.groupNotifications.get() && notifyFriendList.isNotEmpty()) {
            notificationManager.notify(0, NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup("streaks")
                .setGroupSummary(true)
                .setSmallIcon(R.drawable.streak_icon)
                .build())
        }

        notifyFriendList.forEach { (streaks, friend) ->
            remoteSideContext.coroutineScope.launch {
                val bitmojiUrl = BitmojiSelfie.getBitmojiSelfie(friend.selfieId, friend.bitmojiId, BitmojiSelfie.BitmojiSelfieType.THREE_D)
                val bitmojiImage = remoteSideContext.imageLoader.execute(
                    ImageRequestHelper.newBitmojiImageRequest(ctx, bitmojiUrl)
                )

                val notificationBuilder = NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(streaksReminderTranslation["notification_title"])
                    .setContentText(streaksReminderTranslation.format("notification_text",
                        "friend" to (friend.displayName ?: friend.mutableUsername),
                        "hoursLeft" to (streaks?.hoursLeft() ?: 0).toString()
                    ))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setGroup("streaks")
                    .setContentIntent(PendingIntent.getActivity(
                        ctx,
                        0,
                        Intent(ctx, ForceStartActivity::class.java).apply {
                            putExtra("streaks_notification_action", true)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    ))
                    .apply {
                        setSmallIcon(R.drawable.streak_icon)
                        bitmojiImage.drawable?.let {
                            setLargeIcon(it.toBitmap())
                        }
                    }

                if (streaksReminderConfig.groupNotifications.get()) {
                    notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                }

                notificationManager.notify(friend.userId.hashCode(), notificationBuilder.build().apply {
                    flags = NotificationCompat.FLAG_ONLY_ALERT_ONCE
                })
            }
        }
    }

    fun init() {
        if (remoteSideContext == null) throw IllegalStateException("RemoteSideContext is null")
        if (remoteSideContext.config.root.streaksReminder.globalState != true) return

        onReceive(remoteSideContext.androidContext, Intent())
    }

    fun dismissAllNotifications() = getNotificationManager(remoteSideContext!!.androidContext).cancelAll()
}
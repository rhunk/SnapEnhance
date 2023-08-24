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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.bridge.ForceStartActivity
import me.rhunk.snapenhance.ui.util.ImageRequestHelper
import me.rhunk.snapenhance.util.snap.BitmojiSelfie

class StreaksReminder(
    private val remoteSideContext: RemoteSideContext? = null
): BroadcastReceiver() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "streaks"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
        if (remoteSideContext.config.root.streaksReminder.globalState != true) return

        val notifyFriendList = remoteSideContext.modDatabase.getFriends()
            .associateBy { remoteSideContext.modDatabase.getFriendStreaks(it.userId) }
            .filter { (streaks, _) -> streaks != null && streaks.notify && streaks.isAboutToExpire() }

        val notificationManager = getNotificationManager(ctx)

        notifyFriendList.forEach { (streaks, friend) ->
            coroutineScope.launch {
                val bitmojiUrl = BitmojiSelfie.getBitmojiSelfie(friend.selfieId, friend.bitmojiId, BitmojiSelfie.BitmojiSelfieType.THREE_D)
                val bitmojiImage = remoteSideContext.imageLoader.execute(
                    ImageRequestHelper.newBitmojiImageRequest(ctx, bitmojiUrl)
                )

                val notificationBuilder = NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Streaks")
                    .setContentText("You will lose streaks with ${friend.displayName} in ${streaks?.hoursLeft() ?: 0} hours")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(PendingIntent.getActivity(
                        ctx,
                        0,
                        Intent(ctx, ForceStartActivity::class.java).apply {
                            putExtra("streaks_notification_action", true)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    ))
                    .apply {
                        bitmojiImage.drawable?.let {
                            setLargeIcon(it.toBitmap())
                            setSmallIcon(R.drawable.streak_icon)
                        }
                    }

                notificationManager.notify(friend.userId.hashCode(), notificationBuilder.build().apply {
                    flags = NotificationCompat.FLAG_ONLY_ALERT_ONCE
                })
            }
        }
    }

    //TODO: ask for notifications permission for a13+
    fun init() {
        if (remoteSideContext == null) throw IllegalStateException("RemoteSideContext is null")
        val reminderConfig = remoteSideContext.config.root.streaksReminder.also {
            if (it.globalState != true) return
        }

        remoteSideContext.androidContext.getSystemService(AlarmManager::class.java).setRepeating(
            AlarmManager.RTC_WAKEUP, 5000, reminderConfig.interval.get().toLong() * 60 * 60 * 1000,
            PendingIntent.getBroadcast(remoteSideContext.androidContext, 0, Intent(remoteSideContext.androidContext, StreaksReminder::class.java),
                PendingIntent.FLAG_IMMUTABLE)
        )

        onReceive(remoteSideContext.androidContext, Intent())
    }

    fun dismissAllNotifications() = getNotificationManager(remoteSideContext!!.androidContext).cancelAll()
}
package me.rhunk.snapenhance.core.features.impl.downloader

import android.annotation.SuppressLint
import android.widget.Button
import android.widget.RelativeLayout
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker
import java.nio.ByteBuffer

class ProfilePictureDownloader : Feature("ProfilePictureDownloader", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    @SuppressLint("SetTextI18n")
    override fun asyncOnActivityCreate() {
        if (!context.config.downloader.downloadProfilePictures.get()) return

        var friendUsername: String? = null
        var backgroundUrl: String? = null
        var avatarUrl: String? = null

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.view::class.java.name != "com.snap.unifiedpublicprofile.UnifiedPublicProfileView") return@subscribe

            event.parent.addView(Button(event.parent.context).apply {
                text = this@ProfilePictureDownloader.context.translation["profile_picture_downloader.button"]
                layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 200, 0, 0)
                }
                setOnClickListener {
                    ViewAppearanceHelper.newAlertDialogBuilder(
                        this@ProfilePictureDownloader.context.mainActivity!!
                    ).apply {
                        setTitle(this@ProfilePictureDownloader.context.translation["profile_picture_downloader.title"])
                        val choices = mutableMapOf<String, String>()
                        backgroundUrl?.let { choices["avatar_option"] = it }
                        avatarUrl?.let { choices["background_option"] = it }

                        setItems(choices.keys.map {
                            this@ProfilePictureDownloader.context.translation["profile_picture_downloader.$it"]
                        }.toTypedArray()) { _, which ->
                            runCatching {
                                this@ProfilePictureDownloader.context.feature(MediaDownloader::class).downloadProfilePicture(
                                    choices.values.elementAt(which),
                                    friendUsername!!
                                )
                            }.onFailure {
                                this@ProfilePictureDownloader.context.log.error("Failed to download profile picture", it)
                            }
                        }
                    }.show()
                }
            })
        }


        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (!event.url.endsWith("/rpc/getPublicProfile")) return@subscribe
            event.onSuccess {  buffer ->
                ProtoReader(buffer ?: return@onSuccess).followPath(1, 1, 2) {
                    friendUsername = getString(2) ?: return@followPath
                    followPath(4) {
                        backgroundUrl = getString(2)
                        avatarUrl = getString(100)
                    }
                }
            }
        }
    }
}
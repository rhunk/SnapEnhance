package me.rhunk.snapenhance.features.impl.downloader

import android.annotation.SuppressLint
import android.widget.Button
import android.widget.RelativeLayout
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.core.eventbus.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.eventbus.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.nio.ByteBuffer

class ProfilePictureDownloader : Feature("ProfilePictureDownloader", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    @SuppressLint("SetTextI18n")
    override fun asyncOnActivityCreate() {
        var friendUsername: String? = null
        var backgroundUrl: String? = null
        var avatarUrl: String? = null

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.view::class.java.name != "com.snap.unifiedpublicprofile.UnifiedPublicProfileView") return@subscribe

            event.parent.addView(Button(event.parent.context).apply {
                text = "Download"
                layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 200, 0, 0)
                }
                setOnClickListener {
                    ViewAppearanceHelper.newAlertDialogBuilder(
                        this@ProfilePictureDownloader.context.mainActivity!!
                    ).apply {
                        setTitle("Download profile picture")
                        val choices = mutableMapOf<String, String>()
                        backgroundUrl?.let { choices["Background"] = it }
                        avatarUrl?.let { choices["Avatar"] = it }

                        setItems(choices.keys.toTypedArray()) { _, which ->
                            runCatching {
                                this@ProfilePictureDownloader.context.feature(MediaDownloader::class).downloadProfilePicture(
                                    choices.values.elementAt(which),
                                    friendUsername!!
                                )
                            }.onFailure {
                                Logger.error("Failed to download profile picture", it)
                            }
                        }
                    }.show()
                }
            })
        }


        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (!event.url.endsWith("/rpc/getPublicProfile")) return@subscribe
            Hooker.ephemeralHookObjectMethod(event.callback::class.java, event.callback, "onSucceeded", HookStage.BEFORE) { methodParams ->
                val content = methodParams.arg<ByteBuffer>(2).run {
                    ByteArray(capacity()).also {
                        get(it)
                        position(0)
                    }
                }

                ProtoReader(content).followPath(1, 1, 2) {
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
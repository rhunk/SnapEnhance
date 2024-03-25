package me.rhunk.snapenhance.core.features.impl.experiments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.database.CursorWrapper
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Upload
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.util.ktx.getLongOrNull
import me.rhunk.snapenhance.common.util.ktx.getTypeArguments
import me.rhunk.snapenhance.core.event.events.impl.ActivityResultEvent
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.util.dataBuilder
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getId
import java.io.InputStream
import java.lang.reflect.Method
import kotlin.random.Random

class MediaFilePicker : Feature("Media File Picker", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    var lastMediaDuration: Long? = null
        private set

    @SuppressLint("Recycle")
    override fun onActivityCreate() {
        if (!context.config.experimental.mediaFilePicker.get()) return

        lateinit var chatMediaDrawerActionHandler: Any
        lateinit var sendItemsMethod: Method

        findClass("com.snap.composer.memories.ChatMediaDrawer").genericSuperclass?.getTypeArguments()?.getOrNull(1)?.apply {
            methods.first {
                it.parameterTypes.size == 1 && it.parameterTypes[0].name.endsWith("ChatMediaDrawerActionHandler")
            }.also { method ->
                sendItemsMethod = method.parameterTypes[0].methods.first { it.name == "sendItems" }
            }.hook(HookStage.AFTER) {
                chatMediaDrawerActionHandler = it.arg(0)
            }
        }

        var requestCode: Int? = null
        var firstVideoId: Long? = null
        var mediaInputStream: InputStream? = null

        ContentResolver::class.java.apply {
            hook("query", HookStage.AFTER) { param ->
                val uri = param.arg<Uri>(0)
                if (!uri.toString().endsWith(firstVideoId.toString())) return@hook

                param.setResult(object: CursorWrapper(param.getResult() as Cursor) {
                    override fun getLong(columnIndex: Int): Long {
                        if (getColumnName(columnIndex) == "duration") {
                            return lastMediaDuration ?: -1
                        }
                        return super.getLong(columnIndex)
                    }
                })
            }
            hook("openInputStream", HookStage.BEFORE) { param ->
                val uri = param.arg<Uri>(0)
                if (uri.toString().endsWith(firstVideoId.toString())) {
                    param.setResult(mediaInputStream)
                    mediaInputStream = null
                }
            }
        }

        context.event.subscribe(ActivityResultEvent::class) { event ->
            if (event.requestCode != requestCode || event.resultCode != Activity.RESULT_OK) return@subscribe
            requestCode = null

            firstVideoId = context.androidContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                null,
                null,
                "${MediaStore.Video.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLongOrNull("_id")
                } else {
                    null
                }
            }

            if (firstVideoId == null) {
                context.inAppOverlay.showStatusToast(
                    Icons.Default.Upload,
                    "Must have a video in gallery to upload."
                )
                return@subscribe
            }

            fun sendMedia() {
                sendItemsMethod.invoke(chatMediaDrawerActionHandler, listOf<Any>(), listOf(
                    sendItemsMethod.genericParameterTypes[1].getTypeArguments().first().dataBuilder {
                        from("_item") {
                            set("_cameraRollSource", "Snapchat")
                            set("_contentUri", "")
                            set("_durationMs", 0.0)
                            set("_disabled", false)
                            set("_imageRotation", 0.0)
                            set("_width", 1080.0)
                            set("_height", 1920.0)
                            set("_timestampMs", System.currentTimeMillis().toDouble())
                            from("_itemId") {
                                set("_itemId", firstVideoId.toString())
                                set("_type", "VIDEO")
                            }
                        }
                        set("_order", 0.0)
                    }
                ))
            }

            fun startConversation(audioOnly: Boolean) {
                context.coroutineScope.launch {
                    lastMediaDuration = MediaPlayer().run {
                        setDataSource(context.androidContext, event.intent.data!!)
                        prepare()
                        duration.toLong().also {
                            release()
                        }
                    }

                    context.inAppOverlay.showStatusToast(Icons.Default.Crop, "Converting media...", durationMs = 3000)
                    val pfd = context.bridgeClient.convertMedia(
                        context.androidContext.contentResolver.openFileDescriptor(event.intent.data!!, "r")!!,
                        "m4a",
                        "m4a",
                        "aac",
                        if (!audioOnly) "libx264" else null
                    )

                    if (pfd == null) {
                        context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to convert media.")
                        return@launch
                    }

                    context.inAppOverlay.showStatusToast(Icons.Default.CheckCircleOutline, "Media converted successfully.")

                    runCatching {
                        mediaInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                        context.log.verbose("Media duration: $lastMediaDuration")
                        sendMedia()
                    }.onFailure {
                        mediaInputStream = null
                        context.log.error(it)
                        context.inAppOverlay.showStatusToast(Icons.Default.Error, "Failed to send media.")
                    }
                }
            }

            val isAudio = context.androidContext.contentResolver.getType(event.intent.data!!)!!.startsWith("audio/")

            if (isAudio || !context.config.messaging.galleryMediaSendOverride.get()) {
                startConversation(isAudio)
                return@subscribe
            }

            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!)
                .setTitle("Convert video file")
                .setItems(arrayOf("Send as video/audio", "Send as audio only")) { _, which ->
                    startConversation(which == 1)
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.show()
        }

        val buttonTag = Random.nextInt(0, 65535)

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.parent.id != context.resources.getId("chat_drawer_container") || !event.view::class.java.name.endsWith("ChatMediaDrawer")) return@subscribe

            event.view.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    event.parent.addView(
                        Button(event.parent.context).apply {
                            text = "Upload"
                            tag = buttonTag
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setOnClickListener {
                                requestCode = Random.nextInt(0, 65535)
                                this@MediaFilePicker.context.mainActivity!!.startActivityForResult(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "video/*"
                                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
                                    },
                                    requestCode!!
                                )
                            }
                        }
                    )
                }

                override fun onViewDetachedFromWindow(v: View) {
                    event.parent.findViewWithTag<View>(buttonTag)?.let {
                        event.parent.removeView(it)
                    }
                }
            })
        }
    }
}
package me.rhunk.snapenhance.features.impl.tweaks

import android.os.Build
import android.os.FileObserver
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.core.eventbus.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import java.io.File

class DisableVideoLengthRestriction : Feature("DisableVideoLengthRestriction", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private lateinit var fileObserver: FileObserver

    override fun asyncOnActivityCreate() {
        val defaultMediaItem = context.mappings.getMappedClass("DefaultMediaItem")
        val isState by context.config.global.disableVideoLengthRestrictions

        //fix black videos when story is posted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val postedStorySnapFolder = File(context.androidContext.filesDir, "file_manager/posted_story_snap")

            fileObserver = (object : FileObserver(postedStorySnapFolder, MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (event != MOVED_TO || path?.endsWith("posted_story_snap.2") != true) return
                    fileObserver.stopWatching()

                    val file = File(postedStorySnapFolder, path)
                    runCatching {
                        val fileContent = JsonParser.parseReader(file.reader()).asJsonObject
                        if (fileContent["timerOrDuration"].asLong < 0) file.delete()
                    }.onFailure {
                        Logger.error("Failed to read story metadata file", it)
                    }
                }
            })

            context.event.subscribe(SendMessageWithContentEvent::class) { event ->
                if (event.destinations.stories.isEmpty()) return@subscribe
                fileObserver.startWatching()
            }
        }

        Hooker.hookConstructor(defaultMediaItem, HookStage.BEFORE, { isState }) { param ->
            //set the video length argument
            param.setArg(5, -1L)
        }
    }
}
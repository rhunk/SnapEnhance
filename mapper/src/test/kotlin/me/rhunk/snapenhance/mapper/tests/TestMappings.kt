package me.rhunk.snapenhance.mapper.tests

import com.google.gson.GsonBuilder
import me.rhunk.snapmapper.Mapper
import me.rhunk.snapmapper.impl.*
import org.junit.Test
import java.io.File


class TestMappings {
    @Test
    fun testMappings() {
        val mapper = Mapper(
            BCryptClassMapper::class,
            CallbackMapper::class,
            DefaultMediaItemMapper::class,
            MediaQualityLevelProviderMapper::class,
            EnumMapper::class,
            OperaPageViewControllerMapper::class,
            PlatformAnalyticsCreatorMapper::class,
            PlusSubscriptionMapper::class,
            ScCameraSettingsMapper::class,
            StoryBoostStateMapper::class,
            FriendsFeedEventDispatcherMapper::class
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val apkFile = File(System.getenv("SNAPCHAT_APK")!!)
        mapper.loadApk(apkFile.absolutePath)
        val result = mapper.start()
        println("Mappings: ${gson.toJson(result)}")
    }
}

package me.rhunk.snapenhance.mapper.tests

import com.google.gson.GsonBuilder
import me.rhunk.snapenhance.mapper.Mapper
import me.rhunk.snapenhance.mapper.impl.*
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
            OperaPageViewControllerMapper::class,
            PlusSubscriptionMapper::class,
            ScCameraSettingsMapper::class,
            StoryBoostStateMapper::class,
            FriendsFeedEventDispatcherMapper::class,
            CompositeConfigurationProviderMapper::class,
            ScoreUpdateMapper::class,
            FriendRelationshipChangerMapper::class,
            ViewBinderMapper::class,
            FriendingDataSourcesMapper::class,
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val apkFile = File(System.getenv("SNAPCHAT_APK")!!)
        mapper.loadApk(apkFile.absolutePath)
        val result = mapper.start()
        println("Mappings: ${gson.toJson(result)}")
    }
}

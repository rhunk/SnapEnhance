package me.rhunk.snapenhance.bridge.wrapper

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapmapper.Mapper
import me.rhunk.snapmapper.impl.BCryptClassMapper
import me.rhunk.snapmapper.impl.CallbackMapper
import me.rhunk.snapmapper.impl.CompositeConfigurationProviderMapper
import me.rhunk.snapmapper.impl.DefaultMediaItemMapper
import me.rhunk.snapmapper.impl.EnumMapper
import me.rhunk.snapmapper.impl.FriendsFeedEventDispatcherMapper
import me.rhunk.snapmapper.impl.MediaQualityLevelProviderMapper
import me.rhunk.snapmapper.impl.OperaPageViewControllerMapper
import me.rhunk.snapmapper.impl.PlatformAnalyticsCreatorMapper
import me.rhunk.snapmapper.impl.PlusSubscriptionMapper
import me.rhunk.snapmapper.impl.ScCameraSettingsMapper
import me.rhunk.snapmapper.impl.ScoreUpdateMapper
import me.rhunk.snapmapper.impl.StoryBoostStateMapper
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

class MappingsWrapper : FileLoaderWrapper(BridgeFileType.MAPPINGS, "{}".toByteArray(Charsets.UTF_8)) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private val mappers = arrayOf(
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
            FriendsFeedEventDispatcherMapper::class,
            CompositeConfigurationProviderMapper::class,
            ScoreUpdateMapper::class
        )
    }

    private lateinit var context: Context

    private val mappings = ConcurrentHashMap<String, Any>()
    private var snapBuildNumber: Long = 0

    fun init(context: Context) {
        this.context = context
        snapBuildNumber = getSnapchatVersionCode()

        if (isFileExists()) {
            runCatching {
                loadCached()
            }.onFailure {
                Logger.error("Failed to load cached mappings", it)
                delete()
            }
        } else {
            Logger.debug("Mappings file does not exist")
        }
    }

    fun getSnapchatPackageInfo() = runCatching {
        context.packageManager.getPackageInfo(
            Constants.SNAPCHAT_PACKAGE_NAME,
            0
        )
    }.getOrNull()

    fun getSnapchatVersionCode() = getSnapchatPackageInfo()?.longVersionCode ?: -1
    fun getApplicationSourceDir() = getSnapchatPackageInfo()?.applicationInfo?.sourceDir
    fun getGeneratedBuildNumber() = snapBuildNumber

    fun isMappingsOutdated(): Boolean {
        return snapBuildNumber != getSnapchatVersionCode() || isMappingsLoaded().not()
    }

    fun isMappingsLoaded(): Boolean {
        return mappings.isNotEmpty()
    }

    private fun loadCached() {
        if (!isFileExists()) {
            throw Exception("Mappings file does not exist")
        }
        val mappingsObject = JsonParser.parseString(read().toString(Charsets.UTF_8)).asJsonObject.also {
            snapBuildNumber = it["snap_build_number"].asLong
        }

        mappingsObject.entrySet().forEach { (key, value): Map.Entry<String, JsonElement> ->
            if (value.isJsonArray) {
                mappings[key] = gson.fromJson(value, ArrayList::class.java)
                return@forEach
            }
            if (value.isJsonObject) {
                mappings[key] = gson.fromJson(value, ConcurrentHashMap::class.java)
                return@forEach
            }
            mappings[key] = value.asString
        }
    }

    fun refresh() {
        snapBuildNumber = getSnapchatVersionCode()
        val mapper = Mapper(*mappers)

        runCatching {
            mapper.loadApk(getApplicationSourceDir() ?: throw Exception("Failed to get APK"))
        }.onFailure {
            throw Exception("Failed to load APK", it)
        }

        measureTimeMillis {
            val result = mapper.start().apply {
                addProperty("snap_build_number", snapBuildNumber)
            }
            write(result.toString().toByteArray())
        }.also {
            Logger.debug("Generated mappings in $it ms")
        }
    }

    fun getMappedObject(key: String): Any {
        if (mappings.containsKey(key)) {
            return mappings[key]!!
        }
        throw Exception("No mapping found for $key")
    }

    fun getMappedObjectNullable(key: String): Any? {
        return mappings[key]
    }

    fun getMappedClass(className: String): Class<*> {
        return context.classLoader.loadClass(getMappedObject(className) as String)
    }

    fun getMappedClass(key: String, subKey: String): Class<*> {
        return context.classLoader.loadClass(getMappedValue(key, subKey))
    }

    fun getMappedValue(key: String): String {
        return getMappedObject(key) as String
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getMappedList(key: String): List<T> {
        return listOf(getMappedObject(key) as List<T>).flatten()
    }

    fun getMappedValue(key: String, subKey: String): String {
        return getMappedMap(key)[subKey] as String
    }

    @Suppress("UNCHECKED_CAST")
    fun getMappedMap(key: String): Map<String, *> {
        return getMappedObject(key) as Map<String, *>
    }
}
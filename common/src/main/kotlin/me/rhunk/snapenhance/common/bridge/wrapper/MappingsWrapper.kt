package me.rhunk.snapenhance.common.bridge.wrapper

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import me.rhunk.snapenhance.common.BuildConfig
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.bridge.FileLoaderWrapper
import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import me.rhunk.snapenhance.mapper.Mapper
import me.rhunk.snapenhance.mapper.impl.*
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
    }

    private lateinit var context: Context

    private val mappings = ConcurrentHashMap<String, Any>()
    private var mappingUniqueHash: Long = 0

    private fun getUniqueBuildId() = (getSnapchatPackageInfo()?.longVersionCode ?: -1) xor BuildConfig.BUILD_HASH.hashCode().toLong()

    fun init(context: Context) {
        this.context = context
        mappingUniqueHash = getUniqueBuildId()

        if (isFileExists()) {
            runCatching {
                loadCached()
            }.onFailure {
                delete()
            }
        }
    }

    fun getSnapchatPackageInfo() = runCatching {
        context.packageManager.getPackageInfo(
            Constants.SNAPCHAT_PACKAGE_NAME,
            0
        )
    }.getOrNull()

    fun getGeneratedBuildNumber() = mappingUniqueHash
    fun isMappingsOutdated() = mappingUniqueHash != getUniqueBuildId() || isMappingsLoaded().not()
    fun isMappingsLoaded() = mappings.isNotEmpty()

    private fun loadCached() {
        if (!isFileExists()) {
            throw Exception("Mappings file does not exist")
        }
        val mappingsObject = JsonParser.parseString(read().toString(Charsets.UTF_8)).asJsonObject.also {
            mappingUniqueHash = it["unique_hash"].asLong
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
        mappingUniqueHash = getUniqueBuildId()
        val mapper = Mapper(*mappers)

        runCatching {
            mapper.loadApk(getSnapchatPackageInfo()?.applicationInfo?.sourceDir ?: throw Exception("Failed to get APK"))
        }.onFailure {
            throw Exception("Failed to load APK", it)
        }

        measureTimeMillis {
            val result = mapper.start().apply {
                addProperty("unique_hash", mappingUniqueHash)
            }
            write(result.toString().toByteArray())
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
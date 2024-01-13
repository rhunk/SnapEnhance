package me.rhunk.snapenhance.core.features.impl

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.mapper.impl.OperaViewerParamsMapper

class OperaViewerParamsOverride : Feature("OperaViewerParamsOverride", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    data class OverrideKey(
        val name: String,
        val defaultValue: Any?
    )

    data class Override(
        val filter: (value: Any?) -> Boolean,
        val value: (key: OverrideKey, value: Any?) -> Any?
    )

    override fun onActivityCreate() {
        val overrideMap = mutableMapOf<String, Override>()

        fun overrideParam(key: String, filter: (value: Any?) -> Boolean, value: (overrideKey: OverrideKey, value: Any?) -> Any?) {
            overrideMap[key] = Override(filter, value)
        }

        if (context.config.messaging.loopMediaPlayback.get()) {
            //https://github.com/rodit/SnapMod/blob/master/app/src/main/java/xyz/rodit/snapmod/features/opera/SnapDurationModifier.kt
            overrideParam("auto_advance_mode", { true }, { key, _ -> key.defaultValue })
            overrideParam("auto_advance_max_loop_number", { true }, { _, _ -> Int.MAX_VALUE })
            overrideParam("media_playback_mode", { true }, { _, value ->
                val playbackMode = value ?: return@overrideParam null
                playbackMode::class.java.enumConstants.firstOrNull {
                    it.toString() == "LOOPING"
                } ?: return@overrideParam value
            })
        }

        context.mappings.useMapper(OperaViewerParamsMapper::class) {
            classReference.get()?.hook(putMethod.get()!!, HookStage.BEFORE) { param ->
                val key = param.argNullable<Any>(0)?.let {  key ->
                    val fields = key::class.java.fields
                    OverrideKey(
                        name = fields.firstOrNull {
                            it.type == String::class.java
                        }?.get(key)?.toString() ?: return@hook,
                        defaultValue = fields.firstOrNull {
                            it.type == Object::class.java
                        }?.get(key)
                    )
                } ?: return@hook
                val value = param.argNullable<Any>(1) ?: return@hook

                overrideMap[key.name]?.let { override ->
                    if (override.filter(value)) {
                        runCatching {
                            param.setArg(1, override.value(key, value))
                        }.onFailure {
                            context.log.error("Failed to override param $key", it)
                        }
                    }
                }
            }
        }
    }
}
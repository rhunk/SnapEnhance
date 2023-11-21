package me.rhunk.snapenhance.core.event.events.impl

import me.rhunk.snapenhance.core.event.events.AbstractHookEvent
import me.rhunk.snapenhance.core.util.hook.HookAdapter
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker
import java.nio.ByteBuffer

class NetworkApiRequestEvent(
    val request: Any,
    val uploadDataProvider: Any?,
    val callback: Any,
    var url: String,
) : AbstractHookEvent() {
    fun addResultHook(methodName: String, stage: HookStage = HookStage.BEFORE, callback: (HookAdapter) -> Unit) {
        Hooker.ephemeralHookObjectMethod(
            this.callback::class.java,
            this.callback,
            methodName,
            stage
        ) { callback.invoke(it) }
    }

    fun onSuccess(callback: HookAdapter.(ByteArray?) -> Unit) {
        addResultHook("onSucceeded") { param ->
            callback.invoke(param, param.argNullable<ByteBuffer>(2)?.let {
                ByteArray(it.capacity()).also { buffer -> it.get(buffer); it.position(0) }
            })
        }
    }

    fun hookRequestBuffer(onRequest: (ByteArray) -> ByteArray) {
        val streamDataProvider = this.uploadDataProvider?.let { provider ->
            provider::class.java.methods.find { it.name == "getUploadStreamDataProvider" }?.invoke(provider)
        } ?: return
        val streamDataProviderMethods = streamDataProvider::class.java.methods

        val originalBufferSize = streamDataProviderMethods.find { it.name == "getLength" }?.invoke(streamDataProvider) as? Long ?: return
        var originalRequestBuffer = ByteArray(originalBufferSize.toInt())
        streamDataProviderMethods.find { it.name == "read" }?.invoke(streamDataProvider, ByteBuffer.wrap(originalRequestBuffer))
        streamDataProviderMethods.find { it.name == "close" }?.invoke(streamDataProvider)

        runCatching {
            originalRequestBuffer = onRequest.invoke(originalRequestBuffer)
        }.onFailure {
            context.log.error("Failed to hook request buffer", it)
        }

        var offset = 0L
        val unhooks = mutableListOf<() -> Unit>()

        fun hookObjectMethod(methodName: String, callback: (HookAdapter) -> Unit) {
            Hooker.hookObjectMethod(
                streamDataProvider::class.java,
                streamDataProvider,
                methodName,
                HookStage.BEFORE
            ) {
                callback.invoke(it)
            }.also { unhooks.addAll(it) }
        }

        hookObjectMethod("getLength") { it.setResult(originalRequestBuffer.size.toLong()) }
        hookObjectMethod("getOffset") { it.setResult(offset) }
        hookObjectMethod("close") { param ->
            unhooks.forEach { it.invoke() }
            param.setResult(null)
        }
        hookObjectMethod("rewind") {
            offset = 0
            it.setResult(true)
        }
        hookObjectMethod("read") { param ->
            val byteBuffer = param.arg<ByteBuffer>(0)
            val length = originalRequestBuffer.size.coerceAtMost(byteBuffer.remaining())
            byteBuffer.put(originalRequestBuffer, offset.toInt(), length)
            offset += length
            param.setResult(byteBuffer.position().toLong())
        }

        Hooker.hookObjectMethod(
            this.uploadDataProvider::class.java,
            this.uploadDataProvider,
            "getUploadStreamDataProvider",
            HookStage.BEFORE
        ) {
            if (it.nullableThisObject<Any>() != this.uploadDataProvider) return@hookObjectMethod
            it.setResult(streamDataProvider)
        }.also {
            unhooks.addAll(it)
        }
    }
}
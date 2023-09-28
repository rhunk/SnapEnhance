package me.rhunk.snapenhance.core.event

import me.rhunk.snapenhance.ModContext
import kotlin.reflect.KClass

abstract class Event {
    lateinit var context: ModContext
    var canceled = false
}

interface IListener<T> {
    fun handle(event: T)
}

class EventBus(
    val context: ModContext
) {
    private val subscribers = mutableMapOf<KClass<out Event>, MutableList<IListener<out Event>>>()

    fun <T : Event> subscribe(event: KClass<T>, listener: IListener<T>) {
        if (!subscribers.containsKey(event)) {
            subscribers[event] = mutableListOf()
        }
        subscribers[event]!!.add(listener)
    }

    inline fun <T : Event> subscribe(event: KClass<T>, crossinline listener: (T) -> Unit) = subscribe(event, { true }, listener)

    inline fun <T : Event> subscribe(event: KClass<T>, crossinline filter: (T) -> Boolean, crossinline listener: (T) -> Unit): () -> Unit {
        val obj = object : IListener<T> {
            override fun handle(event: T) {
                if (!filter(event)) return
                runCatching {
                    listener(event)
                }.onFailure {
                    context.log.error("Error while handling event ${event::class.simpleName}", it)
                }
            }
        }
        subscribe(event, obj)
        return { unsubscribe(event, obj) }
    }

    fun <T : Event> unsubscribe(event: KClass<T>, listener: IListener<T>) {
        if (!subscribers.containsKey(event)) {
            return
        }
        subscribers[event]!!.remove(listener)
    }

    fun <T : Event> post(event: T, afterBlock: T.() -> Unit = {}): T? {
        if (!subscribers.containsKey(event::class)) {
            return null
        }

        event.context = context

        subscribers[event::class]?.toTypedArray()?.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            runCatching {
                (listener as IListener<T>).handle(event)
            }.onFailure { t ->
                context.log.error("Error while handling event ${event::class.simpleName} by ${listener::class.simpleName}", t)
            }
        }
        afterBlock(event)
        return event
    }

    fun clear() {
        subscribers.clear()
    }
}
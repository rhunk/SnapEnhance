package me.rhunk.snapenhance.core.event

import me.rhunk.snapenhance.core.ModContext
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
    private val subscribers = mutableMapOf<KClass<out Event>, MutableMap<Int, IListener<out Event>>>()

    fun <T : Event> subscribe(event: KClass<T>, listener: IListener<T>, priority: Int? = null) {
        synchronized(subscribers) {
            if (!subscribers.containsKey(event)) {
                subscribers[event] = sortedMapOf()
            }
            val lastSubscriber = subscribers[event]?.keys?.lastOrNull() ?: 0
            subscribers[event]?.put(priority ?: (lastSubscriber + 1), listener)
        }
    }

    inline fun <T : Event> subscribe(event: KClass<T>, priority: Int? = null, crossinline listener: (T) -> Unit) = subscribe(event, { true }, priority, listener)

    inline fun <T : Event> subscribe(event: KClass<T>,  crossinline filter: (T) -> Boolean, priority: Int? = null, crossinline listener: (T) -> Unit): () -> Unit {
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
        subscribe(event, obj, priority)
        return { unsubscribe(event, obj) }
    }

    fun <T : Event> unsubscribe(event: KClass<T>, listener: IListener<T>) {
        synchronized(subscribers) {
            subscribers[event]?.values?.remove(listener)
        }
    }

    fun <T : Event> post(event: T, afterBlock: T.() -> Unit = {}): T? {
        if (!subscribers.containsKey(event::class)) {
            return null
        }

        event.context = context

        subscribers[event::class]?.toSortedMap()?.forEach { (_, listener) ->
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
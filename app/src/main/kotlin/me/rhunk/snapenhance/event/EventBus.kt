package me.rhunk.snapenhance.event

import me.rhunk.snapenhance.ModContext
import kotlin.reflect.KClass

abstract class Event {
    lateinit var context: ModContext
}

interface IListener<T> {
    fun handle(event: T)
}

class EventBus(
    private val context: ModContext
) {
    private val subscribers = mutableMapOf<KClass<out Event>, MutableList<IListener<out Event>>>()

    fun <T : Event> subscribe(event: KClass<T>, listener: IListener<T>) {
        if (!subscribers.containsKey(event)) {
            subscribers[event] = mutableListOf()
        }
        subscribers[event]!!.add(listener)
    }

    fun <T : Event> subscribe(event: KClass<T>, listener: (T) -> Unit) {
        subscribe(event, object : IListener<T> {
            override fun handle(event: T) {
                listener(event)
            }
        })
    }

    fun <T : Event> unsubscribe(event: KClass<T>, listener: IListener<T>) {
        if (!subscribers.containsKey(event)) {
            return
        }
        subscribers[event]!!.remove(listener)
    }

    fun <T : Event> post(event: T) {
        if (!subscribers.containsKey(event::class)) {
            return
        }

        event.context = context

        subscribers[event::class]!!.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            try {
                (listener as IListener<T>).handle(event)
            } catch (t: Throwable) {
                println("Error while handling event ${event::class.simpleName} by ${listener::class.simpleName}")
                t.printStackTrace()
            }
        }
    }

    fun clear() {
        subscribers.clear()
    }
}
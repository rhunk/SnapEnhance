package me.rhunk.snapmapper

import kotlin.reflect.KClass

abstract class AbstractClassMapper(
   vararg val dependsOn: KClass<out AbstractClassMapper> = arrayOf()
) {
    abstract fun run(context: MapperContext)
}
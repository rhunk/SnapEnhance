package me.rhunk.snapenhance.mapper

abstract class AbstractClassMapper {
    private val mappers = mutableListOf<MapperContext.() -> Unit>()

    fun mapper(task: MapperContext.() -> Unit) {
        mappers.add(task)
    }

    fun run(context: MapperContext) {
        mappers.forEach { it(context) }
    }
}
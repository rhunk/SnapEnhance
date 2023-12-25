package me.rhunk.snapenhance.common.scripting.ui.components

open class Node(
    val type: NodeType,
) {
    lateinit var uiChangeDetection: (key: String, value: Any?) -> Unit

    val children = mutableListOf<Node>()
    val attributes = object: HashMap<String, Any?>() {
        override fun put(key: String, value: Any?): Any? {
            return super.put(key, value).also {
                if (::uiChangeDetection.isInitialized) {
                    uiChangeDetection(key, value)
                }
            }
        }
    }

    fun setAttribute(key: String, value: Any?) {
        attributes[key] = value
    }

    fun fillMaxWidth(): Node {
        attributes["fillMaxWidth"] = true
        return this
    }

    fun fillMaxHeight(): Node {
        attributes["fillMaxHeight"] = true
        return this
    }

    fun label(text: String): Node {
        attributes["label"] = text
        return this
    }

    fun padding(padding: Int): Node {
        attributes["padding"] = padding
        return this
    }

    fun fontSize(size: Int): Node {
        attributes["fontSize"] = size
        return this
    }

    fun color(color: Long): Node {
        attributes["color"] = color
        return this
    }
}

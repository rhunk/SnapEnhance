package me.rhunk.snapenhance.common.scripting.ui.components.impl

import me.rhunk.snapenhance.common.scripting.ui.components.Node
import me.rhunk.snapenhance.common.scripting.ui.components.NodeType

class TextInputNode : Node(NodeType.TEXT_INPUT) {
    fun placeholder(text: String): TextInputNode {
        attributes["placeholder"] = text
        return this
    }

    fun value(text: String): TextInputNode {
        attributes["value"] = text
        return this
    }

    fun callback(callback: (String) -> Unit): TextInputNode {
        attributes["callback"] = callback
        return this
    }

    fun readonly(state: Boolean): TextInputNode {
        attributes["readonly"] = state
        return this
    }

    fun singleLine(state: Boolean): TextInputNode {
        attributes["singleLine"] = state
        return this
    }

    fun maxLines(maxLines: Int): TextInputNode {
        attributes["maxLines"] = maxLines
        return this
    }
}
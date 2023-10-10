package me.rhunk.snapenhance.scripting.impl.ui

import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.scripting.impl.ui.components.Node
import me.rhunk.snapenhance.scripting.impl.ui.components.NodeType
import me.rhunk.snapenhance.scripting.impl.ui.components.impl.RowColumnNode
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction


class InterfaceBuilder {
    val nodes = mutableListOf<Node>()
    var onLaunchedCallback: (() -> Unit)? = null


    private fun createNode(type: NodeType, block: Node.() -> Unit): Node {
        return Node(type).apply(block).also { nodes.add(it) }
    }

    fun onLaunched(block: () -> Unit) {
        onLaunchedCallback = block
    }

    fun row(block: (InterfaceBuilder) -> Unit) = RowColumnNode(NodeType.ROW).apply {
        children.addAll(InterfaceBuilder().apply(block).nodes)
    }.also { nodes.add(it) }

    fun column(block: (InterfaceBuilder) -> Unit) = RowColumnNode(NodeType.COLUMN).apply {
        children.addAll(InterfaceBuilder().apply(block).nodes)
    }.also { nodes.add(it) }

    fun text(text: String) = createNode(NodeType.TEXT) {
        label(text)
    }

    fun switch(state: Boolean?, callback: (Boolean) -> Unit) = createNode(NodeType.SWITCH) {
        attributes["state"] = state
        attributes["callback"] = callback
    }

    fun button(label: String, callback: () -> Unit) = createNode(NodeType.BUTTON) {
        label(label)
        attributes["callback"] = callback
    }

    fun slider(min: Int, max: Int, step: Int, value: Int, callback: (Int) -> Unit) = createNode(
        NodeType.SLIDER
    ) {
        attributes["value"] = value
        attributes["min"] = min
        attributes["max"] = max
        attributes["step"] = step
        attributes["callback"] = callback
    }

    fun list(label: String, items: List<String>, callback: (String) -> Unit) = createNode(NodeType.LIST) {
        label(label)
        attributes["items"] = items
        attributes["callback"] = callback
    }
}



class InterfaceManager(
    private val moduleInfo: ModuleInfo,
    private val logger: AbstractLogger,
    private val registerInterface: (String, InterfaceBuilder) -> Unit,
) {
    @JSFunction
    fun create(name: String, callback: Function) {
        logger.info("Creating interface $name for ${moduleInfo.name}")
        val interfaceBuilder = InterfaceBuilder()
        callback.call(Context.getCurrentContext(), callback, callback, arrayOf(interfaceBuilder))
        registerInterface(name, interfaceBuilder)
    }
}
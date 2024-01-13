package me.rhunk.snapenhance.common.scripting.ui

import android.app.Activity
import android.app.AlertDialog
import androidx.compose.runtime.remember
import me.rhunk.snapenhance.common.scripting.bindings.AbstractBinding
import me.rhunk.snapenhance.common.scripting.bindings.BindingSide
import me.rhunk.snapenhance.common.scripting.ktx.contextScope
import me.rhunk.snapenhance.common.scripting.ktx.scriptableObject
import me.rhunk.snapenhance.common.scripting.ui.components.Node
import me.rhunk.snapenhance.common.scripting.ui.components.NodeType
import me.rhunk.snapenhance.common.scripting.ui.components.impl.ActionNode
import me.rhunk.snapenhance.common.scripting.ui.components.impl.ActionType
import me.rhunk.snapenhance.common.scripting.ui.components.impl.RowColumnNode
import me.rhunk.snapenhance.common.ui.createComposeAlertDialog
import org.mozilla.javascript.Function
import org.mozilla.javascript.annotations.JSFunction


class InterfaceBuilder {
    val nodes = mutableListOf<Node>()
    var onDisposeCallback: (() -> Unit)? = null

    private fun createNode(type: NodeType, block: Node.() -> Unit): Node {
        return Node(type).apply(block).also { nodes.add(it) }
    }

    fun onDispose(block: () -> Unit) {
        nodes.add(ActionNode(ActionType.DISPOSE, callback = block))
    }

    fun onLaunched(block: () -> Unit) {
        onLaunched(Unit, block)
    }

    fun onLaunched(key: Any, block: () -> Unit) {
        nodes.add(ActionNode(ActionType.LAUNCHED, key, block))
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



@Suppress("unused")
class InterfaceManager : AbstractBinding("interface-manager", BindingSide.COMMON) {
    private val interfaces = mutableMapOf<String, (args: Map<String, Any?>) -> InterfaceBuilder?>()

    fun buildInterface(scriptInterface: EnumScriptInterface, args: Map<String, Any?> = emptyMap()): InterfaceBuilder? {
        return runCatching {
            interfaces[scriptInterface.key]?.invoke(args)
        }.onFailure {
            context.runtime.logger.error("Failed to build interface ${scriptInterface.key} for ${context.moduleInfo.name}", it)
        }.getOrNull()
    }

    override fun onDispose() {
        interfaces.clear()
    }

    fun hasInterface(scriptInterfaces: EnumScriptInterface): Boolean {
        return interfaces.containsKey(scriptInterfaces.key)
    }

    @JSFunction fun create(name: String, callback: Function) {
        interfaces[name] = { args ->
            val interfaceBuilder = InterfaceBuilder()
            runCatching {
                contextScope {
                    callback.call(this, callback, callback, arrayOf(interfaceBuilder, scriptableObject {
                        args.forEach { (key, value) ->
                            putConst(key,this, value)
                        }
                    }))
                }
                interfaceBuilder
            }.onFailure {
                context.runtime.logger.error("Failed to create interface $name for ${context.moduleInfo.name}", it)
            }.getOrNull()
        }
    }

    @JSFunction fun createAlertDialog(activity: Activity, builder: (AlertDialog.Builder) -> Unit, callback: (interfaceBuilder: InterfaceBuilder, alertDialog: AlertDialog) -> Unit): AlertDialog {
        return createComposeAlertDialog(activity, builder = builder) { alertDialog ->
            ScriptInterface(interfaceBuilder = remember {
                InterfaceBuilder().also {
                    contextScope {
                        callback(it, alertDialog)
                    }
                }
            })
        }
    }

    @JSFunction fun createAlertDialog(activity: Activity, callback: (interfaceBuilder: InterfaceBuilder, alertDialog: AlertDialog) -> Unit): AlertDialog {
        return createAlertDialog(activity, {}, callback)
    }

    override fun getObject() = this
}
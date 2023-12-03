package me.rhunk.snapenhance.scripting.impl.ui.components.impl

import me.rhunk.snapenhance.scripting.impl.ui.components.Node
import me.rhunk.snapenhance.scripting.impl.ui.components.NodeType

enum class ActionType {
    LAUNCHED,
    DISPOSE
}

class ActionNode(
    val actionType: ActionType,
    val key: Any = Unit,
    val callback: () -> Unit
): Node(NodeType.ACTION)
package me.rhunk.snapenhance.common.scripting.ui.components.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import me.rhunk.snapenhance.common.scripting.ui.components.Node
import me.rhunk.snapenhance.common.scripting.ui.components.NodeType


class RowColumnNode(
    type: NodeType,
) : Node(type) {
    companion object {
        private val arrangements = mapOf(
            "start" to Arrangement.Start,
            "end" to Arrangement.End,
            "top" to Arrangement.Top,
            "bottom" to Arrangement.Bottom,
            "center" to Arrangement.Center,
            "spaceBetween" to Arrangement.SpaceBetween,
            "spaceAround" to Arrangement.SpaceAround,
            "spaceEvenly" to Arrangement.SpaceEvenly,
        )
        private val alignments = mapOf(
            "start" to Alignment.Start,
            "end" to Alignment.End,
            "top" to Alignment.Top,
            "bottom" to Alignment.Bottom,
            "centerVertically" to Alignment.CenterVertically,
            "centerHorizontally" to Alignment.CenterHorizontally,
        )
    }

    fun arrangement(arrangement: String): RowColumnNode {
        attributes["arrangement"] = arrangements[arrangement] ?: throw IllegalArgumentException("Invalid arrangement")
        return this
    }

    fun alignment(alignment: String): RowColumnNode {
        attributes["alignment"] = alignments[alignment] ?: throw IllegalArgumentException("Invalid alignment")
        return this
    }

    fun spacedBy(spacing: Int): RowColumnNode {
        attributes["spacing"] = spacing
        return this
    }
}
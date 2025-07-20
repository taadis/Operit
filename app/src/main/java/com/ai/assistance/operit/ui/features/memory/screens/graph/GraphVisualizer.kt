package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphVisualizer(graph: Graph, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    var nodePositions by remember { mutableStateOf(mapOf<String, Offset>()) }
    var draggedNode by remember { mutableStateOf<Node?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Initialize positions on first composition
        LaunchedEffect(graph, width, height) {
            if (nodePositions.isEmpty() && graph.nodes.isNotEmpty()) {
                val newPositions = mutableMapOf<String, Offset>()
                val center = Offset(width / 2, height / 2)
                val radius = (width.coerceAtMost(height) / 2) * 0.8f
                graph.nodes.forEachIndexed { index, node ->
                    if (!newPositions.containsKey(node.id)) {
                        // Arrange nodes in a circle
                        val angle = 2 * Math.PI * index / graph.nodes.size
                        val x = center.x + radius * cos(angle).toFloat()
                        val y = center.y + radius * sin(angle).toFloat()
                        newPositions[node.id] = Offset(x, y)
                    }
                }
                nodePositions = newPositions
            }
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val tappedNode = nodePositions.minByOrNull { (_, pos) ->
                            (pos - startOffset).getDistanceSquared()
                        }?.key
                        
                        draggedNode = graph.nodes.find { it.id == tappedNode }
                    },
                    onDragEnd = {
                        draggedNode = null
                    },
                    onDrag = { change, dragAmount ->
                        draggedNode?.let { node ->
                            nodePositions = nodePositions.toMutableMap().apply {
                                val currentPos = this[node.id] ?: Offset.Zero
                                this[node.id] = currentPos + dragAmount
                            }
                        }
                        change.consume()
                    }
                )
            }
        ) {
            // Draw edges
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceId]
                val targetPos = nodePositions[edge.targetId]
                if (sourcePos != null && targetPos != null) {
                    drawLine(
                        color = Color.Gray,
                        start = sourcePos,
                        end = targetPos,
                        strokeWidth = 2f
                    )
                }
            }

            // Draw nodes
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    drawNode(node, position, textMeasurer)
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNode(
    node: Node,
    position: Offset,
    textMeasurer: TextMeasurer
) {
    val nodeRadius = 60f
    drawCircle(
        color = node.color,
        radius = nodeRadius,
        center = position
    )
    
    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(node.label),
        style = TextStyle(fontSize = 12.sp, color = Color.Black)
    )
    
    val textPosition = Offset(
        x = position.x - textLayoutResult.size.width / 2,
        y = position.y - textLayoutResult.size.height / 2
    )
    
    drawText(textLayoutResult, topLeft = textPosition)
} 
package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphVisualizer(
    graph: Graph,
    modifier: Modifier = Modifier,
    selectedNodeId: String? = null,
    onNodeClick: (Node) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var nodePositions by remember { mutableStateOf(mapOf<String, Offset>()) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        LaunchedEffect(graph, width, height) {
            if (nodePositions.isEmpty() && graph.nodes.isNotEmpty()) {
                val newPositions = mutableMapOf<String, Offset>()
                val center = Offset(width / 2, height / 2)
                val radius = (width.coerceAtMost(height) / 2) * 0.8f
                graph.nodes.forEachIndexed { index, node ->
                    if (!newPositions.containsKey(node.id)) {
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
                coroutineScope {
                    launch {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                            // The transformation model is: view_pos = graph_pos * scale + offset
                            offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                            scale = newScale
                        }
                    }
                    launch {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val clickedNode = graph.nodes.find { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        val distanceInView = (tapOffset - viewPos).getDistance()
                                        val radiusInView = 60f * scale
                                        distanceInView <= radiusInView
                                    } ?: false
                                }
                                clickedNode?.let(onNodeClick)
                            }
                        )
                    }
                }
            }
        ) {
            // Edges
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceId]
                val targetPos = nodePositions[edge.targetId]
                if (sourcePos != null && targetPos != null) {
                    drawLine(
                        color = Color.Gray,
                        start = sourcePos * scale + offset,
                        end = targetPos * scale + offset,
                        strokeWidth = 2f
                    )
                }
            }

            // Nodes
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    val isSelected = node.id == selectedNodeId
                    drawNode(
                        node = node,
                        position = position * scale + offset, // Apply transform here
                        radius = 60f * scale, // Apply scale to radius
                        textMeasurer = textMeasurer,
                        isSelected = isSelected
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNode(
    node: Node,
    position: Offset,
    radius: Float,
    textMeasurer: TextMeasurer,
    isSelected: Boolean
) {
    val color = if (isSelected) Color.Yellow else node.color
    drawCircle(
        color = color,
        radius = radius,
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
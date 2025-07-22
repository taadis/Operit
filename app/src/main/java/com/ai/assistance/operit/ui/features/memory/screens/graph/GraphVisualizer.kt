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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
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

        // Force-directed layout simulation
        LaunchedEffect(graph.nodes, width, height) {
            if (graph.nodes.isNotEmpty()) {
                // Initialize positions randomly across the canvas to avoid "explosion"
                val initialPositions = mutableMapOf<String, Offset>()
                val center = Offset(width / 2, height / 2)
                graph.nodes.forEach { node ->
                    initialPositions[node.id] = Offset(
                        (Math.random() * width).toFloat(),
                        (Math.random() * height).toFloat()
                    )
                }
                withContext(Dispatchers.Main) {
                    nodePositions = initialPositions
                }

                // Run simulation in a background coroutine
                launch(Dispatchers.Default) {
                    val positions = initialPositions.toMutableMap()
                    val velocities = mutableMapOf<String, Offset>()
                    graph.nodes.forEach { node -> velocities[node.id] = Offset.Zero }

                    // Tuned parameters for a more stable and visually pleasing layout
                    val iterations = 300
                    val repulsionStrength = 150000f  // Stronger repulsion to prevent clumping
                    val attractionStrength = 0.05f   // Weaker spring force
                    val idealEdgeLength = 300f       // Longer ideal distance for edges
                    val gravityStrength = 0.02f      // Weaker gravity to allow graph to spread out
                    val maxSpeed = 50f
                    val damping = 0.95f

                    for (i in 0 until iterations) {
                        val forces = mutableMapOf<String, Offset>()
                        graph.nodes.forEach { node -> forces[node.id] = Offset.Zero }

                        // Repulsive forces between all pairs of nodes
                        for (n1 in graph.nodes) {
                            for (n2 in graph.nodes) {
                                if (n1.id == n2.id) continue
                                val p1 = positions[n1.id] ?: continue
                                val p2 = positions[n2.id] ?: continue
                                val delta = p1 - p2
                                val distance = delta.getDistance().coerceAtLeast(1f)
                                val force = repulsionStrength / (distance * distance)
                                val direction = if (distance > 0) delta / distance else Offset.Zero
                                forces[n1.id] = forces[n1.id]!! + direction * force
                            }
                        }

                        // Attractive forces (spring model) along edges
                        for (edge in graph.edges) {
                            val sourcePos = positions[edge.sourceId]
                            val targetPos = positions[edge.targetId]
                            if (sourcePos != null && targetPos != null) {
                                val delta = targetPos - sourcePos
                                val distance = delta.getDistance().coerceAtLeast(1f)
                                // Spring force: k * (x - ideal_length)
                                val force = attractionStrength * (distance - idealEdgeLength)
                                val direction = if (distance > 0) delta / distance else Offset.Zero
                                forces[edge.sourceId] = forces[edge.sourceId]!! + direction * force
                                forces[edge.targetId] = forces[edge.targetId]!! - direction * force
                            }
                        }
                        
                        // Gravity force towards the center
                        for (node in graph.nodes) {
                            val p = positions[node.id] ?: continue
                            val delta = center - p
                            val distance = delta.getDistance().coerceAtLeast(1f)
                            val direction = if (distance > 0) delta / distance else Offset.Zero
                            forces[node.id] = forces[node.id]!! + direction * gravityStrength * distance
                        }

                        // Update positions based on forces
                        for (node in graph.nodes) {
                            val nodeForce = forces[node.id]!!
                            var nodeVelocity = velocities[node.id]!!
                            nodeVelocity = (nodeVelocity + nodeForce) * damping

                            val speed = nodeVelocity.getDistance()
                            if (speed > maxSpeed) {
                                nodeVelocity = nodeVelocity * (maxSpeed / speed)
                            }

                            velocities[node.id] = nodeVelocity
                            positions[node.id] = positions[node.id]!! + nodeVelocity
                        }

                        // Update UI
                        withContext(Dispatchers.Main) {
                            nodePositions = positions.toMap()
                        }
                        delay(16)
                    }
                }
            } else {
                 withContext(Dispatchers.Main) {
                    nodePositions = emptyMap()
                }
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
                            offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                            scale = newScale
                        }
                    }
                    launch {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val clickedNode = graph.nodes.findLast { node -> // findLast to prioritize top node
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
                    val start = sourcePos * scale + offset
                    val end = targetPos * scale + offset
                    
                    drawLine(
                        color = Color.Gray,
                        start = start,
                        end = end,
                        strokeWidth = (edge.weight * 2f).coerceIn(1f, 8f)
                    )
                    
                    edge.label?.let { label ->
                        val center = (start + end) / 2f
                        val angle = atan2(end.y - start.y, end.x - start.x)
                        
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(label),
                            style = TextStyle(fontSize = 10.sp, color = Color.DarkGray)
                        )
                        
                        // Simple collision avoidance for label, could be improved
                        val labelOffset = if (angle > -Math.PI / 2 && angle < Math.PI / 2) -textLayoutResult.size.height.toFloat() else 0f
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = center.x - textLayoutResult.size.width / 2,
                                y = center.y + labelOffset
                            )
                        )
                    }
                }
            }

            // Nodes
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    val isSelected = node.id == selectedNodeId
                    drawNode(
                        node = node,
                        position = position * scale + offset,
                        radius = 60f * scale,
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
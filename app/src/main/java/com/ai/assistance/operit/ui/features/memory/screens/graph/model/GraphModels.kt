package com.ai.assistance.operit.ui.features.memory.screens.graph.model

import androidx.compose.ui.graphics.Color

data class Graph(
    val nodes: List<Node>,
    val edges: List<Edge>
)

data class Node(
    val id: String,
    val label: String,
    val color: Color = Color.LightGray
)

data class Edge(
    val sourceId: String,
    val targetId: String,
    val label: String? = null
) 
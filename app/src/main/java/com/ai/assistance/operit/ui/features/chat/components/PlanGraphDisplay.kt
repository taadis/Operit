package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.PlanItemStatus

/** Component to display AI plan items as a connected graph of nodes */
@Composable
fun PlanGraphDisplay(planItems: List<PlanItem>, modifier: Modifier = Modifier) {
        if (planItems.isEmpty()) return

        var expanded by remember { mutableStateOf(true) }
        val completedCount = planItems.count { it.status == PlanItemStatus.COMPLETED }
        val totalCount = planItems.size
        val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

        // Animation for progress
        val animatedProgress by
                animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(durationMillis = 500)
                )

        // 获取主题颜色 - 在Composable中获取
        val primaryColor = MaterialTheme.colorScheme.primary
        val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
        val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
        val onSecondaryContainerColor = MaterialTheme.colorScheme.onSecondaryContainer
        val errorColor = MaterialTheme.colorScheme.error
        val errorContainerColor = MaterialTheme.colorScheme.errorContainer
        val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer
        val outlineColor = MaterialTheme.colorScheme.outline
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

        // 创建状态颜色映射 - 根据项目状态使用不同的主题颜色
        val todoColors =
                NodeColors(
                        backgroundColor = Color.White,
                        accentColor = outlineColor,
                        textColor = onSurfaceColor
                )

        val inProgressColors =
                NodeColors(
                        backgroundColor = primaryContainerColor,
                        accentColor = primaryColor,
                        textColor = onPrimaryContainerColor
                )

        val completedColors =
                NodeColors(
                        backgroundColor = secondaryContainerColor,
                        accentColor = secondaryColor,
                        textColor = onSecondaryContainerColor
                )

        val failedColors =
                NodeColors(
                        backgroundColor = errorContainerColor,
                        accentColor = errorColor,
                        textColor = onErrorContainerColor
                )

        val cancelledColors =
                NodeColors(
                        backgroundColor = Color.White,
                        accentColor = outlineColor,
                        textColor = onSurfaceVariantColor
                )

        Card(
                modifier = modifier.fillMaxWidth().padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                // Header with progress and expand/collapse button
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { expanded = !expanded }
                                        .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Filled.Timeline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                        text = "AI 工作流程图 (${completedCount}/${totalCount})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Icon(
                                imageVector =
                                        if (expanded) Icons.Filled.ExpandLess
                                        else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                }

                // Progress indicator
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(4.dp)
                                        .padding(horizontal = 12.dp)
                                        .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(2.dp)
                                        )
                ) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth(animatedProgress)
                                                .fillMaxHeight()
                                                .background(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(2.dp)
                                                )
                        )
                }

                // Calculate height based on number of items and flowchart style
                val heightInDp =
                        with(LocalDensity.current) {
                                // 为工作流设置合适的大小，特别是当节点变大时
                                val baseHeight = 160.dp  // 大幅减小基础高度
                                val calculatedHeight =
                                        if (planItems.size > 3) baseHeight + 50.dp
                                        else baseHeight  // 减小额外高度
                                if (calculatedHeight > 280.dp) 280.dp
                                else calculatedHeight  // 大幅减小最大高度
                        }

                // Plan items graph visualization
                AnimatedVisibility(
                        visible = expanded,
                        enter =
                                fadeIn(animationSpec = tween(300)) +
                                        expandVertically(animationSpec = tween(300)),
                        exit =
                                fadeOut(animationSpec = tween(300)) +
                                        shrinkVertically(animationSpec = tween(300))
                ) {
                        Box(modifier = Modifier.fillMaxWidth().height(heightInDp).padding(8.dp)) {
                                WorkflowGraph(
                                        planItems = planItems,
                                        modifier = Modifier.fillMaxSize(),
                                        primaryColor = primaryColor,
                                        todoColors = todoColors,
                                        inProgressColors = inProgressColors,
                                        completedColors = completedColors,
                                        failedColors = failedColors,
                                        cancelledColors = cancelledColors
                                )
                        }
                }
        }
}

/** 保存节点颜色的数据类 */
private data class NodeColors(
        val backgroundColor: Color,
        val accentColor: Color,
        val textColor: Color
)

/** Composable for rendering the workflow graph visualization of plan items */
@Composable
private fun WorkflowGraph(
        planItems: List<PlanItem>,
        modifier: Modifier = Modifier,
        primaryColor: Color,
        todoColors: NodeColors,
        inProgressColors: NodeColors,
        completedColors: NodeColors,
        failedColors: NodeColors,
        cancelledColors: NodeColors
) {
        // Technical state for graph rendering
        var scale by remember { mutableStateOf(0.7f) } // 减小初始缩放比例
        var offset by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        val textMeasurer = rememberTextMeasurer()

        // Remember last set of plan items to animate transitions
        val previousPlanItems = remember { mutableStateOf<List<PlanItem>>(emptyList()) }

        // Track which items have changed status for animations
        val animatingItems = remember { mutableStateOf<Map<String, PlanItemStatus>>(emptyMap()) }

        // Update animations when plan items change
        LaunchedEffect(planItems) {
                val changedItems = mutableMapOf<String, PlanItemStatus>()

                // Find items that changed status
                planItems.forEach { newItem ->
                        val oldItem = previousPlanItems.value.find { it.id == newItem.id }
                        if (oldItem != null && oldItem.status != newItem.status) {
                                changedItems[newItem.id] = newItem.status
                        }
                }

                // Update state for animations
                animatingItems.value = changedItems

                // After a delay, clear the animations
                kotlinx.coroutines.delay(800)
                animatingItems.value = emptyMap()

                // Update previous items
                previousPlanItems.value = planItems
        }

        // Animation for new nodes appearing
        val nodeAppearance = remember { Animatable(0f) }

        LaunchedEffect(planItems.size) {
                if (planItems.size > previousPlanItems.value.size) {
                        nodeAppearance.snapTo(0f)
                        nodeAppearance.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(700, easing = FastOutSlowInEasing)
                        )
                }
        }

        // Sort plan items by creation time/id to ensure proper workflow order
        val sortedItems = remember(planItems) { planItems.sortedBy { it.createdAt } }

        // Calculate node positions for the workflow
        val nodes = remember(sortedItems, size) { calculateWorkflowPositions(sortedItems, size) }

        // 值得传递给绘制函数的颜色
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val errorColor = MaterialTheme.colorScheme.error

        Canvas(
                modifier =
                        modifier.onSizeChanged { size = it }.pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 2.5f)
                                        offset += pan
                                }
                        }
        ) {
                // Apply transformations
                scale(scale) {
                        translate(offset.x / scale, offset.y / scale) {
                                // Draw connections between nodes in workflow order
                                drawWorkflowConnections(nodes, primaryColor)

                                // Draw nodes with animations
                                nodes.forEachIndexed { index, (planItem, position, nodeSize) ->
                                        val isAnimating =
                                                animatingItems.value.containsKey(planItem.id)
                                        val nodeScale =
                                                if (planItems.size > previousPlanItems.value.size &&
                                                                !previousPlanItems.value.any {
                                                                        it.id == planItem.id
                                                                }
                                                ) {
                                                        nodeAppearance.value
                                                } else {
                                                        1f
                                                }

                                        // 获取当前节点的颜色方案
                                        val colors =
                                                when (planItem.status) {
                                                        PlanItemStatus.TODO -> todoColors
                                                        PlanItemStatus.IN_PROGRESS ->
                                                                inProgressColors
                                                        PlanItemStatus.COMPLETED -> completedColors
                                                        PlanItemStatus.FAILED -> failedColors
                                                        PlanItemStatus.CANCELLED -> cancelledColors
                                                }

                                        // Apply scaling animation for new nodes
                                        scale(
                                                nodeScale,
                                                pivot =
                                                        Offset(
                                                                position.x + nodeSize.width / 2,
                                                                position.y + nodeSize.height / 2
                                                        )
                                        ) {
                                                // Apply pulse animation for status changes
                                                if (isAnimating) {
                                                        val pulseScale = 1.05f
                                                        scale(
                                                                pulseScale,
                                                                pivot =
                                                                        Offset(
                                                                                position.x +
                                                                                        nodeSize.width /
                                                                                                2,
                                                                                position.y +
                                                                                        nodeSize.height /
                                                                                                2
                                                                        )
                                                        ) {
                                                                drawWorkflowNode(
                                                                        planItem,
                                                                        position,
                                                                        nodeSize,
                                                                        textMeasurer,
                                                                        index + 1,
                                                                        colors,
                                                                        primaryColor,
                                                                        secondaryColor,
                                                                        errorColor,
                                                                        isAnimating,
                                                                        scale
                                                                )
                                                        }
                                                } else {
                                                        drawWorkflowNode(
                                                                planItem,
                                                                position,
                                                                nodeSize,
                                                                textMeasurer,
                                                                index + 1,
                                                                colors,
                                                                primaryColor,
                                                                secondaryColor,
                                                                errorColor,
                                                                isAnimating,
                                                                scale
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }
}

/**
 * Calculate positions for each node in a workflow layout Items are arranged horizontally in a
 * single line
 */
private fun calculateWorkflowPositions(
        items: List<PlanItem>,
        canvasSize: IntSize
): List<Triple<PlanItem, Offset, Size>> {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || items.isEmpty()) {
                return emptyList()
        }

        val result = mutableListOf<Triple<PlanItem, Offset, Size>>()

        // 调整节点尺寸 - 显著增加宽度以确保文字完整显示
        val nodeWidth = 580f // 从460f增加到580f
        val nodeHeight = 180f // 从150f增加到180f

        // 更合理的节点间距 - 确保水平布局有足够间隙显示箭头
        val horizontalSpacing = 120f // 从100f增加到120f，适应更宽的节点

        // 边距
        val sidePadding = 60f

        // 修改布局策略：纯水平排列，不再换行
        // 计算垂直居中的Y坐标
        val centerY =
                if (canvasSize.height > nodeHeight) {
                        (canvasSize.height - nodeHeight) / 2
                } else {
                        20f // 如果画布高度不足，则使用一个基本的顶部边距
                }

        // 起始X位置
        var currentX = sidePadding

        // 所有节点横向排列
        items.forEachIndexed { index, item ->
                result.add(Triple(item, Offset(currentX, centerY), Size(nodeWidth, nodeHeight)))

                // 更新下一个节点的X坐标
                currentX += nodeWidth + horizontalSpacing
        }

        return result
}

/** Draw connections between workflow nodes */
private fun DrawScope.drawWorkflowConnections(
        nodes: List<Triple<PlanItem, Offset, Size>>,
        primaryColor: Color
) {
        if (nodes.size <= 1) return

        // 更明显的箭头
        val arrowWidth = 15f
        val arrowHeight = 10f

        // Connect nodes in sequence
        for (i in 0 until nodes.size - 1) {
                val fromNode = nodes[i]
                val toNode = nodes[i + 1]

                // Calculate connection points based on relative positions
                val fromPos = fromNode.second
                val fromSize = fromNode.third
                val toPos = toNode.second
                val toSize = toNode.third

                // 确定节点是在同一行还是不同行
                val isToNodeBelow = toPos.y > fromPos.y + fromSize.height / 2

                val start: Offset
                val end: Offset
                val controlPoint1: Offset
                val controlPoint2: Offset

                if (isToNodeBelow) {
                        // 如果目标节点在下一行，我们需要垂直+水平连接
                        // 从右下角连到左上角
                        start = Offset(fromPos.x + fromSize.width, fromPos.y + fromSize.height / 2)

                        end = Offset(toPos.x, toPos.y + toSize.height / 2)

                        // 创建一个S形的路径，先向右，再向下，然后向左到目标节点
                        val midX1 = fromPos.x + fromSize.width + 40f
                        val midY =
                                (fromPos.y + fromSize.height / 2 + toPos.y + toSize.height / 2) / 2
                        val midX2 = toPos.x - 40f

                        controlPoint1 = Offset(midX1, fromPos.y + fromSize.height / 2)
                        controlPoint2 = Offset(midX2, toPos.y + toSize.height / 2)
                } else {
                        // 如果目标节点在同一行，使用简单的水平连接
                        start = Offset(fromPos.x + fromSize.width, fromPos.y + fromSize.height / 2)

                        end = Offset(toPos.x, toPos.y + toSize.height / 2)

                        // 水平连接的控制点
                        val midX = (start.x + end.x) / 2
                        controlPoint1 = Offset(midX, start.y)
                        controlPoint2 = Offset(midX, end.y)
                }

                // 创建路径
                val path =
                        Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(
                                        controlPoint1.x,
                                        controlPoint1.y,
                                        controlPoint2.x,
                                        controlPoint2.y,
                                        end.x,
                                        end.y
                                )
                        }

                // 绘制连线
                val lineColor = primaryColor.copy(alpha = 0.7f)
                drawPath(path = path, color = lineColor, style = Stroke(width = 3f))

                // 绘制箭头 - 更明显
                val angle =
                        if (isToNodeBelow) {
                                val dx = end.x - controlPoint2.x
                                val dy = end.y - controlPoint2.y
                                Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                                        .toFloat()
                        } else {
                                0f // 水平箭头
                        }

                // 使用translate和rotate代替withTransform
                translate(end.x, end.y) {
                        rotate(angle) {
                                // 绘制箭头填充三角形
                                val arrowPath =
                                        Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(-arrowWidth, -arrowHeight / 2)
                                                lineTo(-arrowWidth, arrowHeight / 2)
                                                close()
                                        }

                                drawPath(path = arrowPath, color = lineColor)
                        }
                }
        }
}

/** Draw a single workflow node with enhanced visual elements */
private fun DrawScope.drawWorkflowNode(
        planItem: PlanItem,
        position: Offset,
        size: Size,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        stepNumber: Int,
        colors: NodeColors,
        primaryColor: Color,
        secondaryColor: Color,
        errorColor: Color,
        isAnimating: Boolean = false,
        parentScale: Float = 1f
) {
        // 从传入的colors获取颜色
        val bgColor = colors.backgroundColor
        val accentColor = colors.accentColor
        val textColor = colors.textColor

        // 更圆滑的圆角
        val cornerRadius = 24f

        // 根据缩放调整内边距和线条粗细
        val paddingHorizontal = 48f
        val paddingTop = 36f
        val paddingBottom = 36f

        // 绘制阴影 - 更明显
        drawRoundRect(
                color = Color.Black.copy(alpha = 0.1f),
                topLeft = Offset(position.x + 3, position.y + 3),
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // 绘制卡片背景
        drawRoundRect(
                color = bgColor,
                topLeft = position,
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // 绘制边框 - 更突出
        drawRoundRect(
                color = accentColor.copy(alpha = 0.4f),
                topLeft = position,
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = 2f)
        )

        // 动画效果
        if (isAnimating) {
                drawRoundRect(
                        color = accentColor.copy(alpha = 0.5f),
                        topLeft = Offset(position.x - 3, position.y - 3),
                        size = Size(size.width + 6, size.height + 6),
                        cornerRadius = CornerRadius(cornerRadius + 3, cornerRadius + 3),
                        style = Stroke(width = 3f)
                )
        }

        // 绘制标题栏背景 - 新增
        val titleBarHeight = 42f
        drawRoundRect(
                color = accentColor.copy(alpha = 0.1f),
                topLeft = position,
                size = Size(size.width, titleBarHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // 绘制步骤编号 - 完全重新定位，放在左上角标题栏中
        val stepNumberCircleSize = 32f
        val stepNumberX = position.x + paddingHorizontal / 2
        val stepNumberY = position.y + (titleBarHeight - stepNumberCircleSize) / 2

        // 步骤编号背景
        drawCircle(
                color = accentColor,
                radius = stepNumberCircleSize / 2,
                center =
                        Offset(
                                stepNumberX + stepNumberCircleSize / 2,
                                stepNumberY + stepNumberCircleSize / 2
                        )
        )

        // 步骤编号文字 - 使用与缩放无关的字体大小
        val stepNumberFontSize = 15.sp.toPx() // 固定字体大小，不受缩放影响
        val stepNumberString = stepNumber.toString()
        val stepNumberLayoutResult =
                textMeasurer.measure(
                        text = stepNumberString,
                        style =
                                TextStyle(
                                        color = Color.White,
                                        fontSize = stepNumberFontSize.toSp(),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                )
                )

        val stepNumberTextX =
                stepNumberX + (stepNumberCircleSize - stepNumberLayoutResult.size.width) / 2
        val stepNumberTextY =
                stepNumberY + (stepNumberCircleSize - stepNumberLayoutResult.size.height) / 2

        drawText(
                textLayoutResult = stepNumberLayoutResult,
                topLeft = Offset(stepNumberTextX, stepNumberTextY)
        )

        // 调整：使状态图标和描述文本更加协调
        // 进一步增大图标尺寸
        val statusIconSize = 42f
        val contentTopY = position.y + titleBarHeight + paddingTop // 内容起始位置

        // 计算描述文本尺寸，使用固定的字体大小，不受缩放影响
        val textFontSize = 20.sp.toPx()
        val description = planItem.description
        val textStyle =
                TextStyle(
                        color = textColor,
                        fontSize = textFontSize.toSp(),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Start
                )

        // 计算文本区域宽度 - 确保文本不换行
        val textAreaWidth = size.width - paddingHorizontal * 2 - statusIconSize - 24

        // 测量文本以确定高度 - 确保文本不换行
        val textMeasureResult =
                textMeasurer.measure(
                        text = description,
                        style = textStyle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1, // 强制单行显示
                        constraints = Constraints(maxWidth = textAreaWidth.toInt())
                )

        // 根据文本高度计算图标垂直居中位置
        val textHeight = textMeasureResult.size.height
        val statusIconY = contentTopY + (textHeight - statusIconSize) / 2
        val statusIconX = position.x + paddingHorizontal

        // 绘制状态图标 - 居中对齐描述文本
        when (planItem.status) {
                PlanItemStatus.COMPLETED -> {
                        // 绿色勾选图标
                        drawCircle(
                                color = secondaryColor,
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        )
                        )

                        // 绘制勾选标记
                        val checkPath =
                                Path().apply {
                                        val checkmarkSize = statusIconSize * 0.5f
                                        moveTo(
                                                statusIconX + statusIconSize * 0.3f,
                                                statusIconY + statusIconSize * 0.5f
                                        )
                                        lineTo(
                                                statusIconX + statusIconSize * 0.45f,
                                                statusIconY + statusIconSize * 0.65f
                                        )
                                        lineTo(
                                                statusIconX + statusIconSize * 0.7f,
                                                statusIconY + statusIconSize * 0.35f
                                        )
                                }

                        drawPath(path = checkPath, color = Color.White, style = Stroke(width = 4f))
                }
                PlanItemStatus.IN_PROGRESS -> {
                        // 进行中图标 - 蓝色圆环
                        drawCircle(
                                color = primaryColor.copy(alpha = 0.2f),
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        )
                        )

                        drawCircle(
                                color = primaryColor,
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        ),
                                style = Stroke(width = 3f)
                        )

                        // 绘制小箭头表示进行中
                        val arrowPath =
                                Path().apply {
                                        moveTo(
                                                statusIconX + statusIconSize * 0.35f,
                                                statusIconY + statusIconSize * 0.35f
                                        )
                                        lineTo(
                                                statusIconX + statusIconSize * 0.65f,
                                                statusIconY + statusIconSize * 0.5f
                                        )
                                        lineTo(
                                                statusIconX + statusIconSize * 0.35f,
                                                statusIconY + statusIconSize * 0.65f
                                        )
                                }

                        drawPath(path = arrowPath, color = primaryColor, style = Stroke(width = 3f))
                }
                PlanItemStatus.FAILED -> {
                        // 失败图标 - 红色圆圈加X
                        drawCircle(
                                color = errorColor.copy(alpha = 0.2f),
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        )
                        )

                        // 绘制X
                        drawLine(
                                color = errorColor,
                                start =
                                        Offset(
                                                statusIconX + statusIconSize * 0.3f,
                                                statusIconY + statusIconSize * 0.3f
                                        ),
                                end =
                                        Offset(
                                                statusIconX + statusIconSize * 0.7f,
                                                statusIconY + statusIconSize * 0.7f
                                        ),
                                strokeWidth = 3f
                        )

                        drawLine(
                                color = errorColor,
                                start =
                                        Offset(
                                                statusIconX + statusIconSize * 0.7f,
                                                statusIconY + statusIconSize * 0.3f
                                        ),
                                end =
                                        Offset(
                                                statusIconX + statusIconSize * 0.3f,
                                                statusIconY + statusIconSize * 0.7f
                                        ),
                                strokeWidth = 3f
                        )
                }
                PlanItemStatus.CANCELLED -> {
                        // 取消图标 - 灰色圆圈加斜线
                        drawCircle(
                                color = Color.Gray.copy(alpha = 0.2f),
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        )
                        )

                        drawCircle(
                                color = Color.Gray,
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        ),
                                style = Stroke(width = 3f)
                        )

                        // 绘制斜线表示取消
                        drawLine(
                                color = Color.Gray,
                                start =
                                        Offset(
                                                statusIconX + statusIconSize * 0.3f,
                                                statusIconY + statusIconSize * 0.3f
                                        ),
                                end =
                                        Offset(
                                                statusIconX + statusIconSize * 0.7f,
                                                statusIconY + statusIconSize * 0.7f
                                        ),
                                strokeWidth = 3f
                        )
                }
                else -> {
                        // 待处理 - 空圆圈
                        drawCircle(
                                color = Color.Gray.copy(alpha = 0.1f),
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        )
                        )

                        drawCircle(
                                color = Color.Gray.copy(alpha = 0.5f),
                                radius = statusIconSize / 2,
                                center =
                                        Offset(
                                                statusIconX + statusIconSize / 2,
                                                statusIconY + statusIconSize / 2
                                        ),
                                style = Stroke(width = 2.5f)
                        )
                }
        }

        // 绘制任务文本 - 确保文本不换行并完整显示
        val textStartX = statusIconX + statusIconSize + 24 // 增加图标和文本之间的间距

        // 绘制文本 - 确保文本在节点内
        drawText(textLayoutResult = textMeasureResult, topLeft = Offset(textStartX, contentTopY))
}

package com.ai.assistance.operit.ui.features.chat.webview.computer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ComputerTabComponent(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    isUnsaved: Boolean,
    onClick: () -> Unit,
    onClose: (() -> Unit)?
) {
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.background else Color.Transparent
    val contentColor = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .widthIn(min = 80.dp, max = 180.dp) // Set min/max width for the tab
            .padding(horizontal = 8.dp, vertical = 8.dp) // Adjusted padding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(16.dp) // Adjusted icon size
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (isUnsaved) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(8.dp)
                        .background(
                            color = contentColor,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .padding(start = 4.dp) // Adjusted padding
                        .size(18.dp) // Further reduced button size
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = contentColor,
                        modifier = Modifier.size(14.dp) // Further reduced icon size
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(18.dp)) // Maintain alignment
            }
        }
    }
} 
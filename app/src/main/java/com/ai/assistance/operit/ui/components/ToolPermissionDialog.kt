package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.model.AITool

/**
 * 工具权限请求结果
 */
enum class PermissionRequestResult {
    ALLOW, DENY, DISCONNECT
}

/**
 * 工具权限请求底部弹出对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPermissionDialog(
    tool: AITool,
    operationDescription: String,
    onPermissionResult: (PermissionRequestResult) -> Unit,
    modifier: Modifier = Modifier
) {
    // Using AlertDialog instead of ModalBottomSheet for better visibility and focus
    AlertDialog(
        onDismissRequest = { onPermissionResult(PermissionRequestResult.DENY) },
        title = {
            Text(
                text = "Permission Request",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "AI Assistant needs permission to execute the following operation:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = operationDescription,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Tool: ${tool.name}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "* You can change default permissions in settings",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPermissionResult(PermissionRequestResult.ALLOW) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            Row {
                Button(
                    onClick = { onPermissionResult(PermissionRequestResult.DENY) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Deny")
                }
                
                Button(
                    onClick = { onPermissionResult(PermissionRequestResult.DISCONNECT) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Disconnect")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    )
} 
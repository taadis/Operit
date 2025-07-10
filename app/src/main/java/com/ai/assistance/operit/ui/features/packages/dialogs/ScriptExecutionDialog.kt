package com.ai.assistance.operit.ui.features.packages.dialogs

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.javascript.JsToolManager
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptExecutionDialog(
        packageName: String,
        tool: PackageTool,
        packageManager: PackageManager,
        initialResult: ToolResult?,
        onExecuted: (ToolResult) -> Unit,
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State for script editor
    var scriptText by remember(tool) { mutableStateOf(tool.script) }
    var paramValues by
            remember(tool) { mutableStateOf(tool.parameters.associate { it.name to "" }) }
    var executing by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf(initialResult) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                // Header
                Text(
                        text = "Script Execution: ${tool.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Script Editor
                    Text(
                            text = "Script Code:" + ":",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )

                    TextField(
                            value = scriptText,
                            onValueChange = { newValue -> scriptText = newValue },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            textStyle =
                                    MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace
                                    ),
                            colors =
                                    TextFieldDefaults.textFieldColors(
                                            containerColor = Color(0xFF1E1E1E),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Color.White,
                                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                                    )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Parameters
                    if (tool.parameters.isNotEmpty()) {
                        Text(
                                text = "Script Parameters:" + ":",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Parameter inputs
                        Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tool.parameters.forEach { param ->
                                OutlinedTextField(
                                        value = paramValues[param.name] ?: "",
                                        onValueChange = { value ->
                                            paramValues =
                                                    paramValues.toMutableMap().apply {
                                                        put(param.name, value)
                                                    }
                                        },
                                        label = {
                                            Text("${param.name}${if (param.required) " *" else ""}")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Result area
                    if (executionResult != null) {
                        Text(
                                text = "Execution Result:" + ":",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        shape = RoundedCornerShape(8.dp)
                                                ),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                            ) {
                                val success = executionResult!!.success
                                Icon(
                                        imageVector =
                                                if (success) Icons.Filled.CheckCircle
                                                else Icons.Filled.Error,
                                        contentDescription = if (success) "Success" else "Error",
                                        tint =
                                                if (success) Color(0xFF4CAF50)
                                                else Color(0xFFF44336)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        text =
                                                if (executionResult!!.success)
                                                        executionResult!!.result.toString()
                                                else "Error: ${executionResult!!.error}",
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(text = "Cancel") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = {
                                executing = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        // Check for required parameters
                                        val missingParams =
                                                tool.parameters
                                                        .filter { it.required }
                                                        .map { it.name }
                                                        .filter { paramValues[it].isNullOrEmpty() }

                                        if (missingParams.isNotEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                executionResult =
                                                        ToolResult(
                                                                toolName =
                                                                        "${packageName}:${tool.name}",
                                                                success = false,
                                                                result = StringResultData(""),
                                                                error =
                                                                        "Missing parameters: ${missingParams.joinToString(", ")}"
                                                        )
                                                onExecuted(executionResult!!)
                                            }
                                        } else {
                                            // Create the tool with parameters
                                            val parameters =
                                                    paramValues.map { (name, value) ->
                                                        ToolParameter(name = name, value = value)
                                                    }

                                            val aiTool =
                                                    AITool(
                                                            name = "${packageName}:${tool.name}",
                                                            parameters = parameters
                                                    )

                                            // Create a new interpreter instance
                                            val interpreter =
                                                    JsToolManager.getInstance(
                                                            context,
                                                            packageManager
                                                    )

                                            // Execute the script - directly call the suspending
                                            // function
                                            // Since we're already in a coroutine context with
                                            // Dispatchers.IO,
                                            // we can just call the suspending function directly
                                            val result =
                                                    interpreter.executeScript(scriptText, aiTool)

                                            // 切换回主线程更新UI
                                            withContext(Dispatchers.Main) {
                                                executionResult = result
                                                onExecuted(result)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                                "ScriptExecutionDialog",
                                                "Failed to execute script",
                                                e
                                        )

                                        // 切换回主线程更新UI
                                        withContext(Dispatchers.Main) {
                                            executionResult =
                                                    ToolResult(
                                                            toolName =
                                                                    "${packageName}:${tool.name}",
                                                            success = false,
                                                            result = StringResultData(""),
                                                            error = "Execution error: ${e.message}"
                                                    )
                                            onExecuted(executionResult!!)
                                        }
                                    } finally {
                                        // 切换回主线程更新UI状态
                                        withContext(Dispatchers.Main) { executing = false }
                                    }
                                }
                            },
                            enabled = !executing,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                    )
                    ) {
                        if (executing) {
                            // 使用自定义的简单加载指示器替代CircularProgressIndicator
                            Box(
                                    modifier =
                                            Modifier.size(20.dp)
                                                    .background(
                                                            MaterialTheme.colorScheme.onPrimary,
                                                            CircleShape
                                                    ),
                                    contentAlignment = Alignment.Center
                            ) {
                                // 添加一个旋转动画
                                val infiniteTransition = rememberInfiniteTransition()
                                val rotation by
                                        infiniteTransition.animateFloat(
                                                initialValue = 0f,
                                                targetValue = 360f,
                                                animationSpec =
                                                        infiniteRepeatable(
                                                                animation =
                                                                        tween(
                                                                                1000,
                                                                                easing =
                                                                                        LinearEasing
                                                                        ),
                                                                repeatMode = RepeatMode.Restart
                                                        )
                                        )

                                Box(
                                        modifier =
                                                Modifier.size(16.dp)
                                                        .graphicsLayer { rotationZ = rotation }
                                                        .background(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                shape = CircleShape
                                                        )
                                )
                            }
                        } else {
                            Text(text = "Execute Script")
                        }
                    }
                }
            }
        }
    }
}

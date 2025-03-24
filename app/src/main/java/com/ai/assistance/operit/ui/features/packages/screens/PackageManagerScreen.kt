package com.ai.assistance.operit.ui.features.packages.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.ai.assistance.operit.R
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.packTool.PackageManager
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.javascript.JsToolManager
import com.ai.assistance.operit.tools.PackageTool
import com.ai.assistance.operit.tools.ToolPackage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PackageManagerScreen() {
    val context = LocalContext.current
    val packageManager = remember { 
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()
    
    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    
    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    
    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }
    
    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Load packages
    LaunchedEffect(Unit) {
        try {
            availablePackages.value = packageManager.getAvailablePackages()
            importedPackages.value = packageManager.getImportedPackages()
        } catch (e: Exception) {
            Log.e("PackageManagerScreen", "Failed to load packages", e)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.package_manager),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Tabs for Available and Imported Packages
            var selectedTabIndex by remember { mutableStateOf(0) }
            val tabs = listOf(
                stringResource(id = R.string.available_packages), 
                stringResource(id = R.string.imported_packages)
            )
            
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content based on selected tab
            when (selectedTabIndex) {
                0 -> {
                    // Available Packages
                    if (availablePackages.value.isEmpty()) {
                        EmptyState(message = stringResource(id = R.string.no_packages_available))
                    } else {
                        AvailablePackagesList(
                            packages = availablePackages.value,
                            onPackageClick = { packageName ->
                                selectedPackage = packageName
                                showDetails = true
                            },
                            onImportClick = { packageName ->
                                scope.launch {
                                    try {
                                        val result = packageManager.importPackage(packageName)
                                        importedPackages.value = packageManager.getImportedPackages()
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.package_import_success)
                                        )
                                    } catch (e: Exception) {
                                        Log.e("PackageManagerScreen", "Failed to import package", e)
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.package_import_error)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                1 -> {
                    // Imported Packages
                    if (importedPackages.value.isEmpty()) {
                        EmptyState(message = stringResource(id = R.string.no_packages_imported))
                    } else {
                        ImportedPackagesList(
                            packages = importedPackages.value,
                            availablePackages = availablePackages.value,
                            onPackageClick = { packageName ->
                                selectedPackage = packageName
                                showDetails = true
                            },
                            onRemoveClick = { packageName ->
                                scope.launch {
                                    try {
                                        packageManager.removePackage(packageName)
                                        importedPackages.value = packageManager.getImportedPackages()
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.package_removed)
                                        )
                                    } catch (e: Exception) {
                                        Log.e("PackageManagerScreen", "Failed to remove package", e)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                    packageName = selectedPackage!!,
                    packageDescription = availablePackages.value[selectedPackage]?.description ?: "",
                    packageManager = packageManager,
                    onRunScript = { tool ->
                        selectedTool = tool
                        showScriptExecution = true
                    },
                    onDismiss = { showDetails = false }
                )
            }
            
            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                    packageName = selectedPackage!!,
                    tool = selectedTool!!,
                    packageManager = packageManager,
                    initialResult = scriptExecutionResult,
                    onExecuted = { result ->
                        scriptExecutionResult = result
                    },
                    onDismiss = { 
                        showScriptExecution = false
                        scriptExecutionResult = null
                    }
                )
            }
        }
    }
}

@Composable
fun AvailablePackagesList(
    packages: Map<String, ToolPackage>,
    onPackageClick: (String) -> Unit,
    onImportClick: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = packages.entries.toList(),
            key = { (name, _) -> name }
        ) { (name, pack) ->
            PackageItem(
                name = name,
                description = pack.description,
                isImported = false,
                onClick = { onPackageClick(name) },
                onActionClick = { onImportClick(name) }
            )
        }
    }
}

@Composable
fun ImportedPackagesList(
    packages: List<String>,
    availablePackages: Map<String, ToolPackage>,
    onPackageClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = packages,
            key = { name -> name }
        ) { name ->
            PackageItem(
                name = name,
                description = availablePackages[name]?.description ?: "",
                isImported = true,
                onClick = { onPackageClick(name) },
                onActionClick = { onRemoveClick(name) }
            )
        }
    }
}

@Composable
fun PackageItem(
    name: String,
    description: String,
    isImported: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
            
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = if (isImported) Icons.Default.Delete else Icons.Default.Add,
                    contentDescription = if (isImported) 
                        stringResource(id = R.string.remove_package) 
                    else 
                        stringResource(id = R.string.import_package),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PackageDetailsDialog(
    packageName: String,
    packageDescription: String,
    packageManager: PackageManager,
    onRunScript: (PackageTool) -> Unit,
    onDismiss: () -> Unit
) {
    // Load the package details
    val toolPackage = remember(packageName) {
        try {
            val packages = packageManager.getPackageTools(packageName)
            packages
        } catch (e: Exception) {
            Log.e("PackageDetailsDialog", "Failed to load package details", e)
            null
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.package_details))
        },
        text = {
            Column {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = packageDescription,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.package_tools),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Log.d("PackageDetailsDialog", "Tool package: ${toolPackage?.tools}")

                if (toolPackage?.tools == null || toolPackage.tools.isEmpty()) {
                    Text(
                        text = "No tools found in this package",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = toolPackage.tools,
                            key = { tool -> tool.name }
                        ) { tool ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tool.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = tool.description,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        // Add Run Script button
                                        IconButton(
                                            onClick = { onRunScript(tool) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = stringResource(id = R.string.run_script),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    if (tool.parameters.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.script_parameters) + ":",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        for (param in tool.parameters) {
                                            val requiredText = if (param.required) "(required)" else "(optional)"
                                            Text(
                                                text = "• ${param.name} $requiredText: ${param.description}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    )
}

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
    var paramValues by remember(tool) { 
        mutableStateOf(
            tool.parameters.associate { it.name to "" }
        ) 
    }
    var executing by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf(initialResult) }
    
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Text(
                    text = stringResource(id = R.string.script_execution) + ": ${tool.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Script Editor
                Text(
                    text = stringResource(id = R.string.script_code) + ":",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextField(
                    value = scriptText,
                    onValueChange = { newValue -> scriptText = newValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = TextFieldDefaults.textFieldColors(
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
                        text = stringResource(id = R.string.script_parameters) + ":",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Parameter inputs
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = tool.parameters,
                            key = { param -> param.name }
                        ) { param ->
                            OutlinedTextField(
                                value = paramValues[param.name] ?: "",
                                onValueChange = { value -> 
                                    paramValues = paramValues.toMutableMap().apply {
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
                        text = stringResource(id = R.string.execution_result) + ":",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .border(
                                width = 1.dp,
                                color = if (executionResult!!.success) 
                                    Color(0xFF4CAF50) else Color(0xFFF44336),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        shape = RoundedCornerShape(8.dp),
                        color = if (executionResult!!.success) 
                            Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = if (executionResult!!.success) 
                                    executionResult!!.result 
                                else 
                                    "Error: ${executionResult!!.error}",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            executing = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // Check for required parameters
                                    val missingParams = tool.parameters
                                        .filter { it.required }
                                        .map { it.name }
                                        .filter { paramValues[it].isNullOrEmpty() }
                                    
                                    if (missingParams.isNotEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            executionResult = ToolResult(
                                                toolName = "${packageName}:${tool.name}",
                                                success = false,
                                                result = "",
                                                error = context.getString(R.string.missing_parameters, missingParams.joinToString(", "))
                                            )
                                        }
                                    } else {
                                        // Create the tool with parameters
                                        val parameters = paramValues.map { (name, value) ->
                                            ToolParameter(name = name, value = value)
                                        }
                                        
                                        val aiTool = AITool(
                                            name = "${packageName}:${tool.name}",
                                            parameters = parameters
                                        )
                                        
                                        // Create a new interpreter instance
                                        val interpreter = JsToolManager.getInstance(context, packageManager)
                                        
                                        // Execute the script - directly call the suspending function
                                        // Since we're already in a coroutine context with Dispatchers.IO,
                                        // we can just call the suspending function directly
                                        val result = interpreter.executeScript(scriptText, aiTool)
                                        
                                        // 切换回主线程更新UI
                                        withContext(Dispatchers.Main) {
                                            executionResult = result
                                            onExecuted(result)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ScriptExecutionDialog", "Failed to execute script", e)
                                    
                                    // 切换回主线程更新UI
                                    withContext(Dispatchers.Main) {
                                        executionResult = ToolResult(
                                            toolName = "${packageName}:${tool.name}",
                                            success = false,
                                            result = "",
                                            error = "Execution error: ${e.message}"
                                        )
                                    }
                                } finally {
                                    // 切换回主线程更新UI状态
                                    withContext(Dispatchers.Main) {
                                        executing = false
                                    }
                                }
                            }
                        },
                        enabled = !executing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (executing) {
                            // 使用自定义的简单加载指示器替代CircularProgressIndicator
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                // 添加一个旋转动画
                                val infiniteTransition = rememberInfiniteTransition()
                                val rotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )
                                
                                Box(modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { 
                                        rotationZ = rotation 
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                )
                            }
                        } else {
                            Text(text = stringResource(id = R.string.execute_script))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
} 
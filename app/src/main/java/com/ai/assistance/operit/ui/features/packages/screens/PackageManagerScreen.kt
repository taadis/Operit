package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Upload
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ai.assistance.operit.R
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.StringResultData
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
import java.io.File

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
    
    // File picker launcher for importing external packages
    val packageFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    // Convert URI to file path - this is a simplified approach
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        val nameIndex = it.getColumnIndex("_display_name")
                        if (it.moveToFirst() && nameIndex >= 0) {
                            val fileName = it.getString(nameIndex)
                            if (!fileName.endsWith(".hjson")) {
                                snackbarHostState.showSnackbar(
                                    message = "只支持.hjson文件"
                                )
                                return@launch
                            }
                        }
                    }
                    
                    // Copy the file to a temporary location
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, "temp_package.hjson")
                    
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Import the package from the temporary file
                    val result = packageManager.importPackageFromExternalStorage(tempFile.absolutePath)
                    
                    // Refresh the lists
                    availablePackages.value = packageManager.getAvailablePackages()
                    importedPackages.value = packageManager.getImportedPackages()
                    
                    snackbarHostState.showSnackbar(
                        message = "外部包导入成功"
                    )
                    
                    // Clean up the temporary file
                    tempFile.delete()
                } catch (e: Exception) {
                    Log.e("PackageManagerScreen", "Failed to import external package", e)
                    snackbarHostState.showSnackbar(
                        message = "外部包导入失败: ${e.message}"
                    )
                }
            }
        }
    }
    
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { packageFilePicker.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = "导入外部包",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "包管理器",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 显示外部存储包路径
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "外部包存储路径",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val packagesPath = remember { packageManager.getExternalPackagesPath() }
                    Text(
                        text = packagesPath,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val packagesDir = packageManager.getExternalPackagesPath()
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        // Android 10及以上使用Storage Access Framework
                                        intent.setDataAndType(
                                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                            "*/*"
                                        )
                                        intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "请导航到Android/data/com.ai.assistance.operit/files/packages目录"
                                            )
                                        }
                                        null
                                    } else {
                                        // Android 9及以下可以直接用文件路径
                                        intent.setDataAndType(Uri.parse("file://$packagesDir"), "*/*")
                                        Uri.parse("file://$packagesDir")
                                    }
                                    
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("PackageManagerScreen", "Failed to open file manager", e)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "无法打开文件管理器: ${e.message}"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("打开文件管理器")
                        }
                        
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Package Path", packagesPath)
                                clipboardManager.setPrimaryClip(clip)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "路径已复制到剪贴板"
                                    )
                                }
                            }
                        ) {
                            Text("复制路径")
                        }
                    }
                }
            }
            
            // Tabs for Available and Imported Packages
            var selectedTabIndex by remember { mutableStateOf(0) }
            val tabs = listOf(
                "Available Packages", 
                "Imported Packages"
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
                        EmptyState(message = "No packages available")
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
                                            message = "Package import success"
                                        )
                                    } catch (e: Exception) {
                                        Log.e("PackageManagerScreen", "Failed to import package", e)
                                        snackbarHostState.showSnackbar(
                                            message = "Package import error"
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
                        EmptyState(message = "No packages imported")
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
                                            message = "Package removed"
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
                        "Remove package" 
                    else 
                        "Import package",
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
            Text(text = "Package Details")
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
                    text = "Package Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                

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
                                                contentDescription = "Run Script",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    if (tool.parameters.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Script Parameters:" + ":",
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
                Text(text = "OK")
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
                    text = "Script Execution: ${tool.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Script Editor
                Text(
                    text = "Script Code:" + ":",
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
                        text = "Script Parameters:" + ":",
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
                        text = "Execution Result:" + ":",
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
                                    executionResult!!.result.toString() 
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
                        Text(text = "Cancel")
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
                                                result = StringResultData(""),
                                                error = "Missing parameters: ${missingParams.joinToString(", ")}"
                                            )
                                            onExecuted(executionResult!!)
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
                                            result = StringResultData(""),
                                            error = "Execution error: ${e.message}"
                                        )
                                        onExecuted(executionResult!!)
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
                            Text(text = "Execute Script")
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
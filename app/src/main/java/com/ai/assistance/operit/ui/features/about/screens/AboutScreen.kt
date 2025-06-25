package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowForward

@Composable
fun HtmlText(
        html: String,
        modifier: Modifier = Modifier,
        style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val textColor = style.color.toArgb()

    AndroidView(
            modifier = modifier,
            factory = { context ->
                TextView(context).apply {
                    this.textSize = style.fontSize.value
                    this.setTextColor(textColor)
                    this.movementMethod = LinkMovementMethod.getInstance()
                }
            },
            update = { textView ->
                textView.text =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            @Suppress("DEPRECATION") Html.fromHtml(html)
                        }
            }
    )
}

@Composable
fun InfoItem(
        icon: ImageVector,
        title: String,
        content: @Composable () -> Unit,
        modifier: Modifier = Modifier
) {
    Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp, top = 2.dp)
        )
        Column {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Box(modifier = Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

// 定义用于展示的开源库数据类
data class OpenSourceLibrary(
    val name: String,
    val description: String = "",
    val license: String = "",
    val website: String = ""
)

// 准备开源库列表
private fun getOpenSourceLibraries(): List<OpenSourceLibrary> {
    return listOf(
        OpenSourceLibrary("android-gif-drawable", "GIF support for Android", "MIT", "https://github.com/koral--/android-gif-drawable"),
        OpenSourceLibrary("Android-Image-Cropper", "Image cropping library for Android", "Apache-2.0", "https://github.com/CanHub/Android-Image-Cropper"),
        OpenSourceLibrary("AndroidSVG", "SVG rendering library", "Apache-2.0", "https://github.com/BigBadaboom/androidsvg"),
        OpenSourceLibrary("AndroidX Compose", "Android 声明式 UI 框架", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
        OpenSourceLibrary("AndroidX Core KTX", "Android 核心库的 Kotlin 扩展", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("AndroidX DataStore", "Data storage solution", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
        OpenSourceLibrary("AndroidX Window", "Window manager library for foldables", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/window"),
        OpenSourceLibrary("Apache Commons Compress", "Library for working with archives", "Apache-2.0", "https://commons.apache.org/proper/commons-compress/"),
        OpenSourceLibrary("Apache Commons IO", "Library of I/O utilities", "Apache-2.0", "https://commons.apache.org/proper/commons-io/"),
        OpenSourceLibrary("Apache PDFBox", "Java library for working with PDF documents", "Apache-2.0", "https://pdfbox.apache.org/"),
        OpenSourceLibrary("Apache POI", "文档处理库", "Apache-2.0", "https://poi.apache.org/"),
        OpenSourceLibrary("apk-parser", "A parser for APK files", "Apache-2.0", "https://github.com/hsiafan/apk-parser"),
        OpenSourceLibrary("axml", "A-XML format parsing library", "Apache-2.0", "https://github.com/Sable/axml"),
        OpenSourceLibrary("Bouncy Castle", "Cryptography library", "MIT", "https://www.bouncycastle.org/"),
        OpenSourceLibrary("Coil", "Image loading library for Android", "Apache-2.0", "https://coil-kt.github.io/coil/"),
        OpenSourceLibrary("colorpicker-compose", "A color picker for Jetpack Compose", "Apache-2.0", "https://github.com/skydoves/colorpicker-compose"),
        OpenSourceLibrary("ExoPlayer", "Extensible media player for Android", "Apache-2.0", "https://exoplayer.dev/"),
        OpenSourceLibrary("FFmpegKit", "FFmpeg 工具库", "LGPL-3.0", "https://github.com/arthenica/ffmpeg-kit"),
        OpenSourceLibrary("Glide", "Android 图片加载库", "BSD, MIT, Apache-2.0", "https://github.com/bumptech/glide"),
        OpenSourceLibrary("Gson", "Google JSON 解析库", "Apache-2.0", "https://github.com/google/gson"),
        OpenSourceLibrary("HJSON", "Human-friendly JSON format", "MIT", "https://hjson.github.io/"),
        OpenSourceLibrary("HNSWLib", "Header-only C++/python library for fast approximate nearest neighbor search", "Apache-2.0", "https://github.com/jelmerk/hnswlib"),
        OpenSourceLibrary("iText (v5)", "Library for creating and manipulating PDF files", "MPL/LGPL", "https://itextpdf.com/"),
        OpenSourceLibrary("java-diff-utils", "Diff library for Java", "Apache-2.0", "https://github.com/java-diff-utils/java-diff-utils"),
        OpenSourceLibrary("Jieba-Android", "Jieba Chinese word segmentation for Android", "MIT", "https://github.com/huaban/jieba-analysis"),
        OpenSourceLibrary("JLatexMath-Android", "LaTeX formula rendering library", "Apache-2.0", "https://github.com/noties/jlatexmath-android"),
        OpenSourceLibrary("Jsoup", "Java HTML parser", "MIT", "https://jsoup.org/"),
        OpenSourceLibrary("junrar", "RAR archive extraction library", "The Unlicense", "https://github.com/junrar/junrar"),
        OpenSourceLibrary("kotlin-logging", "Lightweight logging framework for Kotlin", "Apache-2.0", "https://github.com/oshai/kotlin-logging"),
        OpenSourceLibrary("kotlin-uuid", "UUID library for Kotlin", "Apache-2.0", "https://github.com/benasher44/uuid"),
        OpenSourceLibrary("Kotlin Coroutines", "Kotlin 协程库", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceLibrary("kotlinx.serialization", "Kotlin 序列化库", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OpenSourceLibrary("libsu", "Root access library for Android", "Apache-2.0", "https://github.com/topjohnwu/libsu"),
        OpenSourceLibrary("ML Kit", "Google 机器学习工具包", "Apache-2.0", "https://developers.google.com/ml-kit"),
        OpenSourceLibrary("NanoHTTPD", "轻量级 HTTP 服务器", "BSD-3-Clause", "https://github.com/NanoHttpd/nanohttpd"),
        OpenSourceLibrary("OkHttp", "HTTP 客户端", "Apache-2.0", "https://square.github.io/okhttp/"),
        OpenSourceLibrary("RenderX", "LaTeX rendering library", "MIT", "https://github.com/tech-pw/RenderX"),
        OpenSourceLibrary("Room", "Android SQLite 对象映射库", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),
        OpenSourceLibrary("Shizuku", "System service for apps to use system APIs directly", "Apache-2.0", "https://github.com/RikkaApps/Shizuku"),
        OpenSourceLibrary("sherpa-ncnn", "Real-time speech recognition with Next-gen Kaldi", "Apache-2.0", "https://github.com/k2-fsa/sherpa-ncnn"),
        OpenSourceLibrary("SLF4J", "Simple Logging Facade for Java", "MIT", "https://www.slf4j.org/"),
        OpenSourceLibrary("TensorFlow Lite", "On-device machine learning framework", "Apache-2.0", "https://www.tensorflow.org/lite"),
        OpenSourceLibrary("zipalign-java", "zipalign implementation in Java", "MIT", "https://github.com/Iyxan23/zipalign-java")
    ).sortedBy { it.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val libraries = remember { getOpenSourceLibraries() }
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "开源许可",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(libraries) { library ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = library.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (library.description.isNotEmpty()) {
                                Text(
                                    text = library.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                text = "许可: ${library.license}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            if (library.website.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = Uri.parse(library.website)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            onDismiss()
                                            context.startActivity(intent)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "访问项目",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("确定")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取UpdateManager实例
    val updateManager = remember { UpdateManager.getInstance(context) }

    // 监听更新状态
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Initial) }

    // 观察UpdateManager的LiveData
    DisposableEffect(updateManager) {
        val observer =
                androidx.lifecycle.Observer<UpdateStatus> { newStatus -> updateStatus = newStatus }
        updateManager.updateStatus.observeForever(observer)

        onDispose { updateManager.updateStatus.removeObserver(observer) }
    }

    // 显示更新对话框
    var showUpdateDialog by remember { mutableStateOf(false) }
    // 添加下载源选择对话框状态
    var showDownloadSourceMenu by remember { mutableStateOf(false) }

    // 添加开源许可对话框状态
    var showLicenseDialog by remember { mutableStateOf(false) }

    // 检查更新按钮动画
    val buttonAlpha =
            animateFloatAsState(
                    targetValue =
                            if (updateStatus is UpdateStatus.Checking)
                                    0.6f
                            else 1f,
                    label = "ButtonAlpha"
            )

    // 获取应用版本信息
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "未知"
        }
    }

    // 观察更新状态变化
    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is UpdateStatus.Available, is UpdateStatus.UpToDate, is UpdateStatus.Error -> {
                showUpdateDialog = true
            }
            else -> {}
        }
    }

    // 检查更新
    fun checkForUpdates() {
        scope.launch { updateManager.checkForUpdates(appVersion) }
    }

    // 处理下载更新 - 显示下载源选择对话框
    fun handleDownload() {
        val status = updateStatus as? UpdateStatus.Available ?: return
        if (status.downloadUrl.isNotEmpty()) {
            showDownloadSourceMenu = true // 显示下载源选择对话框
        } else {
            // 如果没有下载链接，则直接打开更新页面
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(status.updateUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            showUpdateDialog = false
        }
    }

    // 从指定源下载
    fun downloadFromSource(sourceType: String) {
        val status = updateStatus as? UpdateStatus.Available ?: return
        val downloadUrl = when (sourceType) {
            "github" -> status.downloadUrl // 原始GitHub链接
            "ghfast" -> {
                // 使用 ghfast.top 镜像加速 GitHub 下载
                if (status.downloadUrl.contains("github.com") && status.downloadUrl.endsWith(".apk")) {
                    UpdateManager.getAcceleratedDownloadUrl(status.newVersion, status.downloadUrl)
                } else {
                    status.downloadUrl
                }
            }
            else -> status.downloadUrl
        }
        
        // 打开浏览器下载
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(downloadUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        showDownloadSourceMenu = false
        showUpdateDialog = false
    }

    // 显示开源许可对话框
    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    // 更新对话框
    if (showUpdateDialog) {
        AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val icon =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> Icons.Default.Update
                                    is UpdateStatus.Checking -> Icons.Default.Download
                                    is UpdateStatus.UpToDate -> Icons.Default.CheckCircle
                                    is UpdateStatus.Error -> Icons.Default.Error
                                    else -> Icons.Default.Update
                                }

                        val iconTint =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.Checking -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.UpToDate -> Color(0xFF4CAF50) // Green
                                    is UpdateStatus.Error -> Color(0xFFF44336) // Red
                                    else -> MaterialTheme.colorScheme.primary
                                }

                        Icon(imageVector = icon, contentDescription = null, tint = iconTint)

                        Text(
                                text =
                                        when (updateStatus) {
                                            is UpdateStatus.Available -> "发现新版本"
                                            is UpdateStatus.Checking -> "正在检查更新"
                                            is UpdateStatus.UpToDate -> "检查完成"
                                            is UpdateStatus.Error -> "检查失败"
                                            else -> "更新检查"
                                        }
                        )
                    }
                },
                text = {
                    when (val status = updateStatus) {
                        is UpdateStatus.Available -> {
                            Column {
                                Text(
                                        "当前版本: $appVersion\n新版本: ${status.newVersion}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (status.releaseNotes.isNotEmpty()) {
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                                    Text(
                                            "更新内容:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                    )

                                    Text(
                                            status.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        is UpdateStatus.UpToDate -> {
                            Text("当前已是最新版本: $appVersion")
                        }
                        is UpdateStatus.Error -> {
                            Text(status.message)
                        }
                        else -> {
                            Text("检查更新中...")
                        }
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (updateStatus is UpdateStatus.Available) {
                                    handleDownload()
                                } else if (updateStatus !is UpdateStatus.Checking) {
                                    showUpdateDialog = false
                                }
                            },
                            enabled =
                                    updateStatus is UpdateStatus.Available ||
                                            (updateStatus !is UpdateStatus.Checking)
                    ) {
                        Text(
                                when (updateStatus) {
                                    is UpdateStatus.Available -> "去下载"
                                    else -> "确定"
                                }
                        )
                    }
                },
                dismissButton = {
                    if (updateStatus !is UpdateStatus.Checking) {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("关闭") }
                    }
                }
        )
    }

    // 下载源选择对话框
    if (showDownloadSourceMenu) {
        AlertDialog(
            onDismissRequest = { showDownloadSourceMenu = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "选择下载源",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "请选择适合您网络环境的下载源：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 国内加速镜像选项 - 使用Card而不是Button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { downloadFromSource("ghfast") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "国内加速镜像",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "通过ghfast.top加速，推荐国内用户使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // GitHub原始链接选项
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { downloadFromSource("github") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "GitHub原始链接",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "直接从GitHub官方服务器下载，速度可能较慢",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showDownloadSourceMenu = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "取消",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Logo with circular background
            Box(
                    modifier =
                            Modifier.size(140.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(20.dp),
                    contentAlignment = Alignment.Center
            ) {
                Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )

            // App Version
            Text(
                    text = stringResource(id = R.string.about_version, appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // 添加检查更新按钮 - 美化版
            Button(
                    onClick = { checkForUpdates() },
                    modifier =
                            Modifier.fillMaxWidth(0.8f)
                                    .height(48.dp)
                                    .alpha(buttonAlpha.value)
                                    .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                    enabled = updateStatus !is UpdateStatus.Checking
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    if (updateStatus is UpdateStatus.Checking) {
                        // 使用简单的点代替复杂的加载指示器
                        Box(
                                modifier =
                                        Modifier.size(20.dp)
                                                .background(
                                                        color =
                                                                MaterialTheme.colorScheme.onPrimary
                                                                        .copy(alpha = 0.7f),
                                                        shape = CircleShape
                                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                            text = if (updateStatus is UpdateStatus.Checking) "检查中..." else "检查更新",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Card with app information - 美化版
            Card(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.Start
                ) {
                    Text(
                            text = stringResource(id = R.string.about_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                            text = stringResource(id = R.string.about_description),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // 使用InfoItem组件展示信息
                    InfoItem(
                            icon = Icons.Rounded.Info,
                            title = "开发者",
                            content = {
                                HtmlText(
                                        html = stringResource(id = R.string.about_developer),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    InfoItem(
                            icon = Icons.Rounded.Info,
                            title = "联系方式",
                            content = {
                                Text(
                                        text = stringResource(id = R.string.about_contact),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    InfoItem(
                            icon = Icons.Rounded.Info,
                            title = "项目地址",
                            content = {
                                HtmlText(
                                        html = stringResource(id = R.string.about_website),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            }
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                            text = stringResource(id = R.string.about_copyright),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 添加开源许可卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
                    .clickable { showLicenseDialog = true },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "开源许可声明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

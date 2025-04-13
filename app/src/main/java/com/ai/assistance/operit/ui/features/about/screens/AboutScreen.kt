package com.ai.assistance.operit.ui.features.about.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

// 更新状态
sealed class UpdateStatus {
    object Initial : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(
        val newVersion: String, 
        val updateUrl: String, 
        val releaseNotes: String,
        val downloadUrl: String = "" // 添加下载URL
    ) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus() // 添加下载进度状态
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

// 版本比较器
fun compareVersions(v1: String, v2: String): Int {
    // 移除可能的 'v' 前缀
    val version1 = v1.removePrefix("v")
    val version2 = v2.removePrefix("v")

    val parts1 = version1.split(".")
    val parts2 = version2.split(".")

    val maxLength = maxOf(parts1.size, parts2.size)

    for (i in 0 until maxLength) {
        val part1 = if (i < parts1.size) parts1[i].toIntOrNull() ?: 0 else 0
        val part2 = if (i < parts2.size) parts2[i].toIntOrNull() ?: 0 else 0

        if (part1 < part2) return -1
        if (part1 > part2) return 1
    }

    return 0
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 更新状态
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Initial) }

    // 显示更新对话框
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    // 下载ID
    var downloadId by remember { mutableLongStateOf(-1L) }

    // 检查更新按钮动画
    val buttonAlpha =
            animateFloatAsState(
                    targetValue = if (updateStatus is UpdateStatus.Checking || 
                                      updateStatus is UpdateStatus.Downloading) 0.6f else 1f,
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

    // 安装APK
    fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
    }

    // 下载完成的广播接收器
    val downloadCompleteReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId && downloadId != -1L) {
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (columnIndex != -1) {
                            val status = cursor.getInt(columnIndex)
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                // 下载成功，获取文件URI
                                val uriColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                if (uriColumnIndex != -1) {
                                    val uriString = cursor.getString(uriColumnIndex)
                                    val uri = Uri.parse(uriString)
                                    
                                    // 安装APK
                                    installApk(context, uri)
                                }
                            } else {
                                updateStatus = UpdateStatus.Error("下载失败，状态码: $status")
                                showUpdateDialog = true
                            }
                        }
                    }
                    cursor.close()
                }
            }
        }
    }

    // 注册和取消注册广播接收器
    DisposableEffect(Unit) {
        // 添加RECEIVER_NOT_EXPORTED标志，以避免在Android 13+上的SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        
        onDispose {
            context.unregisterReceiver(downloadCompleteReceiver)
        }
    }

    // 下载APK
    fun downloadApk(downloadUrl: String, version: String) {
        try {
            val fileName = "Operit-${version}.apk"
            
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("下载 Operit ${version}")
                .setDescription("正在下载新版本...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            updateStatus = UpdateStatus.Downloading(0f)
            showUpdateDialog = true
            
            // 在后台监控下载进度
            scope.launch {
                val query = DownloadManager.Query().setFilterById(downloadId)
                var downloading = true
                
                while (downloading && updateStatus is UpdateStatus.Downloading) {
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIndex != -1) {
                            val status = cursor.getInt(statusIndex)
                            
                            when (status) {
                                DownloadManager.STATUS_RUNNING -> {
                                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                    
                                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                                        val bytesTotal = cursor.getLong(bytesTotalIndex)
                                        
                                        if (bytesTotal > 0) {
                                            val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                                            updateStatus = UpdateStatus.Downloading(progress)
                                        }
                                    }
                                }
                                DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                                    downloading = false
                                }
                            }
                        }
                    }
                    cursor.close()
                    kotlinx.coroutines.delay(500) // 每0.5秒更新一次
                }
            }
        } catch (e: Exception) {
            updateStatus = UpdateStatus.Error("下载失败: ${e.message}")
            showUpdateDialog = true
        }
    }

    // 找到下载链接
    suspend fun findApkDownloadUrl(releasesUrl: String, repoOwner: String, repoName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(releasesUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    
                    if (jsonArray.length() > 0) {
                        val latestRelease = jsonArray.getJSONObject(0)
                        val assets = latestRelease.optJSONArray("assets")
                        
                        if (assets != null && assets.length() > 0) {
                            // 找到apk文件
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.getString("name")
                                if (name.endsWith(".apk")) {
                                    return@withContext asset.getString("browser_download_url")
                                }
                            }
                        }
                    }
                }
                
                // 如果找不到APK，返回GitHub仓库页面
                "https://github.com/$repoOwner/$repoName/releases"
            } catch (e: Exception) {
                "https://github.com/$repoOwner/$repoName/releases"
            }
        }
    }

    // 从GitHub API检查更新
    fun checkForUpdates() {
        updateStatus = UpdateStatus.Checking

        scope.launch {
            try {
                val latestRelease =
                        withContext(Dispatchers.IO) {
                            // 从字符串资源中获取GitHub仓库信息
                            val aboutWebsite = context.getString(R.string.about_website)

                            // 解析GitHub仓库链接 - 处理HTML格式
                            val htmlContent = aboutWebsite.replace("&lt;", "<").replace("&gt;", ">")
                            val githubUrlPattern = "https://github.com/([^/\"<>]+)/([^/\"<>]+)".toRegex()
                            val matchResult = githubUrlPattern.find(htmlContent)

                            val (repoOwner, repoName) =
                                    if (matchResult != null) {
                                        Pair(matchResult.groupValues[1], matchResult.groupValues[2])
                                    } else {
                                        Pair("AAswordman", "Operit") // 默认值，从字符串资源中看到的
                                    }

                            val allReleasesUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases"
                            var tagName = ""
                            var htmlUrl = ""
                            var body = ""
                            var downloadUrl = ""
                            
                            // 先尝试获取所有发布版本来寻找下载链接
                            val allReleasesConn = URL(allReleasesUrl).openConnection() as HttpURLConnection
                            allReleasesConn.requestMethod = "GET"
                            allReleasesConn.setRequestProperty(
                                    "Accept",
                                    "application/vnd.github.v3+json"
                            )
                            
                            if (allReleasesConn.responseCode == HttpURLConnection.HTTP_OK) {
                                val response = allReleasesConn.inputStream.bufferedReader().use { it.readText() }
                                val jsonArray = JSONArray(response)
                                
                                if (jsonArray.length() > 0) {
                                    // 获取第一个发布（最新的）
                                    val latestRelease = jsonArray.getJSONObject(0)
                                    tagName = latestRelease.getString("tag_name")
                                    htmlUrl = latestRelease.getString("html_url")
                                    body = latestRelease.optString("body", "")
                                    
                                    // 寻找APK下载地址
                                    val assets = latestRelease.optJSONArray("assets")
                                    if (assets != null && assets.length() > 0) {
                                        // 查找apk文件
                                        for (i in 0 until assets.length()) {
                                            val asset = assets.getJSONObject(i)
                                            val name = asset.getString("name")
                                            if (name.endsWith(".apk")) {
                                                downloadUrl = asset.getString("browser_download_url")
                                                break
                                            }
                                        }
                                    }
                                    
                                    // 如果找到版本但没找到下载链接
                                    if (downloadUrl.isEmpty()) {
                                        downloadUrl = htmlUrl // 退回到使用发布页面
                                    }
                                    
                                    UpdateStatus.Available(tagName, htmlUrl, body, downloadUrl)
                                } else {
                                    throw Exception("仓库 $repoOwner/$repoName 没有发布版本")
                                }
                            } else if (allReleasesConn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                                throw Exception("仓库不存在或无法访问: $repoOwner/$repoName")
                            } else {
                                throw Exception("API请求失败: HTTP ${allReleasesConn.responseCode}")
                            }
                        }

                // 比较版本
                if (latestRelease is UpdateStatus.Available && 
                    compareVersions(latestRelease.newVersion, appVersion) > 0) {
                    updateStatus = latestRelease
                    showUpdateDialog = true
                } else {
                    updateStatus = UpdateStatus.UpToDate
                    showUpdateDialog = true
                }
            } catch (e: Exception) {
                updateStatus = UpdateStatus.Error("更新检查失败: ${e.message}")
                showUpdateDialog = true
            }
        }
    }

    // 更新对话框
    if (showUpdateDialog) {
        AlertDialog(
                onDismissRequest = { 
                    // 如果正在下载，不关闭对话框
                    if (updateStatus !is UpdateStatus.Downloading) {
                        showUpdateDialog = false 
                    }
                },
                title = {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val icon =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> Icons.Default.Update
                                    is UpdateStatus.Downloading -> Icons.Default.Download
                                    is UpdateStatus.UpToDate -> Icons.Default.CheckCircle
                                    is UpdateStatus.Error -> Icons.Default.Error
                                    else -> Icons.Default.Update
                                }

                        val iconTint =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.Downloading -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.UpToDate -> Color(0xFF4CAF50) // Green
                                    is UpdateStatus.Error -> Color(0xFFF44336) // Red
                                    else -> MaterialTheme.colorScheme.primary
                                }

                        Icon(imageVector = icon, contentDescription = null, tint = iconTint)

                        Text(
                                text =
                                        when (updateStatus) {
                                            is UpdateStatus.Available -> "发现新版本"
                                            is UpdateStatus.Downloading -> "正在下载更新"
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
                        is UpdateStatus.Downloading -> {
                            Column {
                                Text(
                                    "正在下载新版本，请稍候...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                // 修复进度条
                                LinearProgressIndicator(
                                    progress = status.progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                                
                                Text(
                                    "${(status.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .align(Alignment.End)
                                )
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
                                    val status = updateStatus as UpdateStatus.Available
                                    
                                    // 判断是否有APK直接下载链接
                                    if (status.downloadUrl.endsWith(".apk")) {
                                        // 直接下载APK
                                        downloadApk(status.downloadUrl, status.newVersion)
                                    } else {
                                        // 打开浏览器下载
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse(status.updateUrl)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        showUpdateDialog = false
                                    }
                                } else if (updateStatus !is UpdateStatus.Downloading) {
                                    showUpdateDialog = false
                                }
                            },
                            enabled = updateStatus is UpdateStatus.Available || 
                                     (updateStatus !is UpdateStatus.Downloading && updateStatus !is UpdateStatus.Checking)
                    ) { 
                        Text(
                            when (updateStatus) {
                                is UpdateStatus.Available -> "立即更新"
                                is UpdateStatus.Downloading -> "下载中..."
                                else -> "确定"
                            }
                        )
                    }
                },
                dismissButton = {
                    if (updateStatus !is UpdateStatus.Downloading) {
                        TextButton(onClick = { showUpdateDialog = false }) { 
                            Text("关闭") 
                        }
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
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
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
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
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
        }
    }
}

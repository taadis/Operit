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

    // 检查更新按钮动画
    val buttonAlpha =
            animateFloatAsState(
                    targetValue =
                            if (updateStatus is UpdateStatus.Checking ||
                                            updateStatus is UpdateStatus.Downloading
                            )
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

    // 处理APK安装
    fun installApk(uri: Uri) {
        val intent = updateManager.createInstallIntent(uri)
        context.startActivity(intent)
    }

    // 下载完成后的处理
    DisposableEffect(Unit) {
        // 注册下载接收器
        updateManager.registerDownloadReceiver { uri -> uri?.let { installApk(it) } }

        onDispose {
            // 取消注册下载接收器
            updateManager.unregisterDownloadReceiver()
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

    // 处理下载更新
    fun handleDownload() {
        val status = updateStatus as? UpdateStatus.Available ?: return

        // 判断是否有APK直接下载链接
        if (status.downloadUrl.endsWith(".apk")) {
            // 直接下载APK
            scope.launch { updateManager.downloadUpdate(status.downloadUrl, status.newVersion) }
        } else {
            // 打开浏览器下载
            val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(status.updateUrl)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            context.startActivity(intent)
            showUpdateDialog = false
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
                                    is UpdateStatus.DownloadComplete -> Icons.Default.CheckCircle
                                    is UpdateStatus.UpToDate -> Icons.Default.CheckCircle
                                    is UpdateStatus.Error -> Icons.Default.Error
                                    else -> Icons.Default.Update
                                }

                        val iconTint =
                                when (updateStatus) {
                                    is UpdateStatus.Available -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.Downloading -> MaterialTheme.colorScheme.primary
                                    is UpdateStatus.DownloadComplete -> Color(0xFF4CAF50) // Green
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
                                            is UpdateStatus.DownloadComplete -> "下载完成"
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

                                // 进度条 - 修复进度卡在99%的问题
                                LinearProgressIndicator(
                                        progress =
                                                if (status.progress >= 0.99f && status.progress < 1f
                                                )
                                                        1f
                                                else status.progress,
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer
                                )

                                Text(
                                        // 如果进度接近100%但未到1，显示为100%
                                        "${(if (status.progress >= 0.99f && status.progress < 1f) 100 else (status.progress * 100).toInt())}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp).align(Alignment.End)
                                )
                            }
                        }
                        is UpdateStatus.DownloadComplete -> {
                            Text("下载已完成，正在准备安装...", style = MaterialTheme.typography.bodyMedium)
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
                                } else if (updateStatus !is UpdateStatus.Downloading &&
                                                updateStatus !is UpdateStatus.DownloadComplete
                                ) {
                                    showUpdateDialog = false
                                }
                            },
                            enabled =
                                    updateStatus is UpdateStatus.Available ||
                                            (updateStatus !is UpdateStatus.Downloading &&
                                                    updateStatus !is UpdateStatus.Checking &&
                                                    updateStatus !is UpdateStatus.DownloadComplete)
                    ) {
                        Text(
                                when (updateStatus) {
                                    is UpdateStatus.Available -> "立即更新"
                                    is UpdateStatus.Downloading -> "下载中..."
                                    is UpdateStatus.DownloadComplete -> "安装中..."
                                    else -> "确定"
                                }
                        )
                    }
                },
                dismissButton = {
                    if (updateStatus !is UpdateStatus.Downloading &&
                                    updateStatus !is UpdateStatus.DownloadComplete
                    ) {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("关闭") }
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
        }
    }
}

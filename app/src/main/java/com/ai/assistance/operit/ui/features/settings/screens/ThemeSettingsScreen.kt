package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.github.skydoves.colorpicker.compose.*
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.launch
import com.google.android.exoplayer2.DefaultLoadControl
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.detectTapGestures

// Add utility function to calculate the luminance of a color
private fun calculateLuminance(color: Color): Float {
    return 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager(context) }
    val scope = rememberCoroutineScope()

    // 收集主题设置
    val themeMode =
        preferencesManager.themeMode.collectAsState(
            initial = UserPreferencesManager.THEME_MODE_LIGHT
        )
            .value
    val useSystemTheme = preferencesManager.useSystemTheme.collectAsState(initial = true).value
    val customPrimaryColor =
        preferencesManager.customPrimaryColor.collectAsState(initial = null).value
    val customSecondaryColor =
        preferencesManager.customSecondaryColor.collectAsState(initial = null).value
    val useCustomColors =
        preferencesManager.useCustomColors.collectAsState(initial = false).value

    // 收集背景图片设置
    val useBackgroundImage =
        preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
        preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val backgroundImageOpacity =
        preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f).value

    // 收集背景媒体类型和视频设置
    val backgroundMediaType =
        preferencesManager.backgroundMediaType.collectAsState(
            initial = UserPreferencesManager.MEDIA_TYPE_IMAGE
        )
            .value
    val videoBackgroundMuted =
        preferencesManager.videoBackgroundMuted.collectAsState(initial = true).value
    val videoBackgroundLoop =
        preferencesManager.videoBackgroundLoop.collectAsState(initial = true).value

    // 默认颜色定义
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()

    // 可变状态
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var primaryColorInput by remember {
        mutableStateOf(customPrimaryColor ?: defaultPrimaryColor)
    }
    var secondaryColorInput by remember {
        mutableStateOf(customSecondaryColor ?: defaultSecondaryColor)
    }
    var useCustomColorsInput by remember { mutableStateOf(useCustomColors) }

    // 背景图片状态
    var useBackgroundImageInput by remember { mutableStateOf(useBackgroundImage) }
    var backgroundImageUriInput by remember { mutableStateOf(backgroundImageUri) }
    var backgroundImageOpacityInput by remember { mutableStateOf(backgroundImageOpacity) }

    // 背景媒体类型和视频设置状态
    var backgroundMediaTypeInput by remember { mutableStateOf(backgroundMediaType) }
    var videoBackgroundMutedInput by remember { mutableStateOf(videoBackgroundMuted) }
    var videoBackgroundLoopInput by remember { mutableStateOf(videoBackgroundLoop) }

    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 视频播放器状态
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            // 添加更严格的内存限制
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        5000,  // 最小缓冲时间，减少到5秒
                        10000, // 最大缓冲时间，减少到10秒
                        500,   // 回放所需的最小缓冲
                        1000   // 重新缓冲后回放所需的最小缓冲
                    )
                    .setTargetBufferBytes(5 * 1024 * 1024) // 将缓冲限制为5MB
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build().apply {
                // 设置循环播放
                repeatMode = Player.REPEAT_MODE_ALL
                // 设置静音
                volume = if (videoBackgroundMutedInput) 0f else 1f
                playWhenReady = true

                // 如果有背景视频URI，加载它
                if (backgroundImageUriInput != null &&
                    backgroundMediaTypeInput ==
                    UserPreferencesManager.MEDIA_TYPE_VIDEO
                ) {
                    try {
                        val mediaItem = MediaItem.fromUri(Uri.parse(backgroundImageUriInput))
                        setMediaItem(mediaItem)
                        prepare()
                    } catch (e: Exception) {
                        Log.e("ThemeSettings", "视频加载错误", e)
                    }
                }
            }
    }

    // 当组件销毁时释放ExoPlayer资源
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.e("ThemeSettings", "ExoPlayer释放错误", e)
            }
        }
    }

    // 处理视频URI变化
    LaunchedEffect(backgroundImageUriInput, backgroundMediaTypeInput) {
        if (backgroundImageUriInput != null &&
            backgroundMediaTypeInput == UserPreferencesManager.MEDIA_TYPE_VIDEO
        ) {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(
                    MediaItem.fromUri(Uri.parse(backgroundImageUriInput))
                )
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                Log.e("ThemeSettings", "更新视频来源错误", e)
            }
        }
    }

    // 处理视频设置变化 - 添加错误处理
    LaunchedEffect(videoBackgroundMutedInput, videoBackgroundLoopInput) {
        try {
            exoPlayer.volume = if (videoBackgroundMutedInput) 0f else 1f
            exoPlayer.repeatMode =
                if (videoBackgroundLoopInput) Player.REPEAT_MODE_ALL
                else Player.REPEAT_MODE_OFF
        } catch (e: Exception) {
            Log.e("ThemeSettings", "更新视频设置错误", e)
        }
    }

    // 图片裁剪启动器
    val cropImageLauncher =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                val croppedUri = result.uriContent
                if (croppedUri != null) {
                    scope.launch {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(
                                context,
                                croppedUri
                            )
                        if (internalUri != null) {
                            backgroundImageUriInput =
                                internalUri.toString()
                            backgroundMediaTypeInput =
                                UserPreferencesManager
                                    .MEDIA_TYPE_IMAGE
                            preferencesManager.saveThemeSettings(
                                backgroundImageUri =
                                internalUri.toString(),
                                backgroundMediaType =
                                UserPreferencesManager
                                    .MEDIA_TYPE_IMAGE
                            )
                            showSaveSuccessMessage = true
                            Toast.makeText(
                                context,
                                "背景图片已保存",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        } else {
                            Toast.makeText(
                                context,
                                "无法复制图片，请选择其他图片",
                                Toast.LENGTH_LONG
                            )
                                .show()
                        }
                    }
                }
            } else if (result.error != null) {
                Toast.makeText(
                    context,
                    "裁剪图片失败: ${result.error!!.message}",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        }

    // 启动图片裁剪函数
    fun launchImageCrop(uri: Uri) {
        // 使用安全的方式获取系统颜色
        var primaryColor: Int
        var onPrimaryColor: Int
        var surfaceColor: Int
        var statusBarColor: Int

        // 检测系统暗色主题
        val isNightMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        try {
            // 尝试使用主题颜色 - 这是一个备选方案
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            primaryColor = typedValue.data

            // 尝试获取系统的状态栏颜色 (API 23+)
            try {
                context.theme.resolveAttribute(android.R.attr.colorPrimaryDark, typedValue, true)
                statusBarColor = typedValue.data
            } catch (e: Exception) {
                // 如果无法获取，使用主题色
                statusBarColor = primaryColor
            }

            context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            surfaceColor = typedValue.data

            onPrimaryColor =
                if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } catch (e: Exception) {
            // 使用后备颜色
            primaryColor = if (isNightMode) 0xFF9C27B0.toInt() else 0xFF6200EE.toInt() // 紫色
            statusBarColor = if (isNightMode) 0xFF7B1FA2.toInt() else 0xFF3700B3.toInt() // 深紫色
            surfaceColor =
                if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            onPrimaryColor =
                if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        }

        val cropOptions =
            CropImageContractOptions(
                uri,
                CropImageOptions().apply {
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    outputCompressFormat =
                        android.graphics.Bitmap.CompressFormat.JPEG
                    outputCompressQuality = 90
                    fixAspectRatio = false
                    cropMenuCropButtonTitle = "完成"
                    activityTitle = "裁剪图片"

                    // 设置主题配色
                    toolbarColor = primaryColor
                    toolbarBackButtonColor = onPrimaryColor
                    toolbarTitleColor = onPrimaryColor
                    activityBackgroundColor = surfaceColor
                    backgroundColor = surfaceColor

                    // 状态栏颜色
                    statusBarColor = statusBarColor

                    // 使用亮色/暗色主题
                    activityMenuIconColor = onPrimaryColor

                    // 改进用户体验
                    showCropOverlay = true
                    showProgressBar = true
                    multiTouchEnabled = true
                    autoZoomEnabled = true
                }
            )
        cropImageLauncher.launch(cropOptions)
    }

    // 图片/视频选择器启动器
    val mediaPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                // 检查是否为视频文件
                val isVideo = FileUtils.isVideoFile(context, uri)

                if (isVideo) {
                    // 视频文件检查大小
                    val isVideoSizeAcceptable =
                        FileUtils.checkVideoSize(context, uri, 30) // 限制为30MB

                    if (!isVideoSizeAcceptable) {
                        // 视频过大，显示警告
                        Toast.makeText(
                            context,
                            "视频文件过大，可能导致应用卡顿或崩溃。请选择小于30MB的视频。",
                            Toast.LENGTH_LONG
                        ).show()
                        return@rememberLauncherForActivityResult
                    }

                    // 视频文件大小合适，直接保存
                    scope.launch {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(
                                context,
                                uri
                            )

                        if (internalUri != null) {
                            backgroundImageUriInput =
                                internalUri.toString()
                            backgroundMediaTypeInput =
                                UserPreferencesManager
                                    .MEDIA_TYPE_VIDEO
                            preferencesManager.saveThemeSettings(
                                backgroundImageUri =
                                internalUri.toString(),
                                backgroundMediaType =
                                UserPreferencesManager
                                    .MEDIA_TYPE_VIDEO
                            )
                            showSaveSuccessMessage = true
                            Toast.makeText(
                                context,
                                "背景视频已保存",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        } else {
                            Toast.makeText(
                                context,
                                "无法复制视频，请选择其他视频",
                                Toast.LENGTH_LONG
                            )
                                .show()
                        }
                    }
                } else {
                    // 图片文件先启动裁剪
                    launchImageCrop(uri)
                }
            }
        }

    // Migrate existing background image if needed (on first load)
    LaunchedEffect(Unit) {
        // Check if we have a background image URI that starts with content://
        backgroundImageUri?.let { uriString ->
            if (uriString.startsWith("content://")) {
                try {
                    // Try to copy to internal storage
                    val uri = Uri.parse(uriString)
                    scope.launch {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(
                                context,
                                uri
                            )
                        if (internalUri != null) {
                            // Update the URI in preferences
                            preferencesManager.saveThemeSettings(
                                backgroundImageUri =
                                internalUri.toString()
                            )
                            // Update the local state
                            backgroundImageUriInput =
                                internalUri.toString()
                            Toast.makeText(
                                context,
                                "背景图片已迁移到内部存储",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "ThemeSettings",
                        "Error migrating background image",
                        e
                    )
                    // If migration fails, disable background image to prevent
                    // crashes
                    scope.launch {
                        preferencesManager.saveThemeSettings(
                            useBackgroundImage = false
                        )
                        useBackgroundImageInput = false
                        Toast.makeText(
                            context,
                            "无法访问旧的背景图片，已关闭背景图片功能",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                }
            }
        }
    }

    // 当设置变化时更新本地状态
    LaunchedEffect(
        themeMode,
        useSystemTheme,
        customPrimaryColor,
        customSecondaryColor,
        useCustomColors,
        useBackgroundImage,
        backgroundImageUri,
        backgroundImageOpacity,
        backgroundMediaType,
        videoBackgroundMuted,
        videoBackgroundLoop
    ) {
        themeModeInput = themeMode
        useSystemThemeInput = useSystemTheme
        if (customPrimaryColor != null) primaryColorInput = customPrimaryColor
        if (customSecondaryColor != null) secondaryColorInput = customSecondaryColor
        useCustomColorsInput = useCustomColors
        useBackgroundImageInput = useBackgroundImage
        backgroundImageUriInput = backgroundImageUri
        backgroundImageOpacityInput = backgroundImageOpacity
        backgroundMediaTypeInput = backgroundMediaType
        videoBackgroundMutedInput = videoBackgroundMuted
        videoBackgroundLoopInput = videoBackgroundLoop
    }

    // Get background image state to check if we need opaque cards
    val hasBackgroundImage =
        preferencesManager.useBackgroundImage.collectAsState(initial = false).value

    // Color surface modifier based on whether background image is used
    val cardModifier =
        if (hasBackgroundImage) {
            // Make cards fully opaque when background image is used
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f)
            )
        } else {
            CardDefaults.cardColors()
        }

    // Add a scroll state that we can control
    val scrollState = rememberScrollState()
    
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 系统主题设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            colors = cardModifier
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "系统主题",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 跟随系统主题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "跟随系统主题",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "开启后会根据系统深色模式自动切换主题",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = useSystemThemeInput,
                        onCheckedChange = {
                            useSystemThemeInput = it
                            scope.launch {
                                preferencesManager
                                    .saveThemeSettings(
                                        useSystemTheme = it
                                    )
                                showSaveSuccessMessage = true
                            }
                        }
                    )
                }

                // 只有当不跟随系统时才显示主题选择
                if (!useSystemThemeInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "选择主题",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 主题模式选择
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                            title = "浅色主题",
                            selected =
                            themeModeInput ==
                                UserPreferencesManager
                                    .THEME_MODE_LIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeModeInput =
                                    UserPreferencesManager
                                        .THEME_MODE_LIGHT
                                scope.launch {
                                    preferencesManager
                                        .saveThemeSettings(
                                            themeMode =
                                            UserPreferencesManager
                                                .THEME_MODE_LIGHT
                                        )
                                    showSaveSuccessMessage =
                                        true
                                }
                            }
                        )

                        ThemeModeOption(
                            title = "深色主题",
                            selected =
                            themeModeInput ==
                                UserPreferencesManager
                                    .THEME_MODE_DARK,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeModeInput =
                                    UserPreferencesManager
                                        .THEME_MODE_DARK
                                scope.launch {
                                    preferencesManager
                                        .saveThemeSettings(
                                            themeMode =
                                            UserPreferencesManager
                                                .THEME_MODE_DARK
                                        )
                                    showSaveSuccessMessage =
                                        true
                                }
                            }
                        )
                    }
                }
            }
        }

        // 自定义颜色设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = cardModifier
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "自定义配色",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 是否使用自定义颜色
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "使用自定义颜色",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "开启后可以自定义应用的主要配色",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = useCustomColorsInput,
                        onCheckedChange = {
                            useCustomColorsInput = it
                            scope.launch {
                                preferencesManager
                                    .saveThemeSettings(
                                        useCustomColors = it
                                    )
                                showSaveSuccessMessage = true
                            }
                        }
                    )
                }

                // 只有当启用自定义颜色时才显示颜色选择
                if (useCustomColorsInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "选择颜色",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 颜色选择
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 主色选择
                        ColorSelectionItem(
                            title = "主色",
                            color = Color(primaryColorInput),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentColorPickerMode = "primary"
                                showColorPicker = true
                            }
                        )

                        // 次色选择
                        ColorSelectionItem(
                            title = "次色",
                            color = Color(secondaryColorInput),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentColorPickerMode = "secondary"
                                showColorPicker = true
                            }
                        )
                    }

                    // Add a color preview section to show how colors will look
                    Text(
                        text = "色彩效果预览",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Create a mini-preview of how the selected colors will
                    // look
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        // Primary color demo
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            val primaryColor = Color(primaryColorInput)
                            val onPrimaryColor =
                                getTextColorForBackground(
                                    primaryColor
                                )

                            // Primary button preview
                            Surface(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(
                                        end = 8.dp
                                    ),
                                color = primaryColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                    modifier =
                                    Modifier.fillMaxSize(),
                                    contentAlignment =
                                    Alignment.Center
                                ) {
                                    Text(
                                        "主按钮",
                                        color =
                                        onPrimaryColor,
                                        style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                    )
                                }
                            }

                            // Secondary button preview
                            val secondaryColor =
                                Color(secondaryColorInput)
                            val onSecondaryColor =
                                getTextColorForBackground(
                                    secondaryColor
                                )

                            Surface(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                color = secondaryColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                    modifier =
                                    Modifier.fillMaxSize(),
                                    contentAlignment =
                                    Alignment.Center
                                ) {
                                    Text(
                                        "次要按钮",
                                        color =
                                        onSecondaryColor,
                                        style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                    )
                                }
                            }
                        }

                        Text(
                            text = "提示: 选择对比度高的颜色组合效果最佳",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 保存自定义颜色按钮
                    Button(
                        onClick = {
                            scope.launch {
                                preferencesManager
                                    .saveThemeSettings(
                                        customPrimaryColor =
                                        primaryColorInput,
                                        customSecondaryColor =
                                        secondaryColorInput
                                    )
                                showSaveSuccessMessage = true
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("保存颜色设置") }
                }
            }
        }

        // 背景图片设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = cardModifier
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "背景媒体设置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 是否使用背景图片
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "使用自定义背景",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "开启后可以选择自定义背景图片或视频",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = useBackgroundImageInput,
                        onCheckedChange = {
                            useBackgroundImageInput = it
                            scope.launch {
                                preferencesManager
                                    .saveThemeSettings(
                                        useBackgroundImage =
                                        it
                                    )
                                showSaveSuccessMessage = true
                            }
                        }
                    )
                }

                // 只有当启用背景图片时才显示图片选择
                if (useBackgroundImageInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 媒体类型选择
                    Text(
                        text = "媒体类型",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaTypeOption(
                            title = "图片",
                            icon = Icons.Default.Image,
                            selected =
                            backgroundMediaTypeInput ==
                                UserPreferencesManager
                                    .MEDIA_TYPE_IMAGE,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                backgroundMediaTypeInput =
                                    UserPreferencesManager
                                        .MEDIA_TYPE_IMAGE
                                if (backgroundImageUriInput != null
                                ) {
                                    // 如果已有背景，保存媒体类型
                                    scope.launch {
                                        preferencesManager
                                            .saveThemeSettings(
                                                backgroundMediaType =
                                                UserPreferencesManager
                                                    .MEDIA_TYPE_IMAGE
                                            )
                                        showSaveSuccessMessage =
                                            true
                                    }
                                }
                            }
                        )

                        MediaTypeOption(
                            title = "视频",
                            icon = Icons.Default.Videocam,
                            selected =
                            backgroundMediaTypeInput ==
                                UserPreferencesManager
                                    .MEDIA_TYPE_VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                backgroundMediaTypeInput =
                                    UserPreferencesManager
                                        .MEDIA_TYPE_VIDEO
                                if (backgroundImageUriInput != null
                                ) {
                                    // 如果已有背景，保存媒体类型
                                    scope.launch {
                                        preferencesManager
                                            .saveThemeSettings(
                                                backgroundMediaType =
                                                UserPreferencesManager
                                                    .MEDIA_TYPE_VIDEO
                                            )
                                        showSaveSuccessMessage =
                                            true
                                    }
                                }
                            }
                        )
                    }

                    // 当前选择的媒体预览
                    if (backgroundImageUriInput != null) {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(bottom = 16.dp)
                                .clip(
                                    RoundedCornerShape(
                                        8.dp
                                    )
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme
                                        .colorScheme
                                        .outline,
                                    RoundedCornerShape(
                                        8.dp
                                    )
                                )
                                .background(
                                    Color.Black.copy(
                                        alpha = 0.1f
                                    )
                                )
                        ) {
                            if (backgroundMediaTypeInput ==
                                UserPreferencesManager
                                    .MEDIA_TYPE_IMAGE
                            ) {
                                // 图片预览
                                Image(
                                    painter =
                                    rememberAsyncImagePainter(
                                        Uri.parse(
                                            backgroundImageUriInput
                                        )
                                    ),
                                    contentDescription =
                                    "背景图片预览",
                                    modifier =
                                    Modifier.fillMaxSize(),
                                    contentScale =
                                    ContentScale.Crop
                                )

                                // 裁剪按钮
                                IconButton(
                                    onClick = {
                                        backgroundImageUriInput
                                            ?.let {
                                                launchImageCrop(
                                                    Uri.parse(
                                                        it
                                                    )
                                                )
                                            }
                                    },
                                    modifier =
                                    Modifier
                                        .align(
                                            Alignment
                                                .TopEnd
                                        )
                                        .padding(
                                            8.dp
                                        )
                                        .background(
                                            MaterialTheme
                                                .colorScheme
                                                .surface
                                                .copy(
                                                    alpha =
                                                    0.7f
                                                ),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector =
                                        Icons.Default
                                            .Crop,
                                        contentDescription =
                                        "重新裁剪",
                                        tint =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    )
                                }
                            } else {
                                // 视频预览
                                // Capture the background color from the Composable context
                                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                                // Determine if it's a light theme
                                val isLightTheme =
                                    calculateLuminance(MaterialTheme.colorScheme.background) > 0.5f

                                AndroidView(
                                    factory = { ctx ->
                                        StyledPlayerView(
                                            ctx
                                        )
                                            .apply {
                                                player =
                                                    exoPlayer
                                                useController =
                                                    false
                                                layoutParams =
                                                    ViewGroup
                                                        .LayoutParams(
                                                            MATCH_PARENT,
                                                            MATCH_PARENT
                                                        )
                                                resizeMode =
                                                    AspectRatioFrameLayout
                                                        .RESIZE_MODE_ZOOM
                                                // Use the captured background color
                                                setBackgroundColor(backgroundColor)
                                                // Create a semi-transparent overlay on the player itself for opacity control
                                                foreground =
                                                    android.graphics.drawable.ColorDrawable(
                                                        android.graphics.Color.argb(
                                                            ((1f - backgroundImageOpacityInput) * 255).toInt(),
                                                            // Use white for light theme, black for dark theme
                                                            if (isLightTheme) 255 else 0,
                                                            if (isLightTheme) 255 else 0,
                                                            if (isLightTheme) 255 else 0
                                                        )
                                                    )
                                            }
                                    },
                                    update = { view ->
                                        // Update the foreground transparency when opacity changes
                                        view.foreground = android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.argb(
                                                ((1f - backgroundImageOpacityInput) * 255).toInt(),
                                                // Use white for light theme, black for dark theme
                                                if (isLightTheme) 255 else 0,
                                                if (isLightTheme) 255 else 0,
                                                if (isLightTheme) 255 else 0
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // 视频控制按钮
                                Row(
                                    modifier =
                                    Modifier
                                        .align(
                                            Alignment
                                                .TopEnd
                                        )
                                        .padding(
                                            8.dp
                                        )
                                ) {
                                    // 静音按钮
                                    IconButton(
                                        onClick = {
                                            videoBackgroundMutedInput =
                                                !videoBackgroundMutedInput
                                            scope
                                                .launch {
                                                    preferencesManager
                                                        .saveThemeSettings(
                                                            videoBackgroundMuted =
                                                            videoBackgroundMutedInput
                                                        )
                                                    showSaveSuccessMessage =
                                                        true
                                                }
                                        },
                                        modifier =
                                        Modifier
                                            .padding(
                                                end =
                                                8.dp
                                            )
                                            .background(
                                                MaterialTheme
                                                    .colorScheme
                                                    .surface
                                                    .copy(
                                                        alpha =
                                                        0.7f
                                                    ),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector =
                                            if (videoBackgroundMutedInput
                                            )
                                                Icons.Default
                                                    .VolumeOff
                                            else
                                                Icons.Default
                                                    .VolumeUp,
                                            contentDescription =
                                            if (videoBackgroundMutedInput
                                            )
                                                "取消静音"
                                            else
                                                "静音",
                                            tint =
                                            MaterialTheme
                                                .colorScheme
                                                .primary
                                        )
                                    }

                                    // 循环按钮
                                    IconButton(
                                        onClick = {
                                            videoBackgroundLoopInput =
                                                !videoBackgroundLoopInput
                                            scope
                                                .launch {
                                                    preferencesManager
                                                        .saveThemeSettings(
                                                            videoBackgroundLoop =
                                                            videoBackgroundLoopInput
                                                        )
                                                    showSaveSuccessMessage =
                                                        true

                                                    // 显示状态更改的Toast提示
                                                    Toast.makeText(
                                                        context,
                                                        if (videoBackgroundLoopInput) "循环播放已开启" else "循环播放已关闭",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        },
                                        modifier =
                                        Modifier.background(
                                            // 根据循环状态显示不同的背景色
                                            if (videoBackgroundLoopInput)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            else
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            CircleShape
                                        )
                                    ) {
                                        Icon(
                                            imageVector =
                                            if (videoBackgroundLoopInput
                                            )
                                                Icons.Default
                                                    .Loop
                                            else
                                                Icons.Outlined
                                                    .Loop,
                                            contentDescription =
                                            if (videoBackgroundLoopInput
                                            )
                                                "关闭循环"
                                            else
                                                "开启循环",
                                            tint =
                                            if (videoBackgroundLoopInput)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(bottom = 16.dp)
                                .clip(
                                    RoundedCornerShape(
                                        8.dp
                                    )
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme
                                        .colorScheme
                                        .outline,
                                    RoundedCornerShape(
                                        8.dp
                                    )
                                )
                                .background(
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "点击选择按钮来添加背景",
                                color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }

                    // 媒体选择按钮和类型
                    Button(
                        onClick = {
                            if (backgroundMediaTypeInput ==
                                UserPreferencesManager
                                    .MEDIA_TYPE_VIDEO
                            ) {
                                mediaPickerLauncher.launch(
                                    "video/*"
                                )
                            } else {
                                mediaPickerLauncher.launch(
                                    "image/*"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (backgroundMediaTypeInput ==
                                UserPreferencesManager
                                    .MEDIA_TYPE_VIDEO
                            )
                                "选择视频"
                            else "选择图片"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 不透明度调节
                    Text(
                        text =
                        "背景不透明度: ${(backgroundImageOpacityInput * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 记住上一次保存的值，以便debounce保存操作
                    var lastSavedOpacity by remember {
                        mutableStateOf(backgroundImageOpacityInput)
                    }
                    
                    // 使用 rememberUpdatedState 来稳定回调函数
                    val currentScope = rememberCoroutineScope()
                    
                    // 创建一个更简单的 disableScrollWhileDragging 状态
                    var isDragging by remember { mutableStateOf(false) }
                    
                    // 自定义交互源，可以帮助我们监控拖动状态
                    val interactionSource = remember { MutableInteractionSource() }
                    
                    // 监听拖动状态
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            when (interaction) {
                                is DragInteraction.Start -> isDragging = true
                                is DragInteraction.Stop -> isDragging = false
                                is DragInteraction.Cancel -> isDragging = false
                            }
                        }
                    }
                    
                    // 如果是在拖动状态，暂时锁定滚动
                    if (isDragging) {
                        DisposableEffect(Unit) {
                            val previousScrollValue = scrollState.value
                            onDispose {
                                // Nothing to do on dispose
                            }
                        }
                    }
                    
                    // 创建一个固定的更新回调和完成回调
                    val updateOpacity = remember {
                        { value: Float ->
                            backgroundImageOpacityInput = value
                        }
                    }
                    
                    val onValueChangeFinished = remember {
                        {
                            if (kotlin.math.abs(lastSavedOpacity - backgroundImageOpacityInput) > 0.01f) {
                                currentScope.launch {
                                    preferencesManager.saveThemeSettings(
                                        backgroundImageOpacity = backgroundImageOpacityInput
                                    )
                                    lastSavedOpacity = backgroundImageOpacityInput
                                    showSaveSuccessMessage = true
                                }
                            }
                        }
                    }
                    
                    // 使用Box包装滑块，解决拖动问题
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        Slider(
                            value = backgroundImageOpacityInput,
                            onValueChange = updateOpacity,
                            onValueChangeFinished = onValueChangeFinished,
                            valueRange = 0.1f..1f,
                            interactionSource = interactionSource,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    
                    // 增加一个间隔，确保滑块下方有足够空间
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 重置按钮
        OutlinedButton(
            onClick = {
                scope.launch {
                    preferencesManager.resetThemeSettings()
                    // 重置后更新本地状态
                    themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
                    useSystemThemeInput = true
                    primaryColorInput = defaultPrimaryColor
                    secondaryColorInput = defaultSecondaryColor
                    useCustomColorsInput = false
                    useBackgroundImageInput = false
                    backgroundImageUriInput = null
                    backgroundImageOpacityInput = 0.3f
                    backgroundMediaTypeInput =
                        UserPreferencesManager.MEDIA_TYPE_IMAGE
                    videoBackgroundMutedInput = true
                    videoBackgroundLoopInput = true
                    showSaveSuccessMessage = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) { Text("重置为默认主题") }

        // 显示保存成功提示
        if (showSaveSuccessMessage) {
            LaunchedEffect(key1 = showSaveSuccessMessage) {
                kotlinx.coroutines.delay(2000)
                showSaveSuccessMessage = false
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { Text(text = "设置已保存", color = MaterialTheme.colorScheme.primary) }
        }

        // 颜色选择器对话框
        if (showColorPicker) {
            ColorPickerDialog(
                showColorPicker = showColorPicker,
                currentColorPickerMode = currentColorPickerMode,
                primaryColorInput = primaryColorInput,
                secondaryColorInput = secondaryColorInput,
                onColorSelected = { primaryColor, secondaryColor ->
                    primaryColor?.let { primaryColorInput = it }
                    secondaryColor?.let { secondaryColorInput = it }

                    // Save the colors
                    scope.launch {
                        if (currentColorPickerMode == "primary" &&
                            primaryColor != null
                        ) {
                            preferencesManager.saveThemeSettings(
                                customPrimaryColor = primaryColor
                            )
                        } else if (currentColorPickerMode == "secondary" &&
                            secondaryColor != null
                        ) {
                            preferencesManager.saveThemeSettings(
                                customSecondaryColor =
                                secondaryColor
                            )
                        }
                        showSaveSuccessMessage = true
                    }
                },
                onDismiss = { showColorPicker = false }
            )
        }
    }
}

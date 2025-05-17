package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.FileUtils
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.launch

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
    val useCustomColors = preferencesManager.useCustomColors.collectAsState(initial = false).value

    // 收集背景图片设置
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val backgroundImageOpacity =
            preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f).value

    // 默认颜色定义
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()

    // 可变状态
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var primaryColorInput by remember { mutableStateOf(customPrimaryColor ?: defaultPrimaryColor) }
    var secondaryColorInput by remember {
        mutableStateOf(customSecondaryColor ?: defaultSecondaryColor)
    }
    var useCustomColorsInput by remember { mutableStateOf(useCustomColors) }

    // 背景图片状态
    var useBackgroundImageInput by remember { mutableStateOf(useBackgroundImage) }
    var backgroundImageUriInput by remember { mutableStateOf(backgroundImageUri) }
    var backgroundImageOpacityInput by remember { mutableStateOf(backgroundImageOpacity) }

    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 图片选择器启动器
    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                if (uri != null) {
                    // Instead of requesting persisted permissions, copy the file to internal
                    // storage
                    scope.launch {
                        // Show loading indicator or message if needed
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri)

                        if (internalUri != null) {
                            backgroundImageUriInput = internalUri.toString()
                            preferencesManager.saveThemeSettings(
                                    backgroundImageUri = internalUri.toString()
                            )
                            showSaveSuccessMessage = true
                            Toast.makeText(context, "背景图片已保存", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "无法复制图片，请选择其他图片", Toast.LENGTH_LONG).show()
                        }
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
                        val internalUri = FileUtils.copyFileToInternalStorage(context, uri)
                        if (internalUri != null) {
                            // Update the URI in preferences
                            preferencesManager.saveThemeSettings(
                                    backgroundImageUri = internalUri.toString()
                            )
                            // Update the local state
                            backgroundImageUriInput = internalUri.toString()
                            Toast.makeText(context, "背景图片已迁移到内部存储", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ThemeSettings", "Error migrating background image", e)
                    // If migration fails, disable background image to prevent crashes
                    scope.launch {
                        preferencesManager.saveThemeSettings(useBackgroundImage = false)
                        useBackgroundImageInput = false
                        Toast.makeText(context, "无法访问旧的背景图片，已关闭背景图片功能", Toast.LENGTH_LONG).show()
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
            backgroundImageOpacity
    ) {
        themeModeInput = themeMode
        useSystemThemeInput = useSystemTheme
        if (customPrimaryColor != null) primaryColorInput = customPrimaryColor
        if (customSecondaryColor != null) secondaryColorInput = customSecondaryColor
        useCustomColorsInput = useCustomColors
        useBackgroundImageInput = useBackgroundImage
        backgroundImageUriInput = backgroundImageUri
        backgroundImageOpacityInput = backgroundImageOpacity
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // 系统主题设置
        Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "跟随系统主题", style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = "开启后会根据系统深色模式自动切换主题",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useSystemThemeInput,
                            onCheckedChange = {
                                useSystemThemeInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(useSystemTheme = it)
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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                                title = "浅色主题",
                                selected =
                                        themeModeInput == UserPreferencesManager.THEME_MODE_LIGHT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    themeModeInput = UserPreferencesManager.THEME_MODE_LIGHT
                                    scope.launch {
                                        preferencesManager.saveThemeSettings(
                                                themeMode = UserPreferencesManager.THEME_MODE_LIGHT
                                        )
                                        showSaveSuccessMessage = true
                                    }
                                }
                        )

                        ThemeModeOption(
                                title = "深色主题",
                                selected = themeModeInput == UserPreferencesManager.THEME_MODE_DARK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    themeModeInput = UserPreferencesManager.THEME_MODE_DARK
                                    scope.launch {
                                        preferencesManager.saveThemeSettings(
                                                themeMode = UserPreferencesManager.THEME_MODE_DARK
                                        )
                                        showSaveSuccessMessage = true
                                    }
                                }
                        )
                    }
                }
            }
        }

        // 自定义颜色设置
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = "自定义配色",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // 是否使用自定义颜色
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "使用自定义颜色", style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = "开启后可以自定义应用的主要配色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useCustomColorsInput,
                            onCheckedChange = {
                                useCustomColorsInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(useCustomColors = it)
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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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

                    // Create a mini-preview of how the selected colors will look
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        // Primary color demo
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            val primaryColor = Color(primaryColorInput)
                            val onPrimaryColor = getTextColorForBackground(primaryColor)

                            // Primary button preview
                            Surface(
                                    modifier =
                                            Modifier.weight(1f).height(40.dp).padding(end = 8.dp),
                                    color = primaryColor,
                                    shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            "主按钮",
                                            color = onPrimaryColor,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Secondary button preview
                            val secondaryColor = Color(secondaryColorInput)
                            val onSecondaryColor = getTextColorForBackground(secondaryColor)

                            Surface(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    color = secondaryColor,
                                    shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            "次要按钮",
                                            color = onSecondaryColor,
                                            style = MaterialTheme.typography.bodyMedium
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
                                    preferencesManager.saveThemeSettings(
                                            customPrimaryColor = primaryColorInput,
                                            customSecondaryColor = secondaryColorInput
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
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = "背景图片设置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // 是否使用背景图片
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "使用自定义背景图片", style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = "开启后可以选择自定义背景图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                            checked = useBackgroundImageInput,
                            onCheckedChange = {
                                useBackgroundImageInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(useBackgroundImage = it)
                                    showSaveSuccessMessage = true
                                }
                            }
                    )
                }

                // 只有当启用背景图片时才显示图片选择
                if (useBackgroundImageInput) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 当前选择的图片预览
                    if (backgroundImageUriInput != null) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(150.dp)
                                                .padding(bottom = 16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                        Color.Black.copy(alpha = 0.1f)
                                                ) // Add subtle dark backdrop
                        ) {
                            Image(
                                    painter =
                                            rememberAsyncImagePainter(
                                                    Uri.parse(backgroundImageUriInput)
                                            ),
                                    contentDescription = "背景图片预览",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(150.dp)
                                                .padding(bottom = 16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                            Text(
                                    text = "点击选择图片按钮来添加背景",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 选择图片按钮
                    Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                    ) { Text("选择图片") }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 不透明度调节
                    Text(
                            text = "背景不透明度: ${(backgroundImageOpacityInput * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Slider(
                            value = backgroundImageOpacityInput,
                            onValueChange = {
                                backgroundImageOpacityInput = it
                                scope.launch {
                                    preferencesManager.saveThemeSettings(
                                            backgroundImageOpacity = it
                                    )
                                    showSaveSuccessMessage = true
                                }
                            },
                            valueRange = 0.1f..1f,
                            steps = 18,
                            colors =
                                    SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor =
                                                    MaterialTheme.colorScheme.surfaceVariant
                                    )
                    )
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
                        showSaveSuccessMessage = true
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) { Text("重置为默认主题") }

        // 显示保存成功提示
        if (showSaveSuccessMessage) {
            LaunchedEffect(key1 = showSaveSuccessMessage) {
                kotlinx.coroutines.delay(2000)
                showSaveSuccessMessage = false
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "设置已保存", color = MaterialTheme.colorScheme.primary)
            }
        }

        // 颜色选择器对话框
        if (showColorPicker) {
            val currentColorForPicker =
                    if (currentColorPickerMode == "primary") primaryColorInput
                    else secondaryColorInput
            val currentColor = Color(currentColorForPicker)
            val pickerController = rememberColorPickerController()

            // Set initial color
            LaunchedEffect(pickerController) { pickerController.setWheelColor(currentColor) }

            // 创建一个可变状态，用于实时预览颜色
            var previewColor by remember { mutableStateOf(currentColor) }

            // 监听颜色变化
            LaunchedEffect(currentColor) { previewColor = currentColor }

            // Define aesthetically pleasing color presets - improved with Material Design colors
            val materialColors =
                    listOf(
                            // Material Design primary colors
                            Color(0xFF6200EE), // Purple 500 (Material primary)
                            Color(0xFF3700B3), // Purple 700 (Material primary variant)
                            Color(0xFF03DAC6), // Teal 200 (Material secondary)
                            Color(0xFF018786), // Teal 700 (Material secondary variant)
                            Color(0xFF1976D2), // Blue 700
                            Color(0xFF0D47A1), // Blue 900
                            Color(0xFF1E88E5), // Blue 600

                            // More Material colors with good contrast
                            Color(0xFFD32F2F), // Red 700
                            Color(0xFF7B1FA2), // Purple 700
                            Color(0xFF388E3C), // Green 700
                            Color(0xFFE64A19), // Deep Orange 700
                            Color(0xFFF57C00), // Orange 700
                            Color(0xFF5D4037), // Brown 700
                            Color(0xFF455A64) // Blue Grey 700
                    )

            AlertDialog(
                    onDismissRequest = { showColorPicker = false },
                    title = {
                        Text(
                                text = if (currentColorPickerMode == "primary") "选择主色" else "选择次色",
                                style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Column {
                            // Live color preview - use solid backgrounds
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(bottom = 16.dp)
                                                    .background(
                                                            MaterialTheme.colorScheme.surface.copy(
                                                                    alpha = 1f
                                                            ),
                                                            RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(8.dp)
                            ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Color sample
                                    Box(
                                            modifier =
                                                    Modifier.size(80.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(previewColor)
                                                            .border(
                                                                    1.dp,
                                                                    MaterialTheme.colorScheme
                                                                            .outline,
                                                                    RoundedCornerShape(8.dp)
                                                            )
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Text preview
                                    Column {
                                        // Show contrast example
                                        val textColor = getTextColorForBackground(previewColor)
                                        Surface(
                                                modifier = Modifier.width(120.dp).height(40.dp),
                                                color = previewColor,
                                                shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                        "示例文本",
                                                        color = textColor,
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }

                                        // Add contrast rating
                                        val contrastRating =
                                                if (isHighContrast(previewColor)) "高对比度 ✓"
                                                else "低对比度 ⚠"
                                        val contrastColor =
                                                if (isHighContrast(previewColor)) Color(0xFF388E3C)
                                                else Color(0xFFD32F2F)

                                        Text(
                                                text = contrastRating,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = contrastColor,
                                                modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Color display preview with alpha tiles
                            AlphaTile(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(60.dp)
                                                    .padding(bottom = 16.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                    controller = pickerController
                            )

                            // HSV Color Picker
                            HsvColorPicker(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(300.dp)
                                                    .padding(vertical = 8.dp),
                                    controller = pickerController,
                                    onColorChanged = { colorEnvelope: ColorEnvelope ->
                                        if (colorEnvelope.fromUser) {
                                            val newColor = colorEnvelope.color.toArgb()
                                            if (currentColorPickerMode == "primary") {
                                                primaryColorInput = newColor
                                            } else {
                                                secondaryColorInput = newColor
                                            }
                                            previewColor = colorEnvelope.color
                                        }
                                    }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Brightness slider
                            BrightnessSlider(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(30.dp)
                                                    .padding(vertical = 4.dp),
                                    controller = pickerController
                            )

                            // Alpha slider
                            AlphaSlider(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(30.dp)
                                                    .padding(vertical = 4.dp),
                                    controller = pickerController,
                                    tileOddColor = Color.White,
                                    tileEvenColor = Color.LightGray
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Preset colors title
                            Text(
                                    text = "推荐颜色",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Preset colors grid
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                materialColors.take(7).forEach { color ->
                                    PresetColorItem(color) {
                                        if (currentColorPickerMode == "primary") {
                                            primaryColorInput = it.toArgb()
                                        } else {
                                            secondaryColorInput = it.toArgb()
                                        }
                                        pickerController.setWheelColor(it)
                                        previewColor = it
                                    }
                                }
                            }

                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                materialColors.takeLast(7).forEach { color ->
                                    PresetColorItem(color) {
                                        if (currentColorPickerMode == "primary") {
                                            primaryColorInput = it.toArgb()
                                        } else {
                                            secondaryColorInput = it.toArgb()
                                        }
                                        pickerController.setWheelColor(it)
                                        previewColor = it
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    // 保存颜色选择
                                    scope.launch {
                                        if (currentColorPickerMode == "primary") {
                                            preferencesManager.saveThemeSettings(
                                                    customPrimaryColor = primaryColorInput
                                            )
                                        } else {
                                            preferencesManager.saveThemeSettings(
                                                    customSecondaryColor = secondaryColorInput
                                            )
                                        }
                                        showSaveSuccessMessage = true
                                        showColorPicker = false
                                    }
                                }
                        ) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showColorPicker = false }) { Text("取消") }
                    }
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
        title: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
    Card(
            modifier = modifier.clickable(onClick = onClick),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (selected)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface
                    ),
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ColorSelectionItem(
        title: String,
        color: Color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
    Column(
            modifier = modifier.clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}

@Composable
private fun PresetColorItem(color: Color, onSelect: (Color) -> Unit) {
    Box(
            modifier =
                    Modifier.size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, Color.White, CircleShape)
                            .clickable { onSelect(color) }
    )
}

// Add helper function to determine text color based on background
private fun getTextColorForBackground(backgroundColor: Color): Color {
    val luminance =
            0.299 * backgroundColor.red +
                    0.587 * backgroundColor.green +
                    0.114 * backgroundColor.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

// Helper function to determine if a color has high contrast with both black and white
private fun isHighContrast(backgroundColor: Color): Boolean {
    val luminance =
            0.299 * backgroundColor.red +
                    0.587 * backgroundColor.green +
                    0.114 * backgroundColor.blue

    // Colors in the middle range (not too light, not too dark) tend to have low contrast with both
    // black and white
    // A good high contrast color is either fairly dark or fairly light
    return luminance < 0.3 || luminance > 0.7
}

package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R

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
            textView.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(html)
            }
        }
    )
}

@Composable
fun AboutScreen() {
        val context = LocalContext.current

        // 获取应用版本信息
        val appVersion = remember {
                try {
                        val packageInfo =
                                context.packageManager.getPackageInfo(context.packageName, 0)
                        packageInfo.versionName
                } catch (e: PackageManager.NameNotFoundException) {
                        "未知"
                }
        }

        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // App Logo
                Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(120.dp).padding(bottom = 16.dp)
                )

                // App Name
                Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // App Version
                Text(
                        text = stringResource(id = R.string.about_version, appVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 24.dp)
                )

                // Card with app information
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.Start
                        ) {
                                Text(
                                        text = stringResource(id = R.string.about_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Text(
                                        text = stringResource(id = R.string.about_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // 开发者信息 (使用HTML链接)
                                HtmlText(
                                        html = stringResource(id = R.string.about_developer),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Text(
                                        text = stringResource(id = R.string.about_contact),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // GitHub链接 (使用HTML链接)
                                HtmlText(
                                        html = stringResource(id = R.string.about_website),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                Text(
                                        text = stringResource(id = R.string.about_copyright),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                        }
                }
        }
}

package com.ai.assistance.operit.ui.features.agreement.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit) {
    val scrollState = rememberScrollState()
    var isButtonEnabled by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(5) }

    // Timer to enable the button after 5 seconds
    LaunchedEffect(Unit) {
        repeat(5) {
            delay(1000)
            remainingSeconds--
        }
        isButtonEnabled = true
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
                text = "用户协议及隐私政策",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = "请在使用我们的应用前，仔细阅读以下协议",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Agreement text content
        Box(
                modifier =
                        Modifier.weight(1f)
                                .fillMaxWidth()
                                .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.medium
                                )
                                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Text(
                        text = "用户协议",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text =
                                """
                        感谢您选择使用我们的应用！本协议描述了您在使用我们的应用程序时应遵守的规则和条款。

                        1. 服务条款
                           我们提供的服务可能会不时更新，我们保留修改、暂停或终止服务的权利。

                        2. 用户账户
                           您可能需要创建账户以使用某些功能。您负责保护账户信息的安全，并对账户内的所有活动承担责任。

                        3. 隐私政策
                           我们重视您的隐私。我们收集和使用您的信息的方式将在隐私政策中详细说明。

                        4. 用户内容
                           您在应用中发布的内容仍然是您的，但您授予我们使用、分发和展示该内容的权利。

                        5. 禁止行为
                           您不得使用我们的应用从事任何违法或有害的活动，包括但不限于侵犯他人权利、发送恶意软件等。

                        6. 终止条款
                           我们保留在您违反协议时终止您使用权的权利。

                        7. 免责声明
                           我们的应用按"现状"提供，不提供任何形式的保证。

                        8. 责任限制
                           在法律允许的最大范围内，我们不对您使用我们应用程序所造成的任何损失负责。

                        9. 修改条款
                           我们可能会不时修改本协议。继续使用我们的应用意味着您接受修改后的条款。

                        10. 适用法律
                            本协议受中华人民共和国法律管辖。
                    """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text = "隐私政策",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text =
                                """
                        我们致力于保护您的隐私和个人信息。本隐私政策说明了我们如何收集、使用和保护您提供给我们的信息。

                        1. 信息收集
                           我们可能会收集以下类型的信息：
                           - 个人信息：如姓名、电子邮件地址等
                           - 设备信息：如设备类型、操作系统版本等
                           - 使用数据：如应用功能使用情况、交互记录等

                        2. 信息使用
                           我们使用收集的信息来：
                           - 提供、维护和改进我们的服务
                           - 响应您的请求和提供客户支持
                           - 发送服务通知和更新

                        3. 信息共享
                           除非在以下情况，我们不会与第三方共享您的个人信息：
                           - 经您同意
                           - 为满足法律要求
                           - 保护我们的权利和财产

                        4. 数据安全
                           我们采取合理的安全措施来保护您的信息免受未经授权的访问或披露。

                        5. 您的权利
                           您有权访问、更正或删除我们持有的关于您的个人信息。

                        6. 儿童隐私
                           我们的服务不面向13岁以下的儿童。

                        7. 隐私政策更新
                           我们可能会不时更新本隐私政策。我们将通过应用内通知或其他适当方式通知您任何重大变更。

                        8. 联系我们
                           如果您对本隐私政策有任何疑问，请联系我们的支持团队。
                    """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Agreement button
        Button(
                onClick = onAgreementAccepted,
                enabled = isButtonEnabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor =
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledContentColor =
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
        ) {
            Text(
                    text = if (isButtonEnabled) "我已阅读并同意上述协议" else "请等待 $remainingSeconds 秒...",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

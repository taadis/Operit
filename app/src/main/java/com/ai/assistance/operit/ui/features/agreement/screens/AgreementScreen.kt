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
                        感谢您选择使用我们的应用！在开始使用前，请务必仔细阅读以下重要声明：

                        1. 免责声明
                           本软件具有强大的功能，可能存在潜在破坏力。用户使用本软件进行的任何操作及其后果，均与开发者无关，我们不承担任何责任。

                        2. 官方下载渠道
                           为确保您的安全，请务必从我们的官方GitHub仓库下载本软件。从其他渠道下载可能存在安全风险，我们不对此负责。

                        3. 开源协议
                           本软件是开源项目，遵循GPL（GNU通用公共许可证）协议。用户可以自由使用、修改和分发本软件，但必须遵守GPL协议的相关条款。

                        4. 第三方使用
                           我们不对第三方如何使用、修改或分发本软件负责。任何基于本软件的衍生作品或相关行为与原开发者无关。

                        5. 用户行为
                           用户应自行承担使用本软件的全部风险和法律责任。请确保您的使用行为符合当地法律法规。

                        6. 终止条款
                           我们保留在用户违反协议时终止其使用权的权利。

                        7. 无保证声明
                           本软件按"现状"提供，不提供任何形式的保证，包括但不限于适用性、可靠性或准确性。

                        8. 责任限制
                           在法律允许的最大范围内，我们不对因使用本软件造成的任何直接或间接损失负责。

                        9. 协议修改
                           我们可能会不时修改本协议，修改后的协议将在发布时生效。

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
                        关于您的隐私，请了解以下重要信息：

                        1. 无数据收集
                           我们不会主动收集您的任何个人信息或使用数据。本应用的设计理念是尊重用户隐私，不进行任何形式的数据收集。

                        2. 本地处理
                           所有数据处理均在您的设备本地完成，不会上传到我们的服务器或任何第三方服务器。因此，您的隐私数据安全完全由您自己控制。

                        3. 第三方服务
                           如果本应用集成了任何第三方服务或库，其数据处理行为不在我们的控制范围内。请参阅相关第三方的隐私政策。

                        4. 权限说明
                           本应用可能需要某些系统权限才能正常运行。这些权限仅用于应用功能实现，不会用于收集您的个人信息。

                        5. 开源透明
                           作为一个开源项目，我们的代码对所有人公开透明。您可以在GitHub上查看源代码，验证我们的隐私声明。

                        6. 无责任声明
                           由于我们不收集、不存储用户数据，因此对于用户数据泄露、丢失或被盗用的情况不承担责任。

                        7. 未成年人保护
                           本应用不专门针对未成年人，也不会有意收集未成年人的信息。

                        8. 政策更新
                           我们可能会更新本隐私政策，更新后的政策将在GitHub发布并在应用中更新。

                        9. 联系我们
                           如果您对隐私政策有任何疑问，请通过GitHub项目页面联系我们。
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
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.5f
                                                ),
                                        disabledContentColor =
                                                MaterialTheme.colorScheme.onPrimary.copy(
                                                        alpha = 0.7f
                                                )
                                )
                ) {
                        Text(
                                text =
                                        if (isButtonEnabled) "我已阅读并同意上述协议"
                                        else "请等待 $remainingSeconds 秒...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}

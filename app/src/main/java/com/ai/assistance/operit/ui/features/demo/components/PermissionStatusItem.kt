package com.ai.assistance.operit.ui.features.demo.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionStatusItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
        )

        Text(
                text = if (isGranted) "已授权" else "未授权",
                color =
                        if (isGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
        )
    }
}

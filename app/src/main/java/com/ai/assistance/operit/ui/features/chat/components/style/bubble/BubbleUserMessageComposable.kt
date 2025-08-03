
package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun BubbleUserMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color
) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager(context) }
    val avatarUri by preferencesManager.customUserAvatarUri.collectAsState(initial = null)
    val avatarShapePref by preferencesManager.avatarShape.collectAsState(initial = UserPreferencesManager.AVATAR_SHAPE_CIRCLE)
    val avatarCornerRadius by preferencesManager.avatarCornerRadius.collectAsState(initial = 8f)

    // Add logging
    LaunchedEffect(avatarUri) {
        Log.d("UserAvatar", "Loading user avatar from: $avatarUri")
    }

    val avatarShape = remember(avatarShapePref, avatarCornerRadius) {
        if (avatarShapePref == UserPreferencesManager.AVATAR_SHAPE_SQUARE) {
            RoundedCornerShape(avatarCornerRadius.dp)
        } else {
            CircleShape
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        // Message bubble
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 64.dp)
                .defaultMinSize(minHeight = 44.dp),
            shape = RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp),
            color = backgroundColor,
            tonalElevation = 2.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Avatar
        if (avatarUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(avatarShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(avatarShape),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
} 
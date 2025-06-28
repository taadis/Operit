package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory

// Constants
private const val CHAT_HISTORY_PAGE_SIZE = 20

// ChatHistorySelector as a top-level composable
@Composable
fun ChatHistorySelector(
        modifier: Modifier = Modifier,
        onNewChat: () -> Unit,
        onSelectChat: (String) -> Unit,
        onDeleteChat: (String) -> Unit,
        chatHistories: List<ChatHistory>,
        currentId: String?
) {
    // State to track how many items to load
    var itemsToLoad by remember { mutableStateOf(CHAT_HISTORY_PAGE_SIZE) }
    val historySelectorListState = rememberLazyListState()

    // Listen for scroll events to load more items when approaching the end
    LaunchedEffect(historySelectorListState) {
        snapshotFlow {
            historySelectorListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
                .collect { lastVisibleIndex ->
                    val totalItemsCount = chatHistories.size
                    if (totalItemsCount > 0 &&
                                    lastVisibleIndex >= itemsToLoad - 5 &&
                                    itemsToLoad < totalItemsCount
                    ) {
                        // Load the next page when user is 5 items from the end
                        itemsToLoad =
                                (itemsToLoad + CHAT_HISTORY_PAGE_SIZE).coerceAtMost(totalItemsCount)
                    }
                }
    }

    Column(modifier = modifier) {
        // 标题
        Text(
                text = stringResource(R.string.chat_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
        )

        // 新建对话按钮
        Button(
                onClick = { onNewChat() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.new_chat))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        // 对话历史列表
        LazyColumn(
                state = historySelectorListState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        ) {
            // Only load a subset of items for better performance
            val itemsToShow = chatHistories.take(itemsToLoad)

            items(items = itemsToShow, key = { it.id }) { history ->
                val isSelected = history.id == currentId

                // Simplify by calculating colors directly in composable context
                val surfaceColor =
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface

                val textColor =
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface

                Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        color = surfaceColor,
                        shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 对话标题，点击切换到该对话
                        Text(
                                text = history.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                        Modifier.weight(1f).clickable { onSelectChat(history.id) }
                        )

                        // 删除按钮
                        IconButton(
                                onClick = { onDeleteChat(history.id) },
                                colors =
                                        IconButtonDefaults.iconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                        )
                        ) {
                            Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

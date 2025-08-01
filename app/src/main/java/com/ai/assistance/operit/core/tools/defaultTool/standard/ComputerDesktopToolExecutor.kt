package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.tools.ComputerDesktopActionResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.chat.webview.computer.ComputerDesktopManager
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.WebAutomationTaskResultData
import kotlinx.coroutines.flow.flow

class ComputerDesktopToolExecutor(private val context: Context) {

    private val desktopManager = ComputerDesktopManager

    suspend fun executeTool(tool: AITool): ToolResult {
        return withContext(Dispatchers.Main) {
            try {
                // Ensure manager is initialized
                desktopManager.initialize(context)
                desktopManager.ensureInitialTab()

                val resultData = when (tool.name) {
                    "computer_get_tabs" -> getTabs()
                    "computer_switch_to_tab" -> switchToTab(tool)
                    "computer_open_desktop" -> openDesktop()
                    "computer_open_browser" -> openBrowser(tool)
                    "computer_get_page_info" -> getPageInfo()
                    "computer_click_element" -> clickElement(tool)
                    "computer_scroll_by" -> scrollBy(tool)
                    "computer_input_text" -> inputText(tool)
                    "computer_close_tab" -> closeTab(tool)
                    "computer_go_back" -> goBack()
                    else -> null
                }

                if (resultData != null) {
                    ToolResult(toolName = tool.name, success = true, result = resultData)
                } else {
                    ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Unknown computer tool: ${tool.name}")
                }
            } catch (e: Exception) {
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error executing computer tool: ${e.message}")
            }
        }
    }

    suspend fun awaitPageLoaded(tool: AITool): ToolResult {
        val timeout = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() ?: 10000L
        val success = desktopManager.awaitPageLoaded(timeout)
        return if (success) {
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData("Page loaded successfully.")
            )
        } else {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Page failed to load within the ${timeout}ms timeout."
            )
        }
    }

    fun automateWebTask(tool: AITool): Flow<ToolResult> {
        return flow {
            val taskGoal = tool.parameters.find { it.name == "task_goal" }?.value ?: "No goal specified."
            val enhancedAIService = EnhancedAIService.getInstance(context)

            val executedCommands = mutableListOf<String>()

            enhancedAIService.executeWebAutomationTask(taskGoal).map { stepResult ->
                stepResult.command?.let { executedCommands.add(it.toString()) }

                if (stepResult.command?.type == "complete" || stepResult.command?.type == "interrupt") {
                    ToolResult(
                        toolName = tool.name,
                        success = stepResult.command.type == "complete",
                        result = WebAutomationTaskResultData(
                            taskGoal = taskGoal,
                            finalState = stepResult.command.type,
                            finalMessage = stepResult.explanation ?: "Task finished.",
                            executedCommands = executedCommands
                        )
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = true, // Intermediate steps are considered successful
                        result = StringResultData(stepResult.explanation ?: "Executing...")
                    )
                }
            }.collect{ emit(it) }
        }
    }

    private fun getTabs(): ComputerDesktopActionResultData {
        val tabs = desktopManager.getTabs()
        val tabInfos = tabs.mapIndexed { index, tab ->
            ComputerDesktopActionResultData.ComputerTabInfo(
                id = tab.id,
                title = tab.title,
                url = tab.url,
                isActive = index == desktopManager.currentTabIndex.value
            )
        }
        return ComputerDesktopActionResultData(
            action = "get_tabs",
            resultSummary = "Successfully retrieved ${tabs.size} open tabs.",
            tabs = tabInfos
        )
    }

    private fun switchToTab(tool: AITool): ComputerDesktopActionResultData {
        val tabId = tool.parameters.find { it.name == "tab_id" }?.value
        val tabIndex = tool.parameters.find { it.name == "tab_index" }?.value?.toIntOrNull()

        when {
            tabId != null -> desktopManager.switchToTab(tabId)
            tabIndex != null -> desktopManager.switchToTab(tabIndex)
            else -> return ComputerDesktopActionResultData(
                action = "switch_to_tab",
                resultSummary = "Failed: Missing tab_id or tab_index parameter."
            )
        }
        return ComputerDesktopActionResultData(
            action = "switch_to_tab",
            target = tabId ?: tabIndex.toString(),
            resultSummary = "Successfully switched to tab."
        )
    }

    private fun openDesktop(): ComputerDesktopActionResultData {
        desktopManager.openDesktop()
        return ComputerDesktopActionResultData(
            action = "open_desktop",
            resultSummary = "New desktop tab opened."
        )
    }

    private fun openBrowser(tool: AITool): ComputerDesktopActionResultData {
        val url = tool.parameters.find { it.name == "url" }?.value
        desktopManager.openBrowser(url)
        return ComputerDesktopActionResultData(
            action = "open_browser",
            target = url,
            resultSummary = "New browser tab opened for URL: ${url ?: "default browser page"}."
        )
    }

    private suspend fun getPageInfo(): ComputerDesktopActionResultData {
        val pageInfo = desktopManager.getPageInfo()
        val tabs = desktopManager.getTabs()
        val currentTabIndex = desktopManager.currentTabIndex.value

        val tabInfos = tabs.mapIndexed { index, tab ->
            ComputerDesktopActionResultData.ComputerTabInfo(
                id = tab.id,
                title = tab.title,
                url = tab.url,
                isActive = index == currentTabIndex
            )
        }

        return if (pageInfo != null) {
            ComputerDesktopActionResultData(
                action = "get_page_info",
                resultSummary = "Successfully retrieved page information and tab context.",
                tabs = tabInfos,
                pageContent = pageInfo
            )
        } else {
            ComputerDesktopActionResultData(
                action = "get_page_info",
                resultSummary = "Failed to retrieve page content, but got tab context.",
                tabs = tabInfos
            )
        }
    }

    private suspend fun clickElement(tool: AITool): ComputerDesktopActionResultData {
        val interaction_id = tool.parameters.find { it.name == "interaction_id" }?.value?.toIntOrNull()
        if (interaction_id == null) {
            return ComputerDesktopActionResultData(action = "click_element", resultSummary = "Failed: Missing interaction_id.")
        }
        val (success, message) = desktopManager.clickElement(interaction_id)
        return ComputerDesktopActionResultData(
            action = "click_element",
            target = "ID: $interaction_id",
            resultSummary = message
        )
    }

    private fun scrollBy(tool: AITool): ComputerDesktopActionResultData {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull() ?: 0
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull() ?: 0
        desktopManager.scrollBy(x, y)
        return ComputerDesktopActionResultData(
            action = "scroll_by",
            target = "x=$x, y=$y",
            resultSummary = "Scrolled page by ($x, $y)."
        )
    }

    private suspend fun inputText(tool: AITool): ComputerDesktopActionResultData {
        val interaction_id = tool.parameters.find { it.name == "interaction_id" }?.value?.toIntOrNull()
        val text = tool.parameters.find { it.name == "text" }?.value
        if (interaction_id == null || text == null) {
            return ComputerDesktopActionResultData(action = "input_text", resultSummary = "Failed: Missing interaction_id or text.")
        }
        val (success, message) = desktopManager.inputText(interaction_id, text)
        return ComputerDesktopActionResultData(
            action = "input_text",
            target = "ID: $interaction_id",
            resultSummary = message
        )
    }

    private fun closeTab(tool: AITool): ComputerDesktopActionResultData {
        val tabId = tool.parameters.find { it.name == "tab_id" }?.value
        val tabIndex = tool.parameters.find { it.name == "tab_index" }?.value?.toIntOrNull()

        when {
            tabId != null -> desktopManager.closeTab(tabId)
            tabIndex != null -> desktopManager.closeTab(tabIndex)
            else -> return ComputerDesktopActionResultData(
                action = "close_tab",
                resultSummary = "Failed: Missing tab_id or tab_index parameter."
            )
        }
        return ComputerDesktopActionResultData(
            action = "close_tab",
            target = tabId ?: tabIndex.toString(),
            resultSummary = "Tab closed successfully." 
        )
    }

    private fun goBack(): ComputerDesktopActionResultData {
        desktopManager.goBack()
        return ComputerDesktopActionResultData(
            action = "go_back",
            resultSummary = "Navigated back to the previous page."
        )
    }
    
    fun getCategory(): ToolCategory {
        return ToolCategory.UI_AUTOMATION
    }
} 
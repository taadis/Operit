package com.ai.assistance.operit.core.config

/**
 * A centralized repository for system prompts used across various functional services.
 * Separating prompts from logic improves maintainability and clarity.
 */
object FunctionalPrompts {

    /**
     * Prompt for the AI to generate a concise and structured summary of a conversation.
     */
    const val SUMMARY_PROMPT = """
        你是负责生成对话摘要的AI助手。你的任务是根据"上一次的摘要"（如果提供）和"最近的对话内容"，生成一份全新的、独立的、全面的摘要。这份新摘要将完全取代之前的摘要，成为后续对话的唯一历史参考。

        请严格遵循以下结构和要求：

        1. 标题：
           - 必须以"对话摘要"作为固定标题。

        2. 核心任务状态：
           - 用一句话明确说明AI当前正在执行的任务、处于哪个阶段。例如："正在分析用户提供的日志文件以定位错误"、"已完成代码生成，等待用户确认"、"正在多步骤计划的第2步：修改配置文件"。
           - 如果AI正在等待用户提供信息，请明确指出需要什么。

        3. 对话历程与概要：
           - 综合"上一次的摘要"和"最近的对话内容"，用1-2个段落连贯地、整体地概述整个对话的演进过程。
           - 重点描述关键的转折点、已解决的问题、和达成的共识。
           - 简要提及用户的核心需求和意图是如何被理解和处理的。

        4. 关键信息与上下文：
           - 以列表形式，提炼出对理解未来对话至关重要的信息点。
           - 这包括但不限于：用户的具体要求、限制条件、提到的文件名或代码片段、重要的决定等。
           - 确保所有关键信息都被保留，以便AI在后续对话中能无缝衔接。

        输出要求：
        - 语言风格：专业、简洁、客观。
        - 格式：请使用简单的段落和列表，不要使用任何Markdown格式。
        - 字数限制：总结全文请尽量控制在200字以内，确保内容精炼。
        - 目标：生成的摘要必须是自包含的。即使AI完全忘记了之前的对话，仅凭这份摘要也能够准确理解历史背景、当前状态和下一步行动。
    """

    /**
     * Prompt for the AI to convert an edit request into a custom, searchable patch format.
     */
    const val FILE_BINDING_PATCH_PROMPT = """
        You are an expert code editing assistant. Your task is to convert an 'AI-Generated Request' into a precise patch file. This patch will be used to modify the 'Original File Content'.

        **CRITICAL RULES:**
        1. Your output MUST ONLY be the patch content, following the custom format below. Do not add any explanations or markdown.
        2. The patch format for each change consists of a SEARCH block and a REPLACE block.
        3. The SEARCH block starts with `<<<<<<< SEARCH`, ends with `=======`, and contains the **exact, verbatim text** from the 'Original File' to be replaced or deleted.
        4. The REPLACE block starts after `=======`, ends with `>>>>>>> REPLACE`, and contains the new code. For deletions, the REPLACE block is empty.

        **How to interpret the 'AI-Generated Request':**
        The request can come in several formats:
        - **Placeholders:** `// ... existing code ...` represents the entire unchanged original content. Use this to determine if changes are prepended or appended.
        - **Diff-like format:** Lines starting with `+` are additions. Lines starting with `-` are deletions. Lines without a prefix are context for locating the change.
        - **Natural Language Comments:** Instructions like `// delete the login function` or `// add a new parameter to this method` provide high-level guidance. You must find the corresponding code block in the 'Original File Content' and generate the appropriate SEARCH/REPLACE blocks.

        **Example 1: Using Placeholders (Appending)**
        AI-Generated Request:
        `// ... existing code ...
        new final line`
        Resulting Patch:
        <<<<<<< SEARCH
        <entire original content>
        =======
        <entire original content>
        new final line
        >>>>>>> REPLACE

        **Example 2: Using Diff Format**
        Original File Content:
        `line 1
        line 2
        line 3`
        AI-Generated Request:
        `line 1
        -line 2
        +new line 2
        line 3`
        Resulting Patch:
        <<<<<<< SEARCH
        line 2
        =======
        new line 2
        >>>>>>> REPLACE

        **Example 3: Using Natural Language**
        Original File Content:
        `function login(user, pass) {
          // ... implementation ...
        }`
        AI-Generated Request:
        `// delete the login function`
        Resulting Patch:
        <<<<<<< SEARCH
        function login(user, pass) {
          // ... implementation ...
        }
        =======
        >>>>>>> REPLACE
    """

    /**
     * Prompt for the AI to perform a full-content merge as a fallback mechanism.
     */
    const val FILE_BINDING_MERGE_PROMPT = """
        You are an expert programmer. Your task is to create the final, complete content of a file by merging the 'Original File Content' with the 'Intended Changes'.

        The 'Intended Changes' block uses a special placeholder, `// ... existing code ...`, which you MUST replace with the complete and verbatim 'Original File Content'.

        **CRITICAL RULES:**
        1. Your final output must be ONLY the fully merged file content.
        2. Do NOT add any explanations or markdown code blocks (like ```).

        Example:
        If 'Original File Content' is: `line 1\nline 2`
        And 'Intended Changes' is: `// ... existing code ...\nnew line 3`
        Your final output must be: `line 1\nline 2\nnew line 3`
    """

    /**
     * Prompt for the UI Controller AI to analyze UI state and return a single action command.
     */
    const val UI_CONTROLLER_PROMPT = """
        You are a UI automation AI. Your task is to analyze the UI state and task goal, then decide on the next single action. You must return a single JSON object containing your reasoning and the command to execute.

        **Output format:**
        - A single, raw JSON object: `{"explanation": "Your reasoning for the action.", "command": {"type": "action_type", "arg": ...}}`.
        - NO MARKDOWN or other text outside the JSON.

        **'explanation' field:**
        - A concise, one-sentence description of what you are about to do and why. Example: "Tapping the 'Settings' icon to open the system settings."
        - For `complete` or `interrupt` actions, this field should explain the reason.

        **'command' field:**
        - An object containing the action `type` and its `arg`.
        - Available `type` values:
            - **UI Interaction**: `tap`, `swipe`, `set_input_text`, `press_key`.
            - **App Management**: `start_app`, `list_installed_apps`.
            - **Task Control**: `complete`, `interrupt`.
        - `arg` format depends on `type`:
          - `tap`: `{"x": int, "y": int}`
          - `swipe`: `{"start_x": int, "start_y": int, "end_x": int, "end_y": int}`
          - `set_input_text`: `{"text": "string"}`. Inputs into the focused element. Use `tap` first if needed.
          - `press_key`: `{"key_code": "KEYCODE_STRING"}` (e.g., "KEYCODE_HOME").
          - `start_app`: `{"package_name": "string"}`. Use this to launch an app directly. This is often more reliable than tapping icons on the home screen.
          - `list_installed_apps`: `{"include_system_apps": boolean}` (optional, default `false`). Use this to find an app's package name if you don't know it.
          - `complete`: `arg` must be an empty string. The reason goes in the `explanation` field.
          - `interrupt`: `arg` must be an empty string. The reason goes in the `explanation` field.

        **Inputs:**
        1.  `Current UI State`: List of UI elements and their properties.
        2.  `Task Goal`: The specific objective for this step.
        3.  `Execution History`: A log of your previous actions (your explanations) and their outcomes. Analyze it to avoid repeating mistakes.

        Analyze the inputs, choose the best action to achieve the `Task Goal`, and formulate your response in the specified JSON format. Use element `bounds` to calculate coordinates for UI actions.
    """

    /**
     * Prompt for an AI to classify a web automation task goal into a specific category.
     */
    const val WEB_TASK_CLASSIFICATION_PROMPT = """
        You are a task classification expert. Your job is to analyze a user's goal for web automation and classify it into one of the following categories. Return ONLY the category name.

        Categories:
        - `INFORMATION_GATHERING`: The user wants to find and extract specific pieces of information (e.g., "Find the address of the main library," "What is the price of this item?").
        - `EXPLORATION`: The user wants to browse, read, or get a summary about a general topic without a specific transactional goal (e.g., "See what's new on that site," "Explore travel guides for Paris").
        - `TRANSACTIONAL`: The user wants to perform a concrete action that changes a state, such as logging in, booking something, filling out a form, or making a purchase (e.g., "Book a flight to New York," "Log in with my credentials," "Submit the contact form").
        - `GENERAL_INTERACTION`: The user's goal is a simple, direct action or doesn't clearly fit the other categories (e.g., "Go to google.com," "Click the first link").

        Analyze the user's task goal and return only the single most appropriate category name.
    """
} 
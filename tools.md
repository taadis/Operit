# OperIT AI 工具文档

本文档提供了 OperIT 系统中所有可用 AI 工具的详细说明，包括工具的功能、参数和返回结果类型。

## 目录

- [基础工具](#基础工具)
- [文件系统工具](#文件系统工具)
- [网络工具](#网络工具)
- [系统操作工具](#系统操作工具)
- [UI 自动化工具](#ui-自动化工具)
- [数据结构参考](#数据结构参考)

## 基础工具

### 查询问题库 (`query_problem_library`)

查询内部问题知识库获取信息。

**参数:**
- `query`: 查询字符串

**返回:** `string` - 查询结果

---

### 使用工具包 (`use_package`)

加载并使用特定的工具包。

**参数:**
- `package_name`: 工具包名称

**返回:** `string` - 工具包加载结果

---

### 计算器 (`calculate`)

执行数学计算。

**参数:**
- `expression`: 数学表达式

**返回:** `CalculationResultData` - 计算结果数据

---

### 休眠 (`sleep`)

暂停执行指定的毫秒数。

**参数:**
- `duration_ms`: 暂停时间（毫秒），最大 10000

**返回:** 包含实际休眠时间的对象

## 文件系统工具

### 列出文件 (`list_files`)

列出指定目录中的文件和子目录。

**参数:**
- `path`: 要列出内容的目录路径

**返回:** `DirectoryListingData` - 包含目录和文件列表

---

### 读取文件 (`read_file`)

读取文件内容。

**参数:**
- `path`: 文件路径

**返回:** `FileContentData` - 包含文件内容和元数据

---

### 写入文件 (`write_file`)

写入或追加内容到文件。

**参数:**
- `path`: 文件路径
- `content`: 要写入的内容
- `append`: (可选) 是否追加到文件末尾 ("true"/"false")

**返回:** `FileOperationData` - 包含操作结果信息

**注意:** 此操作被标记为危险操作，可能需要用户确认。

---

### 删除文件 (`delete_file`)

删除文件或目录。

**参数:**
- `path`: 要删除的文件或目录路径
- `recursive`: (可选) 是否递归删除目录内容 ("true"/"false")

**返回:** `FileOperationData` - 包含操作结果信息

**注意:** 此操作被标记为危险操作，可能需要用户确认。

---

### 检查文件是否存在 (`file_exists`)

检查指定路径的文件或目录是否存在。

**参数:**
- `path`: 要检查的路径

**返回:** `FileExistsData` - 包含存在状态和文件信息

---

### 移动/重命名文件 (`move_file`)

移动或重命名文件/目录。

**参数:**
- `source`: 源文件/目录路径
- `destination`: 目标路径

**返回:** `FileOperationData` - 包含操作结果信息

**注意:** 此操作被标记为危险操作，可能需要用户确认。

---

### 复制文件 (`copy_file`)

复制文件或目录。

**参数:**
- `source`: 源文件/目录路径
- `destination`: 目标路径
- `recursive`: (可选) 是否递归复制目录 ("true"/"false"，默认 false)

**返回:** `FileOperationData` - 包含操作结果信息

---

### 创建目录 (`make_directory`)

创建新目录。

**参数:**
- `path`: 目录路径
- `create_parents`: (可选) 是否创建父目录 ("true"/"false"，默认 false)

**返回:** `FileOperationData` - 包含操作结果信息

---

### 搜索文件 (`find_files`)

在指定目录中搜索匹配特定模式的文件。

**参数:**
- `path`: 搜索的起始目录（**必须**以 /sdcard/ 开头以避免系统问题）
- `pattern`: 文件名匹配模式（例如："*.jpg"）
- `max_depth`: (可选) 控制子目录搜索深度，-1 表示无限制
- `use_path_pattern`: (可选) 是否在路径中使用模式匹配 ("true"/"false"，默认 false)
- `case_insensitive`: (可选) 是否忽略大小写 ("true"/"false"，默认 false)

**返回:** `FindFilesResultData` - 包含匹配的文件列表

---

### 获取文件信息 (`file_info`)

获取文件或目录的详细信息。

**参数:**
- `path`: 文件或目录路径

**返回:** `FileInfoData` - 包含文件的详细信息，如类型、大小、权限等

---

### 压缩文件 (`zip_files`)

压缩文件或目录。

**参数:**
- `source`: 要压缩的文件/目录路径
- `destination`: 目标压缩文件路径

**返回:** `FileOperationData` - 包含操作结果信息

---

### 解压缩文件 (`unzip_files`)

解压缩文件。

**参数:**
- `source`: 压缩文件路径
- `destination`: 解压目标目录

**返回:** `FileOperationData` - 包含操作结果信息

---

### 打开文件 (`open_file`)

使用系统默认应用打开文件。

**参数:**
- `path`: 文件路径

**返回:** `FileOperationData` - 包含操作结果信息

---

### 分享文件 (`share_file`)

使用系统分享功能分享文件。

**参数:**
- `path`: 要分享的文件路径
- `title`: (可选) 分享标题（默认为 "Share File"）

**返回:** `FileOperationData` - 包含操作结果信息

## 网络工具

### 网络搜索 (`web_search`)

在网络上搜索信息。

**参数:**
- `query`: 搜索查询

**返回:** `WebSearchResultData` - 包含搜索结果列表

---

### HTTP 请求 (`http_request`)

发送 HTTP 请求。

**参数:**
- `url`: 请求 URL
- `method`: (可选) HTTP 方法 (GET, POST, PUT, DELETE 等，默认 GET)
- `headers`: (可选) HTTP 头信息（JSON 格式）
- `body`: (可选) 请求体内容
- `body_type`: (可选) 请求体类型 ("json"/"form"/"text"，默认 "json")

**返回:** `HttpResponseData` - 包含响应状态和内容

---

### 下载文件 (`download_file`)

从 URL 下载文件。

**参数:**
- `url`: 文件 URL
- `destination`: 保存文件的本地路径

**返回:** `FileOperationData` - 包含下载结果信息

---

### 获取网页内容 (`fetch_web_page`)

获取并解析网页内容。

**参数:**
- `url`: 网页 URL
- `format`: (可选) 返回格式 ("text" 或 "html"，默认 "text")

**返回:** `WebPageData` - 包含网页标题、文本内容和链接

## 系统操作工具

### 获取设备信息 (`device_info`)

获取当前设备的详细信息。

**参数:** 无

**返回:** `DeviceInfoResultData` - 包含设备详细信息

---

### 修改系统设置 (`modify_system_setting`)

修改系统设置值。

**参数:**
- `setting`: 设置名称
- `value`: 设置值
- `namespace`: (可选) 命名空间: system/secure/global（默认 system）

**返回:** `SystemSettingData` - 包含操作结果

**注意:** 此操作被标记为危险操作，需要用户授权。

---

### 获取系统设置 (`get_system_setting`)

获取系统设置的当前值。

**参数:**
- `setting`: 设置名称
- `namespace`: (可选) 命名空间: system/secure/global（默认 system）

**返回:** `SystemSettingData` - 包含设置值

---

### 安装应用 (`install_app`)

安装 APK 文件。

**参数:**
- `apk_path`: APK 文件路径

**返回:** `AppOperationData` - 包含安装结果

**注意:** 此操作被标记为危险操作，需要用户授权。

---

### 卸载应用 (`uninstall_app`)

卸载已安装的应用程序。

**参数:**
- `package_name`: 应用包名
- `keep_data`: (可选) 是否保留数据（默认 false）

**返回:** `AppOperationData` - 包含卸载结果

**注意:** 此操作被标记为危险操作，需要用户授权。

---

### 列出已安装应用 (`list_installed_apps`)

获取设备上已安装的应用列表。

**参数:**
- `include_system_apps`: (可选) 是否包含系统应用（默认 false）

**返回:** `AppListData` - 包含应用列表

---

### 启动应用 (`start_app`)

启动已安装的应用程序。

**参数:**
- `package_name`: 应用包名
- `activity`: (可选) 活动名称

**返回:** `AppOperationData` - 包含启动结果

---

### 停止应用 (`stop_app`)

停止运行中的应用程序。

**参数:**
- `package_name`: 应用包名

**返回:** `AppOperationData` - 包含停止结果

**注意:** 此操作被标记为危险操作，需要用户授权。

## UI 自动化工具

### 获取页面信息 (`get_page_info`)

获取当前页面的 UI 元素结构。

**参数:**
- `format`: (可选) 格式 ("xml" 或 "json"，默认 "xml")
- `detail`: (可选) 详细级别 ("minimal", "summary", 或 "full"，默认 "summary")

**返回:** `UIPageResultData` - 包含 UI 层次结构信息

---

### 点击元素 (`click_element`)

点击屏幕上的特定 UI 元素。

**参数:**
- `resourceId`: (可选) 元素的资源 ID
- `className`: (可选) 元素的类名
- `text`: (可选) 元素的文本内容
- `contentDesc`: (可选) 元素的内容描述
- `index`: (可选) 要点击的第几个匹配元素（从 0 开始计数，默认 0）
- `partialMatch`: (可选) 是否启用部分匹配（默认 false）

**注意:** 至少需要提供一个识别参数

**返回:** `UIActionResultData` - 包含点击结果

**注意:** 此操作可能被标记为危险操作，取决于目标元素的特性。

---

### 点击坐标 (`tap`)

点击屏幕上的指定坐标。

**参数:**
- `x`: X 坐标
- `y`: Y 坐标

**返回:** `UIActionResultData` - 包含点击结果

---

### 设置输入文本 (`set_input_text`)

在输入框中设置文本。

**参数:**
- `text`: 要输入的文本
- `resourceId`: (可选) 输入框的资源 ID
- `index`: (可选) 第几个匹配的输入框

**返回:** `UIActionResultData` - 包含操作结果

---

### 按键操作 (`press_key`)

模拟按下特定按键。

**参数:**
- `key_code`: 按键代码（例如："KEYCODE_BACK", "KEYCODE_HOME" 等）

**返回:** `UIActionResultData` - 包含操作结果

---

### 滑动操作 (`swipe`)

执行从一个坐标到另一个坐标的滑动操作。

**参数:**
- `start_x`: 起始 X 坐标
- `start_y`: 起始 Y 坐标
- `end_x`: 结束 X 坐标
- `end_y`: 结束 Y 坐标
- `duration`: (可选) 滑动持续时间(毫秒)（默认 300）

**返回:** `UIActionResultData` - 包含操作结果

---

### 启动应用 (`launch_app`)

通过包名启动应用。

**参数:**
- `package_name`: 应用包名

**返回:** `UIActionResultData` - 包含启动结果

---

### 组合操作 (`combined_operation`)

执行一系列 UI 操作并返回操作后的 UI 状态。

**参数:**
- `operation`: 要执行的操作（例如："tap 500 800", "click_element resourceId buttonID [index] [partialMatch]", "swipe 500 1000 500 200"）
- `delay_ms`: (可选) 等待时间(毫秒)（默认 1000）

**返回:** `CombinedOperationResultData` - 包含操作结果和更新后的 UI 状态

**使用建议:**
- 处理 UI 交互问题时，优先使用 combined_operation 工具而非单独的操作工具
- combined_operation 自动等待 UI 更新并返回新状态，解决了需要手动延迟和操作后获取界面的问题
- 对于"点击后会发生什么"或"文本输入后界面如何变化"等场景，combined_operation 是最佳选择
- 例如：使用 "combined_operation" 搭配 "operation=tap 500 800" 代替单独的 "tap" 命令加延迟
- 或者使用 "combined_operation" 搭配 "operation=click_element resourceId buttonID" 代替单独的 "click_element" 命令
- 需要点击列表中特定项目时，使用 "click_element" 的索引参数，例如 "click_element resourceId com.example.app:id/list_item 2" 点击第3个项目
- 当多个元素共享同一标识符（如列表项）时，可以使用 "index" 参数指定要点击的特定元素
- 当无法通过 ID 精确定位元素时，可以先使用 "tap" 工具直接使用坐标点击
- 启动应用时，优先使用 "combined_operation"，这样可以立即获取界面信息

## 数据结构参考

本节列出了工具返回的数据结构定义。

### `StringResultData`

最基本的字符串结果类型。

```kotlin
data class StringResultData(val value: String)
```

### `BooleanResultData`

布尔值结果类型。

```kotlin
data class BooleanResultData(val value: Boolean)
```

### `IntResultData`

整数结果类型。

```kotlin
data class IntResultData(val value: Int)
```

### `CalculationResultData`

计算结果类型，包含表达式、结果和变量值。

```kotlin
data class CalculationResultData(
    val expression: String,
    val result: Double,
    val formattedResult: String,
    val variables: Map<String, Double> = emptyMap()
)
```

### `DirectoryListingData`

目录列表结果，包含文件和子目录信息。

```kotlin
data class DirectoryListingData(
    val path: String,
    val entries: List<FileEntry>
)

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val lastModified: String
)
```

### `FileContentData`

文件内容结果。

```kotlin
data class FileContentData(
    val path: String,
    val content: String,
    val size: Long
)
```

### `FileExistsData`

文件存在检查结果。

```kotlin
data class FileExistsData(
    val path: String,
    val exists: Boolean,
    val isDirectory: Boolean = false,
    val size: Long = 0
)
```

### `FileOperationData`

文件操作结果。

```kotlin
data class FileOperationData(
    val operation: String,
    val path: String,
    val successful: Boolean,
    val details: String
)
```

### `FindFilesResultData`

文件搜索结果。

```kotlin
data class FindFilesResultData(
    val path: String,
    val pattern: String,
    val files: List<String>
)
```

### `HttpResponseData`

HTTP 请求响应结果。

```kotlin
data class HttpResponseData(
    val url: String,
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val contentType: String,
    val content: String,
    val contentSummary: String,
    val size: Int
)
```

### `WebPageData`

网页内容结果。

```kotlin
data class WebPageData(
    val url: String,
    val title: String,
    val contentType: String,
    val content: String,
    val textContent: String,
    val size: Int,
    val links: List<Link>
)

data class Link(
    val text: String,
    val url: String
)
```

### `WebSearchResultData`

网络搜索结果。

```kotlin
data class WebSearchResultData(
    val query: String,
    val results: List<SearchResult>
)

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)
```

### `SystemSettingData`

系统设置结果。

```kotlin
data class SystemSettingData(
    val namespace: String,
    val setting: String,
    val value: String
)
```

### `AppOperationData`

应用操作结果。

```kotlin
data class AppOperationData(
    val operationType: String,
    val packageName: String,
    val success: Boolean,
    val details: String = ""
)
```

### `AppListData`

应用列表结果。

```kotlin
data class AppListData(
    val includesSystemApps: Boolean,
    val packages: List<String>
)
```

### `UIPageResultData`

UI 页面分析结果。

```kotlin
data class UIPageResultData(
    val packageName: String,
    val activityName: String,
    val uiElements: SimplifiedUINode
)
```

### `UIActionResultData`

UI 操作结果。

```kotlin
data class UIActionResultData(
    val actionType: String,
    val actionDescription: String,
    val coordinates: Pair<Int, Int>? = null,
    val elementId: String? = null
)
```

### `CombinedOperationResultData`

组合操作结果。

```kotlin
data class CombinedOperationResultData(
    val operationSummary: String,
    val waitTime: Int,
    val pageInfo: UIPageResultData
)
```

### `DeviceInfoResultData`

设备信息结果。

```kotlin
data class DeviceInfoResultData(
    val deviceId: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val screenResolution: String,
    val screenDensity: Float,
    val totalMemory: String,
    val availableMemory: String,
    val totalStorage: String,
    val availableStorage: String,
    val batteryLevel: Int,
    val batteryCharging: Boolean,
    val cpuInfo: String,
    val networkType: String,
    val additionalInfo: Map<String, String> = emptyMap()
)
```

### `FileInfoData`

文件信息结果。

```kotlin
data class FileInfoData(
    val path: String,
    val exists: Boolean,
    val fileType: String,  // "file", "directory", or "other"
    val size: Long,
    val permissions: String,
    val owner: String,
    val group: String,
    val lastModified: String,
    val rawStatOutput: String
)
```

## 系统架构与文件关系

本节说明系统中各个文件的关系，以及修改工具或数据结构时需要遵循的步骤。

### 关键文件之间的关系

系统中存在以下几个关键文件，它们相互依赖并需要保持同步：

1. **`ToolResultDataClasses.kt`**
   - 包含所有工具返回的数据类定义
   - 是核心数据结构的源头，其他文件都依赖于此处的定义

2. **`FileSystemTools.kt` 等实现文件**
   - 包含工具的具体实现
   - 使用 `ToolResultDataClasses.kt` 中定义的数据类作为返回值

3. **`index.d.ts`**
   - TypeScript 类型定义文件
   - 需要与 Kotlin 数据类定义保持一致
   - 包含 `ToolResultMap` 接口，定义了工具名到返回类型的映射

4. **`operit-tester.ts`**
   - 测试工具的实现
   - 依赖 TypeScript 类型定义
   - 执行工具调用并处理返回的数据结构

5. **`tools.md`**
   - 工具文档
   - 需要准确反映工具的参数和返回类型

6. **`SystemPromptConfig.kt`**
   - 包含系统提示词模板
   - 工具描述需要与实际实现保持一致

### 修改指南

当需要修改工具或数据结构时，请按照以下步骤操作：

#### 1. 修改数据类定义

首先修改 `ToolResultDataClasses.kt` 中的数据类定义。特别注意：

- 避免使用可能导致序列化问题的字段名（如 `type`、`class` 等）
- 确保字段名称有意义并遵循命名约定（如 `fileType` 而非 `type`）
- 添加适当的注释说明字段用途

```kotlin
@Serializable
data class FileInfoData(
    val path: String,
    val exists: Boolean,
    val fileType: String,  // 使用 fileType 而非 type
    // ...其他字段
) : ToolResultData() {
    // 实现 toString() 方法
}
```

#### 2. 更新工具实现

修改 `FileSystemTools.kt` 等实现文件中的代码，以使用新的数据结构。

#### 3. 更新 TypeScript 定义

确保在 `index.d.ts` 中更新对应的接口定义：

```typescript
interface FileInfoData {
    path: string;
    exists: boolean;
    fileType: string;  // 与 Kotlin 字段名保持一致
    // ...其他字段
}

interface ToolResultMap {
    // 确保工具返回类型映射正确
    'file_info': FileInfoData;
    // ...其他工具
}
```

#### 4. 更新测试代码

修改 `operit-tester.ts` 中的测试代码，以适应新的数据结构：

```typescript
const fileInfo = fileInfoResult as FileInfoData;
console.log(`Type: ${fileInfo.fileType}`);  // 使用正确的字段名
```

#### 5. 更新文档

更新 `tools.md` 中的工具文档和数据结构定义：

```markdown
### 获取文件信息 (`file_info`)
**返回:** `FileInfoData` - 包含文件的详细信息...

### `FileInfoData`
```kotlin
data class FileInfoData(
    // 更新后的字段定义
)
```
```

#### 6. 更新系统提示词

如果必要，更新 `SystemPromptConfig.kt` 中的工具描述，确保与实际行为一致。

### 避免常见问题

1. **字段命名冲突**: 避免使用 `type`、`class`、`object` 等可能导致 JSON 序列化问题的名称
2. **类型不一致**: 确保 Kotlin 与 TypeScript 中的类型定义保持一致（如 `Long` 对应 `number`）
3. **文档滞后**: 修改代码后及时更新文档
4. **不完整更新**: 修改数据结构后确保所有相关文件都已更新

遵循以上指南可以确保系统各组件之间保持一致性，减少由于数据结构修改导致的问题。
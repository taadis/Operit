# VSCode中运行JavaScript到Android设备的使用说明

本项目配置了通过VSCode直接运行JavaScript文件到Android设备的功能。这让您可以快速开发和测试JavaScript脚本，无需手动执行命令行工具。

## 准备工作

1. 确保安装了VSCode
2. 确保Android设备已连接并已启用USB调试
3. 确保ADB工具可用（已添加到系统路径）
4. 确保Android设备上安装了兼容的应用程序

## 使用方法

### 方法一：通过调试菜单（推荐）

1. 在VSCode中打开要运行的JavaScript文件
2. 按下`F5`键或点击调试菜单中的"开始调试"
3. 在弹出的输入框中输入以下信息：
   - 要执行的JavaScript函数名（如：`showMessage`）
   - 函数参数，使用JSON格式（如：`{"name": "张三", "message": "你好"}`）
4. 执行结果将在终端窗口显示

### 方法二：通过任务菜单

1. 在VSCode中打开要运行的JavaScript文件
2. 按下`Ctrl+Shift+B`（Windows/Linux）或`Cmd+Shift+B`（macOS）
3. 在弹出的输入框中输入函数名和参数，同上

## 示例

项目中包含了示例脚本 `examples/demo_script.js`，其中定义了以下函数：

### `main(params)`

默认函数，简单返回提供的参数。

示例参数：`{"message": "测试"}`

### `showMessage(params)`

显示消息和当前时间。

示例参数：`{"name": "张三", "message": "欢迎使用"}`

### `calculate(params)`

执行简单的计算操作。

示例参数：`{"a": 10, "b": 5, "operation": "add"}`

可用的操作类型：
- `add`：加法
- `subtract`：减法
- `multiply`：乘法
- `divide`：除法

## 注意事项

1. 确保JavaScript函数中使用`complete()`函数返回结果，这是必须的
2. 参数必须是有效的JSON格式
3. 脚本执行可能需要一些时间，请耐心等待结果
4. 如果遇到问题，请检查VSCode的终端输出和Android设备的日志

## 高级用法

如果需要更复杂的执行选项，可以直接在命令行使用以下命令：

Windows:
```
.\tools\execute_js.bat <JS文件路径> <函数名> <JSON参数>
```

Linux/macOS:
```
./tools/execute_js.sh <JS文件路径> <函数名> <JSON参数>
```

例如：
```
.\tools\execute_js.bat examples\demo_script.js calculate '{"a":5,"b":3,"operation":"multiply"}'
``` 
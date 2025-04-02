# Termux 集成功能

本项目通过 Shizuku 和特殊的权限配置实现了与 Termux 的深度集成，允许应用执行 Termux 命令并自动授权。

## 功能特性

1. **自动检测 Termux 安装状态**：应用可以检测设备上是否已安装 Termux，并提供内置安装包或商店链接。

2. **自动授权 Termux**：利用 Shizuku 提供的 ADB 功能，应用可以自动配置 Termux 允许外部应用调用，无需手动修改设置文件。

3. **命令执行支持**：提供两种方式执行 Termux 命令：
   - 后台执行：无需用户交互，直接在 Termux 环境中执行命令
   - 交互式执行：打开 Termux 应用并预填充命令

4. **丰富的示例命令**：内置常用的 Termux 命令示例，方便用户学习和使用。

## 技术实现

### 1. 权限和查询配置

应用的 `AndroidManifest.xml` 包含以下关键配置：

```xml
<queries>
    <package android:name="com.termux" />
    <package android:name="com.termux.api" />
    <intent>
        <action android:name="com.termux.RUN_COMMAND" />
    </intent>
</queries>
```

这些配置允许应用查询 Termux 安装状态并与之通信。

### 2. 自动授权实现

`TermuxAuthorizer` 类通过 Shizuku 执行以下操作：

1. 创建 Termux 配置目录：`/data/data/com.termux/files/home/.termux`
2. 写入配置：`allow-external-apps=true`
3. 调整文件权限确保 Termux 可以读取配置
4. 重启 Termux 服务以应用更改

### 3. 命令执行

`TermuxCommandExecutor` 类提供两种执行命令的方式：

1. **通过 Intent**：
   ```kotlin
   val executeIntent = Intent("com.termux.RUN_COMMAND")
       .setComponent(ComponentName("com.termux", "com.termux.app.RunCommandService"))
       .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
       .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
   ```

2. **通过 Shizuku ADB**：
   ```kotlin
   val termuxCommand = "am startservice --user 0 -n com.termux/com.termux.app.TermuxService -a com.termux.RUN_COMMAND --es com.termux.RUN_COMMAND_PATH /data/data/com.termux/files/usr/bin/bash --esa com.termux.RUN_COMMAND_ARGUMENTS \"-c,$command\""
   val result = AdbCommandExecutor.executeAdbCommand(termuxCommand)
   ```

3. **直接打开 Termux**：
   ```kotlin
   val intent = Intent(Intent.ACTION_VIEW, Uri.parse("termux:command/$command"))
   ```

## 使用方法

### 1. 安装和授权 Termux

1. 在应用中长按 "Termux终端" 状态项
2. 如果 Termux 未安装，点击 "安装内置版本" 或 "从应用商店安装"
3. 安装完成后，点击 "自动授权 Termux" 按钮

### 2. 执行命令

1. 在 Termux 命令执行器中输入命令，或从示例中选择
2. 选择执行方式：
   - "后台执行"：静默在 Termux 环境中运行命令
   - "打开 Termux 执行"：启动 Termux 应用并执行命令

## 注意事项

1. 自动授权功能需要 Shizuku 权限，请确保 Shizuku 服务已正确运行且已授权。

2. 某些系统可能会阻止 Termux 后台执行，建议将应用添加到电池优化排除列表。

3. 使用 "后台执行" 时，如果需要查看命令输出，可以将输出重定向到文件，例如：
   ```
   echo "Hello World" > /sdcard/termux_output.txt
   ```

4. 如果遇到授权问题，可以尝试重启 Termux 或重新执行授权操作。 
/* METADATA
{
  name: code_runner
  description: 提供多语言代码执行能力，支持JavaScript、Python、Ruby、Go和Rust脚本的运行。可直接执行代码字符串或运行外部文件，适用于快速测试、自动化脚本和教学演示。
  
  // Multiple tools in this package
  tools: [
    {
      name: run_javascript_es5
      description: 运行自定义 JavaScript 脚本
      // This tool takes parameters
      parameters: [
        {
          name: script
          description: 要执行的 JavaScript 脚本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_javascript_file
      description: 运行 JavaScript 文件
      parameters: [
        {
          name: file_path
          description: JavaScript 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_python
      description: 运行自定义 Python 脚本
      parameters: [
        {
          name: script
          description: 要执行的 Python 脚本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_python_file
      description: 运行 Python 文件
      parameters: [
        {
          name: file_path
          description: Python 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_ruby
      description: 运行自定义 Ruby 脚本
      parameters: [
        {
          name: script
          description: 要执行的 Ruby 脚本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_ruby_file
      description: 运行 Ruby 文件
      parameters: [
        {
          name: file_path
          description: Ruby 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_go
      description: 运行自定义 Go 代码
      parameters: [
        {
          name: script
          description: 要执行的 Go 代码内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_go_file
      description: 运行 Go 文件
      parameters: [
        {
          name: file_path
          description: Go 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_rust
      description: 运行自定义 Rust 代码
      parameters: [
        {
          name: script
          description: 要执行的 Rust 代码内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_rust_file
      description: 运行 Rust 文件
      parameters: [
        {
          name: file_path
          description: Rust 文件路径
          type: string
          required: true
        }
      ]
    }
  ]
  
  // Tool category
  category: SYSTEM_OPERATION
}
*/
const codeRunner = (function () {
    /**
     * 测试代码执行器的功能
     * Tests all code runner functionality without any parameters
     */
    async function main() {
        const results = {
            javascript: await testJavaScript(),
            python: await testPython(),
            ruby: await testRuby(),
            go: await testGo(),
            rust: await testRust()
        };
        // Format results for display
        let summary = "代码执行器功能测试结果：\n";
        for (const [lang, result] of Object.entries(results)) {
            summary += `${lang}: ${result.success ? '✅ 成功' : '❌ 失败'} - ${result.message}\n`;
        }
        return summary;
    }
    // 测试JavaScript执行功能
    async function testJavaScript() {
        try {
            // 测试简单的JS代码
            const script = "const testVar = 42; return 'JavaScript运行正常，测试值: ' + testVar;";
            const result = await run_javascript_es5({ script });
            return { success: true, message: "JavaScript执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `JavaScript执行器测试失败: ${error.message}` };
        }
    }
    // 测试Python执行功能  
    async function testPython() {
        try {
            // 检查Python是否可用
            const pythonCheckResult = await Tools.System.terminal("python3 --version", undefined, 10000);
            if (pythonCheckResult.exitCode !== 0) {
                return { success: false, message: "Python不可用，请确保已安装Python" };
            }
            // 测试简单的Python代码
            const script = "print('Python运行正常')";
            await run_python({ script });
            return { success: true, message: "Python执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Python执行器测试失败: ${error.message}` };
        }
    }
    // 测试Ruby执行功能
    async function testRuby() {
        try {
            // 检查Ruby是否可用
            const rubyCheckResult = await Tools.System.terminal("ruby --version", undefined, 10000);
            if (rubyCheckResult.exitCode !== 0) {
                return { success: false, message: "Ruby不可用，请确保已安装Ruby" };
            }
            // 测试简单的Ruby代码
            const script = "puts 'Ruby运行正常'";
            await run_ruby({ script });
            return { success: true, message: "Ruby执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Ruby执行器测试失败: ${error.message}` };
        }
    }
    // 测试Go执行功能
    async function testGo() {
        try {
            // 检查Go是否可用
            const goCheckResult = await Tools.System.terminal("go version", undefined, 10000);
            if (goCheckResult.exitCode !== 0) {
                return { success: false, message: "Go不可用，请确保已安装Go" };
            }
            // 测试简单的Go代码
            const script = `
package main

import "fmt"

func main() {
  fmt.Println("Go运行正常")
}`;
            await run_go({ script });
            return { success: true, message: "Go执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Go执行器测试失败: ${error.message}` };
        }
    }
    // 测试Rust执行功能
    async function testRust() {
        try {
            // 检查Rust是否可用
            const rustCheckResult = await Tools.System.terminal("rustc --version", undefined, 10000);
            if (rustCheckResult.exitCode !== 0) {
                return { success: false, message: "Rust不可用，请确保已安装Rust" };
            }
            // 测试简单的Rust代码
            const script = `
fn main() {
  println!("Rust运行正常");
}`;
            await run_rust({ script });
            return { success: true, message: "Rust执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Rust执行器测试失败: ${error.message}` };
        }
    }
    /**
     * 运行自定义 JavaScript 脚本
     * Runs custom JavaScript code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    async function run_javascript_es5(params) {
        // 执行自定义脚本
        // 获取脚本内容
        const script = params.script;
        // 检查脚本是否为空
        if (!script || script.trim() === "") {
            complete("请提供要执行的脚本内容");
        }
        else {
            try {
                const res = eval(script);
                // 返回执行结果
                complete(res);
            }
            catch (error) {
                complete(`执行脚本时出错: ${error.message}`);
            }
        }
    }
    /**
     * 运行 JavaScript 文件
     * Runs JavaScript code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    async function run_javascript_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            complete("请提供要执行的 JavaScript 文件路径");
            return;
        }
        try {
            // 读取文件内容
            const fileResult = await Tools.Files.read(filePath);
            if (!fileResult || !fileResult.content) {
                complete(`无法读取文件: ${filePath}`);
                return;
            }
            // 执行文件内容
            try {
                const res = eval(fileResult.content);
                complete(res);
            }
            catch (error) {
                complete(`执行 JavaScript 文件时出错: ${error.message}`);
            }
        }
        catch (error) {
            complete(`读取文件时出错: ${error}`);
        }
    }
    /**
     * 运行自定义 Python 脚本
     * Runs custom Python code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    async function run_python(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            complete("请提供要执行的 Python 脚本内容");
            return;
        }
        try {
            // 创建临时文件存储 Python 代码
            // 使用/sdcard/Download目录作为临时文件位置，这个目录通常在Android上是可访问的
            const tempFilePath = "/sdcard/Download/Operit/temp_script.py";
            // 写入 Python 代码到临时文件
            await Tools.Files.write(tempFilePath, script);
            // 执行 Python 脚本
            const result = await Tools.System.terminal(`python3 ${tempFilePath}`, undefined, 30000);
            // 删除临时文件
            await Tools.Files.deleteFile(tempFilePath);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Python 脚本执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Python 脚本时出错: ${error}`);
        }
    }
    /**
     * 运行 Python 文件
     * Runs Python code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    async function run_python_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            complete("请提供要执行的 Python 文件路径");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(filePath);
            if (!fileExists || !fileExists.exists) {
                complete(`Python 文件不存在: ${filePath}`);
                return;
            }
            // 执行 Python 文件
            const result = await Tools.System.terminal(`python3 ${filePath}`, undefined, 30000);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Python 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Python 文件时出错: ${error}`);
        }
    }
    /**
     * 运行自定义 Ruby 脚本
     * Runs custom Ruby code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    async function run_ruby(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            complete("请提供要执行的 Ruby 脚本内容");
            return;
        }
        try {
            // 创建临时文件存储 Ruby 代码
            const tempFilePath = "/sdcard/Download/Operit/temp_script.rb";
            // 写入 Ruby 代码到临时文件
            await Tools.Files.write(tempFilePath, script);
            // 执行 Ruby 脚本
            const result = await Tools.System.terminal(`ruby ${tempFilePath}`, undefined, 30000);
            // 删除临时文件
            await Tools.Files.deleteFile(tempFilePath);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Ruby 脚本执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Ruby 脚本时出错: ${error}`);
        }
    }
    /**
     * 运行 Ruby 文件
     * Runs Ruby code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    async function run_ruby_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            complete("请提供要执行的 Ruby 文件路径");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(filePath);
            if (!fileExists || !fileExists.exists) {
                complete(`Ruby 文件不存在: ${filePath}`);
                return;
            }
            // 执行 Ruby 文件
            const result = await Tools.System.terminal(`ruby ${filePath}`, undefined, 30000);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Ruby 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Ruby 文件时出错: ${error}`);
        }
    }
    /**
     * 运行自定义 Go 代码
     * Runs custom Go code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    async function run_go(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            complete("请提供要执行的 Go 代码内容");
            return;
        }
        try {
            // 创建临时文件存储 Go 代码
            const tempDirPath = "/sdcard/Download/Operit/temp_go";
            const tempFilePath = `${tempDirPath}/main.go`;
            const tempExecPath = `${tempDirPath}/main`;
            // 创建临时目录
            await Tools.System.terminal(`mkdir -p ${tempDirPath}`, undefined, 10000);
            // 写入 Go 代码到临时文件
            await Tools.Files.write(tempFilePath, script);
            // 编译 Go 代码
            await Tools.System.terminal(`cd ${tempDirPath}`, undefined, 10000);
            const compileResult = await Tools.System.terminal(`go build -o main main.go`, undefined, 30000);
            if (compileResult.exitCode !== 0) {
                // 删除临时目录
                await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
                complete(`Go 代码编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
                return;
            }
            // 将编译后的二进制文件复制到Termux主目录中执行
            await Tools.System.terminal(`cp ${tempDirPath}/main /data/data/com.termux/files/home/temp_go_bin`, undefined, 10000);
            await Tools.System.terminal(`chmod +x /data/data/com.termux/files/home/temp_go_bin`, undefined, 10000);
            const result = await Tools.System.terminal(`/data/data/com.termux/files/home/temp_go_bin`, undefined, 30000);
            // 清理临时执行文件
            await Tools.System.terminal(`rm -f /data/data/com.termux/files/home/temp_go_bin`, undefined, 10000);
            // 删除临时目录
            await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Go 代码执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Go 代码时出错: ${error}`);
        }
    }
    /**
     * 运行 Go 文件
     * Runs Go code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    async function run_go_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            complete("请提供要执行的 Go 文件路径");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(filePath);
            if (!fileExists || !fileExists.exists) {
                complete(`Go 文件不存在: ${filePath}`);
                return;
            }
            // 获取临时执行文件路径
            const tempExecPath = "/sdcard/Download/Operit/temp_exec";
            // 编译 Go 文件
            const compileResult = await Tools.System.terminal(`go build -o ${tempExecPath} ${filePath}`, undefined, 30000);
            if (compileResult.exitCode !== 0) {
                await Tools.Files.deleteFile(tempExecPath);
                complete(`Go 文件编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
                return;
            }
            // 将编译后的二进制文件复制到Termux主目录中执行
            await Tools.System.terminal(`cp ${tempExecPath} /data/data/com.termux/files/home/temp_go_bin`, undefined, 10000);
            await Tools.System.terminal(`chmod +x /data/data/com.termux/files/home/temp_go_bin`, undefined, 10000);
            const result = await Tools.System.terminal(`/data/data/com.termux/files/home/temp_go_bin`, undefined, 30000);
            // 清理临时执行文件
            await Tools.System.terminal(`rm -f /data/data/com.termux/files/home/temp_go_bin`, undefined, 10000);
            await Tools.Files.deleteFile(tempExecPath);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Go 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Go 文件时出错: ${error}`);
        }
    }
    /**
     * 运行自定义 Rust 代码
     * Runs custom Rust code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    async function run_rust(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            complete("请提供要执行的 Rust 代码内容");
            return;
        }
        try {
            // 使用Termux的主目录而不是外部存储
            const tempDirPath = "/data/data/com.termux/files/home/temp_rust_project";
            const tempFilePath = `${tempDirPath}/src/main.rs`;
            // 创建Cargo.toml
            const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
            // 创建项目结构
            await Tools.System.terminal(`mkdir -p ${tempDirPath}/src`, undefined, 10000);
            await Tools.System.terminal(`echo '${cargoToml}' > ${tempDirPath}/Cargo.toml`, undefined, 10000);
            await Tools.System.terminal(`echo '${script.replace(/'/g, "'\\''")}' > ${tempFilePath}`, undefined, 10000);
            // 编译和执行Rust项目 - 分开cd和cargo命令
            await Tools.System.terminal(`cd ${tempDirPath}`, undefined, 10000);
            const compileResult = await Tools.System.terminal(`cargo build --release`, undefined, 60000);
            if (compileResult.exitCode !== 0) {
                // 删除临时项目目录
                await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
                complete(`Rust 代码编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
                return;
            }
            // 执行编译后的程序
            // 添加可执行权限
            await Tools.System.terminal(`chmod +x ${tempDirPath}/target/release/temp_rust_script`, undefined, 10000);
            // 直接在Termux环境中执行编译后的二进制文件
            const result = await Tools.System.terminal(`${tempDirPath}/target/release/temp_rust_script`, undefined, 30000);
            // 删除临时项目目录
            await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
            // 检查执行结果
            if (result.exitCode === 0) {
                complete(result.output.trim());
            }
            else {
                complete(`Rust 代码执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        catch (error) {
            complete(`执行 Rust 代码时出错: ${error}`);
        }
    }
    /**
     * 运行 Rust 文件
     * Runs Rust code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    async function run_rust_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            complete("请提供要执行的 Rust 文件路径");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(filePath);
            if (!fileExists || !fileExists.exists) {
                complete(`Rust 文件不存在: ${filePath}`);
                return;
            }
            // 我们需要确定文件是否已在一个Cargo项目中，或者是独立的.rs文件
            const isCargoProject = await Tools.Files.exists(filePath.replace(/\/src\/main\.rs$/, "/Cargo.toml"));
            if (isCargoProject && isCargoProject.exists) {
                // 文件在Cargo项目中
                const projectDir = filePath.replace(/\/src\/main\.rs$/, "");
                // 分开cd和cargo命令
                await Tools.System.terminal(`cd ${projectDir}`, undefined, 10000);
                const compileResult = await Tools.System.terminal(`cargo build --release`, undefined, 60000);
                if (compileResult.exitCode !== 0) {
                    complete(`Rust 项目编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
                    return;
                }
                // 执行编译后的程序
                // 添加可执行权限
                await Tools.System.terminal(`chmod +x ${projectDir}/target/release/$(basename ${projectDir})`, undefined, 10000);
                // 直接在Termux环境中执行编译后的二进制文件
                const result = await Tools.System.terminal(`${projectDir}/target/release/$(basename ${projectDir})`, undefined, 30000);
                if (result.exitCode === 0) {
                    complete(result.output.trim());
                }
                else {
                    complete(`Rust 项目执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
                }
            }
            else {
                // 独立的.rs文件需要创建临时Cargo项目
                const tempDirPath = "/data/data/com.termux/files/home/temp_rust_project";
                const tempFilePath = `${tempDirPath}/src/main.rs`;
                // 创建Cargo.toml
                const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
        `;
                // 创建项目结构
                await Tools.System.terminal(`mkdir -p ${tempDirPath}/src`, undefined, 10000);
                await Tools.System.terminal(`echo '${cargoToml}' > ${tempDirPath}/Cargo.toml`, undefined, 10000);
                // 读取原始文件内容并写入临时文件
                const fileContent = await Tools.Files.read(filePath);
                await Tools.System.terminal(`echo '${fileContent.content.replace(/'/g, "'\\''")}' > ${tempFilePath}`, undefined, 10000);
                // 编译和执行Rust项目 - 分开cd和cargo命令
                await Tools.System.terminal(`cd ${tempDirPath}`, undefined, 10000);
                const compileResult = await Tools.System.terminal(`cargo build --release`, undefined, 60000);
                if (compileResult.exitCode !== 0) {
                    // 删除临时项目目录
                    await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
                    complete(`Rust 文件编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
                    return;
                }
                // 执行编译后的程序
                // 添加可执行权限
                await Tools.System.terminal(`chmod +x ${tempDirPath}/target/release/temp_rust_script`, undefined, 10000);
                // 直接在Termux环境中执行编译后的二进制文件
                const result = await Tools.System.terminal(`${tempDirPath}/target/release/temp_rust_script`, undefined, 30000);
                // 删除临时项目目录
                await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
                if (result.exitCode === 0) {
                    complete(result.output.trim());
                }
                else {
                    complete(`Rust 项目执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
                }
            }
        }
        catch (error) {
            complete(`执行 Rust 文件时出错: ${error}`);
        }
    }
    /**
     * 包装函数 - 统一处理所有代码执行器函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     * @param additionalInfo 附加信息(可选)
     */
    async function code_runner_wrap(func, params, successMessage, failMessage, additionalInfo = "") {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));
            // 执行原始函数
            const result = await func(params);
            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));
            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined)
                return;
            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage,
                    additionalInfo: additionalInfo
                });
            }
            else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    additionalInfo: additionalInfo,
                    data: result
                });
            }
        }
        catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);
            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalInfo: additionalInfo,
                error_stack: error.stack
            });
        }
    }
    return {
        main: async () => await code_runner_wrap(main, {}, "代码执行器功能测试完成", "代码执行器功能测试失败"),
        run_javascript_es5: async (params) => await code_runner_wrap(run_javascript_es5, params, "JavaScript 脚本执行成功", "JavaScript 脚本执行失败"),
        run_javascript_file: async (params) => await code_runner_wrap(run_javascript_file, params, "JavaScript 文件执行成功", "JavaScript 文件执行失败"),
        run_python: async (params) => await code_runner_wrap(run_python, params, "Python 脚本执行成功", "Python 脚本执行失败"),
        run_python_file: async (params) => await code_runner_wrap(run_python_file, params, "Python 文件执行成功", "Python 文件执行失败"),
        run_ruby: async (params) => await code_runner_wrap(run_ruby, params, "Ruby 脚本执行成功", "Ruby 脚本执行失败"),
        run_ruby_file: async (params) => await code_runner_wrap(run_ruby_file, params, "Ruby 文件执行成功", "Ruby 文件执行失败"),
        run_go: async (params) => await code_runner_wrap(run_go, params, "Go 代码执行成功", "Go 代码执行失败"),
        run_go_file: async (params) => await code_runner_wrap(run_go_file, params, "Go 文件执行成功", "Go 文件执行失败"),
        run_rust: async (params) => await code_runner_wrap(run_rust, params, "Rust 代码执行成功", "Rust 代码执行失败"),
        run_rust_file: async (params) => await code_runner_wrap(run_rust_file, params, "Rust 文件执行成功", "Rust 文件执行失败")
    };
})();
// 逐个导出
exports.main = codeRunner.main;
exports.run_javascript_es5 = codeRunner.run_javascript_es5;
exports.run_javascript_file = codeRunner.run_javascript_file;
exports.run_python = codeRunner.run_python;
exports.run_python_file = codeRunner.run_python_file;
exports.run_ruby = codeRunner.run_ruby;
exports.run_ruby_file = codeRunner.run_ruby_file;
exports.run_go = codeRunner.run_go;
exports.run_go_file = codeRunner.run_go_file;
exports.run_rust = codeRunner.run_rust;
exports.run_rust_file = codeRunner.run_rust_file;

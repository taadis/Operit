/* METADATA
{
  name: code_runner
  description: 提供多语言代码执行能力，支持JavaScript、Python、Ruby、Go和Rust脚本的运行。可直接执行代码字符串或运行外部文件，适用于快速测试、自动化脚本和教学演示。
  
  // Multiple tools in this package
  tools: [
    {
      name: main
      description: 测试代码执行器的功能 (无需参数)
      parameters: []
    },
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
    // 测试结果收集
    const results: string[] = [];

    try {
      // 1. 测试 JavaScript 字符串执行
      results.push("--- JavaScript 字符串执行测试 ---");
      try {
        // 创建一个简单的JavaScript计算
        const jsScript = `
          // 简单的计算和字符串操作
          const num1 = 10;
          const num2 = 20;
          const sum = num1 + num2;
          
          // 创建数组并操作
          const fruits = ['苹果', '香蕉', '橙子'];
          fruits.push('葡萄');
          
          // 返回一个包含结果的对象
          ({
            calculation: \`\${num1} + \${num2} = \${sum}\`,
            fruits: fruits,
            timestamp: new Date().toISOString()
          })
        `;

        // 执行JavaScript脚本
        const jsResult = eval(jsScript);
        results.push("JavaScript 执行成功:");
        results.push(JSON.stringify(jsResult, null, 2));
      } catch (error) {
        results.push(`JavaScript 执行失败: ${error.message}`);
      }

      // 2. 测试 Python 字符串执行
      results.push("\n--- Python 字符串执行测试 ---");
      try {
        // 创建一个简单的Python脚本
        const pyScript = `
import sys
import datetime

# 简单的计算
num1 = 10
num2 = 20
sum_result = num1 + num2

# 列表操作
fruits = ['苹果', '香蕉', '橙子']
fruits.append('葡萄')

# 输出结果
print(f"Python 版本: {sys.version}")
print(f"计算结果: {num1} + {num2} = {sum_result}")
print(f"水果列表: {', '.join(fruits)}")
print(f"时间戳: {datetime.datetime.now()}")
        `;

        // 创建临时文件
        const tempFilePath = "/sdcard/Download/temp_test_script_" + Date.now() + ".py";
        await Tools.Files.write(tempFilePath, pyScript);

        // 执行Python脚本
        const pyResult = await Tools.System.terminal(`python3 ${tempFilePath}`, undefined, 30000);

        // 删除临时文件
        await Tools.Files.deleteFile(tempFilePath);

        if (pyResult.exitCode === 0) {
          results.push("Python 执行成功:");
          results.push(pyResult.output);
        } else {
          results.push(`Python 执行失败 (退出码: ${pyResult.exitCode}):`);
          results.push(pyResult.output);
        }
      } catch (error) {
        results.push(`Python 执行失败: ${error}`);
        results.push("注意: 这可能意味着设备上没有安装Python或无法访问临时目录");
      }

      // 3. 测试 Ruby 字符串执行
      results.push("\n--- Ruby 字符串执行测试 ---");
      try {
        // 创建一个简单的Ruby脚本
        const rubyScript = `
# 简单的计算
num1 = 10
num2 = 20
sum_result = num1 + num2

# 数组操作
fruits = ['苹果', '香蕉', '橙子']
fruits.push('葡萄')

# 输出结果
puts "Ruby 版本: #{RUBY_VERSION}"
puts "计算结果: #{num1} + #{num2} = #{sum_result}"
puts "水果列表: #{fruits.join(', ')}"
puts "时间戳: #{Time.now}"
        `;

        // 创建临时文件
        const tempFilePath = "/sdcard/Download/temp_test_script_" + Date.now() + ".rb";
        await Tools.Files.write(tempFilePath, rubyScript);

        // 执行Ruby脚本
        const rubyResult = await Tools.System.terminal(`ruby ${tempFilePath}`, undefined, 30000);

        // 删除临时文件
        await Tools.Files.deleteFile(tempFilePath);

        if (rubyResult.exitCode === 0) {
          results.push("Ruby 执行成功:");
          results.push(rubyResult.output);
        } else {
          results.push(`Ruby 执行失败 (退出码: ${rubyResult.exitCode}):`);
          results.push(rubyResult.output);
        }
      } catch (error) {
        results.push(`Ruby 执行失败: ${error}`);
        results.push("注意: 这可能意味着设备上没有安装Ruby或无法访问临时目录");
      }

      // 4. 测试 Go 字符串执行
      results.push("\n--- Go 字符串执行测试 ---");
      try {
        // 创建一个简单的Go脚本
        const goScript = `
package main

import (
    "fmt"
    "runtime"
    "strings"
    "time"
)

func main() {
    // 简单的计算
    num1 := 10
    num2 := 20
    sumResult := num1 + num2

    // 切片操作
    fruits := []string{"苹果", "香蕉", "橙子"}
    fruits = append(fruits, "葡萄")

    // 输出结果
    fmt.Printf("Go 版本: %s\\n", runtime.Version())
    fmt.Printf("计算结果: %d + %d = %d\\n", num1, num2, sumResult)
    fmt.Printf("水果列表: %s\\n", strings.Join(fruits, ", "))
    fmt.Printf("时间戳: %s\\n", time.Now().String())
}
        `;

        // 创建临时文件
        const tempFilePath = "/sdcard/Download/temp_test_script_" + Date.now() + ".go";
        await Tools.Files.write(tempFilePath, goScript);

        // 执行Go脚本 (Go需要先编译后运行)
        const tempExecPath = "/sdcard/Download/temp_exec_" + Date.now();
        const compileResult = await Tools.System.terminal(`go build -o ${tempExecPath} ${tempFilePath}`, undefined, 30000);

        let goResult;
        if (compileResult.exitCode === 0) {
          goResult = await Tools.System.terminal(`${tempExecPath}`, undefined, 30000);

          // 删除编译的执行文件
          await Tools.Files.deleteFile(tempExecPath);
        } else {
          goResult = compileResult;
        }

        // 删除临时文件
        await Tools.Files.deleteFile(tempFilePath);

        if (goResult.exitCode === 0) {
          results.push("Go 执行成功:");
          results.push(goResult.output);
        } else {
          results.push(`Go 执行失败 (退出码: ${goResult.exitCode}):`);
          results.push(goResult.output);
        }
      } catch (error) {
        results.push(`Go 执行失败: ${error}`);
        results.push("注意: 这可能意味着设备上没有安装Go或无法访问临时目录");
      }

      // 5. 测试 Rust 字符串执行
      results.push("\n--- Rust 字符串执行测试 ---");
      try {
        // 创建一个简单的Rust脚本
        const rustScript = `
fn main() {
    // 简单的计算
    let num1 = 10;
    let num2 = 20;
    let sum_result = num1 + num2;
    
    // 向量操作
    let mut fruits = vec!["苹果", "香蕉", "橙子"];
    fruits.push("葡萄");
    
    // 输出结果
    println!("Rust 版本: {}", rustc_version());
    println!("计算结果: {} + {} = {}", num1, num2, sum_result);
    println!("水果列表: {}", fruits.join(", "));
    println!("时间戳: {}", chrono::Local::now());
}

fn rustc_version() -> String {
    // This is a simple function to get Rust version
    // In a real implementation we would use proper version detection
    "rustc 1.xx.x".to_string()
}
        `;

        // 创建临时文件和临时项目目录
        const tempDirPath = "/sdcard/Download/temp_rust_" + Date.now();
        const tempFilePath = `${tempDirPath}/src/main.rs`;

        // 创建Cargo.toml
        const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
chrono = "0.4"
        `;

        // 创建项目结构
        await Tools.System.terminal(`mkdir -p ${tempDirPath}/src`, undefined, 10000);
        await Tools.Files.write(`${tempDirPath}/Cargo.toml`, cargoToml);
        await Tools.Files.write(tempFilePath, rustScript);

        // 编译和执行Rust项目
        const compileResult = await Tools.System.terminal(`cd ${tempDirPath} && cargo build --release`, undefined, 60000);

        let rustResult;
        if (compileResult.exitCode === 0) {
          rustResult = await Tools.System.terminal(`cd ${tempDirPath} && ./target/release/temp_rust_script`, undefined, 30000);
        } else {
          rustResult = compileResult;
        }

        // 删除临时项目目录
        await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);

        if (rustResult.exitCode === 0) {
          results.push("Rust 执行成功:");
          results.push(rustResult.output);
        } else {
          results.push(`Rust 执行失败 (退出码: ${rustResult.exitCode}):`);
          results.push(rustResult.output);
        }
      } catch (error) {
        results.push(`Rust 执行失败: ${error}`);
        results.push("注意: 这可能意味着设备上没有安装Rust或无法访问临时目录");
      }

      // 返回所有测试结果
      complete(results.join("\n"));
    } catch (error) {
      complete(`测试运行失败: ${error}`);
    }
  }

  /**
   * 运行自定义 JavaScript 脚本
   * Runs custom JavaScript code provided as a parameter
   * @param {Object} params - The parameters object containing script
   */
  async function run_javascript_es5(params: { script: string }) {
    // 执行自定义脚本
    // 获取脚本内容
    const script = params.script;

    // 检查脚本是否为空
    if (!script || script.trim() === "") {
      complete("请提供要执行的脚本内容");
    } else {
      try {
        const res = eval(script);
        // 返回执行结果
        complete(res);
      } catch (error) {
        complete(`执行脚本时出错: ${error.message}`);
      }
    }
  }

  /**
   * 运行 JavaScript 文件
   * Runs JavaScript code from a file
   * @param {Object} params - The parameters object containing file_path
   */
  async function run_javascript_file(params: { file_path: string }) {
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
      } catch (error) {
        complete(`执行 JavaScript 文件时出错: ${error.message}`);
      }
    } catch (error) {
      complete(`读取文件时出错: ${error}`);
    }
  }

  /**
   * 运行自定义 Python 脚本
   * Runs custom Python code provided as a parameter
   * @param {Object} params - The parameters object containing script
   */
  async function run_python(params: { script: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      complete("请提供要执行的 Python 脚本内容");
      return;
    }

    try {
      // 创建临时文件存储 Python 代码
      // 使用/sdcard/Download目录作为临时文件位置，这个目录通常在Android上是可访问的
      const tempFilePath = "/sdcard/Download/temp_script_" + Date.now() + ".py";

      // 写入 Python 代码到临时文件
      await Tools.Files.write(tempFilePath, script);

      // 执行 Python 脚本
      const result = await Tools.System.terminal(`python3 ${tempFilePath}`, undefined, 30000);

      // 删除临时文件
      await Tools.Files.deleteFile(tempFilePath);

      // 检查执行结果
      if (result.exitCode === 0) {
        complete(result.output.trim());
      } else {
        complete(`Python 脚本执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Python 脚本时出错: ${error}`);
    }
  }

  /**
   * 运行 Python 文件
   * Runs Python code from a file
   * @param {Object} params - The parameters object containing file_path
   */
  async function run_python_file(params: { file_path: string }) {
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
      } else {
        complete(`Python 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Python 文件时出错: ${error}`);
    }
  }

  /**
   * 运行自定义 Ruby 脚本
   * Runs custom Ruby code provided as a parameter
   * @param {Object} params - The parameters object containing script
   */
  async function run_ruby(params: { script: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      complete("请提供要执行的 Ruby 脚本内容");
      return;
    }

    try {
      // 创建临时文件存储 Ruby 代码
      const tempFilePath = "/sdcard/Download/temp_script_" + Date.now() + ".rb";

      // 写入 Ruby 代码到临时文件
      await Tools.Files.write(tempFilePath, script);

      // 执行 Ruby 脚本
      const result = await Tools.System.terminal(`ruby ${tempFilePath}`, undefined, 30000);

      // 删除临时文件
      await Tools.Files.deleteFile(tempFilePath);

      // 检查执行结果
      if (result.exitCode === 0) {
        complete(result.output.trim());
      } else {
        complete(`Ruby 脚本执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Ruby 脚本时出错: ${error}`);
    }
  }

  /**
   * 运行 Ruby 文件
   * Runs Ruby code from a file
   * @param {Object} params - The parameters object containing file_path
   */
  async function run_ruby_file(params: { file_path: string }) {
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
      } else {
        complete(`Ruby 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Ruby 文件时出错: ${error}`);
    }
  }

  /**
   * 运行自定义 Go 代码
   * Runs custom Go code provided as a parameter
   * @param {Object} params - The parameters object containing script
   */
  async function run_go(params: { script: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      complete("请提供要执行的 Go 代码内容");
      return;
    }

    try {
      // 创建临时文件存储 Go 代码
      const tempDirPath = "/sdcard/Download/temp_go_" + Date.now();
      const tempFilePath = `${tempDirPath}/main.go`;
      const tempExecPath = `${tempDirPath}/main`;

      // 创建临时目录
      await Tools.System.terminal(`mkdir -p ${tempDirPath}`, undefined, 10000);

      // 写入 Go 代码到临时文件
      await Tools.Files.write(tempFilePath, script);

      // 编译 Go 代码
      const compileResult = await Tools.System.terminal(`cd ${tempDirPath} && go build -o main main.go`, undefined, 30000);

      if (compileResult.exitCode !== 0) {
        // 删除临时目录
        await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
        complete(`Go 代码编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
        return;
      }

      // 执行编译后的程序
      const result = await Tools.System.terminal(`${tempExecPath}`, undefined, 30000);

      // 删除临时目录
      await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);

      // 检查执行结果
      if (result.exitCode === 0) {
        complete(result.output.trim());
      } else {
        complete(`Go 代码执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Go 代码时出错: ${error}`);
    }
  }

  /**
   * 运行 Go 文件
   * Runs Go code from a file
   * @param {Object} params - The parameters object containing file_path
   */
  async function run_go_file(params: { file_path: string }) {
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
      const tempExecPath = "/sdcard/Download/temp_exec_" + Date.now();

      // 编译 Go 文件
      const compileResult = await Tools.System.terminal(`go build -o ${tempExecPath} ${filePath}`, undefined, 30000);

      if (compileResult.exitCode !== 0) {
        await Tools.Files.deleteFile(tempExecPath);
        complete(`Go 文件编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
        return;
      }

      // 执行编译后的程序
      const result = await Tools.System.terminal(`${tempExecPath}`, undefined, 30000);

      // 删除临时执行文件
      await Tools.Files.deleteFile(tempExecPath);

      // 检查执行结果
      if (result.exitCode === 0) {
        complete(result.output.trim());
      } else {
        complete(`Go 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Go 文件时出错: ${error}`);
    }
  }

  /**
   * 运行自定义 Rust 代码
   * Runs custom Rust code provided as a parameter
   * @param {Object} params - The parameters object containing script
   */
  async function run_rust(params: { script: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      complete("请提供要执行的 Rust 代码内容");
      return;
    }

    try {
      // 创建临时文件和临时项目目录
      const tempDirPath = "/sdcard/Download/temp_rust_" + Date.now();
      const tempFilePath = `${tempDirPath}/src/main.rs`;

      // 创建Cargo.toml
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
chrono = "0.4"
      `;

      // 创建项目结构
      await Tools.System.terminal(`mkdir -p ${tempDirPath}/src`, undefined, 10000);
      await Tools.Files.write(`${tempDirPath}/Cargo.toml`, cargoToml);
      await Tools.Files.write(tempFilePath, script);

      // 编译和执行Rust项目
      const compileResult = await Tools.System.terminal(`cd ${tempDirPath} && cargo build --release`, undefined, 60000);

      if (compileResult.exitCode !== 0) {
        // 删除临时项目目录
        await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
        complete(`Rust 代码编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
        return;
      }

      // 执行编译后的程序
      const result = await Tools.System.terminal(`cd ${tempDirPath} && ./target/release/temp_rust_script`, undefined, 30000);

      // 删除临时项目目录
      await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);

      // 检查执行结果
      if (result.exitCode === 0) {
        complete(result.output.trim());
      } else {
        complete(`Rust 代码执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
      }
    } catch (error) {
      complete(`执行 Rust 代码时出错: ${error}`);
    }
  }

  /**
   * 运行 Rust 文件
   * Runs Rust code from a file
   * @param {Object} params - The parameters object containing file_path
   */
  async function run_rust_file(params: { file_path: string }) {
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
        const compileResult = await Tools.System.terminal(`cd ${projectDir} && cargo build --release`, undefined, 60000);

        if (compileResult.exitCode !== 0) {
          complete(`Rust 项目编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
          return;
        }

        // 执行编译后的程序
        const result = await Tools.System.terminal(`cd ${projectDir} && ./target/release/$(basename ${projectDir})`, undefined, 30000);

        if (result.exitCode === 0) {
          complete(result.output.trim());
        } else {
          complete(`Rust 项目执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
        }
      } else {
        // 独立的.rs文件需要创建临时Cargo项目
        const tempDirPath = "/sdcard/Download/temp_rust_" + Date.now();
        const tempFilePath = `${tempDirPath}/src/main.rs`;

        // 创建Cargo.toml
        const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
chrono = "0.4"
        `;

        // 创建项目结构
        await Tools.System.terminal(`mkdir -p ${tempDirPath}/src`, undefined, 10000);
        await Tools.Files.write(`${tempDirPath}/Cargo.toml`, cargoToml);

        // 读取原始文件内容并写入临时文件
        const fileContent = await Tools.Files.read(filePath);
        await Tools.Files.write(tempFilePath, fileContent.content);

        // 编译和执行Rust项目
        const compileResult = await Tools.System.terminal(`cd ${tempDirPath} && cargo build --release`, undefined, 60000);

        if (compileResult.exitCode !== 0) {
          // 删除临时项目目录
          await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);
          complete(`Rust 文件编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
          return;
        }

        // 执行编译后的程序
        const result = await Tools.System.terminal(`cd ${tempDirPath} && ./target/release/temp_rust_script`, undefined, 30000);

        // 删除临时项目目录
        await Tools.System.terminal(`rm -rf ${tempDirPath}`, undefined, 10000);

        if (result.exitCode === 0) {
          complete(result.output.trim());
        } else {
          complete(`Rust 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
        }
      }
    } catch (error) {
      complete(`执行 Rust 文件时出错: ${error}`);
    }
  }

  return {
    main: async () => await main(),
    run_javascript_es5: async (params) => await run_javascript_es5(params),
    run_javascript_file: async (params) => await run_javascript_file(params),
    run_python: async (params) => await run_python(params),
    run_python_file: async (params) => await run_python_file(params),
    run_ruby: async (params) => await run_ruby(params),
    run_ruby_file: async (params) => await run_ruby_file(params),
    run_go: async (params) => await run_go(params),
    run_go_file: async (params) => await run_go_file(params),
    run_rust: async (params) => await run_rust(params),
    run_rust_file: async (params) => await run_rust_file(params)
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
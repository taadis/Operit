/* METADATA
{
  name: code_runner
  description: 提供多语言代码执行能力，支持JavaScript和Python脚本的运行。可直接执行代码字符串或运行外部文件，适用于快速测试、自动化脚本和教学演示。
  
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
        const results = [];
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
            }
            catch (error) {
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
                }
                else {
                    results.push(`Python 执行失败 (退出码: ${pyResult.exitCode}):`);
                    results.push(pyResult.output);
                }
            }
            catch (error) {
                results.push(`Python 执行失败: ${error}`);
                results.push("注意: 这可能意味着设备上没有安装Python或无法访问临时目录");
            }
            // 返回所有测试结果
            complete(results.join("\n"));
        }
        catch (error) {
            complete(`测试运行失败: ${error}`);
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
    return {
        main: async () => await main(),
        run_javascript_es5: async (params) => await run_javascript_es5(params),
        run_javascript_file: async (params) => await run_javascript_file(params),
        run_python: async (params) => await run_python(params),
        run_python_file: async (params) => await run_python_file(params)
    };
})();
// 逐个导出
exports.main = codeRunner.main;
exports.run_javascript_es5 = codeRunner.run_javascript_es5;
exports.run_javascript_file = codeRunner.run_javascript_file;
exports.run_python = codeRunner.run_python;
exports.run_python_file = codeRunner.run_python_file;

/* METADATA
{
  // JavaScript Tester Package - HJSON version
  name: javascript_tester
  description: 测试和展示 JavaScript 脚本功能，包含多种示例
  
  // Multiple tools in this package
  tools: [
    {
      name: calculator_demo
      description: JavaScript 计算器功能演示
      parameters: []
    },
    {
      name: variable_demo
      description: JavaScript 变量和数据类型演示
      parameters: []
    },
    {
      name: native_http_test
      description: 使用 JavaScript 调用原生 HTTP 请求工具
      parameters: []
    },
    {
      name: custom_script
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
    }
  ]
  
  // Tool category
  category: NETWORK
}
*/

/**
 * JavaScript Tester Package
 * A collection of tools to demonstrate JavaScript functionality in the app
 */


/**
 * JavaScript 计算器功能演示
 * Demonstrates basic calculator functions
 */
exports.calculator_demo = function() {
    // Simple calculator functions
    function add(a, b) { return a + b; }
    function subtract(a, b) { return a - b; }
    function multiply(a, b) { return a * b; }
    function divide(a, b) { return b !== 0 ? a / b : "Cannot divide by zero"; }
    
    // Return results of various operations
    complete({
        addition: add(5, 3),
        subtraction: subtract(10, 4),
        multiplication: multiply(6, 7),
        division: divide(20, 5),
        divideByZero: divide(8, 0)
    });
};

/**
 * JavaScript 变量和数据类型演示
 * Demonstrates various JavaScript data types
 */
exports.variable_demo = function() {
    // Demonstrating various JavaScript data types
    const stringValue = "Hello, world!";
    const numberValue = 42;
    const booleanValue = true;
    const arrayValue = [1, 2, 3, 4, 5];
    const objectValue = { name: "Test Object", properties: ["color", "size"] };
    
    // Create a date object
    const dateValue = new Date();
    
    // Return all values
    complete({
        string: stringValue,
        number: numberValue,
        boolean: booleanValue,
        array: arrayValue,
        object: objectValue,
        date: dateValue.toString()
    });
};

/**
 * 使用 JavaScript 调用原生 HTTP 请求工具
 * Demonstrates calling native HTTP functions
 */
exports.native_http_test = function() {
    console.log("调用 fetch_web_page 工具...");
    const fetchResult = toolCall("fetch_web_page", { 
        url: "https://baidu.com",
        format: "text"
    });
    complete({"results":fetchResult});
};

/**
 * 运行自定义 JavaScript 脚本
 * Runs custom JavaScript code provided as a parameter
 * @param {Object} params - The parameters object containing script
 */
exports.custom_script = function(params) {
    // 执行自定义脚本
    // 获取脚本内容
    const script = params.script;
    
    // 检查脚本是否为空
    if (!script || script.trim() === "") {
        complete("请提供要执行的脚本内容");
    } else {
        // 返回接收到的脚本内容
        complete({
            message: "已接收脚本内容",
            script: script,
            length: script.length
        });
    }
};

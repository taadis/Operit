/* METADATA
{
  name: code_runner
  description: 运行代码
  
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
    }
  ]
  
  // Tool category
  category: SYSTEM
}
*/
/**
 * 运行自定义 JavaScript 脚本
 * Runs custom JavaScript code provided as a parameter
 * @param {Object} params - The parameters object containing script
 */
exports.run_javascript_es5 = function (params) {
    // 执行自定义脚本
    // 获取脚本内容
    const script = params.script;
    // 检查脚本是否为空
    if (!script || script.trim() === "") {
        complete("请提供要执行的脚本内容");
    }
    else {
        const res = eval(script);
        // 返回接收到的脚本内容
        complete(res);
    }
};

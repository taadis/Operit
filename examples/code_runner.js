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
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var codeRunner = (function () {
    var _this = this;
    /**
     * 测试代码执行器的功能
     * Tests all code runner functionality without any parameters
     */
    function main() {
        return __awaiter(this, void 0, void 0, function () {
            var results, summary, _i, _a, _b, lang, result;
            var _c;
            return __generator(this, function (_d) {
                switch (_d.label) {
                    case 0:
                        _c = {};
                        return [4 /*yield*/, testJavaScript()];
                    case 1:
                        _c.javascript = _d.sent();
                        return [4 /*yield*/, testPython()];
                    case 2:
                        _c.python = _d.sent();
                        return [4 /*yield*/, testRuby()];
                    case 3:
                        _c.ruby = _d.sent();
                        return [4 /*yield*/, testGo()];
                    case 4:
                        _c.go = _d.sent();
                        return [4 /*yield*/, testRust()];
                    case 5:
                        results = (_c.rust = _d.sent(),
                            _c);
                        summary = "代码执行器功能测试结果：\n";
                        for (_i = 0, _a = Object.entries(results); _i < _a.length; _i++) {
                            _b = _a[_i], lang = _b[0], result = _b[1];
                            summary += "".concat(lang, ": ").concat(result.success ? '✅ 成功' : '❌ 失败', " - ").concat(result.message, "\n");
                        }
                        return [2 /*return*/, summary];
                }
            });
        });
    }
    // 测试JavaScript执行功能
    function testJavaScript() {
        return __awaiter(this, void 0, void 0, function () {
            var script, result, error_1;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        script = "const testVar = 42; return 'JavaScript运行正常，测试值: ' + testVar;";
                        return [4 /*yield*/, run_javascript_es5({ script: script })];
                    case 1:
                        result = _a.sent();
                        return [2 /*return*/, { success: true, message: "JavaScript执行器测试成功" }];
                    case 2:
                        error_1 = _a.sent();
                        return [2 /*return*/, { success: false, message: "JavaScript\u6267\u884C\u5668\u6D4B\u8BD5\u5931\u8D25: ".concat(error_1.message) }];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    // 测试Python执行功能  
    function testPython() {
        return __awaiter(this, void 0, void 0, function () {
            var pythonCheckResult, script, error_2;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, Tools.System.terminal("python3 --version", undefined, 10000)];
                    case 1:
                        pythonCheckResult = _a.sent();
                        if (pythonCheckResult.exitCode !== 0) {
                            return [2 /*return*/, { success: false, message: "Python不可用，请确保已安装Python" }];
                        }
                        script = "print('Python运行正常')";
                        return [4 /*yield*/, run_python({ script: script })];
                    case 2:
                        _a.sent();
                        return [2 /*return*/, { success: true, message: "Python执行器测试成功" }];
                    case 3:
                        error_2 = _a.sent();
                        return [2 /*return*/, { success: false, message: "Python\u6267\u884C\u5668\u6D4B\u8BD5\u5931\u8D25: ".concat(error_2.message) }];
                    case 4: return [2 /*return*/];
                }
            });
        });
    }
    // 测试Ruby执行功能
    function testRuby() {
        return __awaiter(this, void 0, void 0, function () {
            var rubyCheckResult, script, error_3;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, Tools.System.terminal("ruby --version", undefined, 10000)];
                    case 1:
                        rubyCheckResult = _a.sent();
                        if (rubyCheckResult.exitCode !== 0) {
                            return [2 /*return*/, { success: false, message: "Ruby不可用，请确保已安装Ruby" }];
                        }
                        script = "puts 'Ruby运行正常'";
                        return [4 /*yield*/, run_ruby({ script: script })];
                    case 2:
                        _a.sent();
                        return [2 /*return*/, { success: true, message: "Ruby执行器测试成功" }];
                    case 3:
                        error_3 = _a.sent();
                        return [2 /*return*/, { success: false, message: "Ruby\u6267\u884C\u5668\u6D4B\u8BD5\u5931\u8D25: ".concat(error_3.message) }];
                    case 4: return [2 /*return*/];
                }
            });
        });
    }
    // 测试Go执行功能
    function testGo() {
        return __awaiter(this, void 0, void 0, function () {
            var goCheckResult, script, error_4;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, Tools.System.terminal("go version", undefined, 10000)];
                    case 1:
                        goCheckResult = _a.sent();
                        if (goCheckResult.exitCode !== 0) {
                            return [2 /*return*/, { success: false, message: "Go不可用，请确保已安装Go" }];
                        }
                        script = "\npackage main\n\nimport \"fmt\"\n\nfunc main() {\n  fmt.Println(\"Go\u8FD0\u884C\u6B63\u5E38\")\n}";
                        return [4 /*yield*/, run_go({ script: script })];
                    case 2:
                        _a.sent();
                        return [2 /*return*/, { success: true, message: "Go执行器测试成功" }];
                    case 3:
                        error_4 = _a.sent();
                        return [2 /*return*/, { success: false, message: "Go\u6267\u884C\u5668\u6D4B\u8BD5\u5931\u8D25: ".concat(error_4.message) }];
                    case 4: return [2 /*return*/];
                }
            });
        });
    }
    // 测试Rust执行功能
    function testRust() {
        return __awaiter(this, void 0, void 0, function () {
            var rustCheckResult, script, error_5;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, Tools.System.terminal("rustc --version", undefined, 10000)];
                    case 1:
                        rustCheckResult = _a.sent();
                        if (rustCheckResult.exitCode !== 0) {
                            return [2 /*return*/, { success: false, message: "Rust不可用，请确保已安装Rust" }];
                        }
                        script = "\nfn main() {\n  println!(\"Rust\u8FD0\u884C\u6B63\u5E38\");\n}";
                        return [4 /*yield*/, run_rust({ script: script })];
                    case 2:
                        _a.sent();
                        return [2 /*return*/, { success: true, message: "Rust执行器测试成功" }];
                    case 3:
                        error_5 = _a.sent();
                        return [2 /*return*/, { success: false, message: "Rust\u6267\u884C\u5668\u6D4B\u8BD5\u5931\u8D25: ".concat(error_5.message) }];
                    case 4: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行自定义 JavaScript 脚本
     * Runs custom JavaScript code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    function run_javascript_es5(params) {
        return __awaiter(this, void 0, void 0, function () {
            var script, res;
            return __generator(this, function (_a) {
                script = params.script;
                // 检查脚本是否为空
                if (!script || script.trim() === "") {
                    complete("请提供要执行的脚本内容");
                }
                else {
                    try {
                        res = eval(script);
                        // 返回执行结果
                        complete(res);
                    }
                    catch (error) {
                        complete("\u6267\u884C\u811A\u672C\u65F6\u51FA\u9519: ".concat(error.message));
                    }
                }
                return [2 /*return*/];
            });
        });
    }
    /**
     * 运行 JavaScript 文件
     * Runs JavaScript code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    function run_javascript_file(params) {
        return __awaiter(this, void 0, void 0, function () {
            var filePath, fileResult, res, error_6;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        filePath = params.file_path;
                        if (!filePath || filePath.trim() === "") {
                            complete("请提供要执行的 JavaScript 文件路径");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 3, , 4]);
                        return [4 /*yield*/, Tools.Files.read(filePath)];
                    case 2:
                        fileResult = _a.sent();
                        if (!fileResult || !fileResult.content) {
                            complete("\u65E0\u6CD5\u8BFB\u53D6\u6587\u4EF6: ".concat(filePath));
                            return [2 /*return*/];
                        }
                        // 执行文件内容
                        try {
                            res = eval(fileResult.content);
                            complete(res);
                        }
                        catch (error) {
                            complete("\u6267\u884C JavaScript \u6587\u4EF6\u65F6\u51FA\u9519: ".concat(error.message));
                        }
                        return [3 /*break*/, 4];
                    case 3:
                        error_6 = _a.sent();
                        complete("\u8BFB\u53D6\u6587\u4EF6\u65F6\u51FA\u9519: ".concat(error_6));
                        return [3 /*break*/, 4];
                    case 4: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行自定义 Python 脚本
     * Runs custom Python code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    function run_python(params) {
        return __awaiter(this, void 0, void 0, function () {
            var script, tempFilePath, result, error_7;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        script = params.script;
                        if (!script || script.trim() === "") {
                            complete("请提供要执行的 Python 脚本内容");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 5, , 6]);
                        tempFilePath = "/sdcard/Download/Operit/temp_script.py";
                        // 写入 Python 代码到临时文件
                        return [4 /*yield*/, Tools.Files.write(tempFilePath, script)];
                    case 2:
                        // 写入 Python 代码到临时文件
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("python3 ".concat(tempFilePath), undefined, 30000)];
                    case 3:
                        result = _a.sent();
                        // 删除临时文件
                        return [4 /*yield*/, Tools.Files.deleteFile(tempFilePath)];
                    case 4:
                        // 删除临时文件
                        _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Python \u811A\u672C\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 6];
                    case 5:
                        error_7 = _a.sent();
                        complete("\u6267\u884C Python \u811A\u672C\u65F6\u51FA\u9519: ".concat(error_7));
                        return [3 /*break*/, 6];
                    case 6: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行 Python 文件
     * Runs Python code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    function run_python_file(params) {
        return __awaiter(this, void 0, void 0, function () {
            var filePath, fileExists, result, error_8;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        filePath = params.file_path;
                        if (!filePath || filePath.trim() === "") {
                            complete("请提供要执行的 Python 文件路径");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 4, , 5]);
                        return [4 /*yield*/, Tools.Files.exists(filePath)];
                    case 2:
                        fileExists = _a.sent();
                        if (!fileExists || !fileExists.exists) {
                            complete("Python \u6587\u4EF6\u4E0D\u5B58\u5728: ".concat(filePath));
                            return [2 /*return*/];
                        }
                        return [4 /*yield*/, Tools.System.terminal("python3 ".concat(filePath), undefined, 30000)];
                    case 3:
                        result = _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Python \u6587\u4EF6\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 5];
                    case 4:
                        error_8 = _a.sent();
                        complete("\u6267\u884C Python \u6587\u4EF6\u65F6\u51FA\u9519: ".concat(error_8));
                        return [3 /*break*/, 5];
                    case 5: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行自定义 Ruby 脚本
     * Runs custom Ruby code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    function run_ruby(params) {
        return __awaiter(this, void 0, void 0, function () {
            var script, tempFilePath, result, error_9;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        script = params.script;
                        if (!script || script.trim() === "") {
                            complete("请提供要执行的 Ruby 脚本内容");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 5, , 6]);
                        tempFilePath = "/sdcard/Download/Operit/temp_script.rb";
                        // 写入 Ruby 代码到临时文件
                        return [4 /*yield*/, Tools.Files.write(tempFilePath, script)];
                    case 2:
                        // 写入 Ruby 代码到临时文件
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("ruby ".concat(tempFilePath), undefined, 30000)];
                    case 3:
                        result = _a.sent();
                        // 删除临时文件
                        return [4 /*yield*/, Tools.Files.deleteFile(tempFilePath)];
                    case 4:
                        // 删除临时文件
                        _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Ruby \u811A\u672C\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 6];
                    case 5:
                        error_9 = _a.sent();
                        complete("\u6267\u884C Ruby \u811A\u672C\u65F6\u51FA\u9519: ".concat(error_9));
                        return [3 /*break*/, 6];
                    case 6: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行 Ruby 文件
     * Runs Ruby code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    function run_ruby_file(params) {
        return __awaiter(this, void 0, void 0, function () {
            var filePath, fileExists, result, error_10;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        filePath = params.file_path;
                        if (!filePath || filePath.trim() === "") {
                            complete("请提供要执行的 Ruby 文件路径");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 4, , 5]);
                        return [4 /*yield*/, Tools.Files.exists(filePath)];
                    case 2:
                        fileExists = _a.sent();
                        if (!fileExists || !fileExists.exists) {
                            complete("Ruby \u6587\u4EF6\u4E0D\u5B58\u5728: ".concat(filePath));
                            return [2 /*return*/];
                        }
                        return [4 /*yield*/, Tools.System.terminal("ruby ".concat(filePath), undefined, 30000)];
                    case 3:
                        result = _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Ruby \u6587\u4EF6\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 5];
                    case 4:
                        error_10 = _a.sent();
                        complete("\u6267\u884C Ruby \u6587\u4EF6\u65F6\u51FA\u9519: ".concat(error_10));
                        return [3 /*break*/, 5];
                    case 5: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行自定义 Go 代码
     * Runs custom Go code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    function run_go(params) {
        return __awaiter(this, void 0, void 0, function () {
            var script, tempDirPath, tempFilePath, tempExecPath, compileResult, result, error_11;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        script = params.script;
                        if (!script || script.trim() === "") {
                            complete("请提供要执行的 Go 代码内容");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 13, , 14]);
                        tempDirPath = "/sdcard/Download/Operit/temp_go";
                        tempFilePath = "".concat(tempDirPath, "/main.go");
                        tempExecPath = "".concat(tempDirPath, "/main");
                        // 创建临时目录
                        return [4 /*yield*/, Tools.System.terminal("mkdir -p ".concat(tempDirPath), undefined, 10000)];
                    case 2:
                        // 创建临时目录
                        _a.sent();
                        // 写入 Go 代码到临时文件
                        return [4 /*yield*/, Tools.Files.write(tempFilePath, script)];
                    case 3:
                        // 写入 Go 代码到临时文件
                        _a.sent();
                        // 编译 Go 代码
                        return [4 /*yield*/, Tools.System.terminal("cd ".concat(tempDirPath), undefined, 10000)];
                    case 4:
                        // 编译 Go 代码
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("go build -o main main.go", undefined, 30000)];
                    case 5:
                        compileResult = _a.sent();
                        if (!(compileResult.exitCode !== 0)) return [3 /*break*/, 7];
                        // 删除临时目录
                        return [4 /*yield*/, Tools.System.terminal("rm -rf ".concat(tempDirPath), undefined, 10000)];
                    case 6:
                        // 删除临时目录
                        _a.sent();
                        complete("Go \u4EE3\u7801\u7F16\u8BD1\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(compileResult.exitCode, "):\n").concat(compileResult.output));
                        return [2 /*return*/];
                    case 7: 
                    // 将编译后的二进制文件复制到Termux主目录中执行
                    return [4 /*yield*/, Tools.System.terminal("cp ".concat(tempDirPath, "/main /data/data/com.termux/files/home/temp_go_bin"), undefined, 10000)];
                    case 8:
                        // 将编译后的二进制文件复制到Termux主目录中执行
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("chmod +x /data/data/com.termux/files/home/temp_go_bin", undefined, 10000)];
                    case 9:
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("/data/data/com.termux/files/home/temp_go_bin", undefined, 30000)];
                    case 10:
                        result = _a.sent();
                        // 清理临时执行文件
                        return [4 /*yield*/, Tools.System.terminal("rm -f /data/data/com.termux/files/home/temp_go_bin", undefined, 10000)];
                    case 11:
                        // 清理临时执行文件
                        _a.sent();
                        // 删除临时目录
                        return [4 /*yield*/, Tools.System.terminal("rm -rf ".concat(tempDirPath), undefined, 10000)];
                    case 12:
                        // 删除临时目录
                        _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Go \u4EE3\u7801\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 14];
                    case 13:
                        error_11 = _a.sent();
                        complete("\u6267\u884C Go \u4EE3\u7801\u65F6\u51FA\u9519: ".concat(error_11));
                        return [3 /*break*/, 14];
                    case 14: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行 Go 文件
     * Runs Go code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    function run_go_file(params) {
        return __awaiter(this, void 0, void 0, function () {
            var filePath, fileExists, tempExecPath, compileResult, result, error_12;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        filePath = params.file_path;
                        if (!filePath || filePath.trim() === "") {
                            complete("请提供要执行的 Go 文件路径");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 11, , 12]);
                        return [4 /*yield*/, Tools.Files.exists(filePath)];
                    case 2:
                        fileExists = _a.sent();
                        if (!fileExists || !fileExists.exists) {
                            complete("Go \u6587\u4EF6\u4E0D\u5B58\u5728: ".concat(filePath));
                            return [2 /*return*/];
                        }
                        tempExecPath = "/sdcard/Download/Operit/temp_exec";
                        return [4 /*yield*/, Tools.System.terminal("go build -o ".concat(tempExecPath, " ").concat(filePath), undefined, 30000)];
                    case 3:
                        compileResult = _a.sent();
                        if (!(compileResult.exitCode !== 0)) return [3 /*break*/, 5];
                        return [4 /*yield*/, Tools.Files.deleteFile(tempExecPath)];
                    case 4:
                        _a.sent();
                        complete("Go \u6587\u4EF6\u7F16\u8BD1\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(compileResult.exitCode, "):\n").concat(compileResult.output));
                        return [2 /*return*/];
                    case 5: 
                    // 将编译后的二进制文件复制到Termux主目录中执行
                    return [4 /*yield*/, Tools.System.terminal("cp ".concat(tempExecPath, " /data/data/com.termux/files/home/temp_go_bin"), undefined, 10000)];
                    case 6:
                        // 将编译后的二进制文件复制到Termux主目录中执行
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("chmod +x /data/data/com.termux/files/home/temp_go_bin", undefined, 10000)];
                    case 7:
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("/data/data/com.termux/files/home/temp_go_bin", undefined, 30000)];
                    case 8:
                        result = _a.sent();
                        // 清理临时执行文件
                        return [4 /*yield*/, Tools.System.terminal("rm -f /data/data/com.termux/files/home/temp_go_bin", undefined, 10000)];
                    case 9:
                        // 清理临时执行文件
                        _a.sent();
                        return [4 /*yield*/, Tools.Files.deleteFile(tempExecPath)];
                    case 10:
                        _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Go \u6587\u4EF6\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 12];
                    case 11:
                        error_12 = _a.sent();
                        complete("\u6267\u884C Go \u6587\u4EF6\u65F6\u51FA\u9519: ".concat(error_12));
                        return [3 /*break*/, 12];
                    case 12: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行自定义 Rust 代码
     * Runs custom Rust code provided as a parameter
     * @param {Object} params - The parameters object containing script
     */
    function run_rust(params) {
        return __awaiter(this, void 0, void 0, function () {
            var script, tempDirPath, tempFilePath, cargoToml, compileResult, result, error_13;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        script = params.script;
                        if (!script || script.trim() === "") {
                            complete("请提供要执行的 Rust 代码内容");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 12, , 13]);
                        tempDirPath = "/data/data/com.termux/files/home/temp_rust_project";
                        tempFilePath = "".concat(tempDirPath, "/src/main.rs");
                        cargoToml = "\n[package]\nname = \"temp_rust_script\"\nversion = \"0.1.0\"\nedition = \"2021\"\n\n[dependencies]\n      ";
                        // 创建项目结构
                        return [4 /*yield*/, Tools.System.terminal("mkdir -p ".concat(tempDirPath, "/src"), undefined, 10000)];
                    case 2:
                        // 创建项目结构
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("echo '".concat(cargoToml, "' > ").concat(tempDirPath, "/Cargo.toml"), undefined, 10000)];
                    case 3:
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("echo '".concat(script.replace(/'/g, "'\\''"), "' > ").concat(tempFilePath), undefined, 10000)];
                    case 4:
                        _a.sent();
                        // 编译和执行Rust项目 - 分开cd和cargo命令
                        return [4 /*yield*/, Tools.System.terminal("cd ".concat(tempDirPath), undefined, 10000)];
                    case 5:
                        // 编译和执行Rust项目 - 分开cd和cargo命令
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("cargo build --release", undefined, 60000)];
                    case 6:
                        compileResult = _a.sent();
                        if (!(compileResult.exitCode !== 0)) return [3 /*break*/, 8];
                        // 删除临时项目目录
                        return [4 /*yield*/, Tools.System.terminal("rm -rf ".concat(tempDirPath), undefined, 10000)];
                    case 7:
                        // 删除临时项目目录
                        _a.sent();
                        complete("Rust \u4EE3\u7801\u7F16\u8BD1\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(compileResult.exitCode, "):\n").concat(compileResult.output));
                        return [2 /*return*/];
                    case 8: 
                    // 执行编译后的程序
                    // 添加可执行权限
                    return [4 /*yield*/, Tools.System.terminal("chmod +x ".concat(tempDirPath, "/target/release/temp_rust_script"), undefined, 10000)];
                    case 9:
                        // 执行编译后的程序
                        // 添加可执行权限
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("".concat(tempDirPath, "/target/release/temp_rust_script"), undefined, 30000)];
                    case 10:
                        result = _a.sent();
                        // 删除临时项目目录
                        return [4 /*yield*/, Tools.System.terminal("rm -rf ".concat(tempDirPath), undefined, 10000)];
                    case 11:
                        // 删除临时项目目录
                        _a.sent();
                        // 检查执行结果
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Rust \u4EE3\u7801\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 13];
                    case 12:
                        error_13 = _a.sent();
                        complete("\u6267\u884C Rust \u4EE3\u7801\u65F6\u51FA\u9519: ".concat(error_13));
                        return [3 /*break*/, 13];
                    case 13: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 运行 Rust 文件
     * Runs Rust code from a file
     * @param {Object} params - The parameters object containing file_path
     */
    function run_rust_file(params) {
        return __awaiter(this, void 0, void 0, function () {
            var filePath, fileExists, isCargoProject, projectDir, compileResult, result, tempDirPath, tempFilePath, cargoToml, fileContent, compileResult, result, error_14;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        filePath = params.file_path;
                        if (!filePath || filePath.trim() === "") {
                            complete("请提供要执行的 Rust 文件路径");
                            return [2 /*return*/];
                        }
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 21, , 22]);
                        return [4 /*yield*/, Tools.Files.exists(filePath)];
                    case 2:
                        fileExists = _a.sent();
                        if (!fileExists || !fileExists.exists) {
                            complete("Rust \u6587\u4EF6\u4E0D\u5B58\u5728: ".concat(filePath));
                            return [2 /*return*/];
                        }
                        return [4 /*yield*/, Tools.Files.exists(filePath.replace(/\/src\/main\.rs$/, "/Cargo.toml"))];
                    case 3:
                        isCargoProject = _a.sent();
                        if (!(isCargoProject && isCargoProject.exists)) return [3 /*break*/, 8];
                        projectDir = filePath.replace(/\/src\/main\.rs$/, "");
                        // 分开cd和cargo命令
                        return [4 /*yield*/, Tools.System.terminal("cd ".concat(projectDir), undefined, 10000)];
                    case 4:
                        // 分开cd和cargo命令
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("cargo build --release", undefined, 60000)];
                    case 5:
                        compileResult = _a.sent();
                        if (compileResult.exitCode !== 0) {
                            complete("Rust \u9879\u76EE\u7F16\u8BD1\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(compileResult.exitCode, "):\n").concat(compileResult.output));
                            return [2 /*return*/];
                        }
                        // 执行编译后的程序
                        // 添加可执行权限
                        return [4 /*yield*/, Tools.System.terminal("chmod +x ".concat(projectDir, "/target/release/$(basename ").concat(projectDir, ")"), undefined, 10000)];
                    case 6:
                        // 执行编译后的程序
                        // 添加可执行权限
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("".concat(projectDir, "/target/release/$(basename ").concat(projectDir, ")"), undefined, 30000)];
                    case 7:
                        result = _a.sent();
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Rust \u9879\u76EE\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        return [3 /*break*/, 20];
                    case 8:
                        tempDirPath = "/data/data/com.termux/files/home/temp_rust_project";
                        tempFilePath = "".concat(tempDirPath, "/src/main.rs");
                        cargoToml = "\n[package]\nname = \"temp_rust_script\"\nversion = \"0.1.0\"\nedition = \"2021\"\n\n[dependencies]\n        ";
                        // 创建项目结构
                        return [4 /*yield*/, Tools.System.terminal("mkdir -p ".concat(tempDirPath, "/src"), undefined, 10000)];
                    case 9:
                        // 创建项目结构
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("echo '".concat(cargoToml, "' > ").concat(tempDirPath, "/Cargo.toml"), undefined, 10000)];
                    case 10:
                        _a.sent();
                        return [4 /*yield*/, Tools.Files.read(filePath)];
                    case 11:
                        fileContent = _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("echo '".concat(fileContent.content.replace(/'/g, "'\\''"), "' > ").concat(tempFilePath), undefined, 10000)];
                    case 12:
                        _a.sent();
                        // 编译和执行Rust项目 - 分开cd和cargo命令
                        return [4 /*yield*/, Tools.System.terminal("cd ".concat(tempDirPath), undefined, 10000)];
                    case 13:
                        // 编译和执行Rust项目 - 分开cd和cargo命令
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("cargo build --release", undefined, 60000)];
                    case 14:
                        compileResult = _a.sent();
                        if (!(compileResult.exitCode !== 0)) return [3 /*break*/, 16];
                        // 删除临时项目目录
                        return [4 /*yield*/, Tools.System.terminal("rm -rf ".concat(tempDirPath), undefined, 10000)];
                    case 15:
                        // 删除临时项目目录
                        _a.sent();
                        complete("Rust \u6587\u4EF6\u7F16\u8BD1\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(compileResult.exitCode, "):\n").concat(compileResult.output));
                        return [2 /*return*/];
                    case 16: 
                    // 执行编译后的程序
                    // 添加可执行权限
                    return [4 /*yield*/, Tools.System.terminal("chmod +x ".concat(tempDirPath, "/target/release/temp_rust_script"), undefined, 10000)];
                    case 17:
                        // 执行编译后的程序
                        // 添加可执行权限
                        _a.sent();
                        return [4 /*yield*/, Tools.System.terminal("".concat(tempDirPath, "/target/release/temp_rust_script"), undefined, 30000)];
                    case 18:
                        result = _a.sent();
                        // 删除临时项目目录
                        return [4 /*yield*/, Tools.System.terminal("rm -rf ".concat(tempDirPath), undefined, 10000)];
                    case 19:
                        // 删除临时项目目录
                        _a.sent();
                        if (result.exitCode === 0) {
                            complete(result.output.trim());
                        }
                        else {
                            complete("Rust \u9879\u76EE\u6267\u884C\u5931\u8D25 (\u9000\u51FA\u7801: ".concat(result.exitCode, "):\n").concat(result.output));
                        }
                        _a.label = 20;
                    case 20: return [3 /*break*/, 22];
                    case 21:
                        error_14 = _a.sent();
                        complete("\u6267\u884C Rust \u6587\u4EF6\u65F6\u51FA\u9519: ".concat(error_14));
                        return [3 /*break*/, 22];
                    case 22: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 包装函数 - 统一处理所有代码执行器函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     * @param additionalInfo 附加信息(可选)
     */
    function code_runner_wrap(func_1, params_1, successMessage_1, failMessage_1) {
        return __awaiter(this, arguments, void 0, function (func, params, successMessage, failMessage, additionalInfo) {
            var result, error_15;
            if (additionalInfo === void 0) { additionalInfo = ""; }
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        console.log("\u5F00\u59CB\u6267\u884C\u51FD\u6570: ".concat(func.name || '匿名函数'));
                        console.log("\u53C2\u6570:", JSON.stringify(params, null, 2));
                        return [4 /*yield*/, func(params)];
                    case 1:
                        result = _a.sent();
                        console.log("\u51FD\u6570 ".concat(func.name || '匿名函数', " \u6267\u884C\u7ED3\u679C:"), JSON.stringify(result, null, 2));
                        // 如果原始函数已经调用了complete，就不需要再次调用
                        if (result === undefined)
                            return [2 /*return*/];
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
                        return [3 /*break*/, 3];
                    case 2:
                        error_15 = _a.sent();
                        // 详细记录错误信息
                        console.error("\u51FD\u6570 ".concat(func.name || '匿名函数', " \u6267\u884C\u5931\u8D25!"));
                        console.error("\u9519\u8BEF\u4FE1\u606F: ".concat(error_15.message));
                        console.error("\u9519\u8BEF\u5806\u6808: ".concat(error_15.stack));
                        // 处理错误
                        complete({
                            success: false,
                            message: "".concat(failMessage, ": ").concat(error_15.message),
                            additionalInfo: additionalInfo,
                            error_stack: error_15.stack
                        });
                        return [3 /*break*/, 3];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    return {
        main: function () { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(main, {}, "代码执行器功能测试完成", "代码执行器功能测试失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_javascript_es5: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_javascript_es5, params, "JavaScript 脚本执行成功", "JavaScript 脚本执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_javascript_file: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_javascript_file, params, "JavaScript 文件执行成功", "JavaScript 文件执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_python: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_python, params, "Python 脚本执行成功", "Python 脚本执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_python_file: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_python_file, params, "Python 文件执行成功", "Python 文件执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_ruby: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_ruby, params, "Ruby 脚本执行成功", "Ruby 脚本执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_ruby_file: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_ruby_file, params, "Ruby 文件执行成功", "Ruby 文件执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_go: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_go, params, "Go 代码执行成功", "Go 代码执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_go_file: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_go_file, params, "Go 文件执行成功", "Go 文件执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_rust: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_rust, params, "Rust 代码执行成功", "Rust 代码执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        run_rust_file: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, code_runner_wrap(run_rust_file, params, "Rust 文件执行成功", "Rust 文件执行失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); }
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

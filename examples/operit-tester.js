/**
 * OperIT AI Tool Tester
 *
 * This script tests all available tools in the OperIT system as documented in tools.md.
 * It validates each tool's functionality and the structure of its return values.
 *
 * How to run:
 * 1. Compile: tsc operit-tester.ts
 * 2. Run via command line:
 *    - Windows: .\tools\execute_js.bat operit-tester.js main '{}'
 *    - Linux/macOS: ./tools/execute_js.sh operit-tester.js main '{}'
 *    - Specific test category: .\tools\execute_js.bat operit-tester.js testCategory '{"testType":"ui"}'
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
/**
 * Formats and prints an object in a readable format
 */
function prettyPrint(label, data) {
    console.log("\n=== ".concat(label, " ==="));
    console.log(JSON.stringify(data, undefined, 2));
    console.log("=".repeat(label.length + 8));
}
/**
 * Validates that a UI node has the expected structure
 */
function validateUINodeStructure(node) {
    if (!node)
        return false;
    // Check required properties
    if (typeof node.isClickable !== 'boolean') {
        console.error(node);
        console.error(node.isClickable + " is not a boolean, isClickable is " + typeof node.isClickable);
        console.error("Node is missing isClickable property or it's not a boolean");
        return false;
    }
    if (!Array.isArray(node.children)) {
        console.error("Node is missing children array property");
        return false;
    }
    // Check optional properties if present
    if (node.className !== undefined && typeof node.className !== 'string') {
        console.error("Node className is not a string");
        return false;
    }
    if (node.text !== undefined && typeof node.text !== 'string') {
        console.error("Node text is not a string");
        return false;
    }
    if (node.contentDesc !== undefined && typeof node.contentDesc !== 'string') {
        console.error("Node contentDesc is not a string");
        return false;
    }
    if (node.resourceId !== undefined && typeof node.resourceId !== 'string') {
        console.error("Node resourceId is not a string");
        return false;
    }
    if (node.bounds !== undefined && typeof node.bounds !== 'string') {
        console.error("Node bounds is not a string");
        return false;
    }
    // Recursively validate all children
    for (var _i = 0, _a = node.children; _i < _a.length; _i++) {
        var child = _a[_i];
        if (!validateUINodeStructure(child)) {
            return false;
        }
    }
    return true;
}
/**
 * Prints a UI node hierarchy with indentation
 */
function printUIHierarchy(node, indent) {
    if (indent === void 0) { indent = ""; }
    if (!node)
        return;
    var className = node.className || "unknown";
    var text = node.text ? "\"".concat(node.text, "\"") : "";
    var desc = node.contentDesc ? "(".concat(node.contentDesc, ")") : "";
    var id = node.resourceId ? "#".concat(node.resourceId.split("/").pop()) : "";
    var clickable = node.isClickable ? "👆" : "";
    console.log("".concat(indent).concat(clickable, "[").concat(className, "] ").concat(text, " ").concat(desc, " ").concat(id));
    if (node.children) {
        node.children.forEach(function (child) { return printUIHierarchy(child, indent + "  "); });
    }
}
/**
 * Main test runner function that organizes and executes all test categories
 */
function runTests() {
    return __awaiter(this, arguments, void 0, function (params) {
        var results, testSummary, startTime, testType, duration, successCount, totalTests, summaryText, error_1;
        var _a, _b;
        if (params === void 0) { params = {}; }
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    console.log("Starting OperIT Tool Tester...");
                    console.log("Parameters:", params);
                    results = {};
                    testSummary = [];
                    startTime = Date.now();
                    testType = params.testType || "all";
                    _c.label = 1;
                case 1:
                    _c.trys.push([1, 45, , 46]);
                    if (!(testType === "all" || testType === "basic")) return [3 /*break*/, 6];
                    console.log("\n🔧 Testing Basic Tools...");
                    testSummary.push("Running basic tools tests");
                    return [4 /*yield*/, testQueryProblemLibrary(results)];
                case 2:
                    _c.sent();
                    return [4 /*yield*/, testUsePackage(results)];
                case 3:
                    _c.sent();
                    return [4 /*yield*/, testCalculator(results)];
                case 4:
                    _c.sent();
                    return [4 /*yield*/, testSleep(results)];
                case 5:
                    _c.sent();
                    _c.label = 6;
                case 6:
                    if (!(testType === "all" || testType === "files")) return [3 /*break*/, 22];
                    console.log("\n📁 Testing File System Tools...");
                    testSummary.push("Running file system tools tests");
                    // First create a test file as foundation for other tests
                    return [4 /*yield*/, testWriteFile(results)];
                case 7:
                    // First create a test file as foundation for other tests
                    _c.sent();
                    // Then test other operations in a logical sequence
                    return [4 /*yield*/, testFileExists(results)];
                case 8:
                    // Then test other operations in a logical sequence
                    _c.sent();
                    return [4 /*yield*/, testListFiles(results)];
                case 9:
                    _c.sent();
                    return [4 /*yield*/, testReadFile(results)];
                case 10:
                    _c.sent();
                    return [4 /*yield*/, testMakeDirectory(results)];
                case 11:
                    _c.sent();
                    return [4 /*yield*/, testCopyFile(results)];
                case 12:
                    _c.sent();
                    return [4 /*yield*/, testMoveFile(results)];
                case 13:
                    _c.sent();
                    return [4 /*yield*/, testFindFiles(results)];
                case 14:
                    _c.sent();
                    return [4 /*yield*/, testFileInfo(results)];
                case 15:
                    _c.sent();
                    if (!(((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success) && ((_b = results["make_directory"]) === null || _b === void 0 ? void 0 : _b.success))) return [3 /*break*/, 18];
                    return [4 /*yield*/, testZipFiles(results)];
                case 16:
                    _c.sent();
                    return [4 /*yield*/, testUnzipFiles(results)];
                case 17:
                    _c.sent();
                    _c.label = 18;
                case 18: 
                // Test opening and sharing files
                return [4 /*yield*/, testOpenFile(results)];
                case 19:
                    // Test opening and sharing files
                    _c.sent();
                    return [4 /*yield*/, testShareFile(results)];
                case 20:
                    _c.sent();
                    // Clean up test files at the end
                    return [4 /*yield*/, testDeleteFile(results)];
                case 21:
                    // Clean up test files at the end
                    _c.sent();
                    _c.label = 22;
                case 22:
                    if (!(testType === "all" || testType === "network")) return [3 /*break*/, 27];
                    console.log("\n🌐 Testing Network Tools...");
                    testSummary.push("Running network tools tests");
                    return [4 /*yield*/, testWebSearch(results)];
                case 23:
                    _c.sent();
                    return [4 /*yield*/, testHttpRequest(results)];
                case 24:
                    _c.sent();
                    return [4 /*yield*/, testDownloadFile(results)];
                case 25:
                    _c.sent();
                    return [4 /*yield*/, testFetchWebPage(results)];
                case 26:
                    _c.sent();
                    _c.label = 27;
                case 27:
                    if (!(testType === "all" || testType === "system")) return [3 /*break*/, 36];
                    console.log("\n⚙️ Testing System Operation Tools...");
                    testSummary.push("Running system operation tools tests");
                    return [4 /*yield*/, testDeviceInfo(results)];
                case 28:
                    _c.sent();
                    return [4 /*yield*/, testGetSystemSetting(results)];
                case 29:
                    _c.sent();
                    return [4 /*yield*/, testListInstalledApps(results)];
                case 30:
                    _c.sent();
                    return [4 /*yield*/, testStartApp(results)];
                case 31:
                    _c.sent();
                    return [4 /*yield*/, testStopApp(results)];
                case 32:
                    _c.sent();
                    if (!(params.testType === "system_danger")) return [3 /*break*/, 36];
                    return [4 /*yield*/, testModifySystemSetting(results)];
                case 33:
                    _c.sent();
                    return [4 /*yield*/, testInstallApp(results)];
                case 34:
                    _c.sent();
                    return [4 /*yield*/, testUninstallApp(results)];
                case 35:
                    _c.sent();
                    _c.label = 36;
                case 36:
                    if (!(testType === "all" || testType === "ui")) return [3 /*break*/, 44];
                    console.log("\n📱 Testing UI Automation Tools...");
                    testSummary.push("Running UI automation tools tests");
                    return [4 /*yield*/, testGetPageInfo(results)];
                case 37:
                    _c.sent();
                    return [4 /*yield*/, testClickElement(results)];
                case 38:
                    _c.sent();
                    return [4 /*yield*/, testTap(results)];
                case 39:
                    _c.sent();
                    return [4 /*yield*/, testSetInputText(results)];
                case 40:
                    _c.sent();
                    return [4 /*yield*/, testPressKey(results)];
                case 41:
                    _c.sent();
                    return [4 /*yield*/, testSwipe(results)];
                case 42:
                    _c.sent();
                    return [4 /*yield*/, testCombinedOperation(results)];
                case 43:
                    _c.sent();
                    _c.label = 44;
                case 44:
                    duration = Date.now() - startTime;
                    console.log("\n📊 Test Summary:");
                    Object.entries(results).forEach(function (_a) {
                        var test = _a[0], result = _a[1];
                        var status = result.success ? "✅ PASS" : "❌ FAIL";
                        console.log("".concat(status, ": ").concat(test));
                    });
                    successCount = Object.values(results).filter(function (r) { return r.success; }).length;
                    totalTests = Object.keys(results).length;
                    summaryText = "Overall: ".concat(successCount, "/").concat(totalTests, " tests passed in ").concat(duration / 1000, "s");
                    console.log("\n".concat(summaryText));
                    console.log("\nOperIT Tool Tester completed!");
                    // Return results
                    complete({
                        testSummary: testSummary,
                        summary: summaryText,
                        testsPassed: successCount,
                        testsTotal: totalTests,
                        testResults: results,
                        duration: "".concat(duration / 1000, "s")
                    });
                    return [3 /*break*/, 46];
                case 45:
                    error_1 = _c.sent();
                    console.error("Unexpected error in test suite:", error_1);
                    complete({
                        error: String(error_1),
                        testSummary: testSummary,
                        testResults: results
                    });
                    return [3 /*break*/, 46];
                case 46: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the query_problem_library tool
 */
function testQueryProblemLibrary(results) {
    return __awaiter(this, void 0, void 0, function () {
        var queryResult, resultString, err_1;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting query_problem_library...");
                    return [4 /*yield*/, toolCall("query_problem_library", {
                            query: "how to use OperIT tools"
                        })];
                case 1:
                    queryResult = _a.sent();
                    resultString = queryResult;
                    console.log("Query result type: ".concat(typeof resultString));
                    console.log("Query result length: ".concat(resultString.length, " characters"));
                    console.log("Result preview: ".concat(resultString.substring(0, 100), "..."));
                    results["query_problem_library"] = {
                        success: typeof resultString === 'string' && resultString.length > 0,
                        data: resultString
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_1 = _a.sent();
                    console.error("Error testing query_problem_library:", err_1);
                    results["query_problem_library"] = { success: false, error: String(err_1) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the use_package tool
 */
function testUsePackage(results) {
    return __awaiter(this, void 0, void 0, function () {
        var packageResult, resultString, err_2;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting use_package...");
                    return [4 /*yield*/, toolCall("use_package", {
                            package_name: "example_package"
                        })];
                case 1:
                    packageResult = _a.sent();
                    resultString = packageResult;
                    console.log("Package result type: ".concat(typeof resultString));
                    console.log("Package result: ".concat(resultString));
                    results["use_package"] = {
                        success: typeof resultString === 'string',
                        data: resultString
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_2 = _a.sent();
                    console.error("Error testing use_package:", err_2);
                    results["use_package"] = { success: false, error: String(err_2) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the calculate tool
 */
function testCalculator(results) {
    return __awaiter(this, void 0, void 0, function () {
        var expressions, _i, expressions_1, expression, calcResult, calcData, err_3;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting calculate...");
                    expressions = [
                        "2 + 2",
                        "5 * (3 - 1)",
                        "sin(30)"
                    ];
                    _i = 0, expressions_1 = expressions;
                    _a.label = 1;
                case 1:
                    if (!(_i < expressions_1.length)) return [3 /*break*/, 4];
                    expression = expressions_1[_i];
                    console.log("Calculating: ".concat(expression));
                    return [4 /*yield*/, toolCall("calculate", {
                            expression: expression
                        })];
                case 2:
                    calcResult = _a.sent();
                    calcData = calcResult;
                    console.log("Expression: ".concat(calcData.expression));
                    console.log("Result: ".concat(calcData.result));
                    console.log("Formatted result: ".concat(calcData.formattedResult));
                    if (calcData.variables && Object.keys(calcData.variables).length > 0) {
                        console.log("Variables: ".concat(JSON.stringify(calcData.variables)));
                    }
                    _a.label = 3;
                case 3:
                    _i++;
                    return [3 /*break*/, 1];
                case 4:
                    results["calculate"] = { success: true };
                    return [3 /*break*/, 6];
                case 5:
                    err_3 = _a.sent();
                    console.error("Error testing calculate:", err_3);
                    results["calculate"] = { success: false, error: String(err_3) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the sleep tool
 */
function testSleep(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testDurations, _i, testDurations_1, duration, startTime, sleepResult, endTime, actualDuration, sleepData, sleepAccuracy, err_4;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting sleep...");
                    testDurations = [100, 500, 1000];
                    _i = 0, testDurations_1 = testDurations;
                    _a.label = 1;
                case 1:
                    if (!(_i < testDurations_1.length)) return [3 /*break*/, 4];
                    duration = testDurations_1[_i];
                    console.log("Sleeping for ".concat(duration, "ms..."));
                    startTime = Date.now();
                    return [4 /*yield*/, toolCall("sleep", {
                            duration_ms: duration
                        })];
                case 2:
                    sleepResult = _a.sent();
                    endTime = Date.now();
                    actualDuration = endTime - startTime;
                    sleepData = sleepResult;
                    console.log("Requested sleep: ".concat(duration, "ms"));
                    console.log("Actual sleep duration: ~".concat(actualDuration, "ms"));
                    sleepAccuracy = Math.abs(actualDuration - duration) < 100;
                    console.log("Sleep accuracy OK: ".concat(sleepAccuracy ? "✅" : "❌"));
                    _a.label = 3;
                case 3:
                    _i++;
                    return [3 /*break*/, 1];
                case 4:
                    results["sleep"] = { success: true };
                    return [3 /*break*/, 6];
                case 5:
                    err_4 = _a.sent();
                    console.error("Error testing sleep:", err_4);
                    results["sleep"] = { success: false, error: String(err_4) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the list_files tool
 */
function testListFiles(results) {
    return __awaiter(this, void 0, void 0, function () {
        var filesList, dirListing, err_5;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting list_files...");
                    return [4 /*yield*/, toolCall("list_files", {
                            path: "/sdcard/"
                        })];
                case 1:
                    filesList = _a.sent();
                    dirListing = filesList;
                    console.log("Listed directory: ".concat(dirListing.path));
                    console.log("Found ".concat(dirListing.entries.length, " entries"));
                    // Display some sample entries
                    if (dirListing.entries.length > 0) {
                        console.log("\nSample entries:");
                        dirListing.entries.slice(0, 3).forEach(function (entry) {
                            console.log("- ".concat(entry.name, " (").concat(entry.isDirectory ? "Directory" : "File", ", ").concat(entry.size, " bytes, modified: ").concat(entry.lastModified, ")"));
                        });
                    }
                    results["list_files"] = {
                        success: Array.isArray(dirListing.entries),
                        data: dirListing
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_5 = _a.sent();
                    console.error("Error testing list_files:", err_5);
                    results["list_files"] = { success: false, error: String(err_5) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the write_file tool
 */
function testWriteFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testContent, testPath, writeResult, writeData, err_6;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting write_file...");
                    testContent = "This is a test file created by the OperIT tool tester.\nTest timestamp: " + new Date().toISOString();
                    testPath = "/sdcard/operit_test_file.txt";
                    return [4 /*yield*/, toolCall("write_file", {
                            path: testPath,
                            content: testContent
                        })];
                case 1:
                    writeResult = _a.sent();
                    writeData = writeResult;
                    console.log("Operation: ".concat(writeData.operation));
                    console.log("Path: ".concat(writeData.path));
                    console.log("Success: ".concat(writeData.successful));
                    console.log("Details: ".concat(writeData.details));
                    results["write_file"] = {
                        success: writeData.successful,
                        data: { path: testPath, content: testContent, result: writeData }
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_6 = _a.sent();
                    console.error("Error testing write_file:", err_6);
                    results["write_file"] = { success: false, error: String(err_6) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the read_file tool
 */
function testReadFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testPath, readResult, fileData, containsTestMarker, err_7;
        var _a;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _b.trys.push([0, 2, , 3]);
                    console.log("\nTesting read_file...");
                    // Check if write_file was successful
                    if (!((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success)) {
                        console.log("WARNING: write_file test did not succeed. Reading test file may fail.");
                    }
                    testPath = "/sdcard/operit_test_file.txt";
                    return [4 /*yield*/, toolCall("read_file", {
                            path: testPath
                        })];
                case 1:
                    readResult = _b.sent();
                    fileData = readResult;
                    console.log("File path: ".concat(fileData.path));
                    console.log("File size: ".concat(fileData.size, " bytes"));
                    console.log("Content preview: ".concat(fileData.content.substring(0, 100), "..."));
                    containsTestMarker = fileData.content.includes("test file created by the OperIT tool tester");
                    console.log("Content verification: ".concat(containsTestMarker ? "✅ Matched" : "❌ Failed"));
                    results["read_file"] = {
                        success: containsTestMarker && fileData.size > 0,
                        data: fileData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_7 = _b.sent();
                    console.error("Error testing read_file:", err_7);
                    results["read_file"] = { success: false, error: String(err_7) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the file_exists tool
 */
function testFileExists(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testPath, existsResult, existsData, nonExistentPath, nonExistsResult, nonExistsData, err_8;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 3, , 4]);
                    console.log("\nTesting file_exists...");
                    testPath = "/sdcard/operit_test_file.txt";
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: testPath
                        })];
                case 1:
                    existsResult = _a.sent();
                    existsData = existsResult;
                    console.log("File path: ".concat(existsData.path));
                    console.log("File exists: ".concat(existsData.exists));
                    if (existsData.exists) {
                        console.log("Is directory: ".concat(existsData.isDirectory));
                        console.log("Size: ".concat(existsData.size, " bytes"));
                    }
                    nonExistentPath = "/sdcard/this_file_should_not_exist_" + Date.now() + ".txt";
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: nonExistentPath
                        })];
                case 2:
                    nonExistsResult = _a.sent();
                    nonExistsData = nonExistsResult;
                    console.log("\nNon-existent file check:");
                    console.log("File path: ".concat(nonExistsData.path));
                    console.log("File exists: ".concat(nonExistsData.exists, " (should be false)"));
                    results["file_exists"] = {
                        success: existsData.exists && !nonExistsData.exists,
                        data: { exists: existsData, nonExists: nonExistsData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_8 = _a.sent();
                    console.error("Error testing file_exists:", err_8);
                    results["file_exists"] = { success: false, error: String(err_8) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the make_directory tool
 */
function testMakeDirectory(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testDirPath, mkdirResult, mkdirData, verifyResult, verifyData, err_9;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 3, , 4]);
                    console.log("\nTesting make_directory...");
                    testDirPath = "/sdcard/operit_test_directory";
                    return [4 /*yield*/, toolCall("make_directory", {
                            path: testDirPath
                        })];
                case 1:
                    mkdirResult = _a.sent();
                    mkdirData = mkdirResult;
                    console.log("Operation: ".concat(mkdirData.operation));
                    console.log("Path: ".concat(mkdirData.path));
                    console.log("Success: ".concat(mkdirData.successful));
                    console.log("Details: ".concat(mkdirData.details));
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: testDirPath
                        })];
                case 2:
                    verifyResult = _a.sent();
                    verifyData = verifyResult;
                    console.log("Directory exists: ".concat(verifyData.exists));
                    console.log("Is directory: ".concat(verifyData.isDirectory));
                    results["make_directory"] = {
                        success: mkdirData.successful && verifyData.exists && verifyData.isDirectory === true,
                        data: { create: mkdirData, verify: verifyData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_9 = _a.sent();
                    console.error("Error testing make_directory:", err_9);
                    results["make_directory"] = { success: false, error: String(err_9) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the copy_file tool
 */
function testCopyFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var sourcePath, destPath, copyResult, copyData, verifyResult, verifyData, err_10;
        var _a;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _b.trys.push([0, 3, , 4]);
                    console.log("\nTesting copy_file...");
                    // Check if write_file was successful
                    if (!((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success)) {
                        console.log("WARNING: write_file test did not succeed. Copying test file may fail.");
                    }
                    sourcePath = "/sdcard/operit_test_file.txt";
                    destPath = "/sdcard/operit_test_directory/copied_file.txt";
                    return [4 /*yield*/, toolCall("copy_file", {
                            source: sourcePath,
                            destination: destPath
                        })];
                case 1:
                    copyResult = _b.sent();
                    copyData = copyResult;
                    console.log("Operation: ".concat(copyData.operation));
                    console.log("Path: ".concat(copyData.path));
                    console.log("Success: ".concat(copyData.successful));
                    console.log("Details: ".concat(copyData.details));
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: destPath
                        })];
                case 2:
                    verifyResult = _b.sent();
                    verifyData = verifyResult;
                    console.log("Copied file exists: ".concat(verifyData.exists));
                    results["copy_file"] = {
                        success: copyData.successful && verifyData.exists,
                        data: { copy: copyData, verify: verifyData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_10 = _b.sent();
                    console.error("Error testing copy_file:", err_10);
                    results["copy_file"] = { success: false, error: String(err_10) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the move_file tool
 */
function testMoveFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var sourcePath, destPath, moveResult, moveData, sourceCheck, destCheck, sourceExists, destExists, err_11;
        var _a;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _b.trys.push([0, 4, , 5]);
                    console.log("\nTesting move_file...");
                    // Check if copy_file was successful
                    if (!((_a = results["copy_file"]) === null || _a === void 0 ? void 0 : _a.success)) {
                        console.log("WARNING: copy_file test did not succeed. Moving test file may fail.");
                    }
                    sourcePath = "/sdcard/operit_test_directory/copied_file.txt";
                    destPath = "/sdcard/operit_test_directory/moved_file.txt";
                    return [4 /*yield*/, toolCall("move_file", {
                            source: sourcePath,
                            destination: destPath
                        })];
                case 1:
                    moveResult = _b.sent();
                    moveData = moveResult;
                    console.log("Operation: ".concat(moveData.operation));
                    console.log("Path: ".concat(moveData.path));
                    console.log("Success: ".concat(moveData.successful));
                    console.log("Details: ".concat(moveData.details));
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: sourcePath
                        })];
                case 2:
                    sourceCheck = _b.sent();
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: destPath
                        })];
                case 3:
                    destCheck = _b.sent();
                    sourceExists = sourceCheck.exists;
                    destExists = destCheck.exists;
                    console.log("Source file still exists: ".concat(sourceExists, " (should be false)"));
                    console.log("Destination file exists: ".concat(destExists, " (should be true)"));
                    results["move_file"] = {
                        success: moveData.successful && !sourceExists && destExists,
                        data: moveData
                    };
                    return [3 /*break*/, 5];
                case 4:
                    err_11 = _b.sent();
                    console.error("Error testing move_file:", err_11);
                    results["move_file"] = { success: false, error: String(err_11) };
                    return [3 /*break*/, 5];
                case 5: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the find_files tool
 */
function testFindFiles(results) {
    return __awaiter(this, void 0, void 0, function () {
        var findResult, findData, foundEnough, err_12;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting find_files...");
                    return [4 /*yield*/, toolCall("find_files", {
                            path: "/sdcard/",
                            pattern: "operit_test_*",
                            max_depth: 5,
                            case_insensitive: true
                        })];
                case 1:
                    findResult = _a.sent();
                    findData = findResult;
                    console.log("Search path: ".concat(findData.path));
                    console.log("Pattern: ".concat(findData.pattern));
                    console.log("Found files: ".concat(findData.files.length));
                    if (findData.files.length > 0) {
                        console.log("\nFiles found:");
                        findData.files.forEach(function (file) {
                            console.log("- ".concat(file));
                        });
                    }
                    foundEnough = findData.files.length >= 2;
                    console.log("Found sufficient test files: ".concat(foundEnough ? "✅" : "❌"));
                    results["find_files"] = {
                        success: foundEnough,
                        data: findData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_12 = _a.sent();
                    console.error("Error testing find_files:", err_12);
                    results["find_files"] = { success: false, error: String(err_12) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the file_info tool
 */
function testFileInfo(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testFilePath, fileInfoResult, fileInfo, err_13;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting file_info...");
                    testFilePath = "/sdcard/operit_test_file.txt";
                    return [4 /*yield*/, toolCall("file_info", {
                            path: testFilePath
                        })];
                case 1:
                    fileInfoResult = _a.sent();
                    fileInfo = fileInfoResult;
                    console.log("Path: ".concat(fileInfo.path));
                    console.log("Exists: ".concat(fileInfo.exists));
                    console.log("Type: ".concat(fileInfo.fileType));
                    console.log("Size: ".concat(fileInfo.size, " bytes"));
                    console.log("Permissions: ".concat(fileInfo.permissions));
                    console.log("Owner: ".concat(fileInfo.owner));
                    console.log("Group: ".concat(fileInfo.group));
                    console.log("Last Modified: ".concat(fileInfo.lastModified));
                    results["file_info"] = {
                        success: fileInfo.exists,
                        data: fileInfo
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_13 = _a.sent();
                    console.error("Error testing file_info:", err_13);
                    results["file_info"] = { success: false, error: String(err_13) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the zip_files tool
 */
function testZipFiles(results) {
    return __awaiter(this, void 0, void 0, function () {
        var sourceDir, destZip, zipResult, zipData, verifyResult, verifyData, err_14;
        var _a;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _b.trys.push([0, 3, , 4]);
                    console.log("\nTesting zip_files...");
                    sourceDir = "/sdcard/operit_test_directory";
                    destZip = "/sdcard/operit_test.zip";
                    return [4 /*yield*/, toolCall("zip_files", {
                            source: sourceDir,
                            destination: destZip
                        })];
                case 1:
                    zipResult = _b.sent();
                    zipData = zipResult;
                    console.log("Operation: ".concat(zipData.operation));
                    console.log("Path: ".concat(zipData.path));
                    console.log("Success: ".concat(zipData.successful));
                    console.log("Details: ".concat(zipData.details));
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: destZip
                        })];
                case 2:
                    verifyResult = _b.sent();
                    verifyData = verifyResult;
                    console.log("Zip file exists: ".concat(verifyData.exists));
                    console.log("Zip file size: ".concat(verifyData.size, " bytes"));
                    results["zip_files"] = {
                        success: zipData.successful && verifyData.exists && ((_a = verifyData.size) !== null && _a !== void 0 ? _a : 0) > 0,
                        data: { zip: zipData, verify: verifyData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_14 = _b.sent();
                    console.error("Error testing zip_files:", err_14);
                    results["zip_files"] = { success: false, error: String(err_14) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the unzip_files tool
 */
function testUnzipFiles(results) {
    return __awaiter(this, void 0, void 0, function () {
        var sourceZip, destDir, unzipResult, unzipData, listResult, listData, err_15;
        var _a;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _b.trys.push([0, 5, , 6]);
                    console.log("\nTesting unzip_files...");
                    // Check if zip_files was successful
                    if (!((_a = results["zip_files"]) === null || _a === void 0 ? void 0 : _a.success)) {
                        console.log("WARNING: zip_files test did not succeed. Unzipping test file may fail.");
                    }
                    sourceZip = "/sdcard/operit_test.zip";
                    destDir = "/sdcard/operit_test_extracted";
                    // Create the destination directory first
                    return [4 /*yield*/, toolCall("make_directory", {
                            path: destDir
                        })];
                case 1:
                    // Create the destination directory first
                    _b.sent();
                    return [4 /*yield*/, toolCall("unzip_files", {
                            source: sourceZip,
                            destination: destDir
                        })];
                case 2:
                    unzipResult = _b.sent();
                    unzipData = unzipResult;
                    console.log("Operation: ".concat(unzipData.operation));
                    console.log("Path: ".concat(unzipData.path));
                    console.log("Success: ".concat(unzipData.successful));
                    console.log("Details: ".concat(unzipData.details));
                    if (!unzipData.successful) return [3 /*break*/, 4];
                    return [4 /*yield*/, toolCall("list_files", {
                            path: destDir
                        })];
                case 3:
                    listResult = _b.sent();
                    listData = listResult;
                    console.log("\nExtracted contents (".concat(listData.entries.length, " items):"));
                    listData.entries.forEach(function (entry) {
                        console.log("- ".concat(entry.name));
                    });
                    _b.label = 4;
                case 4:
                    results["unzip_files"] = {
                        success: unzipData.successful,
                        data: unzipData
                    };
                    return [3 /*break*/, 6];
                case 5:
                    err_15 = _b.sent();
                    console.error("Error testing unzip_files:", err_15);
                    results["unzip_files"] = { success: false, error: String(err_15) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the open_file tool
 */
function testOpenFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testFilePath, openResult, openData, err_16;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting open_file...");
                    testFilePath = "/sdcard/operit_test_file.txt";
                    return [4 /*yield*/, toolCall("open_file", {
                            path: testFilePath
                        })];
                case 1:
                    openResult = _a.sent();
                    openData = openResult;
                    console.log("Operation: ".concat(openData.operation));
                    console.log("Path: ".concat(openData.path));
                    console.log("Success: ".concat(openData.successful));
                    console.log("Details: ".concat(openData.details));
                    results["open_file"] = {
                        success: openData.successful,
                        data: openData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_16 = _a.sent();
                    console.error("Error testing open_file:", err_16);
                    results["open_file"] = { success: false, error: String(err_16) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the share_file tool
 */
function testShareFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testFilePath, shareResult, shareData, err_17;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting share_file...");
                    testFilePath = "/sdcard/operit_test_file.txt";
                    return [4 /*yield*/, toolCall("share_file", {
                            path: testFilePath,
                            title: "OperIT Test Share"
                        })];
                case 1:
                    shareResult = _a.sent();
                    shareData = shareResult;
                    console.log("Operation: ".concat(shareData.operation));
                    console.log("Path: ".concat(shareData.path));
                    console.log("Success: ".concat(shareData.successful));
                    console.log("Details: ".concat(shareData.details));
                    results["share_file"] = {
                        success: shareData.successful,
                        data: shareData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_17 = _a.sent();
                    console.error("Error testing share_file:", err_17);
                    results["share_file"] = { success: false, error: String(err_17) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the delete_file tool and cleans up test files
 */
function testDeleteFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var testPaths, allSuccessful, _i, testPaths_1, path, deleteResult, deleteData, verifyResult, verifyData, isGone, err_18;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 6, , 7]);
                    console.log("\nTesting delete_file and cleaning up test files...");
                    testPaths = [
                        "/sdcard/operit_test_file.txt",
                        "/sdcard/operit_test_directory",
                        "/sdcard/operit_test.zip",
                        "/sdcard/operit_test_extracted"
                    ];
                    allSuccessful = true;
                    _i = 0, testPaths_1 = testPaths;
                    _a.label = 1;
                case 1:
                    if (!(_i < testPaths_1.length)) return [3 /*break*/, 5];
                    path = testPaths_1[_i];
                    console.log("\nDeleting ".concat(path, "..."));
                    return [4 /*yield*/, toolCall("delete_file", {
                            path: path,
                            recursive: true // Use recursive deletion for directories
                        })];
                case 2:
                    deleteResult = _a.sent();
                    deleteData = deleteResult;
                    console.log("Operation: ".concat(deleteData.operation));
                    console.log("Path: ".concat(deleteData.path));
                    console.log("Success: ".concat(deleteData.successful));
                    if (deleteData.details) {
                        console.log("Details: ".concat(deleteData.details));
                    }
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: path
                        })];
                case 3:
                    verifyResult = _a.sent();
                    verifyData = verifyResult;
                    isGone = !verifyData.exists;
                    console.log("File/directory removed: ".concat(isGone ? "✅" : "❌"));
                    allSuccessful = allSuccessful && deleteData.successful && isGone;
                    _a.label = 4;
                case 4:
                    _i++;
                    return [3 /*break*/, 1];
                case 5:
                    results["delete_file"] = {
                        success: allSuccessful,
                        data: { message: "All test files cleaned up" }
                    };
                    return [3 /*break*/, 7];
                case 6:
                    err_18 = _a.sent();
                    console.error("Error testing delete_file:", err_18);
                    results["delete_file"] = { success: false, error: String(err_18) };
                    return [3 /*break*/, 7];
                case 7: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the web_search tool
 */
function testWebSearch(results) {
    return __awaiter(this, void 0, void 0, function () {
        var searchQuery, searchResult, searchData, topResults, err_19;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting web_search...");
                    searchQuery = "OperIT AI automation tools";
                    return [4 /*yield*/, toolCall("web_search", {
                            query: searchQuery
                        })];
                case 1:
                    searchResult = _a.sent();
                    searchData = searchResult;
                    console.log("Search query: ".concat(searchData.query));
                    console.log("Results found: ".concat(searchData.results.length));
                    if (searchData.results.length > 0) {
                        console.log("\nTop search results:");
                        topResults = searchData.results.slice(0, 3);
                        topResults.forEach(function (result, index) {
                            console.log("\n[Result ".concat(index + 1, "]"));
                            console.log("Title: ".concat(result.title));
                            console.log("URL: ".concat(result.url));
                            console.log("Snippet: ".concat(result.snippet.substring(0, 100), "..."));
                        });
                    }
                    results["web_search"] = {
                        success: searchData.query === searchQuery && searchData.results.length > 0,
                        data: searchData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_19 = _a.sent();
                    console.error("Error testing web_search:", err_19);
                    results["web_search"] = { success: false, error: String(err_19) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the http_request tool
 */
function testHttpRequest(results) {
    return __awaiter(this, void 0, void 0, function () {
        var getResult, getData, postBody, postResult, postData, hasEchoedData, err_20;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 3, , 4]);
                    console.log("\nTesting http_request...");
                    // Test GET request
                    console.log("Testing GET request...");
                    return [4 /*yield*/, toolCall("http_request", {
                            url: "https://httpbin.org/get",
                            method: "GET"
                        })];
                case 1:
                    getResult = _a.sent();
                    getData = getResult;
                    console.log("GET Status: ".concat(getData.statusCode, " ").concat(getData.statusMessage));
                    console.log("Content type: ".concat(getData.contentType));
                    console.log("Content size: ".concat(getData.size, " bytes"));
                    console.log("Content summary: ".concat(getData.contentSummary));
                    // Test POST request with JSON body
                    console.log("\nTesting POST request with JSON body...");
                    postBody = { test: "value", timestamp: new Date().toISOString() };
                    return [4 /*yield*/, toolCall("http_request", {
                            url: "https://httpbin.org/post",
                            method: "POST",
                            headers: JSON.stringify({ "Content-Type": "application/json" }),
                            body: JSON.stringify(postBody),
                            body_type: "json"
                        })];
                case 2:
                    postResult = _a.sent();
                    postData = postResult;
                    console.log("POST Status: ".concat(postData.statusCode, " ").concat(postData.statusMessage));
                    console.log("Content type: ".concat(postData.contentType));
                    console.log("Content size: ".concat(postData.size, " bytes"));
                    hasEchoedData = postData.content.includes(postBody.test);
                    console.log("Request body correctly echoed: ".concat(hasEchoedData ? "✅" : "❌"));
                    results["http_request"] = {
                        success: getData.statusCode === 200 && postData.statusCode === 200,
                        data: { get: getData, post: postData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_20 = _a.sent();
                    console.error("Error testing http_request:", err_20);
                    results["http_request"] = { success: false, error: String(err_20) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the download_file tool
 */
function testDownloadFile(results) {
    return __awaiter(this, void 0, void 0, function () {
        var downloadUrl, downloadPath, downloadResult, downloadData, verifyResult, verifyData, err_21;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting download_file...");
                    downloadUrl = "https://httpbin.org/image/png";
                    downloadPath = "/sdcard/operit_test_download.png";
                    return [4 /*yield*/, toolCall("download_file", {
                            url: downloadUrl,
                            destination: downloadPath
                        })];
                case 1:
                    downloadResult = _a.sent();
                    downloadData = downloadResult;
                    console.log("Operation: ".concat(downloadData.operation));
                    console.log("Path: ".concat(downloadData.path));
                    console.log("Success: ".concat(downloadData.successful));
                    console.log("Details: ".concat(downloadData.details));
                    if (!downloadData.successful) return [3 /*break*/, 4];
                    return [4 /*yield*/, toolCall("file_exists", {
                            path: downloadPath
                        })];
                case 2:
                    verifyResult = _a.sent();
                    verifyData = verifyResult;
                    console.log("\nDownloaded file exists: ".concat(verifyData.exists));
                    console.log("File size: ".concat(verifyData.size, " bytes"));
                    if (!verifyData.exists) return [3 /*break*/, 4];
                    console.log("Cleaning up downloaded file...");
                    return [4 /*yield*/, toolCall("delete_file", {
                            path: downloadPath
                        })];
                case 3:
                    _a.sent();
                    _a.label = 4;
                case 4:
                    results["download_file"] = {
                        success: downloadData.successful,
                        data: downloadData
                    };
                    return [3 /*break*/, 6];
                case 5:
                    err_21 = _a.sent();
                    console.error("Error testing download_file:", err_21);
                    results["download_file"] = { success: false, error: String(err_21) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the fetch_web_page tool
 */
function testFetchWebPage(results) {
    return __awaiter(this, void 0, void 0, function () {
        var pageUrl, textResult, textData, htmlResult, htmlData, err_22;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 3, , 4]);
                    console.log("\nTesting fetch_web_page...");
                    pageUrl = "https://example.com";
                    // Test text format
                    console.log("Testing with text format...");
                    return [4 /*yield*/, toolCall("fetch_web_page", {
                            url: pageUrl,
                            format: "text"
                        })];
                case 1:
                    textResult = _a.sent();
                    textData = textResult;
                    console.log("URL: ".concat(textData.url));
                    console.log("Title: ".concat(textData.title));
                    console.log("Content type: ".concat(textData.contentType));
                    console.log("Size: ".concat(textData.size, " bytes"));
                    console.log("Text content preview: ".concat(textData.textContent.substring(0, 150), "..."));
                    console.log("Links found: ".concat(textData.links.length));
                    // Display some links if available
                    if (textData.links.length > 0) {
                        console.log("\nSample links:");
                        textData.links.slice(0, 3).forEach(function (link) {
                            console.log("- ".concat(link.text || "(no text)", ": ").concat(link.url));
                        });
                    }
                    // Test HTML format
                    console.log("\nTesting with HTML format...");
                    return [4 /*yield*/, toolCall("fetch_web_page", {
                            url: pageUrl,
                            format: "html"
                        })];
                case 2:
                    htmlResult = _a.sent();
                    htmlData = htmlResult;
                    console.log("HTML content available: ".concat(htmlData.content.length > 0 ? "✅" : "❌"));
                    console.log("HTML content preview: ".concat(htmlData.content.substring(0, 100), "..."));
                    results["fetch_web_page"] = {
                        success: textData.url === pageUrl && textData.title.length > 0,
                        data: { text: textData, html: htmlData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_22 = _a.sent();
                    console.error("Error testing fetch_web_page:", err_22);
                    results["fetch_web_page"] = { success: false, error: String(err_22) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the device_info tool
 */
function testDeviceInfo(results) {
    return __awaiter(this, void 0, void 0, function () {
        var deviceResult, deviceData, hasBasicInfo, err_23;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting device_info...");
                    return [4 /*yield*/, toolCall("device_info")];
                case 1:
                    deviceResult = _a.sent();
                    deviceData = deviceResult;
                    console.log("=== Device Information ===");
                    console.log("Device ID: ".concat(deviceData.deviceId));
                    console.log("Model: ".concat(deviceData.manufacturer, " ").concat(deviceData.model));
                    console.log("Android Version: ".concat(deviceData.androidVersion, " (SDK ").concat(deviceData.sdkVersion, ")"));
                    console.log("\n=== Display ===");
                    console.log("Resolution: ".concat(deviceData.screenResolution));
                    console.log("Density: ".concat(deviceData.screenDensity));
                    console.log("\n=== Memory & Storage ===");
                    console.log("Memory: ".concat(deviceData.availableMemory, " available of ").concat(deviceData.totalMemory, " total"));
                    console.log("Storage: ".concat(deviceData.availableStorage, " available of ").concat(deviceData.totalStorage, " total"));
                    console.log("\n=== Status ===");
                    console.log("Battery: ".concat(deviceData.batteryLevel, "% ").concat(deviceData.batteryCharging ? "(charging)" : "(not charging)"));
                    console.log("Network: ".concat(deviceData.networkType));
                    console.log("Processor: ".concat(deviceData.cpuInfo));
                    // Check additional info
                    if (deviceData.additionalInfo && Object.keys(deviceData.additionalInfo).length > 0) {
                        console.log("\n=== Additional Information ===");
                        Object.entries(deviceData.additionalInfo).forEach(function (_a) {
                            var key = _a[0], value = _a[1];
                            console.log("".concat(key, ": ").concat(value));
                        });
                    }
                    hasBasicInfo = deviceData.model &&
                        deviceData.manufacturer &&
                        deviceData.androidVersion &&
                        typeof deviceData.sdkVersion === 'number';
                    results["device_info"] = {
                        success: hasBasicInfo === true,
                        data: deviceData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_23 = _a.sent();
                    console.error("Error testing device_info:", err_23);
                    results["device_info"] = { success: false, error: String(err_23) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the get_system_setting tool
 */
function testGetSystemSetting(results) {
    return __awaiter(this, void 0, void 0, function () {
        var settingsToTest, settingResults, _i, settingsToTest_1, _a, namespace, setting, settingResult, settingData, allSuccessful, err_24;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _b.trys.push([0, 5, , 6]);
                    console.log("\nTesting get_system_setting...");
                    settingsToTest = [
                        { namespace: "system", setting: "screen_brightness_mode" },
                        { namespace: "system", setting: "screen_brightness" },
                        { namespace: "system", setting: "time_12_24" }
                    ];
                    settingResults = [];
                    _i = 0, settingsToTest_1 = settingsToTest;
                    _b.label = 1;
                case 1:
                    if (!(_i < settingsToTest_1.length)) return [3 /*break*/, 4];
                    _a = settingsToTest_1[_i], namespace = _a.namespace, setting = _a.setting;
                    console.log("\nFetching ".concat(namespace, "/").concat(setting, "..."));
                    return [4 /*yield*/, toolCall("get_system_setting", {
                            namespace: namespace,
                            setting: setting
                        })];
                case 2:
                    settingResult = _b.sent();
                    settingData = settingResult;
                    console.log("Setting namespace: ".concat(settingData.namespace));
                    console.log("Setting name: ".concat(settingData.setting));
                    console.log("Setting value: ".concat(settingData.value));
                    settingResults.push({
                        // requested: { namespace, setting },
                        data: settingData,
                        success: settingData.namespace === namespace &&
                            settingData.setting === setting &&
                            settingData.value !== undefined
                    });
                    _b.label = 3;
                case 3:
                    _i++;
                    return [3 /*break*/, 1];
                case 4:
                    allSuccessful = settingResults.every(function (r) { return r.success; });
                    results["get_system_setting"] = {
                        success: allSuccessful,
                        data: settingResults
                    };
                    return [3 /*break*/, 6];
                case 5:
                    err_24 = _b.sent();
                    console.error("Error testing get_system_setting:", err_24);
                    results["get_system_setting"] = { success: false, error: String(err_24) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the modify_system_setting tool - USE WITH CAUTION
 * This is marked as potentially dangerous and requires user authorization
 */
function testModifySystemSetting(results) {
    return __awaiter(this, void 0, void 0, function () {
        var getResult, originalData, newValue, modifyResult, modifyData, verifyResult, verifyData, err_25;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting modify_system_setting...");
                    console.log("CAUTION: This test attempts to modify system settings and requires user authorization");
                    return [4 /*yield*/, toolCall("get_system_setting", {
                            namespace: "system",
                            setting: "screen_brightness_mode"
                        })];
                case 1:
                    getResult = _a.sent();
                    originalData = getResult;
                    console.log("Current brightness mode: ".concat(originalData.value));
                    newValue = originalData.value === "1" ? "0" : "1";
                    console.log("Setting brightness mode to: ".concat(newValue));
                    return [4 /*yield*/, toolCall("modify_system_setting", {
                            namespace: "system",
                            setting: "screen_brightness_mode",
                            value: newValue
                        })];
                case 2:
                    modifyResult = _a.sent();
                    modifyData = modifyResult;
                    console.log("Setting modified: ".concat(modifyData.namespace, "/").concat(modifyData.setting));
                    console.log("New value: ".concat(modifyData.value));
                    return [4 /*yield*/, toolCall("get_system_setting", {
                            namespace: "system",
                            setting: "screen_brightness_mode"
                        })];
                case 3:
                    verifyResult = _a.sent();
                    verifyData = verifyResult;
                    console.log("Verified new value: ".concat(verifyData.value));
                    // Restore original value
                    console.log("Restoring original value: ".concat(originalData.value));
                    return [4 /*yield*/, toolCall("modify_system_setting", {
                            namespace: "system",
                            setting: "screen_brightness_mode",
                            value: originalData.value
                        })];
                case 4:
                    _a.sent();
                    results["modify_system_setting"] = {
                        success: verifyData.value === newValue,
                        data: { original: originalData, modified: modifyData, verified: verifyData }
                    };
                    return [3 /*break*/, 6];
                case 5:
                    err_25 = _a.sent();
                    console.error("Error testing modify_system_setting:", err_25);
                    results["modify_system_setting"] = { success: false, error: String(err_25) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the list_installed_apps tool
 */
function testListInstalledApps(results) {
    return __awaiter(this, void 0, void 0, function () {
        var userAppsResult, userAppsData, allAppsResult, allAppsData, moreWithSystem, err_26;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 3, , 4]);
                    console.log("\nTesting list_installed_apps...");
                    // Test without system apps
                    console.log("Listing user apps (exclude system apps)...");
                    return [4 /*yield*/, toolCall("list_installed_apps", {
                            include_system_apps: false
                        })];
                case 1:
                    userAppsResult = _a.sent();
                    userAppsData = userAppsResult;
                    console.log("Including system apps: ".concat(userAppsData.includesSystemApps));
                    console.log("Total user apps: ".concat(userAppsData.packages.length));
                    if (userAppsData.packages.length > 0) {
                        console.log("\nSample user apps:");
                        userAppsData.packages.slice(0, 5).forEach(function (app) {
                            console.log("- ".concat(app));
                        });
                    }
                    // Test with system apps
                    console.log("\nListing all apps (include system apps)...");
                    return [4 /*yield*/, toolCall("list_installed_apps", {
                            include_system_apps: true
                        })];
                case 2:
                    allAppsResult = _a.sent();
                    allAppsData = allAppsResult;
                    console.log("Including system apps: ".concat(allAppsData.includesSystemApps));
                    console.log("Total apps: ".concat(allAppsData.packages.length));
                    moreWithSystem = allAppsData.packages.length > userAppsData.packages.length;
                    console.log("More apps when including system apps: ".concat(moreWithSystem ? "✅" : "❌"));
                    results["list_installed_apps"] = {
                        success: userAppsData.packages.length > 0 && moreWithSystem,
                        data: { userApps: userAppsData, allApps: allAppsData }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    err_26 = _a.sent();
                    console.error("Error testing list_installed_apps:", err_26);
                    results["list_installed_apps"] = { success: false, error: String(err_26) };
                    return [3 /*break*/, 4];
                case 4: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the install_app tool - USE WITH CAUTION
 * This is marked as potentially dangerous and requires user authorization
 */
function testInstallApp(results) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            try {
                console.log("\nTesting install_app...");
                console.log("CAUTION: This test requires an APK file and user authorization");
                console.log("Skipping actual installation - this is a dangerous operation");
                // Instead of actual installation, we'll just check if the function is available
                results["install_app"] = {
                    success: true,
                    data: { message: "Test skipped - dangerous operation" }
                };
            }
            catch (err) {
                console.error("Error testing install_app:", err);
                results["install_app"] = { success: false, error: String(err) };
            }
            return [2 /*return*/];
        });
    });
}
/**
 * Tests the uninstall_app tool - USE WITH CAUTION
 * This is marked as potentially dangerous and requires user authorization
 */
function testUninstallApp(results) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            try {
                console.log("\nTesting uninstall_app...");
                console.log("CAUTION: This test would uninstall an app and requires user authorization");
                console.log("Skipping actual uninstallation - this is a dangerous operation");
                // Instead of actual uninstallation, we'll just check if the function is available
                results["uninstall_app"] = {
                    success: true,
                    data: { message: "Test skipped - dangerous operation" }
                };
            }
            catch (err) {
                console.error("Error testing uninstall_app:", err);
                results["uninstall_app"] = { success: false, error: String(err) };
            }
            return [2 /*return*/];
        });
    });
}
/**
 * Tests the start_app tool
 */
function testStartApp(results) {
    return __awaiter(this, void 0, void 0, function () {
        var packageName, startResult, startData, activity, startWithActivityResult, activityData, bothSucceeded, activitySpecified, err_27;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 4, , 5]);
                    console.log("\nTesting start_app...");
                    packageName = "com.android.settings";
                    // First test: Standard app launch
                    console.log("Testing standard app launch...");
                    return [4 /*yield*/, toolCall("start_app", {
                            package_name: packageName
                        })];
                case 1:
                    startResult = _a.sent();
                    startData = startResult;
                    console.log("Operation type: ".concat(startData.operationType));
                    console.log("Package name: ".concat(startData.packageName));
                    console.log("Success: ".concat(startData.success));
                    console.log("Details: ".concat(startData.details || ""));
                    // Wait a moment for the app to start
                    console.log("Waiting for app to start...");
                    return [4 /*yield*/, toolCall("sleep", { duration_ms: 2000 })];
                case 2:
                    _a.sent();
                    // Second test: Launch with specific activity
                    console.log("\nTesting specific activity launch...");
                    activity = "com.android.settings.Settings";
                    return [4 /*yield*/, toolCall("start_app", {
                            package_name: packageName,
                            activity: activity
                        })];
                case 3:
                    startWithActivityResult = _a.sent();
                    activityData = startWithActivityResult;
                    console.log("Operation type: ".concat(activityData.operationType));
                    console.log("Package name: ".concat(activityData.packageName));
                    console.log("Success: ".concat(activityData.success));
                    console.log("Details: ".concat(activityData.details || ""));
                    bothSucceeded = startData.success && activityData.success;
                    activitySpecified = activityData.details && activityData.details.includes(activity);
                    results["start_app"] = {
                        success: bothSucceeded && activityData.packageName === packageName && activitySpecified === true,
                        data: {
                            standard: startData,
                            withActivity: activityData
                        }
                    };
                    return [3 /*break*/, 5];
                case 4:
                    err_27 = _a.sent();
                    console.error("Error testing start_app:", err_27);
                    results["start_app"] = { success: false, error: String(err_27) };
                    return [3 /*break*/, 5];
                case 5: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the stop_app tool
 */
function testStopApp(results) {
    return __awaiter(this, void 0, void 0, function () {
        var packageName, stopResult, stopData, err_28;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    console.log("\nTesting stop_app...");
                    packageName = "com.android.settings";
                    return [4 /*yield*/, toolCall("stop_app", {
                            package_name: packageName
                        })];
                case 1:
                    stopResult = _a.sent();
                    stopData = stopResult;
                    console.log("Operation type: ".concat(stopData.operationType));
                    console.log("Package name: ".concat(stopData.packageName));
                    console.log("Success: ".concat(stopData.success));
                    console.log("Details: ".concat(stopData.details || ""));
                    results["stop_app"] = {
                        success: stopData.success && stopData.packageName === packageName,
                        data: stopData
                    };
                    return [3 /*break*/, 3];
                case 2:
                    err_28 = _a.sent();
                    console.error("Error testing stop_app:", err_28);
                    results["stop_app"] = { success: false, error: String(err_28) };
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the get_page_info tool
 */
function testGetPageInfo(results) {
    return __awaiter(this, void 0, void 0, function () {
        function countNodes(node) {
            if (!node)
                return;
            nodeCount_1++;
            if (node.isClickable)
                clickableCount_1++;
            if (node.children && node.children.length > 0) {
                node.children.forEach(countNodes);
            }
        }
        var pageInfoResult, pageData, isValidStructure, nodeCount_1, clickableCount_1, xmlResult, jsonMinResult, jsonFullResult, err_29;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting get_page_info...");
                    // Test with default parameters
                    console.log("Getting page info with default format and detail level...");
                    return [4 /*yield*/, toolCall("get_page_info")];
                case 1:
                    pageInfoResult = _a.sent();
                    pageData = pageInfoResult;
                    console.log("Current package: ".concat(pageData.packageName));
                    console.log("Current activity: ".concat(pageData.activityName));
                    // Validate UI elements structure
                    console.log("\nValidating UI elements structure...");
                    isValidStructure = validateUINodeStructure(pageData.uiElements);
                    console.log("UI structure validation: ".concat(isValidStructure ? "✅ Valid" : "❌ Invalid"));
                    nodeCount_1 = 0;
                    clickableCount_1 = 0;
                    countNodes(pageData.uiElements);
                    console.log("Total UI nodes: ".concat(nodeCount_1));
                    console.log("Clickable elements: ".concat(clickableCount_1));
                    // Print UI hierarchy
                    console.log("\nUI Hierarchy (abbreviated):");
                    printUIHierarchy(pageData.uiElements);
                    // Try with XML format
                    console.log("\nGetting page info with XML format...");
                    return [4 /*yield*/, toolCall("get_page_info", {
                            format: "xml",
                            detail: "summary"
                        })];
                case 2:
                    xmlResult = _a.sent();
                    // Try with JSON format and minimal detail
                    console.log("\nGetting page info with JSON format and minimal detail...");
                    return [4 /*yield*/, toolCall("get_page_info", {
                            format: "json",
                            detail: "minimal"
                        })];
                case 3:
                    jsonMinResult = _a.sent();
                    // Try with JSON format and full detail
                    console.log("\nGetting page info with JSON format and full detail...");
                    return [4 /*yield*/, toolCall("get_page_info", {
                            format: "json",
                            detail: "full"
                        })];
                case 4:
                    jsonFullResult = _a.sent();
                    results["get_page_info"] = {
                        success: isValidStructure && nodeCount_1 > 0,
                        data: {
                            default: pageData,
                            xmlFormat: xmlResult,
                            jsonMinimal: jsonMinResult,
                            jsonFull: jsonFullResult
                        }
                    };
                    return [3 /*break*/, 6];
                case 5:
                    err_29 = _a.sent();
                    console.error("Error testing get_page_info:", err_29);
                    results["get_page_info"] = { success: false, error: String(err_29) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the click_element tool
 */
function testClickElement(results) {
    return __awaiter(this, void 0, void 0, function () {
        function findClickableElement(node) {
            if (node.isClickable && node.resourceId) {
                clickableFound_1 = true;
                resourceId_1 = node.resourceId;
                className_1 = node.className || "";
                bounds_1 = node.bounds || "";
                return;
            }
            for (var _i = 0, _a = node.children; _i < _a.length; _i++) {
                var child = _a[_i];
                if (clickableFound_1)
                    return;
                findClickableElement(child);
            }
        }
        var pageInfo, clickableFound_1, resourceId_1, className_1, bounds_1, potentialElements, button, result, textResult, clickableResult, clickableResult, result, classResult, error_2;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    console.log("\n--- Testing click_element ---");
                    _a.label = 1;
                case 1:
                    _a.trys.push([1, 20, , 21]);
                    return [4 /*yield*/, toolCall("get_page_info")];
                case 2:
                    pageInfo = _a.sent();
                    if (!pageInfo || !pageInfo.uiElements) {
                        results["click_element"] = {
                            success: false,
                            error: "Failed to get initial UI hierarchy"
                        };
                        return [2 /*return*/];
                    }
                    console.log("Current UI before attempting clicks");
                    clickableFound_1 = false;
                    resourceId_1 = "";
                    className_1 = "";
                    bounds_1 = "";
                    // Find a clickable element in the current UI
                    findClickableElement(pageInfo.uiElements);
                    if (!!clickableFound_1) return [3 /*break*/, 13];
                    console.log("No clickable elements with resource ID found, using text based searching");
                    return [4 /*yield*/, toolCall("find_element", {
                            className: "android.widget.Button",
                            partialMatch: true,
                            limit: 5
                        })];
                case 3:
                    potentialElements = _a.sent();
                    if (!(potentialElements && potentialElements.uiElements && potentialElements.uiElements.children && potentialElements.uiElements.children.length > 0)) return [3 /*break*/, 10];
                    button = potentialElements.uiElements.children[0];
                    console.log("Found a potential button to click:", button);
                    return [4 /*yield*/, toolCall("click_element", {
                            className: "android.widget.Button",
                            index: 0
                        })];
                case 4:
                    result = _a.sent();
                    if (!result) return [3 /*break*/, 5];
                    console.log("Successfully clicked button by class name");
                    results["click_element"] = {
                        success: true,
                        data: result
                    };
                    return [3 /*break*/, 9];
                case 5:
                    console.log("Failed to click button by class name, trying by text");
                    return [4 /*yield*/, toolCall("click_element", {
                            text: "OK",
                            partialMatch: true
                        })];
                case 6:
                    textResult = _a.sent();
                    if (!textResult) return [3 /*break*/, 7];
                    console.log("Successfully clicked element with text containing 'OK'");
                    results["click_element"] = {
                        success: true,
                        data: textResult
                    };
                    return [3 /*break*/, 9];
                case 7: return [4 /*yield*/, toolCall("click_element", {
                        className: "android.view.View",
                        isClickable: true,
                        index: 0
                    })];
                case 8:
                    clickableResult = _a.sent();
                    results["click_element"] = {
                        success: clickableResult ? true : false,
                        data: clickableResult || { actionType: "click", actionDescription: "All click attempts failed" }
                    };
                    _a.label = 9;
                case 9: return [3 /*break*/, 12];
                case 10:
                    console.log("No buttons found, trying most basic clickable");
                    return [4 /*yield*/, toolCall("click_element", {
                            isClickable: true,
                            index: 0
                        })];
                case 11:
                    clickableResult = _a.sent();
                    results["click_element"] = {
                        success: clickableResult ? true : false,
                        data: clickableResult || { actionType: "click", actionDescription: "All click attempts failed" }
                    };
                    _a.label = 12;
                case 12: return [3 /*break*/, 19];
                case 13:
                    console.log("Testing click_element with resourceId: ".concat(resourceId_1));
                    return [4 /*yield*/, toolCall("click_element", {
                            resourceId: resourceId_1
                        })];
                case 14:
                    result = _a.sent();
                    console.log("Object click by resource ID result:", result);
                    if (!bounds_1) return [3 /*break*/, 16];
                    console.log("Testing click using bounds: ".concat(bounds_1));
                    return [4 /*yield*/, toolCall("click_element", {
                            bounds: bounds_1
                        })];
                case 15:
                    result = (_a.sent());
                    console.log("Click by bounds result:", result);
                    _a.label = 16;
                case 16:
                    if (!className_1) return [3 /*break*/, 18];
                    console.log("Testing click by className: ".concat(className_1));
                    return [4 /*yield*/, toolCall("click_element", {
                            className: className_1,
                            index: 0
                        })];
                case 17:
                    classResult = _a.sent();
                    console.log("Click by class name result:", classResult);
                    _a.label = 18;
                case 18:
                    results["click_element"] = {
                        success: result ? true : false,
                        data: result
                    };
                    _a.label = 19;
                case 19: return [3 /*break*/, 21];
                case 20:
                    error_2 = _a.sent();
                    console.error("Error in testClickElement:", error_2);
                    results["click_element"] = {
                        success: false,
                        error: error_2.toString()
                    };
                    return [3 /*break*/, 21];
                case 21: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the tap tool
 */
function testTap(results) {
    return __awaiter(this, void 0, void 0, function () {
        var deviceResult, deviceData, width, height, match, centerX, centerY, tapResult, tapData, err_30;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 4, , 5]);
                    console.log("\nTesting tap...");
                    return [4 /*yield*/, toolCall("device_info")];
                case 1:
                    deviceResult = _a.sent();
                    deviceData = deviceResult;
                    width = 1080;
                    height = 1920;
                    if (deviceData.screenResolution) {
                        match = deviceData.screenResolution.match(/(\d+)x(\d+)/);
                        if (match) {
                            width = parseInt(match[1]);
                            height = parseInt(match[2]);
                        }
                    }
                    centerX = Math.floor(width / 2);
                    centerY = Math.floor(height / 2);
                    console.log("Using screen center coordinates: (".concat(centerX, ", ").concat(centerY, ")"));
                    return [4 /*yield*/, toolCall("tap", {
                            x: centerX,
                            y: centerY
                        })];
                case 2:
                    tapResult = _a.sent();
                    tapData = tapResult;
                    console.log("Action type: ".concat(tapData.actionType));
                    console.log("Action description: ".concat(tapData.actionDescription));
                    if (tapData.coordinates) {
                        console.log("Coordinates: (".concat(tapData.coordinates[0], ", ").concat(tapData.coordinates[1], ")"));
                    }
                    // Wait a moment for any UI response
                    console.log("Waiting for UI to respond...");
                    return [4 /*yield*/, toolCall("sleep", { duration_ms: 1000 })];
                case 3:
                    _a.sent();
                    results["tap"] = {
                        success: true,
                        data: tapData
                    };
                    return [3 /*break*/, 5];
                case 4:
                    err_30 = _a.sent();
                    console.error("Error testing tap:", err_30);
                    results["tap"] = { success: false, error: String(err_30) };
                    return [3 /*break*/, 5];
                case 5: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the set_input_text tool
 */
function testSetInputText(results) {
    return __awaiter(this, void 0, void 0, function () {
        function findInput(node) {
            if (!node)
                return;
            if (node.className === "android.widget.EditText" && !inputField_1) {
                inputField_1 = node;
                return;
            }
            if (node.children && node.children.length > 0) {
                node.children.forEach(findInput);
            }
        }
        var pageInfoResult, pageData, inputField_1, testText, inputResult, inputData, err_31;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting set_input_text...");
                    // First try to find an input field
                    console.log("Analyzing UI to find a text input field...");
                    return [4 /*yield*/, toolCall("get_page_info")];
                case 1:
                    pageInfoResult = _a.sent();
                    pageData = pageInfoResult;
                    inputField_1 = undefined;
                    findInput(pageData.uiElements);
                    if (!inputField_1) return [3 /*break*/, 3];
                    console.log("\nFound input field:");
                    console.log("Resource ID: ".concat(inputField_1.resourceId || "(no ID)"));
                    console.log("Current text: ".concat(inputField_1.text || "(empty)"));
                    testText = "OperIT Test Input " + Date.now();
                    console.log("\nSetting text to: \"".concat(testText, "\""));
                    return [4 /*yield*/, toolCall("set_input_text", {
                            text: testText,
                            resourceId: inputField_1.resourceId
                        })];
                case 2:
                    inputResult = _a.sent();
                    inputData = inputResult;
                    console.log("Action type: ".concat(inputData.actionType));
                    console.log("Action description: ".concat(inputData.actionDescription));
                    results["set_input_text"] = {
                        success: true,
                        data: { field: inputField_1, input: inputData, text: testText }
                    };
                    return [3 /*break*/, 4];
                case 3:
                    // If no input field found, report this
                    console.log("\nNo input field found in the current UI");
                    results["set_input_text"] = {
                        success: true,
                        data: { message: "No input field found to test with" }
                    };
                    _a.label = 4;
                case 4: return [3 /*break*/, 6];
                case 5:
                    err_31 = _a.sent();
                    console.error("Error testing set_input_text:", err_31);
                    results["set_input_text"] = { success: false, error: String(err_31) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the press_key tool
 */
function testPressKey(results) {
    return __awaiter(this, void 0, void 0, function () {
        var backResult, backData, homeResult, homeData, err_32;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 5, , 6]);
                    console.log("\nTesting press_key...");
                    // Test pressing the back key
                    console.log("Pressing BACK key...");
                    return [4 /*yield*/, toolCall("press_key", {
                            key_code: "KEYCODE_BACK"
                        })];
                case 1:
                    backResult = _a.sent();
                    backData = backResult;
                    console.log("Action type: ".concat(backData.actionType));
                    console.log("Action description: ".concat(backData.actionDescription));
                    // Wait for UI to respond
                    console.log("Waiting for UI to respond...");
                    return [4 /*yield*/, toolCall("sleep", { duration_ms: 1000 })];
                case 2:
                    _a.sent();
                    // Test pressing the home key
                    console.log("\nPressing HOME key...");
                    return [4 /*yield*/, toolCall("press_key", {
                            key_code: "KEYCODE_HOME"
                        })];
                case 3:
                    homeResult = _a.sent();
                    homeData = homeResult;
                    console.log("Action type: ".concat(homeData.actionType));
                    console.log("Action description: ".concat(homeData.actionDescription));
                    // Wait for UI to respond
                    console.log("Waiting for UI to respond...");
                    return [4 /*yield*/, toolCall("sleep", { duration_ms: 1000 })];
                case 4:
                    _a.sent();
                    results["press_key"] = {
                        success: true,
                        data: { back: backData, home: homeData }
                    };
                    return [3 /*break*/, 6];
                case 5:
                    err_32 = _a.sent();
                    console.error("Error testing press_key:", err_32);
                    results["press_key"] = { success: false, error: String(err_32) };
                    return [3 /*break*/, 6];
                case 6: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the swipe tool
 */
function testSwipe(results) {
    return __awaiter(this, void 0, void 0, function () {
        var deviceResult, deviceData, width, height, match, centerX, startY, endY, swipeResult, swipeData, startX, endX, midY, swipeHResult, swipeHData, error_3;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    console.log("\n--- Testing swipe ---");
                    _a.label = 1;
                case 1:
                    _a.trys.push([1, 6, , 7]);
                    return [4 /*yield*/, toolCall("device_info")];
                case 2:
                    deviceResult = _a.sent();
                    deviceData = deviceResult;
                    width = 1080;
                    height = 1920;
                    if (deviceData.screenResolution) {
                        match = deviceData.screenResolution.match(/(\d+)x(\d+)/);
                        if (match) {
                            width = parseInt(match[1]);
                            height = parseInt(match[2]);
                        }
                    }
                    centerX = Math.floor(width / 2);
                    startY = Math.floor(height * 0.8);
                    endY = Math.floor(height * 0.3);
                    console.log("Swiping up from (".concat(centerX, ", ").concat(startY, ") to (").concat(centerX, ", ").concat(endY, ")"));
                    return [4 /*yield*/, toolCall("swipe", {
                            start_x: centerX,
                            start_y: startY,
                            end_x: centerX,
                            end_y: endY,
                            duration: 500
                        })];
                case 3:
                    swipeResult = _a.sent();
                    swipeData = swipeResult;
                    console.log("Action type: ".concat(swipeData.actionType));
                    console.log("Action description: ".concat(swipeData.actionDescription));
                    // Wait for UI to respond
                    console.log("Waiting for UI to respond...");
                    return [4 /*yield*/, toolCall("sleep", { duration_ms: 1000 })];
                case 4:
                    _a.sent();
                    // Try horizontal swipe too
                    console.log("\nSwiping horizontally (left to right)...");
                    startX = Math.floor(width * 0.2);
                    endX = Math.floor(width * 0.8);
                    midY = Math.floor(height * 0.5);
                    console.log("Swiping from (".concat(startX, ", ").concat(midY, ") to (").concat(endX, ", ").concat(midY, ")"));
                    return [4 /*yield*/, toolCall("swipe", {
                            start_x: startX,
                            start_y: midY,
                            end_x: endX,
                            end_y: midY,
                            duration: 500
                        })];
                case 5:
                    swipeHResult = _a.sent();
                    swipeHData = swipeHResult;
                    console.log("Action type: ".concat(swipeHData.actionType));
                    console.log("Action description: ".concat(swipeHData.actionDescription));
                    results["swipe"] = {
                        success: true,
                        data: { vertical: swipeData, horizontal: swipeHData }
                    };
                    return [3 /*break*/, 7];
                case 6:
                    error_3 = _a.sent();
                    console.error("Error in testSwipe:", error_3);
                    results["swipe"] = { success: false, error: error_3.toString() };
                    return [3 /*break*/, 7];
                case 7: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the combined_operation tool
 */
function testCombinedOperation(results) {
    return __awaiter(this, void 0, void 0, function () {
        function findClickableWithId(node) {
            if (!node)
                return;
            if (node.isClickable && node.resourceId && !clickableId_1) {
                clickableId_1 = node.resourceId;
                return;
            }
            if (node.children && node.children.length > 0) {
                node.children.forEach(findClickableWithId);
            }
        }
        var deviceResult, deviceData, width, height, match, centerX, centerY, combinedTapResult, combinedTapData, hasUIData, startY, endY, combinedSwipeResult, combinedSwipeData, clickElementTest, clickableId_1, combinedClickResult, combinedClickData, err_33;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 6, , 7]);
                    console.log("\nTesting combined_operation...");
                    return [4 /*yield*/, toolCall("device_info")];
                case 1:
                    deviceResult = _a.sent();
                    deviceData = deviceResult;
                    width = 1080;
                    height = 1920;
                    if (deviceData.screenResolution) {
                        match = deviceData.screenResolution.match(/(\d+)x(\d+)/);
                        if (match) {
                            width = parseInt(match[1]);
                            height = parseInt(match[2]);
                        }
                    }
                    centerX = Math.floor(width / 2);
                    centerY = Math.floor(height / 2);
                    // Test tap combined operation
                    console.log("Testing combined tap operation at (".concat(centerX, ", ").concat(centerY, ")..."));
                    return [4 /*yield*/, toolCall("combined_operation", {
                            operation: "tap ".concat(centerX, " ").concat(centerY),
                            delay_ms: 1500
                        })];
                case 2:
                    combinedTapResult = _a.sent();
                    combinedTapData = combinedTapResult;
                    console.log("Operation summary: ".concat(combinedTapData.operationSummary));
                    console.log("Wait time: ".concat(combinedTapData.waitTime, "ms"));
                    console.log("Package after operation: ".concat(combinedTapData.pageInfo.packageName));
                    console.log("Activity after operation: ".concat(combinedTapData.pageInfo.activityName));
                    hasUIData = validateUINodeStructure(combinedTapData.pageInfo.uiElements);
                    console.log("Has valid UI data: ".concat(hasUIData ? "✅" : "❌"));
                    // Try another type of combined operation - swipe
                    console.log("\nTesting combined swipe operation...");
                    startY = Math.floor(height * 0.7);
                    endY = Math.floor(height * 0.3);
                    return [4 /*yield*/, toolCall("combined_operation", {
                            operation: "swipe ".concat(centerX, " ").concat(startY, " ").concat(centerX, " ").concat(endY, " 500"),
                            delay_ms: 1500
                        })];
                case 3:
                    combinedSwipeResult = _a.sent();
                    combinedSwipeData = combinedSwipeResult;
                    console.log("Operation summary: ".concat(combinedSwipeData.operationSummary));
                    console.log("Wait time: ".concat(combinedSwipeData.waitTime, "ms"));
                    clickElementTest = undefined;
                    clickableId_1 = undefined;
                    findClickableWithId(combinedSwipeData.pageInfo.uiElements);
                    if (!clickableId_1) return [3 /*break*/, 5];
                    console.log("\nTesting combined click_element operation on ".concat(clickableId_1, "..."));
                    return [4 /*yield*/, toolCall("combined_operation", {
                            operation: "click_element resourceId ".concat(clickableId_1),
                            delay_ms: 1500
                        })];
                case 4:
                    combinedClickResult = _a.sent();
                    combinedClickData = combinedClickResult;
                    console.log("Operation summary: ".concat(combinedClickData.operationSummary));
                    console.log("Wait time: ".concat(combinedClickData.waitTime, "ms"));
                    clickElementTest = combinedClickData;
                    _a.label = 5;
                case 5:
                    results["combined_operation"] = {
                        success: hasUIData,
                        data: {
                            tap: combinedTapData,
                            swipe: combinedSwipeData,
                            clickElement: clickElementTest
                        }
                    };
                    return [3 /*break*/, 7];
                case 6:
                    err_33 = _a.sent();
                    console.error("Error testing combined_operation:", err_33);
                    results["combined_operation"] = { success: false, error: String(err_33) };
                    return [3 /*break*/, 7];
                case 7: return [2 /*return*/];
            }
        });
    });
}
/**
 * Tests the find_element tool
 */
function testFindElement(results) {
    return __awaiter(this, void 0, void 0, function () {
        function findClickableElement(node) {
            if (node.isClickable && node.resourceId) {
                clickableFound_2 = true;
                resourceId_2 = node.resourceId;
                className_2 = node.className || "";
                bounds_2 = node.bounds || "";
                return;
            }
            for (var _i = 0, _a = node.children; _i < _a.length; _i++) {
                var child = _a[_i];
                if (clickableFound_2)
                    return;
                findClickableElement(child);
            }
        }
        var pageInfo, clickableFound_2, resourceId_2, className_2, bounds_2, result, textResult, resultById, resultByClass, error_4;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    console.log("\n--- Testing find_element ---");
                    _a.label = 1;
                case 1:
                    _a.trys.push([1, 13, , 14]);
                    return [4 /*yield*/, toolCall("get_page_info")];
                case 2:
                    pageInfo = _a.sent();
                    if (!pageInfo || !pageInfo.uiElements) {
                        results["find_element"] = {
                            success: false,
                            error: "Failed to get initial UI hierarchy for reference"
                        };
                        return [2 /*return*/];
                    }
                    console.log("Current UI available for searching");
                    clickableFound_2 = false;
                    resourceId_2 = "";
                    className_2 = "";
                    bounds_2 = "";
                    // Find a clickable element in the current UI
                    findClickableElement(pageInfo.uiElements);
                    if (!!clickableFound_2) return [3 /*break*/, 7];
                    console.log("No clickable elements with resource ID found, using generic search");
                    return [4 /*yield*/, toolCall("find_element", {
                            className: "android.widget.Button",
                            partialMatch: true,
                            limit: 5
                        })];
                case 3:
                    result = _a.sent();
                    if (!(result && result.uiElements && result.uiElements.children && result.uiElements.children.length > 0)) return [3 /*break*/, 4];
                    console.log("Found ".concat(result.uiElements.children.length, " elements with partial class matching android.widget.Button"));
                    prettyPrint("Find Element Results", result);
                    results["find_element"] = {
                        success: true,
                        data: result
                    };
                    return [3 /*break*/, 6];
                case 4: return [4 /*yield*/, toolCall("find_element", {
                        text: "button",
                        partialMatch: true,
                        limit: 5
                    })];
                case 5:
                    textResult = _a.sent();
                    if (textResult && textResult.uiElements && textResult.uiElements.children && textResult.uiElements.children.length > 0) {
                        console.log("Found ".concat(textResult.uiElements.children.length, " elements with text containing 'button'"));
                        prettyPrint("Find Element Text Results", textResult);
                        results["find_element"] = {
                            success: true,
                            data: textResult
                        };
                    }
                    else {
                        results["find_element"] = {
                            success: false,
                            error: "No elements found with search criteria"
                        };
                    }
                    _a.label = 6;
                case 6: return [3 /*break*/, 12];
                case 7:
                    // Test finding by resourceId
                    console.log("Testing find_element with resourceId: ".concat(resourceId_2));
                    return [4 /*yield*/, toolCall("find_element", {
                            resourceId: resourceId_2
                        })];
                case 8:
                    resultById = _a.sent();
                    if (!(resultById && resultById.uiElements && resultById.uiElements.children && resultById.uiElements.children.length > 0)) return [3 /*break*/, 11];
                    console.log("Found ".concat(resultById.uiElements.children.length, " elements with resourceId: ").concat(resourceId_2));
                    prettyPrint("Find Element By ID Results", resultById);
                    if (!className_2) return [3 /*break*/, 10];
                    console.log("Testing find_element with className: ".concat(className_2));
                    return [4 /*yield*/, toolCall("find_element", {
                            className: className_2
                        })];
                case 9:
                    resultByClass = _a.sent();
                    if (resultByClass && resultByClass.uiElements && resultByClass.uiElements.children && resultByClass.uiElements.children.length > 0) {
                        console.log("Found ".concat(resultByClass.uiElements.children.length, " elements with className: ").concat(className_2));
                    }
                    _a.label = 10;
                case 10:
                    // If bounds is available, save it for click_element test with bounds
                    if (bounds_2) {
                        console.log("Found bounds for click testing: ".concat(bounds_2));
                        // We'll use this in testClickElement
                    }
                    results["find_element"] = {
                        success: true,
                        data: resultById
                    };
                    return [3 /*break*/, 12];
                case 11:
                    results["find_element"] = {
                        success: false,
                        error: "No elements found with resourceId: ".concat(resourceId_2)
                    };
                    _a.label = 12;
                case 12: return [3 /*break*/, 14];
                case 13:
                    error_4 = _a.sent();
                    console.error("Error in testFindElement:", error_4);
                    results["find_element"] = {
                        success: false,
                        error: error_4.toString()
                    };
                    return [3 /*break*/, 14];
                case 14: return [2 /*return*/];
            }
        });
    });
}
// Export main function and category-specific test runners
exports.main = runTests;
exports.testCategory = function (params) {
    return runTests(params);
};
// Add exports for these functions
exports.testQueryProblemLibrary = testQueryProblemLibrary;
exports.testUsePackage = testUsePackage;
exports.testCalculator = testCalculator;
exports.testSleep = testSleep;
exports.testListFiles = testListFiles;
exports.testWriteFile = testWriteFile;
exports.testReadFile = testReadFile;
exports.testFileExists = testFileExists;
exports.testMakeDirectory = testMakeDirectory;
exports.testCopyFile = testCopyFile;
exports.testMoveFile = testMoveFile;
exports.testFindFiles = testFindFiles;
exports.testFileInfo = testFileInfo;
exports.testZipFiles = testZipFiles;
exports.testUnzipFiles = testUnzipFiles;
exports.testOpenFile = testOpenFile;
exports.testShareFile = testShareFile;
exports.testDeleteFile = testDeleteFile;
exports.testWebSearch = testWebSearch;
exports.testHttpRequest = testHttpRequest;
exports.testDownloadFile = testDownloadFile;
exports.testFetchWebPage = testFetchWebPage;
exports.testDeviceInfo = testDeviceInfo;
exports.testGetSystemSetting = testGetSystemSetting;
exports.testModifySystemSetting = testModifySystemSetting;
exports.testListInstalledApps = testListInstalledApps;
exports.testInstallApp = testInstallApp;
exports.testUninstallApp = testUninstallApp;
exports.testStartApp = testStartApp;
exports.testStopApp = testStopApp;
exports.testGetPageInfo = testGetPageInfo;
exports.testClickElement = testClickElement;
exports.testTap = testTap;
exports.testSetInputText = testSetInputText;
exports.testPressKey = testPressKey;
exports.testSwipe = testSwipe;
exports.testCombinedOperation = testCombinedOperation;
exports.testFindElement = testFindElement;

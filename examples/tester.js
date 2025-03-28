/**
 * Tool Response Tester
 *
 * This script tests all available tools and their structured return values,
 * with special focus on the UIPageResultData with SimplifiedUINode structure.
 *
 * How to run:
 * 1. Compile: tsc examples/tester.ts
 * 2. Run via VSCode Debug Menu (recommended):
 *    - Select function (main, testUI, testFiles, etc.)
 *    - Press F5 to execute
 * 3. Run via command line:
 *    - Windows: .\tools\execute_js.bat examples\tester.js main '{}'
 *    - Linux/macOS: ./tools/execute_js.sh examples/tester.js main '{}'
 *    - Specific test: .\tools\execute_js.bat examples\tester.js testUI '{}'
 */
/**
 * Helper function to print objects in a readable format
 */
function prettyPrint(label, data) {
    console.log(`\n=== ${label} ===`);
    console.log(JSON.stringify(data, null, 2));
    console.log("=".repeat(label.length + 8));
}
/**
 * Validate that a UI node has the correct structure according to our interface
 */
function validateUINodeStructure(node) {
    if (!node)
        return false;
    // Check essential structure
    if (typeof node.isClickable !== 'boolean') {
        console.error("Node is missing isClickable property or it's not a boolean");
        return false;
    }
    if (!Array.isArray(node.children)) {
        console.error("Node is missing children array property");
        return false;
    }
    // Check that optional properties have correct types if present
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
    for (const child of node.children) {
        if (!validateUINodeStructure(child)) {
            return false;
        }
    }
    return true;
}
/**
 * Helper to print node hierarchy
 */
function printUIHierarchy(node, indent = "") {
    if (!node)
        return;
    const className = node.className || "unknown";
    const text = node.text ? `"${node.text}"` : "";
    const desc = node.contentDesc ? `(${node.contentDesc})` : "";
    const id = node.resourceId ? `#${node.resourceId.split("/").pop()}` : "";
    const clickable = node.isClickable ? "👆" : "";
    console.log(`${indent}${clickable}[${className}] ${text} ${desc} ${id}`);
    if (node.children) {
        node.children.forEach(child => printUIHierarchy(child, indent + "  "));
    }
}
/**
 * Main test function - This is the entry point as per the README.md requirements
 * @param params Optional parameters to customize the test
 */
async function runTests(params = {}) {
    var _a;
    console.log("Starting Tool Response Tester...");
    console.log("Parameters:", params);
    // Store all test results
    const results = {};
    const testSummary = [];
    const startTime = Date.now();
    // Determine which tests to run based on input parameters
    const testType = params.testType || "all";
    try {
        // Test toString() implementations for structured data
        if (testType === "all" || testType === "toString") {
            console.log("\n🔤 Testing toString() Methods...");
            testSummary.push("Running toString() method tests");
            // UI data toString tests
            await testUIToString(results);
            // File operations toString tests
            await testFilesToString(results);
            // HTTP response toString tests
            await testHTTPToString(results);
            // System settings toString tests
            await testSystemToString(results);
            // Added: Calculator toString tests
            await testCalculatorToString(results);
        }
        // Test UI structured data
        if (testType === "all" || testType === "ui") {
            console.log("\n📱 Testing UI Structured Data...");
            testSummary.push("Running UI structured data tests");
            // UI page information test
            await testUIPageInfo(results);
            // UI interaction test with combined operation
            await testCombinedOperation(results);
            // Test UI element search
            await testFindElement(results);
            // Added: Additional UI operations
            await testUIOperations(results);
        }
        // Test file operations
        if (testType === "all" || testType === "files") {
            console.log("\n📁 Testing File Operations...");
            testSummary.push("Running file operations tests");
            // List files test
            await testListFiles(results);
            // Read file test
            await testReadFile(results);
            // File existence test
            await testFileExists(results);
            // Added: Additional file operations
            await testWriteFile(results);
            await testFileOperations(results);
            await testFindFiles(results);
        }
        // Test network operations
        if (testType === "all" || testType === "network") {
            console.log("\n🌐 Testing Network Operations...");
            testSummary.push("Running network operations tests");
            // HTTP request test
            await testHTTPRequest(results);
            // Web search test
            await testWebSearch(results);
            // Added: Web page fetch test
            await testFetchWebPage(results);
        }
        // Test system operations
        if (testType === "all" || testType === "system") {
            console.log("\n⚙️ Testing System Operations...");
            testSummary.push("Running system operations tests");
            // System settings test
            await testSystemSetting(results);
            // Installed apps list test
            await testListApps(results);
            // Device info test
            await testDeviceInfo(results);
            // Added: Sleep test
            await testSleep(results);
            // Added: App operations test
            await testAppOperations(results);
        }
        // Test clipboard operations
        if (testType === "all" || testType === "clipboard") {
            console.log("\n📋 Testing Clipboard Operations...");
            testSummary.push("Running clipboard operations tests");
            // Get/set clipboard test
            await testClipboard(results);
        }
        // Added: Test calculator operations
        if (testType === "all" || testType === "calculator") {
            console.log("\n🧮 Testing Calculator Operations...");
            testSummary.push("Running calculator operations tests");
            // Calculator test
            await testCalculator(results);
            // Date calculation test
            await testDateCalc(results);
        }
        // Added: Test connection operations
        if (testType === "all" || testType === "connection") {
            console.log("\n🔌 Testing Connection Operations...");
            testSummary.push("Running connection operations tests");
        }
        // Added: Test package operations
        if (testType === "all" || testType === "package") {
            console.log("\n📦 Testing Package Operations...");
            testSummary.push("Running package operations tests");
            // Use package test
            await testUsePackage(results);
            // Query problem library test
            await testQueryProblemLibrary(results);
        }
        // Calculate test duration
        const duration = Date.now() - startTime;
        // Print test summary
        console.log("\n📊 Test Summary:");
        Object.entries(results).forEach(([test, result]) => {
            const status = result.success ? "✅ PASS" : "❌ FAIL";
            console.log(`${status}: ${test}`);
        });
        const successCount = Object.values(results).filter(r => r.success).length;
        const totalTests = Object.keys(results).length;
        const summaryText = `Overall: ${successCount}/${totalTests} tests passed in ${duration / 1000}s`;
        console.log(`\n${summaryText}`);
        // If everything worked, dump the UI structure
        if ((_a = results["get_page_info"]) === null || _a === void 0 ? void 0 : _a.success) {
            prettyPrint("UIPageResultData Full Structure", results["get_page_info"].data);
        }
        console.log("\nTool Response Tester completed!");
        // Use complete() function to return results as required by README.md
        complete({
            testSummary,
            summary: summaryText,
            testsPassed: successCount,
            testsTotal: totalTests,
            testResults: results,
            duration: `${duration / 1000}s`
        });
    }
    catch (error) {
        console.error("Unexpected error in test suite:", error);
        complete({
            error: String(error),
            testSummary,
            testResults: results
        });
    }
}
/**
 * Test UI toString methods
 */
async function testUIToString(results) {
    try {
        console.log("\nTesting UIPageResultData.toString()...");
        const pageInfo = await toolCall("get_page_info");
        // Type assertion and toString test
        const uiPageData = pageInfo;
        console.log("UIPageResultData.toString() result:");
        console.log("-".repeat(40));
        console.log(uiPageData.toString());
        console.log("-".repeat(40));
        // Test SimplifiedUINode's toString
        if (uiPageData.uiElements) {
            console.log("\nTesting SimplifiedUINode.toString()...");
            console.log("-".repeat(40));
            console.log(uiPageData.uiElements.toString());
            console.log("-".repeat(40));
            // Test the toTreeString method if available
            console.log("\nTesting SimplifiedUINode.toTreeString()...");
            if (typeof uiPageData.uiElements.toTreeString === 'function') {
                console.log("-".repeat(40));
                console.log(uiPageData.uiElements.toTreeString());
                console.log("-".repeat(40));
            }
            else {
                console.log("toTreeString method not available");
            }
        }
        results["ui_tostring_test"] = { success: true };
    }
    catch (err) {
        console.error("Error testing UI toString methods:", err);
        results["ui_tostring_test"] = { success: false, error: String(err) };
    }
}
/**
 * Test files toString methods
 */
async function testFilesToString(results) {
    try {
        console.log("\nTesting DirectoryListingData.toString()...");
        const filesList = await toolCall("list_files", { path: "/sdcard/" });
        // Type assertion and toString test
        const dirListing = filesList;
        console.log("DirectoryListingData.toString() result:");
        console.log("-".repeat(40));
        console.log(dirListing.toString());
        console.log("-".repeat(40));
        results["files_tostring_test"] = { success: true };
    }
    catch (err) {
        console.error("Error testing Files toString methods:", err);
        results["files_tostring_test"] = { success: false, error: String(err) };
    }
}
/**
 * Test HTTP toString methods
 */
async function testHTTPToString(results) {
    try {
        console.log("\nTesting HttpResponseData.toString()...");
        const httpResponse = await toolCall("http_request", {
            url: "https://httpbin.org/get",
            method: "GET"
        });
        // Type assertion and toString test
        const responseData = httpResponse;
        console.log("HttpResponseData.toString() result:");
        console.log("-".repeat(40));
        console.log(responseData.toString());
        console.log("-".repeat(40));
        results["http_tostring_test"] = { success: true };
    }
    catch (err) {
        console.error("Error testing HTTP toString methods:", err);
        results["http_tostring_test"] = { success: false, error: String(err) };
    }
}
/**
 * Test system toString methods
 */
async function testSystemToString(results) {
    try {
        console.log("\nTesting SystemSettingData.toString()...");
        const settingResult = await toolCall("get_system_setting", {
            setting: "screen_brightness_mode",
            namespace: "system"
        });
        // Type assertion and toString test
        const settingData = settingResult;
        console.log("SystemSettingData.toString() result:");
        console.log("-".repeat(40));
        console.log(settingData.toString());
        console.log("-".repeat(40));
        results["system_tostring_test"] = { success: true };
    }
    catch (err) {
        console.error("Error testing System toString methods:", err);
        results["system_tostring_test"] = { success: false, error: String(err) };
    }
}
/**
 * Test UI page info
 */
async function testUIPageInfo(results) {
    var _a;
    try {
        console.log("\nTesting get_page_info...");
        const pageInfo = await toolCall("get_page_info");
        // Type assertion to verify the structure
        const uiPageData = pageInfo;
        console.log(`Current App: ${uiPageData.packageName}`);
        console.log(`Current Activity: ${uiPageData.activityName}`);
        // Check that we have the structured UI elements
        if (uiPageData.uiElements) {
            console.log("\nUI Elements Structure:");
            console.log(`Root class: ${uiPageData.uiElements.className}`);
            console.log(`Child count: ${((_a = uiPageData.uiElements.children) === null || _a === void 0 ? void 0 : _a.length) || 0}`);
            // Validate the structure matches our expectations
            console.log("\nValidating SimplifiedUINode structure...");
            const isValidStructure = validateUINodeStructure(uiPageData.uiElements);
            console.log(`Structure validation: ${isValidStructure ? "✅ PASS" : "❌ FAIL"}`);
            // Test recursive node traversal (demonstrates the tree structure works)
            let nodeCount = 0;
            let clickableCount = 0;
            function countNodes(node) {
                if (!node)
                    return;
                nodeCount++;
                if (node.isClickable)
                    clickableCount++;
                if (node.children && node.children.length > 0) {
                    node.children.forEach(countNodes);
                }
            }
            countNodes(uiPageData.uiElements);
            console.log(`Total nodes in UI tree: ${nodeCount}`);
            console.log(`Clickable elements: ${clickableCount}`);
            // Find text input fields (demonstrates targeted search through hierarchy)
            const textInputs = [];
            function findTextInputs(node) {
                if (!node)
                    return;
                // Look for EditText elements or elements with input-related hints
                if (node.className === 'EditText' ||
                    (node.text && node.text.toLowerCase().includes('input')) ||
                    (node.contentDesc && node.contentDesc.toLowerCase().includes('input'))) {
                    textInputs.push(node);
                }
                if (node.children && node.children.length > 0) {
                    node.children.forEach(findTextInputs);
                }
            }
            findTextInputs(uiPageData.uiElements);
            console.log(`Found ${textInputs.length} potential text input fields`);
            // Print the hierarchy
            console.log("\nUI Hierarchy:");
            printUIHierarchy(uiPageData.uiElements);
        }
        else {
            console.error("No UI elements found in the response!");
        }
        results["get_page_info"] = { success: true, data: uiPageData };
    }
    catch (err) {
        console.error("Error testing get_page_info:", err);
        results["get_page_info"] = { success: false, error: String(err) };
    }
}
/**
 * Test combined operation
 */
async function testCombinedOperation(results) {
    try {
        console.log("\nTesting combined_operation with tap...");
        // Use some reasonable coordinates that should work on most screens
        const centerX = 500;
        const centerY = 500;
        const combinedResult = await toolCall("combined_operation", {
            operation: `tap ${centerX} ${centerY}`,
            delayMs: 1000
        });
        // Type assertion
        const combinedData = combinedResult;
        console.log(`Operation summary: ${combinedData.operationSummary}`);
        console.log(`Wait time: ${combinedData.waitTime}ms`);
        // Check the page info structure after operation
        if (combinedData.pageInfo && combinedData.pageInfo.uiElements) {
            console.log("\nUI State After Operation:");
            console.log(`App: ${combinedData.pageInfo.packageName}`);
            console.log(`Activity: ${combinedData.pageInfo.activityName}`);
            // Validate the structure matches our expectations
            console.log("\nValidating SimplifiedUINode structure in combined result...");
            const isValidStructure = validateUINodeStructure(combinedData.pageInfo.uiElements);
            console.log(`Structure validation: ${isValidStructure ? "✅ PASS" : "❌ FAIL"}`);
            // Print the hierarchy after operation
            console.log("\nUI Hierarchy After Operation:");
            printUIHierarchy(combinedData.pageInfo.uiElements);
        }
        results["combined_operation"] = { success: true, data: combinedData };
    }
    catch (err) {
        console.error("Error testing combined_operation:", err);
        results["combined_operation"] = { success: false, error: String(err) };
    }
}
/**
 * Test find element
 */
async function testFindElement(results) {
    try {
        console.log("\nTesting find_element...");
        const findElementResult = await toolCall("find_element", {
            resourceId: "*:id/content"
        });
        // Type assertion
        const findElementData = findElementResult;
        console.log(`Found elements: ${findElementData.matchCount}`);
        if (findElementData.matches && findElementData.matches.length > 0) {
            console.log(`First match class: ${findElementData.matches[0].className}`);
            console.log(`First match bounds: ${findElementData.matches[0].bounds}`);
            // Validate structure of found elements
            const allValid = findElementData.matches.every(validateUINodeStructure);
            console.log(`All matches have valid structure: ${allValid ? "✅ PASS" : "❌ FAIL"}`);
        }
        results["find_element"] = { success: true, data: findElementData };
    }
    catch (err) {
        console.error("Error testing find_element:", err);
        results["find_element"] = { success: false, error: String(err) };
    }
}
/**
 * Test list files
 */
async function testListFiles(results) {
    try {
        console.log("\nTesting list_files...");
        const filesList = await toolCall("list_files", { path: "/sdcard/" });
        // Type assertion
        const dirListing = filesList;
        console.log(`Listed directory: ${dirListing.path}`);
        console.log(`Found ${dirListing.entries.length} entries`);
        // Show a few entries
        if (dirListing.entries.length > 0) {
            console.log("\nSample entries:");
            dirListing.entries.slice(0, 3).forEach(entry => {
                console.log(`- ${entry.name} (${entry.isDirectory ? "Directory" : "File"}, ${entry.size} bytes)`);
            });
        }
        results["list_files"] = { success: true, data: dirListing };
    }
    catch (err) {
        console.error("Error testing list_files:", err);
        results["list_files"] = { success: false, error: String(err) };
    }
}
/**
 * Test read file
 */
async function testReadFile(results) {
    try {
        console.log("\nTesting read_file...");
        // Try to read a common Android file
        const fileContent = await toolCall("read_file", {
            path: "/sdcard/Download/test.txt"
        });
        // Type assertion - use unknown as intermediate type to avoid linter error
        const fileData = fileContent;
        console.log(`File path: ${fileData.path}`);
        console.log(`Content length: ${fileData.content.length} characters`);
        console.log(`Content preview: ${fileData.content.substring(0, 100)}...`);
        results["read_file"] = { success: true, data: fileData };
    }
    catch (err) {
        console.error("Error testing read_file:", err);
        results["read_file"] = { success: false, error: String(err) };
    }
}
/**
 * Test file exists
 */
async function testFileExists(results) {
    try {
        console.log("\nTesting file_exists...");
        // Check if a common Android file exists
        const fileExistsResult = await toolCall("file_exists", {
            path: "/system/build.prop"
        });
        // Type assertion - use unknown as intermediate type to avoid linter error
        const fileExistsData = fileExistsResult;
        console.log(`File path: ${fileExistsData.path}`);
        console.log(`File exists: ${fileExistsData.exists}`);
        if (fileExistsData.exists) {
            console.log(`Is directory: ${fileExistsData.isDirectory}`);
            console.log(`Size: ${fileExistsData.size} bytes`);
        }
        results["file_exists"] = { success: true, data: fileExistsData };
    }
    catch (err) {
        console.error("Error testing file_exists:", err);
        results["file_exists"] = { success: false, error: String(err) };
    }
}
/**
 * Test HTTP request
 */
async function testHTTPRequest(results) {
    try {
        console.log("\nTesting http_request...");
        const httpResponse = await toolCall("http_request", {
            url: "https://httpbin.org/get",
            method: "GET"
        });
        // Type assertion
        const responseData = httpResponse;
        console.log(`Status: ${responseData.statusCode} ${responseData.statusMessage}`);
        console.log(`Content type: ${responseData.contentType}`);
        console.log(`Content size: ${responseData.size} bytes`);
        // Test POST request
        console.log("\nTesting http_request with POST...");
        const postResponse = await toolCall("http_request", {
            url: "https://httpbin.org/post",
            method: "POST",
            body: JSON.stringify({ test: "value" }),
            headers: { "Content-Type": "application/json" }
        });
        // Type assertion
        const postResponseData = postResponse;
        console.log(`POST Status: ${postResponseData.statusCode} ${postResponseData.statusMessage}`);
        console.log(`POST Content type: ${postResponseData.contentType}`);
        results["http_request"] = { success: true, data: responseData };
        results["http_post_request"] = { success: true, data: postResponseData };
    }
    catch (err) {
        console.error("Error testing http_request:", err);
        results["http_request"] = { success: false, error: String(err) };
    }
}
/**
 * Test web search
 */
async function testWebSearch(results) {
    try {
        console.log("\nTesting web_search...");
        const searchResults = await toolCall("web_search", { query: "Android UI automation" });
        // Type assertion
        const searchData = searchResults;
        console.log(`Search query: ${searchData.query}`);
        console.log(`Results found: ${searchData.results.length}`);
        // Show first result
        if (searchData.results.length > 0) {
            const firstResult = searchData.results[0];
            console.log("\nFirst result:");
            console.log(`Title: ${firstResult.title}`);
            console.log(`URL: ${firstResult.url}`);
            console.log(`Snippet: ${firstResult.snippet.substring(0, 100)}...`);
        }
        results["web_search"] = { success: true, data: searchData };
    }
    catch (err) {
        console.error("Error testing web_search:", err);
        results["web_search"] = { success: false, error: String(err) };
    }
}
/**
 * Test system setting
 */
async function testSystemSetting(results) {
    try {
        console.log("\nTesting get_system_setting...");
        const settingResult = await toolCall("get_system_setting", {
            setting: "screen_brightness_mode",
            namespace: "system"
        });
        // Type assertion
        const settingData = settingResult;
        console.log(`Setting namespace: ${settingData.namespace}`);
        console.log(`Setting name: ${settingData.setting}`);
        console.log(`Setting value: ${settingData.value}`);
        results["get_system_setting"] = { success: true, data: settingData };
    }
    catch (err) {
        console.error("Error testing get_system_setting:", err);
        results["get_system_setting"] = { success: false, error: String(err) };
    }
}
/**
 * Test list apps
 */
async function testListApps(results) {
    try {
        console.log("\nTesting list_installed_apps...");
        const appsResult = await toolCall("list_installed_apps", {
            include_system_apps: false
        });
        // Type assertion
        const appsData = appsResult;
        console.log(`Including system apps: ${appsData.includesSystemApps}`);
        console.log(`Total apps: ${appsData.packages.length}`);
        // Show a few apps
        if (appsData.packages.length > 0) {
            console.log("\nSample apps:");
            appsData.packages.slice(0, 3).forEach(app => {
                console.log(`- ${app}`);
            });
        }
        results["list_installed_apps"] = { success: true, data: appsData };
    }
    catch (err) {
        console.error("Error testing list_installed_apps:", err);
        results["list_installed_apps"] = { success: false, error: String(err) };
    }
}
/**
 * Test device info
 */
async function testDeviceInfo(results) {
    try {
        console.log("\nTesting get_device_info...");
        const deviceInfoResult = await toolCall("get_device_info");
        // Type assertion
        const deviceData = deviceInfoResult;
        console.log(`Device: ${deviceData.manufacturer} ${deviceData.model}`);
        console.log(`Android version: ${deviceData.androidVersion} (SDK ${deviceData.sdkVersion})`);
        console.log(`Display: ${deviceData.displayWidth}x${deviceData.displayHeight}`);
        console.log(`Screen density: ${deviceData.displayDensity}`);
        results["get_device_info"] = { success: true, data: deviceData };
    }
    catch (err) {
        console.error("Error testing get_device_info:", err);
        results["get_device_info"] = { success: false, error: String(err) };
    }
}
/**
 * Test clipboard operations
 */
async function testClipboard(results) {
    try {
        console.log("\nTesting clipboard operations...");
        // Set clipboard text
        const testText = "Test clipboard text " + new Date().toISOString();
        console.log(`Setting clipboard text: "${testText}"`);
        const setClipboardResult = await toolCall("set_clipboard", {
            text: testText
        });
        // Get clipboard text
        const getClipboardResult = await toolCall("get_clipboard");
        // Type assertion
        const clipboardData = getClipboardResult;
        console.log(`Retrieved clipboard text: "${clipboardData.text}"`);
        // Verify the text matches what we set
        const clipboardMatches = clipboardData.text === testText;
        console.log(`Clipboard text matches: ${clipboardMatches ? "✅ PASS" : "❌ FAIL"}`);
        results["set_clipboard"] = { success: true };
        results["get_clipboard"] = {
            success: clipboardMatches,
            data: clipboardData,
            error: clipboardMatches ? undefined : "Retrieved text doesn't match what was set"
        };
    }
    catch (err) {
        console.error("Error testing clipboard operations:", err);
        results["clipboard_operations"] = { success: false, error: String(err) };
    }
}
/**
 * Run a single test type
 * @param params The test type to run
 */
async function runSingleTest(params) {
    return runTests({ testType: params.testType });
}
/**
 * Run toString tests
 */
async function testToString() {
    return runTests({ testType: "toString" });
}
/**
 * Run UI tests
 */
async function testUI() {
    return runTests({ testType: "ui" });
}
/**
 * Run file tests
 */
async function testFiles() {
    return runTests({ testType: "files" });
}
/**
 * Run network tests
 */
async function testNetwork() {
    return runTests({ testType: "network" });
}
/**
 * Run system tests
 */
async function testSystem() {
    return runTests({ testType: "system" });
}
/**
 * Run clipboard tests
 */
async function testClipboardOnly() {
    return runTests({ testType: "clipboard" });
}
// Export functions to be called as entry points
exports.runTests = runTests;
exports.testToString = testToString;
exports.testUI = testUI;
exports.testFiles = testFiles;
exports.testNetwork = testNetwork;
exports.testSystem = testSystem;
exports.testClipboard = testClipboardOnly;
exports.runSingleTest = runSingleTest;
// Export additional test functions
exports.testCalculator = function () {
    return runTests({ testType: "calculator" });
};
exports.testConnection = function () {
    return runTests({ testType: "connection" });
};
exports.testPackage = function () {
    return runTests({ testType: "package" });
};
// Export individual test functions for direct calling
exports.testUIOperations = testUIOperations;
exports.testFileOperations = testFileOperations;
exports.testWriteFile = testWriteFile;
exports.testFindFiles = testFindFiles;
exports.testFetchWebPage = testFetchWebPage;
exports.testSleep = testSleep;
exports.testAppOperations = testAppOperations;
exports.testCalculate = testCalculator;
exports.testDateCalc = testDateCalc;
exports.testUsePackage = testUsePackage;
exports.testQueryProblemLibrary = testQueryProblemLibrary;
// Export a default main function as per README.md suggestion
exports.main = runTests;
// Added: Test additional UI operations
async function testUIOperations(results) {
    try {
        console.log("\nTesting additional UI operations...");
        // Test click_element
        console.log("Testing click_element...");
        const clickResult = await toolCall("click_element", {
            resourceId: "*:id/button1" // Attempt to click a button with a common ID
        });
        // Type assertion
        const clickData = clickResult;
        console.log(`Action type: ${clickData.actionType}`);
        console.log(`Action description: ${clickData.actionDescription}`);
        if (clickData.elementId) {
            console.log(`Element ID: ${clickData.elementId}`);
        }
        // Test set_input_text
        console.log("\nTesting set_input_text...");
        const setText = await toolCall("set_input_text", {
            resourceId: "*:id/edit_text", // Target a common edit text field
            text: "Test input text"
        });
        // Type assertion
        const setTextData = setText;
        console.log(`Action type: ${setTextData.actionType}`);
        console.log(`Action description: ${setTextData.actionDescription}`);
        // Test press_key
        console.log("\nTesting press_key...");
        const keyPress = await toolCall("press_key", {
            keyCode: "KEYCODE_BACK" // Press back button
        });
        // Type assertion
        const keyPressData = keyPress;
        console.log(`Action type: ${keyPressData.actionType}`);
        console.log(`Action description: ${keyPressData.actionDescription}`);
        // Test swipe
        console.log("\nTesting swipe...");
        const swipeResult = await toolCall("swipe", {
            startX: 500,
            startY: 1000,
            endX: 500,
            endY: 200,
            duration: 500
        });
        // Type assertion
        const swipeData = swipeResult;
        console.log(`Action type: ${swipeData.actionType}`);
        console.log(`Action description: ${swipeData.actionDescription}`);
        // Test launch_app
        console.log("\nTesting launch_app...");
        const launchResult = await toolCall("launch_app", {
            packageName: "com.android.settings"
        });
        // Type assertion
        const launchData = launchResult;
        console.log(`Action type: ${launchData.actionType}`);
        console.log(`Action description: ${launchData.actionDescription}`);
        results["ui_operations"] = { success: true };
    }
    catch (err) {
        console.error("Error testing UI operations:", err);
        results["ui_operations"] = { success: false, error: String(err) };
    }
}
// Added: Test write file
async function testWriteFile(results) {
    try {
        console.log("\nTesting write_file...");
        const testContent = "This is a test file created by the tester.\nTime: " + new Date().toISOString();
        const testPath = "/sdcard/test_file.txt";
        const writeResult = await toolCall("write_file", {
            path: testPath,
            content: testContent
        });
        // Type assertion
        const writeData = writeResult;
        console.log(`Operation: ${writeData.operation}`);
        console.log(`Path: ${writeData.path}`);
        console.log(`Success: ${writeData.successful}`);
        console.log(`Details: ${writeData.details}`);
        results["write_file"] = { success: writeData.successful, data: writeData };
    }
    catch (err) {
        console.error("Error testing write_file:", err);
        results["write_file"] = { success: false, error: String(err) };
    }
}
// Added: Test additional file operations
async function testFileOperations(results) {
    var _a, _b, _c;
    try {
        console.log("\nTesting additional file operations...");
        // Test make_directory
        console.log("Testing make_directory...");
        const testDir = "/sdcard/test_directory";
        const mkdirResult = await toolCall("make_directory", {
            path: testDir
        });
        // Type assertion
        const mkdirData = mkdirResult;
        console.log(`Operation: ${mkdirData.operation}`);
        console.log(`Path: ${mkdirData.path}`);
        console.log(`Success: ${mkdirData.successful}`);
        // Test copy_file (if write_file was successful)
        if ((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success) {
            console.log("\nTesting copy_file...");
            const sourcePath = "/sdcard/test_file.txt";
            const destPath = "/sdcard/test_directory/copied_file.txt";
            const copyResult = await toolCall("copy_file", {
                source: sourcePath,
                destination: destPath
            });
            // Type assertion
            const copyData = copyResult;
            console.log(`Operation: ${copyData.operation}`);
            console.log(`Path: ${copyData.path}`);
            console.log(`Success: ${copyData.successful}`);
            console.log(`Details: ${copyData.details}`);
            results["copy_file"] = { success: copyData.successful, data: copyData };
        }
        // Test move_file (if copy_file was successful)
        if ((_b = results["copy_file"]) === null || _b === void 0 ? void 0 : _b.success) {
            console.log("\nTesting move_file...");
            const sourcePath = "/sdcard/test_directory/copied_file.txt";
            const destPath = "/sdcard/test_directory/moved_file.txt";
            const moveResult = await toolCall("move_file", {
                source: sourcePath,
                destination: destPath
            });
            // Type assertion
            const moveData = moveResult;
            console.log(`Operation: ${moveData.operation}`);
            console.log(`Path: ${moveData.path}`);
            console.log(`Success: ${moveData.successful}`);
            console.log(`Details: ${moveData.details}`);
            results["move_file"] = { success: moveData.successful, data: moveData };
        }
        // Test delete_file (if we've created test files)
        if ((_c = results["write_file"]) === null || _c === void 0 ? void 0 : _c.success) {
            console.log("\nTesting delete_file...");
            const filePath = "/sdcard/test_file.txt";
            const deleteResult = await toolCall("delete_file", {
                path: filePath
            });
            // Type assertion
            const deleteData = deleteResult;
            console.log(`Operation: ${deleteData.operation}`);
            console.log(`Path: ${deleteData.path}`);
            console.log(`Success: ${deleteData.successful}`);
            console.log(`Details: ${deleteData.details}`);
            results["delete_file"] = { success: deleteData.successful, data: deleteData };
        }
        results["file_operations"] = { success: true };
    }
    catch (err) {
        console.error("Error testing file operations:", err);
        results["file_operations"] = { success: false, error: String(err) };
    }
}
// Added: Test find files
async function testFindFiles(results) {
    try {
        console.log("\nTesting find_files...");
        const findResult = await toolCall("find_files", {
            path: "/sdcard",
            pattern: "*.txt",
            max_depth: 2,
            use_path_pattern: false,
            case_insensitive: false
        });
        // 直接处理 FindFilesResultData 类型
        console.log(`Search path: ${findResult.path}`);
        console.log(`Pattern: ${findResult.pattern}`);
        console.log(`Found ${findResult.files.length} files matching pattern`);
        // Show a few results if any found
        if (findResult.files.length > 0) {
            console.log("\nSample files found:");
            findResult.files.slice(0, 3).forEach(file => {
                console.log(`- ${file}`);
            });
        }
        results["find_files"] = { success: true, data: findResult };
    }
    catch (err) {
        console.error("Error testing find_files:", err);
        results["find_files"] = { success: false, error: String(err) };
    }
}
// Added: Test fetch web page
async function testFetchWebPage(results) {
    var _a, _b;
    try {
        console.log("\nTesting fetch_web_page...");
        const pageResult = await toolCall("fetch_web_page", {
            url: "https://example.com"
        });
        // Type assertion
        const pageData = pageResult;
        console.log(`URL: ${pageData.url}`);
        console.log(`Title: ${pageData.title}`);
        console.log(`Content type: ${pageData.contentType}`);
        console.log(`Size: ${pageData.size} bytes`);
        console.log(`Links: ${((_a = pageData.links) === null || _a === void 0 ? void 0 : _a.length) || 0}`);
        // Show a sample of links if available
        if (pageData.links && pageData.links.length > 0) {
            console.log("\nSample links:");
            pageData.links.slice(0, 3).forEach(link => {
                console.log(`- ${link.text}: ${link.url}`);
            });
        }
        // Show content preview
        console.log("\nContent preview:");
        console.log(((_b = pageData.textContent) === null || _b === void 0 ? void 0 : _b.substring(0, 200)) + "...");
        results["fetch_web_page"] = { success: true, data: pageData };
    }
    catch (err) {
        console.error("Error testing fetch_web_page:", err);
        results["fetch_web_page"] = { success: false, error: String(err) };
    }
}
// Added: Test sleep function
async function testSleep(results) {
    try {
        console.log("\nTesting sleep...");
        console.log("Sleeping for 1 second...");
        const sleepResult = await toolCall("sleep", {
            duration_ms: 1000
        });
        // Type assertion
        const sleepData = sleepResult;
        console.log(`Requested sleep: ${sleepData.requestedMs}ms`);
        console.log(`Actual sleep: ${sleepData.sleptMs}ms`);
        results["sleep"] = { success: true, data: sleepData };
    }
    catch (err) {
        console.error("Error testing sleep:", err);
        results["sleep"] = { success: false, error: String(err) };
    }
}
// Added: Test app operations
async function testAppOperations(results) {
    try {
        console.log("\nTesting app operations...");
        // Test start_app
        console.log("Testing start_app...");
        const startResult = await toolCall("start_app", {
            packageName: "com.android.settings"
        });
        // Type assertion
        const startData = startResult;
        console.log(`Operation type: ${startData.operationType}`);
        console.log(`Package name: ${startData.packageName}`);
        console.log(`Success: ${startData.success}`);
        console.log(`Details: ${startData.details}`);
        // Wait a moment
        await toolCall("sleep", {
            duration_ms: 2000
        });
        // Test stop_app
        console.log("\nTesting stop_app...");
        const stopResult = await toolCall("stop_app", {
            packageName: "com.android.settings"
        });
        // Type assertion
        const stopData = stopResult;
        console.log(`Operation type: ${stopData.operationType}`);
        console.log(`Package name: ${stopData.packageName}`);
        console.log(`Success: ${stopData.success}`);
        console.log(`Details: ${stopData.details}`);
        results["app_operations"] = { success: true };
    }
    catch (err) {
        console.error("Error testing app operations:", err);
        results["app_operations"] = { success: false, error: String(err) };
    }
}
// Added: Test calculator
async function testCalculator(results) {
    try {
        console.log("\nTesting calculate...");
        const calcResult = await toolCall("calculate", {
            expression: "2 + 2 * 3"
        });
        // Type assertion
        const calcData = calcResult;
        console.log(`Expression: ${calcData.expression}`);
        console.log(`Result: ${calcData.result}`);
        console.log(`Formatted result: ${calcData.formattedResult}`);
        console.log(`Variables: ${JSON.stringify(calcData.variables)}`);
        results["calculate"] = { success: true, data: calcData };
    }
    catch (err) {
        console.error("Error testing calculate:", err);
        results["calculate"] = { success: false, error: String(err) };
    }
}
// Added: Test date calculation
async function testDateCalc(results) {
    try {
        console.log("\nTesting date_calc...");
        const dateResult = await toolCall("date_calc", {
            date: "today",
            operation: "add",
            value: 7,
            unit: "days"
        });
        // Type assertion
        const dateData = dateResult;
        console.log(`Date: ${dateData.date}`);
        console.log(`Format: ${dateData.format}`);
        console.log(`Formatted date: ${dateData.formattedDate}`);
        results["date_calc"] = { success: true, data: dateData };
    }
    catch (err) {
        console.error("Error testing date_calc:", err);
        results["date_calc"] = { success: false, error: String(err) };
    }
}
// Added: Test calculator toString
async function testCalculatorToString(results) {
    try {
        console.log("\nTesting CalculationResultData.toString()...");
        const calcResult = await toolCall("calculate", {
            expression: "1 + 1"
        });
        // Type assertion
        const calcData = calcResult;
        console.log("CalculationResultData.toString() result:");
        console.log("-".repeat(40));
        console.log(calcData.toString());
        console.log("-".repeat(40));
        results["calculator_tostring_test"] = { success: true };
    }
    catch (err) {
        console.error("Error testing Calculator toString methods:", err);
        results["calculator_tostring_test"] = { success: false, error: String(err) };
    }
}
// Added: Test use package
async function testUsePackage(results) {
    try {
        console.log("\nTesting use_package...");
        const packageResult = await toolCall("use_package", {
            packageName: "example_package"
        });
        console.log(`Package result: ${packageResult}`);
        results["use_package"] = { success: true, data: packageResult };
    }
    catch (err) {
        console.error("Error testing use_package:", err);
        results["use_package"] = { success: false, error: String(err) };
    }
}
// Added: Test query problem library
async function testQueryProblemLibrary(results) {
    try {
        console.log("\nTesting query_problem_library...");
        const queryResult = await toolCall("query_problem_library", {
            query: "android ui testing"
        });
        console.log(`Query result: ${queryResult}`);
        results["query_problem_library"] = { success: true, data: queryResult };
    }
    catch (err) {
        console.error("Error testing query_problem_library:", err);
        results["query_problem_library"] = { success: false, error: String(err) };
    }
}

/*
METADATA
{
    "name": "daily_life",
    "description": "Tools for daily life activities like getting date, device info, and UI interactions",
    "tools": [
        {
            "name": "get_current_date",
            "description": "Get the current date and time in various formats",
            "parameters": [
                {
                    "name": "format",
                    "description": "Date format ('short', 'medium', 'long', or custom pattern)",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "device_status",
            "description": "Get device status information including battery and memory",
            "parameters": []
        },
        {
            "name": "launch_app",
            "description": "Launch an app by package name",
            "parameters": [
                {
                    "name": "package_name",
                    "description": "Package name of the app to launch",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "get_installed_apps",
            "description": "Get a list of installed apps",
            "parameters": [
                {
                    "name": "include_system",
                    "description": "Whether to include system apps",
                    "type": "boolean",
                    "required": false
                }
            ]
        },
        {
            "name": "search_web",
            "description": "Search the web for information",
            "parameters": [
                {
                    "name": "query",
                    "description": "Search query",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "send_sms",
            "description": "Send SMS message using messaging app UI automation",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "Recipient phone number",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "Message content to send",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "make_call",
            "description": "Make a phone call using dialer app UI automation",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "Phone number to call",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "set_alarm",
            "description": "Set an alarm using clock app UI automation",
            "parameters": [
                {
                    "name": "hour",
                    "description": "Hour for the alarm (0-23)",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "minute",
                    "description": "Minute for the alarm (0-59)",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "label",
                    "description": "Optional label for the alarm",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "dailymain",
            "description": "Main function for testing all daily life functions without parameters",
            "parameters": []
        }
    ],
    "category": "SYSTEM"
}
*/
/**
 * Get the current date and time in various formats
 * @param params - Optional parameters including format
 */
async function get_current_date(params) {
    try {
        const format = params.format || 'medium';
        const now = new Date();
        let formattedDate;
        switch (format) {
            case 'short':
                formattedDate = now.toLocaleDateString();
                break;
            case 'long':
                formattedDate = now.toLocaleDateString(undefined, {
                    weekday: 'long',
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit'
                });
                break;
            case 'medium':
            default:
                formattedDate = now.toLocaleDateString(undefined, {
                    year: 'numeric',
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                });
                break;
        }
        const result = {
            timestamp: now.getTime(),
            iso: now.toISOString(),
            formatted: formattedDate,
            date: {
                year: now.getFullYear(),
                month: now.getMonth() + 1,
                day: now.getDate(),
                weekday: now.toLocaleDateString(undefined, { weekday: 'long' })
            },
            time: {
                hours: now.getHours(),
                minutes: now.getMinutes(),
                seconds: now.getSeconds()
            }
        };
        complete(result);
    }
    catch (error) {
        complete({ error: `Failed to get date: ${error.message}` });
    }
}
/**
 * Get device status information
 */
async function device_status() {
    try {
        // Get device information using the System tool
        const deviceInfo = await Tools.System.getDeviceInfo();
        complete({
            battery: {
                level: deviceInfo.batteryLevel,
                charging: deviceInfo.batteryCharging
            },
            memory: {
                total: deviceInfo.totalMemory,
                available: deviceInfo.availableMemory
            },
            storage: {
                total: deviceInfo.totalStorage,
                available: deviceInfo.availableStorage
            },
            device: {
                model: deviceInfo.model,
                manufacturer: deviceInfo.manufacturer,
                androidVersion: deviceInfo.androidVersion
            },
            network: deviceInfo.networkType
        });
    }
    catch (error) {
        complete({ error: `Failed to get device status: ${error.message}` });
    }
}
/**
 * Launch an app by package name
 * @param params - Parameters with package_name
 */
async function launch_app(params) {
    try {
        if (!params.package_name) {
            throw new Error("Package name is required");
        }
        const result = await Tools.System.startApp(params.package_name);
        complete({
            success: result.success,
            message: result.success ?
                `Successfully launched ${params.package_name}` :
                `Failed to launch ${params.package_name}`,
            details: result.details
        });
    }
    catch (error) {
        complete({
            success: false,
            message: `Failed to launch app: ${error.message}`
        });
    }
}
/**
 * Get a list of installed apps
 * @param params - Optional parameters including include_system
 */
async function get_installed_apps(params) {
    try {
        const includeSystem = params.include_system || false;
        const result = await Tools.System.listApps(includeSystem);
        complete({
            success: true,
            total_apps: result.packages.length,
            apps: result.packages
        });
    }
    catch (error) {
        complete({
            success: false,
            message: `Failed to get installed apps: ${error.message}`
        });
    }
}
/**
 * Search the web for information
 * @param params - Parameters with search query
 */
async function search_web(params) {
    try {
        if (!params.query) {
            throw new Error("Search query is required");
        }
        const result = await Tools.Net.search(params.query);
        complete({
            success: true,
            query: params.query,
            results: result.results.map(item => ({
                title: item.title,
                url: item.url,
                snippet: item.snippet
            }))
        });
    }
    catch (error) {
        complete({
            success: false,
            message: `Failed to search web: ${error.message}`
        });
    }
}
/**
 * Send SMS message using messaging app and UI automation
 * @param params - Parameters with phone_number and message
 */
async function send_sms(params) {
    try {
        if (!params.phone_number) {
            throw new Error("Phone number is required");
        }
        if (!params.message) {
            throw new Error("Message is required");
        }
        // Common package names for messaging apps (may vary by device)
        const messagingApps = [
            "com.android.mms", // Default AOSP messaging
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging" // Samsung Messages
        ];
        // Try to find an available messaging app
        let launchedApp = false;
        for (const appPackage of messagingApps) {
            try {
                const result = await Tools.System.startApp(appPackage);
                if (result.success) {
                    launchedApp = true;
                    break;
                }
            }
            catch (e) {
                // Continue to next app
            }
        }
        if (!launchedApp) {
            throw new Error("Could not launch any messaging app");
        }
        // Wait for app to load
        await Tools.System.sleep(1500);
        // Get page info to analyze UI
        const pageInfo = await Tools.UI.getPageInfo();
        const uiRoot = UINode.fromPageInfo(pageInfo);
        // Look for new message button (varies by app)
        const newMessageButton = uiRoot.find(node => {
            var _a, _b, _c, _d, _e, _f;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("New")) || ((_b = node.contentDesc) === null || _b === void 0 ? void 0 : _b.includes("Compose")) ||
                ((_c = node.text) === null || _c === void 0 ? void 0 : _c.includes("New")) || ((_d = node.text) === null || _d === void 0 ? void 0 : _d.includes("Compose")) ||
                ((_e = node.resourceId) === null || _e === void 0 ? void 0 : _e.includes("new")) || ((_f = node.resourceId) === null || _f === void 0 ? void 0 : _f.includes("compose"))) &&
                node.isClickable;
        });
        if (newMessageButton) {
            await newMessageButton.click();
            await Tools.System.sleep(1000);
        }
        // Enter recipient
        const recipientField = uiRoot.find(node => {
            var _a, _b, _c, _d;
            return (((_a = node.text) === null || _a === void 0 ? void 0 : _a.includes("To:")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("Recipient")) ||
                ((_c = node.contentDesc) === null || _c === void 0 ? void 0 : _c.includes("recipient")) || ((_d = node.resourceId) === null || _d === void 0 ? void 0 : _d.includes("recipient")));
        });
        if (recipientField) {
            await recipientField.click();
            await Tools.System.sleep(500);
            await Tools.UI.setText(params.phone_number);
            await Tools.System.sleep(500);
            await Tools.UI.pressKey("KEYCODE_TAB");
        }
        else {
            throw new Error("Could not find recipient field");
        }
        // Enter message
        const messageField = uiRoot.find(node => {
            var _a, _b, _c, _d;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("message")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("message")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("message")) || ((_d = node.className) === null || _d === void 0 ? void 0 : _d.includes("EditText")));
        });
        if (messageField) {
            await messageField.click();
            await Tools.System.sleep(500);
            await Tools.UI.setText(params.message);
        }
        else {
            throw new Error("Could not find message field");
        }
        // Find and click send button
        const sendButton = uiRoot.find(node => {
            var _a, _b, _c;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("Send")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("Send")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("send"))) && node.isClickable;
        });
        if (sendButton) {
            await sendButton.click();
            await Tools.System.sleep(1000);
            complete({
                success: true,
                message: `SMS to ${params.phone_number} was sent with message: "${params.message}"`
            });
        }
        else {
            throw new Error("Could not find send button");
        }
    }
    catch (error) {
        complete({
            success: false,
            message: `Failed to send SMS: ${error.message}`
        });
    }
}
/**
 * Make a phone call using dialer app and UI automation
 * @param params - Parameters with phone_number
 */
async function make_call(params) {
    try {
        if (!params.phone_number) {
            throw new Error("Phone number is required");
        }
        // Common package names for phone/dialer apps
        const dialerApps = [
            "com.android.dialer", // AOSP Dialer
            "com.google.android.dialer", // Google Phone
            "com.samsung.android.dialer" // Samsung Dialer
        ];
        // Try to find an available dialer app
        let launchedApp = false;
        for (const appPackage of dialerApps) {
            try {
                const result = await Tools.System.startApp(appPackage);
                if (result.success) {
                    launchedApp = true;
                    break;
                }
            }
            catch (e) {
                // Continue to next app
            }
        }
        if (!launchedApp) {
            throw new Error("Could not launch any dialer app");
        }
        // Wait for app to load
        await Tools.System.sleep(1500);
        // Get page info to analyze UI
        const pageInfo = await Tools.UI.getPageInfo();
        const uiRoot = UINode.fromPageInfo(pageInfo);
        // Look for dial pad button if needed
        const dialpadButton = uiRoot.find(node => {
            var _a, _b, _c;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("dial")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("dial")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("dial"))) && node.isClickable;
        });
        if (dialpadButton) {
            await dialpadButton.click();
            await Tools.System.sleep(1000);
        }
        // Find number input field or keypad
        // Often the UI will just have a keypad that we need to use
        // Method 1: Try to find a text field to enter the number directly
        const numberField = uiRoot.find(node => {
            var _a, _b, _c, _d;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("number")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("number")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("search")) || ((_d = node.className) === null || _d === void 0 ? void 0 : _d.includes("EditText")));
        });
        if (numberField) {
            await numberField.click();
            await Tools.System.sleep(500);
            await Tools.UI.setText(params.phone_number);
        }
        else {
            // Method 2: Use the keypad by pressing each digit
            for (const digit of params.phone_number) {
                if (digit === '+') {
                    // Find and long-press the 0 key for +
                    const zeroKey = uiRoot.find(node => {
                        return (node.text === "0" || node.contentDesc === "0") && node.isClickable;
                    });
                    if (zeroKey) {
                        // Simulate long press by tapping and waiting
                        await zeroKey.click();
                        await Tools.System.sleep(1000);
                    }
                }
                else {
                    // Find the appropriate key for each digit
                    const digitKey = uiRoot.find(node => {
                        return (node.text === digit || node.contentDesc === digit) && node.isClickable;
                    });
                    if (digitKey) {
                        await digitKey.click();
                        await Tools.System.sleep(200);
                    }
                }
            }
        }
        // Find and click call button
        const callButton = uiRoot.find(node => {
            var _a, _b, _c;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("call")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("call")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("call"))) && node.isClickable;
        });
        if (callButton) {
            await callButton.click();
            complete({
                success: true,
                message: `Phone call initiated to ${params.phone_number}`
            });
        }
        else {
            throw new Error("Could not find call button");
        }
    }
    catch (error) {
        complete({
            success: false,
            message: `Failed to make call: ${error.message}`
        });
    }
}
/**
 * Set an alarm using clock app and UI automation
 * @param params - Parameters with hour, minute and optional label
 */
async function set_alarm(params) {
    try {
        if (params.hour === undefined || params.hour < 0 || params.hour > 23) {
            throw new Error("Valid hour (0-23) is required");
        }
        if (params.minute === undefined || params.minute < 0 || params.minute > 59) {
            throw new Error("Valid minute (0-59) is required");
        }
        // Format time for display
        const formattedHour = params.hour.toString().padStart(2, '0');
        const formattedMinute = params.minute.toString().padStart(2, '0');
        const formattedTime = `${formattedHour}:${formattedMinute}`;
        // Common package names for clock apps
        const clockApps = [
            "com.google.android.deskclock", // Google Clock
            "com.android.deskclock", // AOSP Clock
            "com.sec.android.app.clockpackage" // Samsung Clock
        ];
        // Try to find an available clock app
        let launchedApp = false;
        for (const appPackage of clockApps) {
            try {
                const result = await Tools.System.startApp(appPackage);
                if (result.success) {
                    launchedApp = true;
                    break;
                }
            }
            catch (e) {
                // Continue to next app
            }
        }
        if (!launchedApp) {
            throw new Error("Could not launch any clock app");
        }
        // Wait for app to load
        await Tools.System.sleep(1500);
        // Get page info to analyze UI
        const pageInfo = await Tools.UI.getPageInfo();
        const uiRoot = UINode.fromPageInfo(pageInfo);
        // Navigate to alarm tab if needed
        const alarmTab = uiRoot.find(node => {
            var _a, _b, _c;
            return (((_a = node.text) === null || _a === void 0 ? void 0 : _a.includes("Alarm")) || ((_b = node.contentDesc) === null || _b === void 0 ? void 0 : _b.includes("Alarm")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("alarm"))) && node.isClickable;
        });
        if (alarmTab) {
            await alarmTab.click();
            await Tools.System.sleep(1000);
        }
        // Find add alarm button
        const addButton = uiRoot.find(node => {
            var _a, _b, _c, _d;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("Add")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("Add")) ||
                ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("add")) || ((_d = node.contentDesc) === null || _d === void 0 ? void 0 : _d.includes("+")) ||
                node.text === "+") && node.isClickable;
        });
        if (addButton) {
            await addButton.click();
            await Tools.System.sleep(1000);
        }
        else {
            throw new Error("Could not find add alarm button");
        }
        // Set the time - UI implementation varies widely between apps
        // We'll try different approaches
        // Approach 1: Look for hour and minute input fields
        const hourField = uiRoot.find(node => {
            var _a, _b;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("hour")) || ((_b = node.resourceId) === null || _b === void 0 ? void 0 : _b.includes("hour")));
        });
        const minuteField = uiRoot.find(node => {
            var _a, _b;
            return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("minute")) || ((_b = node.resourceId) === null || _b === void 0 ? void 0 : _b.includes("minute")));
        });
        if (hourField && minuteField) {
            await hourField.click();
            await Tools.System.sleep(500);
            await Tools.UI.setText(params.hour.toString());
            await minuteField.click();
            await Tools.System.sleep(500);
            await Tools.UI.setText(params.minute.toString());
        }
        else {
            // Approach 2: Try to find a time picker
            const timePicker = uiRoot.find(node => {
                var _a, _b;
                return ((_a = node.className) === null || _a === void 0 ? void 0 : _a.includes("TimePicker")) ||
                    ((_b = node.resourceId) === null || _b === void 0 ? void 0 : _b.includes("time_picker"));
            });
            if (timePicker) {
                // This is very device and app specific, so we'll do a best effort
                await Tools.UI.setText(formattedTime);
            }
            else {
                throw new Error("Could not find time input method");
            }
        }
        // Set label if provided
        if (params.label) {
            const labelField = uiRoot.find(node => {
                var _a, _b, _c, _d;
                return (((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("label")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("Label")) ||
                    ((_c = node.resourceId) === null || _c === void 0 ? void 0 : _c.includes("label"))) &&
                    (((_d = node.className) === null || _d === void 0 ? void 0 : _d.includes("EditText")) || node.isClickable);
            });
            if (labelField) {
                await labelField.click();
                await Tools.System.sleep(500);
                await Tools.UI.setText(params.label);
            }
        }
        // Find and click save/ok button
        const saveButton = uiRoot.find(node => {
            var _a, _b, _c, _d, _e, _f;
            return ((((_a = node.contentDesc) === null || _a === void 0 ? void 0 : _a.includes("Save")) || ((_b = node.text) === null || _b === void 0 ? void 0 : _b.includes("Save")) ||
                ((_c = node.contentDesc) === null || _c === void 0 ? void 0 : _c.includes("OK")) || ((_d = node.text) === null || _d === void 0 ? void 0 : _d.includes("OK")) ||
                ((_e = node.resourceId) === null || _e === void 0 ? void 0 : _e.includes("save")) || ((_f = node.resourceId) === null || _f === void 0 ? void 0 : _f.includes("ok"))) &&
                node.isClickable);
        });
        if (saveButton) {
            await saveButton.click();
            await Tools.System.sleep(1000);
            complete({
                success: true,
                message: `Alarm set for ${formattedTime}${params.label ? ' with label: ' + params.label : ''}`
            });
        }
        else {
            throw new Error("Could not find save button");
        }
    }
    catch (error) {
        complete({
            success: false,
            message: `Failed to set alarm: ${error.message}`
        });
    }
}
/**
 * Main function to demonstrate and test all daily life functionality
 * No parameters needed - runs a sequence of operations with predefined values
 */
async function dailymain() {
    try {
        // Store results to return a summary at the end
        const results = {};
        // Get current date and time
        try {
            const now = new Date();
            results.date = {
                current: now.toLocaleString(),
                timestamp: now.getTime(),
                formatted: now.toLocaleDateString(undefined, {
                    weekday: 'long',
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                })
            };
        }
        catch (error) {
            results.date = { error: error.message };
        }
        // Get device information (battery, memory, etc.)
        try {
            const deviceInfo = await Tools.System.getDeviceInfo();
            results.device = {
                model: deviceInfo.model,
                manufacturer: deviceInfo.manufacturer,
                androidVersion: deviceInfo.androidVersion,
                battery: {
                    level: deviceInfo.batteryLevel,
                    charging: deviceInfo.batteryCharging
                },
                network: deviceInfo.networkType
            };
        }
        catch (error) {
            results.device = { error: error.message };
        }
        // List some installed apps (limit to 5 for demonstration)
        try {
            // We'll only check for non-system apps
            const appsResult = await Tools.System.listApps(false);
            const appsList = appsResult.packages.slice(0, 5); // Get only first 5 for demo
            results.apps = {
                total: appsResult.packages.length,
                sample: appsList
            };
        }
        catch (error) {
            results.apps = { error: error.message };
        }
        // SIMULATION ONLY - Don't actually send SMS
        // Just show what would be sent
        results.sms_simulation = {
            action: "Simulated SMS",
            recipient: "10000", // Example recipient number
            message: "This is a test message from daily_life package",
            note: "This is just a simulation, no actual SMS was sent"
        };
        // SIMULATION ONLY - Don't actually make a call
        // Just show what call would be made
        results.call_simulation = {
            action: "Simulated Call",
            number: "10010", // Example phone number
            note: "This is just a simulation, no actual call was made"
        };
        // SIMULATION ONLY - Don't actually set an alarm
        // Just show what alarm would be set
        const alarmHour = new Date().getHours();
        const alarmMinute = (new Date().getMinutes() + 5) % 60; // Current time + 5 minutes
        results.alarm_simulation = {
            action: "Simulated Alarm",
            time: `${alarmHour.toString().padStart(2, '0')}:${alarmMinute.toString().padStart(2, '0')}`,
            label: "Test Alarm",
            note: "This is just a simulation, no actual alarm was set"
        };
        // Web search simulation
        results.search_simulation = {
            action: "Simulated Search",
            query: "latest tech news",
            note: "This is just a simulation, no actual search was performed"
        };
        // Compile all results together with timestamp
        const summary = {
            timestamp: new Date().toISOString(),
            message: "Daily Life functionality test completed successfully",
            results: results,
            note: "This is a test function. To use actual functionality, call the specific functions with appropriate parameters."
        };
        complete(summary);
    }
    catch (error) {
        complete({
            success: false,
            message: `Test failed: ${error.message}`,
            timestamp: new Date().toISOString()
        });
    }
}
// Export the functions
exports.get_current_date = get_current_date;
exports.device_status = device_status;
exports.launch_app = launch_app;
exports.get_installed_apps = get_installed_apps;
exports.search_web = search_web;
exports.send_sms = send_sms;
exports.make_call = make_call;
exports.set_alarm = set_alarm;
exports.dailymain = dailymain;

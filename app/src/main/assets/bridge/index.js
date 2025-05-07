"use strict";
/**
 * MCP TCP Bridge
 *
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const net = __importStar(require("net"));
const child_process_1 = require("child_process");
const uuid_1 = require("uuid");
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
/**
 * MCP Bridge class
 */
class McpBridge {
    constructor(config = {}) {
        this.server = null;
        this.mcpProcess = null;
        this.mcpName = "default";
        this.mcpTools = [];
        this.isReady = false;
        // Service registry
        this.serviceRegistry = new Map();
        // Active connections
        this.activeConnections = new Set();
        // Request tracking
        this.pendingRequests = new Map();
        this.toolResponseMapping = new Map();
        // Request timeout (ms)
        this.REQUEST_TIMEOUT = 60000; // 60 seconds timeout
        // Last MCP error
        this.lastMcpError = null;
        // Default configuration
        this.config = {
            port: 8765,
            host: '127.0.0.1',
            mcpCommand: 'node',
            mcpArgs: ['../your-mcp-server.js'],
            ...config
        };
        // Set registry path
        this.registryPath = this.config.registryPath || './mcp-registry.json';
        // Load registry
        this.loadRegistry();
        // Set timeout check
        setInterval(() => this.checkRequestTimeouts(), 5000);
    }
    /**
     * Load MCP service registry
     */
    loadRegistry() {
        try {
            if (fs.existsSync(this.registryPath)) {
                const data = fs.readFileSync(this.registryPath, 'utf8');
                const registry = JSON.parse(data);
                this.serviceRegistry.clear();
                for (const [key, value] of Object.entries(registry)) {
                    this.serviceRegistry.set(key, value);
                }
                console.log(`Loaded ${this.serviceRegistry.size} MCP service configurations`);
            }
            else {
                console.log('Registry file not found, creating new registry');
                this.saveRegistry();
            }
        }
        catch (e) {
            console.error(`Failed to load registry: ${e}`);
            this.serviceRegistry.clear();
        }
    }
    /**
     * Save MCP service registry
     */
    saveRegistry() {
        try {
            const registry = {};
            for (const [key, value] of this.serviceRegistry.entries()) {
                registry[key] = value;
            }
            const dir = path.dirname(this.registryPath);
            if (!fs.existsSync(dir)) {
                fs.mkdirSync(dir, { recursive: true });
            }
            fs.writeFileSync(this.registryPath, JSON.stringify(registry, null, 2), 'utf8');
        }
        catch (e) {
            console.error(`Failed to save registry: ${e}`);
        }
    }
    /**
     * Register a new MCP service
     */
    registerService(name, info) {
        if (!name || !info.command) {
            return false;
        }
        const serviceInfo = {
            name,
            command: info.command,
            args: info.args || [],
            description: info.description || `MCP Service: ${name}`,
            env: info.env || {},
            created: Date.now(),
            lastUsed: undefined
        };
        this.serviceRegistry.set(name, serviceInfo);
        this.saveRegistry();
        return true;
    }
    /**
     * Unregister an MCP service
     */
    unregisterService(name) {
        if (!this.serviceRegistry.has(name)) {
            return false;
        }
        this.serviceRegistry.delete(name);
        this.saveRegistry();
        return true;
    }
    /**
     * Get list of registered MCP services
     */
    getServiceList() {
        return Array.from(this.serviceRegistry.values());
    }
    /**
     * Start MCP child process
     */
    startMcpProcess() {
        var _a, _b;
        if (!this.config.mcpCommand) {
            console.log('No MCP command configured, skipping MCP process startup');
            return;
        }
        const args = this.config.mcpArgs || [];
        console.log(`Starting MCP process: ${this.config.mcpCommand} ${args.join(' ')}`);
        try {
            this.mcpProcess = (0, child_process_1.spawn)(this.config.mcpCommand, args, {
                stdio: ['pipe', 'pipe', 'pipe'],
                env: {
                    ...process.env,
                    ...this.config.env
                }
            });
            // Handle stdout data
            (_a = this.mcpProcess.stdout) === null || _a === void 0 ? void 0 : _a.on('data', (data) => {
                this.handleMcpResponse(data);
            });
            // Handle stderr data
            (_b = this.mcpProcess.stderr) === null || _b === void 0 ? void 0 : _b.on('data', (data) => {
                const errorText = data.toString().trim();
                console.error(`MCP process error output: ${errorText}`);
                // Try to store the last error to send along with responses
                this.lastMcpError = errorText;
                // Check for pending requests that might need this error information
                // If we have any pending requests, send them the error as a supplementary message
                for (const [requestId, request] of this.pendingRequests.entries()) {
                    // Only send to requests that have been pending for a short time
                    // This avoids sending unrelated errors to long-pending requests
                    const pendingTimeMs = Date.now() - request.timestamp;
                    if (pendingTimeMs < 5000) { // Only for recent requests in the last 5 seconds
                        try {
                            console.log(`Forwarding MCP stderr to client for recent request ${requestId}`);
                            request.socket.write(JSON.stringify({
                                id: requestId,
                                success: false,
                                error: {
                                    code: -32603,
                                    message: `MCP process error: ${errorText}`
                                }
                            }) + '\n');
                            // Clean up
                            this.pendingRequests.delete(requestId);
                            if (request.toolCallId) {
                                this.toolResponseMapping.delete(request.toolCallId);
                            }
                        }
                        catch (writeError) {
                            console.error(`Failed to forward stderr to client: ${writeError}`);
                        }
                    }
                }
            });
            // Handle process errors
            this.mcpProcess.on('error', (error) => {
                console.error(`MCP process spawn error: ${error.message}`);
                if (error.stack) {
                    console.error(`Stack trace: ${error.stack}`);
                }
            });
            this.mcpProcess.on('close', (code) => {
                console.log(`MCP process exited with code: ${code}`);
                this.mcpProcess = null;
                this.isReady = false;
                this.mcpTools = [];
            });
            // Log process info
            if (this.mcpProcess.pid) {
                console.log(`MCP process started with PID: ${this.mcpProcess.pid}`);
            }
            else {
                console.error('MCP process started but no PID available - this may indicate an issue');
            }
            // Fetch tool list after startup
            setTimeout(() => this.fetchMcpTools(), 1000);
        }
        catch (error) {
            // Catch any exceptions during spawn
            console.error(`Failed to start MCP process: ${error instanceof Error ? error.message : String(error)}`);
            if (error instanceof Error && error.stack) {
                console.error(`Stack trace: ${error.stack}`);
            }
        }
    }
    /**
     * Get MCP tools list
     */
    fetchMcpTools() {
        if (!this.mcpProcess || this.mcpProcess.stdin.destroyed) {
            console.error('Cannot get tools list: MCP process not available');
            // Set ready to true anyway, with no tools so the service can still function
            this.isReady = true;
            this.mcpTools = [];
            console.log(`MCP service ${this.mcpName} is now ready with no tools (MCP process unavailable).`);
            return;
        }
        console.log('Fetching tools list from MCP process...');
        try {
            // Create tools/list request with correct MCP method name
            const toolsListRequest = {
                jsonrpc: '2.0',
                id: `init_${Date.now()}`,
                method: 'tools/list', // Correct MCP method name according to spec
                params: {}
            };
            // Log the request details
            console.log(`Sending tools/list request: ${JSON.stringify(toolsListRequest)}`);
            // Send request and check for errors
            const writeResult = this.mcpProcess.stdin.write(JSON.stringify(toolsListRequest) + '\n');
            if (!writeResult) {
                console.error('Failed to write tools list request to MCP process stdin - backpressure detected');
                // Still set ready to true to avoid hanging
                this.isReady = true;
                this.mcpTools = [];
                console.log(`MCP service ${this.mcpName} is now ready with no tools (stdin backpressure).`);
            }
        }
        catch (error) {
            console.error(`Error fetching MCP tools: ${error instanceof Error ? error.message : String(error)}`);
            if (error instanceof Error && error.stack) {
                console.error(`Stack trace: ${error.stack}`);
            }
            // Set ready to true with no tools so service can function without tools
            this.isReady = true;
            this.mcpTools = [];
            console.log(`MCP service ${this.mcpName} is now ready with no tools due to error during fetch.`);
        }
    }
    /**
     * Handle MCP response data
     */
    handleMcpResponse(data) {
        try {
            const responseText = data.toString().trim();
            if (!responseText)
                return;
            // Log raw response for debugging
            console.log(`Raw MCP response: ${responseText}`);
            try {
                const response = JSON.parse(responseText);
                // Log parsed response structure
                console.log(`Parsed MCP response ID: ${response.id || 'none'}, has error: ${!!response.error}, has result: ${!!response.result}`);
                // Check if it's a tool call response
                if (response.id && this.toolResponseMapping.has(response.id)) {
                    const bridgeRequestId = this.toolResponseMapping.get(response.id);
                    const pendingRequest = this.pendingRequests.get(bridgeRequestId);
                    if (pendingRequest) {
                        console.log(`Forwarding response for tool call ${response.id} to bridge request ${bridgeRequestId}`);
                        // Forward response to client
                        pendingRequest.socket.write(JSON.stringify({
                            id: bridgeRequestId,
                            success: !response.error,
                            result: response.result,
                            error: response.error
                        }) + '\n');
                        // Clean up
                        this.pendingRequests.delete(bridgeRequestId);
                        this.toolResponseMapping.delete(response.id);
                    }
                    else {
                        console.error(`Missing pending request for tool call ${response.id} (bridge ID: ${bridgeRequestId})`);
                    }
                }
                else if (response.id && typeof response.id === 'string' && response.id.startsWith('init_')) {
                    // Handle tools list response
                    console.log(`Received tools list response: ${JSON.stringify(response)}`);
                    // Check if this is a method not found error
                    if (response.error && response.error.code === -32601) {
                        console.error(`MCP method not recognized: ${JSON.stringify(response.error)}`);
                        console.log('This MCP server may not support the tools feature or is using a different protocol version.');
                        // Set ready to true anyway, just with no tools
                        this.isReady = true;
                        this.mcpTools = [];
                        console.log(`MCP service ${this.mcpName} is now ready with no tools.`);
                        return;
                    }
                    if (response.error) {
                        console.error(`Tools list request error: ${JSON.stringify(response.error)}`);
                        // Still set isReady to true, with empty tools list, so we don't hang
                        this.isReady = true;
                        this.mcpTools = [];
                        console.log(`MCP service ${this.mcpName} is now ready with no tools due to error.`);
                        return;
                    }
                    // Extract tools from the response based on MCP spec format
                    // MCP response format: { "jsonrpc": "2.0", "id": 123, "result": { "tools": [...] } }
                    if (response.result && response.result.tools && Array.isArray(response.result.tools)) {
                        this.mcpTools = response.result.tools;
                        console.log(`Loaded ${this.mcpTools.length} tools according to MCP spec format`);
                    }
                    else if (response.result && Array.isArray(response.result)) {
                        // Legacy or non-standard format: direct array
                        this.mcpTools = response.result;
                        console.log(`Loaded ${this.mcpTools.length} tools from direct result array`);
                    }
                    else if (response.result) {
                        // Try to extract tools in other formats
                        console.log(`Trying to extract tools from non-standard format: ${JSON.stringify(response.result)}`);
                        // Option 1: Maybe it's an object with tool objects as properties
                        let extractedTools = [];
                        if (typeof response.result === 'object') {
                            // Try to extract tools from object properties
                            for (const key in response.result) {
                                if (key !== 'tools' && typeof response.result[key] === 'object') {
                                    extractedTools.push({
                                        ...response.result[key],
                                        name: response.result[key].name || key
                                    });
                                }
                            }
                        }
                        if (extractedTools.length > 0) {
                            this.mcpTools = extractedTools;
                            console.log(`Extracted ${this.mcpTools.length} tools from object properties`);
                        }
                        else {
                            console.error(`Could not extract tools from response format: ${JSON.stringify(response.result)}`);
                            this.mcpTools = [];
                        }
                    }
                    else {
                        console.error(`Invalid tools list format in response: ${JSON.stringify(response.result)}`);
                        this.mcpTools = [];
                    }
                    this.isReady = true;
                    console.log(`MCP service ${this.mcpName} is now ready with ${this.mcpTools.length} tools`);
                }
                else {
                    // This could be an unmatched response or error message that doesn't match our expectations
                    // Log it, but don't crash the service
                    console.log(`Unmatched MCP response: ${responseText}`);
                    // Try to find any pending requests that might be waiting for this response
                    // by matching part of the response with the tool call ID
                    if (response.id) {
                        // Look for partial matches in toolResponseMapping
                        for (const [toolCallId, bridgeRequestId] of this.toolResponseMapping.entries()) {
                            if (toolCallId.includes(response.id) || response.id.includes(toolCallId)) {
                                console.log(`Found potential match for unmatched response: toolCallId=${toolCallId}, bridgeRequestId=${bridgeRequestId}`);
                                const pendingRequest = this.pendingRequests.get(bridgeRequestId);
                                if (pendingRequest) {
                                    console.log(`Forwarding unmatched response to client for request ${bridgeRequestId}`);
                                    pendingRequest.socket.write(JSON.stringify({
                                        id: bridgeRequestId,
                                        success: !response.error,
                                        result: response.result,
                                        error: response.error || {
                                            code: -32603,
                                            message: 'Unmatched MCP response'
                                        }
                                    }) + '\n');
                                    // Clean up
                                    this.pendingRequests.delete(bridgeRequestId);
                                    this.toolResponseMapping.delete(toolCallId);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            catch (parseError) {
                console.error(`Error parsing MCP response JSON: ${parseError instanceof Error ? parseError.message : String(parseError)}`);
                console.error(`Invalid JSON: ${responseText}`);
                // Try to find any pending requests and return the error
                if (this.pendingRequests.size > 0) {
                    // Just return the error to the oldest pending request
                    const oldestRequest = Array.from(this.pendingRequests.entries())
                        .sort((a, b) => a[1].timestamp - b[1].timestamp)[0];
                    if (oldestRequest) {
                        const [requestId, request] = oldestRequest;
                        console.log(`Forwarding JSON parse error to oldest pending request: ${requestId}`);
                        request.socket.write(JSON.stringify({
                            id: requestId,
                            success: false,
                            error: {
                                code: -32700,
                                message: `Invalid JSON response from MCP server: ${parseError instanceof Error ? parseError.message : String(parseError)}`,
                                data: responseText.substring(0, 100) + (responseText.length > 100 ? '...' : '')
                            }
                        }) + '\n');
                        // Clean up
                        this.pendingRequests.delete(requestId);
                        if (request.toolCallId) {
                            this.toolResponseMapping.delete(request.toolCallId);
                        }
                    }
                }
            }
        }
        catch (e) {
            console.error(`Error handling MCP response: ${e instanceof Error ? e.message : String(e)}`);
            if (e instanceof Error && e.stack) {
                console.error(`Stack trace: ${e.stack}`);
            }
            // Don't crash the server, just log the error
            // Try to find any pending requests and return the error
            if (this.pendingRequests.size > 0) {
                // Send the error to all pending requests since we don't know which one it's for
                for (const [requestId, request] of this.pendingRequests.entries()) {
                    console.log(`Forwarding general error to pending request: ${requestId}`);
                    try {
                        request.socket.write(JSON.stringify({
                            id: requestId,
                            success: false,
                            error: {
                                code: -32603,
                                message: `Internal MCP server error: ${e instanceof Error ? e.message : String(e)}`,
                                data: this.lastMcpError ? { stderr: this.lastMcpError } : undefined
                            }
                        }) + '\n');
                    }
                    catch (writeError) {
                        console.error(`Failed to write error response to socket: ${writeError}`);
                    }
                    // Clean up
                    this.pendingRequests.delete(requestId);
                    if (request.toolCallId) {
                        this.toolResponseMapping.delete(request.toolCallId);
                    }
                }
            }
        }
    }
    /**
     * Handle client MCP command
     */
    handleMcpCommand(command, socket) {
        const { id, command: cmdType, params } = command;
        let response;
        try {
            switch (cmdType) {
                case 'ping':
                    // Health check
                    const serviceName = (params === null || params === void 0 ? void 0 : params.serviceName) || (params === null || params === void 0 ? void 0 : params.name);
                    if (serviceName) {
                        if (this.serviceRegistry.has(serviceName)) {
                            const serviceInfo = this.serviceRegistry.get(serviceName);
                            if (this.mcpName === serviceName) {
                                response = {
                                    id,
                                    success: true,
                                    result: {
                                        status: "ok",
                                        mcpName: serviceName,
                                        name: serviceName,
                                        description: serviceInfo === null || serviceInfo === void 0 ? void 0 : serviceInfo.description,
                                        timestamp: Date.now(),
                                        ready: this.isReady
                                    }
                                };
                                // Update last used time
                                if (serviceInfo) {
                                    serviceInfo.lastUsed = Date.now();
                                    this.saveRegistry();
                                }
                            }
                            else {
                                response = {
                                    id,
                                    success: true,
                                    result: {
                                        status: "registered_but_not_active",
                                        mcpName: this.mcpName,
                                        name: serviceName,
                                        description: serviceInfo === null || serviceInfo === void 0 ? void 0 : serviceInfo.description,
                                        timestamp: Date.now()
                                    }
                                };
                            }
                        }
                        else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32601,
                                    message: `Service '${serviceName}' not registered`
                                }
                            };
                        }
                    }
                    else {
                        // Generic ping
                        response = {
                            id,
                            success: true,
                            result: {
                                timestamp: Date.now(),
                                status: 'ok',
                                mcpName: this.mcpName,
                                ready: this.isReady
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'status':
                    // Bridge status
                    response = {
                        id,
                        success: true,
                        result: {
                            ready: this.isReady,
                            mcpName: this.mcpName,
                            processRunning: !!this.mcpProcess && !this.mcpProcess.stdin.destroyed,
                            pendingRequests: this.pendingRequests.size,
                            activeConnections: this.activeConnections.size
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'listtools':
                    // Available tools list
                    if (!this.mcpProcess || this.mcpProcess.stdin.destroyed) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32603,
                                message: 'MCP server not available'
                            }
                        };
                    }
                    else {
                        response = {
                            id,
                            success: true,
                            result: {
                                tools: this.mcpTools
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'list':
                    // List registered MCP services
                    try {
                        const services = this.getServiceList();
                        response = {
                            id,
                            success: true,
                            result: {
                                services
                            }
                        };
                    }
                    catch (error) {
                        console.error(`Error getting service list: ${error instanceof Error ? error.message : String(error)}`);
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32603,
                                message: `Error retrieving service list: ${error instanceof Error ? error.message : String(error)}`
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'register':
                    // Register new MCP service
                    if (!params || !params.name || !params.command) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameters: name, command"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const registered = this.registerService(params.name, {
                        command: params.command,
                        args: params.args || [],
                        description: params.description,
                        env: params.env
                    });
                    response = {
                        id,
                        success: registered,
                        result: registered ? {
                            status: 'registered',
                            name: params.name
                        } : undefined,
                        error: !registered ? {
                            code: -32602,
                            message: "Failed to register service"
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'unregister':
                    // Unregister MCP service
                    if (!params || !params.name) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const unregistered = this.unregisterService(params.name);
                    response = {
                        id,
                        success: unregistered,
                        result: unregistered ? {
                            status: 'unregistered',
                            name: params.name
                        } : undefined,
                        error: !unregistered ? {
                            code: -32602,
                            message: `Service '${params.name}' does not exist`
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'toolcall':
                    // Tool call
                    if (!params || !params.method) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: method"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    this.handleToolCall(command, socket);
                    break;
                case 'spawn':
                    // Start new MCP service
                    if (!params || !params.command) {
                        // Try to load by name from registry
                        if (params && params.name && this.serviceRegistry.has(params.name)) {
                            const serviceInfo = this.serviceRegistry.get(params.name);
                            params.command = serviceInfo.command;
                            params.args = serviceInfo.args;
                            params.description = serviceInfo.description;
                            if (!params.env && serviceInfo.env) {
                                params.env = serviceInfo.env;
                            }
                        }
                        else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32602,
                                    message: "Missing required parameter: command or valid name"
                                }
                            };
                            socket.write(JSON.stringify(response) + '\n');
                            break;
                        }
                    }
                    // Kill existing process if any
                    if (this.mcpProcess) {
                        this.mcpProcess.kill();
                    }
                    // Update config
                    this.config.mcpCommand = params.command;
                    this.config.mcpArgs = params.args || [];
                    this.mcpName = params.name || "custom";
                    this.config.env = params.env;
                    // Start new process
                    this.startMcpProcess();
                    response = {
                        id,
                        success: true,
                        result: {
                            status: "started",
                            command: this.config.mcpCommand,
                            args: this.config.mcpArgs,
                            name: this.mcpName
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'shutdown':
                    // Shutdown current MCP service
                    if (this.mcpProcess) {
                        this.mcpProcess.kill();
                        this.mcpProcess = null;
                    }
                    this.isReady = false;
                    response = {
                        id,
                        success: true,
                        result: {
                            status: "shutdown"
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                default:
                    // Unknown command
                    response = {
                        id,
                        success: false,
                        error: {
                            code: -32601,
                            message: `Unknown command: ${cmdType}`
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
            }
        }
        catch (error) {
            // Catch any uncaught exceptions in command handling
            console.error(`Unhandled error in command ${cmdType}: ${error instanceof Error ? error.message : String(error)}`);
            if (error instanceof Error && error.stack) {
                console.error(`Stack trace: ${error.stack}`);
            }
            // Send error to client
            const errorResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal server error: ${error instanceof Error ? error.message : String(error)}`,
                    data: this.lastMcpError ? { stderr: this.lastMcpError } : undefined
                }
            };
            socket.write(JSON.stringify(errorResponse) + '\n');
        }
    }
    /**
     * Handle tool call request
     */
    handleToolCall(command, socket) {
        const { id, params } = command;
        const { method, params: methodParams, name: requestedServiceName } = params || {};
        console.log(`Handling tool call: ${method}, service: ${requestedServiceName || this.mcpName}`);
        if (!this.mcpProcess || this.mcpProcess.stdin.destroyed) {
            console.error(`Cannot handle tool call: MCP process not available (method: ${method})`);
            const response = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: 'MCP server temporarily unavailable'
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }
        // Special handling for ping method
        if (method === 'ping' && requestedServiceName) {
            console.log(`Processing ping request for service: ${requestedServiceName}`);
            if (!this.serviceRegistry.has(requestedServiceName)) {
                console.error(`Service ${requestedServiceName} not found in registry for ping request`);
                const response = {
                    id,
                    success: false,
                    error: {
                        code: -32601,
                        message: `Service '${requestedServiceName}' not registered`
                    }
                };
                socket.write(JSON.stringify(response) + '\n');
                return;
            }
            if (this.mcpName !== requestedServiceName) {
                console.log(`Requested service ${requestedServiceName} is registered but not active (current: ${this.mcpName})`);
                const serviceInfo = this.serviceRegistry.get(requestedServiceName);
                const response = {
                    id,
                    success: true,
                    result: {
                        status: "registered_but_not_active",
                        mcpName: this.mcpName,
                        requestedName: requestedServiceName,
                        description: serviceInfo === null || serviceInfo === void 0 ? void 0 : serviceInfo.description,
                        timestamp: Date.now()
                    }
                };
                socket.write(JSON.stringify(response) + '\n');
                return;
            }
            // Update last used time
            const serviceInfo = this.serviceRegistry.get(requestedServiceName);
            if (serviceInfo) {
                serviceInfo.lastUsed = Date.now();
                this.saveRegistry();
            }
            console.log(`Service ${requestedServiceName} is active and responding to ping`);
            const response = {
                id,
                success: true,
                result: {
                    status: "ok",
                    mcpName: requestedServiceName,
                    name: requestedServiceName,
                    description: serviceInfo === null || serviceInfo === void 0 ? void 0 : serviceInfo.description,
                    timestamp: Date.now(),
                    ready: this.isReady
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }
        try {
            // Create tool call request with correct MCP method name
            const toolCallId = params.id || (0, uuid_1.v4)();
            const toolCallRequest = {
                jsonrpc: '2.0',
                id: toolCallId,
                method: 'tools/call', // Correct MCP method name according to spec
                params: {
                    name: params.method,
                    arguments: params.params || {}
                }
            };
            console.log(`Created tool call request: ID=${toolCallId}, tool=${params.method}`);
            // Record request
            this.pendingRequests.set(id, {
                id,
                socket,
                timestamp: Date.now(),
                toolCallId
            });
            // Create mapping between tool call ID and bridge request ID
            this.toolResponseMapping.set(toolCallId, id);
            // Serialize request
            const requestJson = JSON.stringify(toolCallRequest);
            console.log(`Sending tool call to MCP process: ${requestJson}`);
            // Send request to MCP server
            const writeResult = this.mcpProcess.stdin.write(requestJson + '\n');
            if (!writeResult) {
                console.error('Failed to write tool call request to MCP process stdin - backpressure detected');
                // Send backpressure error to client
                const response = {
                    id,
                    success: false,
                    error: {
                        code: -32603,
                        message: 'Failed to send request - MCP server backpressure detected'
                    }
                };
                socket.write(JSON.stringify(response) + '\n');
                // Clean up
                this.pendingRequests.delete(id);
                this.toolResponseMapping.delete(toolCallId);
            }
        }
        catch (error) {
            console.error(`Error handling tool call: ${error instanceof Error ? error.message : String(error)}`);
            if (error instanceof Error && error.stack) {
                console.error(`Stack trace: ${error.stack}`);
            }
            // Send error response
            const response = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal error: ${error instanceof Error ? error.message : String(error)}`,
                    data: this.lastMcpError ? { stderr: this.lastMcpError } : undefined
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            // Clean up any pending mappings
            if (params && params.id) {
                this.toolResponseMapping.delete(params.id);
            }
            this.pendingRequests.delete(id);
        }
    }
    /**
     * Check for request timeouts
     */
    checkRequestTimeouts() {
        const now = Date.now();
        for (const [requestId, request] of this.pendingRequests.entries()) {
            if (now - request.timestamp > this.REQUEST_TIMEOUT) {
                console.log(`Request timeout: ${requestId}`);
                // Send timeout response
                const response = {
                    id: requestId,
                    success: false,
                    error: {
                        code: -32603,
                        message: "Request timeout",
                        data: this.lastMcpError ? { stderr: this.lastMcpError } : undefined
                    }
                };
                request.socket.write(JSON.stringify(response) + '\n');
                // Clean up
                this.pendingRequests.delete(requestId);
                if (request.toolCallId) {
                    this.toolResponseMapping.delete(request.toolCallId);
                }
            }
        }
    }
    /**
     * Start TCP server
     */
    start() {
        // Start MCP process
        this.startMcpProcess();
        // Create TCP server
        this.server = net.createServer((socket) => {
            console.log(`New client connection: ${socket.remoteAddress}:${socket.remotePort}`);
            this.activeConnections.add(socket);
            // Handle data from client
            socket.on('data', (data) => {
                const message = data.toString().trim();
                try {
                    // Parse command
                    const command = JSON.parse(message);
                    // Ensure command has ID
                    if (!command.id) {
                        command.id = (0, uuid_1.v4)();
                    }
                    // Handle command
                    if (command.command) {
                        try {
                            this.handleMcpCommand(command, socket);
                        }
                        catch (error) {
                            // Send error response for any uncaught exceptions
                            console.error(`Error handling command ${command.command}: ${error instanceof Error ? error.message : String(error)}`);
                            if (error instanceof Error && error.stack) {
                                console.error(`Stack trace: ${error.stack}`);
                            }
                            // Send error response to client
                            const response = {
                                id: command.id,
                                success: false,
                                error: {
                                    code: -32603,
                                    message: `Internal error: ${error instanceof Error ? error.message : String(error)}`,
                                    data: this.lastMcpError ? { stderr: this.lastMcpError } : undefined
                                }
                            };
                            socket.write(JSON.stringify(response) + '\n');
                        }
                    }
                    else {
                        // If not a bridge command, might be a direct MCP request
                        if (this.mcpProcess && !this.mcpProcess.stdin.destroyed) {
                            // Forward request to MCP process
                            this.mcpProcess.stdin.write(message + '\n');
                        }
                        else {
                            socket.write(JSON.stringify({
                                jsonrpc: '2.0',
                                id: this.extractId(message),
                                error: {
                                    code: -32603,
                                    message: 'MCP server temporarily unavailable'
                                }
                            }) + '\n');
                        }
                    }
                }
                catch (e) {
                    console.error(`Failed to parse client message: ${e}`);
                    // Send error response
                    socket.write(JSON.stringify({
                        jsonrpc: '2.0',
                        id: null,
                        error: {
                            code: -32700,
                            message: `Invalid JSON: ${e}`
                        }
                    }) + '\n');
                }
            });
            // Handle client disconnect
            socket.on('close', () => {
                console.log(`Client disconnected: ${socket.remoteAddress}:${socket.remotePort}`);
                this.activeConnections.delete(socket);
                // Clean up pending requests for this connection
                for (const [requestId, request] of this.pendingRequests.entries()) {
                    if (request.socket === socket) {
                        this.pendingRequests.delete(requestId);
                        if (request.toolCallId) {
                            this.toolResponseMapping.delete(request.toolCallId);
                        }
                    }
                }
            });
            // Handle client error
            socket.on('error', (err) => {
                console.error(`Client error: ${err.message}`);
                this.activeConnections.delete(socket);
            });
        });
        // Start TCP server
        this.server.listen(this.config.port, this.config.host, () => {
            console.log(`TCP bridge server running on ${this.config.host}:${this.config.port}`);
        });
        // Handle server error
        this.server.on('error', (err) => {
            console.error(`Server error: ${err.message}`);
        });
        // Handle process signals
        process.on('SIGINT', () => this.shutdown());
        process.on('SIGTERM', () => this.shutdown());
    }
    /**
     * Shutdown bridge
     */
    shutdown() {
        console.log('Shutting down bridge...');
        // Close all client connections
        for (const socket of this.activeConnections) {
            socket.end();
        }
        // Close server
        if (this.server) {
            this.server.close();
        }
        // Terminate MCP process
        if (this.mcpProcess) {
            this.mcpProcess.kill();
        }
        console.log('Bridge shut down');
        process.exit(0);
    }
    /**
     * Extract ID from JSON-RPC request
     */
    extractId(request) {
        try {
            const json = JSON.parse(request);
            return json.id || null;
        }
        catch (e) {
            return null;
        }
    }
}
// If running this script directly, create and start bridge
if (require.main === module) {
    // Parse config from command line args
    const args = process.argv.slice(2);
    const port = parseInt(args[0]) || 8765;
    const mcpCommand = args[1] || 'node';
    const mcpArgs = args.slice(2);
    const bridge = new McpBridge({
        port,
        mcpCommand,
        mcpArgs: mcpArgs.length > 0 ? mcpArgs : undefined
    });
    bridge.start();
}
// Export bridge class for use by other modules
exports.default = McpBridge;
//# sourceMappingURL=index.js.map
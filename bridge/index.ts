/**
 * MCP TCP Bridge
 * 
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */

import * as net from 'net';
import { spawn, ChildProcessWithoutNullStreams } from 'child_process';
import * as readline from 'readline';
import { v4 as uuidv4 } from 'uuid';
import * as fs from 'fs';
import * as path from 'path';

// Configuration
interface BridgeConfig {
    port: number;
    host: string;
    mcpCommand: string;
    mcpArgs: string[];
    registryPath?: string;
    env?: Record<string, string>;
}

// MCP service registration info
interface McpServiceInfo {
    name: string;
    command: string;
    args: string[];
    description: string;
    env?: Record<string, string>;
    created: number;
    lastUsed?: number;
}

// Command types
type McpCommandType = 'spawn' | 'shutdown' | 'listtools' | 'toolcall' | 'ping' | 'status' | 'list' | 'register' | 'unregister';

// Command interface
interface McpCommand {
    command: McpCommandType;
    id: string;
    params?: any;
}

// Response interface
interface McpResponse {
    id: string;
    success: boolean;
    result?: any;
    error?: {
        code: number;
        message: string;
        data?: any;
    };
}

// Tool call request
interface ToolCallRequest {
    jsonrpc: string;
    id: string;
    method: string;
    params: any;
}

// Tracking pending requests
interface PendingRequest {
    id: string;
    socket: net.Socket;
    timestamp: number;
    toolCallId?: string;
}

/**
 * MCP Bridge class
 */
class McpBridge {
    private config: BridgeConfig;
    private server: net.Server | null = null;

    // 服务进程映射
    private mcpProcesses: Map<string, ChildProcessWithoutNullStreams> = new Map();
    private mcpToolsMap: Map<string, any[]> = new Map();
    private serviceReadyMap: Map<string, boolean> = new Map();

    // 服务注册表
    private serviceRegistry: Map<string, McpServiceInfo> = new Map();
    private registryPath: string;

    // 活跃连接
    private activeConnections: Set<net.Socket> = new Set();

    // 请求跟踪
    private pendingRequests: Map<string, PendingRequest> = new Map();
    private toolResponseMapping: Map<string, string> = new Map();
    private toolCallServiceMap: Map<string, string> = new Map();

    // 请求超时(毫秒)
    private readonly REQUEST_TIMEOUT = 60000; // 60秒超时

    // 服务错误记录
    private mcpErrors: Map<string, string> = new Map();

    constructor(config: Partial<BridgeConfig> = {}) {
        // 默认配置
        this.config = {
            port: 8752,
            host: '127.0.0.1',
            mcpCommand: 'node',
            mcpArgs: ['../your-mcp-server.js'],
            ...config
        };

        // 设置注册表路径
        this.registryPath = this.config.registryPath || './mcp-registry.json';

        // 加载注册表
        this.loadRegistry();

        // 设置超时检查
        setInterval(() => this.checkRequestTimeouts(), 5000);
    }

    /**
     * 加载MCP服务注册表
     */
    private loadRegistry(): void {
        try {
            if (fs.existsSync(this.registryPath)) {
                const data = fs.readFileSync(this.registryPath, 'utf8');
                const registry = JSON.parse(data);

                this.serviceRegistry.clear();
                for (const [key, value] of Object.entries(registry)) {
                    this.serviceRegistry.set(key, value as McpServiceInfo);
                }
            } else {
                this.serviceRegistry.clear();
                this.saveRegistry();
            }
        } catch (e) {
            this.serviceRegistry.clear();
        }
    }

    /**
     * 保存MCP服务注册表
     */
    private saveRegistry(): void {
        try {
            const registry: Record<string, McpServiceInfo> = {};
            for (const [key, value] of this.serviceRegistry.entries()) {
                registry[key] = value;
            }

            const dir = path.dirname(this.registryPath);
            if (!fs.existsSync(dir)) {
                fs.mkdirSync(dir, { recursive: true });
            }

            fs.writeFileSync(this.registryPath, JSON.stringify(registry, null, 2), 'utf8');
        } catch (e) {
            console.error(`Failed to save registry: ${e}`);
        }
    }

    /**
     * 注册新的MCP服务
     */
    private registerService(name: string, info: Partial<McpServiceInfo>): boolean {
        if (!name || !info.command) {
            return false;
        }

        const serviceInfo: McpServiceInfo = {
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
     * 注销MCP服务
     */
    private unregisterService(name: string): boolean {
        if (!this.serviceRegistry.has(name)) {
            return false;
        }

        this.serviceRegistry.delete(name);
        this.saveRegistry();
        return true;
    }

    /**
     * 获取已注册MCP服务列表
     */
    private getServiceList(): McpServiceInfo[] {
        return Array.from(this.serviceRegistry.values());
    }

    /**
     * 检查服务是否运行中
     */
    private isServiceRunning(serviceName: string): boolean {
        return this.mcpProcesses.has(serviceName) &&
            !this.mcpProcesses.get(serviceName)?.stdin.destroyed;
    }

    /**
     * 启动特定服务的子进程
     */
    private startMcpProcess(serviceName: string, command: string, args: string[], env?: Record<string, string>): void {
        if (!command) {
            console.log(`No command specified for service ${serviceName}, skipping startup`);
            return;
        }

        // 如果服务已运行，不需要重新启动
        if (this.isServiceRunning(serviceName)) {
            console.log(`Service ${serviceName} is already running`);
            return;
        }

        console.log(`Starting MCP process for ${serviceName}: ${command} ${args.join(' ')}`);

        try {
            const mcpProcess = spawn(command, args, {
                stdio: ['pipe', 'pipe', 'pipe'],
                env: {
                    ...process.env,
                    ...env
                }
            });

            // 存储进程
            this.mcpProcesses.set(serviceName, mcpProcess);

            // 初始化为空工具数组
            if (!this.mcpToolsMap.has(serviceName)) {
                this.mcpToolsMap.set(serviceName, []);
            }

            // 标记服务为未就绪状态，直到工具获取完成
            this.serviceReadyMap.set(serviceName, false);

            // 处理标准输出
            mcpProcess.stdout?.on('data', (data: Buffer) => {
                this.handleMcpResponse(data, serviceName);
            });

            // 处理标准错误
            mcpProcess.stderr?.on('data', (data: Buffer) => {
                const errorText = data.toString().trim();
                console.error(`MCP process error from ${serviceName}: ${errorText}`);
                this.mcpErrors.set(serviceName, errorText);
            });

            // 处理进程错误
            mcpProcess.on('error', (error: Error) => {
                console.error(`MCP process error for ${serviceName}: ${error.message}`);
                this.mcpProcesses.delete(serviceName);
                // 保持服务处于就绪状态，以允许重新连接
                this.serviceReadyMap.set(serviceName, true);
            });

            mcpProcess.on('close', (code: number) => {
                console.log(`MCP process for ${serviceName} exited with code: ${code}`);
                this.mcpProcesses.delete(serviceName);
                // 保持服务处于就绪状态
                this.serviceReadyMap.set(serviceName, true);
            });

            // 启动后获取工具列表
            setTimeout(() => this.fetchMcpTools(serviceName), 1000);
        } catch (error) {
            console.error(`Failed to start MCP process for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            // 标记服务为就绪状态
            this.serviceReadyMap.set(serviceName, true);
        }
    }

    /**
     * 获取特定服务的MCP工具列表
     */
    private fetchMcpTools(serviceName: string): void {
        if (!this.isServiceRunning(serviceName)) {
            // 如果进程不可用，设置空工具并标记为就绪
            this.mcpToolsMap.set(serviceName, []);
            this.serviceReadyMap.set(serviceName, true);
            console.log(`MCP service ${serviceName} marked ready with no tools (process unavailable)`);
            return;
        }

        try {
            const mcpProcess = this.mcpProcesses.get(serviceName)!;

            // 创建tools/list请求
            const toolsListRequest = {
                jsonrpc: '2.0',
                id: `init_${serviceName}_${Date.now()}`,
                method: 'tools/list',
                params: {}
            };

            // 发送请求
            mcpProcess.stdin.write(JSON.stringify(toolsListRequest) + '\n');

            // 不论响应如何，都标记服务为就绪状态 - 当响应到达时会更新工具
            this.serviceReadyMap.set(serviceName, true);
        } catch (error) {
            console.error(`Error fetching tools for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            this.mcpToolsMap.set(serviceName, []);
            this.serviceReadyMap.set(serviceName, true);
        }
    }

    /**
     * 处理来自特定服务的MCP响应数据
     */
    private handleMcpResponse(data: Buffer, serviceName: string): void {
        try {
            const responseText = data.toString().trim();
            if (!responseText) return;

            const response = JSON.parse(responseText);

            // 检查是否为工具调用响应
            if (response.id && this.toolResponseMapping.has(response.id)) {
                const bridgeRequestId = this.toolResponseMapping.get(response.id)!;
                const pendingRequest = this.pendingRequests.get(bridgeRequestId);

                if (pendingRequest) {
                    // 转发响应给客户端
                    pendingRequest.socket.write(JSON.stringify({
                        id: bridgeRequestId,
                        success: !response.error,
                        result: response.result,
                        error: response.error
                    }) + '\n');

                    // 清理
                    this.pendingRequests.delete(bridgeRequestId);
                    this.toolResponseMapping.delete(response.id);
                    this.toolCallServiceMap.delete(response.id);
                }
            } else if (response.id && typeof response.id === 'string' && response.id.startsWith(`init_${serviceName}_`)) {
                // 处理工具列表响应
                if (response.error) {
                    // 错误时设置空工具列表
                    this.mcpToolsMap.set(serviceName, []);
                    return;
                }

                // 从响应中提取工具
                let tools: any[] = [];
                if (response.result && response.result.tools && Array.isArray(response.result.tools)) {
                    tools = response.result.tools;
                } else if (response.result && Array.isArray(response.result)) {
                    tools = response.result;
                }

                // 存储该服务的工具
                this.mcpToolsMap.set(serviceName, tools);
                console.log(`MCP service ${serviceName} is ready with ${tools.length} tools`);
            }
        } catch (e) {
            console.error(`Error handling MCP response from ${serviceName}: ${e instanceof Error ? e.message : String(e)}`);

            // 不要崩溃服务器
            this.mcpToolsMap.set(serviceName, []);
        }
    }

    /**
     * 处理客户端MCP命令
     */
    private handleMcpCommand(command: McpCommand, socket: net.Socket): void {
        const { id, command: cmdType, params } = command;
        let response: McpResponse;

        try {
            switch (cmdType) {
                case 'ping':
                    // 健康检查(单个服务或所有服务)
                    const pingServiceName = params?.serviceName || params?.name;

                    if (pingServiceName) {
                        if (this.serviceRegistry.has(pingServiceName)) {
                            const serviceInfo = this.serviceRegistry.get(pingServiceName);
                            const isRunning = this.isServiceRunning(pingServiceName);
                            const isReady = this.serviceReadyMap.get(pingServiceName) || false;

                            response = {
                                id,
                                success: true,
                                result: {
                                    status: isRunning ? "ok" : "registered_not_running",
                                    name: pingServiceName,
                                    description: serviceInfo?.description,
                                    timestamp: Date.now(),
                                    running: isRunning,
                                    ready: isReady
                                }
                            };

                            // 更新最后使用时间
                            if (serviceInfo) {
                                serviceInfo.lastUsed = Date.now();
                                this.saveRegistry();
                            }
                        } else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32601,
                                    message: `Service '${pingServiceName}' not registered`
                                }
                            };
                        }
                    } else {
                        // 普通bridge健康检查
                        const runningServices = [...this.mcpProcesses.keys()];
                        response = {
                            id,
                            success: true,
                            result: {
                                timestamp: Date.now(),
                                status: 'ok',
                                runningServices: runningServices,
                                serviceCount: runningServices.length
                            }
                        };
                    }

                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'status':
                    // bridge状态及所有运行服务
                    const runningServices = [...this.mcpProcesses.keys()];
                    const serviceStatus: Record<string, any> = {};

                    for (const [name, mcpProcess] of this.mcpProcesses.entries()) {
                        serviceStatus[name] = {
                            running: !mcpProcess.stdin.destroyed,
                            ready: this.serviceReadyMap.get(name) || false,
                            toolCount: (this.mcpToolsMap.get(name) || []).length
                        };
                    }

                    response = {
                        id,
                        success: true,
                        result: {
                            runningServices: runningServices,
                            serviceCount: runningServices.length,
                            services: serviceStatus,
                            pendingRequests: this.pendingRequests.size,
                            activeConnections: this.activeConnections.size
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'listtools':
                    // 查询特定服务的可用工具列表
                    const serviceToList = params?.name;

                    if (serviceToList) {
                        if (!this.isServiceRunning(serviceToList)) {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32603,
                                    message: `Service '${serviceToList}' not running`
                                }
                            };
                        } else {
                            response = {
                                id,
                                success: true,
                                result: {
                                    tools: this.mcpToolsMap.get(serviceToList) || []
                                }
                            };
                        }
                    } else {
                        // 未指定服务，列出所有服务的工具
                        const allTools: Record<string, any> = {};
                        for (const [name, tools] of this.mcpToolsMap.entries()) {
                            if (this.isServiceRunning(name)) {
                                allTools[name] = tools;
                            }
                        }

                        response = {
                            id,
                            success: true,
                            result: {
                                serviceTools: allTools
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'list':
                    // 列出已注册的MCP服务并附带运行状态
                    const services = this.getServiceList().map(service => {
                        return {
                            ...service,
                            running: this.isServiceRunning(service.name),
                            ready: this.serviceReadyMap.get(service.name) || false,
                            toolCount: (this.mcpToolsMap.get(service.name) || []).length
                        };
                    });

                    response = {
                        id,
                        success: true,
                        result: { services }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'spawn':
                    // 启动新的MCP服务，不关闭其他服务
                    if (!params) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing parameters"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    const spawnServiceName = params.name;
                    let serviceCommand = params.command;
                    let serviceArgs = params.args || [];
                    let serviceEnv = params.env;

                    // 如果未提供命令，尝试从注册表加载
                    if (!serviceCommand && spawnServiceName && this.serviceRegistry.has(spawnServiceName)) {
                        const info = this.serviceRegistry.get(spawnServiceName)!;
                        serviceCommand = info.command;
                        serviceArgs = info.args;
                        serviceEnv = info.env;
                    } else if (!serviceCommand || !spawnServiceName) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameters: name and command"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }

                    // 启动服务
                    this.startMcpProcess(spawnServiceName, serviceCommand, serviceArgs, serviceEnv);

                    response = {
                        id,
                        success: true,
                        result: {
                            status: "started",
                            name: spawnServiceName,
                            command: serviceCommand,
                            args: serviceArgs
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'shutdown':
                    // 关闭特定的MCP服务
                    const serviceToShutdown = params?.name;

                    if (!serviceToShutdown) {
                        // 未指定服务，返回错误
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                    } else if (!this.isServiceRunning(serviceToShutdown)) {
                        // 服务未运行
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${serviceToShutdown}' not running`
                            }
                        };
                    } else {
                        // 终止进程
                        this.mcpProcesses.get(serviceToShutdown)!.kill();
                        this.mcpProcesses.delete(serviceToShutdown);

                        response = {
                            id,
                            success: true,
                            result: {
                                status: "shutdown",
                                name: serviceToShutdown
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;

                case 'register':
                    // 注册新的MCP服务
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
                    // 注销MCP服务
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

                    // 如果运行中，先关闭
                    if (this.isServiceRunning(params.name)) {
                        this.mcpProcesses.get(params.name)!.kill();
                        this.mcpProcesses.delete(params.name);
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
                    // 调用工具
                    this.handleToolCall(command, socket);
                    break;

                default:
                    // 未知命令
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
        } catch (error) {
            // 通用错误处理
            const errorResponse: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal server error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(errorResponse) + '\n');
        }
    }

    /**
     * 处理工具调用请求
     */
    private handleToolCall(command: McpCommand, socket: net.Socket): void {
        const { id, params } = command;
        const { method, params: methodParams, name: requestedServiceName } = params || {};

        // 确定使用哪个服务
        const serviceName = requestedServiceName || Object.keys(this.mcpProcesses)[0];

        if (!serviceName) {
            console.error(`Cannot handle tool call: No service specified and no default available`);
            const response: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32602,
                    message: 'No service specified and no default available'
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }

        if (!this.isServiceRunning(serviceName)) {
            console.error(`Cannot handle tool call: Service ${serviceName} not running`);
            const response: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Service '${serviceName}' not running`
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }

        try {
            // 创建工具调用请求
            const toolCallId = params.id || uuidv4();
            const toolCallRequest: ToolCallRequest = {
                jsonrpc: '2.0',
                id: toolCallId,
                method: 'tools/call',  // 符合MCP规范的方法名
                params: {
                    name: params.method,
                    arguments: params.params || {}
                }
            };

            // 记录请求
            this.pendingRequests.set(id, {
                id,
                socket,
                timestamp: Date.now(),
                toolCallId
            });

            // 创建工具调用ID和桥接请求ID的映射
            this.toolResponseMapping.set(toolCallId, id);

            // 记住这个工具调用对应的服务
            this.toolCallServiceMap.set(toolCallId, serviceName);

            // 获取特定服务的进程
            const mcpProcess = this.mcpProcesses.get(serviceName)!;

            // 发送请求到MCP服务器
            const writeResult = mcpProcess.stdin.write(JSON.stringify(toolCallRequest) + '\n');
            if (!writeResult) {
                console.error(`Failed to write tool call request to ${serviceName} process stdin`);
                // 发送错误给客户端
                const response: McpResponse = {
                    id,
                    success: false,
                    error: {
                        code: -32603,
                        message: `Failed to send request to ${serviceName}`
                    }
                };
                socket.write(JSON.stringify(response) + '\n');

                // 清理
                this.pendingRequests.delete(id);
                this.toolResponseMapping.delete(toolCallId);
                this.toolCallServiceMap.delete(toolCallId);
            }
        } catch (error) {
            console.error(`Error handling tool call for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);

            // 发送错误响应
            const response: McpResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(response) + '\n');

            // 清理映射
            if (params && params.id) {
                this.toolResponseMapping.delete(params.id);
                this.toolCallServiceMap.delete(params.id);
            }
            this.pendingRequests.delete(id);
        }
    }

    /**
     * 检查请求超时
     */
    private checkRequestTimeouts(): void {
        const now = Date.now();

        for (const [requestId, request] of this.pendingRequests.entries()) {
            if (now - request.timestamp > this.REQUEST_TIMEOUT) {
                console.log(`Request timeout: ${requestId}`);

                // 发送超时响应
                const response: McpResponse = {
                    id: requestId,
                    success: false,
                    error: {
                        code: -32603,
                        message: "Request timeout",
                        data: this.mcpErrors.get(this.toolCallServiceMap.get(requestId) || '') ?
                            { stderr: this.mcpErrors.get(this.toolCallServiceMap.get(requestId) || '') } :
                            undefined
                    }
                };

                request.socket.write(JSON.stringify(response) + '\n');

                // 清理
                this.pendingRequests.delete(requestId);
                if (request.toolCallId) {
                    this.toolResponseMapping.delete(request.toolCallId);
                    this.toolCallServiceMap.delete(request.toolCallId);
                }
            }
        }
    }

    /**
     * 启动TCP服务器
     */
    public start(): void {
        // 创建TCP服务器 - 默认不启动任何MCP进程
        this.server = net.createServer((socket: net.Socket) => {
            console.log(`New client connection: ${socket.remoteAddress}:${socket.remotePort}`);
            this.activeConnections.add(socket);

            // 添加socket超时以防止客户端挂起
            socket.setTimeout(30000); // 30秒超时
            socket.on('timeout', () => {
                console.log(`Socket timeout: ${socket.remoteAddress}:${socket.remotePort}`);
                socket.end();
                this.activeConnections.delete(socket);
            });

            // 处理来自客户端的数据
            socket.on('data', (data: Buffer) => {
                const message = data.toString().trim();

                try {
                    // 解析命令
                    const command = JSON.parse(message) as McpCommand;

                    // 确保命令有ID
                    if (!command.id) {
                        command.id = uuidv4();
                    }

                    // 处理命令
                    if (command.command) {
                        this.handleMcpCommand(command, socket);
                    } else {
                        // 非桥接命令，无默认服务转发
                        socket.write(JSON.stringify({
                            jsonrpc: '2.0',
                            id: this.extractId(message),
                            error: {
                                code: -32600,
                                message: 'Invalid request: no service specified'
                            }
                        }) + '\n');
                    }
                } catch (e) {
                    console.error(`Failed to parse client message: ${e}`);

                    // 发送错误响应
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

            // 处理客户端断开连接
            socket.on('close', () => {
                console.log(`Client disconnected: ${socket.remoteAddress}:${socket.remotePort}`);
                this.activeConnections.delete(socket);

                // 清理此连接的待处理请求
                for (const [requestId, request] of this.pendingRequests.entries()) {
                    if (request.socket === socket) {
                        const toolCallId = request.toolCallId;
                        this.pendingRequests.delete(requestId);
                        if (toolCallId) {
                            this.toolResponseMapping.delete(toolCallId);
                            this.toolCallServiceMap.delete(toolCallId);
                        }
                    }
                }
            });

            // 处理客户端错误
            socket.on('error', (err: Error) => {
                console.error(`Client error: ${err.message}`);
                this.activeConnections.delete(socket);
            });
        });

        // 启动TCP服务器
        this.server.listen(this.config.port, this.config.host, () => {
            console.log(`TCP bridge server running on ${this.config.host}:${this.config.port}`);
        });

        // 处理服务器错误
        this.server.on('error', (err: Error) => {
            console.error(`Server error: ${err.message}`);
        });

        // 处理进程信号
        process.on('SIGINT', () => this.shutdown());
        process.on('SIGTERM', () => this.shutdown());
    }

    /**
     * 关闭桥接器
     */
    public shutdown(): void {
        console.log('Shutting down bridge...');

        // 关闭所有客户端连接
        for (const socket of this.activeConnections) {
            socket.end();
        }

        // 关闭服务器
        if (this.server) {
            this.server.close();
        }

        // 终止所有MCP进程
        for (const [name, mcpProcess] of this.mcpProcesses.entries()) {
            console.log(`Terminating MCP process: ${name}`);
            mcpProcess.kill();
        }
        this.mcpProcesses.clear();

        console.log('Bridge shut down');
        process.exit(0);
    }

    /**
     * 从JSON-RPC请求中提取ID
     */
    private extractId(request: string): string | null {
        try {
            const json = JSON.parse(request);
            return json.id || null;
        } catch (e) {
            return null;
        }
    }
}

// If running this script directly, create and start bridge
if (require.main === module) {
    // Parse config from command line args
    const args = process.argv.slice(2);
    const port = parseInt(args[0]) || 8752;
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
export default McpBridge; 
/**
 * MCP TCP Bridge
 *
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */
interface BridgeConfig {
    port: number;
    host: string;
    mcpCommand: string;
    mcpArgs: string[];
    registryPath?: string;
    env?: Record<string, string>;
}
/**
 * MCP Bridge class
 */
declare class McpBridge {
    private config;
    private server;
    private mcpProcesses;
    private mcpToolsMap;
    private serviceReadyMap;
    private serviceRegistry;
    private registryPath;
    private activeConnections;
    private pendingRequests;
    private toolResponseMapping;
    private toolCallServiceMap;
    private readonly REQUEST_TIMEOUT;
    private mcpErrors;
    constructor(config?: Partial<BridgeConfig>);
    /**
     * 加载MCP服务注册表
     */
    private loadRegistry;
    /**
     * 保存MCP服务注册表
     */
    private saveRegistry;
    /**
     * 注册新的MCP服务
     */
    private registerService;
    /**
     * 注销MCP服务
     */
    private unregisterService;
    /**
     * 获取已注册MCP服务列表
     */
    private getServiceList;
    /**
     * 检查服务是否运行中
     */
    private isServiceRunning;
    /**
     * 启动特定服务的子进程
     */
    private startMcpProcess;
    /**
     * 获取特定服务的MCP工具列表
     */
    private fetchMcpTools;
    /**
     * 处理来自特定服务的MCP响应数据
     */
    private handleMcpResponse;
    /**
     * 处理客户端MCP命令
     */
    private handleMcpCommand;
    /**
     * 处理工具调用请求
     */
    private handleToolCall;
    /**
     * 检查请求超时
     */
    private checkRequestTimeouts;
    /**
     * 启动TCP服务器
     */
    start(): void;
    /**
     * 关闭桥接器
     */
    shutdown(): void;
    /**
     * 从JSON-RPC请求中提取ID
     */
    private extractId;
}
export default McpBridge;

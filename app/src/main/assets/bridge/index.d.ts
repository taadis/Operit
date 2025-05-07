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
    private mcpProcess;
    private mcpName;
    private mcpTools;
    private isReady;
    private serviceRegistry;
    private registryPath;
    private activeConnections;
    private pendingRequests;
    private toolResponseMapping;
    private readonly REQUEST_TIMEOUT;
    private lastMcpError;
    constructor(config?: Partial<BridgeConfig>);
    /**
     * Load MCP service registry
     */
    private loadRegistry;
    /**
     * Save MCP service registry
     */
    private saveRegistry;
    /**
     * Register a new MCP service
     */
    private registerService;
    /**
     * Unregister an MCP service
     */
    private unregisterService;
    /**
     * Get list of registered MCP services
     */
    private getServiceList;
    /**
     * Start MCP child process
     */
    private startMcpProcess;
    /**
     * Get MCP tools list
     */
    private fetchMcpTools;
    /**
     * Handle MCP response data
     */
    private handleMcpResponse;
    /**
     * Handle client MCP command
     */
    private handleMcpCommand;
    /**
     * Handle tool call request
     */
    private handleToolCall;
    /**
     * Check for request timeouts
     */
    private checkRequestTimeouts;
    /**
     * Start TCP server
     */
    start(): void;
    /**
     * Shutdown bridge
     */
    shutdown(): void;
    /**
     * Extract ID from JSON-RPC request
     */
    private extractId;
}
export default McpBridge;

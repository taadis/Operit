/**
 * Query tools type definitions for Assistance Package Tools
 */

/**
 * Query operations namespace
 */
export namespace Query {
    /**
     * Query knowledge library for information
     * @param query - Search query for the knowledge library
     * @returns Promise resolving to the search results as string
     */
    function knowledge(query: string): Promise<string>;
} 
/* METADATA
{
  name: various_search
  description: 提供多平台搜索功能，支持从必应、百度、搜狗、夸克等平台获取搜索结果。
  
  tools: [
    {
      name: search_bing
      description: 使用必应搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        }
      ]
    },
    {
      name: search_baidu
      description: 使用百度搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_sogou
      description: 使用搜狗搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_quark
      description: 使用夸克搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: combined_search
      description: 在多个平台同时执行搜索。建议用户要求搜索的时候默认使用这个工具。
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: platforms
          description: 搜索平台列表字符串，可选值包括"bing","baidu","sogou","quark"，多个平台用逗号分隔，比如"bing,baidu,sogou,quark"
          type: string
          required: true
        }
      ]
    }
  ]
  
  category: NETWORK
}
*/

const various_search = (function () {
    /**
     * 参数类型转换函数 - 将输入参数转换为期望的数据类型
     * @param params 输入参数对象
     * @param paramTypes 参数类型定义
     * @returns 转换后的参数对象
     */
    function convertParamTypes(params: Record<string, any>, paramTypes: Record<string, string>): Record<string, any> {
        if (!params || !paramTypes) return params;

        const result: Record<string, any> = {};

        for (const key in params) {
            if (params[key] === undefined || params[key] === null) {
                result[key] = params[key];
                continue;
            }

            const expectedType = paramTypes[key];
            if (!expectedType) {
                // 如果没有指定类型，保持原样
                result[key] = params[key];
                continue;
            }

            // 获取参数值
            const value = params[key];

            try {
                switch (expectedType.toLowerCase()) {
                    case 'number':
                        // 将字符串转换为数字
                        if (typeof value === 'string') {
                            if (value.includes('.')) {
                                result[key] = parseFloat(value);
                            } else {
                                result[key] = parseInt(value, 10);
                            }

                            // 检查转换结果是否为有效数字
                            if (isNaN(result[key])) {
                                throw new Error(`参数 ${key} 无法转换为数字: ${value}`);
                            }
                        } else {
                            result[key] = value;
                        }
                        break;

                    case 'boolean':
                        // 将字符串转换为布尔值
                        if (typeof value === 'string') {
                            const lowerValue = value.toLowerCase();
                            if (lowerValue === 'true' || lowerValue === '1' || lowerValue === 'yes') {
                                result[key] = true;
                            } else if (lowerValue === 'false' || lowerValue === '0' || lowerValue === 'no') {
                                result[key] = false;
                            } else {
                                throw new Error(`参数 ${key} 无法转换为布尔值: ${value}`);
                            }
                        } else {
                            result[key] = value;
                        }
                        break;

                    case 'array':
                        // 将字符串转换为数组
                        if (typeof value === 'string') {
                            try {
                                result[key] = JSON.parse(value);
                                if (!Array.isArray(result[key])) {
                                    throw new Error('解析结果不是数组');
                                }
                            } catch (e) {
                                throw new Error(`参数 ${key} 无法转换为数组: ${value}`);
                            }
                        } else {
                            result[key] = value;
                        }
                        break;

                    case 'object':
                        // 将字符串转换为对象
                        if (typeof value === 'string') {
                            try {
                                result[key] = JSON.parse(value);
                                if (Array.isArray(result[key]) || typeof result[key] !== 'object') {
                                    throw new Error('解析结果不是对象');
                                }
                            } catch (e) {
                                throw new Error(`参数 ${key} 无法转换为对象: ${value}`);
                            }
                        } else {
                            result[key] = value;
                        }
                        break;

                    default:
                        // 其他类型或未指定类型，保持原样
                        result[key] = value;
                }
            } catch (error) {
                console.error(`参数类型转换错误: ${error.message}`);
                // 转换失败时保留原始值
                result[key] = value;
            }
        }

        return result;
    }

    /**
     * 包装函数 - 统一处理所有搜索函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     */
    async function search_wrap(
        func: (params: any) => Promise<any>,
        params: any
    ): Promise<void> {
        const successMessage = `成功执行${func.name || '搜索'}操作`;
        const failMessage = `${func.name || '搜索'}操作失败`;

        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));

            // 执行原始函数
            const result = await func(params);

            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));

            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage
                });
            } else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    data: result
                });
            }
        } catch (error: any) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);

            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                error_stack: error.stack
            });
        }
    }

    /**
     * 使用必应搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_bing(params: { query: string }) {
        const { query } = params;

        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }

        try {
            // 构建必应搜索URL
            const encodedQuery = encodeURIComponent(query);
            const url = `https://cn.bing.com/search?q=${encodedQuery}&FORM=HDRSC1`;

            // 访问搜索页面
            const response = await Tools.Net.visit(url);

            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取必应搜索结果`
                };
            }

            // 返回搜索结果
            return {
                success: true,
                message: `成功从必应获取搜索结果`,
                query: query,
                content: response.content
            };
        } catch (error) {
            return {
                success: false,
                message: `必应搜索时出错: ${error.message}`
            };
        }
    }

    /**
     * 使用百度搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_baidu(params: { query: string, page?: number }) {
        // 定义参数类型
        const paramTypes = {
            page: 'number',
        };

        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { query, page = 1 } = convertedParams;

        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }

        try {
            // 构建百度搜索URL
            const pn = (page - 1) * 10; // 百度分页参数
            const encodedQuery = encodeURIComponent(query);
            const url = `https://www.baidu.com/s?wd=${encodedQuery}&pn=${pn}`;

            // 访问搜索页面
            const response = await Tools.Net.visit(url);

            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取百度搜索结果`
                };
            }

            // 返回搜索结果
            return {
                success: true,
                message: `成功从百度获取搜索结果`,
                query: query,
                page: page,
                content: response.content
            };
        } catch (error) {
            return {
                success: false,
                message: `百度搜索时出错: ${error.message}`
            };
        }
    }

    /**
     * 使用搜狗搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_sogou(params: { query: string, page?: number }) {
        // 定义参数类型
        const paramTypes = {
            page: 'number',
        };

        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { query, page = 1 } = convertedParams;

        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }

        try {
            // 构建搜狗搜索URL
            const encodedQuery = encodeURIComponent(query);
            const url = `https://www.sogou.com/web?query=${encodedQuery}&page=${page}`;

            // 访问搜索页面
            const response = await Tools.Net.visit(url);

            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取搜狗搜索结果`
                };
            }

            // 返回搜索结果
            return {
                success: true,
                message: `成功从搜狗获取搜索结果`,
                query: query,
                page: page,
                content: response.content
            };
        } catch (error) {
            return {
                success: false,
                message: `搜狗搜索时出错: ${error.message}`
            };
        }
    }

    /**
     * 使用夸克搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_quark(params: { query: string, page?: number }) {
        // 定义参数类型
        const paramTypes = {
            page: 'number',
        };

        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { query, page = 1 } = convertedParams;

        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }

        try {
            // 构建夸克搜索URL
            const encodedQuery = encodeURIComponent(query);
            const url = `https://quark.sm.cn/s?q=${encodedQuery}&page=${page}`;

            // 访问搜索页面
            const response = await Tools.Net.visit(url);

            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取夸克搜索结果`
                };
            }

            // 返回搜索结果
            return {
                success: true,
                message: `成功从夸克获取搜索结果`,
                query: query,
                page: page,
                content: response.content
            };
        } catch (error) {
            return {
                success: false,
                message: `夸克搜索时出错: ${error.message}`
            };
        }
    }

    /**
     * 在多个平台同时执行搜索，调用对应平台的搜索函数
     * @param {Object} params - 包含搜索查询和平台列表的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function combined_search(params: { query: string, platforms: string }) {
        const { query, platforms } = params;

        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }

        if (!platforms || platforms.trim() === "") {
            return {
                success: false,
                message: "请提供有效的平台列表"
            };
        }

        const platformKeys = platforms.split(",").map(p => p.trim());
        const validPlatforms: string[] = [];
        const invalidPlatforms: Array<{ platform: string, error: string }> = [];

        // 验证平台
        for (const platform of platformKeys) {
            if (["bing", "baidu", "sogou", "quark"].includes(platform)) {
                validPlatforms.push(platform);
            } else {
                invalidPlatforms.push({
                    platform,
                    error: `不支持的搜索平台: ${platform}`
                });
            }
        }

        // 如果没有有效平台，返回错误
        if (validPlatforms.length === 0) {
            return {
                success: false,
                message: "没有提供有效的搜索平台",
                supported_platforms: ["bing", "baidu", "sogou", "quark"],
                errors: invalidPlatforms
            };
        }

        const searchPromises = validPlatforms.map(async (platform) => {
            let result;
            try {
                switch (platform) {
                    case 'bing':
                        result = await search_bing({ query });
                        break;
                    case 'baidu':
                        result = await search_baidu({ query, page: 1 });
                        break;
                    case 'sogou':
                        result = await search_sogou({ query, page: 1 });
                        break;
                    case 'quark':
                        result = await search_quark({ query, page: 1 });
                        break;
                }
            } catch (error: any) {
                result = { success: false, message: error.message };
            }
            return { ...result, platform };
        });

        const allResults = await Promise.all(searchPromises);

        const successfulResults = allResults.filter(r => r.success);
        let finalMessage = `组合搜索完成。`;
        if (invalidPlatforms.length > 0) {
            finalMessage += ` 无效平台: ${invalidPlatforms.map(e => e.platform).join(', ')}。`
        }

        if (successfulResults.length > 0) {
            finalMessage += ` 成功平台: ${successfulResults.map(r => r.platform).join(', ')}。`
        }

        const failedResults = allResults.filter(r => !r.success);
        if (failedResults.length > 0) {
            finalMessage += ` 失败平台: ${failedResults.map(r => r.platform).join(', ')}。`
        }

        return {
            success: successfulResults.length > 0,
            message: finalMessage,
            results: allResults,
            errors: invalidPlatforms.length > 0 ? invalidPlatforms : undefined,
        };
    }

    return {
        search_bing: async (params: any) => {
            await search_wrap(search_bing, params);
        },
        search_baidu: async (params: any) => {
            await search_wrap(search_baidu, params);
        },
        search_sogou: async (params: any) => {
            await search_wrap(search_sogou, params);
        },
        search_quark: async (params: any) => {
            await search_wrap(search_quark, params);
        },
        combined_search: async (params: any) => {
            await search_wrap(combined_search, params);
        }
    };
})();

// 导出所有函数
exports.search_bing = various_search.search_bing;
exports.search_baidu = various_search.search_baidu;
exports.search_sogou = various_search.search_sogou;
exports.search_quark = various_search.search_quark;
exports.combined_search = various_search.combined_search;

async function main() {
    console.log("开始测试各搜索平台...");
    const testQuery = "人工智能最新发展";

    various_search.combined_search({ query: testQuery, platforms: "bing,baidu" });
}

// 导出main函数
exports.main = main; 
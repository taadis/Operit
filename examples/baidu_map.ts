/*
METADATA
{
    "name": "baidu_map",
    "description": "百度地图工具集合，提供POI（兴趣点）和AOI（兴趣区域）数据获取接口。通过调用百度地图API，支持按名称、类别或地理范围查询地点信息、获取详细的POI数据和AOI边界坐标，助力地理信息系统应用开发、位置服务和空间数据分析。",
    "tools": [
        {
            "name": "search_poi",
            "description": "搜索百度地图兴趣点(POI)信息",
            "parameters": [
                {
                    "name": "keyword",
                    "description": "搜索关键词，如地名、商场名称等",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "city_code",
                    "description": "城市代码，如北京为131，默认全国范围",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "page",
                    "description": "结果页码，默认为0（第一页）",
                    "type": "number",
                    "required": false
                },
                {
                    "name": "page_size",
                    "description": "每页结果数量，默认为10",
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "get_poi_detail",
            "description": "获取特定POI的详细信息",
            "parameters": [
                {
                    "name": "uid",
                    "description": "POI的唯一ID",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "search_aoi",
            "description": "搜索百度地图兴趣区域(AOI)信息",
            "parameters": [
                {
                    "name": "keyword",
                    "description": "搜索关键词，如商场、小区名称等",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "city_code",
                    "description": "城市代码，如北京为131，默认全国范围",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "city_name",
                    "description": "城市名称，如"北京"，允许直接传入城市名",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "get_aoi_boundary",
            "description": "获取特定AOI的边界坐标",
            "parameters": [
                {
                    "name": "uid",
                    "description": "AOI的唯一ID",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "search_nearby",
            "description": "搜索指定坐标附近的POI",
            "parameters": [
                {
                    "name": "keyword",
                    "description": "搜索关键词，可选",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "longitude",
                    "description": "中心点经度",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "latitude",
                    "description": "中心点纬度",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "radius",
                    "description": "搜索半径，单位为米，默认1000",
                    "type": "number",
                    "required": false
                }
            ]
        }
    ],
    "category": "NETWORK"
}
*/

const baiduMap = (function () {
    // 常用城市编码
    const CITY_CODES = {
        "北京": "131",
        "上海": "289",
        "广州": "257",
        "深圳": "340",
        "杭州": "179",
        "南京": "315",
        "武汉": "218",
        "成都": "75",
        "重庆": "132",
        "西安": "233",
        "全国": "1" // 默认值
    };

    // 请求头配置
    const HEADERS = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'Host': 'map.baidu.com',
        'Pragma': 'no-cache',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'Sec-Fetch-User': '?1',
        'Upgrade-Insecure-Requests': '1',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0',
        'sec-ch-ua': '"Chromium";v="136", "Microsoft Edge";v="136", "Not.A/Brand";v="99"',
        'sec-ch-ua-mobile': '?0',
        'sec-ch-ua-platform': '"Windows"',
        'Referer': 'https://map.baidu.com/'
    };

    /**
     * 创建HTTP客户端
     * 使用OkHttp库实现网络请求
     */
    function createHttpClient() {
        return OkHttp.newBuilder()
            .connectTimeout(10000)
            .readTimeout(30000)
            .writeTimeout(15000)
            .followRedirects(true)
            .build();
    }

    /**
     * 对中文进行URL编码
     */
    function encodeURIComponentSafe(str: string): string {
        try {
            return encodeURIComponent(str);
        } catch (e) {
            console.error(`编码失败: ${e.message}`);
            return str;
        }
    }

    /**
     * 使用OkHttp发起GET请求
     */
    async function httpGet(url: string): Promise<any> {
        try {
            const client = createHttpClient();
            const response = await client.get(url, HEADERS);

            if (!response.isSuccessful()) {
                throw new Error(`请求失败: ${response.statusCode} ${response.statusMessage}`);
            }

            try {
                const jsonResponse = await response.json();
                // Debug: Registrar estructura de respuesta para ayudar en depuración
                console.log(`API响应结构: ${JSON.stringify(Object.keys(jsonResponse))}`);
                return jsonResponse;
            } catch (e) {
                console.error(`解析JSON失败: ${e.message}`);
                return response.content;
            }
        } catch (e) {
            console.error(`网络请求错误: ${e.message}`);
            throw e;
        }
    }

    /**
     * 搜索百度地图POI信息
     */
    async function search_poi(params: {
        keyword: string;
        city_code?: string | number;
        page?: number | string;
        page_size?: number | string;
    }): Promise<any> {
        try {
            // 参数处理
            const keyword = params.keyword;
            if (!keyword) {
                throw new Error("关键词不能为空");
            }

            // 处理城市编码
            let cityCode = params.city_code || "1"; // 默认全国
            if (typeof cityCode === 'string' && CITY_CODES[cityCode]) {
                cityCode = CITY_CODES[cityCode];
            }

            // 处理分页
            const page = parseInt(String(params.page || 0));
            const pageSize = parseInt(String(params.page_size || 10));

            // 构建URL
            const encodedKeyword = encodeURIComponentSafe(keyword);
            const url = `https://map.baidu.com/?newmap=1&qt=s&da_src=searchBox.button&wd=${encodedKeyword}&c=${cityCode}&pn=${page}&rn=${pageSize}`;

            console.log(`搜索POI: ${keyword}, 城市代码: ${cityCode}, 页码: ${page}`);

            // 发起请求
            const result = await httpGet(url);

            // 处理响应
            if (!result || !result.result || !result.result.content) {
                return {
                    success: true,
                    keyword: keyword,
                    city_code: cityCode,
                    page: page,
                    page_size: pageSize,
                    total: 0,
                    pois: []
                };
            }

            // 提取POI信息
            const pois = result.result.content.map(item => ({
                uid: item.uid || "",
                name: item.name || "",
                address: item.addr || "",
                province: item.province || "",
                city: item.city || "",
                area: item.area || "",
                telephone: item.tel || "",
                type: item.std_tag?.primary_industry || "",
                subtype: item.std_tag?.secondary_industry || "",
                longitude: item.x || 0,
                latitude: item.y || 0,
                location: {
                    lng: item.x || 0,
                    lat: item.y || 0
                },
                detail_url: item.ext?.detail_info?.detailUrl || ""
            }));

            return {
                success: true,
                keyword: keyword,
                city_code: cityCode,
                page: page,
                page_size: pageSize,
                total: result.result.total || 0,
                pois: pois
            };
        } catch (error) {
            console.error(`[search_poi] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `搜索POI失败: ${error.message}`,
                keyword: params.keyword
            };
        }
    }

    /**
     * 获取POI详细信息
     */
    async function get_poi_detail(params: { uid: string }): Promise<any> {
        try {
            const uid = params.uid;
            if (!uid) {
                throw new Error("POI的UID不能为空");
            }

            // 构建URL - 使用百度地图详情接口
            const url = `https://map.baidu.com/?qt=inf&uid=${uid}`;

            console.log(`获取POI详情: ${uid}`);

            // 发起请求
            const result = await httpGet(url);

            // 处理响应
            if (!result || !result.content || !result.content.ext) {
                return {
                    success: false,
                    message: "未找到POI详情",
                    uid: uid
                };
            }

            const content = result.content;
            const detailInfo = content.ext.detail_info || {};

            return {
                success: true,
                uid: uid,
                name: content.name || "",
                address: content.addr || "",
                telephone: detailInfo.phone || content.tel || "",
                website: detailInfo.website || "",
                business_hours: detailInfo.shop_hours || "",
                overall_rating: detailInfo.overall_rating || 0,
                price: detailInfo.price || "",
                introduction: detailInfo.introduction || "",
                tags: detailInfo.tag || [],
                images: detailInfo.image ? detailInfo.image.map(img => img.url) : [],
                location: {
                    lng: content.x || 0,
                    lat: content.y || 0
                },
                raw_data: content
            };
        } catch (error) {
            console.error(`[get_poi_detail] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `获取POI详情失败: ${error.message}`,
                uid: params.uid
            };
        }
    }

    /**
     * 搜索百度地图AOI信息
     * 参考 https://blog.csdn.net/Jacey_cai/article/details/131524758
     */
    async function search_aoi(params: {
        keyword: string;
        city_code?: string | number;
        city_name?: string;  // 新增参数，允许直接传入城市名
    }): Promise<any> {
        try {
            // 参数处理
            const keyword = params.keyword;
            if (!keyword) {
                throw new Error("关键词不能为空");
            }

            // 处理城市编码 - 改进版
            let cityCode: string | number = "1"; // 默认全国

            if (params.city_code) {
                // 直接使用提供的城市编码
                cityCode = params.city_code;
            } else if (params.city_name) {
                // 使用动态获取城市编码功能
                cityCode = await getCityCode(params.city_name);
            } else if (typeof cityCode === 'string' && CITY_CODES[cityCode]) {
                cityCode = CITY_CODES[cityCode];
            }

            // 构建URL - 使用搜索API
            const encodedKeyword = encodeURIComponentSafe(keyword);
            const url = `https://map.baidu.com/?newmap=1&qt=s&da_src=searchBox.button&wd=${encodedKeyword}&c=${cityCode}`;

            console.log(`搜索AOI: ${keyword}, 城市代码: ${cityCode}${params.city_name ? ' (城市名:' + params.city_name + ')' : ''}`);

            // 发起请求
            const result = await httpGet(url);

            // 记录完整响应以便调试
            console.log(`AOI搜索结果结构: ${JSON.stringify(Object.keys(result || {}))}`);

            // 主要结构检查 (result.result 或 result.content 或其他可能的结构)
            const dataContent = result?.result?.content ||
                result?.content ||
                result?.place_info?.content ||
                [];

            // 如果没有找到任何内容数据结构，返回空结果
            if (!dataContent || dataContent.length === 0) {
                console.log(`搜索结果为空或格式不符合预期: ${JSON.stringify(result)}`);
                return {
                    success: true,
                    keyword: keyword,
                    city_code: cityCode,
                    city_name: params.city_name,
                    total: 0,
                    aois: []
                };
            }

            // 定义AOI数据结构类型
            type AoiItem = {
                uid: string;
                name: string;
                address: string;
                type: string;
                has_geo_data: boolean;
                center: {
                    lng: number;
                    lat: number;
                };
                detail_url: string;
                raw_data: any;
            };

            // 尝试多种路径查找AOI数据
            let potentialAois: AoiItem[] = [];
            if (Array.isArray(dataContent)) {
                console.log(`找到${dataContent.length}个潜在AOI条目`);
                // 筛选含有坐标或边界数据的地点
                potentialAois = dataContent
                    .filter(item => {
                        // 检查各种可能包含地理信息的字段
                        return (item.geo && item.geo.length > 0) ||
                            (item.x && item.y) ||
                            (item.point && item.point.x && item.point.y) ||
                            (item.ext && item.ext.detail_info && item.ext.detail_info.point) ||
                            (item.uid); // 至少有UID，后续可尝试获取边界
                    })
                    .map(item => {
                        // 提取位置信息（考虑多种可能的数据结构）
                        let lng = item.x ||
                            (item.point && item.point.x) ||
                            (item.ext && item.ext.detail_info && item.ext.detail_info.point && item.ext.detail_info.point.x) ||
                            0;
                        let lat = item.y ||
                            (item.point && item.point.y) ||
                            (item.ext && item.ext.detail_info && item.ext.detail_info.point && item.ext.detail_info.point.y) ||
                            0;

                        return {
                            uid: item.uid || "",
                            name: item.name || "",
                            address: item.addr || "",
                            type: (item.std_tag && item.std_tag.primary_industry) ||
                                (item.type) ||
                                (item.catalog_name) ||
                                "",
                            has_geo_data: !!(item.geo && item.geo.length > 0),
                            center: {
                                lng: lng,
                                lat: lat
                            },
                            detail_url: (item.ext && item.ext.detail_info && item.ext.detail_info.detailUrl) || "",
                            raw_data: item // 保留原始数据以便进一步分析
                        } as AoiItem;
                    });
            } else if (typeof dataContent === 'object') {
                // 处理单个结果对象
                console.log(`找到单个AOI条目`);
                if (dataContent.uid) {
                    let lng = dataContent.x ||
                        (dataContent.point && dataContent.point.x) ||
                        (dataContent.ext && dataContent.ext.detail_info && dataContent.ext.detail_info.point && dataContent.ext.detail_info.point.x) ||
                        0;
                    let lat = dataContent.y ||
                        (dataContent.point && dataContent.point.y) ||
                        (dataContent.ext && dataContent.ext.detail_info && dataContent.ext.detail_info.point && dataContent.ext.detail_info.point.y) ||
                        0;

                    potentialAois.push({
                        uid: dataContent.uid,
                        name: dataContent.name || "",
                        address: dataContent.addr || "",
                        type: (dataContent.std_tag && dataContent.std_tag.primary_industry) ||
                            (dataContent.type) ||
                            (dataContent.catalog_name) ||
                            "",
                        has_geo_data: !!(dataContent.geo && dataContent.geo.length > 0),
                        center: {
                            lng: lng,
                            lat: lat
                        },
                        detail_url: (dataContent.ext && dataContent.ext.detail_info && dataContent.ext.detail_info.detailUrl) || "",
                        raw_data: dataContent
                    } as AoiItem);
                }
            }

            console.log(`找到${potentialAois.length}个AOI结果`);

            return {
                success: true,
                keyword: keyword,
                city_code: cityCode,
                city_name: params.city_name,
                total: potentialAois.length,
                aois: potentialAois
            };
        } catch (error) {
            console.error(`[search_aoi] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `搜索AOI失败: ${error.message}`,
                keyword: params.keyword,
                city_name: params.city_name
            };
        }
    }

    /**
     * 获取AOI边界坐标
     */
    async function get_aoi_boundary(params: { uid: string }): Promise<any> {
        try {
            const uid = params.uid;
            if (!uid) {
                throw new Error("AOI的UID不能为空");
            }

            // 构建URL - 使用百度地图地点详情接口 (尝试两种可能的接口)
            const url = `https://map.baidu.com/?qt=ext&uid=${uid}`;
            const backupUrl = `https://map.baidu.com/?qt=inf&uid=${uid}`;

            console.log(`获取AOI边界: ${uid}`);

            // 发起请求
            let result = await httpGet(url);

            // 记录完整响应以便调试
            console.log(`AOI边界结果结构: ${JSON.stringify(Object.keys(result || {}))}`);

            // 如果主接口没返回地理数据，尝试备用接口
            if (!result || !result.content || !result.content.geo) {
                console.log(`主接口未返回地理数据，尝试备用接口: ${backupUrl}`);
                // 增加延迟避免请求过快
                await Tools.System.sleep(500);
                result = await httpGet(backupUrl);
                console.log(`备用接口响应结构: ${JSON.stringify(Object.keys(result || {}))}`);
            }

            // 仍然没有找到地理数据，返回失败
            if (!result || !result.content) {
                return {
                    success: false,
                    message: "未找到AOI边界数据",
                    uid: uid
                };
            }

            const content = result.content;

            // 尝试多种可能的地理数据结构
            let geoData = content.geo;
            if (!geoData && content.ext && content.ext.geo) {
                geoData = content.ext.geo;
            } else if (!geoData && content.geodata) {
                geoData = content.geodata;
            }

            let boundary: Array<{ lng: number, lat: number }> = [];

            if (Array.isArray(geoData)) {
                // 尝试解析多边形边界
                boundary = geoData.map(point => ({
                    lng: point.x || (typeof point[0] !== 'undefined' ? point[0] : 0),
                    lat: point.y || (typeof point[1] !== 'undefined' ? point[1] : 0)
                }));
                console.log(`解析到${boundary.length}个边界点`);
            } else if (typeof geoData === 'string') {
                // 有些接口返回字符串形式的坐标集
                try {
                    const coordPairs = geoData.split(';');
                    boundary = coordPairs.map(pair => {
                        const [x, y] = pair.split(',').map(parseFloat);
                        return { lng: x || 0, lat: y || 0 };
                    });
                    console.log(`从字符串解析到${boundary.length}个边界点`);
                } catch (e) {
                    console.error(`解析字符串坐标失败: ${e.message}`);
                }
            }

            return {
                success: true,
                uid: uid,
                name: content.name || "",
                address: content.addr || "",
                center: {
                    lng: content.x || (content.point && content.point.x) || 0,
                    lat: content.y || (content.point && content.point.y) || 0
                },
                boundary: boundary,
                point_count: boundary.length,
                raw_data: content
            };
        } catch (error) {
            console.error(`[get_aoi_boundary] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `获取AOI边界失败: ${error.message}`,
                uid: params.uid
            };
        }
    }

    /**
     * 搜索附近POI
     */
    async function search_nearby(params: {
        keyword?: string;
        longitude: number | string;
        latitude: number | string;
        radius?: number | string;
    }): Promise<any> {
        try {
            // 参数处理
            const longitude = parseFloat(String(params.longitude));
            const latitude = parseFloat(String(params.latitude));

            if (isNaN(longitude) || isNaN(latitude)) {
                throw new Error("经纬度坐标无效");
            }

            const keyword = params.keyword || "";
            const radius = parseInt(String(params.radius || 1000));

            // 构建URL - 使用百度地图周边搜索API
            let url = `https://map.baidu.com/?qt=rgeoc&b=${longitude - 0.05},${latitude - 0.05};${longitude + 0.05},${latitude + 0.05}&l=18`;

            if (keyword) {
                const encodedKeyword = encodeURIComponentSafe(keyword);
                url = `https://map.baidu.com/?qt=nb&wd=${encodedKeyword}&cen=${longitude},${latitude}&radius=${radius}&l=18`;
            }

            console.log(`搜索附近POI: 坐标(${longitude}, ${latitude}), 半径: ${radius}米`);

            // 发起请求
            const result = await httpGet(url);

            // 处理响应
            let pois = [];

            if (keyword && result && result.content && result.content.length > 0) {
                // 关键词搜索结果
                pois = result.content.map(item => ({
                    uid: item.uid || "",
                    name: item.name || "",
                    address: item.addr || "",
                    distance: item.dist || 0, // 距离中心点的距离
                    type: item.std_tag?.primary_industry || "",
                    telephone: item.tel || "",
                    location: {
                        lng: item.x || 0,
                        lat: item.y || 0
                    }
                }));
            } else if (result && result.content && result.content.poi_count > 0) {
                // 周边地点搜索结果
                pois = (result.content.poi || []).map(item => ({
                    uid: item.uid || "",
                    name: item.name || "",
                    address: item.addr || "",
                    distance: item.d || 0, // 距离中心点的距离
                    type: item.poiType || "",
                    location: {
                        lng: item.point && item.point.x ? item.point.x : 0,
                        lat: item.point && item.point.y ? item.point.y : 0
                    }
                }));
            }

            return {
                success: true,
                keyword: keyword,
                center: {
                    lng: longitude,
                    lat: latitude
                },
                radius: radius,
                count: pois.length,
                pois: pois
            };
        } catch (error) {
            console.error(`[search_nearby] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `搜索附近POI失败: ${error.message}`,
                center: {
                    lng: params.longitude,
                    lat: params.latitude
                }
            };
        }
    }

    /**
     * 包装函数 - 统一处理所有百度地图工具函数的返回结果
     */
    async function map_wrap<T>(
        func: (params: any) => Promise<any>,
        params: any,
        successMessage: string,
        failMessage: string,
        additionalInfo: string = ""
    ): Promise<void> {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));

            // 处理getCityCode参数特殊情况
            if (func === getCityCode && typeof params === 'string') {
                params = { city_name: params };
            }

            // 执行原始函数
            const result = await func(params);

            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));

            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined) return;

            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage,
                    additionalInfo: additionalInfo
                });
            } else {
                // 数据类型结果
                complete({
                    success: result.success !== false,
                    message: result.success !== false ? successMessage : (result.message || failMessage),
                    additionalInfo: additionalInfo,
                    data: result
                });
            }
        } catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);

            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalInfo: additionalInfo,
                error_stack: error.stack
            });
        }
    }

    /**
     * 测试和展示所有功能
     */
    async function main(): Promise<any> {
        try {
            const results: {
                poi_search?: any;
                poi_detail?: any;
                aoi_search?: any;
                aoi_direct?: any;
                city_code_test?: any;
                nearby_search?: any;
            } = {};

            // 测试城市编码获取功能
            console.log("测试城市编码获取功能...");
            try {
                // 测试几个不同类型的城市
                const cities = ["北京", "上海", "广州", "深圳", "成都", "西安", "昆明", "哈尔滨", "南宁"];

                // 定义城市编码结果类型
                type CityCodeResult = {
                    city: string;
                    code?: string;
                    is_cached?: boolean;
                    error?: string;
                };

                const cityCodeResults: CityCodeResult[] = [];

                // 只测试前5个城市，避免过多请求
                for (let i = 0; i < Math.min(5, cities.length); i++) {
                    const city = cities[i];
                    console.log(`测试获取城市编码 (${i + 1}/5): ${city}`);

                    try {
                        const cityCode = await getCityCode(city);
                        cityCodeResults.push({
                            city: city,
                            code: cityCode,
                            is_cached: CITY_CODES[city] !== undefined
                        });

                        console.log(`✓ 城市 "${city}" 的编码: ${cityCode}`);
                    } catch (error) {
                        cityCodeResults.push({
                            city: city,
                            error: `获取城市编码失败: ${error.message}`
                        });
                        console.log(`✗ 获取 "${city}" 编码失败: ${error.message}`);
                    }

                    // 避免请求过快
                    if (i < 4) {
                        await Tools.System.sleep(1000);
                    }
                }

                results.city_code_test = {
                    cities_tested: cityCodeResults.length,
                    results: cityCodeResults
                };

                console.log("✓ 城市编码获取测试完成");
            } catch (error) {
                results.city_code_test = { error: `城市编码获取测试失败: ${error.message}` };
                console.log("✗ 城市编码获取测试失败");
            }

            await Tools.System.sleep(2000);

            // 1. 测试POI搜索
            console.log("测试POI搜索...");
            try {
                const poiResult = await search_poi({
                    keyword: "北京大学",
                    city_code: "北京"
                });
                results.poi_search = poiResult;
                console.log("✓ POI搜索成功");
            } catch (error) {
                results.poi_search = { error: `POI搜索失败: ${error.message}` };
                console.log("✗ POI搜索失败");
            }

            // 延迟一下，避免请求过快
            await Tools.System.sleep(2000);

            // 2. 如果有POI搜索结果，测试获取POI详情
            if (results.poi_search && results.poi_search.success && results.poi_search.pois && results.poi_search.pois.length > 0) {
                const poiUid = results.poi_search.pois[0].uid;
                console.log(`测试获取POI详情: ${poiUid}...`);
                try {
                    const detailResult = await get_poi_detail({ uid: poiUid });
                    results.poi_detail = detailResult;
                    console.log("✓ POI详情获取成功");
                } catch (error) {
                    results.poi_detail = { error: `POI详情获取失败: ${error.message}` };
                    console.log("✗ POI详情获取失败");
                }
            }

            // 延迟一下，避免请求过快
            await Tools.System.sleep(2000);

            // 3. 测试AOI搜索 - 尝试多个不同类型的AOI
            const aoiKeywords = ["王府井", "大悦城", "颐和园", "天安门广场", "三里屯"];
            const aoiCities = ["北京", "131"];

            console.log("测试AOI搜索...");

            // 定义AOI搜索结果类型
            type AoiSearchResult = {
                keyword: string;
                result?: any;
                error?: string;
            };

            const aoiSearchResults: AoiSearchResult[] = [];

            // 尝试几个不同的AOI区域名称
            for (let i = 0; i < Math.min(3, aoiKeywords.length); i++) {
                const keyword = aoiKeywords[i];
                const cityCode = aoiCities[0]; // 使用北京作为测试

                console.log(`测试AOI搜索 (${i + 1}/${Math.min(3, aoiKeywords.length)}): ${keyword}, 城市: ${cityCode}`);

                try {
                    const aoiResult = await search_aoi({
                        keyword: keyword,
                        city_code: cityCode
                    });

                    aoiSearchResults.push({
                        keyword: keyword,
                        result: aoiResult
                    });

                    console.log(`✓ AOI搜索 "${keyword}" 成功，找到 ${aoiResult.aois?.length || 0} 条结果`);

                    // 如果找到了AOI结果，尝试获取边界
                    if (aoiResult.success && aoiResult.aois && aoiResult.aois.length > 0) {
                        // 延迟一下，避免请求过快
                        await Tools.System.sleep(1500);

                        const aoiUid = aoiResult.aois[0].uid;
                        console.log(`测试获取AOI边界: ${aoiUid} (${keyword})...`);

                        try {
                            const boundaryResult = await get_aoi_boundary({ uid: aoiUid });

                            // 添加到结果中
                            aoiResult.boundary = boundaryResult;

                            console.log(`✓ AOI边界获取成功，获取了 ${boundaryResult.point_count || 0} 个边界点`);
                        } catch (error) {
                            console.log(`✗ AOI边界获取失败: ${error.message}`);
                        }
                    }
                } catch (error) {
                    aoiSearchResults.push({
                        keyword: keyword,
                        error: `AOI搜索失败: ${error.message}`
                    });
                    console.log(`✗ AOI搜索 "${keyword}" 失败`);
                }

                // 避免请求过快，每次搜索后休息一下
                if (i < Math.min(2, aoiKeywords.length)) {
                    console.log(`等待3秒后继续测试下一个AOI...`);
                    await Tools.System.sleep(3000);
                }
            }

            // 保存AOI搜索结果
            results.aoi_search = aoiSearchResults;

            // 延迟一下，避免请求过快
            await Tools.System.sleep(2000);

            // 4. 测试新方法：直接通过名称获取AOI (getAoiByName)
            console.log("测试直接获取AOI信息 (getAoiByName)...");
            try {
                // 用三个不同的地点进行测试，每个都有不同特点
                const testPlaces = [
                    { name: "北京东方广场", city_name: "北京" },  // 商业区
                    { name: "北海公园", city_name: "北京" },      // 公园
                    { name: "石林风景区", city_name: "昆明" }     // 使用动态获取城市编码的地点
                ];

                // 定义AOI直接获取结果类型
                type DirectAoiResult = {
                    place: string;
                    result?: any;
                    error?: string;
                };

                const directAoiResults: DirectAoiResult[] = [];

                for (let i = 0; i < testPlaces.length; i++) {
                    const place = testPlaces[i];
                    console.log(`测试 getAoiByName (${i + 1}/${testPlaces.length}): ${place.name}, 城市: ${place.city_name}`);

                    try {
                        // 使用新方法
                        const aoi = await getAoiByName({
                            name: place.name,
                            city_name: place.city_name
                        });

                        directAoiResults.push({
                            place: place.name,
                            result: aoi
                        });

                        if (aoi.success) {
                            console.log(`✓ 直接获取 "${place.name}" AOI成功`);
                            console.log(`  名称: ${aoi.aoi_info?.name || '未知'}`);
                            console.log(`  地址: ${aoi.aoi_info?.address || '未知'}`);
                            console.log(`  城市编码: ${aoi.city_code}`);
                            console.log(`  边界点数: ${aoi.aoi_info?.boundary?.length || 0}`);
                        } else {
                            console.log(`✗ 直接获取 "${place.name}" AOI失败: ${aoi.message}`);
                        }
                    } catch (error) {
                        directAoiResults.push({
                            place: place.name,
                            error: `直接获取AOI失败: ${error.message}`
                        });
                        console.log(`✗ 直接获取 "${place.name}" AOI出错: ${error.message}`);
                    }

                    // 避免请求过快
                    if (i < testPlaces.length - 1) {
                        console.log(`等待3秒后继续测试下一个地点...`);
                        await Tools.System.sleep(3000);
                    }
                }

                results.aoi_direct = directAoiResults;
                console.log("✓ 直接获取AOI测试完成");
            } catch (error) {
                results.aoi_direct = { error: `直接获取AOI测试失败: ${error.message}` };
                console.log("✗ 直接获取AOI测试失败");
            }

            // 延迟一下，避免请求过快
            await Tools.System.sleep(2000);

            // 5. 测试附近搜索
            console.log("测试附近POI搜索...");
            try {
                // 使用北京天安门的大致坐标
                const nearbyResult = await search_nearby({
                    keyword: "餐厅",
                    longitude: 116.397428,
                    latitude: 39.90923,
                    radius: 1000
                });
                results.nearby_search = nearbyResult;
                console.log("✓ 附近POI搜索成功");
            } catch (error) {
                results.nearby_search = { error: `附近POI搜索失败: ${error.message}` };
                console.log("✗ 附近POI搜索失败");
            }

            // 返回所有测试结果
            return {
                message: "百度地图功能测试完成",
                test_results: results,
                timestamp: new Date().toISOString(),
                summary: "测试了城市编码获取、POI搜索、POI详情、AOI搜索、AOI边界、直接获取AOI和附近POI搜索功能。请查看各功能的测试结果。"
            };
        } catch (error) {
            return {
                success: false,
                message: `测试过程中发生错误: ${error.message}`
            };
        }
    }

    /**
     * 获取用户当前位置
     * 使用系统提供的位置API获取当前位置坐标
     */
    async function getCurrentLocation(): Promise<{ lng: number, lat: number } | null> {
        try {
            console.log("正在获取用户当前位置...");
            const locationResult = await Tools.System.getLocation();

            if (!locationResult) {
                console.error("获取位置失败:", "未知错误");
                return null;
            }

            // 获取成功，返回经纬度
            return {
                lng: locationResult.longitude,
                lat: locationResult.latitude
            };
        } catch (error) {
            console.error("获取位置出错:", error.message);
            return null;
        }
    }

    /**
     * 查找附近的特定类型地点
     * 高级封装函数，自动获取用户当前位置，并搜索附近指定类型的地点
     */
    async function findNearbyPlaces(params: {
        type: string;        // 地点类型，如"餐厅"、"超市"、"医院"等
        radius?: number;     // 搜索半径，默认1000米
        limit?: number;      // 返回结果数量限制，默认10个
        sortByDistance?: boolean;  // 是否按距离排序，默认true
    }): Promise<any> {
        try {
            // 获取用户当前位置
            const currentLocation = await getCurrentLocation();
            if (!currentLocation) {
                return {
                    success: false,
                    message: "无法获取当前位置信息",
                    places: []
                };
            }

            // 调用附近搜索API
            const nearbyResult = await search_nearby({
                keyword: params.type,
                longitude: currentLocation.lng,
                latitude: currentLocation.lat,
                radius: params.radius || 1000
            });

            if (!nearbyResult.success) {
                return nearbyResult;
            }

            // 处理结果
            let places = nearbyResult.pois || [];

            // 按距离排序
            if (params.sortByDistance !== false && places.length > 0) {
                places.sort((a, b) => (a.distance || 0) - (b.distance || 0));
            }

            // 限制返回数量
            if (params.limit && places.length > params.limit) {
                places = places.slice(0, params.limit);
            }

            // 返回处理后的结果
            return {
                success: true,
                current_location: currentLocation,
                type: params.type,
                radius: params.radius || 1000,
                places_count: places.length,
                places: places.map(poi => ({
                    name: poi.name,
                    address: poi.address,
                    distance: `${poi.distance}米`,
                    telephone: poi.telephone || "暂无",
                    location: poi.location
                }))
            };
        } catch (error) {
            console.error(`[findNearbyPlaces] 错误: ${error.message}`);
            return {
                success: false,
                message: `查找附近${params.type}失败: ${error.message}`,
                places: []
            };
        }
    }

    /**
     * 智能路线规划
     * 高级封装函数，根据用户当前位置和目的地名称，提供导航信息
     */
    async function planRoute(params: {
        destination: string;    // 目的地名称
        city_code?: string;     // 城市代码
        transport_mode?: "driving" | "walking" | "transit"; // 交通方式，默认driving
    }): Promise<any> {
        try {
            // 获取用户当前位置
            const currentLocation = await getCurrentLocation();
            if (!currentLocation) {
                return {
                    success: false,
                    message: "无法获取当前位置信息"
                };
            }

            // 先搜索目的地POI
            const poiResult = await search_poi({
                keyword: params.destination,
                city_code: params.city_code,
                page: 0,
                page_size: 1 // 只需要最匹配的一个结果
            });

            if (!poiResult.success || !poiResult.pois || poiResult.pois.length === 0) {
                return {
                    success: false,
                    message: `未能找到目的地: ${params.destination}`,
                    current_location: currentLocation
                };
            }

            const destination = poiResult.pois[0];
            const destLocation = destination.location;

            // 计算直线距离（简单估算，不是实际路程）
            const distance = calculateDistance(
                currentLocation.lat,
                currentLocation.lng,
                destLocation.lat,
                destLocation.lng
            );

            // 构建导航链接（使用百度地图APP或网页版导航）
            const mode = params.transport_mode || "driving";
            const navUrl = `https://api.map.baidu.com/direction?origin=${currentLocation.lat},${currentLocation.lng}&destination=${destLocation.lat},${destLocation.lng}&mode=${mode}&region=${poiResult.city_code}&output=html`;

            return {
                success: true,
                current_location: currentLocation,
                destination: {
                    name: destination.name,
                    address: destination.address,
                    location: destLocation
                },
                estimated_distance: `${(distance / 1000).toFixed(2)}公里`,
                estimated_duration: estimateDuration(distance, mode),
                transport_mode: mode,
                navigation_url: navUrl,
                suggestion: getSuggestion(distance, mode)
            };
        } catch (error) {
            console.error(`[planRoute] 错误: ${error.message}`);
            return {
                success: false,
                message: `路线规划失败: ${error.message}`
            };
        }
    }

    /**
     * 提供当前所在区域信息
     * 高级封装函数，根据用户位置提供周边环境分析
     */
    async function getCurrentAreaInfo(): Promise<any> {
        try {
            // 获取用户当前位置
            const currentLocation = await getCurrentLocation();
            if (!currentLocation) {
                return {
                    success: false,
                    message: "无法获取当前位置信息"
                };
            }

            // 获取周边地理信息
            const url = `https://map.baidu.com/?qt=rgeoc&b=${currentLocation.lng - 0.005},${currentLocation.lat - 0.005};${currentLocation.lng + 0.005},${currentLocation.lat + 0.005}&l=18`;
            const result = await httpGet(url);

            if (!result || !result.content) {
                return {
                    success: false,
                    message: "未能获取周边地理信息",
                    current_location: currentLocation
                };
            }

            // 提取位置信息
            const geoInfo = result.content;
            const address = geoInfo.address || "未知位置";
            const district = geoInfo.address_detail?.district || "";
            const street = geoInfo.address_detail?.street || "";
            const streetNumber = geoInfo.address_detail?.street_number || "";

            // 尝试获取周边POI信息
            const nearbyPois = await search_nearby({
                longitude: currentLocation.lng,
                latitude: currentLocation.lat,
                radius: 500
            });

            // 分析周边地点类型
            const poiTypes: Record<string, number> = {};
            if (nearbyPois.success && nearbyPois.pois && nearbyPois.pois.length > 0) {
                nearbyPois.pois.forEach(poi => {
                    if (poi.type) {
                        poiTypes[poi.type] = (poiTypes[poi.type] || 0) + 1;
                    }
                });
            }

            // 确定区域类型
            let areaType = "未知区域";
            let areaDescription = "无法确定当前区域特征";

            const typeCount = Object.entries(poiTypes).sort((a, b) => b[1] - a[1]);
            if (typeCount.length > 0) {
                const dominantType = typeCount[0][0];

                if (/商场|购物|超市/.test(dominantType)) {
                    areaType = "商业区";
                    areaDescription = "您所在区域是商业繁华地带，周边有多家商场和店铺";
                } else if (/住宅|小区|公寓/.test(dominantType)) {
                    areaType = "居住区";
                    areaDescription = "您所在区域是居民住宅区，环境较为安静";
                } else if (/办公|企业|公司/.test(dominantType)) {
                    areaType = "办公区";
                    areaDescription = "您所在区域是商务办公区，周边有多家企业和写字楼";
                } else if (/学校|大学|教育/.test(dominantType)) {
                    areaType = "教育区";
                    areaDescription = "您所在区域靠近教育机构，周边有学校或培训中心";
                } else if (/医院|诊所|医疗/.test(dominantType)) {
                    areaType = "医疗区";
                    areaDescription = "您所在区域靠近医疗机构，周边有医院或诊所";
                } else if (/公园|景点|旅游/.test(dominantType)) {
                    areaType = "休闲娱乐区";
                    areaDescription = "您所在区域是休闲娱乐场所，适合游玩和放松";
                } else if (/餐饮|餐厅|美食/.test(dominantType)) {
                    areaType = "餐饮区";
                    areaDescription = "您所在区域有丰富的餐饮选择，适合用餐";
                } else {
                    areaType = dominantType;
                    areaDescription = `您所在区域主要是${dominantType}集中的地方`;
                }
            }

            return {
                success: true,
                current_location: currentLocation,
                address: address,
                district: district,
                street: street + (streetNumber ? ` ${streetNumber}号` : ""),
                area_type: areaType,
                area_description: areaDescription,
                nearby_categories: typeCount.slice(0, 5).map(([type, count]) => ({
                    type: type,
                    count: count
                })),
                nearby_landmarks: nearbyPois.success && nearbyPois.pois ?
                    nearbyPois.pois.slice(0, 3).map(poi => ({
                        name: poi.name,
                        distance: `${poi.distance}米`,
                        type: poi.type
                    })) : []
            };
        } catch (error) {
            console.error(`[getCurrentAreaInfo] 错误: ${error.message}`);
            return {
                success: false,
                message: `获取当前区域信息失败: ${error.message}`
            };
        }
    }

    /**
     * 推荐附近设施
     * 高级封装函数，根据用户需求智能推荐当前位置周边设施
     */
    async function recommendNearbyFacilities(params: {
        purpose?: "eating" | "shopping" | "entertainment" | "accommodation" | "transportation";
        time_of_day?: string;  // 可选时间段：morning, noon, afternoon, evening, night
    }): Promise<any> {
        try {
            const purpose = params.purpose || "eating";
            const timeOfDay = params.time_of_day || getCurrentTimeOfDay();

            // 获取用户当前位置
            const currentLocation = await getCurrentLocation();
            if (!currentLocation) {
                return {
                    success: false,
                    message: "无法获取当前位置信息"
                };
            }

            // 根据目的和时间段确定要搜索的关键词
            const keywordMap = {
                eating: {
                    morning: "早餐 早点",
                    noon: "午餐 快餐",
                    afternoon: "下午茶 咖啡厅",
                    evening: "晚餐 餐厅",
                    night: "夜宵 烧烤"
                },
                shopping: {
                    morning: "超市 便利店",
                    noon: "商场 购物中心",
                    afternoon: "商场 购物中心",
                    evening: "超市 便利店",
                    night: "便利店 24小时"
                },
                entertainment: {
                    morning: "公园 博物馆",
                    noon: "电影院 购物中心",
                    afternoon: "景点 游乐场",
                    evening: "KTV 酒吧",
                    night: "酒吧 夜店"
                },
                accommodation: {
                    morning: "酒店 民宿",
                    noon: "酒店 旅馆",
                    afternoon: "酒店 公寓",
                    evening: "酒店 旅馆",
                    night: "快捷酒店 24小时"
                },
                transportation: {
                    morning: "地铁站 公交站",
                    noon: "出租车 网约车",
                    afternoon: "地铁站 公交站",
                    evening: "地铁站 出租车",
                    night: "网约车 出租车"
                }
            };

            const keyword = keywordMap[purpose][timeOfDay];

            // 搜索附近设施
            const nearbyResult = await search_nearby({
                keyword: keyword,
                longitude: currentLocation.lng,
                latitude: currentLocation.lat,
                radius: 1000
            });

            if (!nearbyResult.success || !nearbyResult.pois || nearbyResult.pois.length === 0) {
                return {
                    success: false,
                    message: `未找到符合条件的设施`,
                    purpose: purpose,
                    time_of_day: timeOfDay,
                    current_location: currentLocation
                };
            }

            // 处理结果，按距离排序
            let facilities = nearbyResult.pois;
            facilities.sort((a, b) => (a.distance || 0) - (b.distance || 0));

            // 生成推荐原因
            const generateReason = (poi, index) => {
                if (index === 0) return "距离最近，步行可达";
                if (poi.type.includes("品牌")) return "知名品牌，服务有保障";
                if (index < 3) return "距离较近，位置便捷";
                return "在您附近，可供选择";
            };

            const recommendations = facilities.slice(0, 5).map((poi, index) => ({
                name: poi.name,
                address: poi.address,
                distance: `${poi.distance}米`,
                estimated_time: `步行约${Math.ceil(poi.distance / 80)}分钟`,
                type: poi.type,
                contact: poi.telephone || "暂无联系方式",
                reason: generateReason(poi, index),
                location: poi.location
            }));

            const purposeText = {
                eating: "用餐",
                shopping: "购物",
                entertainment: "娱乐",
                accommodation: "住宿",
                transportation: "交通"
            };

            const timeText = {
                morning: "早晨",
                noon: "中午",
                afternoon: "下午",
                evening: "傍晚",
                night: "夜间"
            };

            return {
                success: true,
                purpose: purposeText[purpose],
                time_of_day: timeText[timeOfDay],
                current_location: currentLocation,
                keyword_used: keyword,
                recommendation_count: recommendations.length,
                recommendations: recommendations,
                suggestion: `为您推荐了${recommendations.length}个适合${timeText[timeOfDay]}${purposeText[purpose]}的地点，距离您最近的是${recommendations[0].name}，${recommendations[0].estimated_time}可到达。`
            };
        } catch (error) {
            console.error(`[recommendNearbyFacilities] 错误: ${error.message}`);
            return {
                success: false,
                message: `推荐附近设施失败: ${error.message}`
            };
        }
    }

    // 工具函数：计算两点间距离（米）
    function calculateDistance(lat1, lng1, lat2, lng2): number {
        const R = 6371000; // 地球半径，单位米
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLng = (lng2 - lng1) * Math.PI / 180;
        const a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // 工具函数：估算行程时间
    function estimateDuration(distance: number, mode: string): string {
        // 根据不同交通方式估算时间
        switch (mode) {
            case "walking":
                // 步行速度约4-5km/h
                const walkingMinutes = Math.ceil(distance / 1000 * 15);
                return `步行约${walkingMinutes}分钟`;
            case "transit":
                // 公共交通复杂，粗略估算
                const transitMinutes = Math.ceil(distance / 1000 * 6);
                return `乘坐公共交通约${transitMinutes}分钟`;
            case "driving":
            default:
                // 驾车速度取决于路况，市区平均30km/h
                const drivingMinutes = Math.ceil(distance / 1000 * 2);
                return `驾车约${drivingMinutes}分钟`;
        }
    }

    // 工具函数：根据距离和交通方式给出建议
    function getSuggestion(distance: number, mode: string): string {
        if (distance < 500) {
            return "目的地非常近，步行即可到达";
        } else if (distance < 2000) {
            return "距离适中，可步行或乘坐短途交通工具";
        } else if (distance < 5000) {
            return "距离较远，建议使用公共交通工具";
        } else {
            return "目的地较远，建议驾车或使用公共交通工具";
        }
    }

    // 工具函数：获取当前时间段
    function getCurrentTimeOfDay(): "morning" | "noon" | "afternoon" | "evening" | "night" {
        const hour = new Date().getHours();
        if (hour >= 5 && hour < 10) return "morning";
        if (hour >= 10 && hour < 14) return "noon";
        if (hour >= 14 && hour < 18) return "afternoon";
        if (hour >= 18 && hour < 22) return "evening";
        return "night";
    }

    /**
     * 按名称获取AOI详细信息和边界
     * 基于CSDN文章 https://blog.csdn.net/Jacey_cai/article/details/131524758
     * 实现了一个更专注于AOI获取的高级封装函数
     */
    async function getAoiByName(params: {
        name: string;        // AOI名称，如商场、景区、建筑名
        city_name?: string;  // 城市名，如"北京"
        city_code?: string;  // 城市代码，如"131"
        need_boundary?: boolean; // 是否需要获取边界坐标，默认true
    }): Promise<any> {
        try {
            console.log(`开始通过名称查找AOI: ${params.name}`);

            // 参数预处理
            const name = params.name;
            if (!name) {
                throw new Error("AOI名称不能为空");
            }

            // 城市处理 - 改进版，支持动态获取城市编码
            let cityCode = "1"; // 默认全国

            if (params.city_code) {
                // 直接使用提供的城市编码
                cityCode = params.city_code;
            } else if (params.city_name) {
                // 使用新函数动态获取城市编码
                cityCode = await getCityCode(params.city_name);
                console.log(`城市 "${params.city_name}" 对应的编码: ${cityCode}`);
            } else if (CITY_CODES[cityCode]) {
                cityCode = CITY_CODES[cityCode];
            }

            const needBoundary = params.need_boundary !== false;

            // 第一步：搜索AOI获取uid
            const searchResults = await search_aoi({
                keyword: name,
                city_code: cityCode,
                city_name: params.city_name // 传递城市名称以便在日志中显示
            });

            if (!searchResults.success || !searchResults.aois || searchResults.aois.length === 0) {
                return {
                    success: false,
                    message: `未找到名为"${name}"的AOI`,
                    name: name,
                    city_code: cityCode,
                    city_name: params.city_name
                };
            }

            console.log(`找到${searchResults.aois.length}个匹配"${name}"的AOI结果`);

            // 找出最匹配的AOI（通常是第一个结果）
            // 如果需要更精确匹配，可以在这里添加名称相似度比较逻辑
            const bestMatch = searchResults.aois[0];

            // 收集基本信息
            const aoiInfo = {
                uid: bestMatch.uid,
                name: bestMatch.name,
                address: bestMatch.address,
                type: bestMatch.type,
                center: bestMatch.center,
                has_geo_data: bestMatch.has_geo_data,
                boundary: []
            };

            // 如果需要获取边界且找到了uid
            if (needBoundary && bestMatch.uid) {
                // 增加延迟以避免请求过快被限制
                await Tools.System.sleep(1000);

                console.log(`开始获取AOI边界: ${bestMatch.uid} (${bestMatch.name})`);

                try {
                    const boundaryResult = await get_aoi_boundary({ uid: bestMatch.uid });

                    if (boundaryResult.success && boundaryResult.boundary) {
                        aoiInfo.boundary = boundaryResult.boundary;
                        console.log(`成功获取AOI边界，共${boundaryResult.boundary.length}个坐标点`);
                    } else {
                        console.log(`获取边界返回成功但未找到边界数据`);
                    }
                } catch (error) {
                    console.error(`获取AOI边界出错: ${error.message}`);
                    // 即使边界获取失败，仍然返回基本信息
                }
            }

            return {
                success: true,
                name: name,
                city_code: cityCode,
                city_name: params.city_name,
                aoi_info: aoiInfo,
                other_matches: searchResults.aois.length > 1 ?
                    searchResults.aois.slice(1, 5).map(aoi => ({
                        uid: aoi.uid,
                        name: aoi.name,
                        address: aoi.address
                    })) : []
            };

        } catch (error) {
            console.error(`[getAoiByName] 错误: ${error.message}`);
            return {
                success: false,
                message: `获取AOI失败: ${error.message}`,
                name: params.name,
                city_name: params.city_name
            };
        }
    }

    /**
     * 根据城市名称动态获取城市编码
     * @param cityName 城市名称，如"杭州"、"南京"等
     * @returns 返回城市编码字符串，如果找不到返回默认编码"1"(全国)
     */
    async function getCityCode(cityName: string): Promise<string> {
        try {
            console.log(`开始查询城市编码: ${cityName}`);

            // 如果已在本地映射表中，直接返回
            if (CITY_CODES[cityName]) {
                console.log(`本地映射表中找到城市"${cityName}"的编码: ${CITY_CODES[cityName]}`);
                return CITY_CODES[cityName];
            }

            // 使用百度地图API查询城市信息
            const encodedCityName = encodeURIComponentSafe(cityName);
            const url = `https://map.baidu.com/?newmap=1&qt=s&wd=${encodedCityName}&c=1`;

            console.log(`发送请求获取城市编码: ${url}`);
            const result = await httpGet(url);

            // 尝试从不同路径提取城市编码
            let cityCode = "1"; // 默认值

            // 路径1: current_city.code
            if (result && result.current_city && result.current_city.code) {
                cityCode = result.current_city.code.toString();
                console.log(`从current_city中找到城市编码: ${cityCode}`);
            }
            // 路径2: content[].area_code 或 city_id
            else if (result && result.content && Array.isArray(result.content) && result.content.length > 0) {
                for (const item of result.content) {
                    if (item.area_code) {
                        cityCode = item.area_code.toString();
                        console.log(`从content[].area_code中找到城市编码: ${cityCode}`);
                        break;
                    } else if (item.city_id) {
                        cityCode = item.city_id.toString();
                        console.log(`从content[].city_id中找到城市编码: ${cityCode}`);
                        break;
                    }
                }
            }
            // 路径3: result_code
            else if (result && result.result && result.result.code) {
                cityCode = result.result.code.toString();
                console.log(`从result.code中找到城市编码: ${cityCode}`);
            }
            // 路径4: result.city_id
            else if (result && result.result && result.result.city_id) {
                cityCode = result.result.city_id.toString();
                console.log(`从result.city_id中找到城市编码: ${cityCode}`);
            }

            // 如果没找到，尝试第二个API端点
            if (cityCode === "1") {
                // 使用城市搜索API
                const secondUrl = `https://map.baidu.com/?qt=cur&wd=${encodedCityName}`;
                console.log(`未找到编码，尝试第二个API端点: ${secondUrl}`);

                try {
                    // 增加延迟避免请求过快
                    await Tools.System.sleep(500);

                    const secondResult = await httpGet(secondUrl);
                    if (secondResult && secondResult.current_city && secondResult.current_city.code) {
                        cityCode = secondResult.current_city.code.toString();
                        console.log(`从第二个API获取到城市编码: ${cityCode}`);
                    }
                } catch (e) {
                    console.error(`第二个API请求失败: ${e.message}`);
                }
            }

            // 如果仍未找到，使用默认值
            if (cityCode === "1") {
                console.log(`未能找到城市"${cityName}"的编码，使用默认编码"1"(全国)`);
            } else {
                // 找到编码后，可以临时添加到CITY_CODES中供本次会话使用
                CITY_CODES[cityName] = cityCode;
                console.log(`已将城市"${cityName}"的编码${cityCode}添加到临时映射表`);
            }

            return cityCode;
        } catch (error) {
            console.error(`获取城市编码失败: ${error.message}`);
            return "1"; // 出错时返回默认编码(全国)
        }
    }

    return {
        // 基础API
        search_poi: async (params) => await map_wrap(
            search_poi,
            params,
            "POI搜索成功",
            "POI搜索失败"
        ),
        get_poi_detail: async (params) => await map_wrap(
            get_poi_detail,
            params,
            "获取POI详情成功",
            "获取POI详情失败"
        ),
        search_aoi: async (params) => await map_wrap(
            search_aoi,
            params,
            "AOI搜索成功",
            "AOI搜索失败"
        ),
        get_aoi_boundary: async (params) => await map_wrap(
            get_aoi_boundary,
            params,
            "获取AOI边界成功",
            "获取AOI边界失败"
        ),
        search_nearby: async (params) => await map_wrap(
            search_nearby,
            params,
            "附近POI搜索成功",
            "附近POI搜索失败"
        ),

        // 高级封装API - 更适合AI使用
        findNearbyPlaces: async (params) => await map_wrap(
            findNearbyPlaces,
            params,
            "查找附近地点成功",
            "查找附近地点失败",
            "基于您当前位置的周边地点查询"
        ),
        planRoute: async (params) => await map_wrap(
            planRoute,
            params,
            "路线规划成功",
            "路线规划失败",
            "从您当前位置到目的地的路线"
        ),
        getCurrentAreaInfo: async (params) => await map_wrap(
            getCurrentAreaInfo,
            params,
            "获取当前区域信息成功",
            "获取当前区域信息失败",
            "您所在区域的环境分析"
        ),
        recommendNearbyFacilities: async (params) => await map_wrap(
            recommendNearbyFacilities,
            params,
            "设施推荐成功",
            "设施推荐失败",
            "基于您当前情况的场所推荐"
        ),

        // 测试函数
        main: async (params) => await map_wrap(
            main,
            params,
            "测试完成",
            "测试失败"
        ),

        // 新增功能
        getAoiByName: async (params) => await map_wrap(
            getAoiByName,
            params,
            "获取AOI信息成功",
            "获取AOI信息失败"
        ),

        // 添加城市编码获取功能
        getCityCode: async (cityNameOrParams: string | { city_name: string }) => {
            let city_name: string;

            if (typeof cityNameOrParams === 'string') {
                city_name = cityNameOrParams;
            } else if (cityNameOrParams && typeof cityNameOrParams === 'object' && cityNameOrParams.city_name) {
                city_name = cityNameOrParams.city_name;
            } else {
                throw new Error("参数错误：需要提供城市名称");
            }

            const result = await getCityCode(city_name);
            return {
                success: true,
                city_name: city_name,
                city_code: result
            };
        }
    };
})();

// 逐个导出
exports.search_poi = baiduMap.search_poi;
exports.get_poi_detail = baiduMap.get_poi_detail;
exports.search_aoi = baiduMap.search_aoi;
exports.get_aoi_boundary = baiduMap.get_aoi_boundary;
exports.search_nearby = baiduMap.search_nearby;

// 导出高级封装API
exports.findNearbyPlaces = baiduMap.findNearbyPlaces;
exports.planRoute = baiduMap.planRoute;
exports.getCurrentAreaInfo = baiduMap.getCurrentAreaInfo;
exports.recommendNearbyFacilities = baiduMap.recommendNearbyFacilities;
exports.getAoiByName = baiduMap.getAoiByName;
exports.getCityCode = baiduMap.getCityCode;

exports.main = baiduMap.main; 
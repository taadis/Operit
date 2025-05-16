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
        'Accept': '*/*',
        'Accept-Encoding': 'gzip, deflate, br',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Connection': 'keep-alive',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
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
    function encodeURIComponentSafe(str) {
        try {
            return encodeURIComponent(str);
        }
        catch (e) {
            console.error(`编码失败: ${e.message}`);
            return str;
        }
    }
    /**
     * 使用OkHttp发起GET请求
     */
    async function httpGet(url) {
        try {
            const client = createHttpClient();
            const response = await client.get(url, HEADERS);
            if (!response.isSuccessful()) {
                throw new Error(`请求失败: ${response.statusCode} ${response.statusMessage}`);
            }
            try {
                return await response.json();
            }
            catch (e) {
                console.error(`解析JSON失败: ${e.message}`);
                return response.content;
            }
        }
        catch (e) {
            console.error(`网络请求错误: ${e.message}`);
            throw e;
        }
    }
    /**
     * 搜索百度地图POI信息
     */
    async function search_poi(params) {
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
            const pois = result.result.content.map(item => {
                var _a, _b, _c, _d;
                return ({
                    uid: item.uid || "",
                    name: item.name || "",
                    address: item.addr || "",
                    province: item.province || "",
                    city: item.city || "",
                    area: item.area || "",
                    telephone: item.tel || "",
                    type: ((_a = item.std_tag) === null || _a === void 0 ? void 0 : _a.primary_industry) || "",
                    subtype: ((_b = item.std_tag) === null || _b === void 0 ? void 0 : _b.secondary_industry) || "",
                    longitude: item.x || 0,
                    latitude: item.y || 0,
                    location: {
                        lng: item.x || 0,
                        lat: item.y || 0
                    },
                    detail_url: ((_d = (_c = item.ext) === null || _c === void 0 ? void 0 : _c.detail_info) === null || _d === void 0 ? void 0 : _d.detailUrl) || ""
                });
            });
            return {
                success: true,
                keyword: keyword,
                city_code: cityCode,
                page: page,
                page_size: pageSize,
                total: result.result.total || 0,
                pois: pois
            };
        }
        catch (error) {
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
    async function get_poi_detail(params) {
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
        }
        catch (error) {
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
     */
    async function search_aoi(params) {
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
            // 构建URL - 使用搜索API
            const encodedKeyword = encodeURIComponentSafe(keyword);
            const url = `https://map.baidu.com/?newmap=1&qt=s&da_src=searchBox.button&wd=${encodedKeyword}&c=${cityCode}`;
            console.log(`搜索AOI: ${keyword}, 城市代码: ${cityCode}`);
            // 发起请求
            const result = await httpGet(url);
            // 处理响应 - 提取AOI信息
            if (!result || !result.result || !result.result.content) {
                return {
                    success: true,
                    keyword: keyword,
                    city_code: cityCode,
                    total: 0,
                    aois: []
                };
            }
            // 筛选结果中可能包含的AOI信息
            const aois = result.result.content
                .filter(item => item.geo && item.geo.length > 0) // 有地理信息的才可能是AOI
                .map(item => {
                var _a;
                return ({
                    uid: item.uid || "",
                    name: item.name || "",
                    address: item.addr || "",
                    type: ((_a = item.std_tag) === null || _a === void 0 ? void 0 : _a.primary_industry) || "",
                    has_geo_data: item.geo && item.geo.length > 0 ? true : false,
                    center: {
                        lng: item.x || 0,
                        lat: item.y || 0
                    }
                });
            });
            return {
                success: true,
                keyword: keyword,
                city_code: cityCode,
                total: aois.length,
                aois: aois
            };
        }
        catch (error) {
            console.error(`[search_aoi] 错误: ${error.message}`);
            console.error(error.stack);
            return {
                success: false,
                message: `搜索AOI失败: ${error.message}`,
                keyword: params.keyword
            };
        }
    }
    /**
     * 获取AOI边界坐标
     */
    async function get_aoi_boundary(params) {
        try {
            const uid = params.uid;
            if (!uid) {
                throw new Error("AOI的UID不能为空");
            }
            // 构建URL - 使用百度地图地点详情接口
            const url = `https://map.baidu.com/?qt=ext&uid=${uid}`;
            console.log(`获取AOI边界: ${uid}`);
            // 发起请求
            const result = await httpGet(url);
            // 处理响应
            if (!result || !result.content || !result.content.geo) {
                return {
                    success: false,
                    message: "未找到AOI边界数据",
                    uid: uid
                };
            }
            const content = result.content;
            // 解析边界数据
            const geoData = content.geo;
            let boundary = [];
            if (Array.isArray(geoData)) {
                // 尝试解析多边形边界
                boundary = geoData.map(point => ({
                    lng: point.x || 0,
                    lat: point.y || 0
                }));
            }
            return {
                success: true,
                uid: uid,
                name: content.name || "",
                address: content.addr || "",
                center: {
                    lng: content.x || 0,
                    lat: content.y || 0
                },
                boundary: boundary,
                point_count: boundary.length,
                raw_data: content
            };
        }
        catch (error) {
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
    async function search_nearby(params) {
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
                pois = result.content.map(item => {
                    var _a;
                    return ({
                        uid: item.uid || "",
                        name: item.name || "",
                        address: item.addr || "",
                        distance: item.dist || 0, // 距离中心点的距离
                        type: ((_a = item.std_tag) === null || _a === void 0 ? void 0 : _a.primary_industry) || "",
                        telephone: item.tel || "",
                        location: {
                            lng: item.x || 0,
                            lat: item.y || 0
                        }
                    });
                });
            }
            else if (result && result.content && result.content.poi_count > 0) {
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
        }
        catch (error) {
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
    async function map_wrap(func, params, successMessage, failMessage, additionalInfo = "") {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));
            // 执行原始函数
            const result = await func(params);
            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));
            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined)
                return;
            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage,
                    additionalInfo: additionalInfo
                });
            }
            else {
                // 数据类型结果
                complete({
                    success: result.success !== false,
                    message: result.success !== false ? successMessage : (result.message || failMessage),
                    additionalInfo: additionalInfo,
                    data: result
                });
            }
        }
        catch (error) {
            // A详细记录错误信息
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
    async function main() {
        try {
            const results = {};
            // 1. 测试POI搜索
            console.log("测试POI搜索...");
            try {
                const poiResult = await search_poi({
                    keyword: "北京大学",
                    city_code: "北京"
                });
                results.poi_search = poiResult;
                console.log("✓ POI搜索成功");
            }
            catch (error) {
                results.poi_search = { error: `POI搜索失败: ${error.message}` };
                console.log("✗ POI搜索失败");
            }
            // 延迟一下，避免请求过快
            await Tools.System.sleep(1000);
            // 2. 如果有POI搜索结果，测试获取POI详情
            if (results.poi_search && results.poi_search.success && results.poi_search.pois && results.poi_search.pois.length > 0) {
                const poiUid = results.poi_search.pois[0].uid;
                console.log(`测试获取POI详情: ${poiUid}...`);
                try {
                    const detailResult = await get_poi_detail({ uid: poiUid });
                    results.poi_detail = detailResult;
                    console.log("✓ POI详情获取成功");
                }
                catch (error) {
                    results.poi_detail = { error: `POI详情获取失败: ${error.message}` };
                    console.log("✗ POI详情获取失败");
                }
            }
            // 延迟一下，避免请求过快
            await Tools.System.sleep(1000);
            // 3. 测试AOI搜索
            console.log("测试AOI搜索...");
            try {
                const aoiResult = await search_aoi({
                    keyword: "王府井",
                    city_code: "北京"
                });
                results.aoi_search = aoiResult;
                console.log("✓ AOI搜索成功");
            }
            catch (error) {
                results.aoi_search = { error: `AOI搜索失败: ${error.message}` };
                console.log("✗ AOI搜索失败");
            }
            // 延迟一下，避免请求过快
            await Tools.System.sleep(1000);
            // 4. 如果有AOI搜索结果，测试获取AOI边界
            if (results.aoi_search && results.aoi_search.success && results.aoi_search.aois && results.aoi_search.aois.length > 0) {
                const aoiUid = results.aoi_search.aois[0].uid;
                console.log(`测试获取AOI边界: ${aoiUid}...`);
                try {
                    const boundaryResult = await get_aoi_boundary({ uid: aoiUid });
                    results.aoi_boundary = boundaryResult;
                    console.log("✓ AOI边界获取成功");
                }
                catch (error) {
                    results.aoi_boundary = { error: `AOI边界获取失败: ${error.message}` };
                    console.log("✗ AOI边界获取失败");
                }
            }
            // 延迟一下，避免请求过快
            await Tools.System.sleep(1000);
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
            }
            catch (error) {
                results.nearby_search = { error: `附近POI搜索失败: ${error.message}` };
                console.log("✗ 附近POI搜索失败");
            }
            // 返回所有测试结果
            return {
                message: "百度地图功能测试完成",
                test_results: results,
                timestamp: new Date().toISOString(),
                summary: "测试了百度地图POI搜索、POI详情、AOI搜索、AOI边界和附近POI搜索功能。请查看各功能的测试结果。"
            };
        }
        catch (error) {
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
    async function getCurrentLocation() {
        try {
            console.log("正在获取用户当前位置...");
            const locationResult = await Tools.System.getCurrentLocation();
            if (!locationResult || !locationResult.success) {
                console.error("获取位置失败:", (locationResult === null || locationResult === void 0 ? void 0 : locationResult.message) || "未知错误");
                return null;
            }
            // 获取成功，返回经纬度
            return {
                lng: locationResult.longitude,
                lat: locationResult.latitude
            };
        }
        catch (error) {
            console.error("获取位置出错:", error.message);
            return null;
        }
    }
    /**
     * 查找附近的特定类型地点
     * 高级封装函数，自动获取用户当前位置，并搜索附近指定类型的地点
     */
    async function findNearbyPlaces(params) {
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
        }
        catch (error) {
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
    async function planRoute(params) {
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
            const distance = calculateDistance(currentLocation.lat, currentLocation.lng, destLocation.lat, destLocation.lng);
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
        }
        catch (error) {
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
    async function getCurrentAreaInfo() {
        var _a, _b, _c;
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
            const district = ((_a = geoInfo.address_detail) === null || _a === void 0 ? void 0 : _a.district) || "";
            const street = ((_b = geoInfo.address_detail) === null || _b === void 0 ? void 0 : _b.street) || "";
            const streetNumber = ((_c = geoInfo.address_detail) === null || _c === void 0 ? void 0 : _c.street_number) || "";
            // 尝试获取周边POI信息
            const nearbyPois = await search_nearby({
                longitude: currentLocation.lng,
                latitude: currentLocation.lat,
                radius: 500
            });
            // 分析周边地点类型
            const poiTypes = {};
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
                }
                else if (/住宅|小区|公寓/.test(dominantType)) {
                    areaType = "居住区";
                    areaDescription = "您所在区域是居民住宅区，环境较为安静";
                }
                else if (/办公|企业|公司/.test(dominantType)) {
                    areaType = "办公区";
                    areaDescription = "您所在区域是商务办公区，周边有多家企业和写字楼";
                }
                else if (/学校|大学|教育/.test(dominantType)) {
                    areaType = "教育区";
                    areaDescription = "您所在区域靠近教育机构，周边有学校或培训中心";
                }
                else if (/医院|诊所|医疗/.test(dominantType)) {
                    areaType = "医疗区";
                    areaDescription = "您所在区域靠近医疗机构，周边有医院或诊所";
                }
                else if (/公园|景点|旅游/.test(dominantType)) {
                    areaType = "休闲娱乐区";
                    areaDescription = "您所在区域是休闲娱乐场所，适合游玩和放松";
                }
                else if (/餐饮|餐厅|美食/.test(dominantType)) {
                    areaType = "餐饮区";
                    areaDescription = "您所在区域有丰富的餐饮选择，适合用餐";
                }
                else {
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
        }
        catch (error) {
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
    async function recommendNearbyFacilities(params) {
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
                if (index === 0)
                    return "距离最近，步行可达";
                if (poi.type.includes("品牌"))
                    return "知名品牌，服务有保障";
                if (index < 3)
                    return "距离较近，位置便捷";
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
        }
        catch (error) {
            console.error(`[recommendNearbyFacilities] 错误: ${error.message}`);
            return {
                success: false,
                message: `推荐附近设施失败: ${error.message}`
            };
        }
    }
    // 工具函数：计算两点间距离（米）
    function calculateDistance(lat1, lng1, lat2, lng2) {
        const R = 6371000; // 地球半径，单位米
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLng = (lng2 - lng1) * Math.PI / 180;
        const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    // 工具函数：估算行程时间
    function estimateDuration(distance, mode) {
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
    function getSuggestion(distance, mode) {
        if (distance < 500) {
            return "目的地非常近，步行即可到达";
        }
        else if (distance < 2000) {
            return "距离适中，可步行或乘坐短途交通工具";
        }
        else if (distance < 5000) {
            return "距离较远，建议使用公共交通工具";
        }
        else {
            return "目的地较远，建议驾车或使用公共交通工具";
        }
    }
    // 工具函数：获取当前时间段
    function getCurrentTimeOfDay() {
        const hour = new Date().getHours();
        if (hour >= 5 && hour < 10)
            return "morning";
        if (hour >= 10 && hour < 14)
            return "noon";
        if (hour >= 14 && hour < 18)
            return "afternoon";
        if (hour >= 18 && hour < 22)
            return "evening";
        return "night";
    }
    return {
        // 基础API
        search_poi: async (params) => await map_wrap(search_poi, params, "POI搜索成功", "POI搜索失败"),
        get_poi_detail: async (params) => await map_wrap(get_poi_detail, params, "获取POI详情成功", "获取POI详情失败"),
        search_aoi: async (params) => await map_wrap(search_aoi, params, "AOI搜索成功", "AOI搜索失败"),
        get_aoi_boundary: async (params) => await map_wrap(get_aoi_boundary, params, "获取AOI边界成功", "获取AOI边界失败"),
        search_nearby: async (params) => await map_wrap(search_nearby, params, "附近POI搜索成功", "附近POI搜索失败"),
        // 高级封装API - 更适合AI使用
        findNearbyPlaces: async (params) => await map_wrap(findNearbyPlaces, params, "查找附近地点成功", "查找附近地点失败", "基于您当前位置的周边地点查询"),
        planRoute: async (params) => await map_wrap(planRoute, params, "路线规划成功", "路线规划失败", "从您当前位置到目的地的路线"),
        getCurrentAreaInfo: async (params) => await map_wrap(getCurrentAreaInfo, params, "获取当前区域信息成功", "获取当前区域信息失败", "您所在区域的环境分析"),
        recommendNearbyFacilities: async (params) => await map_wrap(recommendNearbyFacilities, params, "设施推荐成功", "设施推荐失败", "基于您当前情况的场所推荐"),
        // 测试函数
        main: async (params) => await map_wrap(main, params, "测试完成", "测试失败")
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
exports.main = baiduMap.main;

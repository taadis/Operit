package com.ai.assistance.operit.data.mcp

import android.util.Base64
import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.GitHubConstants
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.OfficialMCPConstants
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** Handles all network operations for MCP repository data */
class MCPNetworkClient {

    companion object {
        private const val CONNECT_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 15000 // 15 seconds
    }

    /**
     * Fetches a page of MCP servers from GitHub issues
     *
     * @param page The page number to fetch
     * @param perPage Number of items per page
     * @return JSON response string or null if request failed
     */
    suspend fun fetchMCPServersPage(page: Int, perPage: Int): String? {
        try {
            // Build URL parameters - sort by comments to get most popular items
            val queryParams =
                    StringBuilder()
                            .append(
                                    "?${GitHubConstants.STATE_PARAM}=${GitHubConstants.DEFAULT_STATE}"
                            )
                            .append("&${GitHubConstants.PER_PAGE_PARAM}=$perPage")
                            .append("&${GitHubConstants.PAGE_PARAM}=$page")
                            .append("&${GitHubConstants.SORT_PARAM}=comments") // Sort by comments
                            .append("&${GitHubConstants.DIRECTION_PARAM}=desc")

            val pageUrl = "${GitHubConstants.ISSUES_ENDPOINT}$queryParams"
            Log.d(TAG, "Fetching page $page data (sorted by comments): $pageUrl")

            return executeGetRequest(pageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch page $page data", e)
            return null
        }
    }

    /**
     * Fetches a specific MCP server by issue ID
     *
     * @param issueId The GitHub issue ID
     * @return JSON response string or null if request failed
     */
    suspend fun fetchMCPServerByIssueId(issueId: String): String? {
        try {
            // Build URL for the specific issue
            val issueUrl = "${GitHubConstants.ISSUES_ENDPOINT}/$issueId"
            Log.d(TAG, "Requesting single issue API URL: $issueUrl")

            return executeGetRequest(issueUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch issue ID $issueId", e)
            return null
        }
    }

    /**
     * Performs a search for MCP servers using GitHub Search API
     *
     * @param query The search query
     * @param sortBy The field to sort by
     * @param sortDirection The direction to sort (asc/desc)
     * @param page The page number
     * @return JSON response string or null if request failed
     */
    suspend fun searchMCPServers(
            query: String,
            sortBy: String,
            sortDirection: String,
            page: Int
    ): String? {
        try {
            val searchQuery = buildSearchQuery(query)

            val url =
                    "${GitHubConstants.SEARCH_ENDPOINT}?${GitHubConstants.QUERY_PARAM}=$searchQuery&sort=$sortBy&order=$sortDirection&page=$page&per_page=30"

            Log.d(TAG, "Search URL: $url")

            return executeGetRequest(url)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search MCP servers", e)
            return null
        }
    }

    /**
     * Fetches repository information for a given GitHub repository URL
     *
     * @param repoUrl The full GitHub repository URL
     * @return JSON response string or null if request failed
     */
    suspend fun fetchRepositoryInfo(repoUrl: String): String? {
        try {
            // Parse repository URL to extract owner and repo name
            val repoUrlRegex = "https://github.com/([^/]+)/([^/]+)".toRegex()
            val match = repoUrlRegex.find(repoUrl) ?: return null

            val owner = match.groupValues[1]
            val repo = match.groupValues[2]

            // Build API request URL
            val apiUrl = "${GitHubConstants.API_BASE_URL}/repos/$owner/$repo"

            return executeGetRequest(apiUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch repository info for $repoUrl", e)
            return null
        }
    }

    /**
     * Fetches README content from the official MCP repository
     *
     * @return Decoded README content or null if request failed
     */
    suspend fun fetchOfficialRepoReadme(): String? {
        try {
            Log.d(
                    TAG,
                    "Fetching README content from official GitHub repo: ${OfficialMCPConstants.README_API_URL}"
            )

            val responseStr = executeGetRequest(OfficialMCPConstants.README_API_URL) ?: return null

            // Parse GitHub API JSON response
            val responseJson = JSONObject(responseStr)

            // Get README content from Base64 encoding
            val content = responseJson.optString("content", "")
            if (content.isNotBlank()) {
                // GitHub API returns Base64 content that may include newlines, need to remove them
                val cleanedBase64 = content.replace("\n", "")
                val decodedBytes = Base64.decode(cleanedBase64, Base64.DEFAULT)
                return String(decodedBytes)
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch official repo README", e)
            return null
        }
    }

    /**
     * Tests if a logo URL is accessible and is an image
     *
     * @param logoUrl The URL to test
     * @return true if the URL is accessible and is an image, false otherwise
     */
    suspend fun testLogoUrl(logoUrl: String): Boolean {
        try {
            val url = URL(logoUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "HEAD" // Only request headers, don't download content
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            val responseCode = connection.responseCode
            val contentType = connection.getHeaderField("Content-Type") ?: ""

            // Handle redirects
            if (responseCode in 300..399) {
                val newUrl = connection.getHeaderField("Location")
                if (!newUrl.isNullOrBlank()) {
                    // Close original connection
                    connection.disconnect()

                    // Create new connection
                    val redirectConnection = URL(newUrl).openConnection() as HttpURLConnection
                    redirectConnection.connectTimeout = CONNECT_TIMEOUT
                    redirectConnection.readTimeout = READ_TIMEOUT
                    redirectConnection.requestMethod = "HEAD"
                    redirectConnection.setRequestProperty("User-Agent", "Mozilla/5.0")

                    // Check redirected URL
                    val redirectResponseCode = redirectConnection.responseCode
                    val redirectContentType =
                            redirectConnection.getHeaderField("Content-Type") ?: ""

                    // Close connection
                    redirectConnection.disconnect()

                    // Verify redirected URL is accessible and is an image
                    return redirectResponseCode == HttpURLConnection.HTTP_OK &&
                            (redirectContentType.contains("image/") || isLikelyImageURL(newUrl))
                }
            }

            // Verify URL is accessible and is an image
            return responseCode == HttpURLConnection.HTTP_OK &&
                    (contentType.contains("image/") || isLikelyImageURL(logoUrl))
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Executes a GET request to the specified URL
     *
     * @param urlString The URL to request
     * @return Response body as string or null if request failed
     */
    private fun executeGetRequest(urlString: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                    GitHubConstants.ACCEPT_HEADER,
                    GitHubConstants.ACCEPT_JSON_VALUE
            )
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val responseCode = connection.responseCode

            // Handle GitHub API rate limiting
            if (responseCode == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0") {
                val resetTime = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
                val message =
                        if (resetTime != null) {
                            val resetTimeString =
                                    java.text.SimpleDateFormat(
                                                    "HH:mm:ss",
                                                    java.util.Locale.getDefault()
                                            )
                                            .format(java.util.Date(resetTime * 1000))
                            "GitHub API rate limited, try again after $resetTimeString"
                        } else {
                            "GitHub API rate limited, try again later"
                        }
                Log.e(TAG, message)
                return null
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                return response.toString()
            } else {
                // Handle HTTP errors
                val errorBody =
                        if (connection.errorStream != null) {
                            BufferedReader(InputStreamReader(connection.errorStream)).use {
                                it.readText()
                            }
                        } else {
                            "No error details"
                        }
                Log.e(TAG, "HTTP error $responseCode: $errorBody")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute GET request to $urlString", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Builds a search query string for GitHub Search API
     *
     * @param query The user's search query
     * @return Formatted search query string
     */
    private fun buildSearchQuery(query: String): String {
        val sb = StringBuilder()

        // Specify repository
        sb.append("repo:cline/mcp-marketplace")

        // Specify it's an Issue
        sb.append(" is:issue")

        // Specify status is open
        sb.append(" is:open")

        // Ensure it's a server - only get server submissions
        sb.append(" server")

        // Find issues with reactions preferentially - improve recommendation quality
        sb.append(" sort:reactions-desc")

        // Add user search keywords
        if (query.isNotBlank()) {
            // URL encode the query
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            sb.append(" $encodedQuery")
        }

        return sb.toString()
    }

    /**
     * Checks if a URL is likely an image URL (based on extension)
     *
     * @param url The URL to check
     * @return true if the URL likely points to an image, false otherwise
     */
    private fun isLikelyImageURL(url: String): Boolean {
        val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico")
        return imageExtensions.any { ext ->
            url.lowercase().endsWith(".$ext") || url.lowercase().contains(".$ext?")
        }
    }
}

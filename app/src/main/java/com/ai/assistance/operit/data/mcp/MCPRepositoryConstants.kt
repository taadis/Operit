package com.ai.assistance.operit.data.mcp

import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer

/** 储存 MCPRepository 模块使用的常量 */
object MCPRepositoryConstants {
    const val TAG = "MCPRepository"
    const val CACHE_DURATION_HOURS = 24
    const val MAX_PAGES = 10

    // Cline Marketplace 常量
    object GitHubConstants {
        const val API_BASE_URL = "https://api.github.com"
        const val ISSUES_ENDPOINT = "$API_BASE_URL/repos/cline/mcp-marketplace/issues"
        const val SEARCH_ENDPOINT = "$API_BASE_URL/search/issues"

        const val QUERY_PARAM = "q"
        const val PER_PAGE_PARAM = "per_page"
        const val PAGE_PARAM = "page"
        const val SORT_PARAM = "sort"
        const val DIRECTION_PARAM = "direction"
        const val STATE_PARAM = "state"

        const val ACCEPT_HEADER = "Accept"
        const val ACCEPT_JSON_VALUE = "application/vnd.github.v3+json"

        const val DEFAULT_PAGE_SIZE = 30
        const val DEFAULT_STATE = "open"
    }

    // MCP 官方仓库常量
    object OfficialMCPConstants {
        const val API_BASE_URL = "https://api.github.com"
        const val REPO_URL = "https://github.com/modelcontextprotocol/servers"
        const val README_API_URL =
            "$API_BASE_URL/repos/modelcontextprotocol/servers/contents/README.md"
        const val CACHE_FILE_NAME = "official_mcp_servers_cache.json"

        /** 官方服务器Logo映射 为官方服务器提供默认的Logo URL，以提高显示效果 */
        object OfficialServerLogos {
            private const val BASE_GITHUB_LOGO =
                "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/assets/logos/"
            private const val DEFAULT_LOGO =
                "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/assets/logo.png"

            // 官方服务器Logo映射表
            val LOGO_MAP =
                mapOf(
                    // 参考服务器
                    "AWS KB Retrieval" to "$BASE_GITHUB_LOGO/aws.png",
                    "Brave Search" to
                        "https://brave.com/static-assets/images/brave-logo-no-shadow.png",
                    "EverArt" to "$BASE_GITHUB_LOGO/everart.png",
                    "Everything" to "$BASE_GITHUB_LOGO/everything.png",
                    "Fetch" to "$BASE_GITHUB_LOGO/fetch.png",
                    "Filesystem" to "$BASE_GITHUB_LOGO/filesystem.png",
                    "Git" to
                        "https://git-scm.com/images/logos/downloads/Git-Icon-1788C.png",
                    "GitHub" to
                        "https://github.githubassets.com/assets/github-mark-9be88460eaa6.svg",
                    "GitLab" to
                        "https://about.gitlab.com/images/press/logo/png/gitlab-icon-rgb.png",
                    "Google Drive" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Google_Drive_icon_%282020%29.svg/1200px-Google_Drive_icon_%282020%29.svg.png",
                    "Google Maps" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/a/aa/Google_Maps_icon_%282020%29.svg/1200px-Google_Maps_icon_%282020%29.svg.png",
                    "Memory" to "$BASE_GITHUB_LOGO/memory.png",
                    "PostgreSQL" to
                        "https://www.postgresql.org/media/img/about/press/elephant.png",
                    "Puppeteer" to "https://pptr.dev/img/favicon.ico",
                    "Redis" to "https://redis.io/images/redis-logo.svg",
                    "Sentry" to
                        "https://sentry.io/_assets/logos/django-f6f336cde20615169bb2c3167e2ecd2c37809d488c553cfd3524024f3d6894b5.svg",
                    "Sequential Thinking" to
                        "$BASE_GITHUB_LOGO/sequential-thinking.png",
                    "Slack" to
                        "https://a.slack-edge.com/80588/marketing/img/icons/icon_slack_hash_colored.png",
                    "Sqlite" to
                        "https://www.sqlite.org/images/sqlite370_banner.gif",
                    "Time" to "$BASE_GITHUB_LOGO/time.png",

                    // 官方集成
                    "21st.dev Magic" to "https://21st.dev/logo.svg",
                    "Adfin" to "$BASE_GITHUB_LOGO/adfin.png",
                    "AgentQL" to "$BASE_GITHUB_LOGO/agentql.png",
                    "AgentRPC" to "$BASE_GITHUB_LOGO/agentrpc.png",
                    "Aiven" to
                        "https://aiven.io/images/site/aiven-logo-white.png",
                    "Apache IoTDB" to "https://iotdb.apache.org/img/logo.png",
                    "Apify" to "https://apify.com/img/apify-square.png",
                    "APIMatic MCP" to "$BASE_GITHUB_LOGO/apimatic.png",
                    "Arize Phoenix" to "$BASE_GITHUB_LOGO/arize-phoenix.png",
                    "Astra DB" to
                        "https://www.datastax.com/sites/default/files/2020-08/datastax-square-200-transparent.png",
                    "Atlan" to "$BASE_GITHUB_LOGO/atlan.png",
                    "Audiense Insights" to "$BASE_GITHUB_LOGO/audiense.png",
                    "AWS" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/Amazon_Web_Services_Logo.svg/1200px-Amazon_Web_Services_Logo.svg.png",
                    "Axiom" to "$BASE_GITHUB_LOGO/axiom.png",
                    "Azure" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Microsoft_Azure.svg/1200px-Microsoft_Azure.svg.png",
                    "Bankless Onchain" to "$BASE_GITHUB_LOGO/bankless.png",
                    "BICScan" to "$BASE_GITHUB_LOGO/bicscan.png",
                    "Bitrise" to
                        "https://www.bitrise.io/assets/logos/bitrise-logo-dark.svg",
                    "Box" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/5/57/Box%2C_Inc._logo.svg/1200px-Box%2C_Inc._logo.svg.png",
                    "Browserbase" to "$BASE_GITHUB_LOGO/browserbase.png",
                    "Chargebee" to
                        "https://chargebee.com/img/icons/chargebee-icon.svg",
                    "Chroma" to "https://www.trychroma.com/favicon.png",
                    "Chronulus AI" to "$BASE_GITHUB_LOGO/chronulus.png",
                    "CircleCI" to
                        "https://d3r49iyjzglexf.cloudfront.net/circleci-logo-stacked-fb-657e221fda1646a7e652c09c9fbfb2b0feb5d710089bb4d8e8c759d37a832694.png",
                    "ClickHouse" to "https://clickhouse.com/favicon.ico",
                    "Cloudflare" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/9/94/Cloudflare_Logo.png/1200px-Cloudflare_Logo.png",
                    "Codacy" to "$BASE_GITHUB_LOGO/codacy.png",
                    "CodeLogic" to "$BASE_GITHUB_LOGO/codelogic.png",
                    "Comet" to "$BASE_GITHUB_LOGO/comet.png",
                    "Convex" to "https://convex.dev/apple-touch-icon.png",
                    "Couchbase" to
                        "https://www.couchbase.com/wp-content/uploads/2022/08/CB-logo-mobile.svg",
                    "Dart" to "$BASE_GITHUB_LOGO/dart.png",
                    "DevHub" to "$BASE_GITHUB_LOGO/devhub.png",
                    "E2B" to "$BASE_GITHUB_LOGO/e2b.png",
                    "EduBase" to "$BASE_GITHUB_LOGO/edubase.png",
                    "Elasticsearch" to
                        "https://static-www.elastic.co/v3/assets/bltefdd0b53724fa2ce/blt36f2da8d650732a0/5d0823c3d8ff351753cbf8c9/logo-elasticsearch-32-color.svg",
                    "eSignatures" to "$BASE_GITHUB_LOGO/esignatures.png",
                    "Exa" to "https://exa.ai/favicon.ico",
                    "Fewsats" to "$BASE_GITHUB_LOGO/fewsats.png",
                    "Fibery" to "$BASE_GITHUB_LOGO/fibery.png",
                    "Financial Datasets" to
                        "$BASE_GITHUB_LOGO/financial-datasets.png",
                    "Firecrawl" to "$BASE_GITHUB_LOGO/firecrawl.png",
                    "Fireproof" to "$BASE_GITHUB_LOGO/fireproof.png",
                    "GibsonAI" to "$BASE_GITHUB_LOGO/gibsonai.png",
                    "Gitee" to "https://gitee.com/static/images/logo-black.svg",
                    "Gyazo" to "https://gyazo.com/favicon.ico",
                    "gotoHuman" to "$BASE_GITHUB_LOGO/gotohuman.png",
                    "Grafana" to
                        "https://grafana.com/static/img/menu/grafana2.svg",
                    "Graphlit" to "$BASE_GITHUB_LOGO/graphlit.png",
                    "GreptimeDB" to "$BASE_GITHUB_LOGO/greptimedb.png",
                    "Heroku" to
                        "https://brand.heroku.com/static/mediadata/heroku-logo-solid.png",
                    "Hologres" to "$BASE_GITHUB_LOGO/hologres.png",
                    "Hyperbrowser" to "$BASE_GITHUB_LOGO/hyperbrowser.png",
                    "IBM wxflows" to
                        "https://www.ibm.com/brand/experience-guides/developer/8f4e3cc2b5d52354a6d43c8edba1e3c9/02_8-bar-positive.svg",
                    "ForeverVM" to "$BASE_GITHUB_LOGO/forevervm.png",
                    "Inbox Zero" to "$BASE_GITHUB_LOGO/inbox-zero.png",
                    "Inkeep" to "$BASE_GITHUB_LOGO/inkeep.png",
                    "Integration App" to
                        "$BASE_GITHUB_LOGO/integration-app.png",
                    "JetBrains" to
                        "https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg",
                    "Kagi Search" to "https://kagi.com/kagi-search-logo.svg",
                    "Keboola" to "$BASE_GITHUB_LOGO/keboola.png",
                    "Klavis ReportGen" to "$BASE_GITHUB_LOGO/klavis.png",
                    "Lara Translate" to "$BASE_GITHUB_LOGO/lara-translate.png",
                    "Logfire" to "$BASE_GITHUB_LOGO/logfire.png",
                    "Langfuse Prompt Management" to
                        "https://www.langfuse.com/favicon.ico",
                    "Lingo.dev" to "$BASE_GITHUB_LOGO/lingo-dev.png",
                    "Mailgun" to
                        "https://www.mailgun.com/wp-content/uploads/2021/04/MG_Wordmark_RGB_Default-1.png",
                    "Make" to
                        "https://images.ctfassets.net/qqlj6g4ee76j/5Tnh9CJwZmKKSHVpQZ8ENJ/c8da843c511d1359a331e462a7f42d88/Make_Icon_White-BG.png",
                    "MCP Toolbox for Databases" to
                        "$BASE_GITHUB_LOGO/mcp-toolbox.png",
                    "Meilisearch" to "https://www.meilisearch.com/favicon.ico",
                    "Metoro" to "$BASE_GITHUB_LOGO/metoro.png",
                    "Milvus" to "https://milvus.io/favicon.png",
                    "Momento" to "$BASE_GITHUB_LOGO/momento.png",
                    "MotherDuck" to
                        "https://motherduck.com/images/logo-stacked-red.svg",
                    "Needle" to "$BASE_GITHUB_LOGO/needle.png",
                    "Neo4j" to
                        "https://dist.neo4j.com/wp-content/uploads/neo4j_logo_globe.png",
                    "Neon" to "https://neon.tech/favicon/apple-touch-icon.png",
                    "Notion" to
                        "https://upload.wikimedia.org/wikipedia/commons/4/45/Notion_app_logo.png",
                    "OceanBase" to "$BASE_GITHUB_LOGO/oceanbase.png",
                    "Octagon" to "$BASE_GITHUB_LOGO/octagon.png",
                    "OlaMaps" to "$BASE_GITHUB_LOGO/olamaps.png",
                    "Oxylabs" to "$BASE_GITHUB_LOGO/oxylabs.png",
                    "Paddle" to "$BASE_GITHUB_LOGO/paddle.png",
                    "PayPal" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/PayPal.svg/1200px-PayPal.svg.png",
                    "Perplexity" to "https://www.perplexity.ai/favicon.ico",
                    "Pinecone" to
                        "https://images.prismic.io/pinecone-site/e1bd2016-7a5f-4b2b-88af-0fa3d316b1d1_pinecone-favicon-compression.svg?auto=compress,format",
                    "Pinecone Assistant" to
                        "https://images.prismic.io/pinecone-site/e1bd2016-7a5f-4b2b-88af-0fa3d316b1d1_pinecone-favicon-compression.svg?auto=compress,format",
                    "Prisma" to
                        "https://prismalens.vercel.app/header/prisma-logo.svg",
                    "Qdrant" to "https://qdrant.tech/images/favicon.ico",
                    "Ramp" to "$BASE_GITHUB_LOGO/ramp.png",
                    "Raygun" to "$BASE_GITHUB_LOGO/raygun.png",
                    "Rember" to "$BASE_GITHUB_LOGO/rember.png",
                    "Riza" to "$BASE_GITHUB_LOGO/riza.png",
                    "Search1API" to "$BASE_GITHUB_LOGO/search1api.png",
                    "ScreenshotOne" to "$BASE_GITHUB_LOGO/screenshotone.png",
                    "Semgrep" to "$BASE_GITHUB_LOGO/semgrep.png",
                    "SingleStore" to "https://www.singlestore.com/favicon.ico",
                    "StarRocks" to "$BASE_GITHUB_LOGO/starrocks.png",
                    "Stripe" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/Stripe_Logo%2C_revised_2016.svg/2560px-Stripe_Logo%2C_revised_2016.svg.png",
                    "Tavily" to "https://tavily.com/apple-touch-icon.png",
                    "Thirdweb" to "$BASE_GITHUB_LOGO/thirdweb.png",
                    "Tinybird" to "$BASE_GITHUB_LOGO/tinybird.png",
                    "UnifAI" to "$BASE_GITHUB_LOGO/unifai.png",
                    "Unstructured" to "$BASE_GITHUB_LOGO/unstructured.png",
                    "Vectorize" to "$BASE_GITHUB_LOGO/vectorize.png",
                    "Verodat" to "$BASE_GITHUB_LOGO/verodat.png",
                    "VeyraX" to "$BASE_GITHUB_LOGO/veyrax.png",
                    "Xero" to
                        "https://www.xero.com/content/dam/xero/pilot-images/brand/logo-social.png",
                    "Zapier" to
                        "https://cdn.zappy.app/8f853364f9b383d9d99a8a404c1be371.png",
                    "ZenML" to "$BASE_GITHUB_LOGO/zenml.png",

                    // Additional entries for more MCP servers
                    "Anthropic" to
                        "https://assets-global.website-files.com/62a9205a776e712efa05e927/62e138e1a5e1595e0015fdb5_Frame%2037.svg",
                    "Claude" to
                        "https://assets-global.website-files.com/62a9205a776e712efa05e927/62e138e1a5e1595e0015fdb5_Frame%2037.svg",
                    "ChatGPT" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/0/04/ChatGPT_logo.svg/1200px-ChatGPT_logo.svg.png",
                    "OpenAI" to
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/OpenAI_Logo.svg/1280px-OpenAI_Logo.svg.png",
                    "Google Gemini" to
                        "https://upload.wikimedia.org/wikipedia/commons/f/f0/Google_Gemini_logo.svg",
                    "Hugging Face" to "https://huggingface.co/favicon.ico",
                    "LlamaIndex" to "https://www.llamaindex.ai/favicon.ico",
                    "LangChain" to
                        "https://python.langchain.com/img/favicon.ico",
                    "MongoDB" to
                        "https://www.mongodb.com/assets/images/global/favicon.ico",
                    "Weaviate" to "https://weaviate.io/img/site/favicon.ico",
                    "Supabase" to "https://supabase.com/favicon.ico",
                    "Vector" to "https://assets.vector.dev/vector-logo.svg",
                    "Jupyter" to "https://jupyter.org/favicon.ico",
                    "GitHub Copilot" to
                        "https://github.githubassets.com/assets/copilot-85b67f5c9a51.svg",
                    "Vercel" to
                        "https://assets.vercel.com/image/upload/front/favicon/vercel/180x180.png",
                    "Firebase" to
                        "https://www.gstatic.com/devrel-devsite/prod/vf0396724755d04dbab75050e6812ced8fb2ab11d424163433e2a80930107da57/firebase/images/favicon.png",
                    "VS Code" to "https://code.visualstudio.com/favicon.ico",
                    "DataDog" to "https://www.datadoghq.com/favicon.ico",
                    "New Relic" to "https://newrelic.com/favicon.ico",
                    "Snowflake" to
                        "https://www.snowflake.com/wp-content/themes/snowflake/img/favicon.ico",
                    "Deepgram" to "https://deepgram.com/favicon.ico",
                    "Weights & Biases" to "https://wandb.ai/favicon.ico",
                    "Databricks" to "https://www.databricks.com/favicon.ico",
                    "Weights & Biases" to "https://wandb.ai/favicon.ico",
                    "Modal" to "https://modal.com/favicon.ico",
                    "Deno" to "https://deno.land/favicon.ico",
                    "Bun" to "https://bun.sh/favicon.ico",
                    "Cloudflare Workers" to
                        "https://workers.cloudflare.com/favicon.ico",
                    "Digital Ocean" to
                        "https://www.digitalocean.com/favicon.ico",
                    "Kubernetes" to "https://kubernetes.io/images/favicon.png",
                    "Docker" to
                        "https://www.docker.com/sites/default/files/d8/2019-07/Moby-logo.png",
                    "Render" to "https://render.com/favicon.ico",
                    "Fly.io" to "https://fly.io/favicon.ico",
                    "MindsDB" to "https://mindsdb.com/favicon.ico",
                    "Metabase" to "https://www.metabase.com/images/favicon.ico",
                    "PlanetScale" to "https://planetscale.com/favicon.ico",
                    "CockroachDB" to
                        "https://www.cockroachlabs.com/favicon.ico",
                    "pgvector" to "https://github.com/pgvector.png",
                    "Cassandra" to "https://cassandra.apache.org/favicon.ico",
                    "Elasticsearch" to "https://www.elastic.co/favicon.ico",
                    "Solr" to
                        "https://solr.apache.org/assets/images/favicon.ico"
                )

            /**
             * 获取官方服务器的Logo URL
             *
             * @param serverName 服务器名称
             * @param category 服务器类别
             * @return Logo URL，如果没有指定的Logo，则返回默认Logo
             */
            fun getLogoUrl(serverName: String, category: String): String {
                // 0. 直接处理已知固定图片的服务器，避免任何处理逻辑，减少延迟
                val cleanServerName = serverName.trim()

                // 常用服务的直接映射，保证快速响应
                when (cleanServerName.lowercase()) {
                    "tavily" -> return "https://storage.googleapis.com/cline_public_images/tavily.jpg"
                    "brave search" -> return "https://storage.googleapis.com/cline_public_images/brave-search.png"
                    "aws kb retrieval" -> return "https://storage.googleapis.com/cline_public_images/aws.png"
                    "github" -> return "https://storage.googleapis.com/cline_public_images/github.png"
                    "slack" -> return "https://storage.googleapis.com/cline_public_images/slack.png"
                    "stripe" -> return "https://storage.googleapis.com/cline_public_images/stripe.png"
                    "neo4j" -> return "https://storage.googleapis.com/cline_public_images/Neo4j.png"
                    "time" -> return "https://storage.googleapis.com/cline_public_images/time.png"
                    "sentry" -> return "https://storage.googleapis.com/cline_public_images/sentry.png"
                    "supabase" -> return "https://storage.googleapis.com/cline_public_images/supabase.png"
                    "sequential thinking" -> return "https://storage.googleapis.com/cline_public_images/sequential-thinking.png"
                    "hyperbrowser" -> return "https://storage.googleapis.com/cline_public_images/hyperbrowser.png"
                    "google drive" -> return "https://storage.googleapis.com/cline_public_images/google-drive.png"
                    "google maps" -> return "https://storage.googleapis.com/cline_public_images/google-maps.png"
                }

                // 1. 先检查明确的映射，这是最优先的
                val explicitLogo = LOGO_MAP[serverName]
                if (explicitLogo != null) {
                    return explicitLogo
                }

                // 2. 为最常见的技术公司返回已知在大多数区域都很稳定的CDN URL
                val domainName = extractDomainName(serverName)
                if (domainName.isNotEmpty()) {
                    when (domainName) {
                        "github.com" -> return "https://github.githubassets.com/assets/github-mark-9be88460eaa6.svg"
                        "slack.com" -> return "https://a.slack-edge.com/80588/marketing/img/icons/icon_slack_hash_colored.png"
                        "stripe.com" -> return "https://storage.googleapis.com/cline_public_images/stripe.png" // 使用Cline的备份
                        "postgresql.org" -> return "https://www.postgresql.org/media/img/about/press/elephant.png"
                        "redis.io" -> return "https://storage.googleapis.com/cline_public_images/redis.png" // 使用Cline的备份
                        "perplexity.ai" -> return "https://storage.googleapis.com/cline_public_images/perplexity.jpg" // 使用Cline的备份
                        "huggingface.co" -> return "https://huggingface.co/favicon.ico"
                        "notion.so" -> return "https://storage.googleapis.com/cline_public_images/notion.png" // 使用Cline的备份
                    }
                }

                // 3. 使用 Cline 托管图片的最可能匹配格式

                // a) 保持原始大小写，移除特殊字符 - 这通常是最匹配的
                val normalizedNamePreservingCase = cleanServerName.replace(Regex("[^a-zA-Z0-9]"), "")

                // b) 处理连字符版本，通常用于目录和存储名称
                val normalizedNameWithHyphens = cleanServerName.lowercase().replace(Regex("[^a-z0-9]"), "-")

                // c) 全小写版本，用于通用匹配
                val normalizedNameLowerCase = normalizedNamePreservingCase.lowercase()

                // 4. 基于实际可用图片返回最可能存在的URL (验证过多个服务，png格式通常更常见)
                // PNG格式优先 (大部分Cline托管图片是PNG)
                return "https://storage.googleapis.com/cline_public_images/${normalizedNameWithHyphens}.png"
            }

            /**
             * Attempts to extract a domain name from a service name For example,
             * "GitHub" would return "github.com"
             */
            private fun extractDomainName(serverName: String): String {
                // Remove common suffixes that aren't part of the domain
                val cleanName =
                    serverName
                        .replace(" API", "")
                        .replace(" MCP", "")
                        .replace(" Server", "")
                        .lowercase()
                        .trim()

                // Try to match known domains
                return when {
                    cleanName == "github" -> "github.com"
                    cleanName == "gitlab" -> "gitlab.com"
                    cleanName == "google drive" -> "drive.google.com"
                    cleanName == "google maps" -> "maps.google.com"
                    cleanName == "slack" -> "slack.com"
                    cleanName == "stripe" -> "stripe.com"
                    cleanName == "notion" -> "notion.so"
                    cleanName == "brave search" -> "brave.com"
                    cleanName == "postgresql" || cleanName == "postgres" ->
                        "postgresql.org"
                    cleanName == "zapier" -> "zapier.com"
                    cleanName.contains("redis") -> "redis.io"
                    cleanName.contains("perplexity") -> "perplexity.ai"
                    cleanName.contains("pinecone") -> "pinecone.io"
                    cleanName.contains("tavily") -> "tavily.com"
                    cleanName.contains("exa") -> "exa.ai"
                    cleanName.contains("openai") -> "openai.com"
                    cleanName.contains("anthropic") -> "anthropic.com"
                    cleanName.contains("axiom") -> "axiom.co"
                    cleanName.contains("hugging") -> "huggingface.co"
                    cleanName.contains("firebase") -> "firebase.google.com"
                    cleanName.contains("clickhouse") -> "clickhouse.com"
                    cleanName.contains("chroma") -> "trychroma.com"
                    cleanName.contains("qdrant") -> "qdrant.tech"
                    // Common domains for tech companies
                    else -> guessDomain(cleanName)
                }
            }

            /** Try to guess a domain based on company name patterns */
            private fun guessDomain(name: String): String {
                if (name.isBlank()) return ""

                // Remove non-alphanumeric characters
                val simpleName = name.replace(Regex("[^a-z0-9]"), "")
                if (simpleName.isBlank()) return ""

                // Database/data companies often use .io
                val dbKeywords =
                    listOf(
                        "db",
                        "data",
                        "base",
                        "sql",
                        "graph",
                        "vector",
                        "search",
                        "store"
                    )
                val aiKeywords =
                    listOf(
                        "ai",
                        "ml",
                        "llm",
                        "gpt",
                        "claude",
                        "language",
                        "model",
                        "intelligence"
                    )

                // Return empty because we'll fall back to GitHub logo path
                return ""
            }
        }

        /** 官方服务器列表 提供一份固定的官方服务器列表，作为GitHub API获取失败时的备选方案 */
        object OfficialServers {
            /** 获取硬编码的官方服务器列表 返回包含基本信息的官方服务器，保证UI中始终能看到官方服务器 */
            fun getEssentialOfficialServers():
                List<MCPServer> {
                val servers =
                    mutableListOf<
                        MCPServer>()

                // 参考服务器列表 - 基于官方README顺序
                addReferenceServers(servers)

                // 官方集成服务器列表 - 选择一些常用/重要的
                addOfficialIntegrationServers(servers)

                return servers
            }

            /** 添加参考服务器 */
            private fun addReferenceServers(
                servers:
                MutableList<
                    MCPServer>
            ) {
                // 参考服务器，参考README.md中的顺序
                addServer(
                    servers,
                    "AWS KB Retrieval",
                    "Retrieval from AWS Knowledge Base using Bedrock Agent Runtime",
                    "Reference",
                    true
                )
                addServer(
                    servers,
                    "Brave Search",
                    "Web and local search using Brave's Search API",
                    "Reference",
                    true
                )
                addServer(
                    servers,
                    "EverArt",
                    "AI image generation using various models",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Everything",
                    "Reference / test server with prompts, resources, and tools",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Fetch",
                    "Web content fetching and conversion for efficient LLM usage",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Filesystem",
                    "Secure file operations with configurable access controls",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Git",
                    "Tools to read, search, and manipulate Git repositories",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "GitHub",
                    "Repository management, file operations, and GitHub API integration",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "GitLab",
                    "GitLab API, enabling project management",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Google Drive",
                    "File access and search capabilities for Google Drive",
                    "Reference",
                    true
                )
                addServer(
                    servers,
                    "Google Maps",
                    "Location services, directions, and place details",
                    "Reference",
                    true
                )
                addServer(
                    servers,
                    "Memory",
                    "Knowledge graph-based persistent memory system",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "PostgreSQL",
                    "Read-only database access with schema inspection",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Puppeteer",
                    "Browser automation and web scraping",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Redis",
                    "Interact with Redis key-value stores",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Sentry",
                    "Retrieving and analyzing issues from Sentry.io",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Sequential Thinking",
                    "Dynamic and reflective problem-solving through thought sequences",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Slack",
                    "Channel management and messaging capabilities",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Sqlite",
                    "Database interaction and business intelligence capabilities",
                    "Reference",
                    false
                )
                addServer(
                    servers,
                    "Time",
                    "Time and timezone conversion capabilities",
                    "Reference",
                    false
                )
            }

            /** 添加官方集成服务器 */
            private fun addOfficialIntegrationServers(
                servers:
                MutableList<
                    MCPServer>
            ) {
                // 添加重要的官方集成（基于GitHub README）
                addServer(
                    servers,
                    "21st.dev Magic",
                    "Create crafted UI components inspired by the best 21st.dev design engineers",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Adfin",
                    "The only platform you need to get paid - all payments in one place, invoicing and accounting reconciliations",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "AgentQL",
                    "Enable AI agents to get structured data from unstructured web with AgentQL",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "AgentRPC",
                    "Connect to any function, any language, across network boundaries using AgentRPC",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Aiven",
                    "Navigate your Aiven projects and interact with PostgreSQL, Apache Kafka, ClickHouse and OpenSearch services",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Apache IoTDB",
                    "MCP Server for Apache IoTDB database and its tools",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Apify",
                    "Actors MCP Server: Use 3,000+ pre-built cloud tools for data extraction",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "APIMatic MCP",
                    "Validate OpenAPI specifications using APIMatic",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Arize Phoenix",
                    "Inspect traces, manage prompts, curate datasets, and run experiments using Arize Phoenix",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Astra DB",
                    "Comprehensive tools for managing collections and documents in a DataStax Astra DB NoSQL database",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Atlan",
                    "Interact with Atlan services through multiple tools",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Audiense Insights",
                    "Marketing insights and audience analysis from Audiense reports",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "AWS",
                    "Specialized MCP servers that bring AWS best practices directly to your development workflow",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Axiom",
                    "Query and analyze your Axiom logs, traces, and all other event data in natural language",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Azure",
                    "Access to key Azure services and tools like Azure Storage, Cosmos DB, and more",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Bankless Onchain",
                    "Query Onchain data, like ERC20 tokens, transaction history, smart contract state",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "BICScan",
                    "Risk score / asset holdings of EVM blockchain address (EOA, CA, ENS) and even domain names",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Bitrise",
                    "Chat with your builds, CI, and more",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Box",
                    "Interact with the Intelligent Content Management platform through Box AI",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Browserbase",
                    "Automate browser interactions in the cloud",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Chroma",
                    "Vector database for storing and retrieving embeddings",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "E2B",
                    "Run code in secure sandboxes hosted by E2B",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Exa",
                    "Search Engine made for AIs by Exa",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Notion",
                    "Interact with the Notion API",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Perplexity",
                    "An MCP server that connects to Perplexity's Sonar API, enabling real-time web-wide research",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Pinecone",
                    "Vector database for machine learning applications",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Redis",
                    "Interact with Redis key-value stores",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Slack",
                    "Channel management and messaging capabilities",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Stripe",
                    "Interact with Stripe API",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Tavily",
                    "Search engine for AI agents (search + extract) powered by Tavily",
                    "Official Integration",
                    true
                )
                addServer(
                    servers,
                    "Zapier",
                    "Connect your AI Agents to 8,000 apps instantly",
                    "Official Integration",
                    true
                )
            }

            /** 添加单个服务器到列表 */
            private fun addServer(
                servers:
                MutableList<
                    MCPServer>,
                name: String,
                description: String,
                category: String,
                requiresApiKey: Boolean
            ) {
                // 生成唯一ID (基于名称)
                val idBase =
                    "official_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"

                // 获取Logo URL
                val logoUrl = OfficialServerLogos.getLogoUrl(name, category)

                // 创建仓库URL
                val repoUrl =
                    if (name.contains(" ")) {
                        // 为官方插件添加特殊前缀，以便安装器可以识别并单独处理
                        "mcp-official:${name.replace(" ", "-").lowercase()}"
                    } else {
                        // 使用特殊前缀格式
                        "mcp-official:${name.lowercase()}"
                    }

                // 创建服务器对象
                val server =
                    MCPServer(
                        id = idBase,
                        name = name,
                        description = description,
                        logoUrl = logoUrl,
                        stars = 50, // 给官方服务器一个较高的默认星级
                        category = category,
                        requiresApiKey = requiresApiKey,
                        author = "Model Context Protocol (Official)",
                        isVerified = true, // 官方仓库中的都标记为已验证
                        isInstalled = false, // 初始未安装
                        version = "latest",
                        updatedAt = "",
                        longDescription =
                        "$description\n\n*This is an official Model Context Protocol server.*",
                        repoUrl = repoUrl
                    )

                servers.add(server)
            }
        }
    }

    enum class SortOptions(val value: String) {
        CREATED("created"),
        UPDATED("updated"),
        COMMENTS("comments"),
        REACTIONS("reactions"),
        RECOMMENDED("recommended"), // This is a local sort option, not part of GitHub API
    }

    enum class SortDirection(val value: String) {
        ASC("asc"),
        DESC("desc")
    }

    // ModelContextProtocol GitHub 常量
    object MCPGitHubConstants {
        const val API_BASE_URL = "https://api.github.com"
        const val REPO_ENDPOINT = "$API_BASE_URL/repos/modelcontextprotocol/servers"
        const val CONTENTS_ENDPOINT = "$REPO_ENDPOINT/contents"
        const val README_URL =
            "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/README.md"

        // 主要目录
        const val REFERENCE_SERVERS_DIR = "reference-servers"

        // 请求头
        const val ACCEPT_HEADER = "Accept"
        const val ACCEPT_JSON_VALUE = "application/vnd.github.v3+json"
    }

    // MCP 服务器类型
    enum class MCPServerType {
        CLINE_MARKETPLACE, // Cline Marketplace 服务器
        MCP_REFERENCE, // 官方参考服务器
        MCP_THIRD_PARTY // 第三方服务器
    }

    // 安装命令类型
    enum class InstallCommandType {
        NPX, // 基于TypeScript的npm/npx安装
        UVX, // 基于Python的uvx安装
        PIP, // 基于Python的pip安装
        OTHER // 其他安装方式
    }

    // 标记服务器提交的关键字 - 按照标准格式
    const val SERVER_SUBMISSION_TAG = "[Server Submission]"

    // Issue正文中的标准部分 - 按照官方提交格式
    const val REPO_URL_SECTION = "### GitHub Repository URL"
    const val LOGO_SECTION = "### Logo Image"
    const val TESTING_SECTION = "### Installation Testing"
    const val INFO_SECTION = "### Additional Information"
    const val SHORT_DESC_SECTION = "#### Short Description"
    const val WHY_ADD_SECTION = "#### Why add this server"

    // 官方提交格式的复选框
    const val TESTING_CHECKBOX_README =
        "- [x] I have tested that Cline can successfully set up this server using only the README.md and/or llms-install.md file"
    const val TESTING_CHECKBOX_STABLE = "- [x] The server is stable and ready for public use"

    // Logo标准尺寸 (根据提交标准)
    const val LOGO_STANDARD_SIZE = 400 // 400x400 PNG
}

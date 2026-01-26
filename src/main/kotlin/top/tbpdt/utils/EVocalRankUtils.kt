package top.tbpdt.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.springframework.stereotype.Service
import top.tbpdt.logger
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap


object PubDateSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PubDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder.decodeSerializableValue(JsonElement.serializer())
        return when (input) {
            is JsonPrimitive -> input.content
            is JsonArray -> {
                input.firstOrNull()?.jsonPrimitive?.content ?: ""
            }

            else -> throw IllegalArgumentException("Unsupported format for pubdate")
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class VideoData(
    val version: Double,
    val ranknum: Int,
    val url: String,
    val coverurl: String,
    @Serializable(with = PubDateSerializer::class)
    val pubdate: String,
    val generate_time: String,
    val generate_timestamp: Long,
    val collect_start_time: String,
    val collect_end_time: String,
    val collect_start_time_timestamp: Long,
    val collect_end_time_timestamp: Long,
    val main_rank: List<MainRankItem>,
    val second_rank: List<MainRankItem>,
//    val history_10_year: List<HistoryItem>,
//    val ed: List<EdItem>,
//    val op: List<OpItem>,
    val statistic: Statistic,
//    val thanks_list: List<>
)

@Serializable
data class MainRankItem(
    val url: String,
    val avid: String,
    val coverurl: String,
    val title: String,
    @Serializable(with = PubDateSerializer::class)
    val pubdate: String,
    val point: Int,
    val play: Int,
    val coin: Int,
    val comment: Int,
    val danmaku: Int,
    val favorite: Int,
    val like: Int,
    val share: Int,
    val rank: Int,
//    val ext_rank: Map<String, Any>  // 使用 Map 表示 JSON 中空的 "ext_rank"
)

@Serializable
data class HistoryItem(
    val url: String,
    val avid: String,
    val coverurl: String,
    val title: String,
    @Serializable(with = PubDateSerializer::class)
    val pubdate: String,
    val point: Int,
    val play: Int,
    val coin: Int,
    val comment: Int,
    val danmaku: Int,
    val favorite: Int,
    val like: Int,
    val share: Int,
    val rank: Int
)

@Serializable
data class EdItem(
    val url: String,
    val avid: String,
    val coverurl: String,
    val title: String,
    @Serializable(with = PubDateSerializer::class)
    val pubdate: String
)

@Serializable
data class OpItem(
    val url: String,
    val avid: String,
    val coverurl: String,
    val title: String,
    @Serializable(with = PubDateSerializer::class)
    val pubdate: String
)

@Serializable
data class Statistic(
    val diff: Diff,
    val total_collect_count: Int,
    val new_video_count: Int,
    val new_in_rank_count: Int,
    val new_in_mainrank_count: Int,
    val pick_up_count: Int,
    val oth_pick_up_count: Int,
    val new_vc_in_rank_count: Int,
    val new_vc_in_mainrank_count: Int,
    val vc_in_rank_count: Int,
    val vc_in_mainrank_count: Int,
    val new_sv_in_rank_count: Int,
    val new_sv_in_mainrank_count: Int,
    val sv_in_rank_count: Int,
    val sv_in_mainrank_count: Int,
    val new_ace_in_rank_count: Int,
    val new_ace_in_mainrank_count: Int,
    val ace_in_rank_count: Int,
    val ace_in_mainrank_count: Int
)

@Serializable
data class Diff(
    val total_play: Int,
    val new_video_count: Int,
    val new_in_rank_count: Int,
    val new_in_mainrank_count: Int,
    val new_vc_in_rank_count: Int,
    val new_vc_in_mainrank_count: Int,
    val vc_in_rank_count: Int,
    val vc_in_mainrank_count: Int,
    val new_sv_in_rank_count: Int,
    val new_sv_in_mainrank_count: Int,
    val sv_in_rank_count: Int,
    val sv_in_mainrank_count: Int,
    val new_ace_in_rank_count: Int,
    val new_ace_in_mainrank_count: Int,
    val ace_in_rank_count: Int,
    val ace_in_mainrank_count: Int
)

/**
 * Spring-managed service for fetching EVocal rank data.
 * Initializes the Ktor HttpClient on bean construction and closes it on bean destruction.
 */
@Service
class EVocalRankUtils {
    private val ORIGIN_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val ORIGIN_DATE_TIME = LocalDateTime.parse("2026-01-18 08:00:00", ORIGIN_FORMATTER)
    private val ORIGIN_ISSUE_NUMBER = 702

    private lateinit var client: HttpClient
    private val cacheDir = File("data/cache/evocalrank")

    // 并发安全
    private val imageLoadingTasks = ConcurrentHashMap<String, Deferred<ByteArray?>>()
    private val rankLoadingTasks = ConcurrentHashMap<Int, Deferred<VideoData>>()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PostConstruct
    fun initClient() {
        // 初始化缓存目录
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_0000
                connectTimeoutMillis = 5_0000
            }
            install(ContentEncoding) {
                deflate()
                gzip()
            }
            defaultRequest {
                url("https://s3-cdn.evocalrank.com/")

                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0")

                header("sec-ch-ua", "\"Microsoft Edge\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"")
                header("sec-ch-ua-mobile", "?0")
                header("sec-ch-ua-platform", "\"Windows\"")

                header("Sec-Fetch-Dest", "document")
                header("Sec-Fetch-Mode", "navigate")
                header("Sec-Fetch-Site", "same-origin")
                header("Sec-Fetch-User", "?1")

                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
//                header(HttpHeaders.AcceptEncoding, "gzip, deflate, br, zstd")
                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")

                header("Upgrade-Insecure-Requests", "1")
                header(HttpHeaders.Connection, "keep-alive")
            }
        }
    }

    fun getIssueNow(): Int {
        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")) // CST
        val weeksSince = ChronoUnit.WEEKS.between(ORIGIN_DATE_TIME, now)
        logger().info("现在是第 ${ORIGIN_ISSUE_NUMBER + weeksSince.toInt()} 期，时间$now")
        return ORIGIN_ISSUE_NUMBER + weeksSince.toInt()
    }

    suspend fun getLatestRank(isFetchAll: Boolean = false): VideoData {
        val issueNow = getIssueNow()
        val latestFile = File(cacheDir, "$issueNow.json")

        // 只有命中绝对最新的 issueNow 缓存时才直接返回
        if (latestFile.exists()) {
            try {
                logger().info("命中最新期数本地缓存: ${issueNow}.json")
                return withContext(Dispatchers.IO) {
                    jsonParser.decodeFromString<VideoData>(latestFile.readText())
                }
            } catch (e: Exception) {
                logger().error("最新缓存解析失败: ${e.message}")
            }
        }

        val videoData = try {
            val deferred = synchronized(rankLoadingTasks) {
                rankLoadingTasks.getOrPut(issueNow) {
                    serviceScope.async {
                        fetchWithFallback(issueNow)
                    }
                }
            }
            deferred.await()
        } finally {
            synchronized(rankLoadingTasks) {
                rankLoadingTasks.remove(issueNow)
            }
        }

        if (isFetchAll) {
            serviceScope.launch { cacheAll(videoData) }
        }

        return videoData
    }


    suspend fun getImage(fileName: String, url: String): ByteArray? {
        val fixedFileName = if (fileName.endsWith(".jpg", ignoreCase = true)) fileName else "$fileName.jpg"
        val cacheFile = File(cacheDir, fixedFileName)

        val cachedData = withContext(Dispatchers.IO) {
            if (cacheFile.exists()) cacheFile.readBytes() else null
        }
        if (cachedData != null) {
            logger().info("命中本地缓存: $fixedFileName")
            return cachedData
        }

        // 请求合并
        val deferred = synchronized(imageLoadingTasks) {
            imageLoadingTasks.getOrPut(url) {
                CoroutineScope(Dispatchers.IO).async {
                    try {
                        performDownload(fixedFileName, url, cacheFile)
                    } finally {
                        imageLoadingTasks.remove(url)
                    }
                }
            }
        }

        return deferred.await()
    }

    private suspend fun fetchWithFallback(startIssue: Int): VideoData {
        for (currentIssue in startIssue downTo (startIssue - 5)) {
            val currentFile = File(cacheDir, "$currentIssue.json")

            // 检查这一期本地是否有缓存
            if (currentFile.exists()) {
                try {
                    return withContext(Dispatchers.IO) {
                        jsonParser.decodeFromString<VideoData>(currentFile.readText())
                    }
                } catch (_: Exception) {
                    // 如果本地文件损坏，则继续走网络请求覆盖它
                }
            }

            // 没有就去请求
            val remoteData = tryFetchRank(currentIssue, currentFile)
            if (remoteData != null) {
                return remoteData
            }

            logger().warn("第 $currentIssue 期获取失败，尝试上一期...")
        }

        throw Exception("尝试了最近 5 期数据，本地及网络均不可用")
    }

    private suspend fun tryFetchRank(issue: Int, currentCacheFile: File): VideoData? {
        logger().info("尝试获取并缓存第 $issue 期数据")
        val url = "https://www.evocalrank.com/data/rank_data/$issue.json"

        val response: HttpResponse = client.get(url)

        if (response.status == HttpStatusCode.NotFound) {
            logger().warn("第 $issue 期不存在 (404)")
            return null
        }

        val responseBody = response.bodyAsText()
        val videoData = jsonParser.decodeFromString<VideoData>(responseBody)

        withContext(Dispatchers.IO) {
            val tempFile = File(cacheDir, "${issue}_${System.currentTimeMillis()}.json.tmp")
            try {
                tempFile.writeText(responseBody)
                tempFile.renameTo(currentCacheFile)
                logger().info("第 $issue 期数据已缓存至 ${currentCacheFile.name}")
            } catch (e: Exception) {
                logger().error("JSON 写入磁盘失败: ${e.message}")
            }
        }

        return videoData
    }

    private suspend fun performDownload(fileName: String, url: String, cacheFile: File): ByteArray? {
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            try {
                logger().info("开始下载 (第 $attempt 次尝试): $url")
                val response: HttpResponse = client.get(url)

                if (response.status.value != HttpStatusCode.OK.value) {
                    logger().warn("下载返回非 200 状态: ${response.status.value} (尝试 $attempt/$maxAttempts)")
                    if (attempt < maxAttempts) {
                        delay((100L..2000L).random()) // 重试前等待一下
                        continue
                    }
                    return null
                }

                val bytes = response.readBytes()

                val tempFile = File(cacheDir, "${fileName}_${System.nanoTime()}.tmp")
                try {
                    tempFile.writeBytes(bytes)
                    if (!tempFile.renameTo(cacheFile)) {
                        logger().error("重命名失败: $fileName")
                    }
                } catch (e: Exception) {
                    logger().error("磁盘写入失败: ${e.message}")
                    if (tempFile.exists()) tempFile.delete()
                }

                return bytes

            } catch (e: Exception) {
                logger().error("第 $attempt 次下载异常: ${e.message}")
                if (attempt < maxAttempts) {
                    delay((100L..2000L).random())
                } else {
                    logger().error("已达到最大重试次数，下载放弃: $url")
                }
            }
        }
        return null
    }

    fun clearCache(): Boolean {
        return try {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                logger().info("已清空 evocalrank 缓存目录")
                true
            } else false
        } catch (e: Exception) {
            logger().error("清空缓存失败: ${e.message}")
            false
        }
    }

    suspend fun cacheAll(videoData: VideoData) {
        val allItems = videoData.main_rank.map { "第 ${it.rank} 名" to it } +
                videoData.second_rank.map { "第 ${it.rank + 30} 名" to it }

        logger().info("开始后台预缓存，共 ${allItems.size} 张图片")

        coroutineScope {
            val semaphore = Semaphore(3)
            allItems.forEach { (label, item) ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            getImage(item.avid, item.coverurl)
                        } catch (e: Exception) {
                            logger().error("预缓存 $label 图片失败: ${item.avid}")
                        }
                    }
                }
            }
        }
        logger().info("所有预缓存任务已提交")
    }

    @PreDestroy
    fun closeClient() {
        if (this::client.isInitialized) {
            client.close()
        }
    }
}
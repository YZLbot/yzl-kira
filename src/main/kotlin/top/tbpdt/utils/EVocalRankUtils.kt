package top.tbpdt.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import top.tbpdt.logger
import java.io.IOException

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
    private lateinit var client: HttpClient

    @PostConstruct
    fun initClient() {
        client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            defaultRequest {
                header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
                )
            }
        }
    }

    suspend fun getLatestRank(): VideoData {
        val response: HttpResponse = client.get("https://www.evocalrank.com/data/info/latest.json")
        val responseBody = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }.decodeFromString<VideoData>(responseBody)
    }

    @PreDestroy
    fun closeClient() {
        if (this::client.isInitialized) {
            client.close()
        }
    }

    suspend fun getImage(av: String, url: String): ByteArray? {
        val client = OkHttpClient()
        logger().info("尝试为 $av 获取封面 $url")

        val request = Request.Builder().url(url).header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
        ).build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

}
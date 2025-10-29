package top.tbpdt.handle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import top.tbpdt.logger
import top.tbpdt.utils.JsonLoaderService

@Serializable
data class Lyrics(
    val author: List<String>,
    val title: String,
    val year: Int,
    val lines: List<String>
)

@Component
/**
 * @author Takeoff0518
 */
class RandomLyrics(public val jsonLoaderService: JsonLoaderService) {


    private var cachedLyricsList: List<Lyrics>? = null

    private fun loadLyricsData(): List<Lyrics> {
        if (cachedLyricsList != null) {
            return cachedLyricsList!!
        }
        try {
            logger().debug("Loading Lyrics data...")
            val jsonString = jsonLoaderService.lyricsJsonString
            cachedLyricsList = Json.decodeFromString<List<Lyrics>>(jsonString)
            if (cachedLyricsList.isNullOrEmpty()) {
                throw IllegalStateException("加载了一个空的 JSON 列表……")
            }
            return cachedLyricsList!!
        } catch (e: Exception) {
            cachedLyricsList = null
            e.printStackTrace()
            throw e
        }
    }

    fun getLyrics(): Lyrics {
        return try {
            val lyricsList = loadLyricsData()
            lyricsList.random()
        } catch (e: Exception) {
            Lyrics(emptyList(), "", 0, listOf("[未获取到歌词]"))
        }
    }

    fun Lyrics.toFixedString(): String {
        if (this.year == 0 || this.lines.isEmpty()) {
            return "[未获取到歌词]"
        }
        val result = StringBuilder().append('\n')
        for (line in this.lines) {
            result.append(line)
            result.append('\n')
        }
        result.append("——")
        for (author in this.author) {
            result.append(author)
            result.append(' ')
        }
        result.append("《${this.title}》(${this.year})")
        return result.toString()
    }

    @Listener
    @ContentTrim
    @Filter("^/歌词$")
    suspend fun handleMessageEvent(event: ChatGroupMessageEvent) {
        val lyrics = getLyrics()
        if (lyrics.year == 0) {
            event.content().send("唔姆，读取歌词文件失败...")
        } else {
            event.content().send(lyrics.toFixedString())
        }
    }
}
package top.tbpdt.handle

import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.plus
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import top.tbpdt.utils.EVocalRankUtils

/**
 * @author Takeoff0518
 */
@Component
class EvocalRank(private val eVocalRankUtils: EVocalRankUtils) {
    @Listener
    @ContentTrim
    @Filter("^/中V周刊.*")
    suspend fun handleMessageEvent(event: ChatGroupMessageEvent) {
        println(event.messageContent.plainText?.trim()?.removePrefix("/中V周刊"))
        val requestRank = event.messageContent.plainText?.trim()?.removePrefix("/中V周刊")?.trim()?.toIntOrNull()
        if (requestRank != null) {
            if (requestRank !in 1..110) {
                event.content().send("\n你查询的曲子超出了范围(1~110)！".toText())
                return
            }
//            event.content().send("加载中……".toText())
            val latestData = try {
                eVocalRankUtils.getLatestRank()
            } catch (e: Exception) {
                event.content().send("\n意外失去了与母星的联系……".toText())
                e.printStackTrace()
                return
            }
            val requestData = if (requestRank <= 30)
                latestData.main_rank[requestRank - 1] else
                latestData.second_rank[requestRank - 30 - 1]
            val returnStr = "[${requestRank}]${requestData.title}\n" +
                    "(${avToBV(requestData.avid)})\n" +
                    "\uD83D\uDD25得分: ${requestData.point}\n" +
                    "\uD83D\uDCC5发布日期: ${requestData.pubdate.drop(5).dropLast(3)}\n" +
                    "\u25B6\uFE0F播放: ${requestData.play}\n" +
                    "\uD83D\uDCAC评论: ${requestData.comment}\n" +
                    "\uD83D\uDE80弹幕: ${requestData.danmaku}\n" +
                    "\uD83D\uDC4D点赞: ${requestData.like}\n" +
                    "\u2B50收藏: ${requestData.favorite}\n" +
                    "\uD83E\uDE99硬币: ${requestData.coin}"
//                    requestData.url + "\n"
            val image = eVocalRankUtils.getImage(requestData.avid, requestData.coverurl)?.toOfflineImage()
            if (image == null) {
                event.content().send("\n$returnStr".toText())
            } else {
                event.content()
                    .send("\n".toText() + returnStr.toText() + image)

            }
//            event.content()
//                .send("\n".toText() + returnStr.toText() + "\n\n[视频封面暂不可用]".toText())
            return
        }

//        event.content().send("加载中……".toText())

        val latestData = try {
            eVocalRankUtils.getLatestRank(true)
        } catch (e: Exception) {
            event.content().send("\n意外失去了与母星的联系……\n${e.message}".toText())
            e.printStackTrace()
            return
        }
        val image = eVocalRankUtils.getImage("${latestData.ranknum}", latestData.coverurl)?.toOfflineImage()
        val overviewStr = "\n周刊虚拟歌手中文曲排行榜♪${latestData.ranknum}\n" +
                "总计收录: ${latestData.statistic.total_collect_count}首\n" +
                "新曲数: ${latestData.statistic.new_video_count}首\n" +
                "新曲入榜数: ${latestData.statistic.new_in_rank_count}首\n" +
                "新曲入主榜数: ${latestData.statistic.new_in_mainrank_count}首\n" +
                "最后收录时间: ${latestData.collect_end_time}\n" +
                "发送 “@我 /中V周刊 [排名(1~110)]” 以获取单个稿件的详细信息~"
        event.content().send(overviewStr.toText() + (image ?: "".toText()))
        val builder = StringBuilder().append("\n")
        for (i in latestData.main_rank) {
            builder.append("[${i.rank}]${i.title}\n")
        }
        event.content().send(builder.toString().toText())
    }

    fun avToBV(avId: String): String {
        val XOR_CODE = 23442827791579L
        val MAX_AID = 1L shl 51 //  2^51
        val DATA = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
        val BASE = 58

        val aid = avId.substring(2).toLong()
        val bytes = "BV1000000000".toCharArray()
        var tmp = (MAX_AID or aid) xor XOR_CODE

        var bvIdx = bytes.size - 1
        while (tmp > 0) {
            bytes[bvIdx] = DATA[(tmp % BASE).toInt()]
            tmp /= BASE
            bvIdx--
        }
        fun swap(i: Int, j: Int) {
            val t = bytes[i]
            bytes[i] = bytes[j]
            bytes[j] = t
        }
        swap(3, 9)
        swap(4, 7)

        return String(bytes)
    }
}
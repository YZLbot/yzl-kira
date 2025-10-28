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
            if (requestRank !in 1..30) {
                event.content().send("\n你查询的曲子超出了主榜范围(1~30)！".toText())
                return
            }
            event.content().send("加载中……".toText())
            val latestData = try {
                eVocalRankUtils.getLatestRank()
            } catch (e: Exception) {
                event.content().send("\n意外失去了与母星的联系……".toText())
                e.printStackTrace()
                return
            }
            val requestData = latestData.main_rank[requestRank - 1]
            val returnStr = "#${requestRank} ${requestData.avid}\n" +
                    "${requestData.title}\n" +
                    "得分: ${requestData.point}\n" +
                    "发布日期: ${requestData.pubdate}\n" +
                    "播放: ${requestData.play}\n" +
                    "评论: ${requestData.comment}\n" +
                    "弹幕: ${requestData.danmaku}\n" +
                    "点赞：${requestData.like}\n" +
                    "收藏: ${requestData.favorite}\n" +
                    "硬币: ${requestData.coin}"
//                    requestData.url + "\n"
            val image = eVocalRankUtils.getImage(requestData.avid, requestData.coverurl)?.toOfflineImage()
            if (image == null) {
                event.content().send("[视频封面获取失败]\n$returnStr".toText())
            } else {
                event.content()
                    .send("\n".toText() + returnStr.toText() + image)

            }
//            event.content()
//                .send("\n".toText() + returnStr.toText() + "\n\n[视频封面暂不可用]".toText())
            return
        }

        event.content().send("加载中……".toText())

        val latestData = try {
            eVocalRankUtils.getLatestRank()
        } catch (e: Exception) {
            event.content().send("\n意外失去了与母星的联系……\n${e.message}".toText())
            e.printStackTrace()
            return
        }
        val image = eVocalRankUtils.getImage("mainCover", latestData.coverurl)?.toOfflineImage()
        val overviewStr = "\n周刊虚拟歌手中文曲排行榜♪${latestData.ranknum}\n" +
                "总计收录: ${latestData.statistic.total_collect_count}首\n" +
                "新曲数: ${latestData.statistic.new_video_count}首\n" +
                "新曲入榜数: ${latestData.statistic.new_in_rank_count}首\n" +
                "新曲入主榜数: ${latestData.statistic.new_in_mainrank_count}首\n" +
                "最后收录时间: ${latestData.collect_end_time}\n" +
                "发送 .vcrank [排名(1~30)] 以获取主榜详细信息~"
        println("尝试发送消息！")
        event.content().send(overviewStr.toText() + (image ?: "[视频封面获取失败]".toText()))
        val builder = StringBuilder().append("\n")
        for (i in latestData.main_rank) {
            builder.append("[${i.rank}]${i.title}\n")
        }
        event.content().send(builder.toString().toText())
    }
}
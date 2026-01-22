package top.tbpdt.handle

import kotlinx.serialization.json.jsonObject
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import top.tbpdt.utils.JsonLoaderService

/**
 * @author Takeoff0518
 */

@Component
class Divine(private val jsonLoaderService: JsonLoaderService) {

    val userCache = mutableMapOf<String, Int>()

    @Listener
    @ContentTrim
    @Filter("^/抽签$")
    suspend fun handleDraw(event: ChatGroupMessageEvent) {
        val randoms = (1..384).random()
        val divContent = jsonLoaderService.divinationJsonElement.jsonObject["$randoms"]?.jsonObject?.get("shi").toString().replace("\"", "")
        val divResult = "\n\n[第 $randoms 签]\n\n$divContent\n\n发送 “@我 /解签” 获取这一签的解签~"
        event.content().send(divResult)
        userCache[event.authorId.toString()] = randoms
    }

    @Listener
    @ContentTrim
    @Filter("^/解签$")
    suspend fun handleInterpret(event: ChatGroupMessageEvent) {
        if (!userCache.containsKey(event.authorId.toString())) {
            event.content().send("请先抽签再来解签哦~")
            return
        }
        val randoms = userCache[event.authorId.toString()] ?: 0

        if (randoms == 0) {
            event.content().send("唔姆，似乎出了点问题诶……再尝试一下抽签吧~")
            return
        }

        val divContent =
            jsonLoaderService.divinationJsonElement.jsonObject["$randoms"]?.jsonObject?.get("jie").toString().replace("\"", "")
        val divResult = "\n\n[第 $randoms 签: 解签]\n\n$divContent"

        event.content().send(divResult)
    }

}
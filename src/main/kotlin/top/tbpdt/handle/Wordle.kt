package top.tbpdt.handle

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import love.forte.simbot.common.id.ID
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.plus
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import top.tbpdt.utils.GuessResult.*
import top.tbpdt.utils.JsonLoaderService
import top.tbpdt.utils.WordleRound

data class WordInfo(
    val word: String,
    val chineseExplanation: String,
    val englishExplanation: String
)

/**
 * @author Takeoff0518
 */
@Component
class Wordle(private val jsonLoaderService: JsonLoaderService) {

    val groupCache: MutableMap<ID, WordleRound> = mutableMapOf()

    fun getRandomWordByLength(wordElements: JsonElement, wordLength: Int): WordInfo? {
        val jsonObject = wordElements.jsonObject

        val matchingWords = jsonObject.filter { (word, _) ->
            word.length == wordLength
        }

        if (matchingWords.isEmpty()) {
            return null
        }

        val randomEntry = matchingWords.entries.random()
        val word = randomEntry.key
        val wordData = randomEntry.value.jsonObject

        val chineseExplanation = wordData["中释"]?.toString()?.removeSurrounding("\"") ?: ""
        val englishExplanation = wordData["英释"]?.toString()?.removeSurrounding("\"") ?: ""

        return WordInfo(word, chineseExplanation, englishExplanation)
    }

    @Listener
    @ContentTrim
    @Filter("^/猜单词.*")
    suspend fun startAndHintHandle(event: ChatGroupMessageEvent) {

        val argument = event.messageContent.plainText?.trim()?.removePrefix("/猜单词")?.trim()

        // 不提示却触发了这个功能
        if (event.content().id in groupCache.keys && argument.isNullOrEmpty()) {
            event.reply("本群已经开始了一个猜单词游戏哦，请发送“@我 单词”进行猜词~")
            return
        }

        // 提示
        if (argument != null && argument.trim() == "hint") {
            if (groupCache[event.content().id]?.isHinted == true) {
                event.content().send("提示机会已经用完了哦，加油~")
                return
            }
            groupCache[event.content().id]?.isHinted = true
            val image = groupCache[event.content().id]?.drawHint()?.toOfflineImage()
            event.content().send(image ?: "[图片生成失败]".toText())
            return
        }

        // 结束
        if (argument != null && argument.trim() == "exit") {
            groupCache[event.content().id]?.guess(groupCache[event.content().id]!!.word)
            val image = groupCache[event.content().id]?.draw()?.toOfflineImage()
            event.content().send(image ?: "[图片生成失败]".toText())
            event.content()
                .send("游戏终止~\n".toText() + (groupCache[event.content().id]?.result ?: "释义获取失败！").toText())
            groupCache.remove(event.content().id)
            return
        }

        if (event.content().id in groupCache.keys) {
            return
        }
        // 开始
        val wordLength = argument?.removePrefix("/猜单词")?.trim()?.toIntOrNull() ?: (5..6).random()
        val word = getRandomWordByLength(jsonLoaderService.wordleJsonElement, wordLength)
        if (word == null) {
            event.reply("没有找到长度为 $wordLength 的单词呢……")
            return
        }
        groupCache[event.content().id] = WordleRound(word.word, word.chineseExplanation, word.englishExplanation)
        val image = groupCache[event.content().id]?.draw()?.toOfflineImage()
        event.content().send(
            (image
                ?: "[图片生成失败]".toText()) + "\n猜单词开始！\n发送“@我 /猜单词 hint”可获取提示（仅可用一次）\n发送“@我 /猜单词 exit”结束猜词".toText()
        )
    }

    @Listener
    @ContentTrim
    @Filter("^[^/].+") // 匹配开头不为 / 的内容
    suspend fun playHandle(event: ChatGroupMessageEvent) {
        if (event.content().id !in groupCache.keys) {
            return
        }
        val guessWord = event.messageContent.plainText?.trim()
        if (guessWord == null || guessWord.isBlank()) {
            return
        }
        val gameStatus = groupCache[event.content().id]?.guess(guessWord)
        val image = groupCache[event.content().id]?.draw()?.toOfflineImage()
        when (gameStatus) {
            DUPLICATE -> {
                event.content()
                    .send("你已经猜过这个单词了，再尝试一下别的单词吧~".toText() + (image ?: "[图片生成失败]".toText()))
            }

            WIN -> {
                event.content().send(image ?: "[图片生成失败]".toText())
                event.content()
                    .send("\n".toText() + (groupCache[event.content().id]?.result ?: "释义获取失败！").toText())
                groupCache.remove(event.content().id)
            }

            LOSS -> {
                event.content().send(image ?: "[图片生成失败]".toText())
                event.content()
                    .send("\n".toText() + (groupCache[event.content().id]?.result ?: "释义获取失败！").toText())
                groupCache.remove(event.content().id)
            }

            ILLEGAL -> {
                event.content().send("唔……似乎填不进去呢~".toText() + (image ?: "[图片生成失败]".toText()))
            }

            null -> {
                event.content().send(image ?: "[图片生成失败]".toText())
            }
        }
    }
}
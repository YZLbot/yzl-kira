package top.tbpdt.handle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import kotlinx.serialization.json.Json
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
import top.tbpdt.configer.WordleConfig
import top.tbpdt.configer.WordleConfigManager
import top.tbpdt.logger
import top.tbpdt.utils.ExternalConfigManager
import top.tbpdt.utils.GuessResult.*
import top.tbpdt.utils.WordleRound
import java.io.File

data class WordInfo(
    val word: String,
    val chineseExplanation: String,
    val englishExplanation: String
)

/**
 * @author Takeoff0518
 */
@Component
class Wordle(
    private val wordleConfiger: WordleConfigManager,
    private val externalConfigManager: ExternalConfigManager,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val mapper = jacksonObjectMapper()
        lateinit var wordsSet: Set<String>
    }

    lateinit var config: WordleConfig
    lateinit var dictJsonElements: MutableList<JsonElement>
    lateinit var dictNames: MutableList<String>
    lateinit var dictIndexes: MutableMap<String, Int>

    fun getdictId(groupId: String): Int {
        return dictIndexes.getOrPut(groupId) {
            logger().info("为群 $groupId 生成默认词典 id")
            wordleConfiger.updateConfig { config ->
                config.dictIndex[groupId] = 0
            }
            0
        }
    }

    suspend fun getGroupId(event: ChatGroupMessageEvent) = event.content().id

    suspend fun getGroupIdStr(event: ChatGroupMessageEvent) = getGroupId(event).toString()

    @PostConstruct
    fun configInit() {
        wordleConfiger.initialize()
        config = wordleConfiger.getCurrentConfig()
        logger().info("加载 Wordle 词典中...")
        dictIndexes = config.dictIndex
        dictNames = config.dictListString
        dictJsonElements = config.dictListString.mapNotNull { dict ->
            externalConfigManager.readFileContent("config/wordleDicts/$dict.json")
                ?.let { Json.parseToJsonElement(it) }
        }.toMutableList()

        logger().info("Wordle 词典加载完毕！共加载 ${dictJsonElements.size} 个词典！")

        val wordsSetData: Map<String, Int> = objectMapper.readValue(
            File("config/wordleDicts/allWords.json"),
            object : TypeReference<Map<String, Int>>() {}
        )
        wordsSet = wordsSetData.keys.toHashSet()
        logger().info("单词存在检查表加载完成，共计 ${wordsSet.size} 个单词！")
    }

    val groupCache: MutableMap<ID, WordleRound> = mutableMapOf()

    fun getRandomWordByLength(wordLength: Int, groupId: String): WordInfo? {
        val jsonObject = dictJsonElements[getdictId(groupId)].jsonObject

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
        if (getGroupId(event) in groupCache.keys && argument.isNullOrEmpty()) {
            event.content().send("本群已经开始了一个猜单词游戏哦，请发送“@我 单词”进行猜词~")
            return
        }

        // 提示
        if (argument != null && argument.trim() == "hint") {
            if (groupCache[getGroupId(event)]?.isHinted == true) {
                event.content().send("提示机会已经用完了哦，加油~")
                return
            }
            groupCache[getGroupId(event)]?.isHinted = true
            val image = groupCache[getGroupId(event)]?.drawHint()?.toOfflineImage()
            event.content().send(image ?: "[图片生成失败]".toText())
            return
        }

        // 结束
        if (argument != null && argument.trim() == "exit") {
            groupCache[getGroupId(event)]?.guess(groupCache[getGroupId(event)]!!.word)
            val image = groupCache[getGroupId(event)]?.draw()?.toOfflineImage()
            event.content().send(image ?: "[图片生成失败]".toText())
            event.content()
                .send("游戏终止~\n".toText() + (groupCache[getGroupId(event)]?.result ?: "释义获取失败！").toText())
            groupCache.remove(getGroupId(event))
            return
        }

        if (getGroupId(event) in groupCache.keys) {
            return
        }

        // 选择词库
        if (argument != null && argument.trim().startsWith("dict")) {
            val selectedDict: String = argument.removePrefix("dict").trim();
            if (selectedDict.isEmpty()) {
                val response = StringBuilder()
                response.append('\n');
                for (i in 0..<dictNames.size) {
                    response.append("[$i] ${dictNames[i]}\n")
                }
                response.append("当前词库：${dictNames[getdictId(getGroupIdStr(event))]}")
                response.append("若要修改词库，请发送“@我 /猜单词 dict [词库id]”")
                event.content().send(response.toString());
                return
            }
            val selectedDictIdx = selectedDict.toIntOrNull()
            if (selectedDictIdx == null) {
                event.content().send("词库id解析失败，请输入纯数字的id哦~")
                return
            }
            if (selectedDictIdx < 0 || selectedDictIdx > dictNames.size) {
                event.content().send("词库id超出范围，应为[0,${dictNames.size - 1}]~")
                return
            }
            dictIndexes[getGroupIdStr(event)] = selectedDictIdx
            event.content().send("词库已成功切换为 ${dictNames[selectedDictIdx]}~")
            return
        }

        if (argument?.trim()?.any { !it.isDigit() } == true) {
            event.content().send(
                "没有解析到命令……\n" +
                        "如果正在猜单词，请发送“@我 ${argument.trim()}”参与猜单词~"
            )
            return
        }

        // 开始
        val wordLength = argument?.removePrefix("/猜单词")?.trim()?.toIntOrNull() ?: (4..7).random()
        val word = getRandomWordByLength(wordLength, getGroupIdStr(event))
        if (word == null) {
            event.content().send("没有找到长度为 $wordLength 的单词呢……")
            return
        }
        groupCache[getGroupId(event)] =
            WordleRound(getGroupIdStr(event), word.word, word.chineseExplanation, word.englishExplanation)
        val image = groupCache[getGroupId(event)]?.draw()?.toOfflineImage()
        event.content().send(
            (image
                ?: "[图片生成失败]".toText()) + ("\n猜单词开始！\n" +
                    "词库：${dictNames[getdictId(getGroupIdStr(event))]}\n" +
                    "发送“@我 [单词]”参与猜单词\n" +
                    "发送“@我 /猜单词 hint”可获取提示（仅可用一次）\n" +
                    "发送“@我 /猜单词 exit”结束猜词").toText()
        )
    }


    @Listener
    @ContentTrim
    @Filter("^[^/].+") // 匹配开头不为 / 的内容
    suspend fun playHandle(event: ChatGroupMessageEvent) {
        if (getGroupId(event) !in groupCache.keys) {
            return
        }
        val guessWord = event.messageContent.plainText?.trim()
        if (guessWord == null || guessWord.isBlank()) {
            return
        }
        val gameStatus = groupCache[getGroupId(event)]?.guess(guessWord)
        val image = groupCache[getGroupId(event)]?.draw()?.toOfflineImage()
        when (gameStatus) {
            DUPLICATE -> {
                event.content()
                    .send("你已经猜过这个单词了，再尝试一下别的单词吧~".toText() + (image ?: "[图片生成失败]".toText()))
            }

            WIN -> {
                event.content().send(image ?: "[图片生成失败]".toText())
                event.content()
                    .send("\n".toText() + (groupCache[getGroupId(event)]?.result ?: "释义获取失败！").toText())
                groupCache.remove(getGroupId(event))
            }

            LOSS -> {
                event.content().send(image ?: "[图片生成失败]".toText())
                event.content()
                    .send("\n".toText() + (groupCache[getGroupId(event)]?.result ?: "释义获取失败！").toText())
                groupCache.remove(getGroupId(event))
            }

            ILLEGAL -> {
                event.content().send("唔……似乎填不进去呢~".toText() + (image ?: "[图片生成失败]".toText()))
            }

            UNKNOWN -> {
                event.content()
                    .send("没有在词库中找到这个词呢……再换一个试试？".toText() + (image ?: "[图片生成失败]".toText()))
            }

            null -> {
                event.content().send(image ?: "[图片生成失败]".toText())
            }
        }
    }
}
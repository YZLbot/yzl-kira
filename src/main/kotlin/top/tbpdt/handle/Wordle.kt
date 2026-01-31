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

    fun ID.shortHash() = this.toString().take(8).lowercase()

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
        val groupId = getGroupId(event)
        val groupIdStr = getGroupIdStr(event)
        val argument = event.messageContent.plainText?.trim()?.removePrefix("/猜单词")?.trim() ?: ""

        // 配置词典，无论游戏是否运行都可以操作
        if (argument.startsWith("dict")) {
            handleDictLogic(event, argument, groupIdStr)
            return
        }

        val wordleRoundNow = groupCache[groupId] // nullable

        if (wordleRoundNow != null) {
            // 正在游戏
            when (argument) {
                "hint" -> {
                    if (wordleRoundNow.isHinted) {
                        event.content().send("提示机会已经用完了哦，加油~")
                        return
                    }
                    if (!wordleRoundNow.getHint().contains('*')) {
                        event.content().send("唔姆，再提示答案就出来了，阿绫就不告诉你啦，加油~")
                        return
                    }
                    wordleRoundNow.isHinted = true
                    val image = wordleRoundNow.drawHint().toOfflineImage()
                    event.content().send(image)
                }
                "exit" -> { // 强制结束
                    wordleRoundNow.guess(wordleRoundNow.word)
                    val image = wordleRoundNow.draw().toOfflineImage()
                    event.content().send(image)
                    event.content().send("游戏终止~\n".toText() + wordleRoundNow.result.toText())
                    groupCache.remove(groupId)
                }
                "" -> {
                    // 只发了 "/猜单词"
                    event.content().send("本群已经开始了一个猜单词游戏哦，请发送“@我 单词”进行猜词~")
                }
                else -> {
                    // "/猜单词 5" or "/猜单词 乱七八糟"
                    event.content().send("游戏正在进行中！如果要猜词，请直接发送“@我 [单词]”；\n如果想获取提示，请发送“/猜单词 hint”")
                }
            }
        } else {
            // 未在游戏
            // 没游戏时发送指令
            if (argument == "hint" || argument == "exit") {
                event.content().send("当前没有正在进行的猜单词游戏哦，发送“/猜单词”开始一个吧！")
                return
            }
            // 校验无效参数
            if (argument.isNotEmpty() && argument.any { !it.isDigit() }) {
                event.content().send("没有解析到命令……如果要开始游戏，请发送“/猜单词 [长度]”")
                return
            }
            // 开始游戏
            val wordLength = argument.toIntOrNull() ?: (4..7).random()
            if (wordLength < 2) {
                event.content().send("单词长度太短了，没法猜呢……换个长度试一下？")
                return
            }
            val word = getRandomWordByLength(wordLength, groupIdStr)
            if (word == null) {
                event.content().send("没有找到长度为 $wordLength 的单词，换个长度试一下？")
                return
            }
            // 初始化
            val newRound = WordleRound(groupIdStr, word.word, word.chineseExplanation, word.englishExplanation)
            groupCache[groupId] = newRound
            logger().info("群 ${event.content().id.shortHash()} 开始猜单词 ${word.word}")
            val image = newRound.draw().toOfflineImage()
            event.content().send(
                image + ("\n猜单词开始！\n" +
                        "词库：${dictNames[getdictId(groupIdStr)]}\n" +
                        "发送“@我 [单词]”参与猜单词\n" +
                        "发送“@我 /猜单词 hint”可获取提示（仅可用一次）\n" +
                        "发送“@我 /猜单词 exit”结束猜词").toText()
            )
        }
    }

    suspend fun handleDictLogic(event: ChatGroupMessageEvent, argument: String, groupIdStr: String) {
        val selectedDict = argument.removePrefix("dict").trim()
        if (selectedDict.isEmpty()) {
            val response = StringBuilder("\n")
            dictNames.forEachIndexed { i, name -> response.append("[$i] $name\n") }
            response.append("当前词库：${dictNames[getdictId(groupIdStr)]}\n")
            response.append("若要修改词库，请发送“/猜单词 dict [词库id]”")
            event.content().send(response.toString())
            return
        }

        val selectedDictIdx = selectedDict.toIntOrNull()
        if (selectedDictIdx == null || selectedDictIdx !in dictNames.indices) {
            event.content().send("词库id无效，应为 [0, ${dictNames.size - 1}] 之间的数字~")
            return
        }

        dictIndexes[groupIdStr] = selectedDictIdx
        event.content().send("词库已成功切换为 ${dictNames[selectedDictIdx]}~")
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

        val wordleRoundNow = groupCache[getGroupId(event)] ?: return

        val gameStatus = wordleRoundNow.guess(guessWord)
        val image = wordleRoundNow.draw().toOfflineImage()
        when (gameStatus) {
            DUPLICATE -> {
                event.content()
                    .send("你已经猜过这个单词了，再尝试一下别的单词吧~".toText() + image)
            }

            WIN -> {
                event.content().send(image)
                event.content()
                    .send("\n".toText() + wordleRoundNow.result.toText())
                groupCache.remove(getGroupId(event))
            }

            LOSS -> {
                event.content().send(image)
                event.content()
                    .send("\n".toText() + wordleRoundNow.result.toText())
                groupCache.remove(getGroupId(event))
            }

            ILLEGAL -> {
                event.content().send("唔……似乎填不进去呢~".toText() + image)
            }

            UNKNOWN -> {
                event.content()
                    .send("没有在词库中找到这个词呢……再换一个试试？".toText() + image)
            }

            null -> {
                event.content().send(image)
            }
        }
    }
}
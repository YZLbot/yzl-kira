package top.tbpdt.utils

import jakarta.annotation.PostConstruct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.springframework.context.annotation.Configuration
import top.tbpdt.logger


/**
 * @author Takeoff0518
 */
@Configuration
class JsonLoaderService(private val externalConfigManager: ExternalConfigManager) {

    // 存储解析后的 JSON 元素
    lateinit var divinationJsonElement: JsonElement
    lateinit var wordleJsonElement: JsonElement
    lateinit var divinationJsonString: String
    lateinit var lyricsJsonString: String
    lateinit var wordleJsonString: String

    @PostConstruct
    fun loadJsonFile() {
//        val resource = ClassPathResource("divination.json")
        divinationJsonString = externalConfigManager.readFileContent("config/divination.json")!!
        lyricsJsonString = externalConfigManager.readFileContent("config/lyrics.json")!!
        wordleJsonString = externalConfigManager.readFileContent("config/words.json")!!
        divinationJsonElement = Json.parseToJsonElement(divinationJsonString)
        wordleJsonElement = Json.parseToJsonElement(wordleJsonString)

        logger().info("抽签 JSON 文件加载完成，共 ${divinationJsonElement.jsonObject.size} 条~")
        logger().info("单词加载完成，共 ${wordleJsonElement.jsonObject.size} 条~")
    }
}
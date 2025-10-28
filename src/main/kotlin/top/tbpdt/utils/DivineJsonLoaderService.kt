package top.tbpdt.utils

import jakarta.annotation.PostConstruct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource


/**
 * @author Takeoff0518
 */
@Configuration
class DivineJsonLoaderService {

    // 存储解析后的 JSON 元素
    lateinit var jsonElement: JsonElement

    @PostConstruct
    fun loadJsonFile() {
        val resource = ClassPathResource("divination.json")
        val jsonString = resource.inputStream.bufferedReader().use { it.readText() }
        jsonElement = Json.parseToJsonElement(jsonString)
        println("JSON 文件加载完成，共 ${jsonElement.jsonObject.size} 条~")
    }
}
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
class DivineJsonLoaderService(private val externalConfigManager: ExternalConfigManager) {

    // 存储解析后的 JSON 元素
    lateinit var jsonElement: JsonElement

    @PostConstruct
    fun loadJsonFile() {
//        val resource = ClassPathResource("divination.json")
        val jsonString = externalConfigManager.readFileContent("config/divination.json")
        jsonElement = Json.parseToJsonElement(jsonString ?: "{}")
        logger().info("JSON 文件加载完成，共 ${jsonElement.jsonObject.size} 条~")
    }
}
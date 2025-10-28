package top.tbpdt.utils
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import top.tbpdt.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * @author Takeoff0518
 */
@Component
class ExternalConfigManager {

    companion object {
        private val EXTERNAL_FILES = listOf(
            "config/qq.bot.json",
            "config/lyrics.json",
            "config/divination.json"
        )
    }

    /**
     * 在应用启动完成后初始化外部文件
     */
    @EventListener(ApplicationReadyEvent::class)
    fun initializeExternalFiles() {
        EXTERNAL_FILES.forEach { filePath ->
            val externalFile = File(filePath)
            if (!externalFile.exists()) {
                copyFromJarToExternal(filePath, externalFile)
                logger().info("已创建外部文件: ${externalFile.absolutePath}")
            } else {
                logger().info("外部文件已存在: ${externalFile.absolutePath}")
            }
        }
    }

    /**
     * 从JAR内复制文件到外部
     */
    private fun copyFromJarToExternal(internalPath: String, externalFile: File) {
        try {
            externalFile.parentFile?.mkdirs()

            val classPathResource = ClassPathResource(internalPath)
            if (classPathResource.exists()) {
                classPathResource.inputStream.use { inputStream ->
                    Files.copy(inputStream, externalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                logger().error("JAR内未找到文件: $internalPath")
            }
        } catch (e: Exception) {
            logger().error("复制文件失败: $internalPath -> ${externalFile.absolutePath}")
            e.printStackTrace()
        }
    }

    /**
     * 读取文件内容，优先读取外部文件
     */
    fun readFileContent(filePath: String): String? {
        val externalFile = File(filePath)

        return try {
            if (externalFile.exists()) {
                externalFile.readText()
            } else {
                // 找不到喵
                readFromJar(filePath)
            }
        } catch (e: Exception) {
            logger().error("读取文件失败: $filePath")
            e.printStackTrace()
            null
        }
    }

    /**
     * 从JAR内读取文件
     */
    private fun readFromJar(filePath: String): String? {
        return try {
            val classPathResource = ClassPathResource(filePath)
            if (classPathResource.exists()) {
                classPathResource.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 写入内容到外部文件
     */
    fun writeToExternalFile(filePath: String, content: String): Boolean {
        return try {
            val externalFile = File(filePath)
            externalFile.parentFile?.mkdirs()
            externalFile.writeText(content)
            true
        } catch (e: Exception) {
            logger().error("写入文件失败: $filePath")
            e.printStackTrace()
            false
        }
    }

    /**
     * 检查外部文件是否存在
     */
    fun externalFileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
}
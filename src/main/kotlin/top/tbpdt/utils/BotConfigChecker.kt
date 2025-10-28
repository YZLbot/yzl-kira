package top.tbpdt.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import top.tbpdt.logger
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * @author Takeoff0518
 */


@Component
class BotConfigChecker : ApplicationRunner {

    private val objectMapper = ObjectMapper()
    private val scheduler = Executors.newScheduledThreadPool(1)

    companion object {
        private const val DEFAULT_APP_ID = "你的bot appid"
        private const val DEFAULT_SECRET = "你的bot secret"
        private const val DEFAULT_TOKEN = "你的bot token, 从 4.0.0-beta6 之后暂时不再会用到了"
    }

    override fun run(args: ApplicationArguments?) {
        scheduler.schedule({
            checkBotConfigs()
        }, 3, TimeUnit.SECONDS)
    }

    private fun checkBotConfigs() {
        val externalBotDir = File("config")
//        for (i in (externalBotDir.listFiles() ?: emptyArray())) {
//            println(i.name)
//        }
        if (!externalBotDir.exists()) {
            showConfigPrompt("未找到外部配置文件目录，请确保程序有写入权限")
            exitApplication()
            return
        }

        val configFiles = externalBotDir.listFiles { file ->
            file.name.endsWith("bot.json")
        } ?: emptyArray()

        if (configFiles.isEmpty()) {
            showConfigPrompt("未找到任何机器人配置文件")
            exitApplication()
            return
        }

        var hasUnconfiguredBot = false

        configFiles.forEach { file ->
            try {
                if (isUnconfiguredBot(file)) {
                    logger().warn("检测到未配置的 bot 配置文件: ${file.name}")
                    hasUnconfiguredBot = true
                } else {
                    logger().info("配置文件已正确设置: ${file.name}")
                }
            } catch (e: Exception) {
                logger().error("无法解析配置文件 ${file.name}: ${e.message}")
            }
        }

        if (hasUnconfiguredBot) {
            showDetailedConfigPrompt()
            exitApplication()
        }
//        else {
//            println("所有 bot 配置都已正确设置，程序继续运行...")
//        }
    }

    private fun isUnconfiguredBot(configFile: File): Boolean {
        val jsonContent = configFile.readText()
        val rootNode = objectMapper.readTree(jsonContent)

        // 检查ticket字段下的配置
        val ticketNode = rootNode.path("ticket")
        if (ticketNode.isMissingNode) {
            return true
        }

        val appId = ticketNode.path("appId").asText()
        val secret = ticketNode.path("secret").asText()
        val token = ticketNode.path("token").asText()

        // 如果任意一个字段还是默认值，则认为未配置
        return appId == DEFAULT_APP_ID ||
                secret == DEFAULT_SECRET
    }

    private fun showConfigPrompt(reason: String) {
        logger().error("bot 配置检查失败")
        logger().error(reason)
    }

    private fun showDetailedConfigPrompt() {
        logger().warn("bot配置未完成！检测到 bot 配置文件中的字段仍为默认值")
        logger().warn("请按照以下步骤操作：")
        logger().warn("1. 打开 ${File("config").absolutePath} 目录")
        logger().warn("2. 编辑其中的 *.bot.json 文件")
        logger().warn("3. 将上述字段替换为你的 bot 凭证")
        logger().warn("4. 保存文件后重新启动程序")
        logger().warn("配置文件位置: ${File("config").absolutePath}")
    }

    private fun exitApplication() {
        scheduler.shutdown()
        exitProcess(0)
    }
}
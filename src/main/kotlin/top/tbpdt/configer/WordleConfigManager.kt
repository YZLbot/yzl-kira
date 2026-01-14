package top.tbpdt.configer

import org.springframework.core.env.StandardEnvironment
import org.springframework.stereotype.Service

/**
 * @author Takeoff0518
 */
@Service
class WordleConfigManager(environment: StandardEnvironment) : BaseYamlConfigManager<WordleConfig>(
    environment = environment,
    configClass = WordleConfig::class,
    configFileName = "wordle-config.yml"
) {
    override fun createDefaultConfig(): WordleConfig {
        return WordleConfig(
            dictIndex = mutableMapOf(
                "123" to 0
            ),
            dictListString = mutableListOf(
                "CET-4",
                "CET-6"
            )
        )
    }
}

data class WordleConfig(
    var dictIndex: MutableMap<String, Int>,
    val dictListString: MutableList<String>
)

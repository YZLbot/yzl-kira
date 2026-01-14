package top.tbpdt.configer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.reflect.KClass

/**
 * 抽象 YAML 配置管理器，用于加载、管理和持久化 YAML 格式的配置文件。
 *
 * 该类提供了以下功能：
 * 1. 自动创建默认配置文件（当文件不存在时）
 * 2. 从 YAML 文件加载配置并反序列化为指定类型
 * 3. 将配置注册到 Spring 环境作为 PropertySource
 * 4. 支持配置热重载（通过文件修改时间检测）
 * 5. 提供配置变更监听机制
 * 6. 支持配置的备份和保存
 *
 * e.g.:
 * ```kotlin
 * class MyConfigManager(environment: StandardEnvironment) :
 *     BaseYamlConfigManager<MyConfig>(environment, MyConfig::class, "yamlConfig.yml") {
 *
 *     override fun createDefaultConfig(): MyConfig {
 *         return MyConfig("default", 100)
 *     }
 * }
 * ```
 *
 * @param T 配置数据类型，必须是支持 Jackson 序列化的Kotlin数据类
 * @param environment Spring 环境，用于注册配置属性源
 * @param configClass 配置类的 KClass 对象，用于反序列化
 * @param configFileName 配置文件名（相对于"./config/"目录）
 *
 * @author Takeoff0518
 * @since 3.0.0
 * @see com.fasterxml.jackson.databind.ObjectMapper
 * @see org.springframework.core.env.StandardEnvironment
 * @see org.springframework.core.env.MapPropertySource
 */
abstract class BaseYamlConfigManager<T : Any>(
    private val environment: StandardEnvironment,
    private val configClass: KClass<T>,
    private val configFileName: String
) {

    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val configFile = File("./config/$configFileName")
    private var lastModified: Long = 0

    // 当前实例
    protected var _currentConfig: T? = null

    // 配置变更监听器
    private val listeners = mutableListOf<(T) -> Unit>()

    /**
     * 初始化配置管理器
     */
    @Synchronized
    fun initialize(): T {
        ensureConfigFileExists()
        loadExternalConfig()
        lastModified = configFile.lastModified()
        return _currentConfig!!
    }

    /**
     * 确保配置文件存在，如果不存在则创建默认配置
     */
    private fun ensureConfigFileExists(): Boolean {
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            val defaultConfig = createDefaultConfig()
            objectMapper.writeValue(configFile, defaultConfig)
            return false
        }
        return true
    }

    /**
     * 创建默认配置 - 子类必须实现
     */
    protected abstract fun createDefaultConfig(): T

    /**
     * 从外部文件加载配置
     */
    @Synchronized
    fun loadExternalConfig(): T {
        try {
            val configMap: Map<String, Any> = objectMapper.readValue(configFile)

            // 找到实际的配置（去掉可能的根节点）
            val configData = findConfigData(configMap)

            val configInstance = objectMapper.convertValue(configData, configClass.java)

            // 更新PropertySource
            updatePropertySource(configMap)

            _currentConfig = configInstance
            notifyListeners(configInstance)

            return configInstance
        } catch (e: Exception) {
            throw RuntimeException("Failed to load config from $configFileName: ${e.message}", e)
        }
    }

    /**
     * 在配置映射中查找实际的配置数据
     */
    private fun findConfigData(configMap: Map<String, Any>): Any {
        return when {
            configMap.size == 1 && configMap.values.first() is Map<*, *> ->
                configMap.values.first()

            else -> configMap
        }
    }

    /**
     * 更新Spring环境中的PropertySource
     */
    private fun updatePropertySource(configMap: Map<String, Any>) {
        val propertySourceName = "externalConfig_${configFileName.removeSuffix(".yml")}"
        val propertySource = MapPropertySource(propertySourceName, configMap)

        val propertySources = environment.propertySources
        if (propertySources.contains(propertySourceName)) {
            propertySources.replace(propertySourceName, propertySource)
        } else {
            propertySources.addFirst(propertySource)
        }
    }

    /**
     * 保存当前配置到文件
     */
    @Synchronized
    fun saveConfigToFile(config: T? = null): Boolean {
        try {
            val configToSave = config ?: _currentConfig ?: throw IllegalStateException("No config to save")

            // 创建备份
            if (configFile.exists()) {
                val backupFile = File(configFile.parent, "${configFile.name}.backup")
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, configToSave)
            lastModified = configFile.lastModified()

            return true
        } catch (e: Exception) {
            throw RuntimeException("Failed to save config to $configFileName: ${e.message}", e)
        }
    }

    /**
     * 获取当前配置
     */
    @Synchronized
    fun getCurrentConfig(): T {
        return _currentConfig ?: throw IllegalStateException("Config not initialized")
    }

    /**
     * 更新配置
     */
    @Synchronized
    fun updateConfig(updater: (T) -> Unit): T {
        val config = getCurrentConfig()
        updater(config)
        saveConfigToFile(config)
        notifyListeners(config)
        return config
    }

    /**
     * 检查配置文件是否有更新
     */
    fun hasFileChanged(): Boolean {
        return configFile.exists() && configFile.lastModified() > lastModified
    }

    /**
     * 添加配置变更监听器
     */
    fun addChangeListener(listener: (T) -> Unit) {
        listeners.add(listener)
    }

    /**
     * 移除配置变更监听器
     */
    fun removeChangeListener(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * 通知所有监听器配置已变更
     */
    private fun notifyListeners(config: T) {
        listeners.forEach { it(config) }
    }

    /**
     * 获取配置文件路径
     */
    fun getConfigFilePath(): String = configFile.absolutePath
}
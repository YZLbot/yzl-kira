package top.tbpdt

import love.forte.simbot.spring.EnableSimbot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

/**
 * Spring程序的入口注解类。添加 [EnableSimbot] 注解来标记启用 simbot 相关的功能。
 */
@EnableSimbot
@SpringBootApplication
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
    
}

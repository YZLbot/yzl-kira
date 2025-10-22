package top.tbpdt

import love.forte.simbot.spring.EnableSimbot
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring程序的入口注解类。添加 [EnableSimbot] 注解来标记启用 simbot 相关的功能。
 */
@EnableSimbot
@SpringBootApplication
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}

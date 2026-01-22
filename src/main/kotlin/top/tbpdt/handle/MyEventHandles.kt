package top.tbpdt.handle

import love.forte.simbot.common.PriorityConstant
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.Event
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import top.tbpdt.logger

/**
 * 一个用于承载监听函数的事件处理器类。
 *
 * 将它标记为 [Component] 以交由Spring进行管理，
 * simbot-spring 会解析其中标记了 [Listener] 的函数们。
 */
@Component
class MyEventHandles {
    /**
     * 示例1: 监听所有的事件，然后将它们输出到控制台。
     *
     * @param event 你要监听的事件的类型。
     * 必须是一个 [Event] 类型的子类
     */


    @Listener
    @ContentTrim
    @Filter("^/测试$")
    suspend fun handleHelloMessageEvent(event: ChatGroupMessageEvent) {
        event.content().send(
            (
                    listOf(
                        "200 OK",
                        "我在~",
                        "阿绫~阿绫~"
                    ).random()
                    )
        )
    }

}
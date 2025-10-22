package top.tbpdt.handle

import kotlinx.coroutines.delay
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.Event
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component

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
    fun handleAllAndPrint(event: Event) {
        if (event::class.simpleName == "QGGroupSendSupportPostSendEventImpl") {
            println("发送了信息！")
        }
    }

    @Listener
    suspend fun handleMessageEvent(event: ChatGroupMessageEvent) {
        println("[${event.content().name}(${event.content().id})] ${event.author().name}(${event.author().id}) -> ${event.messageContent.messages}")
    }

    @Listener
    @ContentTrim
    @Filter("^/test$")
    suspend fun handleHelloMessageEvent(event: ChatGroupMessageEvent) {
        event.content().send(
            ("\n\n" +
                    listOf(
                        "alive",
                        "200 OK",
                        "418 I'm a teapot",
                        "我已出舱，感觉良好~",
                        "Hello World!",
                        "摸鱼ing...",
                        "QTH INTERNET 73.",
                        "5X9 73.",
                        "FB UR 5NN PSE K.",
                        "online"
                    ).random()
                    )
        )
        delay((100L..1000L).random())
        event.content().send("\n\n我在~")
    }

}
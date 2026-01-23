package top.tbpdt.handle

import love.forte.simbot.common.PriorityConstant
import love.forte.simbot.common.id.ID
import love.forte.simbot.component.qguild.event.QGC2CMessageCreateEvent
import love.forte.simbot.component.qguild.event.QGC2CMessageCreateEventPostReplyEvent
import love.forte.simbot.component.qguild.event.QGGroupAddRobotEvent
import love.forte.simbot.component.qguild.event.QGGroupDelRobotEvent
import love.forte.simbot.component.qguild.event.QGGroupSendSupportPostSendEvent
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.InteractionMessage
import love.forte.simbot.message.*
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import top.tbpdt.logger

/**
 * @author Takeoff0518
 */
@Component
class SimpleEventLogger {

    fun ID.shortHash() = this.toString().take(8).lowercase()

    /**
     * 将消息包装类统一转为可读字符串
     */
    fun InteractionMessage.toLogString(): String {
        return when (this) {
            is InteractionMessage.Text -> this.text

            is InteractionMessage.Message -> this.message.toLogString()

            is InteractionMessage.MessageContent -> {
                // MessageContent 内部通常也包含 messages 序列
                this.messageContent.messages.toLogString()
            }

            else -> "[未知交互消息: ${this::class.simpleName}]"
        }
    }

    /**
     * 处理 Element 和 Messages 的差异
     */
    fun Message.toLogString(): String {
        val messages: Messages = when (this) {
            is Messages -> this
            is Message.Element -> messagesOf(this) // 将单元素包装成消息链
        }

        return messages.joinToString(separator = " ", prefix = "", postfix = "") { element ->
            when (element) {
                is PlainText -> element.text
                is Image -> "[图片]"
                is At -> "[@${element.target}]"
                is AtAll -> "[@全体成员]"
                is Face -> "[表情 ${element.id}]"
                else -> "[${element::class.simpleName}]"
            }
        }
    }

    // 群消息接收
    @Listener(priority = PriorityConstant.PRIORITIZE_9)
    suspend fun onBotGroupReceive(event: ChatGroupMessageEvent) {
        val content = event.messageContent.messages.toLogString()
        logger().info("[群 ${event.content().id.shortHash()}] ${event.author().id.shortHash()} -> $content")
    }

    // 群消息发送
    @Listener(priority = PriorityConstant.PRIORITIZE_9)
    suspend fun onBotGroupSend(event: QGGroupSendSupportPostSendEvent) {
        val content = event.message.toLogString()
        logger().info("[群 ${event.content.id.shortHash()}] <- $content")
    }

    // 好友消息接收
    @Listener(priority = PriorityConstant.PRIORITIZE_9)
    suspend fun onBotFriendReceive(event: QGC2CMessageCreateEvent) {
        val content = event.messageContent.messages.toLogString()
        logger().info("[好友 ${event.authorId.shortHash()}] -> $content")
    }

    // 好友消息发送
    @Listener(priority = PriorityConstant.PRIORITIZE_9)
    suspend fun onBotFriendSend(event: QGC2CMessageCreateEventPostReplyEvent) {
        val content = event.message.toLogString()
        logger().info("[好友 ${event.content.content().id.shortHash()}] <- $content")
    }

    // 加群
    @Listener(priority = PriorityConstant.PRIORITIZE_9)
    suspend fun onGroupAddBot(event: QGGroupAddRobotEvent) {
        logger().info("机器人被 ${event.operator().id.shortHash()} 添加到群聊 ${event.content().id.shortHash()}")
    }

    // 退群
    @Listener(priority = PriorityConstant.PRIORITIZE_9)
    suspend fun onGroupDelBot(event: QGGroupDelRobotEvent) {
        logger().info("机器人被 ${event.operator().id.shortHash()} 移出群聊 ${event.content().id.shortHash()}")
    }

}
package com.lanchat.network

import java.util.UUID

/**
 * 机器人用户
 * 用于测试聊天功能
 */
data class Bot(
    val id: String = "bot_${UUID.randomUUID().toString().substring(0, 8)}",
    val name: String,
    val avatar: String? = null,
    val description: String = "我是聊天机器人，可以陪你聊天测试",
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 转换为Peer对象
     */
    fun toPeer(): Peer {
        return Peer(
            id = id,
            username = "$name 🤖",
            ipAddress = "127.0.0.1",
            port = 0,
            avatar = avatar,
            isOnline = true
        )
    }
    
    /**
     * 自动回复消息
     */
    fun autoReply(message: String): String {
        val replies = when {
            message.contains("你好") || message.contains("hi") || message.contains("hello") -> 
                listOf("你好呀！", "Hi~", "很高兴见到你！", "👋")
            message.contains("名字") || message.contains("叫什么") -> 
                listOf("我是$name", "你可以叫我$name", "我的名字是$name~")
            message.contains("在吗") || message.contains("在不在") -> 
                listOf("我在呀！", "在的，有什么事吗？", "嗯嗯，我在~")
            message.contains("谢谢") || message.contains("感谢") -> 
                listOf("不客气！", "不用谢~", "能帮到你我很开心！")
            message.contains("再见") || message.contains("拜拜") -> 
                listOf("再见！", "下次聊~", "拜拜👋")
            message.contains("?") || message.contains("？") -> 
                listOf("好问题！", "让我想想...", "嗯，这个嘛...", "🤔")
            else -> 
                listOf("收到！", "嗯嗯", "好的~", "😄", "👍", "有意思！", "继续说~")
        }
        return replies.random()
    }
}

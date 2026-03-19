package com.lanchat

import com.lanchat.network.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 机器人功能测试
 */
class BotTest {
    
    @Test
    fun `test create bot`() {
        val bot = Bot(name = "测试机器人")
        
        assertNotNull(bot.id)
        assertTrue(bot.id.startsWith("bot_"))
        assertEquals("测试机器人", bot.name)
        assertNotNull(bot.description)
        assertTrue(bot.createdAt > 0)
    }
    
    @Test
    fun `test create bot with description`() {
        val bot = Bot(
            name = "客服机器人",
            description = "我是客服机器人，可以帮助你解答问题"
        )
        
        assertEquals("客服机器人", bot.name)
        assertEquals("我是客服机器人，可以帮助你解答问题", bot.description)
    }
    
    @Test
    fun `test bot auto reply to greeting`() {
        val bot = Bot(name = "小助手")
        
        val replies = listOf("你好", "hi", "hello", "Hi", "Hello", "HI")
        
        replies.forEach { greeting ->
            val reply = bot.autoReply(greeting)
            assertNotNull(reply)
            assertTrue(reply.isNotEmpty())
            println("Greeting '$greeting' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot auto reply to name question`() {
        val bot = Bot(name = "小明")
        
        val questions = listOf("你叫什么名字", "你的名字是什么", "你叫什么")
        
        questions.forEach { question ->
            val reply = bot.autoReply(question)
            assertTrue(reply.contains("小明"))
            println("Question '$question' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot auto reply to presence check`() {
        val bot = Bot(name = "机器人")
        
        val checks = listOf("在吗", "在不在", "在不")
        
        checks.forEach { check ->
            val reply = bot.autoReply(check)
            assertNotNull(reply)
            assertTrue(reply.isNotEmpty())
            println("Check '$check' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot auto reply to thanks`() {
        val bot = Bot(name = "助手")
        
        val thanks = listOf("谢谢", "感谢", "多谢")
        
        thanks.forEach { thank ->
            val reply = bot.autoReply(thank)
            assertNotNull(reply)
            assertTrue(reply.isNotEmpty())
            println("Thanks '$thank' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot auto reply to goodbye`() {
        val bot = Bot(name = "助手")
        
        val goodbyes = listOf("再见", "拜拜", "下次见")
        
        goodbyes.forEach { goodbye ->
            val reply = bot.autoReply(goodbye)
            assertNotNull(reply)
            assertTrue(reply.isNotEmpty())
            println("Goodbye '$goodbye' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot auto reply to question`() {
        val bot = Bot(name = "问答机器人")
        
        val questions = listOf("你好吗？", "这是什么？", "怎么回事？")
        
        questions.forEach { question ->
            val reply = bot.autoReply(question)
            assertNotNull(reply)
            assertTrue(reply.isNotEmpty())
            println("Question '$question' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot auto reply to random message`() {
        val bot = Bot(name = "聊天机器人")
        
        val messages = listOf("测试消息", "哈哈", "随便说说", "12345")
        
        messages.forEach { message ->
            val reply = bot.autoReply(message)
            assertNotNull(reply)
            assertTrue(reply.isNotEmpty())
            println("Message '$message' -> Reply: $reply")
        }
    }
    
    @Test
    fun `test bot id uniqueness`() {
        val botIds = mutableSetOf<String>()
        
        for (i in 1..100) {
            val bot = Bot(name = "机器人$i")
            botIds.add(bot.id)
        }
        
        // 验证ID唯一性
        assertEquals(100, botIds.size)
    }
    
    @Test
    fun `test bot to peer conversion`() {
        val bot = Bot(
            id = "bot_test",
            name = "测试机器人",
            description = "测试描述"
        )
        
        val peer = bot.toPeer()
        
        assertEquals("bot_test", peer.id)
        assertTrue(peer.username.contains("测试机器人"))
        assertTrue(peer.username.contains("🤖"))
        assertEquals("127.0.0.1", peer.ipAddress)
        assertEquals(0, peer.port)
        assertTrue(peer.isOnline)
    }
}

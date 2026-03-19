package com.lanchat

import com.lanchat.message.Message
import com.lanchat.message.MessageType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息功能测试
 */
class MessageTest {
    
    @Test
    fun `test create text message`() {
        val message = Message(
            type = MessageType.TEXT,
            senderId = "sender_1",
            receiverId = "receiver_1",
            content = "Hello, World!"
        )
        
        assertNotNull(message.id)
        assertEquals(MessageType.TEXT, message.type)
        assertEquals("sender_1", message.senderId)
        assertEquals("receiver_1", message.receiverId)
        assertEquals("Hello, World!", message.content)
        assertTrue(message.timestamp > 0)
    }
    
    @Test
    fun `test message formatted time`() {
        val message = Message(
            type = MessageType.TEXT,
            senderId = "sender_1",
            receiverId = "receiver_1",
            content = "Test",
            timestamp = System.currentTimeMillis()
        )
        
        val formattedTime = message.getFormattedTime()
        assertNotNull(formattedTime)
        assertTrue(formattedTime.isNotEmpty())
        
        // 验证格式：HH:mm
        val timePattern = Regex("\\d{2}:\\d{2}")
        assertTrue(timePattern.matches(formattedTime))
    }
    
    @Test
    fun `test mention message`() {
        val message = Message(
            type = MessageType.MENTION_MEMBER,
            senderId = "sender_1",
            receiverId = "group_1",
            content = "@张三 你好",
            mentionedUserIds = listOf("user_1"),
            mentionAll = false,
            groupId = "group_1"
        )
        
        assertEquals(MessageType.MENTION_MEMBER, message.type)
        assertEquals(1, message.mentionedUserIds.size)
        assertTrue(message.mentionedUserIds.contains("user_1"))
        assertFalse(message.mentionAll)
        assertEquals("group_1", message.groupId)
    }
    
    @Test
    fun `test mention all message`() {
        val message = Message(
            type = MessageType.MENTION_ALL,
            senderId = "sender_1",
            receiverId = "group_1",
            content = "@全体成员 开会了",
            mentionAll = true,
            groupId = "group_1"
        )
        
        assertEquals(MessageType.MENTION_ALL, message.type)
        assertTrue(message.mentionAll)
    }
    
    @Test
    fun `test file message`() {
        val message = Message(
            type = MessageType.FILE,
            senderId = "sender_1",
            receiverId = "receiver_1",
            content = "document.pdf",
            fileName = "document.pdf"
        )
        
        assertEquals(MessageType.FILE, message.type)
        assertEquals("document.pdf", message.content)
        assertEquals("document.pdf", message.fileName)
    }
    
    @Test
    fun `test image message`() {
        val message = Message(
            type = MessageType.IMAGE,
            senderId = "sender_1",
            receiverId = "receiver_1",
            content = "photo.png",
            fileName = "photo.png"
        )
        
        assertEquals(MessageType.IMAGE, message.type)
    }
    
    @Test
    fun `test group chat message`() {
        val message = Message(
            type = MessageType.GROUP_CHAT,
            senderId = "sender_1",
            receiverId = "group_1",
            content = "大家好",
            groupId = "group_1",
            senderName = "张三"
        )
        
        assertEquals(MessageType.GROUP_CHAT, message.type)
        assertEquals("group_1", message.groupId)
        assertEquals("张三", message.senderName)
    }
    
    @Test
    fun `test message id uniqueness`() {
        val messages = mutableSetOf<String>()
        
        for (i in 1..100) {
            val message = Message(
                type = MessageType.TEXT,
                senderId = "sender",
                receiverId = "receiver",
                content = "Message $i"
            )
            messages.add(message.id)
        }
        
        // 验证ID唯一性
        assertEquals(100, messages.size)
    }
}

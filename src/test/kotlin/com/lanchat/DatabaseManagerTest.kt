package com.lanchat

import com.lanchat.db.DatabaseManager
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.Bot
import com.lanchat.network.Group
import com.lanchat.network.Peer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.util.UUID

/**
 * 数据库管理器测试
 */
class DatabaseManagerTest {
    
    private val testDir = System.getProperty("java.io.tmpdir") + "/lanchat_test_${System.currentTimeMillis()}"
    
    @BeforeEach
    fun setup() {
        File(testDir).mkdirs()
        DatabaseManager.initialize(testDir)
    }
    
    @AfterEach
    fun cleanup() {
        DatabaseManager.close()
        File(testDir).deleteRecursively()
    }
    
    // =============== 设置测试 ===============
    
    @Test
    fun `test save and get setting`() {
        DatabaseManager.saveSetting("username", "testuser")
        assertEquals("testuser", DatabaseManager.getSetting("username"))
        
        DatabaseManager.saveSetting("username", "newuser")
        assertEquals("newuser", DatabaseManager.getSetting("username"))
        
        assertEquals("", DatabaseManager.getSetting("nonexistent"))
        assertEquals("default", DatabaseManager.getSetting("nonexistent", "default"))
    }
    
    // =============== 联系人测试 ===============
    
    @Test
    fun `test save and load peer`() {
        val peer = Peer(
            id = "peer_1",
            username = "张三",
            ipAddress = "192.168.1.100",
            port = 8889,
            avatar = null,
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )
        
        DatabaseManager.savePeer(peer)
        
        val loadedPeers = DatabaseManager.loadPeers()
        assertTrue(loadedPeers.containsKey("peer_1"))
        
        val loadedPeer = loadedPeers["peer_1"]!!
        assertEquals("张三", loadedPeer.username)
        assertEquals("192.168.1.100", loadedPeer.ipAddress)
        assertEquals(8889, loadedPeer.port)
        assertTrue(loadedPeer.isOnline)
    }
    
    @Test
    fun `test delete peer`() {
        val peer = Peer(
            id = "peer_2",
            username = "李四",
            ipAddress = "192.168.1.101",
            port = 8889
        )
        
        DatabaseManager.savePeer(peer)
        assertTrue(DatabaseManager.loadPeers().containsKey("peer_2"))
        
        DatabaseManager.deletePeer("peer_2")
        assertFalse(DatabaseManager.loadPeers().containsKey("peer_2"))
    }
    
    @Test
    fun `test save multiple peers`() {
        for (i in 1..10) {
            val peer = Peer(
                id = "peer_$i",
                username = "用户$i",
                ipAddress = "192.168.1.$i",
                port = 8889
            )
            DatabaseManager.savePeer(peer)
        }
        
        val loadedPeers = DatabaseManager.loadPeers()
        assertEquals(10, loadedPeers.size)
    }
    
    // =============== 消息测试 ===============
    
    @Test
    fun `test save and load message`() {
        val message = Message(
            id = "msg_1",
            type = MessageType.TEXT,
            senderId = "sender_1",
            receiverId = "receiver_1",
            content = "Hello, World!",
            timestamp = System.currentTimeMillis(),
            senderName = "发送者"
        )
        
        DatabaseManager.saveMessage(message)
        
        // currentUserId 应该是当前用户ID，消息会被归类到发送者的chatId
        // 因为 senderId != currentUserId，所以 chatId = senderId
        val loadedMessages = DatabaseManager.loadMessages("receiver_1")
        assertTrue(loadedMessages.containsKey("sender_1"))
        assertEquals(1, loadedMessages["sender_1"]!!.size)
        
        val loadedMessage = loadedMessages["sender_1"]!![0]
        assertEquals("Hello, World!", loadedMessage.content)
        assertEquals("发送者", loadedMessage.senderName)
    }
    
    @Test
    fun `test message read status`() {
        val message = Message(
            id = "msg_read_1",
            type = MessageType.GROUP_CHAT,
            senderId = "sender_1",
            receiverId = "group_1",
            content = "群消息测试",
            timestamp = System.currentTimeMillis(),
            senderName = "发送者",
            groupId = "group_1",
            readByUserIds = listOf("user_1"),
            readByUserNames = listOf("张三")
        )
        
        DatabaseManager.saveMessage(message)
        
        val loadedMessages = DatabaseManager.loadMessages("sender_1")
        val loadedMessage = loadedMessages["group_1"]!![0]
        
        assertEquals(1, loadedMessage.readByUserIds.size)
        assertTrue(loadedMessage.readByUserIds.contains("user_1"))
        assertEquals("张三", loadedMessage.readByUserNames[0])
    }
    
    @Test
    fun `test update message read by`() {
        val message = Message(
            id = "msg_read_update",
            type = MessageType.GROUP_CHAT,
            senderId = "sender_1",
            receiverId = "group_1",
            content = "测试更新已读状态",
            timestamp = System.currentTimeMillis(),
            senderName = "发送者",
            groupId = "group_1"
        )
        
        DatabaseManager.saveMessage(message)
        
        // 更新已读状态
        DatabaseManager.updateMessageReadBy("msg_read_update", "user_1", "张三")
        
        val loadedMessages = DatabaseManager.loadMessages("sender_1")
        val loadedMessage = loadedMessages["group_1"]!!.find { it.id == "msg_read_update" }!!
        
        assertTrue(loadedMessage.readByUserIds.contains("user_1"))
        assertTrue(loadedMessage.readByUserNames.contains("张三"))
    }
    
    @Test
    fun `test message with mention`() {
        val message = Message(
            id = "msg_2",
            type = MessageType.MENTION_MEMBER,
            senderId = "sender_1",
            receiverId = "group_1",
            content = "@张三 你好",
            timestamp = System.currentTimeMillis(),
            mentionedUserIds = listOf("user_1", "user_2"),
            mentionAll = false,
            groupId = "group_1"
        )
        
        DatabaseManager.saveMessage(message)
        
        val loadedMessages = DatabaseManager.loadMessages("sender_1")
        val loadedMessage = loadedMessages["group_1"]!![0]
        
        assertEquals(2, loadedMessage.mentionedUserIds.size)
        assertTrue(loadedMessage.mentionedUserIds.contains("user_1"))
        assertFalse(loadedMessage.mentionAll)
    }
    
    // =============== 群组测试 ===============
    
    @Test
    fun `test save and load group`() {
        val group = Group(
            id = "group_1",
            name = "测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1", "member_2")
        )
        
        DatabaseManager.saveGroup(group)
        
        val loadedGroups = DatabaseManager.loadGroups()
        assertTrue(loadedGroups.containsKey("group_1"))
        
        val loadedGroup = loadedGroups["group_1"]!!
        assertEquals("测试群", loadedGroup.name)
        assertEquals("owner_1", loadedGroup.ownerId)
        assertEquals(3, loadedGroup.memberIds.size)
        assertTrue(loadedGroup.isOwner("owner_1"))
    }
    
    @Test
    fun `test delete group`() {
        val group = Group(
            id = "group_2",
            name = "待删除群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1")
        )
        
        DatabaseManager.saveGroup(group)
        assertTrue(DatabaseManager.loadGroups().containsKey("group_2"))
        
        DatabaseManager.deleteGroup("group_2")
        assertFalse(DatabaseManager.loadGroups().containsKey("group_2"))
    }
    
    // =============== 机器人测试 ===============
    
    @Test
    fun `test save and load bot`() {
        val bot = Bot(
            id = "bot_1",
            name = "小助手",
            description = "我是一个测试机器人"
        )
        
        DatabaseManager.saveBot(bot)
        
        val loadedBots = DatabaseManager.loadBots()
        assertTrue(loadedBots.containsKey("bot_1"))
        
        val loadedBot = loadedBots["bot_1"]!!
        assertEquals("小助手", loadedBot.name)
        assertEquals("我是一个测试机器人", loadedBot.description)
    }
    
    @Test
    fun `test bot auto reply`() {
        val bot = Bot(name = "测试机器人")
        
        // 测试各种消息的回复
        val helloReply = bot.autoReply("你好")
        assertTrue(helloReply.isNotEmpty())
        
        val nameReply = bot.autoReply("你叫什么名字")
        assertTrue(nameReply.contains("测试机器人"))
        
        val byeReply = bot.autoReply("再见")
        assertTrue(byeReply.isNotEmpty())
    }
    
    @Test
    fun `test delete bot`() {
        val bot = Bot(id = "bot_2", name = "待删除机器人")
        
        DatabaseManager.saveBot(bot)
        assertTrue(DatabaseManager.loadBots().containsKey("bot_2"))
        
        DatabaseManager.deleteBot("bot_2")
        assertFalse(DatabaseManager.loadBots().containsKey("bot_2"))
    }
}

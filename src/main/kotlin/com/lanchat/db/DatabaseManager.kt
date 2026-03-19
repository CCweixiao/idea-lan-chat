package com.lanchat.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.Bot
import com.lanchat.network.Group
import com.lanchat.network.Peer
import java.io.File
import java.sql.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据库管理器
 * 使用SQLite持久化存储数据
 */
object DatabaseManager {
    
    private const val DB_NAME = "lanchat.db"
    private val gson = Gson()
    
    private lateinit var dbPath: String
    private var connection: Connection? = null
    
    /**
     * 初始化数据库
     */
    fun initialize(dataDir: String) {
        dbPath = File(dataDir, DB_NAME).absolutePath
        File(dataDir).mkdirs()
        
        connect()
        createTables()
    }
    
    private fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            connection?.autoCommit = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun createTables() {
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """)
        
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS peers (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                ip_address TEXT NOT NULL,
                port INTEGER NOT NULL,
                avatar TEXT,
                is_online INTEGER DEFAULT 1,
                last_seen INTEGER
            )
        """)
        
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                receiver_id TEXT NOT NULL,
                content TEXT,
                file_path TEXT,
                timestamp INTEGER,
                sender_name TEXT,
                mentioned_user_ids TEXT,
                mention_all INTEGER DEFAULT 0,
                group_id TEXT
            )
        """)
        
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS groups (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                member_ids TEXT NOT NULL,
                created_at INTEGER
            )
        """)
        
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS bots (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                avatar TEXT,
                description TEXT,
                created_at INTEGER
            )
        """)
        
        // 创建索引
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages(receiver_id)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)")
    }
    
    private fun executeUpdate(sql: String) {
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeUpdate(sql)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // =============== 设置相关 ===============
    
    fun saveSetting(key: String, value: String) {
        try {
            val sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getSetting(key: String, default: String = ""): String {
        try {
            val sql = "SELECT value FROM settings WHERE key = ?"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery()?.use { rs ->
                    if (rs.next()) {
                        return rs.getString("value")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return default
    }
    
    // =============== 联系人相关 ===============
    
    fun savePeer(peer: Peer) {
        try {
            val sql = """
                INSERT OR REPLACE INTO peers 
                (id, username, ip_address, port, avatar, is_online, last_seen) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, peer.id)
                stmt.setString(2, peer.username)
                stmt.setString(3, peer.ipAddress)
                stmt.setInt(4, peer.port)
                stmt.setString(5, peer.avatar)
                stmt.setInt(6, if (peer.isOnline) 1 else 0)
                stmt.setLong(7, peer.lastSeen)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadPeers(): Map<String, Peer> {
        val peers = ConcurrentHashMap<String, Peer>()
        try {
            val sql = "SELECT * FROM peers"
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery(sql)?.use { rs ->
                    while (rs.next()) {
                        val peer = Peer(
                            id = rs.getString("id"),
                            username = rs.getString("username"),
                            ipAddress = rs.getString("ip_address"),
                            port = rs.getInt("port"),
                            avatar = rs.getString("avatar"),
                            isOnline = rs.getInt("is_online") == 1,
                            lastSeen = rs.getLong("last_seen")
                        )
                        peers[peer.id] = peer
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return peers
    }
    
    fun deletePeer(peerId: String) {
        try {
            val sql = "DELETE FROM peers WHERE id = ?"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, peerId)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // =============== 消息相关 ===============
    
    fun saveMessage(message: Message) {
        try {
            val sql = """
                INSERT OR REPLACE INTO messages 
                (id, type, sender_id, receiver_id, content, file_path, timestamp, 
                 sender_name, mentioned_user_ids, mention_all, group_id) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, message.id)
                stmt.setString(2, message.type.name)
                stmt.setString(3, message.senderId)
                stmt.setString(4, message.receiverId)
                stmt.setString(5, message.content)
                stmt.setString(6, message.fileName)
                stmt.setLong(7, message.timestamp)
                stmt.setString(8, message.senderName)
                stmt.setString(9, gson.toJson(message.mentionedUserIds))
                stmt.setInt(10, if (message.mentionAll) 1 else 0)
                stmt.setString(11, message.groupId)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadMessages(): Map<String, MutableList<Message>> {
        val messages = ConcurrentHashMap<String, MutableList<Message>>()
        try {
            val sql = "SELECT * FROM messages ORDER BY timestamp ASC"
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery(sql)?.use { rs ->
                    while (rs.next()) {
                        val mentionedIds: List<String> = try {
                            gson.fromJson(rs.getString("mentioned_user_ids"), 
                                object : TypeToken<List<String>>() {}.type) ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        val message = Message(
                            id = rs.getString("id"),
                            type = MessageType.valueOf(rs.getString("type")),
                            senderId = rs.getString("sender_id"),
                            receiverId = rs.getString("receiver_id"),
                            content = rs.getString("content"),
                            fileName = rs.getString("file_path"),
                            timestamp = rs.getLong("timestamp"),
                            senderName = rs.getString("sender_name"),
                            mentionedUserIds = mentionedIds,
                            mentionAll = rs.getInt("mention_all") == 1,
                            groupId = rs.getString("group_id")
                        )
                        
                        // 按 receiver_id 分组
                        messages.getOrPut(message.receiverId) { mutableListOf() }.add(message)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return messages
    }
    
    // =============== 群组相关 ===============
    
    fun saveGroup(group: Group) {
        try {
            val sql = """
                INSERT OR REPLACE INTO groups 
                (id, name, owner_id, member_ids, created_at) 
                VALUES (?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, group.id)
                stmt.setString(2, group.name)
                stmt.setString(3, group.ownerId)
                stmt.setString(4, gson.toJson(group.memberIds))
                stmt.setLong(5, group.createdAt)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadGroups(): Map<String, Group> {
        val groups = ConcurrentHashMap<String, Group>()
        try {
            val sql = "SELECT * FROM groups"
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery(sql)?.use { rs ->
                    while (rs.next()) {
                        val memberIds: List<String> = try {
                            gson.fromJson(rs.getString("member_ids"), 
                                object : TypeToken<List<String>>() {}.type) ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        val group = Group(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            ownerId = rs.getString("owner_id"),
                            memberIds = memberIds.toMutableList()
                        )
                        groups[group.id] = group
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return groups
    }
    
    fun deleteGroup(groupId: String) {
        try {
            val sql = "DELETE FROM groups WHERE id = ?"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, groupId)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // =============== 机器人相关 ===============
    
    fun saveBot(bot: Bot) {
        try {
            val sql = """
                INSERT OR REPLACE INTO bots 
                (id, name, avatar, description, created_at) 
                VALUES (?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, bot.id)
                stmt.setString(2, bot.name)
                stmt.setString(3, bot.avatar)
                stmt.setString(4, bot.description)
                stmt.setLong(5, bot.createdAt)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadBots(): Map<String, Bot> {
        val bots = ConcurrentHashMap<String, Bot>()
        try {
            val sql = "SELECT * FROM bots"
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery(sql)?.use { rs ->
                    while (rs.next()) {
                        val bot = Bot(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            avatar = rs.getString("avatar"),
                            description = rs.getString("description"),
                            createdAt = rs.getLong("created_at")
                        )
                        bots[bot.id] = bot
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bots
    }
    
    fun deleteBot(botId: String) {
        try {
            val sql = "DELETE FROM bots WHERE id = ?"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, botId)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 关闭数据库连接
     */
    fun close() {
        connection?.close()
        connection = null
    }
}

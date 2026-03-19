package com.lanchat.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.*
import java.io.File
import java.sql.*
import java.util.concurrent.ConcurrentHashMap

object DatabaseManager {

    private const val DB_NAME = "lanchat.db"
    private val gson = Gson()

    private lateinit var dbPath: String
    private var connection: Connection? = null

    fun initialize(dataDir: String) {
        dbPath = File(dataDir, DB_NAME).absolutePath
        File(dataDir).mkdirs()
        connect()
        createTables()
        migrateDatabase()
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
                created_at INTEGER,
                group_number TEXT DEFAULT ''
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

        executeUpdate("""
            CREATE TABLE IF NOT EXISTS friend_requests (
                id TEXT PRIMARY KEY,
                from_user_id TEXT NOT NULL,
                from_username TEXT NOT NULL,
                from_ip TEXT NOT NULL,
                from_port INTEGER NOT NULL,
                to_ip TEXT NOT NULL,
                to_port INTEGER NOT NULL,
                message TEXT,
                status TEXT NOT NULL DEFAULT 'PENDING_SENT',
                timestamp INTEGER
            )
        """)

        executeUpdate("""
            CREATE TABLE IF NOT EXISTS group_requests (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                group_number TEXT DEFAULT '',
                group_name TEXT DEFAULT '',
                requester_id TEXT NOT NULL,
                requester_name TEXT NOT NULL,
                requester_ip TEXT DEFAULT '',
                requester_port INTEGER DEFAULT 8889,
                target_id TEXT DEFAULT '',
                type TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                message TEXT DEFAULT '',
                timestamp INTEGER
            )
        """)

        executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages(receiver_id)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)")
    }

    private fun migrateDatabase() {
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeUpdate("ALTER TABLE groups ADD COLUMN group_number TEXT DEFAULT ''")
            }
        } catch (_: Exception) { }
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeUpdate("ALTER TABLE peers ADD COLUMN signature TEXT DEFAULT ''")
            }
        } catch (_: Exception) { }
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeUpdate("ALTER TABLE messages ADD COLUMN is_read INTEGER DEFAULT 0")
            }
        } catch (_: Exception) { }
    }

    private fun executeUpdate(sql: String) {
        try {
            connection?.createStatement()?.use { stmt -> stmt.executeUpdate(sql) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =============== Settings ===============

    fun saveSetting(key: String, value: String) {
        try {
            val sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, key); stmt.setString(2, value); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getSetting(key: String, default: String = ""): String {
        try {
            val sql = "SELECT value FROM settings WHERE key = ?"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery()?.use { rs -> if (rs.next()) return rs.getString("value") }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return default
    }

    // =============== Peers ===============

    fun savePeer(peer: Peer) {
        try {
            val sql = """
                INSERT OR REPLACE INTO peers 
                (id, username, ip_address, port, avatar, signature, is_online, last_seen) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, peer.id); stmt.setString(2, peer.username)
                stmt.setString(3, peer.ipAddress); stmt.setInt(4, peer.port)
                stmt.setString(5, peer.avatar); stmt.setString(6, peer.signature)
                stmt.setInt(7, if (peer.isOnline) 1 else 0)
                stmt.setLong(8, peer.lastSeen); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadPeers(): Map<String, Peer> {
        val peers = ConcurrentHashMap<String, Peer>()
        try {
            val sql = "SELECT * FROM peers"
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery(sql)?.use { rs ->
                    while (rs.next()) {
                        val peer = Peer(
                            id = rs.getString("id"), username = rs.getString("username"),
                            ipAddress = rs.getString("ip_address"), port = rs.getInt("port"),
                            avatar = rs.getString("avatar"),
                            signature = try { rs.getString("signature") } catch (_: Exception) { null },
                            isOnline = rs.getInt("is_online") == 1,
                            lastSeen = rs.getLong("last_seen")
                        )
                        peers[peer.id] = peer
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return peers
    }

    fun deletePeer(peerId: String) {
        try {
            connection?.prepareStatement("DELETE FROM peers WHERE id = ?")?.use { stmt ->
                stmt.setString(1, peerId); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 清空与指定联系人的聊天记录
     */
    fun clearMessages(chatId: String) {
        try {
            connection?.prepareStatement("DELETE FROM messages WHERE receiver_id = ? OR sender_id = ?")?.use { stmt ->
                stmt.setString(1, chatId); stmt.setString(2, chatId); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 清空所有聊天记录
     */
    fun clearAllMessages() {
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeUpdate("DELETE FROM messages")
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // =============== Messages ===============

    fun saveMessage(message: Message) {
        try {
            val sql = """
                INSERT OR REPLACE INTO messages 
                (id, type, sender_id, receiver_id, content, file_path, timestamp, 
                 sender_name, mentioned_user_ids, mention_all, group_id, is_read) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, message.id); stmt.setString(2, message.type.name)
                stmt.setString(3, message.senderId); stmt.setString(4, message.receiverId)
                stmt.setString(5, message.content); stmt.setString(6, message.fileName)
                stmt.setLong(7, message.timestamp); stmt.setString(8, message.senderName)
                stmt.setString(9, gson.toJson(message.mentionedUserIds))
                stmt.setInt(10, if (message.mentionAll) 1 else 0)
                stmt.setString(11, message.groupId)
                stmt.setInt(12, if (message.isRead) 1 else 0)
                stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 标记指定聊天的所有消息为已读
     */
    fun markMessagesAsRead(chatId: String, currentUserId: String) {
        try {
            // 标记私聊消息（对方发来的）
            connection?.prepareStatement(
                "UPDATE messages SET is_read = 1 WHERE sender_id = ? AND receiver_id = ? AND is_read = 0"
            )?.use { stmt ->
                stmt.setString(1, chatId)
                stmt.setString(2, currentUserId)
                stmt.executeUpdate()
            }
            // 标记群聊消息
            connection?.prepareStatement(
                "UPDATE messages SET is_read = 1 WHERE group_id = ? AND sender_id != ? AND is_read = 0"
            )?.use { stmt ->
                stmt.setString(1, chatId)
                stmt.setString(2, currentUserId)
                stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 获取未读消息计数
     */
    fun getUnreadCounts(currentUserId: String): Map<String, Int> {
        val counts = ConcurrentHashMap<String, Int>()
        try {
            // 私聊未读
            connection?.prepareStatement(
                "SELECT sender_id, COUNT(*) as cnt FROM messages WHERE receiver_id = ? AND sender_id != ? AND is_read = 0 GROUP BY sender_id"
            )?.use { stmt ->
                stmt.setString(1, currentUserId)
                stmt.setString(2, currentUserId)
                stmt.executeQuery()?.use { rs ->
                    while (rs.next()) {
                        counts[rs.getString("sender_id")] = rs.getInt("cnt")
                    }
                }
            }
            // 群聊未读
            connection?.prepareStatement(
                "SELECT group_id, COUNT(*) as cnt FROM messages WHERE group_id IS NOT NULL AND sender_id != ? AND is_read = 0 GROUP BY group_id"
            )?.use { stmt ->
                stmt.setString(1, currentUserId)
                stmt.executeQuery()?.use { rs ->
                    while (rs.next()) {
                        val groupId = rs.getString("group_id")
                        if (groupId != null) {
                            counts[groupId] = rs.getInt("cnt")
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return counts
    }

    fun loadMessages(currentUserId: String): Map<String, MutableList<Message>> {
        val messages = ConcurrentHashMap<String, MutableList<Message>>()
        try {
            val sql = "SELECT * FROM messages ORDER BY timestamp ASC"
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery(sql)?.use { rs ->
                    while (rs.next()) {
                        val mentionedIds: List<String> = try {
                            gson.fromJson(rs.getString("mentioned_user_ids"),
                                object : TypeToken<List<String>>() {}.type) ?: emptyList()
                        } catch (_: Exception) { emptyList() }

                        val isRead = try { rs.getInt("is_read") == 1 } catch (_: Exception) { false }

                        val message = Message(
                            id = rs.getString("id"),
                            type = MessageType.valueOf(rs.getString("type")),
                            senderId = rs.getString("sender_id"),
                            receiverId = rs.getString("receiver_id"),
                            content = rs.getString("content"),
                            fileName = rs.getString("file_path"),
                            timestamp = rs.getLong("timestamp"),
                            isRead = isRead,
                            senderName = rs.getString("sender_name"),
                            mentionedUserIds = mentionedIds,
                            mentionAll = rs.getInt("mention_all") == 1,
                            groupId = rs.getString("group_id")
                        )

                        val chatId = if (!message.groupId.isNullOrEmpty()) {
                            message.groupId
                        } else if (message.senderId == currentUserId) {
                            message.receiverId
                        } else {
                            message.senderId
                        }
                        messages.getOrPut(chatId) { mutableListOf() }.add(message)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return messages
    }

    // =============== Groups ===============

    fun saveGroup(group: Group) {
        try {
            val sql = """
                INSERT OR REPLACE INTO groups 
                (id, name, owner_id, member_ids, created_at, group_number) 
                VALUES (?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, group.id); stmt.setString(2, group.name)
                stmt.setString(3, group.ownerId); stmt.setString(4, gson.toJson(group.memberIds))
                stmt.setLong(5, group.createdAt); stmt.setString(6, group.groupNumber)
                stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
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
                        } catch (_: Exception) { emptyList() }

                        val groupNumber = try { rs.getString("group_number") ?: "" } catch (_: Exception) { "" }

                        val group = Group(
                            id = rs.getString("id"), name = rs.getString("name"),
                            ownerId = rs.getString("owner_id"),
                            memberIds = memberIds.toMutableList(),
                            createdAt = rs.getLong("created_at"),
                            groupNumber = groupNumber
                        )
                        groups[group.id] = group
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return groups
    }

    fun deleteGroup(groupId: String) {
        try {
            connection?.prepareStatement("DELETE FROM groups WHERE id = ?")?.use { stmt ->
                stmt.setString(1, groupId); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // =============== Bots ===============

    fun saveBot(bot: Bot) {
        try {
            val sql = "INSERT OR REPLACE INTO bots (id, name, avatar, description, created_at) VALUES (?, ?, ?, ?, ?)"
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, bot.id); stmt.setString(2, bot.name)
                stmt.setString(3, bot.avatar); stmt.setString(4, bot.description)
                stmt.setLong(5, bot.createdAt); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadBots(): Map<String, Bot> {
        val bots = ConcurrentHashMap<String, Bot>()
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery("SELECT * FROM bots")?.use { rs ->
                    while (rs.next()) {
                        val bot = Bot(
                            id = rs.getString("id"), name = rs.getString("name"),
                            avatar = rs.getString("avatar"), description = rs.getString("description"),
                            createdAt = rs.getLong("created_at")
                        )
                        bots[bot.id] = bot
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return bots
    }

    fun deleteBot(botId: String) {
        try {
            connection?.prepareStatement("DELETE FROM bots WHERE id = ?")?.use { stmt ->
                stmt.setString(1, botId); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // =============== Friend Requests ===============

    fun saveFriendRequest(request: FriendRequest) {
        try {
            val sql = """
                INSERT OR REPLACE INTO friend_requests
                (id, from_user_id, from_username, from_ip, from_port,
                 to_ip, to_port, message, status, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, request.id); stmt.setString(2, request.fromUserId)
                stmt.setString(3, request.fromUsername); stmt.setString(4, request.fromIp)
                stmt.setInt(5, request.fromPort); stmt.setString(6, request.toIp)
                stmt.setInt(7, request.toPort); stmt.setString(8, request.message)
                stmt.setString(9, request.status.name); stmt.setLong(10, request.timestamp)
                stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadFriendRequests(): Map<String, FriendRequest> {
        val requests = ConcurrentHashMap<String, FriendRequest>()
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery("SELECT * FROM friend_requests")?.use { rs ->
                    while (rs.next()) {
                        val request = FriendRequest(
                            id = rs.getString("id"), fromUserId = rs.getString("from_user_id"),
                            fromUsername = rs.getString("from_username"), fromIp = rs.getString("from_ip"),
                            fromPort = rs.getInt("from_port"), toIp = rs.getString("to_ip"),
                            toPort = rs.getInt("to_port"), message = rs.getString("message") ?: "",
                            status = try { FriendRequestStatus.valueOf(rs.getString("status")) }
                            catch (_: Exception) { FriendRequestStatus.PENDING_SENT },
                            timestamp = rs.getLong("timestamp")
                        )
                        requests[request.id] = request
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return requests
    }

    fun deleteFriendRequest(requestId: String) {
        try {
            connection?.prepareStatement("DELETE FROM friend_requests WHERE id = ?")?.use { stmt ->
                stmt.setString(1, requestId); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // =============== Group Requests ===============

    fun saveGroupRequest(request: GroupRequest) {
        try {
            val sql = """
                INSERT OR REPLACE INTO group_requests
                (id, group_id, group_number, group_name, requester_id, requester_name,
                 requester_ip, requester_port, target_id, type, status, message, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, request.id); stmt.setString(2, request.groupId)
                stmt.setString(3, request.groupNumber); stmt.setString(4, request.groupName)
                stmt.setString(5, request.requesterId); stmt.setString(6, request.requesterName)
                stmt.setString(7, request.requesterIp); stmt.setInt(8, request.requesterPort)
                stmt.setString(9, request.targetId); stmt.setString(10, request.type.name)
                stmt.setString(11, request.status.name); stmt.setString(12, request.message)
                stmt.setLong(13, request.timestamp); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadGroupRequests(): Map<String, GroupRequest> {
        val requests = ConcurrentHashMap<String, GroupRequest>()
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.executeQuery("SELECT * FROM group_requests")?.use { rs ->
                    while (rs.next()) {
                        val request = GroupRequest(
                            id = rs.getString("id"), groupId = rs.getString("group_id"),
                            groupNumber = rs.getString("group_number") ?: "",
                            groupName = rs.getString("group_name") ?: "",
                            requesterId = rs.getString("requester_id"),
                            requesterName = rs.getString("requester_name"),
                            requesterIp = rs.getString("requester_ip") ?: "",
                            requesterPort = rs.getInt("requester_port"),
                            targetId = rs.getString("target_id") ?: "",
                            type = try { GroupRequestType.valueOf(rs.getString("type")) }
                            catch (_: Exception) { GroupRequestType.INVITE },
                            status = try { GroupRequestStatus.valueOf(rs.getString("status")) }
                            catch (_: Exception) { GroupRequestStatus.PENDING },
                            message = rs.getString("message") ?: "",
                            timestamp = rs.getLong("timestamp")
                        )
                        requests[request.id] = request
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return requests
    }

    fun deleteGroupRequest(requestId: String) {
        try {
            connection?.prepareStatement("DELETE FROM group_requests WHERE id = ?")?.use { stmt ->
                stmt.setString(1, requestId); stmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun close() {
        connection?.close()
        connection = null
    }
}

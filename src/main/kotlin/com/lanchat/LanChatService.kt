package com.lanchat

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.lanchat.db.DatabaseManager
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.swing.SwingUtilities

@Service(Service.Level.APP)
class LanChatService : Disposable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var networkManager: NetworkManager? = null
    private val gson = Gson()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers

    private val _messages = MutableStateFlow<Map<String, MutableList<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, MutableList<Message>>> = _messages

    private val _groups = MutableStateFlow<Map<String, Group>>(emptyMap())
    val groups: StateFlow<Map<String, Group>> = _groups

    private val _bots = MutableStateFlow<Map<String, Bot>>(emptyMap())
    val bots: StateFlow<Map<String, Bot>> = _bots

    private val _friendRequests = MutableStateFlow<Map<String, FriendRequest>>(emptyMap())
    val friendRequests: StateFlow<Map<String, FriendRequest>> = _friendRequests

    private val _groupRequests = MutableStateFlow<Map<String, GroupRequest>>(emptyMap())
    val groupRequests: StateFlow<Map<String, GroupRequest>> = _groupRequests

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts

    private var _currentUser: Peer? = null
    val currentUser: Peer? get() = _currentUser

    private var _localIp: String = "127.0.0.1"
    val localIp: String get() = _localIp

    private var _username: String = System.getProperty("user.name", "Anonymous")
    val username: String get() = _username

    private var _userAvatar: String? = null
    val userAvatar: String? get() = _userAvatar

    private var isInitialized = false

    private val messageListeners = mutableListOf<(Message) -> Unit>()

    fun addMessageListener(listener: (Message) -> Unit) { messageListeners.add(listener) }
    fun removeMessageListener(listener: (Message) -> Unit) { messageListeners.remove(listener) }

    private fun notifyMessageListeners(message: Message) {
        SwingUtilities.invokeLater { messageListeners.forEach { it(message) } }
    }

    companion object {
        fun getInstance(): LanChatService {
            return ApplicationManager.getApplication().getService(LanChatService::class.java)
        }
    }

    init { initialize() }

    private fun initialize() {
        if (isInitialized) return
        isInitialized = true

        _localIp = getLocalIpAddress()

        val dataDir = System.getProperty("user.home") + "/.lanchat"
        DatabaseManager.initialize(dataDir)

        _username = DatabaseManager.getSetting("username", _username)
        _userAvatar = DatabaseManager.getSetting("userAvatar")

        val savedUserId = DatabaseManager.getSetting("currentUserId")
        val userId = if (savedUserId.isNotEmpty()) savedUserId else {
            val newId = java.util.UUID.randomUUID().toString()
            DatabaseManager.saveSetting("currentUserId", newId)
            newId
        }

        _currentUser = Peer(id = userId, username = _username, ipAddress = _localIp, port = 8889)

        _peers.value = DatabaseManager.loadPeers()
        _messages.value = DatabaseManager.loadMessages(userId)
        _groups.value = DatabaseManager.loadGroups()
        _bots.value = DatabaseManager.loadBots()
        _friendRequests.value = DatabaseManager.loadFriendRequests()
        _groupRequests.value = DatabaseManager.loadGroupRequests()
        _unreadCounts.value = DatabaseManager.getUnreadCounts(userId)

        migrateGroupNumbers()

        networkManager = NetworkManager()

        scope.launch {
            networkManager?.let { nm ->
                nm.start(_username, userId)

                val actualPort = nm.getActualTcpPort()
                if (actualPort != 8889) {
                    _currentUser = _currentUser?.copy(port = actualPort)
                }

                launch { nm.messageReceived.collect { message -> handleReceivedMessage(message) } }

                nm.peerDiscovered.collect { peer ->
                    addPeer(peer)
                    autoAcceptFriendRequests(peer)
                }
            }
        }
    }

    private fun migrateGroupNumbers() {
        val m = _groups.value.toMutableMap()
        var changed = false
        m.values.filter { it.groupNumber.isEmpty() }.forEach { group ->
            val updated = Group(
                id = group.id, name = group.name, ownerId = group.ownerId,
                memberIds = group.memberIds, createdAt = group.createdAt,
                avatar = group.avatar, groupNumber = generateUniqueGroupNumber()
            )
            m[group.id] = updated
            DatabaseManager.saveGroup(updated)
            changed = true
        }
        if (changed) _groups.value = m
    }

    private fun generateUniqueGroupNumber(): String {
        val existingNumbers = _groups.value.values.map { it.groupNumber }.toSet()
        var number: String
        do {
            number = String.format("%06d", (Math.random() * 1000000).toInt())
        } while (existingNumbers.contains(number))
        return number
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) { "127.0.0.1" }
    }

    // =============== Message Routing ===============

    private var currentChatId: String? = null

    fun setCurrentChatId(chatId: String?) {
        currentChatId = chatId
        if (chatId != null) {
            markAsRead(chatId)
        }
    }

    private fun handleReceivedMessage(message: Message) {
        if (message.senderId == _currentUser?.id) return
        when (message.type) {
            MessageType.GROUP_SYNC -> handleGroupSync(message)
            MessageType.FRIEND_REQUEST -> handleIncomingFriendRequest(message)
            MessageType.FRIEND_RESPONSE -> handleFriendResponse(message)
            MessageType.GROUP_INVITE -> handleGroupInvite(message)
            MessageType.GROUP_INVITE_RESPONSE -> handleGroupInviteResponse(message)
            MessageType.MESSAGE_READ_ACK -> handleMessageReadAck(message)
            else -> {
                addMessageToHistory(message)
                notifyMessageListeners(message)

                // 增加未读计数（如果当前没有打开对应的聊天窗口）
                val chatId = when {
                    !message.groupId.isNullOrEmpty() -> message.groupId
                    else -> message.senderId
                }
                if (chatId != currentChatId) {
                    incrementUnreadCount(chatId)
                }
            }
        }
    }

    private fun incrementUnreadCount(chatId: String) {
        val current = _unreadCounts.value.toMutableMap()
        current[chatId] = (current[chatId] ?: 0) + 1
        _unreadCounts.value = current
    }

    fun markAsRead(chatId: String) {
        val userId = _currentUser?.id ?: return
        val userName = _username
        val current = _unreadCounts.value.toMutableMap()
        if (current.containsKey(chatId) && current[chatId]!! > 0) {
            current[chatId] = 0
            _unreadCounts.value = current
            DatabaseManager.markMessagesAsRead(chatId, userId, userName)

            // 更新内存中的消息状态
            val msgs = _messages.value.toMutableMap()
            msgs[chatId]?.forEach { msg ->
                if (!msg.isSentByMe(userId)) {
                    val index = msgs[chatId]?.indexOf(msg)
                    if (index != null && index >= 0) {
                        msgs[chatId]?.set(index, msg.copy(isRead = true))
                    }
                }
            }
            _messages.value = msgs
            
            // 如果是群聊，广播已读回执给其他成员
            val group = _groups.value[chatId]
            if (group != null) {
                broadcastReadAck(chatId, userId, userName)
            }
        }
    }

    /**
     * 已读回执数据
     */
    data class ReadAckPayload(
        val groupId: String,
        val messageId: String? = null,  // 如果为空，表示已读所有消息
        val readerId: String,
        val readerName: String
    )

    /**
     * 广播已读回执给群成员
     */
    private fun broadcastReadAck(groupId: String, readerId: String, readerName: String) {
        val payload = ReadAckPayload(
            groupId = groupId,
            readerId = readerId,
            readerName = readerName
        )
        val msg = Message(
            type = MessageType.MESSAGE_READ_ACK,
            senderId = readerId,
            receiverId = groupId,
            content = gson.toJson(payload),
            groupId = groupId,
            senderName = readerName
        )
        scope.launch {
            val targets = getGroupMemberTargets(groupId)
            if (targets.isNotEmpty()) {
                networkManager?.sendToMultiple(msg, targets)
            }
        }
    }

    /**
     * 处理收到的已读回执
     */
    private fun handleMessageReadAck(message: Message) {
        val payload = try {
            gson.fromJson(message.content, ReadAckPayload::class.java)
        } catch (_: Exception) { return }

        val groupId = payload.groupId
        val readerId = payload.readerId
        val readerName = payload.readerName

        // 更新该群聊中我发送的消息的已读状态
        val myId = _currentUser?.id ?: return
        val msgs = _messages.value.toMutableMap()
        val groupMsgs = msgs[groupId] ?: return
        var changed = false

        groupMsgs.forEachIndexed { index, msg ->
            // 只更新我发送的消息，且该用户还没被标记为已读
            if (msg.senderId == myId && !msg.readByUserIds.contains(readerId)) {
                val updated = msg.copy(
                    readByUserIds = msg.readByUserIds + readerId,
                    readByUserNames = msg.readByUserNames + readerName
                )
                groupMsgs[index] = updated
                DatabaseManager.updateMessageReadBy(msg.id, readerId, readerName)
                changed = true
            }
        }

        if (changed) {
            msgs[groupId] = groupMsgs
            _messages.value = msgs
        }
    }

    // =============== Peer Management ===============

    private fun addPeer(peer: Peer) {
        val currentPeers = _peers.value.toMutableMap()
        currentPeers[peer.id] = peer
        _peers.value = currentPeers
        DatabaseManager.savePeer(peer)
    }

    fun addManualPeer(ipAddress: String, port: Int, name: String): Boolean {
        if (_peers.value.values.any { it.ipAddress == ipAddress && it.port == port }) return false
        val peer = Peer(
            id = "manual_${System.currentTimeMillis()}",
            username = name, ipAddress = ipAddress, port = port, isOnline = true
        )
        addPeer(peer)
        return true
    }

    fun isPeerExists(ipAddress: String, port: Int): Boolean {
        return _peers.value.values.any { it.ipAddress == ipAddress && it.port == port }
    }

    fun removePeer(peerId: String) {
        val m = _peers.value.toMutableMap(); m.remove(peerId); _peers.value = m
        DatabaseManager.deletePeer(peerId)
    }

    fun searchPeersByIp(ip: String): List<Peer> =
        _peers.value.values.filter { it.ipAddress.contains(ip) }

    /**
     * 探测指定IP:端口的用户信息
     * 用于在添加好友前确认对方在线并获取基本信息
     */
    suspend fun probePeer(ipAddress: String, port: Int): Peer? {
        return networkManager?.probePeer(ipAddress, port)
    }

    private fun resolvePeerTarget(memberId: String): Pair<String, Int>? {
        val peer = _peers.value[memberId] ?: return null
        return Pair(peer.ipAddress, peer.port)
    }

    private fun getGroupMemberTargets(groupId: String): List<Pair<String, Int>> {
        val group = _groups.value[groupId] ?: return emptyList()
        val myId = _currentUser?.id
        return group.memberIds.filter { it != myId }.mapNotNull { resolvePeerTarget(it) }
    }

    // =============== Sending Messages ===============

    fun sendTextMessage(receiverId: String, content: String) {
        val msg = Message(
            type = MessageType.TEXT, senderId = _currentUser?.id ?: return,
            receiverId = receiverId, content = content, senderName = _username
        )
        sendPeerMessage(msg, receiverId)
    }

    fun sendImageMessage(receiverId: String, imagePath: String) {
        val msg = Message(
            type = MessageType.IMAGE, senderId = _currentUser?.id ?: return,
            receiverId = receiverId, content = imagePath
        )
        sendPeerMessage(msg, receiverId)
    }

    fun sendFileMessage(receiverId: String, filePath: String, fileName: String) {
        val msg = Message(
            type = MessageType.FILE, senderId = _currentUser?.id ?: return,
            receiverId = receiverId, content = filePath, fileName = fileName
        )
        sendPeerMessage(msg, receiverId)
    }

    private fun sendPeerMessage(message: Message, peerId: String) {
        scope.launch {
            val target = resolvePeerTarget(peerId)
            addMessageToHistory(message)

            if (target != null) {
                val isLoopback = target.first == _localIp || target.first == "127.0.0.1"
                if (isLoopback) {
                    delay(300)
                    val peerName = _peers.value[peerId]?.username ?: "对方"
                    val echo = Message(
                        type = MessageType.TEXT, senderId = peerId,
                        receiverId = _currentUser?.id ?: "",
                        content = "[回声] ${message.content}", senderName = peerName
                    )
                    addMessageToHistory(echo)
                    notifyMessageListeners(echo)
                } else {
                    networkManager?.sendMessage(message, target.first, target.second)
                }
            }
        }
    }

    fun sendGroupMessage(
        groupId: String, content: String,
        mentionedUserIds: List<String> = emptyList(), mentionAll: Boolean = false
    ) {
        val senderId = _currentUser?.id ?: return
        val type = when {
            mentionAll -> MessageType.MENTION_ALL
            mentionedUserIds.isNotEmpty() -> MessageType.MENTION_MEMBER
            else -> MessageType.GROUP_CHAT
        }
        val message = Message(
            type = type, senderId = senderId, receiverId = groupId,
            content = content, mentionedUserIds = mentionedUserIds,
            mentionAll = mentionAll, groupId = groupId, senderName = _username
        )
        scope.launch {
            val targets = getGroupMemberTargets(groupId)
            if (targets.isNotEmpty()) networkManager?.sendToMultiple(message, targets)
            addMessageToHistory(message)
        }
    }

    fun sendMentionMessage(
        receiverId: String, content: String,
        mentionedUserIds: List<String> = emptyList(),
        mentionAll: Boolean = false, groupId: String? = null
    ) {
        val msg = Message(
            type = if (mentionAll) MessageType.MENTION_ALL else MessageType.MENTION_MEMBER,
            senderId = _currentUser?.id ?: return, receiverId = receiverId,
            content = content, mentionedUserIds = mentionedUserIds,
            mentionAll = mentionAll, groupId = groupId, senderName = _username
        )
        if (groupId != null) {
            scope.launch {
                val targets = getGroupMemberTargets(groupId)
                if (targets.isNotEmpty()) networkManager?.sendToMultiple(msg, targets)
                addMessageToHistory(msg)
            }
        } else {
            sendPeerMessage(msg, receiverId)
        }
    }

    // =============== Message History ===============

    private fun addMessageToHistory(message: Message) {
        val chatId = when {
            !message.groupId.isNullOrEmpty() -> message.groupId
            message.senderId == _currentUser?.id -> message.receiverId
            else -> message.senderId
        }
        val currentMessages = _messages.value.toMutableMap()
        val chatMessages = currentMessages.getOrPut(chatId) { mutableListOf() }
        chatMessages.add(message)
        _messages.value = currentMessages
        DatabaseManager.saveMessage(message)
    }

    fun getChatHistory(chatId: String): List<Message> = _messages.value[chatId] ?: emptyList()

    /**
     * 清空指定聊天的聊天记录
     */
    fun clearChatHistory(chatId: String) {
        val currentMessages = _messages.value.toMutableMap()
        currentMessages.remove(chatId)
        _messages.value = currentMessages
        DatabaseManager.clearMessages(chatId)
        
        // 同时清除未读计数
        val counts = _unreadCounts.value.toMutableMap()
        counts.remove(chatId)
        _unreadCounts.value = counts
    }

    /**
     * 清空所有聊天记录
     */
    fun clearAllChatHistory() {
        _messages.value = emptyMap()
        _unreadCounts.value = emptyMap()
        DatabaseManager.clearAllMessages()
    }

    /**
     * 获取存储空间信息
     */
    fun getStorageInfo(): StorageInfo {
        return StorageInfo(
            databaseSize = DatabaseManager.getDatabaseSize(),
            formattedSize = DatabaseManager.getFormattedDatabaseSize(),
            messageStats = DatabaseManager.getMessageStats(),
            totalChats = _messages.value.size
        )
    }

    data class StorageInfo(
        val databaseSize: Long,
        val formattedSize: String,
        val messageStats: Map<String, Int>,
        val totalChats: Int
    ) {
        val totalMessages: Int get() = messageStats["total"] ?: 0
        val imageCount: Int get() = messageStats["images"] ?: 0
        val fileCount: Int get() = messageStats["files"] ?: 0
    }

    // =============== Group Management + Sync ===============

    data class GroupSyncPayload(
        val action: String,
        val groupId: String,
        val groupName: String? = null,
        val groupNumber: String? = null,
        val ownerId: String? = null,
        val ownerName: String? = null,
        val memberIds: List<String>? = null,
        val createdAt: Long? = null,
        val targetMemberId: String? = null
    )

    private fun broadcastGroupSync(payload: GroupSyncPayload, targets: List<Pair<String, Int>>) {
        if (targets.isEmpty()) return
        val msg = Message(
            type = MessageType.GROUP_SYNC,
            senderId = _currentUser?.id ?: return,
            receiverId = payload.groupId,
            content = gson.toJson(payload),
            groupId = payload.groupId,
            senderName = _username
        )
        scope.launch { networkManager?.sendToMultiple(msg, targets) }
    }

    fun createGroup(groupName: String, memberIds: List<String>): String {
        val ownerId = _currentUser?.id ?: return ""
        val allMembers = (memberIds + ownerId).distinct().toMutableList()
        val groupNumber = generateUniqueGroupNumber()
        val group = Group(
            name = groupName, ownerId = ownerId, memberIds = allMembers,
            groupNumber = groupNumber
        )

        val m = _groups.value.toMutableMap(); m[group.id] = group; _groups.value = m
        DatabaseManager.saveGroup(group)

        val targets = allMembers.filter { it != ownerId }.mapNotNull { resolvePeerTarget(it) }
        broadcastGroupSync(
            GroupSyncPayload(
                action = "CREATE", groupId = group.id, groupName = groupName,
                groupNumber = groupNumber, ownerId = ownerId, ownerName = _username,
                memberIds = allMembers, createdAt = group.createdAt
            ), targets
        )
        return group.id
    }

    fun deleteGroup(groupId: String) {
        val userId = _currentUser?.id ?: return
        val group = _groups.value[groupId] ?: return
        if (!group.isOwner(userId)) return

        val targets = getGroupMemberTargets(groupId)
        broadcastGroupSync(GroupSyncPayload(action = "DELETE", groupId = groupId), targets)

        val m = _groups.value.toMutableMap(); m.remove(groupId); _groups.value = m
        DatabaseManager.deleteGroup(groupId)
    }

    fun updateGroupName(groupId: String, newName: String) {
        val userId = _currentUser?.id ?: return
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return
        if (!group.isOwner(userId)) return

        val updated = Group(
            id = group.id, name = newName, ownerId = group.ownerId,
            memberIds = group.memberIds, createdAt = group.createdAt,
            avatar = group.avatar, groupNumber = group.groupNumber
        )
        m[groupId] = updated; _groups.value = m
        DatabaseManager.saveGroup(updated)

        val targets = getGroupMemberTargets(groupId)
        broadcastGroupSync(
            GroupSyncPayload(action = "UPDATE", groupId = groupId, groupName = newName), targets
        )
    }

    fun addGroupMember(groupId: String, newMemberId: String): Boolean {
        val userId = _currentUser?.id ?: return false
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return false
        if (!group.addMember(userId, newMemberId)) return false

        m[groupId] = group; _groups.value = m
        DatabaseManager.saveGroup(group)

        val newMemberTarget = resolvePeerTarget(newMemberId)
        if (newMemberTarget != null) {
            broadcastGroupSync(
                GroupSyncPayload(
                    action = "CREATE", groupId = group.id, groupName = group.name,
                    groupNumber = group.groupNumber, ownerId = group.ownerId,
                    ownerName = _username, memberIds = group.memberIds, createdAt = group.createdAt
                ), listOf(newMemberTarget)
            )
        }

        val existingTargets = group.memberIds
            .filter { it != userId && it != newMemberId }
            .mapNotNull { resolvePeerTarget(it) }
        broadcastGroupSync(
            GroupSyncPayload(
                action = "ADD_MEMBER", groupId = groupId,
                targetMemberId = newMemberId, memberIds = group.memberIds
            ), existingTargets
        )
        return true
    }

    fun removeGroupMember(groupId: String, memberId: String): Boolean {
        val userId = _currentUser?.id ?: return false
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return false
        if (!group.removeMember(userId, memberId)) return false

        m[groupId] = group; _groups.value = m
        DatabaseManager.saveGroup(group)

        val removedTarget = resolvePeerTarget(memberId)
        if (removedTarget != null) {
            broadcastGroupSync(
                GroupSyncPayload(action = "DELETE", groupId = groupId), listOf(removedTarget)
            )
        }

        val remainingTargets = getGroupMemberTargets(groupId)
        broadcastGroupSync(
            GroupSyncPayload(
                action = "REMOVE_MEMBER", groupId = groupId,
                targetMemberId = memberId, memberIds = group.memberIds
            ), remainingTargets
        )
        return true
    }

    fun isGroupOwner(groupId: String): Boolean {
        val userId = _currentUser?.id ?: return false
        return _groups.value[groupId]?.isOwner(userId) ?: false
    }

    fun getGroup(groupId: String): Group? = _groups.value[groupId]

    fun getGroupMembers(groupId: String): List<Peer> {
        val group = _groups.value[groupId] ?: return emptyList()
        return group.memberIds.mapNotNull { memberId ->
            _peers.value[memberId] ?: if (memberId == _currentUser?.id) _currentUser else null
        }
    }

    fun searchGroupByNumber(number: String): List<Group> {
        return _groups.value.values.filter { it.groupNumber.contains(number) }
    }

    private fun handleGroupSync(message: Message) {
        val payload = try {
            gson.fromJson(message.content, GroupSyncPayload::class.java)
        } catch (_: Exception) { return }

        val m = _groups.value.toMutableMap()

        when (payload.action) {
            "CREATE" -> {
                if (m.containsKey(payload.groupId)) return
                val group = Group(
                    id = payload.groupId,
                    name = payload.groupName ?: "未命名群聊",
                    ownerId = payload.ownerId ?: message.senderId,
                    memberIds = payload.memberIds?.toMutableList() ?: mutableListOf(),
                    createdAt = payload.createdAt ?: System.currentTimeMillis(),
                    groupNumber = payload.groupNumber ?: ""
                )
                m[group.id] = group; _groups.value = m
                DatabaseManager.saveGroup(group)

                if (payload.ownerId != null && payload.ownerName != null) {
                    val existing = _peers.value[payload.ownerId]
                    if (existing == null) {
                        addPeer(Peer(
                            id = payload.ownerId, username = payload.ownerName,
                            ipAddress = "unknown", port = 8889, isOnline = true
                        ))
                    }
                }
            }
            "UPDATE" -> {
                val group = m[payload.groupId] ?: return
                val updated = Group(
                    id = group.id, name = payload.groupName ?: group.name,
                    ownerId = group.ownerId, memberIds = group.memberIds,
                    createdAt = group.createdAt, avatar = group.avatar,
                    groupNumber = group.groupNumber
                )
                m[payload.groupId] = updated; _groups.value = m
                DatabaseManager.saveGroup(updated)
            }
            "DELETE" -> {
                m.remove(payload.groupId); _groups.value = m
                DatabaseManager.deleteGroup(payload.groupId)
            }
            "ADD_MEMBER" -> {
                val group = m[payload.groupId] ?: return
                payload.targetMemberId?.let { newId ->
                    if (!group.memberIds.contains(newId)) {
                        group.memberIds.add(newId)
                        m[payload.groupId] = group; _groups.value = m
                        DatabaseManager.saveGroup(group)
                    }
                }
            }
            "REMOVE_MEMBER" -> {
                val group = m[payload.groupId] ?: return
                payload.targetMemberId?.let { removedId ->
                    group.memberIds.remove(removedId)
                    m[payload.groupId] = group; _groups.value = m
                    DatabaseManager.saveGroup(group)
                }
            }
        }
    }

    // =============== Group Invite / Join Request ===============

    data class GroupInvitePayload(
        val action: String,
        val groupId: String,
        val groupNumber: String = "",
        val groupName: String = "",
        val requestId: String = "",
        val requesterId: String = "",
        val requesterName: String = "",
        val message: String = ""
    )

    fun inviteToGroup(groupId: String, peerId: String) {
        val cu = _currentUser ?: return
        val group = _groups.value[groupId] ?: return
        if (!group.isOwner(cu.id)) return
        if (group.memberIds.contains(peerId)) return

        val peer = _peers.value[peerId] ?: return
        val request = GroupRequest(
            groupId = groupId, groupNumber = group.groupNumber,
            groupName = group.name, requesterId = cu.id,
            requesterName = _username, requesterIp = _localIp,
            requesterPort = networkManager?.getActualTcpPort() ?: 8889,
            targetId = peerId, type = GroupRequestType.INVITE,
            message = "${_username} 邀请你加入群聊「${group.name}」"
        )
        val m = _groupRequests.value.toMutableMap(); m[request.id] = request; _groupRequests.value = m
        DatabaseManager.saveGroupRequest(request)

        val payload = GroupInvitePayload(
            action = "INVITE", groupId = groupId, groupNumber = group.groupNumber,
            groupName = group.name, requestId = request.id,
            requesterId = cu.id, requesterName = _username,
            message = request.message
        )
        val msg = Message(
            type = MessageType.GROUP_INVITE, senderId = cu.id,
            receiverId = peerId, content = gson.toJson(payload), senderName = _username
        )
        scope.launch { networkManager?.sendMessage(msg, peer.ipAddress, peer.port) }
    }

    fun requestJoinGroup(groupId: String, greeting: String = "") {
        val cu = _currentUser ?: return
        val group = _groups.value[groupId] ?: return
        if (group.memberIds.contains(cu.id)) return

        val request = GroupRequest(
            groupId = groupId, groupNumber = group.groupNumber,
            groupName = group.name, requesterId = cu.id,
            requesterName = _username, requesterIp = _localIp,
            requesterPort = networkManager?.getActualTcpPort() ?: 8889,
            targetId = group.ownerId, type = GroupRequestType.JOIN_REQUEST,
            message = greeting.ifEmpty { "我是${_username}，想加入群聊「${group.name}」" }
        )
        val m = _groupRequests.value.toMutableMap(); m[request.id] = request; _groupRequests.value = m
        DatabaseManager.saveGroupRequest(request)

        val ownerTarget = resolvePeerTarget(group.ownerId) ?: return
        val payload = GroupInvitePayload(
            action = "JOIN_REQUEST", groupId = groupId, groupNumber = group.groupNumber,
            groupName = group.name, requestId = request.id,
            requesterId = cu.id, requesterName = _username,
            message = request.message
        )
        val msg = Message(
            type = MessageType.GROUP_INVITE, senderId = cu.id,
            receiverId = group.ownerId, content = gson.toJson(payload), senderName = _username
        )
        scope.launch { networkManager?.sendMessage(msg, ownerTarget.first, ownerTarget.second) }
    }

    fun acceptGroupInvite(requestId: String) {
        val m = _groupRequests.value.toMutableMap()
        val request = m[requestId] ?: return
        val cu = _currentUser ?: return

        val updated = request.copy(status = GroupRequestStatus.ACCEPTED)
        m[requestId] = updated; _groupRequests.value = m
        DatabaseManager.saveGroupRequest(updated)

        when (request.type) {
            GroupRequestType.INVITE -> {
                // Invitee accepts → notify the inviter (group owner) to add us
                val payload = GroupInvitePayload(
                    action = "ACCEPT_INVITE", groupId = request.groupId,
                    requestId = requestId, requesterId = cu.id,
                    requesterName = _username
                )
                val msg = Message(
                    type = MessageType.GROUP_INVITE_RESPONSE, senderId = cu.id,
                    receiverId = request.requesterId, content = gson.toJson(payload),
                    senderName = _username
                )
                val target = resolvePeerTarget(request.requesterId)
                if (target != null) {
                    scope.launch { networkManager?.sendMessage(msg, target.first, target.second) }
                }
            }
            GroupRequestType.JOIN_REQUEST -> {
                // Owner approves join request → add member and notify
                addGroupMember(request.groupId, request.requesterId)
                val payload = GroupInvitePayload(
                    action = "ACCEPT_JOIN", groupId = request.groupId,
                    groupNumber = request.groupNumber, groupName = request.groupName,
                    requestId = requestId, requesterId = cu.id,
                    requesterName = _username
                )
                val msg = Message(
                    type = MessageType.GROUP_INVITE_RESPONSE, senderId = cu.id,
                    receiverId = request.requesterId, content = gson.toJson(payload),
                    senderName = _username
                )
                val target = Pair(request.requesterIp, request.requesterPort)
                scope.launch { networkManager?.sendMessage(msg, target.first, target.second) }
            }
        }
    }

    fun rejectGroupInvite(requestId: String) {
        val m = _groupRequests.value.toMutableMap()
        val request = m[requestId] ?: return
        val cu = _currentUser ?: return

        val updated = request.copy(status = GroupRequestStatus.REJECTED)
        m[requestId] = updated; _groupRequests.value = m
        DatabaseManager.saveGroupRequest(updated)

        val action = if (request.type == GroupRequestType.INVITE) "REJECT_INVITE" else "REJECT_JOIN"
        val targetId = if (request.type == GroupRequestType.INVITE) request.requesterId else request.requesterId
        val payload = GroupInvitePayload(
            action = action, groupId = request.groupId,
            requestId = requestId, requesterId = cu.id,
            requesterName = _username
        )
        val msg = Message(
            type = MessageType.GROUP_INVITE_RESPONSE, senderId = cu.id,
            receiverId = targetId, content = gson.toJson(payload), senderName = _username
        )

        val target = if (request.type == GroupRequestType.INVITE)
            resolvePeerTarget(request.requesterId)
        else Pair(request.requesterIp, request.requesterPort)

        if (target != null) {
            scope.launch { networkManager?.sendMessage(msg, target.first, target.second) }
        }
    }

    private fun handleGroupInvite(message: Message) {
        val payload = try {
            gson.fromJson(message.content, GroupInvitePayload::class.java)
        } catch (_: Exception) { return }

        when (payload.action) {
            "INVITE" -> {
                val request = GroupRequest(
                    groupId = payload.groupId, groupNumber = payload.groupNumber,
                    groupName = payload.groupName, requesterId = payload.requesterId,
                    requesterName = payload.requesterName,
                    requesterIp = _peers.value[payload.requesterId]?.ipAddress ?: "",
                    requesterPort = _peers.value[payload.requesterId]?.port ?: 8889,
                    targetId = _currentUser?.id ?: "", type = GroupRequestType.INVITE,
                    message = payload.message
                )
                val m = _groupRequests.value.toMutableMap()
                m[request.id] = request; _groupRequests.value = m
                DatabaseManager.saveGroupRequest(request)
            }
            "JOIN_REQUEST" -> {
                val request = GroupRequest(
                    groupId = payload.groupId, groupNumber = payload.groupNumber,
                    groupName = payload.groupName, requesterId = payload.requesterId,
                    requesterName = payload.requesterName,
                    requesterIp = _peers.value[payload.requesterId]?.ipAddress ?: "",
                    requesterPort = _peers.value[payload.requesterId]?.port ?: 8889,
                    targetId = _currentUser?.id ?: "", type = GroupRequestType.JOIN_REQUEST,
                    message = payload.message
                )
                val m = _groupRequests.value.toMutableMap()
                m[request.id] = request; _groupRequests.value = m
                DatabaseManager.saveGroupRequest(request)
            }
        }
    }

    private fun handleGroupInviteResponse(message: Message) {
        val payload = try {
            gson.fromJson(message.content, GroupInvitePayload::class.java)
        } catch (_: Exception) { return }

        when (payload.action) {
            "ACCEPT_INVITE" -> {
                // The invitee accepted; as the owner, add them to the group
                addGroupMember(payload.groupId, payload.requesterId)
                updateGroupRequestStatus(payload.requestId, GroupRequestStatus.ACCEPTED)
            }
            "ACCEPT_JOIN" -> {
                updateGroupRequestStatus(payload.requestId, GroupRequestStatus.ACCEPTED)
            }
            "REJECT_INVITE", "REJECT_JOIN" -> {
                updateGroupRequestStatus(payload.requestId, GroupRequestStatus.REJECTED)
            }
        }
    }

    private fun updateGroupRequestStatus(requestId: String, status: GroupRequestStatus) {
        if (requestId.isEmpty()) return
        val m = _groupRequests.value.toMutableMap()
        val request = m[requestId] ?: return
        val updated = request.copy(status = status)
        m[requestId] = updated; _groupRequests.value = m
        DatabaseManager.saveGroupRequest(updated)
    }

    fun getPendingGroupInvites(): List<GroupRequest> =
        _groupRequests.value.values.filter {
            it.status == GroupRequestStatus.PENDING && it.targetId == _currentUser?.id
        }.sortedByDescending { it.timestamp }

    fun getAllGroupRequests(): List<GroupRequest> =
        _groupRequests.value.values.sortedByDescending { it.timestamp }

    fun deleteGroupRequest(requestId: String) {
        val m = _groupRequests.value.toMutableMap()
        m.remove(requestId); _groupRequests.value = m
        DatabaseManager.deleteGroupRequest(requestId)
    }

    // =============== User Settings ===============

    fun updateUsername(newUsername: String) {
        _username = newUsername
        networkManager?.updateUsername(newUsername)
        _currentUser = _currentUser?.copy(username = newUsername)
        DatabaseManager.saveSetting("username", newUsername)
    }

    fun refreshPeers() { networkManager?.sendDiscovery() }

    // =============== Bot Management ===============

    fun createBot(name: String, description: String = ""): Bot {
        val bot = Bot(name = name, description = description.ifEmpty { "我是$name，可以陪你聊天测试" })
        val m = _bots.value.toMutableMap(); m[bot.id] = bot; _bots.value = m
        DatabaseManager.saveBot(bot)
        addPeer(bot.toPeer())
        return bot
    }

    fun deleteBot(botId: String) {
        val m = _bots.value.toMutableMap(); m.remove(botId); _bots.value = m
        DatabaseManager.deleteBot(botId)
        removePeer(botId)
    }

    fun getBot(botId: String): Bot? = _bots.value[botId]

    fun sendBotMessage(botId: String, content: String) {
        val bot = _bots.value[botId] ?: return
        val senderId = _currentUser?.id ?: return

        val userMessage = Message(
            type = MessageType.TEXT, senderId = senderId,
            receiverId = botId, content = content, senderName = _username
        )
        addMessageToHistory(userMessage)

        scope.launch {
            delay(500 + (Math.random() * 1000).toLong())
            val botMessage = Message(
                type = MessageType.TEXT, senderId = botId,
                receiverId = senderId, content = bot.autoReply(content),
                senderName = bot.name
            )
            addMessageToHistory(botMessage)
            notifyMessageListeners(botMessage)
        }
    }

    // =============== Friend Requests ===============

    data class FriendRequestPayload(
        val action: String,
        val fromUserId: String,
        val fromUsername: String,
        val fromIp: String,
        val fromPort: Int,
        val message: String = ""
    )

    fun sendFriendRequest(targetIp: String, targetPort: Int = 8889, greeting: String = "") {
        val cu = _currentUser ?: return
        if (isPeerExists(targetIp, targetPort)) return

        val request = FriendRequest(
            fromUserId = cu.id, fromUsername = _username,
            fromIp = _localIp, fromPort = networkManager?.getActualTcpPort() ?: 8889,
            toIp = targetIp, toPort = targetPort,
            message = greeting.ifEmpty { "你好，我是${_username}，请求添加你为好友" },
            status = FriendRequestStatus.PENDING_SENT
        )
        val m = _friendRequests.value.toMutableMap(); m[request.id] = request; _friendRequests.value = m
        DatabaseManager.saveFriendRequest(request)

        val payload = FriendRequestPayload(
            action = "REQUEST", fromUserId = cu.id, fromUsername = _username,
            fromIp = _localIp, fromPort = networkManager?.getActualTcpPort() ?: 8889,
            message = request.message
        )
        val msg = Message(
            type = MessageType.FRIEND_REQUEST, senderId = cu.id,
            receiverId = "", content = gson.toJson(payload), senderName = _username
        )
        scope.launch { networkManager?.sendMessage(msg, targetIp, targetPort) }
    }

    fun acceptFriendRequest(requestId: String) {
        val m = _friendRequests.value.toMutableMap()
        val request = m[requestId] ?: return
        val updated = request.copy(status = FriendRequestStatus.ACCEPTED)
        m[requestId] = updated; _friendRequests.value = m
        DatabaseManager.saveFriendRequest(updated)

        addManualPeer(request.fromIp, request.fromPort, request.fromUsername)

        val cu = _currentUser ?: return
        val payload = FriendRequestPayload(
            action = "ACCEPT", fromUserId = cu.id, fromUsername = _username,
            fromIp = _localIp, fromPort = networkManager?.getActualTcpPort() ?: 8889
        )
        val msg = Message(
            type = MessageType.FRIEND_RESPONSE, senderId = cu.id,
            receiverId = request.fromUserId, content = gson.toJson(payload), senderName = _username
        )
        scope.launch { networkManager?.sendMessage(msg, request.fromIp, request.fromPort) }
    }

    fun rejectFriendRequest(requestId: String) {
        val m = _friendRequests.value.toMutableMap()
        val request = m[requestId] ?: return
        val updated = request.copy(status = FriendRequestStatus.REJECTED)
        m[requestId] = updated; _friendRequests.value = m
        DatabaseManager.saveFriendRequest(updated)

        val cu = _currentUser ?: return
        val payload = FriendRequestPayload(
            action = "REJECT", fromUserId = cu.id, fromUsername = _username,
            fromIp = _localIp, fromPort = networkManager?.getActualTcpPort() ?: 8889
        )
        val msg = Message(
            type = MessageType.FRIEND_RESPONSE, senderId = cu.id,
            receiverId = request.fromUserId, content = gson.toJson(payload), senderName = _username
        )
        scope.launch { networkManager?.sendMessage(msg, request.fromIp, request.fromPort) }
    }

    private fun handleIncomingFriendRequest(message: Message) {
        val payload = try {
            gson.fromJson(message.content, FriendRequestPayload::class.java)
        } catch (_: Exception) { return }

        val request = FriendRequest(
            fromUserId = payload.fromUserId, fromUsername = payload.fromUsername,
            fromIp = payload.fromIp, fromPort = payload.fromPort,
            toIp = _localIp, toPort = networkManager?.getActualTcpPort() ?: 8889,
            message = payload.message, status = FriendRequestStatus.PENDING_RECEIVED
        )
        val m = _friendRequests.value.toMutableMap(); m[request.id] = request; _friendRequests.value = m
        DatabaseManager.saveFriendRequest(request)
    }

    private fun handleFriendResponse(message: Message) {
        val payload = try {
            gson.fromJson(message.content, FriendRequestPayload::class.java)
        } catch (_: Exception) { return }

        when (payload.action) {
            "ACCEPT" -> {
                val m = _friendRequests.value.toMutableMap()
                val sentRequest = m.values.find {
                    it.status == FriendRequestStatus.PENDING_SENT && it.toIp == payload.fromIp
                }
                if (sentRequest != null) {
                    val updated = sentRequest.copy(status = FriendRequestStatus.ACCEPTED)
                    m[sentRequest.id] = updated; _friendRequests.value = m
                    DatabaseManager.saveFriendRequest(updated)
                }
                addManualPeer(payload.fromIp, payload.fromPort, payload.fromUsername)
            }
            "REJECT" -> {
                val m = _friendRequests.value.toMutableMap()
                val sentRequest = m.values.find {
                    it.status == FriendRequestStatus.PENDING_SENT && it.toIp == payload.fromIp
                }
                if (sentRequest != null) {
                    val updated = sentRequest.copy(status = FriendRequestStatus.REJECTED)
                    m[sentRequest.id] = updated; _friendRequests.value = m
                    DatabaseManager.saveFriendRequest(updated)
                }
            }
        }
    }

    fun simulateIncomingFriendRequest(fromIp: String, fromUsername: String, greeting: String = "") {
        val request = FriendRequest(
            fromUserId = "remote_${System.currentTimeMillis()}", fromUsername = fromUsername,
            fromIp = fromIp, fromPort = 8889, toIp = _localIp, toPort = 8889,
            message = greeting.ifEmpty { "你好，我是${fromUsername}，请求添加你为好友" },
            status = FriendRequestStatus.PENDING_RECEIVED
        )
        val m = _friendRequests.value.toMutableMap(); m[request.id] = request; _friendRequests.value = m
        DatabaseManager.saveFriendRequest(request)
    }

    fun getPendingReceivedRequests(): List<FriendRequest> =
        _friendRequests.value.values.filter { it.status == FriendRequestStatus.PENDING_RECEIVED }
            .sortedByDescending { it.timestamp }

    fun getPendingSentRequests(): List<FriendRequest> =
        _friendRequests.value.values.filter { it.status == FriendRequestStatus.PENDING_SENT }
            .sortedByDescending { it.timestamp }

    fun getAllFriendRequests(): List<FriendRequest> =
        _friendRequests.value.values.sortedByDescending { it.timestamp }

    fun deleteFriendRequest(requestId: String) {
        val m = _friendRequests.value.toMutableMap()
        m.remove(requestId); _friendRequests.value = m
        DatabaseManager.deleteFriendRequest(requestId)
    }

    private fun autoAcceptFriendRequests(peer: Peer) {
        val m = _friendRequests.value.toMutableMap()
        var changed = false
        m.values.filter { it.status == FriendRequestStatus.PENDING_SENT && it.toIp == peer.ipAddress }
            .forEach { req ->
                m[req.id] = req.copy(status = FriendRequestStatus.ACCEPTED)
                changed = true
            }
        if (changed) {
            _friendRequests.value = m
            m.values.filter { it.status == FriendRequestStatus.ACCEPTED }
                .forEach { DatabaseManager.saveFriendRequest(it) }
        }
    }

    // =============== Avatar ===============

    fun updateUserAvatar(avatarPath: String) {
        _userAvatar = avatarPath
        _currentUser = _currentUser?.copy(avatar = avatarPath)
        DatabaseManager.saveSetting("userAvatar", avatarPath)
    }

    override fun dispose() {
        scope.cancel()
        networkManager?.stop()
        networkManager = null
        DatabaseManager.close()
    }
}

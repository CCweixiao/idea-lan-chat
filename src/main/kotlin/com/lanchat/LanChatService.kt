package com.lanchat

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.lanchat.db.DatabaseManager
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.*
import com.lanchat.ui.settings.LanChatSettings
import com.lanchat.util.CryptoManager
import com.lanchat.util.UserIdGenerator
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
    private val inviteAckReceived = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts

    private val _blockedPeerIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedPeerIds: StateFlow<Set<String>> = _blockedPeerIds

    private val _pinnedIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedIds: StateFlow<Set<String>> = _pinnedIds

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
        const val FILE_TRANSFER_ASSISTANT_ID = "__file_transfer_assistant__"
        val FILE_TRANSFER_ASSISTANT = Peer(
            id = FILE_TRANSFER_ASSISTANT_ID,
            username = "文件传输助手",
            ipAddress = "localhost",
            port = 0,
            isOnline = true
        )

        fun getInstance(): LanChatService {
            return ApplicationManager.getApplication().getService(LanChatService::class.java)
        }
    }

    init {
        // 延迟初始化，避免在 CONFIGURATION_STORE_INITIALIZED 阶段调用需要 COMPONENTS_LOADED 的 API
        ApplicationManager.getApplication().invokeLater {
            initialize()
        }
    }

    private fun initialize() {
        if (isInitialized) return
        isInitialized = true

        _localIp = getLocalIpAddress()

        val dataDir = System.getProperty("user.home") + "/.lanchat"
        DatabaseManager.initialize(dataDir)

        _username = DatabaseManager.getSetting("username", _username)
        _userAvatar = DatabaseManager.getSetting("userAvatar")

        // 使用 MAC 地址生成唯一用户 ID
        val savedUserId = DatabaseManager.getSetting("currentUserId")
        val userId = if (savedUserId.isNotEmpty() && UserIdGenerator.isValidUserId(savedUserId)) {
            savedUserId
        } else {
            // 生成基于 MAC 地址的唯一 ID
            val newId = UserIdGenerator.generateUserId()
            DatabaseManager.saveSetting("currentUserId", newId)
            newId
        }

        val settings = LanChatSettings()
        _currentUser = Peer(id = userId, username = _username, ipAddress = _localIp, port = settings.getTcpPort())

        val loadedPeers = DatabaseManager.loadPeers().mapValues { (_, peer) ->
            peer.copy(isOnline = false)
        }.toMutableMap()
        loadedPeers[FILE_TRANSFER_ASSISTANT_ID] = FILE_TRANSFER_ASSISTANT
        _peers.value = loadedPeers

        val pinnedRaw = DatabaseManager.getSetting("pinned_ids", "")
        _pinnedIds.value = if (pinnedRaw.isNotEmpty()) pinnedRaw.split(",").toSet() else emptySet()
        _messages.value = DatabaseManager.loadMessages(userId)
        _groups.value = DatabaseManager.loadGroups()
        _bots.value = DatabaseManager.loadBots()
        _friendRequests.value = DatabaseManager.loadFriendRequests()
        _groupRequests.value = DatabaseManager.loadGroupRequests()
        _unreadCounts.value = DatabaseManager.getUnreadCounts(userId)
        _blockedPeerIds.value = DatabaseManager.loadBlockedPeers()

        migrateGroupNumbers()

        val encryptionKey = DatabaseManager.getSetting("encryptionKey", "")
        CryptoManager.initialize(encryptionKey.ifEmpty { null })
        val encryptionEnabled = DatabaseManager.getSetting("encryptionEnabled", "true")
        CryptoManager.isEnabled = encryptionEnabled != "false"

        networkManager = NetworkManager(
            udpPort = settings.getUdpPort(),
            tcpPort = settings.getTcpPort()
        )

        scope.launch {
            networkManager?.let { nm ->
                nm.start(_username, userId)
                nm.updateAvatarHash(_userAvatar)

                val actualPort = nm.getActualTcpPort()
                if (actualPort != settings.getTcpPort()) {
                    _currentUser = _currentUser?.copy(port = actualPort)
                }

                launch { nm.messageReceived.collect { message -> handleReceivedMessage(message) } }

                nm.peerDiscovered.collect { peer ->
                    // 检查是否在黑名单中，黑名单中的用户不自动添加
                    if (!_blockedPeerIds.value.contains(peer.id)) {
                        // 检查是否有相同 IP+port 的旧记录（可能是手动添加的临时ID）
                        val existingPeer = _peers.value.values.find {
                            it.ipAddress == peer.ipAddress && it.port == peer.port
                        }
                        if (existingPeer != null && existingPeer.id != peer.id) {
                            // 找到相同 IP+port 但 ID 不同的记录，删除旧记录使用真实 userId
                            val m = _peers.value.toMutableMap()
                            m.remove(existingPeer.id)
                            _peers.value = m
                            DatabaseManager.deletePeer(existingPeer.id)
                        }
                        addPeer(peer)
                        autoAcceptFriendRequests(peer)
                    }
                }
            }
        }

        scope.launch {
            while (true) {
                delay(20_000)
                checkPeerOnlineStatus()
            }
        }
    }

    /**
     * 主动检测好友在线状态：
     * 1. 对所有好友发起 TCP 探测（并行，2秒超时）
     * 2. 探测成功 → 标记在线，更新 lastSeen
     * 3. 探测失败且超过阈值 → 标记离线
     */
    private fun checkPeerOnlineStatus() {
        val currentPeers = _peers.value
        if (currentPeers.isEmpty()) return

        scope.launch {
            val probeStartTime = System.currentTimeMillis()
            val results = currentPeers.values
                .filter { it.id != FILE_TRANSFER_ASSISTANT_ID }
                .map { peer ->
                    async(Dispatchers.IO) {
                        val online = try {
                            networkManager?.probePeer(peer.ipAddress, peer.port, 2000) != null
                        } catch (_: Exception) { false }
                        peer.id to online
                    }
                }.awaitAll()

            val updated = _peers.value.toMutableMap()
            var changed = false
            results.forEach { (id, probeSuccess) ->
                val peer = updated[id] ?: return@forEach
                if (probeSuccess) {
                    if (!peer.isOnline || peer.lastSeen < probeStartTime) {
                        updated[id] = peer.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                        changed = true
                    }
                } else {
                    // TCP 探测 2 秒超时已失败，若 lastSeen 也在本轮探测之前，直接标记离线
                    if (peer.isOnline && peer.lastSeen < probeStartTime) {
                        updated[id] = peer.copy(isOnline = false)
                        changed = true
                    }
                }
            }
            if (changed) {
                _peers.value = updated
                updated.values.filter { it.id != FILE_TRANSFER_ASSISTANT_ID }.forEach { DatabaseManager.savePeer(it) }
            }
        }
    }

    private fun migrateGroupNumbers() {
        val m = _groups.value.toMutableMap()
        var changed = false
        m.values.filter { it.groupNumber.isEmpty() }.forEach { group ->
            val updated = group.copy(groupNumber = generateUniqueGroupNumber())
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
        if (message.senderId == _currentUser?.id
            && message.type != MessageType.FRIEND_REQUEST
            && message.type != MessageType.FRIEND_RESPONSE) return
        when (message.type) {
            MessageType.GROUP_SYNC -> handleGroupSync(message)
            MessageType.GROUP_LEAVE -> handleGroupLeave(message)
            MessageType.FRIEND_REQUEST -> handleIncomingFriendRequest(message)
            MessageType.FRIEND_RESPONSE -> handleFriendResponse(message)
            MessageType.GROUP_INVITE -> handleGroupInvite(message)
            MessageType.GROUP_INVITE_RESPONSE -> handleGroupInviteResponse(message)
            MessageType.PROFILE_UPDATE -> handleProfileUpdate(message)
            MessageType.AVATAR_REQUEST -> handleAvatarRequest(message)
            MessageType.MESSAGE_READ_ACK -> {
                // 已关闭群消息回执功能：忽略回执消息
            }
            else -> {
                // 对于群聊消息，检查用户是否还在群中
                if (isGroupMessage(message) && !isMemberOfGroup(message.groupId)) {
                    // 自愈机制：如果消息携带了群成员快照且包含自己，自动加入群
                    if (trySelfHealGroup(message)) {
                        addMessageToHistory(message)
                        notifyMessageListeners(message)
                        val chatId = message.groupId ?: return
                        if (chatId != currentChatId) {
                            incrementUnreadCount(chatId)
                        }
                    }
                    return
                }
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
        // 记录当前时间为该聊天的最后阅读时间
        DatabaseManager.updateLastReadAt(chatId, System.currentTimeMillis())

        val current = _unreadCounts.value.toMutableMap()
        current.remove(chatId)
        _unreadCounts.value = current
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
        val existing = currentPeers[peer.id]
        val updatedPeer = peer.copy(
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            // 保留已有的签名等信息
            signature = existing?.signature ?: peer.signature,
            // 保留已有的本地头像路径
            avatar = existing?.avatar ?: peer.avatar
        )
        currentPeers[peer.id] = updatedPeer
        _peers.value = currentPeers
        DatabaseManager.savePeer(updatedPeer)

        // 如果对方的 avatarHash 与本地缓存的头像不一致，请求头像
        val localHash = computeAvatarHash(updatedPeer.avatar)
        val remoteHash = peer.avatarHash
        if (remoteHash != null && remoteHash != localHash) {
            requestAvatar(peer.id, peer.ipAddress, peer.port)
        }
    }

    private fun computeAvatarHash(avatarPath: String?): String? {
        if (avatarPath == null) return null
        return try {
            val bytes = java.io.File(avatarPath).readBytes()
            java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun requestAvatar(peerId: String, ip: String, port: Int) {
        scope.launch {
            try {
                val msg = Message(
                    type = MessageType.AVATAR_REQUEST,
                    senderId = _currentUser?.id ?: return@launch,
                    receiverId = peerId,
                    content = "avatar_request",
                    senderName = _username
                )
                networkManager?.sendMessage(msg, ip, port)
            } catch (_: Exception) {}
        }
    }

    /**
     * 手动添加好友
     * @param ipAddress IP 地址
     * @param port 端口
     * @param name 用户名
     * @param userId 可选的用户真实 ID（从探测或好友申请中获取）
     * @return 是否添加成功
     */
    fun addManualPeer(ipAddress: String, port: Int, name: String, userId: String? = null): Boolean {
        // 检查是否已存在（通过 IP+port）
        if (_peers.value.values.any { it.ipAddress == ipAddress && it.port == port }) return false

        // 优先使用真实 userId，如果没有则使用基于 IP+port 的稳定临时 ID
        val finalUserId = userId ?: "manual_${ipAddress}_${port}".hashCode().toString()
        val peer = Peer(
            id = finalUserId,
            username = name, ipAddress = ipAddress, port = port, isOnline = true
        )
        addPeer(peer)
        // 从黑名单中移除（如果存在）
        unblockPeer(finalUserId)
        return true
    }

    private fun unblockPeer(peerId: String) {
        if (_blockedPeerIds.value.contains(peerId)) {
            val blocked = _blockedPeerIds.value.toMutableSet()
            blocked.remove(peerId)
            _blockedPeerIds.value = blocked
            DatabaseManager.removeBlockedPeer(peerId)
        }
    }

    fun isPeerExists(ipAddress: String, port: Int): Boolean {
        return _peers.value.values.any { it.ipAddress == ipAddress && it.port == port }
    }

    fun removePeer(peerId: String) {
        if (peerId == FILE_TRANSFER_ASSISTANT_ID) return
        val m = _peers.value.toMutableMap(); m.remove(peerId); _peers.value = m
        DatabaseManager.deletePeer(peerId)

        // 从所有群中移除该成员
        val gm = _groups.value.toMutableMap()
        var groupChanged = false
        gm.values.forEach { group ->
            if (group.memberIds.contains(peerId)) {
                group.memberIds.remove(peerId)
                DatabaseManager.saveGroup(group)
                groupChanged = true
            }
        }
        if (groupChanged) _groups.value = gm.toMap()

        // 添加到黑名单，防止自动加回
        val blocked = _blockedPeerIds.value.toMutableSet()
        blocked.add(peerId)
        _blockedPeerIds.value = blocked
        DatabaseManager.addBlockedPeer(peerId)
    }

    fun searchPeersByIp(ip: String): List<Peer> =
        _peers.value.values.filter { it.ipAddress.contains(ip) }

    // =============== Pinned Items ===============

    fun isPinned(id: String): Boolean = _pinnedIds.value.contains(id)

    fun togglePin(id: String) {
        val mutable = _pinnedIds.value.toMutableSet()
        if (mutable.contains(id)) mutable.remove(id) else mutable.add(id)
        _pinnedIds.value = mutable
        DatabaseManager.saveSetting("pinned_ids", mutable.joinToString(","))
    }

    // =============== File Transfer Assistant ===============

    fun isFileTransferAssistant(peerId: String): Boolean = peerId == FILE_TRANSFER_ASSISTANT_ID

    fun sendToFileTransferAssistant(message: Message) {
        val localMsg = message.copy(fileData = null)
        addMessageToHistory(localMsg)
        notifyMessageListeners(localMsg)
    }

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

    /**
     * 为群消息附加成员快照信息，用于接收端自愈（非好友或群同步丢失时可自动恢复群信息）
     */
    private fun withGroupSnapshot(message: Message, groupId: String): Message {
        val group = _groups.value[groupId] ?: return message
        val ownerPeer = _peers.value[group.ownerId]
        return message.copy(
            groupMemberIds = group.memberIds.toList(),
            groupName = group.name,
            groupOwnerName = ownerPeer?.username ?: group.ownerId
        )
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
        val file = java.io.File(imagePath)
        val bytes = try { file.readBytes() } catch (_: Exception) { return }
        val localCopy = saveToLanChatDir(file)
        val msg = Message(
            type = MessageType.IMAGE, senderId = _currentUser?.id ?: return,
            receiverId = receiverId, content = localCopy.absolutePath,
            fileName = file.name, fileSize = file.length(), fileData = bytes
        )
        sendPeerMessage(msg, receiverId)
    }

    fun sendFileMessage(receiverId: String, filePath: String, fileName: String) {
        val file = java.io.File(filePath)
        val bytes = try { file.readBytes() } catch (_: Exception) { return }
        val localCopy = saveToLanChatDir(file)
        val msg = Message(
            type = MessageType.FILE, senderId = _currentUser?.id ?: return,
            receiverId = receiverId, content = localCopy.absolutePath,
            fileName = fileName, fileSize = file.length(), fileData = bytes
        )
        sendPeerMessage(msg, receiverId)
    }

    private fun saveToLanChatDir(srcFile: java.io.File): java.io.File {
        val dir = com.lanchat.network.NetworkManager.getLanChatDir()
        var target = java.io.File(dir, srcFile.name)
        if (target.exists() && target.absolutePath != srcFile.absolutePath) {
            val ext = srcFile.extension
            val base = srcFile.nameWithoutExtension
            target = java.io.File(dir, "${base}_${System.currentTimeMillis()}.$ext")
        }
        if (target.absolutePath != srcFile.absolutePath) {
            srcFile.copyTo(target, overwrite = true)
        }
        return target
    }

    private fun sendPeerMessage(message: Message, peerId: String) {
        if (peerId == FILE_TRANSFER_ASSISTANT_ID) {
            addMessageToHistory(message.copy(fileData = null))
            return
        }
        scope.launch {
            val target = resolvePeerTarget(peerId)
            addMessageToHistory(message.copy(fileData = null))

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
        if (isUserMuted(groupId, senderId)) return
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
        val enriched = withGroupSnapshot(message, groupId)
        scope.launch {
            val targets = getGroupMemberTargets(groupId)
            if (targets.isNotEmpty()) networkManager?.sendToMultiple(enriched, targets)
            addMessageToHistory(message)
        }
    }

    fun sendGroupImageMessage(groupId: String, imagePath: String) {
        val senderId = _currentUser?.id ?: return
        val file = java.io.File(imagePath)
        val bytes = try { file.readBytes() } catch (_: Exception) { return }
        val localCopy = saveToLanChatDir(file)
        val message = Message(
            type = MessageType.IMAGE, senderId = senderId, receiverId = groupId,
            content = localCopy.absolutePath, fileName = file.name,
            fileSize = file.length(), fileData = bytes, groupId = groupId, senderName = _username
        )
        val enriched = withGroupSnapshot(message, groupId)
        scope.launch {
            val targets = getGroupMemberTargets(groupId)
            if (targets.isNotEmpty()) networkManager?.sendToMultiple(enriched, targets)
            addMessageToHistory(message.copy(fileData = null))
        }
    }

    fun sendGroupFileMessage(groupId: String, filePath: String, fileName: String) {
        val senderId = _currentUser?.id ?: return
        val file = java.io.File(filePath)
        val bytes = try { file.readBytes() } catch (_: Exception) { return }
        val localCopy = saveToLanChatDir(file)
        val message = Message(
            type = MessageType.FILE, senderId = senderId, receiverId = groupId,
            content = localCopy.absolutePath, fileName = fileName,
            fileSize = file.length(), fileData = bytes, groupId = groupId, senderName = _username
        )
        val enriched = withGroupSnapshot(message, groupId)
        scope.launch {
            val targets = getGroupMemberTargets(groupId)
            if (targets.isNotEmpty()) networkManager?.sendToMultiple(enriched, targets)
            addMessageToHistory(message.copy(fileData = null))
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
            val enriched = withGroupSnapshot(msg, groupId)
            scope.launch {
                val targets = getGroupMemberTargets(groupId)
                if (targets.isNotEmpty()) networkManager?.sendToMultiple(enriched, targets)
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

        // 检查消息是否已存在（通过 ID 去重）
        if (chatMessages.any { it.id == message.id }) {
            return // 消息已存在，不再重复添加
        }

        chatMessages.add(message)
        _messages.value = currentMessages
        DatabaseManager.saveMessage(message)
    }

    fun getChatHistory(chatId: String): List<Message> = _messages.value[chatId] ?: emptyList()

    fun getLastReadAt(chatId: String): Long = DatabaseManager.getLastReadAt(chatId)

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
        val targetMemberId: String? = null,
        val mutedMembers: Map<String, Long>? = null,
        val globalMute: Boolean? = null
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

        val updated = group.copy(name = newName)
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

        // 群内系统消息：成员入群
        val memberName = _peers.value[newMemberId]?.username ?: "新成员"
        val systemNotice = Message(
            type = MessageType.SYSTEM,
            senderId = _currentUser?.id ?: "",
            receiverId = groupId,
            groupId = groupId,
            content = "$memberName 已成功入群",
            senderName = _username
        )
        addMessageToHistory(systemNotice)
        val enrichedNotice = withGroupSnapshot(systemNotice, groupId)
        scope.launch {
            val allTargets = getGroupMemberTargets(groupId)
            if (allTargets.isNotEmpty()) {
                networkManager?.sendToMultiple(enrichedNotice, allTargets)
            }
        }
        return true
    }

    fun removeGroupMember(groupId: String, memberId: String): Boolean {
        val userId = _currentUser?.id ?: return false
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return false
        val removedName = _peers.value[memberId]?.username ?: "未知用户"
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

        val sysMsg = Message(
            type = MessageType.SYSTEM, senderId = userId, receiverId = groupId,
            groupId = groupId, content = "$removedName 被移除群聊", senderName = _username
        )
        addMessageToHistory(sysMsg)
        notifyMessageListeners(sysMsg)
        val enrichedSysMsg = withGroupSnapshot(sysMsg, groupId)
        scope.launch {
            if (remainingTargets.isNotEmpty()) {
                networkManager?.sendToMultiple(enrichedSysMsg, remainingTargets)
            }
        }
        return true
    }

    fun isGroupOwner(groupId: String): Boolean {
        val userId = _currentUser?.id ?: return false
        return _groups.value[groupId]?.isOwner(userId) ?: false
    }

    fun isUserMuted(groupId: String, userId: String? = null): Boolean {
        val uid = userId ?: _currentUser?.id ?: return false
        return _groups.value[groupId]?.isMuted(uid) ?: false
    }

    /**
     * @param durationMs 禁言时长（毫秒），-1 表示永久
     */
    fun muteUser(groupId: String, memberId: String, durationMs: Long) {
        val userId = _currentUser?.id ?: return
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return
        if (!group.isOwner(userId)) return

        val unmuteAt = if (durationMs == -1L) -1L else System.currentTimeMillis() + durationMs
        group.mutedMembers[memberId] = unmuteAt
        m[groupId] = group; _groups.value = m
        DatabaseManager.saveGroup(group)

        val memberName = _peers.value[memberId]?.username ?: "用户"
        val durationText = formatMuteDuration(durationMs)
        val sysMsg = Message(
            type = MessageType.SYSTEM, senderId = userId, receiverId = groupId,
            groupId = groupId, content = "$memberName 已被禁言$durationText", senderName = _username
        )
        addMessageToHistory(sysMsg); notifyMessageListeners(sysMsg)

        val targets = getGroupMemberTargets(groupId)
        broadcastGroupSync(GroupSyncPayload(
            action = "MUTE_UPDATE", groupId = groupId,
            mutedMembers = group.mutedMembers.toMap(), globalMute = group.globalMute
        ), targets)
        val enrichedSysMsg = withGroupSnapshot(sysMsg, groupId)
        scope.launch { if (targets.isNotEmpty()) networkManager?.sendToMultiple(enrichedSysMsg, targets) }
    }

    fun unmuteUser(groupId: String, memberId: String) {
        val userId = _currentUser?.id ?: return
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return
        if (!group.isOwner(userId)) return

        group.mutedMembers.remove(memberId)
        m[groupId] = group; _groups.value = m
        DatabaseManager.saveGroup(group)

        val memberName = _peers.value[memberId]?.username ?: "用户"
        val sysMsg = Message(
            type = MessageType.SYSTEM, senderId = userId, receiverId = groupId,
            groupId = groupId, content = "$memberName 已被解除禁言", senderName = _username
        )
        addMessageToHistory(sysMsg); notifyMessageListeners(sysMsg)

        val targets = getGroupMemberTargets(groupId)
        broadcastGroupSync(GroupSyncPayload(
            action = "MUTE_UPDATE", groupId = groupId,
            mutedMembers = group.mutedMembers.toMap(), globalMute = group.globalMute
        ), targets)
        val enrichedSysMsg2 = withGroupSnapshot(sysMsg, groupId)
        scope.launch { if (targets.isNotEmpty()) networkManager?.sendToMultiple(enrichedSysMsg2, targets) }
    }

    fun setGlobalMute(groupId: String, muted: Boolean) {
        val userId = _currentUser?.id ?: return
        val m = _groups.value.toMutableMap()
        val group = m[groupId] ?: return
        if (!group.isOwner(userId)) return

        val updated = group.copy(globalMute = muted)
        m[groupId] = updated; _groups.value = m
        DatabaseManager.saveGroup(updated)

        val sysMsg = Message(
            type = MessageType.SYSTEM, senderId = userId, receiverId = groupId,
            groupId = groupId, content = if (muted) "群主已开启全员禁言" else "群主已关闭全员禁言",
            senderName = _username
        )
        addMessageToHistory(sysMsg); notifyMessageListeners(sysMsg)

        val targets = getGroupMemberTargets(groupId)
        broadcastGroupSync(GroupSyncPayload(
            action = "MUTE_UPDATE", groupId = groupId,
            mutedMembers = updated.mutedMembers.toMap(), globalMute = updated.globalMute
        ), targets)
        val enrichedSysMsg3 = withGroupSnapshot(sysMsg, groupId)
        scope.launch { if (targets.isNotEmpty()) networkManager?.sendToMultiple(enrichedSysMsg3, targets) }
    }

    private fun formatMuteDuration(durationMs: Long): String {
        if (durationMs == -1L) return "（永久）"
        val minutes = durationMs / 60_000
        return when {
            minutes < 60 -> "（${minutes}分钟）"
            minutes < 1440 -> "（${minutes / 60}小时）"
            else -> "（${minutes / 1440}天）"
        }
    }

    fun getGroup(groupId: String): Group? = _groups.value[groupId]

    fun getGroupMembers(groupId: String): List<Peer> {
        val group = _groups.value[groupId] ?: return emptyList()
        return group.memberIds.mapNotNull { memberId ->
            _peers.value[memberId] ?: if (memberId == _currentUser?.id) _currentUser else null
        }
    }

    fun getGroupOnlineCount(groupId: String): Int {
        val group = _groups.value[groupId] ?: return 0
        val myId = _currentUser?.id
        return group.memberIds.count { memberId ->
            if (memberId == myId) true
            else _peers.value[memberId]?.isOnline == true
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
                val updated = group.copy(name = payload.groupName ?: group.name)
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
            "MUTE_UPDATE" -> {
                val group = m[payload.groupId] ?: return
                val updated = group.copy(
                    mutedMembers = payload.mutedMembers?.toMutableMap() ?: group.mutedMembers,
                    globalMute = payload.globalMute ?: group.globalMute
                )
                m[payload.groupId] = updated; _groups.value = m
                DatabaseManager.saveGroup(updated)
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

    data class GroupLeavePayload(
        val groupId: String,
        val leaverId: String,
        val leaverName: String
    )

    /**
     * 邀请用户入群，等待对方 ACK（最多10秒）
     * @return true=对方已收到并创建邀请记录, false=超时未收到反馈
     */
    suspend fun inviteToGroup(groupId: String, peerId: String): Boolean {
        val cu = _currentUser ?: return false
        val group = _groups.value[groupId] ?: return false
        if (!group.isOwner(cu.id)) return false
        if (group.memberIds.contains(peerId)) return false
        if (peerId == FILE_TRANSFER_ASSISTANT_ID) return false  // 不允许邀请文件传输助手入群

        val peer = _peers.value[peerId] ?: return false
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
        networkManager?.sendMessage(msg, peer.ipAddress, peer.port)

        // 等待 ACK，最多 10 秒
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 10_000) {
            if (inviteAckReceived.remove(request.id) == true) return true
            delay(200)
        }
        return false
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
                val rm = _groupRequests.value.toMutableMap()
                rm[request.id] = request; _groupRequests.value = rm
                DatabaseManager.saveGroupRequest(request)

                // 收到邀请后发送 ACK 给邀请方
                val cu = _currentUser ?: return
                val ackPayload = GroupInvitePayload(
                    action = "INVITE_ACK", groupId = payload.groupId,
                    requestId = payload.requestId, requesterId = cu.id,
                    requesterName = _username
                )
                val ackMsg = Message(
                    type = MessageType.GROUP_INVITE_RESPONSE, senderId = cu.id,
                    receiverId = payload.requesterId, content = gson.toJson(ackPayload),
                    senderName = _username
                )
                val target = resolvePeerTarget(payload.requesterId)
                if (target != null) {
                    scope.launch { networkManager?.sendMessage(ackMsg, target.first, target.second) }
                }
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
            "INVITE_ACK" -> {
                inviteAckReceived[payload.requestId] = true
            }
            "ACCEPT_INVITE" -> {
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

    // =============== Group Leave ===============

    /**
     * 退出群聊
     * 成员可以直接退群，广播退群消息给所有群成员
     * 退群后不再接收群消息，但历史记录保留
     */
    enum class LeaveGroupResult { SUCCESS, IS_OWNER, NOT_MEMBER, ERROR }

    fun leaveGroup(groupId: String): LeaveGroupResult {
        val userId = _currentUser?.id ?: return LeaveGroupResult.ERROR
        val group = _groups.value[groupId] ?: return LeaveGroupResult.ERROR

        if (group.isOwner(userId)) return LeaveGroupResult.IS_OWNER
        if (!group.isMember(userId)) return LeaveGroupResult.NOT_MEMBER

        // 先获取群成员目标（退群前，这样还能拿到成员信息）
        val targets = getGroupMemberTargets(groupId)

        // 广播退群消息给所有群成员
        val payload = GroupLeavePayload(
            groupId = groupId,
            leaverId = userId,
            leaverName = _username
        )
        val message = Message(
            type = MessageType.GROUP_LEAVE,
            senderId = userId,
            receiverId = groupId,
            content = gson.toJson(payload),
            senderName = _username,
            groupId = groupId
        )

        val enrichedLeave = withGroupSnapshot(message, groupId)
        scope.launch {
            if (targets.isNotEmpty()) {
                networkManager?.sendToMultiple(enrichedLeave, targets)
            }
        }

        // 本地删除群和聊天记录
        val m = _groups.value.toMutableMap()
        m.remove(groupId)
        _groups.value = m
        DatabaseManager.deleteGroup(groupId)
        clearChatHistory(groupId)

        return LeaveGroupResult.SUCCESS
    }

    private fun handleGroupLeave(message: Message) {
        val payload = try {
            gson.fromJson(message.content, GroupLeavePayload::class.java)
        } catch (_: Exception) { return }

        val group = _groups.value[payload.groupId] ?: return
        val userId = _currentUser?.id ?: return

        // 如果是自己退群的消息，忽略（因为已经在 leaveGroup 中处理了）
        if (payload.leaverId == userId) return

        // 其他成员收到退群消息，从群组中移除该成员
        if (group.isOwner(userId) || group.isMember(userId)) {
            val m = _groups.value.toMutableMap()
            val updatedGroup = group.copy(
                memberIds = group.memberIds.filter { it != payload.leaverId }.toMutableList()
            )
            m[payload.groupId] = updatedGroup
            _groups.value = m
            DatabaseManager.saveGroup(updatedGroup)

            // 其他成员收到退群事件时插入系统消息
            addMessageToHistory(
                Message(
                    type = MessageType.SYSTEM,
                    senderId = payload.leaverId,
                    receiverId = payload.groupId,
                    groupId = payload.groupId,
                    content = "${payload.leaverName} 已退群",
                    senderName = payload.leaverName
                )
            )
        }
    }

    // =============== Profile Update ===============

    private fun handleProfileUpdate(message: Message) {
        val senderId = message.senderId
        val newUsername = message.senderName ?: message.content

        var avatarLocalPath: String? = null
        if (message.fileName != null) {
            val receivedFile = java.io.File(message.content)
            if (receivedFile.exists() && receivedFile.length() > 0) {
                val avatarDir = java.io.File(System.getProperty("user.home"), ".lanchat/avatars")
                if (!avatarDir.exists()) avatarDir.mkdirs()
                val ext = message.fileName.substringAfterLast('.', "png")
                val target = java.io.File(avatarDir, "${senderId}.$ext")
                try {
                    receivedFile.copyTo(target, overwrite = true)
                    receivedFile.delete()
                    avatarLocalPath = target.absolutePath
                } catch (_: Exception) { }
            }
        }

        val peers = _peers.value.toMutableMap()
        val existing = peers[senderId] ?: return
        val updated = existing.copy(
            username = newUsername,
            avatar = avatarLocalPath ?: existing.avatar
        )
        peers[senderId] = updated
        _peers.value = peers
        DatabaseManager.savePeer(updated)
    }

    // =============== User Settings ===============

    private fun handleAvatarRequest(message: Message) {
        // 收到头像请求时，发送自己的头像
        val senderId = message.senderId
        val peer = _peers.value.values.find { it.id == senderId } ?: return
        if (_userAvatar != null) {
            scope.launch {
                try {
                    val avatarFile = java.io.File(_userAvatar!!)
                    if (!avatarFile.exists()) return@launch
                    val avatarBytes = avatarFile.readBytes()
                    val msg = Message(
                        type = MessageType.PROFILE_UPDATE,
                        senderId = _currentUser?.id ?: return@launch,
                        receiverId = senderId,
                        content = "",
                        senderName = _username,
                        fileName = avatarFile.name,
                        fileSize = avatarBytes.size.toLong(),
                        fileData = avatarBytes
                    )
                    networkManager?.sendMessage(msg, peer.ipAddress, peer.port)
                } catch (_: Exception) {}
            }
        }
    }

    fun updateUsername(newUsername: String) {
        _username = newUsername
        networkManager?.updateUsername(newUsername)
        _currentUser = _currentUser?.copy(username = newUsername)
        DatabaseManager.saveSetting("username", newUsername)
    }

    fun refreshPeers() { networkManager?.sendDiscovery() }

    fun updateEncryptionKey(passphrase: String) {
        DatabaseManager.saveSetting("encryptionKey", passphrase)
        CryptoManager.updatePassphrase(passphrase)
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        DatabaseManager.saveSetting("encryptionEnabled", if (enabled) "true" else "false")
        CryptoManager.isEnabled = enabled
    }

    fun isEncryptionEnabled(): Boolean = CryptoManager.isEnabled

    fun getEncryptionKey(): String = DatabaseManager.getSetting("encryptionKey", "")

    /**
     * 刷新用户 ID
     * 如果当前 ID 无效（缺失或格式错误），则根据 MAC 地址重新生成
     * 用于修复之前使用 UUID 导致的 ID 不稳定问题
     *
     * @return true 如果 ID 被重新生成，false 如果 ID 有效无需更改
     */
    fun refreshUserId(): Boolean {
        val currentUserId = _currentUser?.id ?: return false

        // 检查当前 ID 是否有效（32位十六进制字符串）
        if (UserIdGenerator.isValidUserId(currentUserId)) {
            return false // ID 有效，无需更改
        }

        // 重新生成基于 MAC 地址的唯一 ID
        val newId = UserIdGenerator.generateUserId()

        // 更新数据库
        DatabaseManager.saveSetting("currentUserId", newId)

        // 更新当前用户
        _currentUser = _currentUser?.copy(id = newId)

        // 更新 NetworkManager 的用户 ID
        networkManager?.updateUserId(newId)

        return true
    }

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
        val hasPending = _friendRequests.value.values.any {
            it.status == FriendRequestStatus.PENDING_SENT && it.toIp == targetIp && it.toPort == targetPort
        }
        if (hasPending) return

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
        scope.launch {
            networkManager?.sendMessage(msg, targetIp, targetPort)
            delay(800)
            networkManager?.sendMessage(msg, targetIp, targetPort)
        }
    }

    fun acceptFriendRequest(requestId: String) {
        val m = _friendRequests.value.toMutableMap()
        val request = m[requestId] ?: return
        val updated = request.copy(status = FriendRequestStatus.ACCEPTED)
        m[requestId] = updated; _friendRequests.value = m
        DatabaseManager.saveFriendRequest(updated)

        val peer = Peer(
            id = request.fromUserId,
            username = request.fromUsername,
            ipAddress = request.fromIp,
            port = request.fromPort,
            isOnline = true
        )
        addPeer(peer)
        unblockPeer(request.fromUserId)

        val cu = _currentUser ?: return
        val payload = FriendRequestPayload(
            action = "ACCEPT", fromUserId = cu.id, fromUsername = _username,
            fromIp = _localIp, fromPort = networkManager?.getActualTcpPort() ?: 8889
        )
        val msg = Message(
            type = MessageType.FRIEND_RESPONSE, senderId = cu.id,
            receiverId = request.fromUserId, content = gson.toJson(payload), senderName = _username
        )
        scope.launch {
            networkManager?.sendMessage(msg, request.fromIp, request.fromPort)
            // 再发一次确保送达（TCP 可能因网络延迟丢失）
            delay(500)
            networkManager?.sendMessage(msg, request.fromIp, request.fromPort)
        }
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

        // 防重复：如果已存在来自同一用户的待处理申请，忽略
        val existing = _friendRequests.value.values.any {
            it.fromUserId == payload.fromUserId
                && (it.status == FriendRequestStatus.PENDING_RECEIVED || it.status == FriendRequestStatus.ACCEPTED)
        }
        if (existing) return

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
                    (it.status == FriendRequestStatus.PENDING_SENT || it.status == FriendRequestStatus.ACCEPTED)
                        && it.toIp == payload.fromIp
                }
                if (sentRequest != null && sentRequest.status == FriendRequestStatus.PENDING_SENT) {
                    val updated = sentRequest.copy(status = FriendRequestStatus.ACCEPTED)
                    m[sentRequest.id] = updated; _friendRequests.value = m
                    DatabaseManager.saveFriendRequest(updated)
                }
                val peer = Peer(
                    id = payload.fromUserId,
                    username = payload.fromUsername,
                    ipAddress = payload.fromIp,
                    port = payload.fromPort,
                    isOnline = true
                )
                addPeer(peer)
                unblockPeer(payload.fromUserId)
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

    // =============== Helper Functions ===============

    private fun isGroupMessage(message: Message): Boolean {
        return message.type == MessageType.GROUP_CHAT ||
               message.type == MessageType.MENTION_MEMBER ||
               message.type == MessageType.MENTION_ALL ||
               (message.type == MessageType.SYSTEM && !message.groupId.isNullOrEmpty())
    }

    private fun isMemberOfGroup(groupId: String?): Boolean {
        if (groupId == null) return false
        val group = _groups.value[groupId] ?: return false
        val userId = _currentUser?.id ?: return false
        return group.isMember(userId)
    }

    /**
     * 自愈机制：从群消息携带的成员快照中恢复群信息
     * 当用户因 GROUP_SYNC 丢失等原因不在本地群列表中，但实际仍是群成员时触发
     * @return true 如果成功自愈（用户在成员快照中）
     */
    private fun trySelfHealGroup(message: Message): Boolean {
        val myId = _currentUser?.id ?: return false
        val groupId = message.groupId ?: return false

        // 检查成员快照是否包含自己
        if (message.groupMemberIds.isEmpty() || !message.groupMemberIds.contains(myId)) {
            return false
        }

        // 如果群已经存在但成员列表过期，更新成员列表
        val existingGroup = _groups.value[groupId]
        if (existingGroup != null) {
            val updated = existingGroup.copy(memberIds = message.groupMemberIds.toMutableList())
            val m = _groups.value.toMutableMap()
            m[groupId] = updated
            _groups.value = m
            DatabaseManager.saveGroup(updated)
            return true
        }

        // 群不存在，从快照重建群信息
        val group = Group(
            id = groupId,
            name = message.groupName ?: "群聊",
            ownerId = message.senderId, // 发送者可能是群主，后续 GROUP_SYNC 会更新
            memberIds = message.groupMemberIds.toMutableList(),
            groupNumber = ""
        )
        val m = _groups.value.toMutableMap()
        m[groupId] = group
        _groups.value = m
        DatabaseManager.saveGroup(group)

        // 添加发送者为 peer（如果不存在）
        if (_peers.value[message.senderId] == null && message.senderName != null) {
            addPeer(Peer(
                id = message.senderId,
                username = message.senderName,
                ipAddress = "unknown",
                port = 8889,
                isOnline = true
            ))
        }

        return true
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
        networkManager?.updateAvatarHash(avatarPath)
    }

    fun broadcastProfileUpdate() {
        val senderId = _currentUser?.id ?: return
        val avatarFile = _userAvatar?.let { java.io.File(it) }
        val avatarBytes = try { avatarFile?.takeIf { it.exists() }?.readBytes() } catch (_: Exception) { null }
        val avatarFileName = avatarFile?.name

        val msg = Message(
            type = MessageType.PROFILE_UPDATE,
            senderId = senderId,
            receiverId = "",
            content = _username,
            senderName = _username,
            fileName = avatarFileName,
            fileSize = avatarBytes?.size?.toLong(),
            fileData = avatarBytes
        )
        scope.launch {
            val targets = _peers.value.values
                .filter { it.id != senderId && it.id != FILE_TRANSFER_ASSISTANT_ID && it.isOnline }
                .map { Pair(it.ipAddress, it.port) }
            if (targets.isNotEmpty()) {
                networkManager?.sendToMultiple(msg, targets)
            }
        }
    }

    override fun dispose() {
        scope.cancel()
        networkManager?.stop()
        networkManager = null
        DatabaseManager.close()
    }
}

package com.lanchat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.NetworkManager
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * LAN Chat 主服务
 */
@Service(Service.Level.APP)
class LanChatService : Disposable {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var networkManager: NetworkManager? = null
    
    // 联系人列表
    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers
    
    // 消息历史
    private val _messages = MutableStateFlow<Map<String, MutableList<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, MutableList<Message>>> = _messages
    
    // 当前用户信息
    private var _currentUser: Peer? = null
    val currentUser: Peer?
        get() = _currentUser
    
    // 本机IP
    private var _localIp: String = "127.0.0.1"
    val localIp: String
        get() = _localIp
    
    // 用户设置
    private var _username: String = System.getProperty("user.name", "Anonymous")
    val username: String
        get() = _username
    
    // 是否已初始化
    private var isInitialized = false
    
    companion object {
        fun getInstance(): LanChatService {
            return ApplicationManager.getApplication().getService(LanChatService::class.java)
        }
    }
    
    init {
        initialize()
    }
    
    private fun initialize() {
        if (isInitialized) return
        isInitialized = true
        
        // 获取本机IP
        _localIp = getLocalIpAddress()
        
        // 初始化网络管理器
        networkManager = NetworkManager()
        
        scope.launch {
            networkManager?.let { nm ->
                // 启动网络服务
                nm.start(_username)
                
                // 设置当前用户
                _currentUser = Peer(
                    id = nm.currentUserId,
                    username = _username,
                    ipAddress = _localIp,
                    port = 8889
                )
                
                // 监听新用户上线
                nm.peerDiscovered.collect { peer ->
                    addPeer(peer)
                }
            }
        }
    }
    
    /**
     * 获取本机IP地址
     */
    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
    
    /**
     * 添加联系人
     */
    private fun addPeer(peer: Peer) {
        val currentPeers = _peers.value.toMutableMap()
        currentPeers[peer.id] = peer
        _peers.value = currentPeers
    }
    
    /**
     * 手动添加联系人
     */
    fun addManualPeer(ipAddress: String, port: Int, name: String) {
        val peer = Peer(
            id = "manual_${System.currentTimeMillis()}",
            username = name,
            ipAddress = ipAddress,
            port = port,
            isOnline = true
        )
        addPeer(peer)
    }
    
    /**
     * 移除联系人
     */
    fun removePeer(peerId: String) {
        val currentPeers = _peers.value.toMutableMap()
        currentPeers.remove(peerId)
        _peers.value = currentPeers
    }
    
    /**
     * 发送文本消息
     */
    fun sendTextMessage(receiverId: String, content: String) {
        val senderId = _currentUser?.id ?: return
        
        val message = Message(
            type = MessageType.TEXT,
            senderId = senderId,
            receiverId = receiverId,
            content = content
        )
        sendMessage(message)
    }
    
    /**
     * 发送图片消息
     */
    fun sendImageMessage(receiverId: String, imagePath: String) {
        val senderId = _currentUser?.id ?: return
        
        val message = Message(
            type = MessageType.IMAGE,
            senderId = senderId,
            receiverId = receiverId,
            content = imagePath
        )
        sendMessage(message)
    }
    
    /**
     * 发送文件消息
     */
    fun sendFileMessage(receiverId: String, filePath: String, fileName: String) {
        val senderId = _currentUser?.id ?: return
        
        val message = Message(
            type = MessageType.FILE,
            senderId = senderId,
            receiverId = receiverId,
            content = filePath,
            fileName = fileName
        )
        sendMessage(message)
    }
    
    /**
     * 发送消息
     */
    private fun sendMessage(message: Message) {
        scope.launch {
            networkManager?.sendMessage(message)
            addMessageToHistory(message)
        }
    }
    
    /**
     * 添加消息到历史记录
     */
    private fun addMessageToHistory(message: Message) {
        val chatId = if (message.senderId == _currentUser?.id) {
            message.receiverId
        } else {
            message.senderId
        }
        
        val currentMessages = _messages.value.toMutableMap()
        val chatMessages = currentMessages.getOrPut(chatId) { mutableListOf() }
        chatMessages.add(message)
        _messages.value = currentMessages
    }
    
    /**
     * 获取聊天历史
     */
    fun getChatHistory(chatId: String): List<Message> {
        return _messages.value[chatId] ?: emptyList()
    }
    
    /**
     * 创建群聊
     */
    fun createGroup(groupName: String, memberIds: List<String>): String {
        val groupId = "group_${System.currentTimeMillis()}"
        return groupId
    }
    
    /**
     * 设置用户名
     */
    fun updateUsername(newUsername: String) {
        _username = newUsername
        networkManager?.updateUsername(newUsername)
        _currentUser = _currentUser?.copy(username = newUsername)
    }
    
    /**
     * 刷新联系人列表
     */
    fun refreshPeers() {
        networkManager?.sendDiscovery()
    }
    
    override fun dispose() {
        scope.cancel()
        networkManager?.stop()
        networkManager = null
    }
}

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
import java.util.concurrent.ConcurrentHashMap

/**
 * LAN Chat 主服务
 * 负责管理整个插件的核心功能
 */
@Service(Service.Level.APP)
class LanChatService : Disposable {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val networkManager = NetworkManager()
    
    // 联系人列表
    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers
    
    // 消息历史
    private val _messages = MutableStateFlow<Map<String, MutableList<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, MutableList<Message>>> = _messages
    
    // 当前用户信息
    var currentUser: Peer? = null
        private set
    
    // 用户设置
    var username: String = System.getProperty("user.name", "Anonymous")
    var avatar: String? = null
    
    companion object {
        fun getInstance(): LanChatService {
            return ApplicationManager.getApplication().getService(LanChatService::class.java)
        }
    }
    
    init {
        initialize()
    }
    
    private fun initialize() {
        scope.launch {
            // 启动网络服务
            networkManager.start(username)
            
            // 监听新用户上线
            networkManager.peerDiscovered.collect { peer ->
                addPeer(peer)
            }
        }
    }
    
    /**
     * 添加联系人
     */
    private fun addPeer(peer: Peer) {
        _peers.value = _peers.value + (peer.id to peer)
    }
    
    /**
     * 移除联系人
     */
    fun removePeer(peerId: String) {
        _peers.value = _peers.value - peerId
    }
    
    /**
     * 发送文本消息
     */
    fun sendTextMessage(receiverId: String, content: String) {
        val message = Message(
            type = MessageType.TEXT,
            senderId = currentUser?.id ?: return,
            receiverId = receiverId,
            content = content
        )
        sendMessage(message)
    }
    
    /**
     * 发送图片消息
     */
    fun sendImageMessage(receiverId: String, imagePath: String) {
        val message = Message(
            type = MessageType.IMAGE,
            senderId = currentUser?.id ?: return,
            receiverId = receiverId,
            content = imagePath
        )
        sendMessage(message)
    }
    
    /**
     * 发送文件消息
     */
    fun sendFileMessage(receiverId: String, filePath: String, fileName: String) {
        val message = Message(
            type = MessageType.FILE,
            senderId = currentUser?.id ?: return,
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
            networkManager.sendMessage(message)
            addMessageToHistory(message)
        }
    }
    
    /**
     * 添加消息到历史记录
     */
    private fun addMessageToHistory(message: Message) {
        val chatId = if (message.receiverId == "broadcast") "broadcast" else {
            if (message.senderId == currentUser?.id) message.receiverId else message.senderId
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
        // 实现群聊创建逻辑
        return groupId
    }
    
    /**
     * 设置用户名
     */
    fun updateUsername(newUsername: String) {
        username = newUsername
        networkManager.updateUsername(newUsername)
    }
    
    override fun dispose() {
        scope.cancel()
        networkManager.stop()
    }
}

package com.lanchat.network

import com.google.gson.Gson
import com.lanchat.message.Message
import com.lanchat.message.DiscoveryMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 网络管理器
 * 负责局域网用户发现和消息传输
 */
class NetworkManager {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    
    // UDP 广播相关
    private var udpSocket: DatagramSocket? = null
    private val udpPort = 8888
    private val broadcastAddress = InetAddress.getByName("255.255.255.255")
    
    // TCP 服务端
    private var serverSocket: ServerSocket? = null
    private val tcpPort = 8889
    
    // 客户端连接
    private val connections = ConcurrentHashMap<String, Socket>()
    
    // 发现的新用户流
    private val _peerDiscovered = MutableSharedFlow<Peer>()
    val peerDiscovered: SharedFlow<Peer> = _peerDiscovered.asSharedFlow()
    
    // 接收到的消息流
    private val _messageReceived = MutableSharedFlow<Message>()
    val messageReceived: SharedFlow<Message> = _messageReceived.asSharedFlow()
    
    // 当前用户ID
    var currentUserId: String = UUID.randomUUID().toString()
        private set
    private var currentUsername: String = "Anonymous"
    private var isRunning = false
    
    companion object {
        private const val DISCOVERY_INTERVAL = 5000L
        private const val DISCOVERY_TIMEOUT = 15000L
    }
    
    fun start(username: String) {
        currentUsername = username
        isRunning = true
        
        scope.launch {
            startUdpListener()
            startTcpServer()
            startDiscovery()
        }
    }
    
    private fun startUdpListener() {
        udpSocket = DatagramSocket(udpPort)
        udpSocket?.broadcast = true
        
        scope.launch {
            val buffer = ByteArray(4096)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    
                    val json = String(packet.data, 0, packet.length)
                    val discoveryMsg = gson.fromJson(json, DiscoveryMessage::class.java)
                    
                    if (discoveryMsg.userId != currentUserId) {
                        val peer = Peer(
                            id = discoveryMsg.userId,
                            username = discoveryMsg.username,
                            ipAddress = packet.address.hostAddress,
                            port = discoveryMsg.tcpPort,
                            avatar = discoveryMsg.avatar
                        )
                        _peerDiscovered.emit(peer)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun startTcpServer() {
        serverSocket = ServerSocket(tcpPort)
        
        scope.launch {
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: continue
                    handleClientConnection(clientSocket)
                } catch (e: Exception) {
                    if (isRunning) e.printStackTrace()
                }
            }
        }
    }
    
    private fun handleClientConnection(socket: Socket) {
        scope.launch {
            try {
                val reader = socket.getInputStream().bufferedReader()
                while (isRunning && !socket.isClosed) {
                    val json = reader.readLine() ?: break
                    val message = gson.fromJson(json, Message::class.java)
                    _messageReceived.emit(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket.close()
            }
        }
    }
    
    private fun startDiscovery() {
        scope.launch {
            while (isRunning) {
                try {
                    sendDiscoveryInternal()
                    delay(DISCOVERY_INTERVAL)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private suspend fun sendDiscoveryInternal() {
        try {
            val discoveryMsg = DiscoveryMessage(
                userId = currentUserId,
                username = currentUsername,
                tcpPort = tcpPort
            )
            val json = gson.toJson(discoveryMsg)
            val data = json.toByteArray()
            
            val packet = DatagramPacket(data, data.size, broadcastAddress, udpPort)
            udpSocket?.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun sendMessage(message: Message) {
        val json = gson.toJson(message)
        
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket("127.0.0.1", tcpPort)
                socket.outputStream.write("$json\n".toByteArray())
                socket.outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun updateUsername(newUsername: String) {
        currentUsername = newUsername
    }
    
    fun sendDiscovery() {
        scope.launch {
            sendDiscoveryInternal()
        }
    }
    
    fun stop() {
        isRunning = false
        udpSocket?.close()
        serverSocket?.close()
        connections.values.toList().forEach { it.close() }
        connections.clear()
        scope.cancel()
    }
}

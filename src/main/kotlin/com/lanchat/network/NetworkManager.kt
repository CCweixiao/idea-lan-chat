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
    private var currentUserId: String = UUID.randomUUID().toString()
    private var currentUsername: String = "Anonymous"
    private var isRunning = false
    
    companion object {
        private const val DISCOVERY_INTERVAL = 5000L // 5秒广播一次
        private const val DISCOVERY_TIMEOUT = 15000L // 15秒超时
    }
    
    /**
     * 启动网络服务
     */
    fun start(username: String) {
        currentUsername = username
        isRunning = true
        
        // 获取本机IP
        val localIp = getLocalIpAddress()
        
        scope.launch {
            // 启动UDP广播监听
            startUdpListener()
            
            // 启动TCP服务端
            startTcpServer()
            
            // 启动广播发现
            startDiscovery()
        }
    }
    
    /**
     * 启动UDP监听
     */
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
                    
                    // 忽略自己的广播
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
    
    /**
     * 启动TCP服务端
     */
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
    
    /**
     * 处理客户端连接
     */
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
    
    /**
     * 启动广播发现
     */
    private fun startDiscovery() {
        scope.launch {
            while (isRunning) {
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
                    
                    delay(DISCOVERY_INTERVAL)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(message: Message) {
        val peerIp = message.receiverId // 这里需要从peers获取IP
        val json = gson.toJson(message)
        
        withContext(Dispatchers.IO) {
            try {
                // 这里简化处理，实际需要根据peerId获取对应的IP和端口
                val socket = Socket("127.0.0.1", tcpPort)
                socket.outputStream.write("$json\n".toByteArray())
                socket.outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 发送文件
     */
    suspend fun sendFile(peerId: String, filePath: String) {
        // 实现文件传输逻辑
    }
    
    /**
     * 更新用户名
     */
    fun updateUsername(newUsername: String) {
        currentUsername = newUsername
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
     * 停止网络服务
     */
    fun stop() {
        isRunning = false
        udpSocket?.close()
        serverSocket?.close()
        connections.values.toList().forEach { it.close() }
        connections.clear()
        scope.cancel()
    }
}

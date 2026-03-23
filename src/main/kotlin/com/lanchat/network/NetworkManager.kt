package com.lanchat.network

import com.google.gson.Gson
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.message.DiscoveryMessage
import com.lanchat.util.CryptoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class NetworkManager(
    private val udpPort: Int = 8888,
    private val tcpPort: Int = 8889
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var udpSocket: DatagramSocket? = null
    private val broadcastAddress = InetAddress.getByName("255.255.255.255")

    private var serverSocket: ServerSocket? = null

    private val connections = ConcurrentHashMap<String, Socket>()

    private val _peerDiscovered = MutableSharedFlow<Peer>()
    val peerDiscovered: SharedFlow<Peer> = _peerDiscovered.asSharedFlow()

    private val _messageReceived = MutableSharedFlow<Message>()
    val messageReceived: SharedFlow<Message> = _messageReceived.asSharedFlow()

    var currentUserId: String = UUID.randomUUID().toString()
        private set
    private var currentUsername: String = "Anonymous"
    private var currentAvatarHash: String? = null
    private var isRunning = false
    private var lastLocalIp: String = ""  // 上次检测到的本地 IP，用于检测 IP 变化

    companion object {
        private const val DISCOVERY_INTERVAL = 5000L
        private const val ENCRYPTED_PREFIX = "ENC:"
        private const val FILE_HEADER_PREFIX = "FILE:"
        private val LAN_CHAT_DIR = File(System.getProperty("user.home"), ".lanchat/files")

        fun getLanChatDir(): File {
            if (!LAN_CHAT_DIR.exists()) LAN_CHAT_DIR.mkdirs()
            return LAN_CHAT_DIR
        }
    }

    private fun encryptJson(json: String): String {
        if (!CryptoManager.isEnabled) return json
        return try {
            ENCRYPTED_PREFIX + CryptoManager.encrypt(json)
        } catch (_: Exception) { json }
    }

    private fun decryptJson(data: String): String {
        if (!data.startsWith(ENCRYPTED_PREFIX)) return data
        return try {
            CryptoManager.decrypt(data.removePrefix(ENCRYPTED_PREFIX))
        } catch (_: Exception) { data.removePrefix(ENCRYPTED_PREFIX) }
    }

    /**
     * Start the network manager.
     * @param username display name for discovery
     * @param userId stable persistent user ID; if null, a random one is used
     */
    fun start(username: String, userId: String? = null) {
        currentUsername = username
        if (userId != null) {
            currentUserId = userId
        }
        isRunning = true

        scope.launch { startUdpListener() }
        scope.launch { startTcpServer() }
        scope.launch { startDiscovery() }
        scope.launch { monitorLocalIp() }
    }

    private suspend fun startUdpListener() {
        try {
            udpSocket = DatagramSocket(udpPort).apply { broadcast = true }
        } catch (e: Exception) {
            // Port may already be bound by another IDE instance; try ephemeral port
            try {
                udpSocket = DatagramSocket().apply { broadcast = true }
            } catch (e2: Exception) {
                e2.printStackTrace()
                return
            }
        }

        val buffer = ByteArray(4096)
        while (isRunning) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet) ?: break

                val raw = String(packet.data, 0, packet.length)
                val json = decryptJson(raw)
                val discoveryMsg = gson.fromJson(json, DiscoveryMessage::class.java)

                if (discoveryMsg.userId != currentUserId) {
                    val peer = Peer(
                        id = discoveryMsg.userId,
                        username = discoveryMsg.username,
                        ipAddress = packet.address.hostAddress ?: continue,
                        port = discoveryMsg.tcpPort,
                        avatarHash = discoveryMsg.avatarHash
                    )
                    _peerDiscovered.emit(peer)
                }
            } catch (e: Exception) {
                if (isRunning) delay(1000)
            }
        }
    }

    private suspend fun startTcpServer() {
        try {
            serverSocket = ServerSocket(tcpPort)
        } catch (e: Exception) {
            try {
                serverSocket = ServerSocket(0)
            } catch (e2: Exception) {
                e2.printStackTrace()
                return
            }
        }

        while (isRunning) {
            try {
                val clientSocket = serverSocket?.accept() ?: break
                scope.launch { handleClientConnection(clientSocket) }
            } catch (e: Exception) {
                if (isRunning) delay(1000)
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        try {
            val input = socket.getInputStream().buffered()
            val output = socket.outputStream

            while (isRunning && !socket.isClosed) {
                val lineBytes = readLine(input) ?: break
                val raw = String(lineBytes, Charsets.UTF_8)
                try {
                    if (raw.startsWith(FILE_HEADER_PREFIX)) {
                        val fileSize = raw.removePrefix(FILE_HEADER_PREFIX).toLong()
                        val headerLineBytes = readLine(input) ?: break
                        val headerJson = decryptJson(String(headerLineBytes, Charsets.UTF_8))
                        val message = gson.fromJson(headerJson, Message::class.java)

                        val dir = getLanChatDir()
                        val safeFileName = (message.fileName ?: "file_${System.currentTimeMillis()}")
                            .replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]"), "_")
                        var target = File(dir, safeFileName)
                        if (target.exists()) {
                            val ext = safeFileName.substringAfterLast('.', "")
                            val base = safeFileName.substringBeforeLast('.', safeFileName)
                            target = File(dir, "${base}_${System.currentTimeMillis()}.$ext")
                        }
                        FileOutputStream(target).use { fos ->
                            var remaining = fileSize
                            val buf = ByteArray(8192)
                            while (remaining > 0) {
                                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                val read = input.read(buf, 0, toRead)
                                if (read == -1) break
                                fos.write(buf, 0, read)
                                remaining -= read
                            }
                        }
                        val receivedMsg = message.copy(content = target.absolutePath)
                        _messageReceived.emit(receivedMsg)
                    } else {
                        val json = decryptJson(raw)
                        val message = gson.fromJson(json, Message::class.java)

                        if (message.type == MessageType.PROBE) {
                            val response = Message(
                                type = MessageType.PROBE_RESPONSE,
                                senderId = currentUserId,
                                receiverId = message.senderId,
                                content = "",
                                senderName = currentUsername
                            )
                            val responseJson = encryptJson(gson.toJson(response))
                            output.write("$responseJson\n".toByteArray())
                            output.flush()
                            break
                        }

                        _messageReceived.emit(message)
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun readLine(input: InputStream): ByteArray? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (baos.size() > 0) baos.toByteArray() else null
            if (b == '\n'.code) return baos.toByteArray()
            baos.write(b)
        }
    }

    /**
     * 监听本地 IP 变化，一旦检测到 IP 改变，立即发送多次广播通知其他设备更新
     * 用于处理 WiFi/有线切换、VPN 连接等场景
     */
    private suspend fun monitorLocalIp() {
        lastLocalIp = getLocalIpAddress()
        while (isRunning) {
            delay(10000L) // 每 10 秒检测一次
            try {
                val newIp = getLocalIpAddress()
                if (newIp != lastLocalIp && newIp != "127.0.0.1") {
                    lastLocalIp = newIp
                    // IP 变化后立即连续发送 3 次广播，确保其他设备快速感知
                    repeat(3) {
                        sendDiscoveryInternal()
                        delay(500)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * 获取本地局域网 IP 地址（优先 IPv4，排除回环地址）
     */
    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp || ni.isVirtual) continue
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

    private suspend fun startDiscovery() {
        while (isRunning) {
            try {
                sendDiscoveryInternal()
            } catch (_: Exception) { }
            delay(DISCOVERY_INTERVAL)
        }
    }

    private fun sendDiscoveryInternal() {
        try {
            val discoveryMsg = DiscoveryMessage(
                userId = currentUserId,
                username = currentUsername,
                tcpPort = serverSocket?.localPort ?: tcpPort,
                avatarHash = currentAvatarHash
            )
            val json = gson.toJson(discoveryMsg)
            val encrypted = encryptJson(json)
            val data = encrypted.toByteArray()
            val packet = DatagramPacket(data, data.size, broadcastAddress, udpPort)
            udpSocket?.send(packet)
        } catch (_: Exception) { }
    }

    /**
     * Send a message to a single target via TCP.
     * For IMAGE/FILE messages with fileData, uses the binary file transfer protocol.
     */
    suspend fun sendMessage(message: Message, targetIp: String, targetPort: Int = tcpPort) {
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIp, targetPort), 5000)
                    socket.soTimeout = 30000
                    val out = socket.outputStream.buffered()

                    val hasFilePayload = message.fileData != null && message.type in listOf(
                        MessageType.IMAGE, MessageType.FILE, MessageType.PROFILE_UPDATE
                    )
                    if (hasFilePayload) {
                        val headerMsg = message.copy(content = "")
                        val headerJson = encryptJson(gson.toJson(headerMsg))
                        val data = message.fileData!!
                        val fileSize = data.size.toLong()
                        out.write("$FILE_HEADER_PREFIX$fileSize\n".toByteArray())
                        out.write("$headerJson\n".toByteArray())
                        out.write(data)
                        out.flush()
                    } else {
                        val json = gson.toJson(message)
                        val encrypted = encryptJson(json)
                        out.write("$encrypted\n".toByteArray())
                        out.flush()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * 探测指定IP:端口的用户信息
     * @return 探测结果，成功返回 Peer，失败返回 null
     */
    suspend fun probePeer(targetIp: String, targetPort: Int = tcpPort, timeoutMs: Long = 5000): Peer? {
        return withContext(Dispatchers.IO) {
            try {
                val probeMsg = Message(
                    type = MessageType.PROBE,
                    senderId = currentUserId,
                    receiverId = "",
                    content = "",
                    senderName = currentUsername
                )
                val encrypted = encryptJson(gson.toJson(probeMsg))

                Socket().use { socket ->
                    socket.soTimeout = timeoutMs.toInt()
                    socket.connect(InetSocketAddress(targetIp, targetPort), 3000)

                    socket.outputStream.write("$encrypted\n".toByteArray())
                    socket.outputStream.flush()

                    val reader = socket.getInputStream().bufferedReader()
                    val raw = reader.readLine() ?: return@withContext null
                    val responseJson = decryptJson(raw)

                    val response = gson.fromJson(responseJson, Message::class.java)
                    if (response.type == MessageType.PROBE_RESPONSE) {
                        Peer(
                            id = response.senderId,
                            username = response.senderName ?: "未知用户",
                            ipAddress = targetIp,
                            port = targetPort,
                            isOnline = true
                        )
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Send a message to multiple targets in parallel.
     */
    suspend fun sendToMultiple(message: Message, targets: List<Pair<String, Int>>) {
        coroutineScope {
            targets.forEach { (ip, port) ->
                launch { sendMessage(message, ip, port) }
            }
        }
    }

    fun updateUsername(newUsername: String) {
        currentUsername = newUsername
    }

    fun updateAvatarHash(avatarPath: String?) {
        currentAvatarHash = if (avatarPath != null) {
            try {
                val bytes = java.io.File(avatarPath).readBytes()
                java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
            } catch (_: Exception) { null }
        } else null
    }

    fun updateUserId(newUserId: String) {
        currentUserId = newUserId
    }

    fun sendDiscovery() {
        scope.launch { sendDiscoveryInternal() }
    }

    fun getActualTcpPort(): Int = serverSocket?.localPort ?: tcpPort

    fun stop() {
        isRunning = false
        try { udpSocket?.close() } catch (_: Exception) { }
        try { serverSocket?.close() } catch (_: Exception) { }
        connections.values.forEach { try { it.close() } catch (_: Exception) { } }
        connections.clear()
        scope.cancel()
    }
}

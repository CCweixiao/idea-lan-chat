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

class NetworkManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var udpSocket: DatagramSocket? = null
    private val udpPort = 8888
    private val broadcastAddress = InetAddress.getByName("255.255.255.255")

    private var serverSocket: ServerSocket? = null
    private val tcpPort = 8889

    private val connections = ConcurrentHashMap<String, Socket>()

    private val _peerDiscovered = MutableSharedFlow<Peer>()
    val peerDiscovered: SharedFlow<Peer> = _peerDiscovered.asSharedFlow()

    private val _messageReceived = MutableSharedFlow<Message>()
    val messageReceived: SharedFlow<Message> = _messageReceived.asSharedFlow()

    var currentUserId: String = UUID.randomUUID().toString()
        private set
    private var currentUsername: String = "Anonymous"
    private var isRunning = false

    companion object {
        private const val DISCOVERY_INTERVAL = 5000L
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

                val json = String(packet.data, 0, packet.length)
                val discoveryMsg = gson.fromJson(json, DiscoveryMessage::class.java)

                if (discoveryMsg.userId != currentUserId) {
                    val peer = Peer(
                        id = discoveryMsg.userId,
                        username = discoveryMsg.username,
                        ipAddress = packet.address.hostAddress ?: continue,
                        port = discoveryMsg.tcpPort,
                        avatar = discoveryMsg.avatar
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
            val reader = socket.getInputStream().bufferedReader()
            while (isRunning && !socket.isClosed) {
                val json = reader.readLine() ?: break
                try {
                    val message = gson.fromJson(json, Message::class.java)
                    _messageReceived.emit(message)
                } catch (_: Exception) { }
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
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
                tcpPort = serverSocket?.localPort ?: tcpPort
            )
            val json = gson.toJson(discoveryMsg)
            val data = json.toByteArray()
            val packet = DatagramPacket(data, data.size, broadcastAddress, udpPort)
            udpSocket?.send(packet)
        } catch (_: Exception) { }
    }

    /**
     * Send a message to a single target via TCP.
     */
    suspend fun sendMessage(message: Message, targetIp: String, targetPort: Int = tcpPort) {
        val json = gson.toJson(message)
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIp, targetPort), 3000)
                    socket.outputStream.write("$json\n".toByteArray())
                    socket.outputStream.flush()
                }
            } catch (_: Exception) { }
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

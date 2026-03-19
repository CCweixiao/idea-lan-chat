package com.lanchat.message

/**
 * 发现消息
 * 用于UDP广播发现局域网用户
 */
data class DiscoveryMessage(
    val userId: String,
    val username: String,
    val tcpPort: Int,
    val avatar: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

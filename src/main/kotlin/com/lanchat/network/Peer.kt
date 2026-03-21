package com.lanchat.network

/**
 * 用户节点
 */
data class Peer(
    val id: String,
    val username: String,
    val ipAddress: String,
    val port: Int,
    val avatar: String? = null,
    val signature: String? = null,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

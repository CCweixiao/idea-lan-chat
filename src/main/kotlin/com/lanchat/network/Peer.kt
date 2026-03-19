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
    val signature: String? = null,  // 个人签名
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
}

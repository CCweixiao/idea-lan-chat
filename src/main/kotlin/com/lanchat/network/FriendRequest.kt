package com.lanchat.network

import java.util.UUID

enum class FriendRequestStatus {
    PENDING_SENT,
    PENDING_RECEIVED,
    ACCEPTED,
    REJECTED
}

data class FriendRequest(
    val id: String = UUID.randomUUID().toString(),
    val fromUserId: String,
    val fromUsername: String,
    val fromIp: String,
    val fromPort: Int,
    val toIp: String,
    val toPort: Int = 8889,
    val message: String = "",
    val status: FriendRequestStatus = FriendRequestStatus.PENDING_SENT,
    val timestamp: Long = System.currentTimeMillis()
)

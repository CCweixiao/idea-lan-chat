package com.lanchat.network

import java.util.UUID

enum class GroupRequestType {
    INVITE,
    JOIN_REQUEST
}

enum class GroupRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

data class GroupRequest(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val groupNumber: String = "",
    val groupName: String = "",
    val requesterId: String,
    val requesterName: String,
    val requesterIp: String = "",
    val requesterPort: Int = 8889,
    val targetId: String = "",
    val type: GroupRequestType,
    val status: GroupRequestStatus = GroupRequestStatus.PENDING,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

package com.lanchat.network

import java.util.UUID

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ownerId: String,
    val memberIds: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val avatar: String? = null,
    val groupNumber: String = ""
) {
    fun isOwner(userId: String): Boolean = userId == ownerId
    fun isMember(userId: String): Boolean = memberIds.contains(userId)
    fun getMemberCount(): Int = memberIds.size

    fun addMember(operatorId: String, memberId: String): Boolean {
        if (!isOwner(operatorId)) return false
        if (memberIds.contains(memberId)) return false
        memberIds.add(memberId)
        return true
    }

    fun removeMember(operatorId: String, memberId: String): Boolean {
        if (!isOwner(operatorId)) return false
        if (memberId == ownerId) return false
        return memberIds.remove(memberId)
    }
}

package com.lanchat.network

import java.util.UUID

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ownerId: String,
    val memberIds: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val avatar: String? = null,
    val groupNumber: String = "",
    val mutedMembers: MutableMap<String, Long> = mutableMapOf(),
    val globalMute: Boolean = false
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

    /**
     * @return true if the user is currently muted (owner is never muted)
     */
    fun isMuted(userId: String): Boolean {
        if (isOwner(userId)) return false
        if (globalMute) return true
        val unmuteAt = mutedMembers[userId] ?: return false
        if (unmuteAt == -1L) return true
        if (System.currentTimeMillis() >= unmuteAt) {
            mutedMembers.remove(userId)
            return false
        }
        return true
    }

    fun getMuteExpiry(userId: String): Long? = mutedMembers[userId]
}

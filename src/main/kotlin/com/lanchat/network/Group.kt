package com.lanchat.network

import java.util.UUID

/**
 * 群组实体
 */
data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ownerId: String,           // 群主ID
    val memberIds: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val avatar: String? = null
) {
    /**
     * 判断用户是否是群主
     */
    fun isOwner(userId: String): Boolean {
        return userId == ownerId
    }
    
    /**
     * 判断用户是否在群组中
     */
    fun isMember(userId: String): Boolean {
        return memberIds.contains(userId)
    }
    
    /**
     * 添加成员（只有群主可以操作）
     */
    fun addMember(operatorId: String, memberId: String): Boolean {
        if (!isOwner(operatorId)) return false
        if (memberIds.contains(memberId)) return false
        memberIds.add(memberId)
        return true
    }
    
    /**
     * 移除成员（只有群主可以操作）
     */
    fun removeMember(operatorId: String, memberId: String): Boolean {
        if (!isOwner(operatorId)) return false
        return memberIds.remove(memberId)
    }
    
    /**
     * 获取成员数量
     */
    fun getMemberCount(): Int = memberIds.size
}

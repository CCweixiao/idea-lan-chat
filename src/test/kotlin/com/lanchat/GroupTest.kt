package com.lanchat

import com.lanchat.network.Group
import com.lanchat.network.Peer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 群组功能测试
 */
class GroupTest {
    
    @Test
    fun `test create group`() {
        val group = Group(
            name = "测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1", "member_2")
        )
        
        assertNotNull(group.id)
        assertEquals("测试群", group.name)
        assertEquals("owner_1", group.ownerId)
        assertEquals(3, group.memberIds.size)
    }
    
    @Test
    fun `test group owner permission`() {
        val group = Group(
            name = "权限测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1")
        )
        
        // 群主判断
        assertTrue(group.isOwner("owner_1"))
        assertFalse(group.isOwner("other_user"))
    }
    
    @Test
    fun `test add member by owner`() {
        val group = Group(
            name = "添加成员测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1")
        )
        
        // 群主添加成员
        val result = group.addMember("owner_1", "member_1")
        assertTrue(result)
        assertTrue(group.memberIds.contains("member_1"))
        assertEquals(2, group.memberIds.size)
    }
    
    @Test
    fun `test add member by non-owner should fail`() {
        val group = Group(
            name = "权限测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1")
        )
        
        // 非群主尝试添加成员
        val result = group.addMember("member_1", "member_2")
        assertFalse(result)
        assertFalse(group.memberIds.contains("member_2"))
        assertEquals(2, group.memberIds.size)
    }
    
    @Test
    fun `test remove member by owner`() {
        val group = Group(
            name = "移除成员测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1", "member_2")
        )
        
        // 群主移除成员
        val result = group.removeMember("owner_1", "member_1")
        assertTrue(result)
        assertFalse(group.memberIds.contains("member_1"))
        assertEquals(2, group.memberIds.size)
    }
    
    @Test
    fun `test remove member by non-owner should fail`() {
        val group = Group(
            name = "权限测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1", "member_2")
        )
        
        // 非群主尝试移除成员
        val result = group.removeMember("member_1", "member_2")
        assertFalse(result)
        assertTrue(group.memberIds.contains("member_2"))
        assertEquals(3, group.memberIds.size)
    }
    
    @Test
    fun `test add duplicate member`() {
        val group = Group(
            name = "重复成员测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1")
        )
        
        // 添加已存在的成员
        val result = group.addMember("owner_1", "member_1")
        assertFalse(result) // 应该返回false，不允许重复添加
        assertEquals(2, group.memberIds.size)
    }
    
    @Test
    fun `test remove owner should fail`() {
        val group = Group(
            name = "移除群主测试群",
            ownerId = "owner_1",
            memberIds = mutableListOf("owner_1", "member_1")
        )
        
        // 尝试移除群主
        val result = group.removeMember("owner_1", "owner_1")
        assertFalse(result)
        assertTrue(group.memberIds.contains("owner_1"))
    }
}

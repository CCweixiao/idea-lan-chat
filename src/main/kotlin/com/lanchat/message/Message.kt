package com.lanchat.message

/**
 * 消息类型
 */
enum class MessageType {
    TEXT,           // 文本消息
    IMAGE,          // 图片消息
    FILE,           // 文件消息
    SYSTEM,         // 系统消息
    TYPING,         // 正在输入
    READ,           // 已读回执
    GROUP_CHAT,     // 群聊消息
    MENTION_MEMBER, // @成员消息
    MENTION_ALL     // @全体成员
}

/**
 * 消息实体
 */
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: MessageType,
    val senderId: String,
    val receiverId: String,        // 可以是用户ID或群组ID
    val content: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    
    // @功能相关
    val mentionedUserIds: List<String> = emptyList(),  // 被@的用户ID列表
    val mentionAll: Boolean = false,                    // 是否@全体成员
    val groupId: String? = null,                        // 群组ID（群聊消息）
    val senderName: String? = null                      // 发送者昵称（用于显示）
) {
    /**
     * 判断是否是自己发送的消息
     */
    fun isSentByMe(myUserId: String): Boolean {
        return senderId == myUserId
    }
    
    /**
     * 判断是否被@了
     */
    fun isMentioned(userId: String): Boolean {
        return mentionAll || mentionedUserIds.contains(userId)
    }
    
    /**
     * 获取格式化的时间
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
    
    /**
     * 获取格式化的文件大小
     */
    fun getFormattedFileSize(): String {
        if (fileSize == null) return ""
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> "${fileSize / (1024 * 1024)} MB"
        }
    }
    
    /**
     * 获取@文本
     */
    fun getMentionText(): String {
        if (mentionAll) return "@全体成员 "
        if (mentionedUserIds.isEmpty()) return ""
        return mentionedUserIds.joinToString(" ") { "@$it " }
    }
}

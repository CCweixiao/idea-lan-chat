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
    READ            // 已读回执
}

/**
 * 消息实体
 */
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: MessageType,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    /**
     * 判断是否是自己发送的消息
     */
    fun isSentByMe(myUserId: String): Boolean {
        return senderId == myUserId
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
}

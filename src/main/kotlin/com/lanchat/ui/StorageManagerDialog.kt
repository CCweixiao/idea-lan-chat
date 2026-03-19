package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * 存储空间管理对话框
 */
class StorageManagerDialog(private val project: Project) : JDialog() {

    private val service = LanChatService.getInstance()
    private val infoLabel = JLabel()
    private val statsArea = JTextArea(8, 30)

    init {
        title = "存储空间管理"
        isModal = true
        setupUI()
        updateInfo()
        preferredSize = Dimension(450, 400)
        pack()
        setLocationRelativeTo(null)
    }

    private fun setupUI() {
        contentPane.layout = BorderLayout(0, 12)
        (contentPane as JPanel).border = JBUI.Borders.empty(16)

        // 标题和存储信息
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel("存储空间管理").apply {
                font = Font("Microsoft YaHei", Font.BOLD, 16)
            }, BorderLayout.WEST)
        }
        contentPane.add(headerPanel, BorderLayout.NORTH)

        // 统计信息
        val statsPanel = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            
            statsArea.apply {
                isEditable = false
                font = Font("Microsoft YaHei", Font.PLAIN, 13)
                background = JBColor(Color(245, 245, 245), Color(45, 45, 45))
                border = JBUI.Borders.empty(12)
                lineWrap = true
                wrapStyleWord = true
            }
            add(JScrollPane(statsArea).apply {
                border = BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1)
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }, BorderLayout.CENTER)
        }
        contentPane.add(statsPanel, BorderLayout.CENTER)

        // 操作按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            
            add(JButton("清空所有聊天记录").apply {
                foreground = Color(220, 50, 50)
                font = Font("Microsoft YaHei", Font.PLAIN, 13)
                addActionListener {
                    val confirm = JOptionPane.showConfirmDialog(
                        this@StorageManagerDialog,
                        "确定要清空所有聊天记录吗？\n此操作不可恢复！",
                        "确认清空",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    )
                    if (confirm == JOptionPane.YES_OPTION) {
                        val doubleConfirm = JOptionPane.showConfirmDialog(
                            this@StorageManagerDialog,
                            "再次确认：清空所有聊天记录？",
                            "二次确认",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                        )
                        if (doubleConfirm == JOptionPane.YES_OPTION) {
                            service.clearAllChatHistory()
                            updateInfo()
                            JOptionPane.showMessageDialog(
                                this@StorageManagerDialog,
                                "所有聊天记录已清空",
                                "完成",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                    }
                }
            })
            
            add(JButton("刷新").apply {
                font = Font("Microsoft YaHei", Font.PLAIN, 13)
                addActionListener { updateInfo() }
            })
            
            add(JButton("关闭").apply {
                font = Font("Microsoft YaHei", Font.PLAIN, 13)
                addActionListener { dispose() }
            })
        }
        contentPane.add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun updateInfo() {
        val info = service.getStorageInfo()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        val sb = StringBuilder()
        sb.appendLine("📊 存储空间使用情况")
        sb.appendLine()
        sb.appendLine("📁 数据库大小: ${info.formattedSize}")
        sb.appendLine("💬 总消息数: ${info.totalMessages} 条")
        sb.appendLine("🖼️ 图片消息: ${info.imageCount} 条")
        sb.appendLine("📎 文件消息: ${info.fileCount} 条")
        sb.appendLine("👥 聊天会话: ${info.totalChats} 个")
        sb.appendLine()
        sb.appendLine("💡 提示：清空聊天记录可释放存储空间")
        
        statsArea.text = sb.toString()
    }
}

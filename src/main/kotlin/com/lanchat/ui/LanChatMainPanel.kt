package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * LAN Chat 主面板
 * 包含联系人列表和聊天窗口
 */
class LanChatMainPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    private val contactListPanel = ContactListPanel(project)
    private val chatPanel = ChatPanel(project)
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        // 创建分割器
        val splitter = Splitter(false, 0.25f).apply {
            firstComponent = contactListPanel
            secondComponent = chatPanel
            dividerWidth = 1
        }
        
        add(splitter, BorderLayout.CENTER)
        
        // 设置最小尺寸
        minimumSize = Dimension(400, 300)
        
        // 设置边框
        border = JBUI.Borders.empty()
    }
}

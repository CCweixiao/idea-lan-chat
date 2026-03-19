package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JSplitPane
import javax.swing.JPanel

/**
 * LAN Chat 主面板
 */
class LanChatMainPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = LanChatService.getInstance()

    init {
        setupUI()
    }

    private fun setupUI() {
        background = JBColor.PanelBackground

        // 创建聊天面板
        val chatPanel = ChatPanel(project)

        // 创建联系人列表，传入回调函数
        val contactListPanel = ContactListPanel(project) { chatItem ->
            when (chatItem) {
                is ChatItem.PeerItem -> chatPanel.setCurrentPeer(chatItem.peer)
                is ChatItem.GroupItem -> chatPanel.setCurrentGroup(chatItem.group)
                null -> chatPanel.clearChat()
            }
        }

        // 创建分割器
        val splitter = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contactListPanel, chatPanel).apply {
            resizeWeight = 0.25
            dividerSize = 1
            isOneTouchExpandable = false
        }

        add(splitter, BorderLayout.CENTER)

        // 设置最小尺寸
        minimumSize = Dimension(500, 400)
        border = JBUI.Borders.empty()
    }
}

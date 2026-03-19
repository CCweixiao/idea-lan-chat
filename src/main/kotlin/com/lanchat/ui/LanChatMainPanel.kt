package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.*

/**
 * LAN Chat 主面板
 */
class LanChatMainPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    private lateinit var splitter: JSplitPane
    private lateinit var contactListPanel: ContactListPanel
    private lateinit var chatPanel: ChatPanel
    private var isContactListHidden = false
    private var isChatMinimized = false
    private var lastDividerLocation = 200
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        background = JBColor.PanelBackground
        
        // 创建聊天面板
        chatPanel = ChatPanel(project)
        
        // 创建联系人列表，传入回调函数
        contactListPanel = ContactListPanel(project) { chatItem ->
            when (chatItem) {
                is ChatItem.PeerItem -> chatPanel.setCurrentPeer(chatItem.peer)
                is ChatItem.GroupItem -> chatPanel.setCurrentGroup(chatItem.group)
                null -> chatPanel.clearChat()
            }
        }
        
        // 创建分割器
        splitter = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contactListPanel, chatPanel).apply {
            resizeWeight = 0.25
            dividerSize = 1
            isOneTouchExpandable = false
        }
        
        // 顶部工具栏
        val toolBar = createToolBar()
        
        add(toolBar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        
        // 设置最小尺寸
        minimumSize = Dimension(500, 400)
        border = JBUI.Borders.empty()
    }
    
    private fun createToolBar(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(4, 8)
            
            // 左侧：标题
            val titleLabel = JLabel("LAN Chat").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.WEST)
            
            // 右侧：功能按钮
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                
                // 隐藏/显示联系人列表
                add(createToolBarButton("隐藏联系人", AllIcons.Actions.Collapseall) {
                    toggleContactList()
                })
                
                // 缩小聊天框
                add(createToolBarButton("缩小聊天框", AllIcons.General.HideToolWindow) {
                    toggleChatMinimize()
                })
            }
            add(buttonPanel, BorderLayout.EAST)
        }
    }
    
    private fun createToolBarButton(tooltip: String, icon: Icon, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            margin = Insets(4, 4, 4, 4)
            cursor = Cursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }
    }
    
    /**
     * 切换联系人列表显示/隐藏
     */
    private fun toggleContactList() {
        if (isContactListHidden) {
            // 显示联系人列表
            splitter.leftComponent = contactListPanel
            splitter.dividerLocation = lastDividerLocation
            isContactListHidden = false
        } else {
            // 隐藏联系人列表
            lastDividerLocation = splitter.dividerLocation
            splitter.leftComponent = null
            splitter.dividerLocation = 0
            isContactListHidden = true
        }
    }
    
    /**
     * 切换聊天框最小化
     */
    private fun toggleChatMinimize() {
        if (isChatMinimized) {
            // 恢复聊天框
            chatPanel.isVisible = true
            isChatMinimized = false
        } else {
            // 最小化聊天框（显示为一个小的提示栏）
            chatPanel.isVisible = false
            isChatMinimized = true
        }
    }
}

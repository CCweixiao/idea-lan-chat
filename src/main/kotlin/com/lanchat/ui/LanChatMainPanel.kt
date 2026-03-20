package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.ui.settings.LanChatSettings
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * LAN Chat 主面板
 */
class LanChatMainPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    private lateinit var contactListPanel: ContactListPanel
    private lateinit var chatPanel: ChatPanel
    private lateinit var splitter: JSplitPane
    private lateinit var centerPanel: JPanel
    private lateinit var miniBar: MiniChatBar
    
    private var isContactListHidden = false
    private var isChatMinimized = false
    private var lastDividerLocation = 200
    
    init {
        // 加载保存的主题设置
        val settings = LanChatSettings()
        settings.loadState(settings.state)
        setupUI()
    }
    
    private fun setupUI() {
        background = JBColor.PanelBackground
        
        // 创建聊天面板
        chatPanel = ChatPanel(project)
        
        // 创建联系人列表
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
        
        // 迷你聊天栏（最小化状态）
        miniBar = MiniChatBar(project)
        
        // 中间区域：CardLayout 切换完整视图和迷你视图
        centerPanel = JPanel(CardLayout()).apply {
            add(splitter, "full")
            add(miniBar, "mini")
        }
        
        // 顶部工具栏
        val toolBar = createToolBar()
        
        add(toolBar, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        
        minimumSize = Dimension(500, 400)
        border = JBUI.Borders.empty()
    }
    
    private fun createToolBar(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(4, 8)
            
            val titleLabel = JLabel("LAN Chat").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }
            add(titleLabel, BorderLayout.WEST)
            
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                
                add(createToolBarButton("隐藏联系人", AllIcons.Actions.Collapseall) {
                    toggleContactList()
                })
                
                add(createToolBarButton(if (isChatMinimized) "展开聊天" else "最小化聊天", 
                    if (isChatMinimized) AllIcons.General.ExpandComponent else AllIcons.General.HideToolWindow) {
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
    
    private fun toggleContactList() {
        if (isContactListHidden) {
            splitter.leftComponent = contactListPanel
            if (lastDividerLocation > 0) {
                SwingUtilities.invokeLater { splitter.dividerLocation = lastDividerLocation }
            }
            isContactListHidden = false
        } else {
            lastDividerLocation = splitter.dividerLocation
            // 使用 setDividerLocation(0.0) 而非设 null，避免 JSplitPane bug
            SwingUtilities.invokeLater {
                splitter.leftComponent = null
                splitter.dividerLocation = 0
                splitter.resizeWeight = 1.0
            }
            isContactListHidden = true
        }
    }
    
    private fun toggleChatMinimize() {
        if (isChatMinimized) {
            // 展开聊天
            (centerPanel.layout as CardLayout).show(centerPanel, "full")
            isChatMinimized = false
        } else {
            // 最小化为迷你栏
            miniBar.refreshUnreadCounts()
            (centerPanel.layout as CardLayout).show(centerPanel, "mini")
            isChatMinimized = true
        }
    }
    
    /**
     * 迷你聊天栏 - 最小化状态下的简洁显示
     */
    inner class MiniChatBar(private val project: Project) : JPanel(BorderLayout()) {
        
        init {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(8, 12)
            
            // 点击展开
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        toggleChatMinimize()
                    }
                }
            })
        }
        
        fun refreshUnreadCounts() {
            removeAll()
            
            val peerUnread = service.unreadCounts.value.values.sum()
            val currentChat = chatPanel.getCurrentChatName()
            
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                
                // 左侧：当前聊天名
                val titleLabel = JLabel(currentChat ?: "LAN Chat").apply {
                    font = font.deriveFont(Font.BOLD, 13f)
                }
                add(titleLabel, BorderLayout.WEST)
                
                // 右侧：未读总数 + 提示
                val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    
                    if (peerUnread > 0) {
                        add(JLabel("${peerUnread} 条未读").apply {
                            foreground = JBColor.GRAY
                            font = font.deriveFont(Font.PLAIN, 11f)
                        })
                    }
                    
                    add(JLabel("双击展开").apply {
                        foreground = JBColor(Color(180, 180, 180), Color(100, 100, 100))
                        font = font.deriveFont(Font.PLAIN, 10f)
                    })
                }
                add(rightPanel, BorderLayout.EAST)
            }
            
            add(infoPanel, BorderLayout.CENTER)
            
            revalidate()
            repaint()
        }
    }
}

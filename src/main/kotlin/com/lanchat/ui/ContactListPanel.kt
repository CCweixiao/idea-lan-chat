package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 联系人列表面板
 */
class ContactListPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val listModel = DefaultListModel<Peer>()
    private val contactList = JBList(listModel)
    private var selectedPeer: Peer? = null
    
    init {
        setupUI()
        observePeers()
    }
    
    private fun setupUI() {
        // 顶部工具栏
        val toolbar = JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(8)
            
            add(JLabel("联系人").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }, BorderLayout.WEST)
            
            add(createToolbarButtons(), BorderLayout.EAST)
        }
        
        add(toolbar, BorderLayout.NORTH)
        
        // 联系人列表
        contactList.cellRenderer = ContactListCellRenderer()
        contactList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        contactList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                selectedPeer = contactList.selectedValue
                // 通知聊天面板切换对话
                selectedPeer?.let {
                    // 切换聊天对象
                }
            }
        }
        
        // 双击打开聊天
        contactList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    // 打开聊天窗口
                }
            }
        })
        
        val scrollPane = JBScrollPane(contactList).apply {
            border = JBUI.Borders.empty()
        }
        
        add(scrollPane, BorderLayout.CENTER)
        
        // 底部状态栏
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.emptyTop(4)
            add(JLabel("在线: ${listModel.size()} 人").apply {
                foreground = JBColor.GRAY
            })
        }
        
        add(statusBar, BorderLayout.SOUTH)
    }
    
    private fun createToolbarButtons(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            // 创建群聊按钮
            add(JButton(AllIcons.Actions.AddMulticaret).apply {
                toolTipText = "创建群聊"
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener {
                    showCreateGroupDialog()
                }
            })
            
            // 刷新按钮
            add(JButton(AllIcons.Actions.Refresh).apply {
                toolTipText = "刷新联系人"
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener {
                    // 刷新联系人列表
                }
            })
        }
    }
    
    private fun showCreateGroupDialog() {
        val availablePeers = listModel.elements().toList()
        val dialog = CreateGroupDialog(project, availablePeers)
        if (dialog.showAndGet()) {
            val selectedPeers = dialog.selectedPeers
            val groupName = dialog.groupName
            // 创建群聊
            service.createGroup(groupName, selectedPeers.map { it.id })
        }
    }
    
    private fun observePeers() {
        scope.launch {
            service.peers.collectLatest { peers ->
                SwingUtilities.invokeLater {
                    listModel.clear()
                    peers.values.sortedByDescending { it.isOnline }.forEach { peer ->
                        listModel.addElement(peer)
                    }
                }
            }
        }
    }
    
    /**
     * 联系人列表单元格渲染器
     */
    private class ContactListCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val statusLabel = JLabel()
        private val avatarLabel = JLabel()
        
        init {
            border = JBUI.Borders.empty(8, 12)
            background = JBColor.PanelBackground
            
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(nameLabel.apply {
                    font = font.deriveFont(Font.PLAIN, 13f)
                }, BorderLayout.NORTH)
                add(statusLabel.apply {
                    font = font.deriveFont(Font.PLAIN, 11f)
                    foreground = JBColor.GRAY
                }, BorderLayout.SOUTH)
            }
            
            add(avatarLabel, BorderLayout.WEST)
            add(infoPanel, BorderLayout.CENTER)
        }
        
        override fun getListCellRendererComponent(
            list: JList<out Peer>,
            peer: Peer,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): JPanel {
            nameLabel.text = peer.username
            statusLabel.text = if (peer.isOnline) "在线" else "离线"
            
            // 头像
            avatarLabel.text = peer.username.firstOrNull()?.uppercase() ?: "?"
            avatarLabel.font = avatarLabel.font.deriveFont(Font.BOLD, 16f)
            avatarLabel.horizontalAlignment = SwingConstants.CENTER
            avatarLabel.preferredSize = Dimension(32, 32)
            
            // 选中状态
            background = if (isSelected) JBColor(LightGray, DarkGray) else JBColor.PanelBackground
            
            return this
        }
        
        companion object {
            private val LightGray = Color(230, 230, 230)
            private val DarkGray = Color(70, 70, 70)
        }
    }
}

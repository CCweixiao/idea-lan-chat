package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
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
 * 联系人列表面板 - 仿微信风格
 */
class ContactListPanel(private val project: Project, private val onPeerSelected: (Peer?) -> Unit) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val listModel = DefaultListModel<Peer>()
    private val contactList = JList(listModel)
    private var selectedPeer: Peer? = null
    private val statusLabel = JLabel()
    
    init {
        setupUI()
        observePeers()
    }
    
    private fun setupUI() {
        background = JBColor.PanelBackground
        
        // 顶部标题栏
        val header = JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(12, 12, 8, 12)
            
            // 左侧标题
            val titlePanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                
                val titleLabel = JLabel("联系人").apply {
                    font = Font("Microsoft YaHei", Font.BOLD, 16)
                }
                add(titleLabel, BorderLayout.NORTH)
                
                // 本机IP
                val ipPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(JLabel("本机: ").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 11)
                        foreground = JBColor.GRAY
                    })
                    add(JLabel(service.localIp).apply {
                        font = Font("Microsoft YaHei", Font.BOLD, 11)
                        foreground = JBColor(Color(0, 122, 255), Color(100, 150, 255))
                    })
                }
                add(ipPanel, BorderLayout.SOUTH)
            }
            add(titlePanel, BorderLayout.WEST)
            
            // 右侧按钮
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                
                // 刷新按钮
                add(createIconButton(AllIcons.Actions.Refresh, "刷新") {
                    service.refreshPeers()
                })
                
                // 添加联系人
                add(createIconButton(AllIcons.General.Add, "添加联系人") {
                    showAddContactDialog()
                })
                
                // 创建群聊
                add(createIconButton(AllIcons.Actions.AddMulticaret, "创建群聊") {
                    showCreateGroupDialog()
                })
            }
            add(buttonPanel, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)
        
        // 联系人列表
        contactList.cellRenderer = ContactCellRenderer()
        contactList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        contactList.background = JBColor.PanelBackground
        contactList.border = JBUI.Borders.empty()
        
        contactList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                selectedPeer = contactList.selectedValue
                onPeerSelected(selectedPeer)
            }
        }
        
        contactList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    // 双击打开聊天
                }
            }
        })
        
        val scrollPane = JScrollPane(contactList).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        add(scrollPane, BorderLayout.CENTER)
        
        // 底部状态栏
        val footer = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(4, 12)
            
            statusLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            statusLabel.foreground = JBColor.GRAY
            add(statusLabel)
        }
        add(footer, BorderLayout.SOUTH)
        
        updateStatus()
    }
    
    private fun createIconButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            margin = Insets(4, 4, 4, 4)
            cursor = Cursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }
    }
    
    private fun showAddContactDialog() {
        val dialog = AddContactDialog(project)
        if (dialog.showAndGet()) {
            if (dialog.isAddSelf) {
                // 添加自己
                val selfPeer = Peer(
                    id = service.currentUser?.id ?: "self",
                    username = "${service.username} (自己)",
                    ipAddress = service.localIp,
                    port = 8889,
                    isOnline = true
                )
                service.addManualPeer(selfPeer.ipAddress, selfPeer.port, selfPeer.username)
            } else {
                dialog.selectedPeer?.let { peer ->
                    service.addManualPeer(peer.ipAddress, peer.port, peer.username)
                }
            }
        }
    }
    
    private fun showCreateGroupDialog() {
        val availablePeers = listModel.elements().toList()
        if (availablePeers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "暂无联系人，请先添加联系人", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        
        val dialog = CreateGroupDialog(project, availablePeers)
        if (dialog.showAndGet()) {
            val selectedPeers = dialog.selectedPeers
            val groupName = dialog.groupName
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
                    updateStatus()
                }
            }
        }
    }
    
    private fun updateStatus() {
        statusLabel.text = "在线: ${listModel.size()} 人"
    }
    
    /**
     * 联系人单元格渲染器 - 仿微信风格
     */
    private class ContactCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        private val statusDot = JLabel()
        
        init {
            isOpaque = true
            border = JBUI.Borders.empty(10, 16)
            
            // 头像区域
            statusDot.preferredSize = Dimension(40, 40)
            statusDot.horizontalAlignment = SwingConstants.CENTER
            statusDot.font = Font("Microsoft YaHei", Font.BOLD, 16)
            add(statusDot, BorderLayout.WEST)
            
            // 信息区域
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(12)
                
                nameLabel.font = Font("Microsoft YaHei", Font.PLAIN, 14)
                add(nameLabel, BorderLayout.CENTER)
                
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
                ipLabel.foreground = JBColor.GRAY
                add(ipLabel, BorderLayout.SOUTH)
            }
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
            ipLabel.text = "${peer.ipAddress}:${peer.port}"
            
            // 状态点
            if (peer.isOnline) {
                statusDot.text = "●"
                statusDot.foreground = Color(76, 175, 80)
            } else {
                statusDot.text = "○"
                statusDot.foreground = JBColor.GRAY
            }
            
            // 选中状态
            background = if (isSelected) {
                JBColor(Color(237, 237, 237), Color(60, 60, 60))
            } else {
                JBColor.PanelBackground
            }
            
            return this
        }
    }
}

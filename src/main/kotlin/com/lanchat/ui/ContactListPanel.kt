package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
class ContactListPanel(private val project: Project, private val onPeerSelected: (Peer?) -> Unit) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val listModel = DefaultListModel<Peer>()
    private val contactList = JBList(listModel)
    private var selectedPeer: Peer? = null
    private val statusLabel = JLabel()
    
    init {
        setupUI()
        observePeers()
    }
    
    private fun setupUI() {
        // 顶部工具栏
        val toolbar = JPanel(BorderLayout())
        toolbar.background = JBColor.PanelBackground
        toolbar.border = JBUI.Borders.empty(8)
        
        // 标题
        val titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        
        val titleLabel = JLabel("联系人")
        titleLabel.font = Font("Microsoft YaHei", Font.BOLD, 14)
        titlePanel.add(titleLabel, BorderLayout.NORTH)
        
        // 显示本机IP
        val ipLabel = JLabel("本机: ${service.localIp}")
        ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        ipLabel.foreground = JBColor.GRAY
        titlePanel.add(ipLabel, BorderLayout.SOUTH)
        
        toolbar.add(titlePanel, BorderLayout.WEST)
        toolbar.add(createToolbarButtons(), BorderLayout.EAST)
        
        add(toolbar, BorderLayout.NORTH)
        
        // 联系人列表
        contactList.cellRenderer = ContactListCellRenderer()
        contactList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        contactList.background = JBColor.PanelBackground
        
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
        
        val scrollPane = JBScrollPane(contactList)
        scrollPane.border = JBUI.Borders.empty()
        add(scrollPane, BorderLayout.CENTER)
        
        // 底部状态栏
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
        statusBar.background = JBColor.PanelBackground
        statusBar.border = JBUI.Borders.empty(4, 8)
        
        statusLabel.text = "在线: 0 人"
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        statusBar.add(statusLabel)
        
        add(statusBar, BorderLayout.SOUTH)
    }
    
    private fun createToolbarButtons(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            isOpaque = false
            
            // 创建群聊按钮
            add(JButton(AllIcons.Actions.AddMulticaret).apply {
                toolTipText = "创建群聊"
                isBorderPainted = false
                isContentAreaFilled = false
                margin = Insets(4, 4, 4, 4)
                addActionListener {
                    showCreateGroupDialog()
                }
            })
            
            // 添加联系人按钮
            add(JButton(AllIcons.General.Add).apply {
                toolTipText = "添加联系人"
                isBorderPainted = false
                isContentAreaFilled = false
                margin = Insets(4, 4, 4, 4)
                addActionListener {
                    showAddContactDialog()
                }
            })
            
            // 刷新按钮
            add(JButton(AllIcons.Actions.Refresh).apply {
                toolTipText = "刷新联系人"
                isBorderPainted = false
                isContentAreaFilled = false
                margin = Insets(4, 4, 4, 4)
                addActionListener {
                    service.refreshPeers()
                }
            })
        }
    }
    
    private fun showAddContactDialog() {
        val ipField = JTextField(15)
        val portField = JTextField("8889", 6)
        val nameField = JTextField(15)
        
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(4, 4, 4, 4)
        
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("IP地址:"), gbc)
        gbc.gridx = 1
        panel.add(ipField, gbc)
        
        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("端口:"), gbc)
        gbc.gridx = 1
        panel.add(portField, gbc)
        
        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JLabel("昵称:"), gbc)
        gbc.gridx = 1
        panel.add(nameField, gbc)
        
        val result = Messages.showOkCancelDialog(
            panel,
            "添加联系人",
            "LAN Chat",
            "确定",
            "取消",
            AllIcons.General.Add
        )
        
        if (result == Messages.OK) {
            val ip = ipField.text.trim()
            val port = portField.text.trim().toIntOrNull() ?: 8889
            val name = nameField.text.trim().ifEmpty { "匿名用户" }
            
            if (ip.isNotEmpty()) {
                service.addManualPeer(ip, port, name)
            }
        }
    }
    
    private fun showCreateGroupDialog() {
        val availablePeers = listModel.elements().toList()
        if (availablePeers.isEmpty()) {
            Messages.showInfoMessage("暂无联系人，请先添加联系人", "提示")
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
                    statusLabel.text = "在线: ${peers.size} 人"
                }
            }
        }
    }
    
    /**
     * 联系人列表单元格渲染器
     */
    private class ContactListCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        private val statusIcon = JLabel()
        
        init {
            border = JBUI.Borders.empty(8, 12)
            background = JBColor.PanelBackground
            
            val infoPanel = JPanel(BorderLayout())
            infoPanel.isOpaque = false
            
            nameLabel.font = Font("Microsoft YaHei", Font.PLAIN, 13)
            infoPanel.add(nameLabel, BorderLayout.NORTH)
            
            ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
            ipLabel.foreground = JBColor.GRAY
            infoPanel.add(ipLabel, BorderLayout.SOUTH)
            
            statusIcon.horizontalAlignment = SwingConstants.CENTER
            statusIcon.preferredSize = Dimension(32, 32)
            
            add(statusIcon, BorderLayout.WEST)
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
            
            // 状态图标
            if (peer.isOnline) {
                statusIcon.text = "●"
                statusIcon.foreground = Color(76, 175, 80)
            } else {
                statusIcon.text = "○"
                statusIcon.foreground = JBColor.GRAY
            }
            
            statusIcon.font = Font("Microsoft YaHei", Font.BOLD, 20)
            
            // 选中状态
            background = if (isSelected) 
                JBColor(Color(230, 230, 230), Color(70, 70, 70))
            else 
                JBColor.PanelBackground
            
            return this
        }
    }
}

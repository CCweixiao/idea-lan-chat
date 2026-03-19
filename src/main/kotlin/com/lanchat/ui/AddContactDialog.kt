package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Peer
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * 添加联系人对话框
 */
class AddContactDialog(private val project: Project) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    
    // 搜索框
    private val searchField = JTextField(20)
    
    // IP输入框
    private val ipField = JTextField(15)
    private val portField = JTextField("8889", 6)
    private val nameField = JTextField(15)
    
    // 搜索结果列表
    private val resultListModel = DefaultListModel<Peer>()
    private val resultList = JList(resultListModel)
    
    // 当前选中的联系人
    var selectedPeer: Peer? = null
        private set
    
    // 是否添加自己
    var isAddSelf = false
        private set
    
    init {
        title = "添加联系人"
        init()
        setupSearch()
    }
    
    private fun setupSearch() {
        // 搜索框监听
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                searchContacts()
            }
        })
        
        // 设置列表渲染器
        resultList.cellRenderer = PeerListCellRenderer()
        
        // 初始加载已发现的联系人
        refreshDiscoveredPeers()
    }
    
    private fun searchContacts() {
        val query = searchField.text.trim().lowercase()
        resultListModel.clear()
        
        service.peers.value.values
            .filter { peer ->
                query.isEmpty() || 
                peer.username.lowercase().contains(query) ||
                peer.ipAddress.contains(query)
            }
            .forEach { peer ->
                resultListModel.addElement(peer)
            }
    }
    
    private fun refreshDiscoveredPeers() {
        resultListModel.clear()
        service.peers.value.values.forEach { peer ->
            resultListModel.addElement(peer)
        }
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(550, 450)
            
            // 顶部：搜索区域
            val searchPanel = JPanel(BorderLayout(8, 0)).apply {
                border = JBUI.Borders.emptyBottom(8)
                
                add(JLabel("搜索:"), BorderLayout.WEST)
                add(searchField, BorderLayout.CENTER)
                
                // 搜索按钮
                val searchBtn = JButton(AllIcons.Actions.Find).apply {
                    toolTipText = "搜索"
                    addActionListener { searchContacts() }
                }
                add(searchBtn, BorderLayout.EAST)
            }
            add(searchPanel, BorderLayout.NORTH)
            
            // 中间：搜索结果列表
            val listPanel = JPanel(BorderLayout(0, 8)).apply {
                add(JLabel("已发现的联系人:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, BorderLayout.NORTH)
                
                // 列表和添加按钮
                val listWithButton = JPanel(BorderLayout()).apply {
                    add(JScrollPane(resultList).apply {
                        preferredSize = Dimension(200, 180)
                    }, BorderLayout.CENTER)
                    
                    // 底部按钮区
                    val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
                        // 添加选中按钮
                        val addSelectedBtn = JButton("添加选中", AllIcons.General.Add).apply {
                            addActionListener {
                                selectedPeer = resultList.selectedValue
                                if (selectedPeer != null) {
                                    close(OK_EXIT_CODE)
                                }
                            }
                        }
                        add(addSelectedBtn)
                        
                        // 刷新按钮
                        val refreshBtn = JButton("刷新", AllIcons.Actions.Refresh).apply {
                            addActionListener {
                                service.refreshPeers()
                                refreshDiscoveredPeers()
                            }
                        }
                        add(refreshBtn)
                        
                        // 添加自己按钮
                        val addSelfBtn = JButton("添加自己").apply {
                            toolTipText = "添加自己用于测试"
                            addActionListener {
                                isAddSelf = true
                                close(OK_EXIT_CODE)
                            }
                        }
                        add(addSelfBtn)
                    }
                    add(buttonPanel, BorderLayout.SOUTH)
                }
                add(listWithButton, BorderLayout.CENTER)
            }
            add(listPanel, BorderLayout.CENTER)
            
            // 底部：手动输入区域
            val manualPanel = JPanel(GridBagLayout()).apply {
                border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.GRAY),
                    "手动添加联系人"
                )
                
                val gbc = GridBagConstraints()
                gbc.fill = GridBagConstraints.HORIZONTAL
                gbc.insets = Insets(4, 4, 4, 4)
                
                gbc.gridx = 0; gbc.gridy = 0
                add(JLabel("IP地址:"), gbc)
                gbc.gridx = 1; gbc.weightx = 1.0
                add(ipField, gbc)
                
                gbc.gridx = 2; gbc.weightx = 0.0
                add(JLabel("端口:"), gbc)
                gbc.gridx = 3
                add(portField, gbc)
                
                gbc.gridx = 0; gbc.gridy = 1
                add(JLabel("昵称:"), gbc)
                gbc.gridx = 1; gbc.gridwidth = 2
                add(nameField, gbc)
                
                gbc.gridx = 3; gbc.gridwidth = 1
                val manualAddBtn = JButton("添加", AllIcons.General.Add).apply {
                    addActionListener {
                        val ip = ipField.text.trim()
                        val port = portField.text.trim().toIntOrNull() ?: 8889
                        val name = nameField.text.trim().ifEmpty { "匿名用户" }
                        
                        if (ip.isNotEmpty()) {
                            selectedPeer = Peer(
                                id = "manual_${System.currentTimeMillis()}",
                                username = name,
                                ipAddress = ip,
                                port = port,
                                isOnline = true
                            )
                            close(OK_EXIT_CODE)
                        }
                    }
                }
                add(manualAddBtn, gbc)
            }
            add(manualPanel, BorderLayout.SOUTH)
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
    
    /**
     * 联系人列表单元格渲染器 - 美化显示
     */
    private class PeerListCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        private val statusLabel = JLabel()
        
        init {
            isOpaque = true
            border = JBUI.Borders.empty(8, 12)
            
            // 左侧：头像区域
            val avatarPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                preferredSize = Dimension(36, 36)
            }
            add(avatarPanel, BorderLayout.WEST)
            
            // 中间：信息区域
            val infoPanel = JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(8)
                
                nameLabel.font = Font("Microsoft YaHei", Font.BOLD, 13)
                add(nameLabel, BorderLayout.NORTH)
                
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
                ipLabel.foreground = JBColor.GRAY
                add(ipLabel, BorderLayout.CENTER)
            }
            add(infoPanel, BorderLayout.CENTER)
            
            // 右侧：状态
            statusLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            add(statusLabel, BorderLayout.EAST)
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
            
            // 在线状态
            if (peer.isOnline) {
                statusLabel.text = "● 在线"
                statusLabel.foreground = Color(76, 175, 80)
            } else {
                statusLabel.text = "○ 离线"
                statusLabel.foreground = JBColor.GRAY
            }
            
            // 选中状态
            background = if (isSelected) {
                JBColor(Color(232, 240, 254), Color(60, 60, 80))
            } else {
                JBColor.PanelBackground
            }
            
            return this
        }
    }
}

package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
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
        
        // 双击选择联系人
        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    selectedPeer = resultList.selectedValue
                    if (selectedPeer != null) {
                        ipField.text = selectedPeer!!.ipAddress
                        portField.text = selectedPeer!!.port.toString()
                        nameField.text = selectedPeer!!.username
                    }
                }
            }
        })
        
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
            preferredSize = Dimension(500, 400)
            
            // 顶部：搜索区域
            val searchPanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("搜索联系人:"), BorderLayout.WEST)
                add(searchField, BorderLayout.CENTER)
                
                // 添加"添加自己"按钮
                val addSelfBtn = JButton("添加自己").apply {
                    toolTipText = "添加自己用于测试"
                    addActionListener {
                        isAddSelf = true
                        doOKAction()
                    }
                }
                add(addSelfBtn, BorderLayout.EAST)
            }
            add(searchPanel, BorderLayout.NORTH)
            
            // 中间：搜索结果列表
            val listPanel = JPanel(BorderLayout()).apply {
                add(JLabel("已发现的联系人:"), BorderLayout.NORTH)
                add(JScrollPane(resultList).apply {
                    preferredSize = Dimension(200, 150)
                }, BorderLayout.CENTER)
                
                // 刷新按钮
                val refreshBtn = JButton(AllIcons.Actions.Refresh).apply {
                    toolTipText = "刷新"
                    addActionListener {
                        service.refreshPeers()
                        refreshDiscoveredPeers()
                    }
                }
                add(refreshBtn, BorderLayout.SOUTH)
            }
            add(listPanel, BorderLayout.CENTER)
            
            // 底部：手动输入区域
            val manualPanel = JPanel(GridBagLayout()).apply {
                border = JBUI.Borders.emptyTop(12)
                
                val gbc = GridBagConstraints()
                gbc.fill = GridBagConstraints.HORIZONTAL
                gbc.insets = Insets(4, 4, 4, 4)
                
                gbc.gridx = 0; gbc.gridy = 0
                gbc.gridwidth = 3
                add(JLabel("或手动添加:"), gbc)
                
                gbc.gridx = 0; gbc.gridy = 1
                gbc.gridwidth = 1
                add(JLabel("IP地址:"), gbc)
                gbc.gridx = 1
                add(ipField, gbc)
                
                gbc.gridx = 0; gbc.gridy = 2
                add(JLabel("端口:"), gbc)
                gbc.gridx = 1
                add(portField, gbc)
                
                gbc.gridx = 0; gbc.gridy = 3
                add(JLabel("昵称:"), gbc)
                gbc.gridx = 1
                add(nameField, gbc)
            }
            add(manualPanel, BorderLayout.SOUTH)
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("添加") {
                override fun doAction(e: ActionEvent?) {
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
                    }
                    
                    if (selectedPeer != null || isAddSelf) {
                        close(OK_EXIT_CODE)
                    }
                }
            },
            cancelAction
        )
    }
    
    override fun doValidate(): ValidationInfo? {
        return null
    }
}

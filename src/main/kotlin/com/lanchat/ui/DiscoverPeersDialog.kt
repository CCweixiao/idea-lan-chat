package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.FriendRequest
import com.lanchat.network.FriendRequestStatus
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * 发现附近的人对话框
 * 类似 Telegram 的"附近的人"功能
 */
class DiscoverPeersDialog(private val project: Project) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val peerListModel = DefaultListModel<Peer>()
    private val peerList = JList(peerListModel)
    private val statusLabel = JLabel("正在搜索附近的人...")
    private val refreshButton = JButton("刷新", AllIcons.Actions.Refresh)
    
    // 已发送的好友申请
    private val sentRequests = mutableMapOf<String, Boolean>()
    
    init {
        title = "发现附近的人"
        init()
        setupUI()
        startDiscovery()
    }
    
    private fun setupUI() {
        peerList.cellRenderer = PeerListItemRenderer()
        peerList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        refreshButton.addActionListener {
            statusLabel.text = "正在搜索..."
            peerListModel.clear()
            service.refreshPeers()
            CoroutineScope(Dispatchers.Default).launch {
                delay(3000) // 等待 3 秒收集响应
                SwingUtilities.invokeLater {
                    updatePeerList()
                }
            }
        }
    }
    
    private fun startDiscovery() {
        // 发送发现请求
        service.refreshPeers()
        
        // 监听发现的用户
        scope.launch {
            delay(2000) // 等待 2 秒收集响应
            SwingUtilities.invokeLater {
                updatePeerList()
            }
        }
        
        // 定期更新列表
        scope.launch {
            service.peers.collectLatest {
                delay(500)
                SwingUtilities.invokeLater {
                    updatePeerList()
                }
            }
        }
    }
    
    private fun updatePeerList() {
        peerListModel.clear()
        
        // 获取当前用户 ID
        val currentUserId = service.currentUser?.id ?: return
        
        // 过滤已添加的好友，只显示新发现的用户
        service.peers.value.values
            .filter { it.id != currentUserId } // 排除自己
            .filter { !service.isPeerExists(it.ipAddress, it.port) || it.id.startsWith("discovered_") } // 只显示未添加的
            .sortedByDescending { it.isOnline }
            .forEach { peer ->
                peerListModel.addElement(peer)
            }
        
        val count = peerListModel.size()
        statusLabel.text = if (count == 0) {
            "暂未发现附近的人，点击刷新重试"
        } else {
            "发现 $count 位附近的人"
        }
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(500, 450)
            
            // 顶部说明
            val headerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(8)
                
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(JLabel("发现附近的人").apply {
                        font = Font("Microsoft YaHei", Font.BOLD, 16)
                    }, BorderLayout.NORTH)
                    add(JLabel("搜索同一局域网内开启了 LAN Chat 的用户").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 12)
                        foreground = JBColor.GRAY
                    }, BorderLayout.SOUTH)
                }, BorderLayout.WEST)
                
                add(refreshButton, BorderLayout.EAST)
            }
            add(headerPanel, BorderLayout.NORTH)
            
            // 中间列表
            add(JScrollPane(peerList).apply {
                border = JBUI.Borders.empty()
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }, BorderLayout.CENTER)
            
            // 底部状态
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                isOpaque = false
                add(statusLabel.apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 12)
                    foreground = JBColor.GRAY
                })
            }, BorderLayout.SOUTH)
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
    
    /**
     * 联系人列表项渲染器
     */
    private inner class PeerListItemRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        private val statusIcon = JLabel()
        private val addButton = JButton("添加好友")
        private var currentPeer: Peer? = null
        
        init {
            isOpaque = true
            border = JBUI.Borders.empty(10, 16)
            
            // 左侧：在线状态图标
            statusIcon.preferredSize = Dimension(12, 12)
            add(statusIcon, BorderLayout.WEST)
            
            // 中间：用户信息
            val infoPanel = JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(12)
                
                nameLabel.font = Font("Microsoft YaHei", Font.PLAIN, 14)
                add(nameLabel, BorderLayout.CENTER)
                
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
                ipLabel.foreground = JBColor.GRAY
                add(ipLabel, BorderLayout.SOUTH)
            }
            add(infoPanel, BorderLayout.CENTER)
            
            // 右侧：添加按钮
            addButton.apply {
                font = Font("Microsoft YaHei", Font.PLAIN, 12)
                isFocusPainted = false
                margin = Insets(4, 12, 4, 12)
                addActionListener {
                    currentPeer?.let { peer ->
                        sendFriendRequest(peer)
                    }
                }
            }
            add(addButton, BorderLayout.EAST)
        }
        
        override fun getListCellRendererComponent(
            list: JList<out Peer>,
            peer: Peer,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): JPanel {
            currentPeer = peer
            nameLabel.text = peer.username
            ipLabel.text = "${peer.ipAddress}:${peer.port}"
            
            // 在线状态
            if (peer.isOnline) {
                statusIcon.text = "●"
                statusIcon.foreground = Color(76, 175, 80)
            } else {
                statusIcon.text = "○"
                statusIcon.foreground = JBColor.GRAY
            }
            
            // 添加按钮状态
            if (sentRequests[peer.id] == true) {
                addButton.text = "已申请"
                addButton.isEnabled = false
            } else if (service.isPeerExists(peer.ipAddress, peer.port)) {
                addButton.text = "已添加"
                addButton.isEnabled = false
            } else {
                addButton.text = "添加好友"
                addButton.isEnabled = true
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
    
    /**
     * 发送好友申请
     */
    private fun sendFriendRequest(peer: Peer) {
        // 直接添加为好友（简化流程）
        val success = service.addManualPeer(peer.ipAddress, peer.port, peer.username)
        
        if (success) {
            sentRequests[peer.id] = true
            peerList.repaint()
            
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    peerList,
                    "已添加 ${peer.username} 为好友！",
                    "添加成功",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } else {
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    peerList,
                    "该好友已存在",
                    "提示",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }
}

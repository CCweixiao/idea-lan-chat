package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.geom.Ellipse2D
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

class AddContactDialog(private val project: Project) : DialogWrapper(project) {

    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val ipField = JTextField(15)
    private val portField = JTextField("8889", 6)
    private val greetingField = JTextField(20)

    init {
        title = "联系人与群"
        init()
    }

    override fun getInitialSize(): Dimension = Dimension(600, 480)

    override fun createCenterPanel(): JComponent {
        val tabbedPane = JTabbedPane().apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            addTab("好友申请", createFriendRequestTab())
            addTab("添加联系人", createAddContactTab())
            addTab("申请记录", createRequestHistoryTab())
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(tabbedPane, BorderLayout.CENTER)
        }
    }

    // =============== Tab 0: Friend Requests ===============

    private fun createFriendRequestTab(): JPanel {
        val requestListModel = DefaultListModel<FriendRequest>()
        val requestList = JList(requestListModel).apply {
            cellRenderer = FriendRequestCellRenderer()
            fixedCellHeight = 70
            background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
        }
        val groupRequestListModel = DefaultListModel<GroupRequest>()
        val groupRequestList = JList(groupRequestListModel).apply {
            cellRenderer = GroupRequestCellRenderer()
            fixedCellHeight = 70
            background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
        }

        fun loadRequests() {
            requestListModel.clear()
            service.getPendingReceivedRequests().forEach { requestListModel.addElement(it) }
            groupRequestListModel.clear()
            service.getPendingGroupInvites().forEach { groupRequestListModel.addElement(it) }
        }
        loadRequests()

        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(8)

            val topPanel = JPanel(BorderLayout(0, 6)).apply {
                add(JLabel("待处理的好友申请").apply { font = Font("Microsoft YaHei", Font.BOLD, 13) }, BorderLayout.NORTH)
                add(JScrollPane(requestList).apply {
                    border = createListBorder(); preferredSize = Dimension(0, 140)
                }, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                    isOpaque = false
                    add(createSubtleButton("通过") {
                        val s = requestList.selectedValue
                        if (s == null) {
                            JOptionPane.showMessageDialog(window, "请先选择一条好友申请", "提示", JOptionPane.WARNING_MESSAGE)
                            return@createSubtleButton
                        }
                        service.acceptFriendRequest(s.id); loadRequests()
                        JOptionPane.showMessageDialog(window, "已添加 ${s.fromUsername} 为好友", "成功", JOptionPane.INFORMATION_MESSAGE)
                    })
                    add(createSubtleButton("拒绝") {
                        val s = requestList.selectedValue
                        if (s == null) {
                            JOptionPane.showMessageDialog(window, "请先选择一条好友申请", "提示", JOptionPane.WARNING_MESSAGE)
                            return@createSubtleButton
                        }
                        service.rejectFriendRequest(s.id); loadRequests()
                    })
                    add(createSubtleButton("刷新") { loadRequests() })
                }, BorderLayout.SOUTH)
            }

            val bottomPanel = JPanel(BorderLayout(0, 6)).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor(Color(220, 220, 220), Color(60, 60, 60))),
                    JBUI.Borders.emptyTop(8)
                )
                add(JLabel("群聊邀请 / 入群申请").apply { font = Font("Microsoft YaHei", Font.BOLD, 13) }, BorderLayout.NORTH)
                add(JScrollPane(groupRequestList).apply {
                    border = createListBorder(); preferredSize = Dimension(0, 120)
                }, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                    isOpaque = false
                    add(createSubtleButton("同意") {
                        val s = groupRequestList.selectedValue
                        if (s == null) {
                            JOptionPane.showMessageDialog(window, "请先选择一条群聊邀请/申请", "提示", JOptionPane.WARNING_MESSAGE)
                            return@createSubtleButton
                        }
                        service.acceptGroupInvite(s.id); loadRequests()
                        JOptionPane.showMessageDialog(window, "已处理", "成功", JOptionPane.INFORMATION_MESSAGE)
                    })
                    add(createSubtleButton("拒绝") {
                        val s = groupRequestList.selectedValue
                        if (s == null) {
                            JOptionPane.showMessageDialog(window, "请先选择一条群聊邀请/申请", "提示", JOptionPane.WARNING_MESSAGE)
                            return@createSubtleButton
                        }
                        service.rejectGroupInvite(s.id); loadRequests()
                    })
                }, BorderLayout.SOUTH)
            }

            add(topPanel, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    // =============== Tab 1: Add Contact (by IP) ===============

    private fun createAddContactTab(): JPanel {
        val probeStatusLabel = JLabel().apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            foreground = JBColor.GRAY
        }
        val probedPeerInfo = JLabel().apply {
            font = Font("Microsoft YaHei", Font.BOLD, 14)
        }

        var probedPeer: Peer? = null

        val searchButton = JButton("搜索用户")
        searchButton.preferredSize = Dimension(120, 32)
        searchButton.cursor = Cursor(Cursor.HAND_CURSOR)
        searchButton.isFocusPainted = false
        searchButton.addActionListener {
            val ip = ipField.text.trim()
            val port = portField.text.trim().toIntOrNull() ?: 8889
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(window, "请输入IP地址", "提示", JOptionPane.WARNING_MESSAGE)
                return@addActionListener
            }
            probeStatusLabel.text = "正在探测 $ip:$port ..."
            probeStatusLabel.foreground = JBColor(Color(100, 100, 200), Color(130, 130, 230))
            probedPeerInfo.text = ""
            scope.launch {
                val peer = service.probePeer(ip, port)
                SwingUtilities.invokeLater {
                    if (peer != null) {
                        probedPeer = peer
                        probeStatusLabel.text = "找到用户："
                        probeStatusLabel.foreground = ThemeManager.primaryButtonColor
                        probedPeerInfo.text = "${peer.username} (${peer.ipAddress}:${peer.port})"
                        probedPeerInfo.foreground = ThemeManager.primaryButtonColor
                    } else {
                        probedPeer = null
                        probeStatusLabel.text = "未找到用户，请确认IP和端口"
                        probeStatusLabel.foreground = ThemeManager.dangerTextColor
                        probedPeerInfo.text = ""
                    }
                }
            }
        }

        val sendButton = JButton("发送好友申请")
        sendButton.preferredSize = Dimension(140, 32)
        sendButton.cursor = Cursor(Cursor.HAND_CURSOR)
        sendButton.isFocusPainted = false
        sendButton.addActionListener {
            val peer = probedPeer
            if (peer == null) {
                JOptionPane.showMessageDialog(window, "请先搜索确认对方在线", "提示", JOptionPane.WARNING_MESSAGE)
                return@addActionListener
            }
            if (service.peers.value.containsKey(peer.id)) {
                JOptionPane.showMessageDialog(window, "该用户已在好友列表中", "提示", JOptionPane.INFORMATION_MESSAGE)
                return@addActionListener
            }
            service.sendFriendRequest(peer.ipAddress, peer.port, greetingField.text.trim())
            JOptionPane.showMessageDialog(window, "好友申请已发送给 ${peer.username}", "成功", JOptionPane.INFORMATION_MESSAGE)
            probedPeer = null; probeStatusLabel.text = ""; probedPeerInfo.text = ""
        }

        return JPanel(BorderLayout(8, 8)).apply {
            border = JBUI.Borders.empty(12)
            add(JLabel("通过IP添加好友").apply {
                font = Font("Microsoft YaHei", Font.BOLD, 13)
            }, BorderLayout.NORTH)

            val formPanel = JPanel().apply {
                layout = GridLayout(5, 1, 0, 8)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(JLabel("IP:"))
                    add(ipField)
                    add(JLabel("端口:"))
                    add(portField)
                })
                add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(searchButton)
                    add(JLabel("提示：对方需在线，记得关闭VPN").apply {
                        foreground = JBColor.GRAY; font = Font("Microsoft YaHei", Font.PLAIN, 11)
                    })
                })
                add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                    add(probeStatusLabel)
                    add(probedPeerInfo)
                })
                add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(JLabel("验证消息:"))
                    add(greetingField.also { it.text = "你好，我是${service.username}" })
                })
                add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(sendButton)
                })
            }
            add(formPanel, BorderLayout.CENTER)
        }
    }

    // =============== Tab 2: Request History ===============

    private fun createRequestHistoryTab(): JPanel {
        val historyListModel = DefaultListModel<Any>()
        val historyList = JList<Any>(historyListModel).apply {
            cellRenderer = RequestHistoryCellRenderer()
            fixedCellHeight = 56
            background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
        }

        fun loadHistory() {
            historyListModel.clear()
            val friendReqs = service.getAllFriendRequests()
            val groupReqs = service.getAllGroupRequests()
            val all = (friendReqs + groupReqs).sortedByDescending {
                when (it) {
                    is FriendRequest -> it.timestamp
                    is GroupRequest -> it.timestamp
                    else -> 0L
                }
            }
            all.forEach { historyListModel.addElement(it) }
        }
        loadHistory()

        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(8)
            add(JLabel("所有申请记录 (好友 + 群聊)").apply {
                font = Font("Microsoft YaHei", Font.BOLD, 13)
            }, BorderLayout.NORTH)
            add(JScrollPane(historyList).apply { border = createListBorder() }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(createRedButton("删除选中") {
                    val s = historyList.selectedValue ?: return@createRedButton
                    when (s) {
                        is FriendRequest -> service.deleteFriendRequest(s.id)
                        is GroupRequest -> service.deleteGroupRequest(s.id)
                    }
                    loadHistory()
                })
                add(createSmallButton("刷新") { loadHistory() })
            }, BorderLayout.SOUTH)
        }
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    // =============== UI Helpers ===============

    private fun createListBorder() = BorderFactory.createLineBorder(
        JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1
    )

    private fun createSubtleButton(text: String, action: () -> Unit): JButton {
        return object : JButton(text) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width, height, 8, 8)
                g2d.font = font; g2d.color = foreground
                val fm = g2d.fontMetrics
                g2d.drawString(text, (width - fm.stringWidth(text)) / 2, (height + fm.ascent - fm.descent) / 2)
            }
        }.apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            background = ThemeManager.sendButtonColor; foreground = ThemeManager.sendButtonText
            isBorderPainted = false; isFocusPainted = false; isOpaque = false; isContentAreaFilled = false
            cursor = Cursor(Cursor.HAND_CURSOR)
            margin = Insets(5, 16, 5, 16)
            addActionListener { action() }
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) { background = ThemeManager.sendButtonHoverColor; repaint() }
                override fun mouseExited(e: java.awt.event.MouseEvent) { background = ThemeManager.sendButtonColor; repaint() }
                override fun mousePressed(e: java.awt.event.MouseEvent) { background = ThemeManager.sendButtonPressedColor; repaint() }
                override fun mouseReleased(e: java.awt.event.MouseEvent) { background = ThemeManager.sendButtonHoverColor; repaint() }
            })
        }
    }

    private fun createRedButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            foreground = JBColor(Color(220, 50, 50), Color(230, 80, 80))
            isFocusPainted = false; cursor = Cursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }
    }

    private fun createSmallButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 12)
            isFocusPainted = false; addActionListener { action() }
        }
    }

    // =============== Cell Renderers ===============

    private class PeerCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        private val statusLabel = JLabel()
        private val peerColors = ThemeManager.avatarColors
        private var avatarInitial = "?"; private var avatarColor = peerColors[0]
        private val avatarPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g); val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = minOf(width, height) - 4; val x = (width - size) / 2; val y = (height - size) / 2
                g2d.color = avatarColor
                g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
                g2d.color = Color.WHITE; g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 2 / 5)
                val fm = g2d.fontMetrics
                g2d.drawString(avatarInitial, x + (size - fm.stringWidth(avatarInitial)) / 2, y + (size + fm.ascent - fm.descent) / 2)
            }
        }.apply { isOpaque = false; preferredSize = Dimension(40, 40) }

        init {
            isOpaque = true; border = JBUI.Borders.empty(8, 12)
            add(avatarPanel, BorderLayout.WEST)
            add(JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false; border = JBUI.Borders.emptyLeft(10)
                nameLabel.font = Font("Microsoft YaHei", Font.BOLD, 13); add(nameLabel, BorderLayout.CENTER)
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); ipLabel.foreground = JBColor.GRAY; add(ipLabel, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
            statusLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); statusLabel.border = JBUI.Borders.emptyRight(4)
            add(statusLabel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(list: JList<out Peer>, peer: Peer, index: Int, isSelected: Boolean, cellHasFocus: Boolean): JPanel {
            nameLabel.text = peer.username; ipLabel.text = "${peer.ipAddress}:${peer.port}"
            avatarInitial = peer.username.firstOrNull()?.toString() ?: "?"; avatarColor = peerColors[Math.abs(avatarInitial.hashCode()) % peerColors.size]
            avatarPanel.repaint()
            statusLabel.text = if (peer.isOnline) "在线" else "离线"
            statusLabel.foreground = if (peer.isOnline) ThemeManager.onlineColor else JBColor.GRAY
            background = if (isSelected) JBColor(Color(215, 230, 250), Color(55, 65, 85)) else JBColor(Color(250, 250, 250), Color(45, 45, 45))
            return this
        }
    }

    private class FriendRequestCellRenderer : JPanel(BorderLayout()), ListCellRenderer<FriendRequest> {
        private val nameLabel = JLabel(); private val msgLabel = JLabel(); private val ipLabel = JLabel()
        init {
            isOpaque = true; border = JBUI.Borders.empty(8, 12)
            add(JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false
                nameLabel.font = Font("Microsoft YaHei", Font.BOLD, 13); add(nameLabel, BorderLayout.NORTH)
                msgLabel.font = Font("Microsoft YaHei", Font.PLAIN, 12); msgLabel.foreground = JBColor(Color(100, 100, 100), Color(160, 160, 160)); add(msgLabel, BorderLayout.CENTER)
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); ipLabel.foreground = JBColor.GRAY; add(ipLabel, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }
        override fun getListCellRendererComponent(list: JList<out FriendRequest>, req: FriendRequest, index: Int, isSelected: Boolean, cellHasFocus: Boolean): JPanel {
            nameLabel.text = req.fromUsername; msgLabel.text = req.message; ipLabel.text = "来自 ${req.fromIp}:${req.fromPort}"
            background = if (isSelected) JBColor(Color(215, 230, 250), Color(55, 65, 85)) else JBColor(Color(250, 250, 250), Color(45, 45, 45))
            return this
        }
    }

    private class GroupRequestCellRenderer : JPanel(BorderLayout()), ListCellRenderer<GroupRequest> {
        private val titleLabel = JLabel(); private val msgLabel = JLabel(); private val typeLabel = JLabel()
        init {
            isOpaque = true; border = JBUI.Borders.empty(8, 12)
            add(JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false
                titleLabel.font = Font("Microsoft YaHei", Font.BOLD, 13); add(titleLabel, BorderLayout.NORTH)
                msgLabel.font = Font("Microsoft YaHei", Font.PLAIN, 12); msgLabel.foreground = JBColor(Color(100, 100, 100), Color(160, 160, 160)); add(msgLabel, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            typeLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10); typeLabel.border = JBUI.Borders.emptyRight(4)
            add(typeLabel, BorderLayout.EAST)
        }
        override fun getListCellRendererComponent(list: JList<out GroupRequest>, req: GroupRequest, index: Int, isSelected: Boolean, cellHasFocus: Boolean): JPanel {
            titleLabel.text = "${req.groupName} (${req.groupNumber})"
            msgLabel.text = "${req.requesterName}: ${req.message}"
            typeLabel.text = if (req.type == GroupRequestType.INVITE) "邀请" else "申请"
            typeLabel.foreground = if (req.type == GroupRequestType.INVITE) JBColor(Color(33, 150, 243), Color(100, 180, 255)) else JBColor(Color(255, 152, 0), Color(255, 180, 50))
            background = if (isSelected) JBColor(Color(215, 230, 250), Color(55, 65, 85)) else JBColor(Color(250, 250, 250), Color(45, 45, 45))
            return this
        }
    }

    private class RequestHistoryCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Any> {
        private val titleLabel = JLabel(); private val detailLabel = JLabel(); private val statusLabel = JLabel()
        private val timeLabel = JLabel()
        private val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        init {
            isOpaque = true; border = JBUI.Borders.empty(6, 12)
            add(JPanel(BorderLayout(0, 1)).apply {
                isOpaque = false
                val topRow = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    titleLabel.font = Font("Microsoft YaHei", Font.PLAIN, 13); add(titleLabel, BorderLayout.CENTER)
                    timeLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10); timeLabel.foreground = JBColor.GRAY; add(timeLabel, BorderLayout.EAST)
                }
                add(topRow, BorderLayout.NORTH)
                detailLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); detailLabel.foreground = JBColor.GRAY; add(detailLabel, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            statusLabel.font = Font("Microsoft YaHei", Font.BOLD, 11); statusLabel.border = JBUI.Borders.emptyLeft(8)
            add(statusLabel, BorderLayout.EAST)
        }
        override fun getListCellRendererComponent(list: JList<out Any>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): JPanel {
            val myId = LanChatService.getInstance().currentUser?.id ?: ""
            when (value) {
                is FriendRequest -> {
                    val isSent = value.fromUserId == myId
                    titleLabel.text = if (isSent) "[好友] → ${value.toIp}" else "[好友] ${value.fromUsername}"
                    detailLabel.text = if (isSent) "我发起的好友申请" else value.message
                    timeLabel.text = sdf.format(Date(value.timestamp))
                    when (value.status) {
                        FriendRequestStatus.PENDING_SENT -> { statusLabel.text = "等待验证"; statusLabel.foreground = JBColor(Color(255, 152, 0), Color(255, 180, 50)) }
                        FriendRequestStatus.PENDING_RECEIVED -> { statusLabel.text = "待处理"; statusLabel.foreground = JBColor(Color(33, 150, 243), Color(100, 180, 255)) }
                        FriendRequestStatus.ACCEPTED -> { statusLabel.text = "已通过"; statusLabel.foreground = ThemeManager.primaryButtonColor }
                        FriendRequestStatus.REJECTED -> { statusLabel.text = "已拒绝"; statusLabel.foreground = JBColor(Color(220, 50, 50), Color(230, 80, 80)) }
                    }
                }
                is GroupRequest -> {
                    val isSent = value.requesterId == myId
                    val typeText = when (value.type) {
                        GroupRequestType.INVITE -> if (isSent) "我邀请他人入群" else "${value.requesterName} 邀请你入群"
                        GroupRequestType.JOIN_REQUEST -> if (isSent) "我申请加入群聊" else "${value.requesterName} 申请加入"
                    }
                    titleLabel.text = "[群聊] ${value.groupName}"
                    detailLabel.text = typeText
                    timeLabel.text = sdf.format(Date(value.timestamp))
                    when (value.status) {
                        GroupRequestStatus.PENDING -> { statusLabel.text = "待处理"; statusLabel.foreground = JBColor(Color(255, 152, 0), Color(255, 180, 50)) }
                        GroupRequestStatus.ACCEPTED -> { statusLabel.text = "已同意"; statusLabel.foreground = ThemeManager.primaryButtonColor }
                        GroupRequestStatus.REJECTED -> { statusLabel.text = "已拒绝"; statusLabel.foreground = JBColor(Color(220, 50, 50), Color(230, 80, 80)) }
                    }
                }
            }
            background = if (isSelected) JBColor(Color(215, 230, 250), Color(55, 65, 85)) else JBColor(Color(250, 250, 250), Color(45, 45, 45))
            return this
        }
    }
}

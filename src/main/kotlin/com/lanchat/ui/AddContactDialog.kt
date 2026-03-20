package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.*
import kotlinx.coroutines.*
import java.awt.*
import java.awt.geom.Ellipse2D
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class AddContactDialog(private val project: Project) : DialogWrapper(project) {

    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val searchField = JTextField(20)
    private val ipField = JTextField(15)
    private val portField = JTextField("8889", 6)
    private val nameField = JTextField(15)
    private val greetingField = JTextField(20)
    private val groupSearchField = JTextField(15)

    private val resultListModel = DefaultListModel<Peer>()
    private val resultList = JList(resultListModel)

    var selectedPeer: Peer? = null; private set
    var isAddSelf = false; private set

    init {
        title = "联系人与群"
        init()
        setupSearch()
    }

    private fun setupSearch() {
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = searchContacts()
            override fun removeUpdate(e: DocumentEvent) = searchContacts()
            override fun changedUpdate(e: DocumentEvent) = searchContacts()
        })
        resultList.cellRenderer = PeerCellRenderer()
        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.fixedCellHeight = 56
        refreshDiscoveredPeers()
    }

    private fun searchContacts() {
        val query = searchField.text.trim().lowercase()
        resultListModel.clear()
        service.peers.value.values.filter { peer ->
            query.isEmpty() || peer.username.lowercase().contains(query) || peer.ipAddress.contains(query)
        }.forEach { resultListModel.addElement(it) }
    }

    private fun refreshDiscoveredPeers() {
        resultListModel.clear()
        service.peers.value.values.forEach { resultListModel.addElement(it) }
    }

    override fun createCenterPanel(): JComponent {
        val tabbedPane = JTabbedPane().apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            addTab("发现附近", createDiscoverTab())
            addTab("搜索联系人", createSearchTab())
            addTab("好友申请", createFriendRequestTab())
            addTab("发送申请", createSendRequestTab())
            addTab("搜索群聊", createSearchGroupTab())
            addTab("申请记录", createRequestHistoryTab())
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            preferredSize = Dimension(620, 480)
            add(tabbedPane, BorderLayout.CENTER)
        }
    }

    // =============== Tab 0: Discover Nearby ===============

    private fun createDiscoverTab(): JPanel {
        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(8)
            
            // 说明
            val headerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(JLabel("发现附近的人").apply {
                        font = Font("Microsoft YaHei", Font.BOLD, 14)
                    }, BorderLayout.NORTH)
                    add(JLabel("搜索同一局域网内开启了 LAN Chat 的用户").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 11)
                        foreground = JBColor.GRAY
                    }, BorderLayout.SOUTH)
                }, BorderLayout.WEST)
                
                add(JButton("刷新", AllIcons.Actions.Refresh).apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 12)
                    addActionListener {
                        service.refreshPeers()
                    }
                }, BorderLayout.EAST)
            }
            add(headerPanel, BorderLayout.NORTH)
            
            // 打开完整对话框按钮
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(12)
                
                add(JButton("打开「发现附近的人」").apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 13)
                    icon = AllIcons.Actions.Find
                    addActionListener {
                        DiscoverPeersDialog(project).show()
                    }
                }, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
        }
    }

    // =============== Tab 1: Search Contacts ===============

    private fun createSearchTab(): JPanel {
        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(8)

            val searchPanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel(AllIcons.Actions.Find).apply { border = JBUI.Borders.emptyLeft(4) }, BorderLayout.WEST)
                searchField.font = Font("Microsoft YaHei", Font.PLAIN, 14)
                searchField.border = createFieldBorder()
                searchField.toolTipText = "输入用户名或IP搜索"
                add(searchField, BorderLayout.CENTER)
            }
            add(searchPanel, BorderLayout.NORTH)

            val resultPanel = JPanel(BorderLayout(0, 6)).apply {
                val headerPanel = JPanel(BorderLayout()).apply {
                    add(JLabel("局域网联系人").apply {
                        font = Font("Microsoft YaHei", Font.BOLD, 13)
                        foreground = JBColor(Color(80, 80, 80), Color(180, 180, 180))
                    }, BorderLayout.WEST)
                    add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                        isOpaque = false
                        add(createSmallButton("刷新") { service.refreshPeers(); refreshDiscoveredPeers() })
                        add(createSmallButton("添加自己(测试)") { isAddSelf = true; close(OK_EXIT_CODE) })
                    }, BorderLayout.EAST)
                }
                add(headerPanel, BorderLayout.NORTH)
                resultList.background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
                add(JScrollPane(resultList).apply {
                    border = createListBorder()
                }, BorderLayout.CENTER)
                add(createGreenButton("添加选中的联系人") {
                    selectedPeer = resultList.selectedValue
                    if (selectedPeer != null) close(OK_EXIT_CODE)
                    else JOptionPane.showMessageDialog(window, "请先选择联系人", "提示", JOptionPane.INFORMATION_MESSAGE)
                }, BorderLayout.SOUTH)
            }
            add(resultPanel, BorderLayout.CENTER)
        }
    }

    // =============== Tab 2: Friend Requests ===============

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
                add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false
                    add(createGreenButton("通过") {
                        val s = requestList.selectedValue ?: return@createGreenButton
                        service.acceptFriendRequest(s.id); loadRequests()
                        JOptionPane.showMessageDialog(window, "已添加 ${s.fromUsername} 为好友", "成功", JOptionPane.INFORMATION_MESSAGE)
                    })
                    add(createRedButton("拒绝") {
                        val s = requestList.selectedValue ?: return@createRedButton
                        service.rejectFriendRequest(s.id); loadRequests()
                    })
                    add(createSmallButton("模拟收到申请") {
                        service.simulateIncomingFriendRequest(
                            "192.168.1.${(10..200).random()}", "测试用户${(1..99).random()}"
                        )
                        loadRequests()
                    })
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
                add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false
                    add(createGreenButton("同意") {
                        val s = groupRequestList.selectedValue ?: return@createGreenButton
                        service.acceptGroupInvite(s.id); loadRequests()
                        JOptionPane.showMessageDialog(window, "已处理", "成功", JOptionPane.INFORMATION_MESSAGE)
                    })
                    add(createRedButton("拒绝") {
                        val s = groupRequestList.selectedValue ?: return@createRedButton
                        service.rejectGroupInvite(s.id); loadRequests()
                    })
                }, BorderLayout.SOUTH)
            }

            add(topPanel, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    // =============== Tab 3: Send Request ===============

    private fun createSendRequestTab(): JPanel {
        val probeStatusLabel = JLabel().apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            foreground = JBColor.GRAY
        }
        val probedPeerInfo = JLabel().apply {
            font = Font("Microsoft YaHei", Font.BOLD, 14)
        }
        
        var probedPeer: Peer? = null

        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(12)
            
            // 标题
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JLabel("通过IP添加好友").apply { 
                    font = Font("Microsoft YaHei", Font.BOLD, 13) 
                }, BorderLayout.WEST)
                add(JLabel("先探测确认对方在线，再发送申请").apply { 
                    font = Font("Microsoft YaHei", Font.PLAIN, 11)
                    foreground = JBColor.GRAY
                }, BorderLayout.SOUTH)
            }, BorderLayout.NORTH)

            // 表单区域（带滚动）
            val formPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.emptyTop(4)

                // 第一行：IP + 端口 + 搜索按钮
                add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false
                    add(JLabel("IP:").apply { font = Font("Microsoft YaHei", Font.PLAIN, 13) })
                    add(JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.X_AXIS)
                        add(ipField.also { 
                            it.font = Font("Microsoft YaHei", Font.PLAIN, 13)
                            it.border = createFieldBorder()
                            it.preferredSize = Dimension(160, 28)
                        })
                        add(JLabel(":").apply { font = Font("Microsoft YaHei", Font.PLAIN, 13) })
                        add(portField.also {
                            it.font = Font("Microsoft YaHei", Font.PLAIN, 13)
                            it.preferredSize = Dimension(50, 28)
                        })
                    })
                    add(createGreenButton("搜索用户") {
                        val ip = ipField.text.trim()
                        val port = portField.text.trim().toIntOrNull() ?: 8889
                        if (ip.isEmpty()) {
                            JOptionPane.showMessageDialog(window, "请输入IP地址", "提示", JOptionPane.WARNING_MESSAGE)
                            return@createGreenButton
                        }
                        
                        probeStatusLabel.text = "正在探测 $ip:$port ..."
                        probeStatusLabel.foreground = JBColor(Color(100, 100, 200), Color(130, 130, 230))
                        probedPeerInfo.text = ""
                        
                        scope.launch {
                            val peer = service.probePeer(ip, port)
                            SwingUtilities.invokeLater {
                                if (peer != null) {
                                    probedPeer = peer
                                    probeStatusLabel.text = "✓ 找到用户："
                                    probeStatusLabel.foreground = ThemeManager.primaryButtonColor
                                    probedPeerInfo.text = "${peer.username} (${peer.ipAddress}:${peer.port})"
                                    probedPeerInfo.foreground = ThemeManager.primaryButtonColor
                                } else {
                                    probedPeer = null
                                    probeStatusLabel.text = "✗ 未找到用户，请确认IP和端口是否正确"
                                    probeStatusLabel.foreground = ThemeManager.dangerTextColor
                                    probedPeerInfo.text = ""
                                }
                            }
                        }
                    })
                })

                // 探测结果
                add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                    add(probeStatusLabel)
                    add(probedPeerInfo)
                })

                // 验证消息
                add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false; border = JBUI.Borders.emptyTop(12)
                    add(JLabel("验证消息:").apply { font = Font("Microsoft YaHei", Font.PLAIN, 13) })
                    add(greetingField.also {
                        it.font = Font("Microsoft YaHei", Font.PLAIN, 13)
                        it.text = "你好，我是${service.username}"
                        it.preferredSize = Dimension(350, 28)
                    })
                })

                // 发送申请按钮
                add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false; border = JBUI.Borders.emptyTop(12)
                    add(createGreenButton("发送好友申请") {
                        val peer = probedPeer
                        if (peer == null) {
                            JOptionPane.showMessageDialog(window, "请先搜索确认对方在线", "提示", JOptionPane.WARNING_MESSAGE)
                            return@createGreenButton
                        }
                        if (service.peers.value.containsKey(peer.id)) {
                            JOptionPane.showMessageDialog(window, "该用户已在好友列表中", "提示", JOptionPane.INFORMATION_MESSAGE)
                            return@createGreenButton
                        }
                        service.sendFriendRequest(peer.ipAddress, peer.port, greetingField.text.trim())
                        JOptionPane.showMessageDialog(window, "好友申请已发送给 ${peer.username}", "成功", JOptionPane.INFORMATION_MESSAGE)
                        probedPeer = null; probeStatusLabel.text = ""; probedPeerInfo.text = ""
                    })
                })
            }
            add(JScrollPane(formPanel).apply {
                border = null
                viewportBorder = null
            }, BorderLayout.CENTER)
        }
    }

    // =============== Tab 4: Search Group ===============

    private fun createSearchGroupTab(): JPanel {
        val groupListModel = DefaultListModel<Group>()
        val groupList = JList(groupListModel).apply {
            cellRenderer = GroupSearchCellRenderer()
            fixedCellHeight = 60
            background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
        }

        fun searchGroups() {
            val query = groupSearchField.text.trim()
            groupListModel.clear()
            if (query.isEmpty()) {
                service.groups.value.values.forEach { groupListModel.addElement(it) }
            } else {
                service.searchGroupByNumber(query).forEach { groupListModel.addElement(it) }
                service.groups.value.values
                    .filter { it.name.lowercase().contains(query.lowercase()) && !groupListModel.contains(it) }
                    .forEach { groupListModel.addElement(it) }
            }
        }
        searchGroups()

        groupSearchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = searchGroups()
            override fun removeUpdate(e: DocumentEvent) = searchGroups()
            override fun changedUpdate(e: DocumentEvent) = searchGroups()
        })

        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(8)

            val searchPanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel(AllIcons.Actions.Find).apply { border = JBUI.Borders.emptyLeft(4) }, BorderLayout.WEST)
                groupSearchField.font = Font("Microsoft YaHei", Font.PLAIN, 14)
                groupSearchField.border = createFieldBorder()
                groupSearchField.toolTipText = "输入群号或群名搜索"
                add(groupSearchField, BorderLayout.CENTER)
            }
            add(searchPanel, BorderLayout.NORTH)

            add(JScrollPane(groupList).apply { border = createListBorder() }, BorderLayout.CENTER)

            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(createGreenButton("申请加入") {
                    val g = groupList.selectedValue ?: return@createGreenButton
                    val currentId = service.currentUser?.id ?: return@createGreenButton
                    if (g.memberIds.contains(currentId)) {
                        JOptionPane.showMessageDialog(window, "你已经是该群成员", "提示", JOptionPane.INFORMATION_MESSAGE)
                        return@createGreenButton
                    }
                    val greeting = JOptionPane.showInputDialog(window, "输入验证消息:", "申请加入群聊", JOptionPane.PLAIN_MESSAGE) ?: return@createGreenButton
                    service.requestJoinGroup(g.id, greeting)
                    JOptionPane.showMessageDialog(window, "入群申请已发送", "成功", JOptionPane.INFORMATION_MESSAGE)
                })
                add(JLabel("提示：群号由群主创建时自动生成").apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 11); foreground = JBColor.GRAY
                })
            }, BorderLayout.SOUTH)
        }
    }

    // =============== Tab 5: Request History ===============

    private fun createRequestHistoryTab(): JPanel {
        val historyListModel = DefaultListModel<Any>()
        val historyList = JList<Any>(historyListModel).apply {
            cellRenderer = RequestHistoryCellRenderer()
            fixedCellHeight = 56
            background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
        }

        fun loadHistory() {
            historyListModel.clear()
            val friendReqs = service.getAllFriendRequests().map { it as Any }
            val groupReqs = service.getAllGroupRequests().map { it as Any }
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

    private fun createFieldBorder() = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(JBColor(Color(210, 210, 210), Color(70, 70, 70)), 1),
        JBUI.Borders.empty(6, 10)
    )

    private fun createListBorder() = BorderFactory.createLineBorder(
        JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1
    )

    private fun createGreenButton(text: String, action: () -> Unit): JButton {
        return object : JButton(text) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background; g2d.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
                super.paintComponent(g2d)
            }
        }.apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            background = ThemeManager.primaryButtonColor; foreground = ThemeManager.primaryButtonText
            isBorderPainted = false; isFocusPainted = false; isOpaque = false
            cursor = Cursor(Cursor.HAND_CURSOR); preferredSize = Dimension(0, 32)
            border = JBUI.Borders.empty(4, 16, 4, 16)
            addActionListener { action() }
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) { background = ThemeManager.primaryButtonHoverColor; repaint() }
                override fun mouseExited(e: java.awt.event.MouseEvent) { background = ThemeManager.primaryButtonColor; repaint() }
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

    private class GroupSearchCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Group> {
        private val nameLabel = JLabel(); private val numberLabel = JLabel(); private val memberLabel = JLabel()
        init {
            isOpaque = true; border = JBUI.Borders.empty(8, 12)
            val avatarPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g); val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val size = minOf(width, height) - 4; val x = (width - size) / 2; val y = (height - size) / 2
                    g2d.color = JBColor(Color(87, 137, 213), Color(70, 120, 190))
                    g2d.fillRoundRect(x, y, size, size, 8, 8)
                    g2d.color = Color.WHITE; g2d.font = Font("Microsoft YaHei", Font.BOLD, 14)
                    val fm = g2d.fontMetrics; val text = "群"
                    g2d.drawString(text, x + (size - fm.stringWidth(text)) / 2, y + (size + fm.ascent - fm.descent) / 2)
                }
            }.apply { isOpaque = false; preferredSize = Dimension(40, 40) }
            add(avatarPanel, BorderLayout.WEST)
            add(JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false; border = JBUI.Borders.emptyLeft(10)
                nameLabel.font = Font("Microsoft YaHei", Font.BOLD, 13); add(nameLabel, BorderLayout.CENTER)
                numberLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); numberLabel.foreground = JBColor.GRAY; add(numberLabel, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
            memberLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); memberLabel.foreground = JBColor.GRAY
            memberLabel.border = JBUI.Borders.emptyRight(4)
            add(memberLabel, BorderLayout.EAST)
        }
        override fun getListCellRendererComponent(list: JList<out Group>, group: Group, index: Int, isSelected: Boolean, cellHasFocus: Boolean): JPanel {
            nameLabel.text = group.name; numberLabel.text = "群号: ${group.groupNumber}"; memberLabel.text = "${group.getMemberCount()}人"
            val isMember = LanChatService.getInstance().currentUser?.id?.let { group.memberIds.contains(it) } ?: false
            if (isMember) { memberLabel.text = "已加入"; memberLabel.foreground = ThemeManager.primaryButtonColor } else { memberLabel.foreground = JBColor.GRAY }
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
            when (value) {
                is FriendRequest -> {
                    val isSent = value.status == FriendRequestStatus.PENDING_SENT || value.status == FriendRequestStatus.ACCEPTED && value.fromUserId == LanChatService.getInstance().currentUser?.id
                    titleLabel.text = if (value.status == FriendRequestStatus.PENDING_RECEIVED || value.status == FriendRequestStatus.PENDING_SENT) "[好友] ${value.fromUsername}" else "[好友] ${value.fromUsername}"
                    detailLabel.text = value.message
                    timeLabel.text = sdf.format(Date(value.timestamp))
                    when (value.status) {
                        FriendRequestStatus.PENDING_SENT -> { statusLabel.text = "等待验证"; statusLabel.foreground = JBColor(Color(255, 152, 0), Color(255, 180, 50)) }
                        FriendRequestStatus.PENDING_RECEIVED -> { statusLabel.text = "待处理"; statusLabel.foreground = JBColor(Color(33, 150, 243), Color(100, 180, 255)) }
                        FriendRequestStatus.ACCEPTED -> { statusLabel.text = "已通过"; statusLabel.foreground = ThemeManager.primaryButtonColor }
                        FriendRequestStatus.REJECTED -> { statusLabel.text = "已拒绝"; statusLabel.foreground = JBColor(Color(220, 50, 50), Color(230, 80, 80)) }
                    }
                }
                is GroupRequest -> {
                    titleLabel.text = "[群聊] ${value.groupName}"
                    detailLabel.text = "${if (value.type == GroupRequestType.INVITE) "邀请" else "申请"}: ${value.message}"
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

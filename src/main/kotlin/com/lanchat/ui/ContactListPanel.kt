package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.net.URL
import com.lanchat.network.Group
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

sealed class ChatItem {
    data class PeerItem(val peer: Peer) : ChatItem()
    data class GroupItem(val group: Group) : ChatItem()
}

class ContactListPanel(
    private val project: Project,
    private val onChatItemSelected: (ChatItem?) -> Unit
) : JPanel(BorderLayout()) {

    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ThemeManager.listBackground
    }
    private val scrollPane: JBScrollPane
    private val statusLabel = JLabel()
    private val nicknameLabel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    private val searchField = PlaceholderTextField("搜索联系人/群聊")

    private var groupItems = mutableListOf<ChatItem.GroupItem>()
    private var peerItems = mutableListOf<ChatItem.PeerItem>()
    private var isGroupExpanded = true
    private var isPeerExpanded = true
    private var selectedItem: ChatItem? = null

    companion object {
        private val ACCENT_GREEN = Color(7, 193, 96)
        private val TITLE_FONT get() = ThemeManager.boldFont(2)
        private val SECTION_FONT get() = ThemeManager.boldFont(-2)
        private val NAME_FONT get() = ThemeManager.plainFont()
        private val SUB_FONT get() = ThemeManager.plainFont(-3)
    }

    init {
        scrollPane = JBScrollPane(contentPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            background = ThemeManager.listBackground
        }
        setupUI()
        observeData()
    }

    private fun setupUI() {
        background = ThemeManager.listBackground
        add(createHeader(), BorderLayout.NORTH)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(createSearchPanel(), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)
    }

    private fun createHeader(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = ThemeManager.listBackground
            border = JBUI.Borders.empty(12, 16, 4, 16)

            val titlePanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                val titleLabel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false
                    add(JLabel(createTitleAvatarIcon()))
                    add(JLabel("好友列表").apply { font = TITLE_FONT })
                }
                add(titleLabel, BorderLayout.NORTH)
                nicknameLabel.apply {
                    updateNicknameLabel()
                }
                add(nicknameLabel, BorderLayout.SOUTH)
            }
            add(titlePanel, BorderLayout.WEST)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(createWechatButton(AllIcons.General.Add, "添加联系人") { showAddContactDialog() })
                add(createWechatButton(AllIcons.Actions.AddMulticaret, "创建群聊") { showCreateGroupDialog() })
            }
            add(buttonPanel, BorderLayout.EAST)
        }
    }

    private fun createSearchPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 14, 8, 14)

            val wrapper = object : JPanel(BorderLayout()) {
                override fun paintComponent(g: Graphics) {
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.color = background
                    g2d.fillRoundRect(0, 0, width, height, 16, 16)
                }
            }.apply {
                isOpaque = false
                background = JBColor(Color(225, 225, 225), ThemeManager.itemHover)
                searchField.apply {
                    border = JBUI.Borders.empty(7, 12, 7, 12)
                    font = ThemeManager.plainFont(-1)
                    background = JBColor(Color(0, 0, 0, 0), Color(0, 0, 0, 0))
                    isOpaque = false
                    document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent) = rebuildList()
                        override fun removeUpdate(e: DocumentEvent) = rebuildList()
                        override fun changedUpdate(e: DocumentEvent) = rebuildList()
                    })
                }
                add(searchField, BorderLayout.CENTER)
            }
            add(wrapper, BorderLayout.CENTER)
        }
    }

    private fun createFooter(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            background = ThemeManager.listBackground
            border = JBUI.Borders.empty(6, 16, 6, 16)
            statusLabel.font = SUB_FONT
            statusLabel.foreground = JBColor.GRAY
            add(statusLabel)
        }
    }

    private fun createWechatButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return object : JButton(icon) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                if (model.isRollover) {
                    g2d.color = JBColor(Color(210, 210, 210), ThemeManager.borderColor)
                    g2d.fillRoundRect(2, 2, width - 4, height - 4, 8, 8)
                }
                super.paintComponent(g2d)
            }
        }.apply {
            toolTipText = tooltip
            isBorderPainted = false; isContentAreaFilled = false
            isFocusPainted = false; isOpaque = false
            margin = Insets(4, 4, 4, 4)
            preferredSize = Dimension(30, 30)
            cursor = Cursor(Cursor.HAND_CURSOR)
            isRolloverEnabled = true
            addActionListener { action() }
        }
    }

    // =============== Section Header ===============

    private fun createSectionHeader(title: String, count: Int, expanded: Boolean, onClick: () -> Unit): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.color = background
                g2d.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque = false
            background = ThemeManager.listBackground
            border = JBUI.Borders.empty(8, 16, 4, 16)
            cursor = Cursor(Cursor.HAND_CURSOR)

            val arrow = if (expanded) "\u25BC" else "\u25B6"
            add(JLabel("$arrow  $title").apply {
                font = SECTION_FONT
                foreground = ThemeManager.secondaryTextColor
            }, BorderLayout.WEST)

            add(JLabel("$count").apply {
                font = SUB_FONT
                foreground = ThemeManager.tertiaryTextColor
            }, BorderLayout.EAST)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) { onClick() }
            })
        }
    }

    // =============== Contact Item Panel ===============

    private fun createItemPanel(item: ChatItem, isPinned: Boolean = false, isAssistant: Boolean = false): JPanel {
        val isSelected = item == selectedItem
        val chatId = when (item) {
            is ChatItem.GroupItem -> item.group.id
            is ChatItem.PeerItem -> item.peer.id
        }
        val unreadCount = service.unreadCounts.value[chatId] ?: 0

        val pinnedBg = JBColor(Color(242, 242, 242), Color(52, 52, 52))
        class HoverPanel : JPanel(BorderLayout()) {
            var hovering = false
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.color = when {
                    isSelected -> ThemeManager.itemSelected
                    hovering -> ThemeManager.itemHover
                    isPinned -> pinnedBg
                    else -> ThemeManager.listBackground
                }
                g2d.fillRect(0, 0, width, height)
            }
        }

        val panel = HoverPanel().apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 16, 8, 16)
            cursor = Cursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(0, 60)
        }

        val avatarPanel: JPanel
        val nameText: String
        val subText: String
        var isGroup = false
        var peerRef: Peer? = null  // 保存 peer 引用，用于实时获取状态

        when (item) {
            is ChatItem.GroupItem -> {
                val group = item.group
                nameText = group.name
                subText = "${group.getMemberCount()}人 · 群号${group.groupNumber}"
                isGroup = true
                avatarPanel = createAvatarPanel(group.name.firstOrNull()?.toString() ?: "群", true)
            }
            is ChatItem.PeerItem -> {
                val peer = item.peer
                peerRef = peer
                nameText = peer.username
                subText = if (isAssistant) "本地文件存储" else peer.ipAddress
                avatarPanel = if (isAssistant) createAvatarPanel("📁", false, Color(64, 158, 255))
                    else createAvatarPanel(peer.username.firstOrNull()?.toString() ?: "?", false)
            }
        }

        panel.add(avatarPanel, BorderLayout.WEST)

        val displayName = if (isPinned) "📌 $nameText" else nameText
        val infoPanel = JPanel(BorderLayout(0, 2)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(12)
            add(JLabel(displayName).apply { font = NAME_FONT }, BorderLayout.CENTER)
            add(JLabel(subText).apply { font = SUB_FONT; foreground = JBColor.GRAY }, BorderLayout.SOUTH)
        }
        panel.add(infoPanel, BorderLayout.CENTER)

        // 右侧面板（在线状态 + 未读徽章）
        val rightPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
        }

        // 未读消息徽章
        if (unreadCount > 0) {
            val badgePanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                    // 计算徽章大小
                    val text = if (unreadCount > 99) "99+" else unreadCount.toString()
                    val fm = g2d.fontMetrics
                    val textWidth = fm.stringWidth(text)
                    val padding = 6
                    val badgeWidth = maxOf(textWidth + padding * 2, 18)
                    val badgeHeight = 18
                    val x = (width - badgeWidth) / 2
                    val y = 2

                    // 绘制红色圆形徽章
                    g2d.color = ThemeManager.badgeColor
                    g2d.fillRoundRect(x, y, badgeWidth, badgeHeight, 9, 9)

                    // 绘制文字
                    g2d.color = Color.WHITE
                    g2d.font = ThemeManager.boldFont(-3)
                    val textX = x + (badgeWidth - textWidth) / 2
                    val textY = y + (badgeHeight + fm.ascent - fm.descent) / 2 - 1
                    g2d.drawString(text, textX, textY)
                }
            }.apply {
                isOpaque = false
                preferredSize = Dimension(40, 22)
            }
            rightPanel.add(badgePanel, BorderLayout.NORTH)
        }

        // 在线状态指示
        if (!isGroup && !isAssistant) {
            rightPanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, if (unreadCount > 0) 26 else 14)).apply {
                isOpaque = false
                preferredSize = Dimension(16, if (unreadCount > 0) 36 else 44)
                add(object : JPanel() {
                    override fun paintComponent(g: Graphics) {
                        super.paintComponent(g)
                        val g2d = g as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        // 实时从 service 获取最新在线状态
                        val currentPeer = peerRef?.let { service.peers.value[it.id] }
                        val actuallyOnline = currentPeer?.isOnline ?: false
                        g2d.color = if (actuallyOnline) ThemeManager.onlineColor else ThemeManager.offlineColor
                        g2d.fillOval(0, 0, 8, 8)
                    }
                }.apply { isOpaque = false; preferredSize = Dimension(8, 8) })
            }, BorderLayout.CENTER)
        }

        panel.add(rightPanel, BorderLayout.EAST)

        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    selectedItem = item; onChatItemSelected(item); rebuildList()
                }
            }
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showPopupMenu(e, item, isAssistant) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showPopupMenu(e, item, isAssistant) }
            override fun mouseEntered(e: MouseEvent) { panel.hovering = true; panel.repaint() }
            override fun mouseExited(e: MouseEvent) { panel.hovering = false; panel.repaint() }
        })

        return panel
    }

    private fun createAvatarPanel(initial: String, isGroup: Boolean, colorOverride: Color? = null): JPanel {
        val peerColors = ThemeManager.avatarColors
        val groupColor = ThemeManager.groupIconColor

        return object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = minOf(width, height) - 4
                val x = (width - size) / 2
                val y = (height - size) / 2

                if (isGroup) {
                    g2d.color = colorOverride ?: groupColor
                    g2d.fillRoundRect(x, y, size, size, 10, 10)
                } else {
                    val color = colorOverride ?: peerColors[Math.abs(initial.hashCode()) % peerColors.size]
                    g2d.color = color
                    g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
                }

                g2d.color = Color.WHITE
                g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 2 / 5)
                val fm = g2d.fontMetrics
                g2d.drawString(initial,
                    x + (size - fm.stringWidth(initial)) / 2,
                    y + (size + fm.ascent - fm.descent) / 2
                )
            }
        }.apply {
            isOpaque = false
            preferredSize = Dimension(44, 44)
        }
    }

    // =============== Data & Rebuild ===============

    private fun updateNicknameLabel() {
        nicknameLabel.removeAll()
        nicknameLabel.add(JLabel("${service.username} · ").apply {
            font = SUB_FONT; foreground = JBColor(ACCENT_GREEN, ThemeManager.onlineColor)
        })
        nicknameLabel.add(JLabel(service.localIp).apply {
            font = SUB_FONT; foreground = JBColor(ACCENT_GREEN, ThemeManager.onlineColor)
            cursor = Cursor(Cursor.HAND_CURSOR)
            toolTipText = "点击复制 IP 地址"
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(java.awt.datatransfer.StringSelection(service.localIp), null)
                    text = "${service.localIp} ✓"
                    cursor = Cursor(Cursor.DEFAULT_CURSOR)
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        SwingUtilities.invokeLater {
                            text = service.localIp
                            cursor = Cursor(Cursor.HAND_CURSOR)
                        }
                    }
                }
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    foreground = JBColor(ACCENT_GREEN.brighter(), ThemeManager.onlineColor.brighter())
                }
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    foreground = JBColor(ACCENT_GREEN, ThemeManager.onlineColor)
                }
            })
        })
    }

    private fun observeData() {
        scope.launch { service.peers.collect { updateData() } }
        scope.launch { service.groups.collectLatest { updateData() } }
        scope.launch { service.unreadCounts.collectLatest { updateData() } }
        scope.launch { service.pinnedIds.collectLatest { SwingUtilities.invokeLater { rebuildList() } } }
    }

    private fun updateData() {
        SwingUtilities.invokeLater {
            updateNicknameLabel()
            groupItems.clear()
            peerItems.clear()
            service.groups.value.values.sortedByDescending { it.createdAt }.forEach {
                groupItems.add(ChatItem.GroupItem(it))
            }
            service.peers.value.values
                .filter { it.id != LanChatService.FILE_TRANSFER_ASSISTANT_ID }
                .sortedWith(compareByDescending<Peer> { it.isOnline }.thenByDescending { it.lastSeen })
                .forEach { peerItems.add(ChatItem.PeerItem(it)) }
            rebuildList()
            updateStatus()
        }
    }

    private fun rebuildList() {
        val query = searchField.text.trim().lowercase()
        val pinned = service.pinnedIds.value

        val filteredGroups = if (query.isEmpty()) groupItems else groupItems.filter {
            it.group.name.lowercase().contains(query) || it.group.groupNumber.contains(query)
        }
        val filteredPeers = if (query.isEmpty()) peerItems else peerItems.filter {
            it.peer.username.lowercase().contains(query) || it.peer.ipAddress.contains(query)
        }

        val pinnedGroupItems = filteredGroups.filter { pinned.contains(it.group.id) }
        val normalGroupItems = filteredGroups.filter { !pinned.contains(it.group.id) }
        val pinnedPeerItems = filteredPeers.filter { pinned.contains(it.peer.id) }
        val normalPeerItems = filteredPeers.filter { !pinned.contains(it.peer.id) }

        contentPanel.removeAll()

        val fta = ChatItem.PeerItem(LanChatService.FILE_TRANSFER_ASSISTANT)
        if (query.isEmpty() || "文件传输助手".contains(query)) {
            contentPanel.add(createItemPanel(fta, isAssistant = true))
        }

        contentPanel.add(createSectionHeader("群聊", filteredGroups.size, isGroupExpanded) {
            isGroupExpanded = !isGroupExpanded; rebuildList()
        })
        if (isGroupExpanded) {
            pinnedGroupItems.forEach { contentPanel.add(createItemPanel(it, isPinned = true)) }
            normalGroupItems.forEach { contentPanel.add(createItemPanel(it)) }
        }

        contentPanel.add(createSectionHeader("好友", filteredPeers.size, isPeerExpanded) {
            isPeerExpanded = !isPeerExpanded; rebuildList()
        })
        if (isPeerExpanded) {
            pinnedPeerItems.forEach { contentPanel.add(createItemPanel(it, isPinned = true)) }
            normalPeerItems.forEach { contentPanel.add(createItemPanel(it)) }
        }

        contentPanel.add(Box.createVerticalGlue())
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun updateStatus() {
        val peerCount = peerItems.size
        val groupCount = groupItems.size
        val onlineCount = peerItems.count { it.peer.isOnline }
        statusLabel.text = "联系人 $peerCount · 在线 $onlineCount · 群聊 $groupCount"
    }

    // =============== Actions ===============

    private fun showAddContactDialog() {
        AddContactDialog(project).showAndGet()
    }

    private fun showCreateGroupDialog() {
        val currentUserId = service.currentUser?.id
        val availablePeers = peerItems.map { it.peer }
            .filter { it.id != currentUserId }

        val dialog = CreateGroupDialog(project, availablePeers)
        if (dialog.showAndGet()) {
            val memberIds = dialog.selectedPeers.map { it.id }
            service.createGroup(dialog.groupName, memberIds)
        }
    }

    private fun showPopupMenu(e: MouseEvent, item: ChatItem, isAssistant: Boolean = false) {
        val menu = JPopupMenu()
        val itemId = when (item) {
            is ChatItem.GroupItem -> item.group.id
            is ChatItem.PeerItem -> item.peer.id
        }
        val pinText = if (service.isPinned(itemId)) "取消置顶" else "置顶"
        menu.add(createMenuItem(pinText) { service.togglePin(itemId) })

        when (item) {
            is ChatItem.GroupItem -> {
                menu.add(createMenuItem("群管理") {
                    GroupManageDialog(project, item.group.id).show()
                })
                if (service.isGroupOwner(item.group.id)) {
                    menu.addSeparator()
                    menu.add(createMenuItem("解散群聊", ThemeManager.dangerTextColor) {
                        val confirm = JOptionPane.showConfirmDialog(
                            this, "确定要解散群聊「${item.group.name}」吗？", "确认", JOptionPane.YES_NO_OPTION
                        )
                        if (confirm == JOptionPane.YES_OPTION) {
                            service.deleteGroup(item.group.id)
                            if (selectedItem == item) { selectedItem = null; onChatItemSelected(null) }
                        }
                    })
                }
            }
            is ChatItem.PeerItem -> {
                if (!isAssistant) {
                    menu.addSeparator()
                    menu.add(createMenuItem("复制用户名") {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(java.awt.datatransfer.StringSelection(item.peer.username), null)
                    })
                    menu.add(createMenuItem("复制 IP 地址") {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(java.awt.datatransfer.StringSelection(item.peer.ipAddress), null)
                    })
                    menu.addSeparator()
                    menu.add(createMenuItem("删除联系人", ThemeManager.dangerTextColor) {
                        val confirm = JOptionPane.showConfirmDialog(
                            this, "确定要删除联系人「${item.peer.username}」吗？", "确认", JOptionPane.YES_NO_OPTION
                        )
                        if (confirm == JOptionPane.YES_OPTION) {
                            service.removePeer(item.peer.id)
                            if (selectedItem == item) { selectedItem = null; onChatItemSelected(null) }
                        }
                    })
                }
            }
        }
        menu.show(e.component, e.x, e.y)
    }

    private fun createMenuItem(text: String, color: Color? = null, action: () -> Unit): JMenuItem {
        return JMenuItem(text).apply {
            font = ThemeManager.plainFont(-1)
            if (color != null) foreground = JBColor(color, color)
            addActionListener { action() }
        }
    }

    private fun createTitleAvatarIcon(size: Int = 18): Icon {
        return object : Icon {
            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

                // 渐变紫蓝色背景圆
                val gradient = java.awt.GradientPaint(
                    x.toFloat(), y.toFloat(), Color(99, 102, 241),
                    (x + size).toFloat(), (y + size).toFloat(), Color(139, 92, 246)
                )
                g2d.paint = gradient
                g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))

                // 白色人像
                g2d.color = Color.WHITE
                // 头
                val headSize = (size * 0.35).toInt()
                val headX = x + (size - headSize) / 2
                val headY = y + (size * 0.2).toInt()
                g2d.fill(Ellipse2D.Double(headX.toDouble(), headY.toDouble(), headSize.toDouble(), headSize.toDouble()))
                // 身体
                val bodyWidth = (size * 0.7).toInt()
                val bodyHeight = (size * 0.35).toInt()
                val bodyX = x + (size - bodyWidth) / 2
                val bodyY = y + (size * 0.55).toInt()
                g2d.fill(RoundRectangle2D.Double(
                    bodyX.toDouble(), bodyY.toDouble(),
                    bodyWidth.toDouble(), bodyHeight.toDouble(),
                    bodyWidth.toDouble(), bodyHeight.toDouble()
                ))
            }
            override fun getIconWidth() = size
            override fun getIconHeight() = size
        }
    }

    private class PlaceholderTextField(private val placeholder: String) : JTextField() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (text.isNullOrEmpty()) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2d.color = ThemeManager.tertiaryTextColor
                g2d.font = font
                val fm = g2d.fontMetrics
                g2d.drawString(placeholder, insets.left + 2, height / 2 + fm.ascent / 2 - 2)
            }
        }
    }
}

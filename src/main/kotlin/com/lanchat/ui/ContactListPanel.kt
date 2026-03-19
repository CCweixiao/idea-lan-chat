package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Group
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
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
        background = BG_COLOR
    }
    private val scrollPane: JBScrollPane
    private val statusLabel = JLabel()
    private val searchField = PlaceholderTextField("搜索联系人/群聊")

    private var groupItems = mutableListOf<ChatItem.GroupItem>()
    private var peerItems = mutableListOf<ChatItem.PeerItem>()
    private var isGroupExpanded = true
    private var isPeerExpanded = true
    private var selectedItem: ChatItem? = null

    companion object {
        private val BG_COLOR = JBColor(Color(237, 237, 237), Color(38, 38, 38))
        private val ITEM_BG = JBColor(Color(237, 237, 237), Color(38, 38, 38))
        private val ITEM_HOVER = JBColor(Color(218, 218, 218), Color(50, 50, 50))
        private val ITEM_SELECTED = JBColor(Color(196, 196, 196), Color(55, 55, 60))
        private val HEADER_BG = JBColor(Color(237, 237, 237), Color(38, 38, 38))
        private val ACCENT_GREEN = Color(7, 193, 96)
        private val TITLE_FONT = Font("Microsoft YaHei", Font.BOLD, 16)
        private val SECTION_FONT = Font("Microsoft YaHei", Font.BOLD, 12)
        private val NAME_FONT = Font("Microsoft YaHei", Font.PLAIN, 14)
        private val SUB_FONT = Font("Microsoft YaHei", Font.PLAIN, 11)
    }

    init {
        scrollPane = JBScrollPane(contentPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            background = BG_COLOR
        }
        setupUI()
        observeData()
    }

    private fun setupUI() {
        background = BG_COLOR
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
            background = BG_COLOR
            border = JBUI.Borders.empty(12, 16, 4, 16)

            val titlePanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JLabel("消息").apply { font = TITLE_FONT }, BorderLayout.NORTH)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 2)).apply {
                    isOpaque = false
                    add(JLabel("IP: ").apply { font = SUB_FONT; foreground = JBColor.GRAY })
                    add(JLabel(service.localIp).apply {
                        font = SUB_FONT; foreground = JBColor(ACCENT_GREEN, Color(100, 200, 130))
                    })
                }, BorderLayout.SOUTH)
            }
            add(titlePanel, BorderLayout.WEST)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(createWechatButton(AllIcons.General.Add, "添加联系人 / 搜索群") { showAddContactDialog() })
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
                background = JBColor(Color(225, 225, 225), Color(50, 50, 50))
                searchField.apply {
                    border = JBUI.Borders.empty(7, 12, 7, 12)
                    font = Font("Microsoft YaHei", Font.PLAIN, 13)
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
            background = BG_COLOR
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
                    g2d.color = JBColor(Color(210, 210, 210), Color(60, 60, 60))
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
            background = HEADER_BG
            border = JBUI.Borders.empty(8, 16, 4, 16)
            cursor = Cursor(Cursor.HAND_CURSOR)

            val arrow = if (expanded) "\u25BC" else "\u25B6"
            add(JLabel("$arrow  $title").apply {
                font = SECTION_FONT
                foreground = JBColor(Color(140, 140, 140), Color(130, 130, 130))
            }, BorderLayout.WEST)

            add(JLabel("$count").apply {
                font = SUB_FONT
                foreground = JBColor(Color(170, 170, 170), Color(110, 110, 110))
            }, BorderLayout.EAST)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) { onClick() }
            })
        }
    }

    // =============== Contact Item Panel ===============

    private fun createItemPanel(item: ChatItem): JPanel {
        val isSelected = item == selectedItem

        class HoverPanel : JPanel(BorderLayout()) {
            var hovering = false
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.color = when {
                    isSelected -> ITEM_SELECTED
                    hovering -> ITEM_HOVER
                    else -> ITEM_BG
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
        var isOnline = false

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
                nameText = peer.username
                subText = peer.ipAddress
                isOnline = peer.isOnline
                avatarPanel = createAvatarPanel(peer.username.firstOrNull()?.toString() ?: "?", false)
            }
        }

        panel.add(avatarPanel, BorderLayout.WEST)

        panel.add(JPanel(BorderLayout(0, 2)).apply {
            isOpaque = false; border = JBUI.Borders.emptyLeft(12)
            add(JLabel(nameText).apply { font = NAME_FONT }, BorderLayout.CENTER)
            add(JLabel(subText).apply { font = SUB_FONT; foreground = JBColor.GRAY }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)

        if (!isGroup) {
            panel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 14)).apply {
                isOpaque = false; preferredSize = Dimension(16, 44)
                add(object : JPanel() {
                    override fun paintComponent(g: Graphics) {
                        super.paintComponent(g)
                        val g2d = g as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2d.color = if (isOnline) Color(76, 175, 80) else Color(190, 190, 190)
                        g2d.fillOval(0, 0, 8, 8)
                    }
                }.apply { isOpaque = false; preferredSize = Dimension(8, 8) })
            }, BorderLayout.EAST)
        }

        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    selectedItem = item; onChatItemSelected(item); rebuildList()
                }
            }
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showPopupMenu(e, item) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showPopupMenu(e, item) }
            override fun mouseEntered(e: MouseEvent) { panel.hovering = true; panel.repaint() }
            override fun mouseExited(e: MouseEvent) { panel.hovering = false; panel.repaint() }
        })

        return panel
    }

    private fun createAvatarPanel(initial: String, isGroup: Boolean): JPanel {
        val peerColors = listOf(
            Color(76, 175, 80), Color(33, 150, 243), Color(156, 39, 176),
            Color(255, 152, 0), Color(233, 30, 99), Color(0, 150, 136)
        )
        val groupColor = JBColor(Color(87, 137, 213), Color(70, 120, 190))

        return object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = minOf(width, height) - 4
                val x = (width - size) / 2
                val y = (height - size) / 2

                if (isGroup) {
                    g2d.color = groupColor
                    g2d.fillRoundRect(x, y, size, size, 10, 10)
                } else {
                    val color = peerColors[Math.abs(initial.hashCode()) % peerColors.size]
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

    private fun observeData() {
        scope.launch { service.peers.collectLatest { updateData() } }
        scope.launch { service.groups.collectLatest { updateData() } }
    }

    private fun updateData() {
        SwingUtilities.invokeLater {
            groupItems.clear()
            peerItems.clear()
            service.groups.value.values.sortedByDescending { it.createdAt }.forEach {
                groupItems.add(ChatItem.GroupItem(it))
            }
            service.peers.value.values
                .sortedWith(compareByDescending<Peer> { it.isOnline }.thenByDescending { it.lastSeen })
                .forEach { peerItems.add(ChatItem.PeerItem(it)) }
            rebuildList()
            updateStatus()
        }
    }

    private fun rebuildList() {
        val query = searchField.text.trim().lowercase()

        val filteredGroups = if (query.isEmpty()) groupItems else groupItems.filter {
            it.group.name.lowercase().contains(query) || it.group.groupNumber.contains(query)
        }
        val filteredPeers = if (query.isEmpty()) peerItems else peerItems.filter {
            it.peer.username.lowercase().contains(query) || it.peer.ipAddress.contains(query)
        }

        contentPanel.removeAll()

        contentPanel.add(createSectionHeader("群聊", filteredGroups.size, isGroupExpanded) {
            isGroupExpanded = !isGroupExpanded; rebuildList()
        })
        if (isGroupExpanded) {
            filteredGroups.forEach { contentPanel.add(createItemPanel(it)) }
        }

        contentPanel.add(createSectionHeader("好友", filteredPeers.size, isPeerExpanded) {
            isPeerExpanded = !isPeerExpanded; rebuildList()
        })
        if (isPeerExpanded) {
            filteredPeers.forEach { contentPanel.add(createItemPanel(it)) }
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
        val dialog = AddContactDialog(project)
        if (dialog.showAndGet()) {
            if (dialog.isAddSelf) {
                service.addManualPeer(service.localIp, 8889, "${service.username} (自己)")
            } else {
                dialog.selectedPeer?.let { peer ->
                    service.addManualPeer(peer.ipAddress, peer.port, peer.username)
                }
            }
        }
    }

    private fun showCreateGroupDialog() {
        val currentUserId = service.currentUser?.id
        val availablePeers = peerItems.map { it.peer }
            .filter { it.id != currentUserId }

        val dialog = CreateGroupDialog(project, availablePeers)
        if (dialog.showAndGet()) {
            val memberIds = dialog.selectedPeers.map { it.id }
            service.createGroup(dialog.groupName, memberIds)
            if (dialog.shouldCreateBot && dialog.botName != null) {
                service.createBot(dialog.botName!!)
            }
        }
    }

    private fun showPopupMenu(e: MouseEvent, item: ChatItem) {
        val menu = JPopupMenu()
        when (item) {
            is ChatItem.GroupItem -> {
                menu.add(createMenuItem("群管理") {
                    GroupManageDialog(project, item.group.id).show()
                })
                if (service.isGroupOwner(item.group.id)) {
                    menu.addSeparator()
                    menu.add(createMenuItem("解散群聊", Color(220, 50, 50)) {
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
                menu.add(createMenuItem("删除联系人", Color(220, 50, 50)) {
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
        menu.show(e.component, e.x, e.y)
    }

    private fun createMenuItem(text: String, color: Color? = null, action: () -> Unit): JMenuItem {
        return JMenuItem(text).apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            if (color != null) foreground = JBColor(color, color)
            addActionListener { action() }
        }
    }

    private class PlaceholderTextField(private val placeholder: String) : JTextField() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (text.isNullOrEmpty()) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2d.color = JBColor(Color(170, 170, 170), Color(120, 120, 120))
                g2d.font = font
                val fm = g2d.fontMetrics
                g2d.drawString(placeholder, insets.left + 2, height / 2 + fm.ascent / 2 - 2)
            }
        }
    }
}

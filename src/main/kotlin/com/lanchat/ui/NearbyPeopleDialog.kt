package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.RenderingHints
import javax.swing.*

/**
 * 查找附近的人 - 扫描局域网内安装了插件且在线的用户
 */
class NearbyPeopleDialog(private val project: Project) : JDialog() {

    private val service = LanChatService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val listPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val scrollPane: JBScrollPane

    private val pendingRequestIps = mutableSetOf<String>()  // 已发送好友申请的 IP:Port
    private val friendPeerIds = mutableSetOf<String>()       // 已是好友的 peerId

    init {
        title = "查找附近的人"
        isModal = false
        size = Dimension(380, 480)
        minimumSize = Dimension(320, 300)
        isResizable = true
        setLocationRelativeTo(null)

        loadExistingData()

        val mainPanel = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(12)
            background = JBColor.PanelBackground
        }

        // 顶部：提示 + 重新扫描按钮
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel("发现附近的人").apply {
                font = ThemeManager.boldFont(1)
            }, BorderLayout.WEST)
            val refreshBtn = JButton("重新扫描").apply {
                font = ThemeManager.plainFont(-2)
                isBorderPainted = false
                isOpaque = false
                cursor = Cursor(Cursor.HAND_CURSOR)
                toolTipText = "重新扫描局域网"
                addActionListener { scan() }
            }
            add(refreshBtn, BorderLayout.EAST)
        }

        val tipPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel("显示局域网内安装了 LAN Chat 插件的用户").apply {
                font = ThemeManager.plainFont(-3)
                foreground = JBColor.GRAY
            }, BorderLayout.WEST)
        }

        val topContainer = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(headerPanel, BorderLayout.NORTH)
            add(tipPanel, BorderLayout.SOUTH)
        }
        mainPanel.add(topContainer, BorderLayout.NORTH)

        // 列表
        scrollPane = JBScrollPane(listPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            background = JBColor.PanelBackground
        }
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        contentPane = mainPanel

        // 监听 peers 变化
        scope.launch {
            service.peers.collectLatest {
                SwingUtilities.invokeLater { rebuildList() }
            }
        }

        // 监听好友申请变化
        scope.launch {
            service.friendRequests.collectLatest { requests ->
                pendingRequestIps.clear()
                requests.values.filter { it.status.name == "PENDING_SENT" }.forEach {
                    pendingRequestIps.add("${it.toIp}:${it.toPort}")
                }
                SwingUtilities.invokeLater { rebuildList() }
            }
        }

        scan()
    }

    private fun loadExistingData() {
        friendPeerIds.addAll(service.peers.value.keys.filter { 
            it != service.currentUser?.id && it != "file_transfer_assistant" 
        })
        service.friendRequests.value.values
            .filter { it.status.name == "PENDING_SENT" }
            .forEach { pendingRequestIps.add("${it.toIp}:${it.toPort}") }
    }

    private fun scan() {
        service.refreshPeers()
        listPanel.removeAll()
        listPanel.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(Box.createVerticalStrut(40))
            add(JLabel("扫描中...").apply {
                font = ThemeManager.plainFont(-1)
                foreground = JBColor.GRAY
                alignmentX = Component.CENTER_ALIGNMENT
            })
            isOpaque = false
        })
        listPanel.revalidate()
        listPanel.repaint()

        scope.launch {
            delay(2500)
            SwingUtilities.invokeLater { rebuildList() }
        }
    }

    private fun rebuildList() {
        val myId = service.currentUser?.id ?: return
        val myIp = service.localIp

        val allPeers = service.peers.value.values.toList()
        val nearbyPeers = allPeers.filter {
            it.id != myId && it.id != "file_transfer_assistant"
        }

        friendPeerIds.clear()
        friendPeerIds.addAll(allPeers
            .filter { it.id != myId && it.id != "file_transfer_assistant" }
            .map { it.id })

        listPanel.removeAll()

        if (nearbyPeers.isEmpty()) {
            listPanel.add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(Box.createVerticalStrut(40))
                add(JLabel("暂未发现附近的人").apply {
                    font = ThemeManager.plainFont(-1)
                    foreground = JBColor.GRAY
                    alignmentX = Component.CENTER_ALIGNMENT
                })
                add(Box.createVerticalStrut(8))
                add(JLabel("请确认同一局域网内有其他用户正在使用").apply {
                    font = ThemeManager.plainFont(-3)
                    foreground = JBColor.GRAY
                    alignmentX = Component.CENTER_ALIGNMENT
                })
                isOpaque = false
            })
        } else {
            val sorted = nearbyPeers.sortedWith(
                compareByDescending<Peer> { it.isOnline }.thenBy { it.username.lowercase() }
            )

            sorted.forEach { peer ->
                val ipPort = "${peer.ipAddress}:${peer.port}"
                val isAlreadyFriend = friendPeerIds.contains(peer.id)
                val hasPendingRequest = pendingRequestIps.contains(ipPort)
                val isSelf = peer.ipAddress == myIp
                listPanel.add(createPeerItem(peer, isAlreadyFriend, hasPendingRequest, isSelf))
            }
            listPanel.add(Box.createVerticalStrut(8))
        }

        listPanel.revalidate()
        listPanel.repaint()
    }

    private fun createPeerItem(
        peer: Peer,
        isAlreadyFriend: Boolean,
        hasPendingRequest: Boolean,
        isSelf: Boolean
    ): JPanel {
        val panel = object : JPanel(BorderLayout(0, 4)) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            var hovering = false
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.color = if (hovering) ThemeManager.itemHover else JBColor.PanelBackground
                g2d.fillRect(0, 0, width, height)
                super.paintComponent(g)
            }
        }.apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 12)
            preferredSize = Dimension(0, 52)
            cursor = Cursor(Cursor.HAND_CURSOR)

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { hovering = true; repaint() }
                override fun mouseExited(e: MouseEvent) { hovering = false; repaint() }
            })
        }

        // 头像
        val initial = peer.username.firstOrNull()?.toString() ?: "?"
        val avatarPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = minOf(width, height) - 4
                val x = (width - size) / 2
                val y = (height - size) / 2
                val colors = ThemeManager.avatarColors
                g2d.color = colors[Math.abs(initial.hashCode()) % colors.size]
                g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
                g2d.color = Color.WHITE
                g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 2 / 5)
                val fm = g2d.fontMetrics
                g2d.drawString(initial, x + (size - fm.stringWidth(initial)) / 2, y + (size + fm.ascent - fm.descent) / 2)
            }
        }.apply {
            isOpaque = false
            preferredSize = Dimension(36, 36)
        }
        panel.add(avatarPanel, BorderLayout.WEST)

        // 信息
        val infoPanel = JPanel(BorderLayout(0, 1)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(10)
        }
        val nameLabel = JLabel(peer.username).apply {
            font = ThemeManager.plainFont()
        }
        infoPanel.add(nameLabel, BorderLayout.NORTH)

        val statusText = when {
            isSelf -> "${peer.ipAddress} (本机)"
            peer.isOnline -> peer.ipAddress
            else -> "${peer.ipAddress} (离线)"
        }
        val statusLabel = JLabel(statusText).apply {
            font = ThemeManager.plainFont(-3)
            foreground = if (peer.isOnline) JBColor.GRAY else JBColor(Color(180, 180, 180), Color(140, 140, 140))
        }
        infoPanel.add(statusLabel, BorderLayout.CENTER)
        panel.add(infoPanel, BorderLayout.CENTER)

        // 右侧按钮
        val actionLabel: JLabel = when {
            isSelf -> JLabel("本机").apply {
                font = ThemeManager.plainFont(-3)
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(4, 12)
            }
            isAlreadyFriend -> JLabel("已添加").apply {
                font = ThemeManager.plainFont(-3)
                foreground = ThemeManager.onlineColor
                border = JBUI.Borders.empty(4, 12)
            }
            hasPendingRequest -> JLabel("待验证").apply {
                font = ThemeManager.plainFont(-3)
                foreground = JBColor(Color(200, 150, 0), Color(180, 130, 0))
                border = JBUI.Borders.empty(4, 12)
            }
            !peer.isOnline -> JLabel("离线").apply {
                font = ThemeManager.plainFont(-3)
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(4, 12)
            }
            else -> JLabel("添加").apply {
                font = ThemeManager.boldFont(-2)
                foreground = JBColor(Color(50, 130, 240), Color(70, 140, 255))
                cursor = Cursor(Cursor.HAND_CURSOR)
                border = JBUI.Borders.empty(4, 12)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        service.sendFriendRequest(peer.ipAddress, peer.port)
                        text = "已发送"
                        foreground = JBColor(Color(200, 150, 0), Color(180, 130, 0))
                        cursor = Cursor(Cursor.DEFAULT_CURSOR)
                    }
                    override fun mouseEntered(e: MouseEvent) {
                        if (text == "添加") foreground = JBColor(Color(30, 100, 200), Color(50, 120, 220))
                    }
                    override fun mouseExited(e: MouseEvent) {
                        if (text == "添加") foreground = JBColor(Color(50, 130, 240), Color(70, 140, 255))
                    }
                })
            }
        }
        panel.add(actionLabel, BorderLayout.EAST)

        return panel
    }

    override fun show() {
        isVisible = true
    }

    override fun dispose() {
        super.dispose()
        scope.cancel()
    }
}

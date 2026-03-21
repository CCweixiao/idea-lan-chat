package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Group
import com.lanchat.network.Peer
import kotlinx.coroutines.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import javax.swing.*

class GroupManageDialog(
    private val project: Project,
    private val groupId: String
) : DialogWrapper(project) {

    private val service = LanChatService.getInstance()
    private var group: Group? = null

    private val groupNameField = JTextField(20)
    private val memberListModel = DefaultListModel<Peer>()
    private val memberList = JList(memberListModel)
    private val availableContactsModel = DefaultListModel<Peer>()
    private val availableContactsList = JList(availableContactsModel)
    private val memberCountLabel = JLabel()

    private var isOwner = false
    private var debugInfo = ""
    private val globalMuteButton = JButton()
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        title = "群聊详情"
        init()
    }

    override fun dispose() {
        dialogScope.cancel()
        super.dispose()
    }

    private fun loadData() {
        group = service.getGroup(groupId)
        group?.let { g ->
            groupNameField.text = g.name
            val myId = service.currentUser?.id ?: "null"
            debugInfo = "myId=$myId, ownerId=${g.ownerId}, equals=${myId == g.ownerId}"
            isOwner = g.ownerId == myId
            groupNameField.isEditable = isOwner
            loadMembers()
            loadAvailableContacts()
        }
    }

    private fun loadMembers() {
        memberListModel.clear()
        service.getGroupMembers(groupId).forEach { memberListModel.addElement(it) }
        memberCountLabel.text = "群成员 (${memberListModel.size()})"
    }

    private fun loadAvailableContacts() {
        availableContactsModel.clear()
        group?.let { g ->
            service.peers.value.values
                .filter { !g.memberIds.contains(it.id) && it.id != service.currentUser?.id && it.id != LanChatService.FILE_TRANSFER_ASSISTANT_ID }
                .forEach { availableContactsModel.addElement(it) }
        }
    }

    override fun createCenterPanel(): JComponent {
        loadData()
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(12)
            preferredSize = Dimension(580, 520)

            add(createGroupInfoPanel(), BorderLayout.NORTH)

            val centerPanel = if (isOwner) createOwnerMemberPanel() else createViewOnlyMemberPanel()
            add(centerPanel, BorderLayout.CENTER)
        }
    }

    private fun createGroupInfoPanel(): JPanel {
        return JPanel(BorderLayout(12, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor(Color(225, 225, 225), Color(55, 55, 55))),
                JBUI.Borders.empty(0, 0, 12, 0)
            )

            val avatarPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val size = minOf(width, height) - 4
                    val x = (width - size) / 2; val y = (height - size) / 2
                    g2d.color = JBColor(Color(87, 137, 213), Color(70, 120, 190))
                    g2d.fillRoundRect(x, y, size, size, 10, 10)
                    g2d.color = Color.WHITE
                    g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 2 / 5)
                    val fm = g2d.fontMetrics
                    val initial = group?.name?.firstOrNull()?.toString() ?: "群"
                    g2d.drawString(initial,
                        x + (size - fm.stringWidth(initial)) / 2,
                        y + (size + fm.ascent - fm.descent) / 2)
                }
            }.apply { isOpaque = false; preferredSize = Dimension(56, 56) }
            add(avatarPanel, BorderLayout.WEST)

            val infoPanel = JPanel(BorderLayout(0, 4)).apply {
                isOpaque = false

                val namePanel = JPanel(BorderLayout(6, 0)).apply {
                    isOpaque = false
                    add(JLabel("群名:").apply { font = Font("Microsoft YaHei", Font.PLAIN, 13); foreground = JBColor.GRAY }, BorderLayout.WEST)
                    groupNameField.font = Font("Microsoft YaHei", Font.PLAIN, 14)
                    groupNameField.border = if (isOwner) {
                        BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(JBColor(Color(210, 210, 210), Color(70, 70, 70)), 1),
                            JBUI.Borders.empty(4, 6)
                        )
                    } else JBUI.Borders.empty(4, 6)
                    add(groupNameField, BorderLayout.CENTER)
                }
                add(namePanel, BorderLayout.CENTER)

                val metaPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false

                    val groupNumber = group?.groupNumber ?: ""
                    add(JLabel("群号: $groupNumber").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 12)
                        foreground = JBColor(Color(100, 100, 100), Color(160, 160, 160))
                    })

                    add(createCopyButton(groupNumber))

                    add(JLabel("·").apply { foreground = JBColor.GRAY })

                    add(JLabel("群主: ${getOwnerName()}").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 11); foreground = JBColor.GRAY
                    })

                    if (isOwner) {
                        add(JLabel("(你是群主)").apply {
                            font = Font("Microsoft YaHei", Font.PLAIN, 11)
                            foreground = JBColor(Color(7, 193, 96), Color(100, 200, 130))
                        })
                    }
                }
                add(metaPanel, BorderLayout.SOUTH)
            }
            add(infoPanel, BorderLayout.CENTER)
        }
    }

    private fun createCopyButton(text: String): JButton {
        return object : JButton("复制") {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background; g2d.fillRoundRect(0, 0, width - 1, height - 1, 6, 6)
                super.paintComponent(g2d)
            }
        }.apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 10)
            background = JBColor(Color(230, 230, 230), Color(60, 60, 60))
            foreground = JBColor(Color(80, 80, 80), Color(180, 180, 180))
            isBorderPainted = false; isFocusPainted = false; isOpaque = false
            preferredSize = Dimension(40, 20); cursor = Cursor(Cursor.HAND_CURSOR)
            toolTipText = "复制群号"
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                this.text = "已复制"
                Timer(1500) { this.text = "复制" }.apply { isRepeats = false; start() }
            }
        }
    }

    private fun createOwnerMemberPanel(): JPanel {
        return JPanel(BorderLayout(12, 0)).apply {
            val leftPanel = JPanel(BorderLayout(0, 6)).apply {
                preferredSize = Dimension(260, 0)
                memberCountLabel.font = Font("Microsoft YaHei", Font.BOLD, 13)
                memberCountLabel.border = JBUI.Borders.emptyBottom(4)
                add(memberCountLabel, BorderLayout.NORTH)

                memberList.cellRenderer = MemberCellRenderer(groupId)
                memberList.fixedCellHeight = 50
                memberList.background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
                add(JScrollPane(memberList).apply {
                    border = BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1)
                }, BorderLayout.CENTER)

                val leftButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    isOpaque = false; border = JBUI.Borders.emptyTop(4)
                    add(createStyledButton("移除", danger = true) { removeSelectedMember() })
                    add(createStyledButton("禁言") { showMuteDialog() })
                    add(createStyledButton("解禁") { unmuteSelectedMember() })
                }
                add(leftButtonPanel, BorderLayout.SOUTH)
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(BorderLayout(0, 6)).apply {
                val rightHeaderPanel = JPanel(BorderLayout(0, 4)).apply {
                    isOpaque = false; border = JBUI.Borders.emptyBottom(4)
                    add(JLabel("邀请联系人").apply {
                        font = Font("Microsoft YaHei", Font.BOLD, 13)
                    }, BorderLayout.NORTH)

                    globalMuteButton.font = Font("Microsoft YaHei", Font.PLAIN, 12)
                    updateGlobalMuteButton()
                    globalMuteButton.isBorderPainted = false; globalMuteButton.isFocusPainted = false
                    globalMuteButton.cursor = Cursor(Cursor.HAND_CURSOR)
                    globalMuteButton.addActionListener { toggleGlobalMute() }
                    add(globalMuteButton, BorderLayout.SOUTH)
                }
                add(rightHeaderPanel, BorderLayout.NORTH)

                availableContactsList.cellRenderer = MemberCellRenderer(null)
                availableContactsList.fixedCellHeight = 50
                availableContactsList.background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
                add(JScrollPane(availableContactsList).apply {
                    border = BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1)
                }, BorderLayout.CENTER)

                add(createStyledButton("发送入群邀请") { inviteSelectedMember() }, BorderLayout.SOUTH)
            }
            add(rightPanel, BorderLayout.CENTER)
        }
    }

    private fun createViewOnlyMemberPanel(): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            memberCountLabel.font = Font("Microsoft YaHei", Font.BOLD, 13)
            memberCountLabel.border = JBUI.Borders.emptyBottom(4)
            add(memberCountLabel, BorderLayout.NORTH)

            memberList.cellRenderer = MemberCellRenderer(groupId)
            memberList.fixedCellHeight = 50
            memberList.background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
            memberList.selectionModel = object : DefaultListSelectionModel() {
                override fun setSelectionInterval(index0: Int, index1: Int) {}
            }
            add(JScrollPane(memberList).apply {
                border = BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1)
            }, BorderLayout.CENTER)

            val buttonPanel = JPanel(BorderLayout(0, 6)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(8)

                add(JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                    isOpaque = false
                    add(JLabel("你不是群主，无法管理成员").apply {
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 11); foreground = JBColor.GRAY
                    })
                }, BorderLayout.NORTH)

                add(createStyledButton("退出群聊", danger = true) {
                    val confirm = JOptionPane.showConfirmDialog(
                        this@GroupManageDialog.window,
                        "确定要退出群聊「${group?.name}」吗？\n退出后群聊和聊天记录将被删除。",
                        "确认退群",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    )
                    if (confirm == JOptionPane.YES_OPTION) {
                        when (service.leaveGroup(groupId)) {
                            LanChatService.LeaveGroupResult.SUCCESS -> {
                                JOptionPane.showMessageDialog(
                                    this@GroupManageDialog.window, "已退出群聊", "成功",
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                                close(OK_EXIT_CODE)
                            }
                            LanChatService.LeaveGroupResult.IS_OWNER -> {
                                JOptionPane.showMessageDialog(
                                    this@GroupManageDialog.window, "你是群主，无法退群，请先解散群聊", "提示",
                                    JOptionPane.WARNING_MESSAGE
                                )
                            }
                            LanChatService.LeaveGroupResult.NOT_MEMBER -> {
                                JOptionPane.showMessageDialog(
                                    this@GroupManageDialog.window, "你已不在该群中", "提示",
                                    JOptionPane.WARNING_MESSAGE
                                )
                                close(OK_EXIT_CODE)
                            }
                            LanChatService.LeaveGroupResult.ERROR -> {
                                JOptionPane.showMessageDialog(
                                    this@GroupManageDialog.window, "退群失败", "提示",
                                    JOptionPane.WARNING_MESSAGE
                                )
                            }
                        }
                    }
                }, BorderLayout.CENTER)
            }
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }

    private fun createStyledButton(text: String, danger: Boolean = false, action: () -> Unit): JButton {
        val normalBg = if (danger) JBColor(Color(255, 235, 235), Color(80, 45, 45)) else ThemeManager.sendButtonColor
        val hoverBg = if (danger) JBColor(Color(255, 215, 215), Color(95, 50, 50)) else ThemeManager.sendButtonHoverColor
        val pressedBg = if (danger) JBColor(Color(255, 195, 195), Color(110, 55, 55)) else ThemeManager.sendButtonPressedColor
        val textColor = if (danger) JBColor(Color(200, 50, 50), Color(230, 100, 100)) else ThemeManager.sendButtonText

        return object : JButton(text) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
                g2d.color = foreground
                val fm = g2d.fontMetrics
                val tx = (width - fm.stringWidth(text)) / 2
                val ty = (height + fm.ascent - fm.descent) / 2
                g2d.drawString(text, tx, ty)
            }
        }.apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
            background = normalBg; foreground = textColor
            isBorderPainted = false; isFocusPainted = false; isOpaque = false; isContentAreaFilled = false
            cursor = Cursor(Cursor.HAND_CURSOR)
            val textWidth = getFontMetrics(font).stringWidth(text)
            preferredSize = Dimension(textWidth + 40, 32)
            addActionListener { action() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { background = hoverBg; repaint() }
                override fun mouseExited(e: MouseEvent) { background = normalBg; repaint() }
                override fun mousePressed(e: MouseEvent) { background = pressedBg; repaint() }
                override fun mouseReleased(e: MouseEvent) { background = hoverBg; repaint() }
            })
        }
    }

    private fun showMuteDialog() {
        val selectedPeer = memberList.selectedValue ?: run {
            JOptionPane.showMessageDialog(window, "请先选择要禁言的成员", "提示", JOptionPane.WARNING_MESSAGE); return
        }
        if (selectedPeer.id == group?.ownerId) {
            JOptionPane.showMessageDialog(window, "不能禁言群主", "提示", JOptionPane.WARNING_MESSAGE); return
        }
        val options = arrayOf("永久", "1分钟", "5分钟", "10分钟", "30分钟", "1小时", "6小时", "12小时", "1天", "3天", "7天")
        val choice = JOptionPane.showInputDialog(
            window, "选择禁言时长：", "禁言 ${selectedPeer.username}",
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]
        ) as? String ?: return

        val durationMs = when (choice) {
            "永久" -> -1L
            "1分钟" -> 60_000L
            "5分钟" -> 5 * 60_000L
            "10分钟" -> 10 * 60_000L
            "30分钟" -> 30 * 60_000L
            "1小时" -> 3_600_000L
            "6小时" -> 6 * 3_600_000L
            "12小时" -> 12 * 3_600_000L
            "1天" -> 86_400_000L
            "3天" -> 3 * 86_400_000L
            "7天" -> 7 * 86_400_000L
            else -> return
        }
        service.muteUser(groupId, selectedPeer.id, durationMs)
        loadMembers()
        JOptionPane.showMessageDialog(window, "${selectedPeer.username} 已被禁言", "成功", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun unmuteSelectedMember() {
        val selectedPeer = memberList.selectedValue ?: run {
            JOptionPane.showMessageDialog(window, "请先选择要解禁的成员", "提示", JOptionPane.WARNING_MESSAGE); return
        }
        if (group?.mutedMembers?.containsKey(selectedPeer.id) != true) {
            JOptionPane.showMessageDialog(window, "${selectedPeer.username} 未被禁言", "提示", JOptionPane.INFORMATION_MESSAGE); return
        }
        service.unmuteUser(groupId, selectedPeer.id)
        loadMembers()
        JOptionPane.showMessageDialog(window, "${selectedPeer.username} 已解除禁言", "成功", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun toggleGlobalMute() {
        val g = group ?: return
        val newState = !g.globalMute
        val confirm = JOptionPane.showConfirmDialog(
            window, if (newState) "开启全员禁言后，除群主外所有成员将不能发言，确认？" else "确认关闭全员禁言？",
            "全员禁言", JOptionPane.YES_NO_OPTION
        )
        if (confirm == JOptionPane.YES_OPTION) {
            service.setGlobalMute(groupId, newState)
            group = service.getGroup(groupId)
            updateGlobalMuteButton()
        }
    }

    private fun updateGlobalMuteButton() {
        val g = group
        if (g != null && g.globalMute) {
            globalMuteButton.text = "🔇 全员禁言已开启（点击关闭）"
            globalMuteButton.foreground = JBColor(Color(200, 50, 50), Color(230, 100, 100))
        } else {
            globalMuteButton.text = "🔊 全员禁言已关闭（点击开启）"
            globalMuteButton.foreground = JBColor(Color(100, 100, 100), Color(160, 160, 160))
        }
    }

    private fun inviteSelectedMember() {
        val selectedPeer = availableContactsList.selectedValue ?: return
        val statusLabel = JLabel("正在发送邀请给 ${selectedPeer.username}，等待对方确认...")
        statusLabel.font = Font("Microsoft YaHei", Font.PLAIN, 12)
        statusLabel.foreground = JBColor(Color(100, 100, 100), Color(160, 160, 160))

        val progressDialog = JDialog(window as? Frame, "邀请中", false)
        progressDialog.apply {
            layout = BorderLayout()
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(20)
                add(statusLabel, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            setSize(350, 100)
            setLocationRelativeTo(window)
        }
        progressDialog.isVisible = true

        dialogScope.launch {
            val success = service.inviteToGroup(groupId, selectedPeer.id)
            SwingUtilities.invokeLater {
                progressDialog.dispose()
                if (success) {
                    JOptionPane.showMessageDialog(
                        window, "入群邀请已成功送达 ${selectedPeer.username}", "邀请成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        window, "邀请发送失败，${selectedPeer.username} 可能不在线或网络异常（10秒未收到反馈）", "邀请失败",
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            }
        }
    }

    private fun removeSelectedMember() {
        val selectedPeer = memberList.selectedValue ?: return
        if (selectedPeer.id == group?.ownerId) {
            JOptionPane.showMessageDialog(this.window, "不能移除群主", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        val confirm = JOptionPane.showConfirmDialog(
            this.window, "确定要移除「${selectedPeer.username}」吗？", "确认", JOptionPane.YES_NO_OPTION
        )
        if (confirm == JOptionPane.YES_OPTION && service.removeGroupMember(groupId, selectedPeer.id)) {
            loadMembers(); loadAvailableContacts()
        }
    }

    private fun getOwnerName(): String {
        val ownerId = group?.ownerId ?: return "未知"
        if (ownerId == service.currentUser?.id) return service.username
        return service.peers.value[ownerId]?.username ?: "未知"
    }

    override fun createActions(): Array<Action> {
        return if (isOwner) {
            arrayOf(
                object : DialogWrapperAction("保存") {
                    override fun doAction(e: ActionEvent?) {
                        val newName = groupNameField.text.trim()
                        if (newName.isEmpty()) {
                            JOptionPane.showMessageDialog(window, "群名称不能为空", "提示", JOptionPane.WARNING_MESSAGE)
                            return
                        }
                        if (newName.length > 20) {
                            JOptionPane.showMessageDialog(window, "群名称不能超过20个字符", "提示", JOptionPane.WARNING_MESSAGE)
                            return
                        }
                        if (newName != group?.name) {
                            service.updateGroupName(groupId, newName)
                        }
                        close(OK_EXIT_CODE)
                    }
                },
                cancelAction
            )
        } else {
            arrayOf(object : DialogWrapperAction("关闭") {
                override fun doAction(e: ActionEvent?) { close(OK_EXIT_CODE) }
            })
        }
    }

    private class MemberCellRenderer(private val groupId: String?) : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel(); private val roleLabel = JLabel(); private val ipLabel = JLabel()
        private val peerColors = listOf(Color(76, 175, 80), Color(33, 150, 243), Color(156, 39, 176), Color(255, 152, 0), Color(233, 30, 99), Color(0, 150, 136))
        private var avatarInitial = "?"; private var avatarColor = peerColors[0]
        private val avatarPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g); val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = minOf(width, height) - 4; val x = (width - size) / 2; val y = (height - size) / 2
                g2d.color = avatarColor
                g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
                g2d.color = Color.WHITE; g2d.font = Font("Microsoft YaHei", Font.BOLD, 13)
                val fm = g2d.fontMetrics
                g2d.drawString(avatarInitial, x + (size - fm.stringWidth(avatarInitial)) / 2, y + (size + fm.ascent - fm.descent) / 2)
            }
        }.apply { isOpaque = false; preferredSize = Dimension(36, 36) }

        init {
            isOpaque = true; border = JBUI.Borders.empty(6, 10)
            add(avatarPanel, BorderLayout.WEST)
            add(JPanel(BorderLayout(0, 1)).apply {
                isOpaque = false; border = JBUI.Borders.emptyLeft(10)
                val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false; nameLabel.font = Font("Microsoft YaHei", Font.PLAIN, 13); add(nameLabel)
                    roleLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10); add(roleLabel)
                }
                add(topRow, BorderLayout.CENTER)
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11); ipLabel.foreground = JBColor.GRAY; add(ipLabel, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(list: JList<out Peer>, peer: Peer, index: Int, isSelected: Boolean, cellHasFocus: Boolean): JPanel {
            nameLabel.text = peer.username; ipLabel.text = "${peer.ipAddress}:${peer.port}"
            avatarInitial = peer.username.firstOrNull()?.toString() ?: "?"
            avatarColor = peerColors[Math.abs(avatarInitial.hashCode()) % peerColors.size]; avatarPanel.repaint()

            if (groupId != null) {
                val svc = LanChatService.getInstance()
                val grp = svc.getGroup(groupId)
                when {
                    grp != null && grp.ownerId == peer.id -> {
                        roleLabel.text = "群主"; roleLabel.foreground = JBColor(Color(255, 152, 0), Color(255, 180, 50)); roleLabel.isVisible = true
                    }
                    grp != null && grp.isMuted(peer.id) -> {
                        roleLabel.text = "🔇 已禁言"; roleLabel.foreground = JBColor(Color(200, 50, 50), Color(230, 100, 100)); roleLabel.isVisible = true
                    }
                    grp != null && peer.id == svc.currentUser?.id -> {
                        roleLabel.text = "我"; roleLabel.foreground = JBColor(Color(7, 193, 96), Color(100, 200, 130)); roleLabel.isVisible = true
                    }
                    else -> roleLabel.isVisible = false
                }
            } else roleLabel.isVisible = false

            background = if (isSelected) JBColor(Color(215, 230, 250), Color(55, 65, 85))
            else JBColor(Color(250, 250, 250), Color(45, 45, 45))
            return this
        }
    }
}

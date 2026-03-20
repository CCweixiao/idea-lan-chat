package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Group
import com.lanchat.network.Peer
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

    init {
        title = "群聊详情"
        init()
    }

    private fun loadData() {
        group = service.getGroup(groupId)
        group?.let { g ->
            groupNameField.text = g.name
            val myId = service.currentUser?.id ?: "null"
            debugInfo = "myId=$myId, ownerId=${g.ownerId}, equals=${myId == g.ownerId}"
            isOwner = service.isGroupOwner(groupId)
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
                .filter { !g.memberIds.contains(it.id) && it.id != service.currentUser?.id }
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

                add(createStyledButton("移除选中成员", Color(220, 50, 50)) { removeSelectedMember() }, BorderLayout.SOUTH)
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(BorderLayout(0, 6)).apply {
                add(JLabel("邀请联系人").apply {
                    font = Font("Microsoft YaHei", Font.BOLD, 13)
                    border = JBUI.Borders.emptyBottom(4)
                }, BorderLayout.NORTH)

                availableContactsList.cellRenderer = MemberCellRenderer(null)
                availableContactsList.fixedCellHeight = 50
                availableContactsList.background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
                add(JScrollPane(availableContactsList).apply {
                    border = BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1)
                }, BorderLayout.CENTER)

                add(createStyledButton("发送入群邀请", Color(7, 193, 96)) { inviteSelectedMember() }, BorderLayout.SOUTH)
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

            add(JLabel("你不是群主，无法管理成员 ($debugInfo)").apply {
                font = Font("Microsoft YaHei", Font.PLAIN, 11); foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.CENTER; border = JBUI.Borders.emptyTop(8)
            }, BorderLayout.SOUTH)
        }
    }

    private fun createStyledButton(text: String, color: Color, action: () -> Unit): JButton {
        return object : JButton(text) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background; g2d.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
                super.paintComponent(g2d)
            }
        }.apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 12)
            background = color; foreground = Color.WHITE
            isBorderPainted = false; isFocusPainted = false; isOpaque = false
            cursor = Cursor(Cursor.HAND_CURSOR); preferredSize = Dimension(0, 30)
            addActionListener { action() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { background = color.darker(); repaint() }
                override fun mouseExited(e: MouseEvent) { background = color; repaint() }
            })
        }
    }

    private fun inviteSelectedMember() {
        val selectedPeer = availableContactsList.selectedValue ?: return
        service.inviteToGroup(groupId, selectedPeer.id)
        JOptionPane.showMessageDialog(
            this.window, "已发送入群邀请给 ${selectedPeer.username}，等待对方确认", "邀请已发送",
            JOptionPane.INFORMATION_MESSAGE
        )
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
                        if (newName.isNotEmpty() && newName != group?.name) {
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

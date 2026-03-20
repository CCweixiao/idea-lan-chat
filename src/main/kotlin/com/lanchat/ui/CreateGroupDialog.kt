package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Peer
import java.awt.*
import java.awt.geom.Ellipse2D
import javax.swing.*

class CreateGroupDialog(
    private val project: Project,
    private val availablePeers: List<Peer>
) : DialogWrapper(project) {

    private val service = LanChatService.getInstance()

    private val groupNameField = JTextField(20)
    private val peerListModel = DefaultListModel<Peer>()
    private val peerList = JList(peerListModel)

    val groupName: String get() = groupNameField.text.trim()
    val selectedPeers: List<Peer> get() = peerList.selectedValuesList.ifEmpty { emptyList() }

    var shouldCreateBot = false
        private set
    var botName: String? = null
        private set

    private val botCheckBox = JCheckBox("同时创建群机器人")
    private val botNameField = JTextField(15)

    init {
        title = "创建群聊"
        init()
        setupUI()
    }

    private fun setupUI() {
        val currentUserId = service.currentUser?.id
        availablePeers.filter { it.id != currentUserId }.forEach { peerListModel.addElement(it) }
        peerList.cellRenderer = PeerListCellRenderer()
        botCheckBox.addActionListener {
            botNameField.isEnabled = botCheckBox.isSelected
            if (botCheckBox.isSelected && botNameField.text.isEmpty()) botNameField.text = "小助手"
        }
        botNameField.isEnabled = false
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(450, 400)

            val namePanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("群名称:").apply { font = Font("Microsoft YaHei", Font.PLAIN, 13) }, BorderLayout.WEST)
                groupNameField.font = Font("Microsoft YaHei", Font.PLAIN, 14)
                groupNameField.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor(Color(210, 210, 210), Color(70, 70, 70)), 1),
                    JBUI.Borders.empty(6, 8)
                )
                add(groupNameField, BorderLayout.CENTER)
            }
            add(namePanel, BorderLayout.NORTH)

            val memberPanel = JPanel(BorderLayout(0, 8)).apply {
                add(JLabel("选择成员 (你将自动成为群主):").apply {
                    font = Font("Microsoft YaHei", Font.BOLD, 13)
                }, BorderLayout.NORTH)
                peerList.background = JBColor(Color(250, 250, 250), Color(45, 45, 45))
                peerList.fixedCellHeight = 52
                add(JScrollPane(peerList).apply {
                    border = BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1)
                }, BorderLayout.CENTER)
                add(JLabel("提示：按住Ctrl多选成员，不选则创建只有你自己的群").apply {
                    foreground = JBColor.GRAY; font = Font("Microsoft YaHei", Font.PLAIN, 11)
                }, BorderLayout.SOUTH)
            }
            add(memberPanel, BorderLayout.CENTER)

            val botPanel = JPanel(BorderLayout(0, 6)).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor(Color(220, 220, 220), Color(60, 60, 60))),
                    JBUI.Borders.emptyTop(8)
                )
                botCheckBox.font = Font("Microsoft YaHei", Font.PLAIN, 13)
                add(botCheckBox, BorderLayout.NORTH)
                val botNamePanel = JPanel(BorderLayout(6, 0)).apply {
                    border = JBUI.Borders.emptyLeft(24)
                    add(JLabel("机器人名称:").apply { font = Font("Microsoft YaHei", Font.PLAIN, 12) }, BorderLayout.WEST)
                    botNameField.font = Font("Microsoft YaHei", Font.PLAIN, 13)
                    add(botNameField, BorderLayout.CENTER)
                }
                add(botNamePanel, BorderLayout.CENTER)
            }
            add(botPanel, BorderLayout.SOUTH)
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (groupName.isEmpty()) return ValidationInfo("请输入群名称", groupNameField)
        if (botCheckBox.isSelected && botNameField.text.trim().isEmpty())
            return ValidationInfo("请输入机器人名称", botNameField)
        return null
    }

    override fun doOKAction() {
        shouldCreateBot = botCheckBox.isSelected
        botName = if (shouldCreateBot) botNameField.text.trim() else null
        super.doOKAction()
    }

    private class PeerListCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        private val peerColors = ThemeManager.avatarColors
        private var avatarInitial = "?"
        private var avatarColor = peerColors[0]
        private val avatarPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = minOf(width, height) - 4
                val x = (width - size) / 2; val y = (height - size) / 2
                g2d.color = avatarColor
                g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
                g2d.color = Color.WHITE; g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 2 / 5)
                val fm = g2d.fontMetrics
                g2d.drawString(avatarInitial,
                    x + (size - fm.stringWidth(avatarInitial)) / 2,
                    y + (size + fm.ascent - fm.descent) / 2)
            }
        }.apply { isOpaque = false; preferredSize = Dimension(36, 36) }

        init {
            isOpaque = true; border = JBUI.Borders.empty(6, 10)
            add(avatarPanel, BorderLayout.WEST)
            add(JPanel(BorderLayout(0, 2)).apply {
                isOpaque = false; border = JBUI.Borders.emptyLeft(10)
                nameLabel.font = Font("Microsoft YaHei", Font.PLAIN, 13); add(nameLabel, BorderLayout.CENTER)
                ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10); ipLabel.foreground = JBColor.GRAY
                add(ipLabel, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out Peer>, peer: Peer, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): JPanel {
            nameLabel.text = peer.username; ipLabel.text = "${peer.ipAddress}:${peer.port}"
            avatarInitial = peer.username.firstOrNull()?.toString() ?: "?"
            avatarColor = peerColors[Math.abs(avatarInitial.hashCode()) % peerColors.size]
            avatarPanel.repaint()
            background = if (isSelected) JBColor(Color(232, 240, 254), Color(60, 60, 80))
            else JBColor.PanelBackground
            return this
        }
    }
}

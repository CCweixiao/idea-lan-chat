package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.Bot
import com.lanchat.network.Group
import com.lanchat.network.Peer
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = LanChatService.getInstance()

    private val messagePanel: JPanel
    private val messageScrollPane: JBScrollPane
    private val inputArea = JTextArea(3, 20)
    private var currentPeer: Peer? = null
    private var currentGroup: Group? = null
    private var currentBot: Bot? = null

    private val titleLabel = JLabel()
    private val statusLabel = JLabel()
    private val headerButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
    private val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))

    private val chatCard = JPanel(BorderLayout())
    private val emptyCard = JPanel(BorderLayout())
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val inputPanel: JPanel

    companion object {
        private const val MAX_BUBBLE_WIDTH = 300
        private val MSG_FONT = Font("Microsoft YaHei", Font.PLAIN, 14)
        private val NAME_FONT = Font("Microsoft YaHei", Font.PLAIN, 11)
        private val TIME_FONT = Font("Microsoft YaHei", Font.PLAIN, 10)
        private val MENTION_FONT = Font("Microsoft YaHei", Font.BOLD, 12)

        private val fontMetricsCache: FontMetrics by lazy {
            val bi = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val g = bi.createGraphics()
            g.font = MSG_FONT
            val fm = g.fontMetrics
            g.dispose()
            fm
        }
    }

    private val messageListener: (Message) -> Unit = { message ->
        if (message.senderId != service.currentUser?.id) {
            val currentChatId = currentBot?.id ?: currentPeer?.id ?: currentGroup?.id
            if (currentChatId != null) {
                val msgChatId = when {
                    !message.groupId.isNullOrEmpty() -> message.groupId
                    else -> message.senderId
                }
                if (msgChatId == currentChatId) {
                    addMessageToPanel(message)
                }
            }
        }
    }

    init {
        messagePanel = JPanel()
        messagePanel.layout = BoxLayout(messagePanel, BoxLayout.Y_AXIS)
        messagePanel.background = JBColor(Color(240, 240, 240), Color(30, 30, 30))

        messageScrollPane = JBScrollPane(messagePanel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            background = JBColor(Color(240, 240, 240), Color(30, 30, 30))
        }

        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = EmptyBorder(8, 10, 8, 10)
        inputArea.font = MSG_FONT
        inputArea.background = JBColor(Color(255, 255, 255), Color(50, 50, 50))

        inputPanel = createInputPanel()
        setupUI()
        service.addMessageListener(messageListener)
    }

    private fun setupUI() {
        background = JBColor(Color(240, 240, 240), Color(30, 30, 30))

        emptyCard.background = JBColor(Color(240, 240, 240), Color(30, 30, 30))
        emptyCard.add(JLabel("选择联系人或群聊开始聊天").apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 15)
            foreground = JBColor(Color(170, 170, 170), Color(100, 100, 100))
            horizontalAlignment = SwingConstants.CENTER
        }, BorderLayout.CENTER)

        chatCard.apply {
            isOpaque = false
            add(createHeader(), BorderLayout.NORTH)
            add(messageScrollPane, BorderLayout.CENTER)
            add(inputPanel, BorderLayout.SOUTH)
        }

        cardPanel.apply {
            add(emptyCard, "empty")
            add(chatCard, "chat")
        }
        cardLayout.show(cardPanel, "empty")
        add(cardPanel, BorderLayout.CENTER)
        setupShortcuts()
    }

    private fun createHeader(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor(Color(245, 245, 245), Color(45, 45, 45))
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor(Color(225, 225, 225), Color(50, 50, 50))),
                JBUI.Borders.empty(10, 16, 10, 16)
            )
            preferredSize = Dimension(0, 52)

            val infoPanel = JPanel(BorderLayout(0, 1)).apply {
                isOpaque = false
                titleLabel.font = Font("Microsoft YaHei", Font.BOLD, 15)
                add(titleLabel, BorderLayout.CENTER)
                statusLabel.font = NAME_FONT
                statusLabel.foreground = JBColor.GRAY
                add(statusLabel, BorderLayout.SOUTH)
            }
            add(infoPanel, BorderLayout.WEST)
            headerButtonPanel.isOpaque = false
            add(headerButtonPanel, BorderLayout.EAST)
        }
    }

    private fun updateHeaderButtons() {
        headerButtonPanel.removeAll()
        if (currentGroup != null) {
            val group = currentGroup!!
            val numberText = if (group.groupNumber.isNotEmpty()) " · ${group.groupNumber}" else ""
            headerButtonPanel.add(object : JButton("成员(${group.getMemberCount()})$numberText") {
                override fun paintComponent(g: Graphics) {
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    if (model.isRollover) {
                        g2d.color = JBColor(Color(230, 230, 230), Color(55, 55, 55))
                        g2d.fillRoundRect(2, 2, width - 4, height - 4, 6, 6)
                    }
                    super.paintComponent(g2d)
                }
            }.apply {
                font = Font("Microsoft YaHei", Font.PLAIN, 12)
                isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
                foreground = JBColor(Color(100, 100, 100), Color(170, 170, 170))
                cursor = Cursor(Cursor.HAND_CURSOR); toolTipText = "查看群成员"
                isRolloverEnabled = true
                addActionListener { showGroupManageDialog() }
            })
        }
        headerButtonPanel.add(createHeaderButton(AllIcons.General.Settings, "设置") { showProfileDialog() })
        headerButtonPanel.revalidate(); headerButtonPanel.repaint()
    }

    private fun updateToolbar() {
        toolbarPanel.removeAll()
        toolbarPanel.add(createToolbarIcon(AllIcons.General.Web, "发送图片") { sendImage() })
        toolbarPanel.add(createToolbarIcon(AllIcons.FileTypes.Any_type, "发送文件") { sendFile() })
        if (currentGroup != null) {
            toolbarPanel.add(Box.createHorizontalStrut(4))
            toolbarPanel.add(createToolbarIcon(AllIcons.Actions.GroupByModule, "@成员") { showMentionDialog() })
        }
        toolbarPanel.revalidate(); toolbarPanel.repaint()
    }

    private fun createHeaderButton(icon: Icon, tooltip: String, action: () -> Unit) = JButton(icon).apply {
        toolTipText = tooltip; isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
        margin = Insets(4, 4, 4, 4); preferredSize = Dimension(28, 28)
        cursor = Cursor(Cursor.HAND_CURSOR); addActionListener { action() }
    }

    private fun createInputPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor(Color(245, 245, 245), Color(45, 45, 45))
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor(Color(225, 225, 225), Color(50, 50, 50))),
                JBUI.Borders.empty(6, 12, 8, 12)
            )
            toolbarPanel.isOpaque = false; toolbarPanel.border = JBUI.Borders.emptyBottom(4)
            toolbarPanel.add(createToolbarIcon(AllIcons.General.Web, "发送图片") { sendImage() })
            toolbarPanel.add(createToolbarIcon(AllIcons.FileTypes.Any_type, "发送文件") { sendFile() })
            toolbarPanel.add(createToolbarIcon(AllIcons.Actions.Preview, "查看聊天记录") { showChatHistory() })
            add(toolbarPanel, BorderLayout.NORTH)

            val inputRow = JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(JBScrollPane(inputArea).apply {
                    border = BorderFactory.createLineBorder(JBColor(Color(215, 215, 215), Color(60, 60, 60)), 1)
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    preferredSize = Dimension(0, 60)
                }, BorderLayout.CENTER)

                val sendButton = object : JButton("发送") {
                    override fun paintComponent(g: Graphics) {
                        val g2d = g as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2d.color = background; g2d.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
                        super.paintComponent(g2d)
                    }
                }.apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 13)
                    background = Color(7, 193, 96); foreground = Color.WHITE
                    isBorderPainted = false; isFocusPainted = false; isOpaque = false
                    preferredSize = Dimension(64, 34); cursor = Cursor(Cursor.HAND_CURSOR)
                    addActionListener { sendMessage() }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseEntered(e: MouseEvent) { background = Color(6, 173, 86); repaint() }
                        override fun mouseExited(e: MouseEvent) { background = Color(7, 193, 96); repaint() }
                        override fun mousePressed(e: MouseEvent) { background = Color(5, 153, 70); repaint() }
                        override fun mouseReleased(e: MouseEvent) { background = Color(6, 173, 86); repaint() }
                    })
                }
                add(JPanel(BorderLayout()).apply { isOpaque = false; add(sendButton, BorderLayout.SOUTH) }, BorderLayout.EAST)
            }
            add(inputRow, BorderLayout.CENTER)
        }
    }

    private fun createToolbarIcon(icon: Icon, tooltip: String, action: () -> Unit) = JButton(icon).apply {
        toolTipText = tooltip; isBorderPainted = false; isContentAreaFilled = false
        isFocusPainted = false; isOpaque = false; margin = Insets(3, 5, 3, 5)
        preferredSize = Dimension(26, 26); cursor = Cursor(Cursor.HAND_CURSOR)
        addActionListener { action() }
    }

    private fun setupShortcuts() {
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send")
        inputArea.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = sendMessage()
        })
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "newline")
        inputArea.actionMap.put("newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) { inputArea.append("\n") }
        })
    }

    // =============== Public API ===============

    fun setCurrentPeer(peer: Peer?) {
        currentPeer = peer; currentGroup = null; currentBot = peer?.let { service.getBot(it.id) }
        SwingUtilities.invokeLater {
            if (peer != null) {
                if (currentBot != null) {
                    titleLabel.text = "${currentBot!!.name} [BOT]"
                    statusLabel.text = "机器人 · 自动回复"
                    statusLabel.foreground = JBColor(Color(100, 100, 200), Color(150, 130, 230))
                } else {
                    titleLabel.text = peer.username
                    statusLabel.text = if (peer.isOnline) "在线 · ${peer.ipAddress}" else "离线 · ${peer.ipAddress}"
                    statusLabel.foreground = if (peer.isOnline) JBColor(Color(76, 175, 80), Color(100, 200, 130)) else JBColor.GRAY
                }
                updateHeaderButtons(); updateToolbar()
                cardLayout.show(cardPanel, "chat"); loadChatHistory(peer.id)
            } else { cardLayout.show(cardPanel, "empty") }
        }
    }

    fun setCurrentGroup(group: Group?) {
        currentGroup = group; currentPeer = null; currentBot = null
        SwingUtilities.invokeLater {
            if (group != null) {
                titleLabel.text = group.name
                statusLabel.text = "${group.getMemberCount()} 位成员"; statusLabel.foreground = JBColor.GRAY
                updateHeaderButtons(); updateToolbar()
                cardLayout.show(cardPanel, "chat"); loadChatHistory(group.id)
            } else { cardLayout.show(cardPanel, "empty") }
        }
    }

    fun clearChat() {
        currentPeer = null; currentGroup = null; currentBot = null
        SwingUtilities.invokeLater { cardLayout.show(cardPanel, "empty"); clearChatDisplay() }
    }

    // =============== Chat History ===============

    private fun loadChatHistory(chatId: String) {
        messagePanel.removeAll(); lastDisplayedTimestamp = 0
        val messages = service.getChatHistory(chatId)
        if (messages.isEmpty()) {
            messagePanel.add(JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(JLabel("暂无消息，发送第一条消息吧").apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 13)
                    foreground = JBColor(Color(180, 180, 180), Color(100, 100, 100))
                })
            })
        } else {
            var prevTs = 0L
            for (msg in messages) {
                addTimeSeparatorIfNeeded(msg.timestamp, prevTs); prevTs = msg.timestamp
                messagePanel.add(createMessageRow(msg))
            }
            lastDisplayedTimestamp = prevTs
        }
        messagePanel.add(Box.createVerticalGlue())
        messagePanel.revalidate(); messagePanel.repaint(); scrollToBottom()
    }

    /**
     * 显示聊天记录对话框（类似微信）
     */
    private fun showChatHistory() {
        val chatId = currentPeer?.id ?: currentGroup?.id
        if (chatId == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个联系人或群组", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val messages = service.getChatHistory(chatId)
        if (messages.isEmpty()) {
            JOptionPane.showMessageDialog(this, "暂无聊天记录", "聊天记录", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        // 创建聊天记录对话框
        val dialog = JDialog(SwingUtilities.getWindowAncestor(this), "聊天记录", Dialog.ModalityType.MODELESS)
        dialog.apply {
            preferredSize = Dimension(500, 600)
            layout = BorderLayout()

            // 消息列表
            val historyPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = JBColor(Color(250, 250, 250), Color(40, 40, 40))
            }

            var prevTs = 0L
            for (msg in messages) {
                // 时间分隔
                if (prevTs == 0L || msg.timestamp - prevTs > 5 * 60 * 1000) {
                    historyPanel.add(JPanel(FlowLayout(FlowLayout.CENTER)).apply {
                        isOpaque = false
                        border = JBUI.Borders.empty(6, 0)
                        add(JLabel(formatTimeSeparator(msg.timestamp)).apply {
                            font = Font("Microsoft YaHei", Font.PLAIN, 11)
                            foreground = JBColor.GRAY
                        })
                    })
                }
                prevTs = msg.timestamp

                // 消息行
                val isSentByMe = msg.senderId == service.currentUser?.id
                historyPanel.add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(4, 12)
                    add(JLabel("${msg.senderName ?: "未知"}: ${msg.content}").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 13)
                        foreground = if (isSentByMe) JBColor(Color(7, 193, 96), Color(100, 200, 130)) else JBColor.BLACK
                    }, BorderLayout.CENTER)
                    add(JLabel(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))).apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 10)
                        foreground = JBColor.GRAY
                    }, BorderLayout.EAST)
                })
            }

            add(JScrollPane(historyPanel).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }, BorderLayout.CENTER)

            // 底部操作栏
            val bottomPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                background = JBColor.PanelBackground
                border = JBUI.Borders.empty(8)
                add(JButton("清空聊天记录").apply {
                    foreground = Color(220, 50, 50)
                    addActionListener {
                        val confirm = JOptionPane.showConfirmDialog(
                            dialog, "确定要清空所有聊天记录吗？", "确认", JOptionPane.YES_NO_OPTION
                        )
                        if (confirm == JOptionPane.YES_OPTION) {
                            service.clearChatHistory(chatId)
                            loadChatHistory(chatId)
                            dialog.dispose()
                            JOptionPane.showMessageDialog(this@ChatPanel, "聊天记录已清空", "提示", JOptionPane.INFORMATION_MESSAGE)
                        }
                    }
                })
                add(JButton("关闭").apply {
                    addActionListener { dialog.dispose() }
                })
            }
            add(bottomPanel, BorderLayout.SOUTH)

            pack()
            setLocationRelativeTo(this@ChatPanel)
        }
        dialog.isVisible = true
    }

    private var lastDisplayedTimestamp = 0L

    private fun addTimeSeparatorIfNeeded(timestamp: Long, prevTimestamp: Long) {
        if (prevTimestamp == 0L || timestamp - prevTimestamp > 5 * 60 * 1000) {
            val sep = object : JPanel(FlowLayout(FlowLayout.CENTER)) {
                override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            }.apply {
                isOpaque = false; border = JBUI.Borders.empty(6, 0)
                add(JLabel(formatTimeSeparator(timestamp)).apply {
                    font = NAME_FONT; foreground = JBColor(Color(180, 180, 180), Color(110, 110, 110))
                })
            }
            messagePanel.add(sep)
        }
    }

    private fun formatTimeSeparator(ts: Long): String {
        val now = Calendar.getInstance(); val mt = Calendar.getInstance().apply { timeInMillis = ts }
        return when {
            now.get(Calendar.YEAR) == mt.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == mt.get(Calendar.DAY_OF_YEAR) ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
            now.get(Calendar.YEAR) == mt.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - mt.get(Calendar.DAY_OF_YEAR) == 1 ->
                "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
            now.get(Calendar.YEAR) == mt.get(Calendar.YEAR) ->
                SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(ts))
            else -> SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(Date(ts))
        }
    }

    // =============== Send Messages ===============

    private var pendingMentionUserIds: List<String> = emptyList()
    private var pendingMentionAll = false

    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) { inputArea.requestFocus(); return }

        currentBot?.let { bot ->
            service.sendBotMessage(bot.id, text); inputArea.text = ""
            addMessageToPanel(Message(type = MessageType.TEXT, senderId = service.currentUser?.id ?: "",
                receiverId = bot.id, content = text, senderName = service.username)); return
        }
        currentGroup?.let { group ->
            val mIds = pendingMentionUserIds.toList(); val mAll = pendingMentionAll
            pendingMentionUserIds = emptyList(); pendingMentionAll = false
            service.sendGroupMessage(group.id, text, mIds, mAll); inputArea.text = ""
            val type = when { mAll -> MessageType.MENTION_ALL; mIds.isNotEmpty() -> MessageType.MENTION_MEMBER; else -> MessageType.GROUP_CHAT }
            addMessageToPanel(Message(type = type, senderId = service.currentUser?.id ?: "", receiverId = group.id,
                content = text, senderName = service.username, groupId = group.id, mentionedUserIds = mIds, mentionAll = mAll)); return
        }
        currentPeer?.let { peer ->
            service.sendTextMessage(peer.id, text); inputArea.text = ""
            addMessageToPanel(Message(type = MessageType.TEXT, senderId = service.currentUser?.id ?: "",
                receiverId = peer.id, content = text, senderName = service.username))
        } ?: JOptionPane.showMessageDialog(this, "请先选择一个联系人或群聊", "提示", JOptionPane.WARNING_MESSAGE)
    }

    private fun sendImage() {
        val fc = JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("图片文件", "jpg", "jpeg", "png", "gif") }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentPeer?.let { service.sendImageMessage(it.id, fc.selectedFile.absolutePath); addImageMessageToPanel(fc.selectedFile) }
                ?: JOptionPane.showMessageDialog(this, "请先选择一个联系人", "提示", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun sendFile() {
        val fc = JFileChooser()
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentPeer?.let { service.sendFileMessage(it.id, fc.selectedFile.absolutePath, fc.selectedFile.name); addFileMessageToPanel(fc.selectedFile) }
                ?: JOptionPane.showMessageDialog(this, "请先选择一个联系人", "提示", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun showMentionDialog() {
        val group = currentGroup ?: return
        val dialog = MentionMembersDialog(project, group.id)
        if (dialog.showAndGet()) {
            pendingMentionUserIds = dialog.selectedMemberIds; pendingMentionAll = dialog.isMentionAll
            val mentionText = if (dialog.isMentionAll) "@全体成员 " else
                dialog.selectedMemberIds.mapNotNull { id -> service.getGroupMembers(group.id).find { it.id == id }?.username }.joinToString(" ") { "@$it " }
            if (mentionText.isNotEmpty()) { inputArea.append(mentionText); inputArea.requestFocus() }
        }
    }

    // =============== Message Display ===============

    fun addMessageToPanel(message: Message) {
        SwingUtilities.invokeLater {
            addTimeSeparatorIfNeeded(message.timestamp, lastDisplayedTimestamp)
            lastDisplayedTimestamp = message.timestamp
            val count = messagePanel.componentCount
            if (count > 0 && messagePanel.getComponent(count - 1) is Box.Filler) messagePanel.remove(count - 1)
            messagePanel.add(createMessageRow(message))
            messagePanel.add(Box.createVerticalGlue())
            messagePanel.revalidate(); messagePanel.repaint(); scrollToBottom()
        }
    }

    private fun addImageMessageToPanel(file: File) {
        SwingUtilities.invokeLater {
            messagePanel.add(createImageMessageComponent(file))
            messagePanel.revalidate(); messagePanel.repaint(); scrollToBottom()
        }
    }

    private fun addFileMessageToPanel(file: File) {
        SwingUtilities.invokeLater {
            messagePanel.add(createFileMessageComponent(file))
            messagePanel.revalidate(); messagePanel.repaint(); scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater { messageScrollPane.verticalScrollBar.let { it.value = it.maximum } }
    }

    // =============== Message Row (fixes vertical stretching) ===============

    /**
     * Creates a message row whose max height = preferred height,
     * preventing BoxLayout from stretching it vertically.
     */
    private fun createMessageRow(message: Message): JPanel {
        val isSentByMe = message.senderId == service.currentUser?.id
        val senderName = message.senderName ?: if (isSentByMe) service.username else "未知用户"
        val avatarPath = if (isSentByMe) service.userAvatar else null
        val bot = service.getBot(message.senderId)
        val isBotMessage = bot != null
        val isGroupChat = currentGroup != null

        val avatarSize = 36
        val avatarIcon = if (isBotMessage) createInitialAvatar("B", avatarSize, Color(100, 100, 200))
        else createAvatarIcon(avatarPath, senderName, avatarSize)

        val avatarLabel = JLabel(avatarIcon).apply {
            verticalAlignment = SwingConstants.TOP
            preferredSize = Dimension(avatarSize + 4, avatarSize + 4)
            border = JBUI.Borders.emptyTop(2)
        }

        val bubble = createBubble(message, isSentByMe, senderName, isBotMessage, isGroupChat)

        return object : JPanel(BorderLayout()) {
            override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            isOpaque = false
            border = JBUI.Borders.empty(3, 12, 3, 12)

            if (isSentByMe) {
                add(JPanel(BorderLayout()).apply { isOpaque = false; add(bubble, BorderLayout.EAST) }, BorderLayout.CENTER)
                add(avatarLabel, BorderLayout.EAST)
            } else {
                add(JPanel(BorderLayout()).apply { isOpaque = false; add(bubble, BorderLayout.WEST) }, BorderLayout.CENTER)
                add(avatarLabel, BorderLayout.WEST)
            }
        }
    }

    // =============== Bubble ===============

    private fun createBubble(
        message: Message, isSentByMe: Boolean, senderName: String,
        isBotMessage: Boolean, isGroupChat: Boolean
    ): JPanel {
        val bubbleColor = if (isSentByMe) JBColor(Color(149, 236, 105), Color(76, 141, 54))
        else JBColor(Color(255, 255, 255), Color(58, 58, 58))

        val textColor = JBColor(Color(30, 30, 30), Color(220, 220, 220))
        val hasMention = message.mentionAll || message.mentionedUserIds.isNotEmpty()

        // Measure text to determine bubble width
        val fm = fontMetricsCache
        val lines = message.content.split("\n")
        val maxLineWidth = lines.maxOfOrNull { fm.stringWidth(it) } ?: 0
        val naturalWidth = maxLineWidth + 8
        val contentWidth = minOf(naturalWidth, MAX_BUBBLE_WIDTH)
        val needsWrap = naturalWidth > MAX_BUBBLE_WIDTH

        // Create selectable text component
        val textArea = JTextArea(message.content).apply {
            isEditable = false
            isOpaque = false
            lineWrap = needsWrap
            wrapStyleWord = true
            font = MSG_FONT
            foreground = textColor
            border = null
            cursor = Cursor(Cursor.TEXT_CURSOR)
            caretColor = Color(0, 0, 0, 0)
        }

        // Calculate proper text size
        if (needsWrap) {
            textArea.setSize(contentWidth, Short.MAX_VALUE.toInt())
            val h = textArea.preferredSize.height
            textArea.preferredSize = Dimension(contentWidth, h)
        } else {
            textArea.preferredSize = Dimension(naturalWidth, textArea.preferredSize.height)
        }

        // Build the bubble panel with rounded background
        val bubblePanel = object : JPanel(BorderLayout(0, 3)) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width, height, 12, 12)
            }
        }.apply {
            isOpaque = false
            background = bubbleColor
            border = JBUI.Borders.empty(8, 12, 6, 12)

            // Sender name for group/bot messages
            if (!isSentByMe && (isGroupChat || isBotMessage)) {
                val nameColor = if (isBotMessage) JBColor(Color(100, 100, 200), Color(150, 130, 230))
                else JBColor(Color(120, 120, 120), Color(160, 160, 160))
                val displayName = if (isBotMessage) "$senderName [BOT]" else senderName
                add(JLabel(displayName).apply { font = NAME_FONT; foreground = nameColor }, BorderLayout.NORTH)
            }

            // Content area
            val contentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false

                if (hasMention) {
                    val mentionText = if (message.mentionAll) "[@全体成员]" else {
                        val names = message.mentionedUserIds.mapNotNull { id ->
                            service.peers.value[id]?.username ?: if (id == service.currentUser?.id) service.username else null
                        }
                        "[@${names.joinToString(", ")}]"
                    }
                    add(JLabel(mentionText).apply {
                        font = MENTION_FONT; foreground = JBColor(Color(33, 150, 243), Color(100, 180, 255))
                        alignmentX = LEFT_ALIGNMENT; border = JBUI.Borders.emptyBottom(2)
                    })
                }

                textArea.alignmentX = LEFT_ALIGNMENT
                add(textArea)
            }
            add(contentPanel, BorderLayout.CENTER)

            // Timestamp
            add(JLabel(message.getFormattedTime()).apply {
                font = TIME_FONT
                foreground = if (isSentByMe) JBColor(Color(80, 120, 60), Color(100, 150, 80))
                else JBColor(Color(170, 170, 170), Color(120, 120, 120))
                horizontalAlignment = if (isSentByMe) SwingConstants.RIGHT else SwingConstants.LEFT
            }, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 0, 6)
            add(bubblePanel, BorderLayout.CENTER)
        }
    }

    // =============== Image / File Components ===============

    private fun createImageMessageComponent(file: File): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            isOpaque = false; border = JBUI.Borders.empty(3, 12, 3, 12)
            val wrapper = object : JPanel(BorderLayout()) {
                override fun paintComponent(g: Graphics) {
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.color = background; g2d.fillRoundRect(0, 0, width, height, 8, 8)
                }
            }.apply {
                isOpaque = false; background = JBColor(Color(149, 236, 105), Color(76, 141, 54)); border = JBUI.Borders.empty(4)
                try {
                    val icon = ImageIcon(file.path)
                    val scale = minOf(200.0 / icon.iconWidth, 200.0 / icon.iconHeight, 1.0)
                    add(JLabel(ImageIcon(icon.image.getScaledInstance((icon.iconWidth * scale).toInt(), (icon.iconHeight * scale).toInt(), Image.SCALE_SMOOTH))), BorderLayout.CENTER)
                } catch (_: Exception) {
                    add(JLabel("[图片] ${file.name}").apply { font = Font("Microsoft YaHei", Font.PLAIN, 13) }, BorderLayout.CENTER)
                }
            }
            add(wrapper, BorderLayout.EAST)
        }
    }

    private fun createFileMessageComponent(file: File): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            isOpaque = false; border = JBUI.Borders.empty(3, 12, 3, 12)
            val card = object : JPanel(BorderLayout(10, 0)) {
                override fun paintComponent(g: Graphics) {
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.color = background; g2d.fillRoundRect(0, 0, width, height, 8, 8)
                }
            }.apply {
                isOpaque = false; background = JBColor(Color(255, 255, 255), Color(58, 58, 58))
                border = JBUI.Borders.empty(12, 14); preferredSize = Dimension(240, 60)
                add(JLabel(AllIcons.FileTypes.Any_type).apply { preferredSize = Dimension(36, 36) }, BorderLayout.WEST)
                add(JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    add(JLabel(file.name).apply { font = Font("Microsoft YaHei", Font.PLAIN, 13) }, BorderLayout.NORTH)
                    add(JLabel("${file.length() / 1024} KB").apply { font = NAME_FONT; foreground = JBColor.GRAY }, BorderLayout.SOUTH)
                }, BorderLayout.CENTER)
            }
            add(card, BorderLayout.EAST)
        }
    }

    // =============== Avatar ===============

    private fun createAvatarIcon(avatarPath: String?, name: String, size: Int = 36): Icon {
        if (avatarPath != null) {
            try { val f = File(avatarPath); if (f.exists()) return ImageIcon(ImageIO.read(f).getScaledInstance(size, size, Image.SCALE_SMOOTH)) } catch (_: Exception) {}
        }
        return createInitialAvatar(name.firstOrNull()?.toString() ?: "?", size)
    }

    private fun createInitialAvatar(initial: String, size: Int = 36, forcedColor: Color? = null): Icon {
        val colors = listOf(Color(76, 175, 80), Color(33, 150, 243), Color(156, 39, 176), Color(255, 152, 0), Color(233, 30, 99), Color(0, 150, 136))
        val color = forcedColor ?: colors[Math.abs(initial.hashCode()) % colors.size]
        return object : Icon {
            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = color; g2d.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
                g2d.color = Color.WHITE; g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 2 / 5)
                val fm = g2d.fontMetrics; g2d.drawString(initial, x + (size - fm.stringWidth(initial)) / 2, y + (size + fm.ascent - fm.descent) / 2)
            }
            override fun getIconWidth() = size; override fun getIconHeight() = size
        }
    }

    // =============== Dialogs ===============

    private fun showProfileDialog() { ProfileDialog(project).showAndGet() }
    private fun showGroupManageDialog() { currentGroup?.let { GroupManageDialog(project, it.id).show() } }

    private fun clearChatDisplay() {
        titleLabel.text = ""; statusLabel.text = ""
        messagePanel.removeAll(); messagePanel.revalidate(); messagePanel.repaint(); lastDisplayedTimestamp = 0
    }
}

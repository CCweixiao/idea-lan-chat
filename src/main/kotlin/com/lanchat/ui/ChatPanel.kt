package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.util.CryptoManager
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.Group
import com.lanchat.network.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
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
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val messagePanel: JPanel
    private val messageScrollPane: JBScrollPane
    private val inputArea = object : JTextArea(3, 20) {
        override fun paste() {
            if (tryPasteImage()) return
            super.paste()
        }
    }
    private var currentPeer: Peer? = null
    private var currentGroup: Group? = null
    private var scrollToBottomButton: JButton? = null
    private lateinit var sendButton: JButton
    private var pendingNewMsgCount = 0
    private var lastSendTime = 0L

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
        private const val SEND_RATE_LIMIT_MS = 500L
        private const val MAX_IMAGE_SIZE = 10L * 1024 * 1024
        private const val MAX_FILE_SIZE = 100L * 1024 * 1024
        private val MSG_FONT get() = ThemeManager.plainFont()
        private val NAME_FONT get() = ThemeManager.plainFont(-3)
        private val TIME_FONT get() = ThemeManager.plainFont(-4)
        private val MENTION_FONT get() = ThemeManager.boldFont(-2)

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
            val currentChatId = currentPeer?.id ?: currentGroup?.id
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
        messagePanel.background = ThemeManager.panelBackground

        messageScrollPane = JBScrollPane(messagePanel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            background = ThemeManager.panelBackground
        }

        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = EmptyBorder(8, 10, 8, 10)
        inputArea.font = MSG_FONT
        inputArea.background = ThemeManager.inputBackground

        inputPanel = createInputPanel()
        setupUI()
        service.addMessageListener(messageListener)
        observeGroupChanges()

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) { scrollToBottom() }
        })
    }

    private fun setupUI() {
        background = ThemeManager.panelBackground

        emptyCard.background = ThemeManager.panelBackground
        emptyCard.add(JLabel("选择联系人或群聊开始聊天").apply {
            font = ThemeManager.boldFont(1)
            foreground = JBColor(Color(170, 170, 170), Color(100, 100, 100))
            horizontalAlignment = SwingConstants.CENTER
        }, BorderLayout.CENTER)

        scrollToBottomButton = object : JButton("↓ 最新消息") {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width - 1, height - 1, 16, 16)
                g2d.color = foreground
                val fm = g2d.fontMetrics
                g2d.drawString(text, (width - fm.stringWidth(text)) / 2, (height + fm.ascent - fm.descent) / 2)
            }
            override fun getPreferredSize(): Dimension {
                val fm = getFontMetrics(font)
                val w = fm.stringWidth(text) + 28
                return Dimension(maxOf(w, 110), 28)
            }
        }.apply {
            background = JBColor(Color(0, 0, 0, 140), Color(200, 200, 200, 160))
            foreground = JBColor(Color.WHITE, Color(40, 40, 40))
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            isBorderPainted = false; isFocusPainted = false; isContentAreaFilled = false; isOpaque = false
            cursor = Cursor(Cursor.HAND_CURSOR)
            isVisible = false
            addActionListener {
                pendingNewMsgCount = 0
                updateScrollButtonText()
                scrollToBottom()
            }
        }

        messageScrollPane.verticalScrollBar.addAdjustmentListener { e ->
            val sb = e.adjustable
            val atBottom = sb.value + sb.visibleAmount >= sb.maximum - 30
            val btn = scrollToBottomButton ?: return@addAdjustmentListener
            val shouldShow = !atBottom
            if (btn.isVisible != shouldShow) {
                btn.isVisible = shouldShow
                btn.parent?.let { it.doLayout(); it.repaint() }
            }
            if (atBottom && pendingNewMsgCount > 0) {
                pendingNewMsgCount = 0
                updateScrollButtonText()
            }
        }

        val messageArea = object : JPanel() {
            override fun isOptimizedDrawingEnabled() = false
        }.apply {
            layout = object : LayoutManager {
                override fun addLayoutComponent(name: String?, comp: Component?) {}
                override fun removeLayoutComponent(comp: Component?) {}
                override fun preferredLayoutSize(parent: Container?) = messageScrollPane.preferredSize
                override fun minimumLayoutSize(parent: Container?) = messageScrollPane.minimumSize
                override fun layoutContainer(parent: Container) {
                    messageScrollPane.setBounds(0, 0, parent.width, parent.height)
                    val btn = scrollToBottomButton ?: return
                    val bw = btn.preferredSize.width; val bh = btn.preferredSize.height
                    btn.setBounds((parent.width - bw) / 2, parent.height - bh - 12, bw, bh)
                }
            }
            add(scrollToBottomButton)
            add(messageScrollPane)
        }

        chatCard.apply {
            isOpaque = false
            add(createHeader(), BorderLayout.NORTH)
            add(messageArea, BorderLayout.CENTER)
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
                titleLabel.font = ThemeManager.boldFont(1)
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
            val online = service.getGroupOnlineCount(group.id)
            val numberText = if (group.groupNumber.isNotEmpty()) " · ${group.groupNumber}" else ""
            headerButtonPanel.add(object : JButton("成员(${online}/${group.getMemberCount()})$numberText") {
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
                font = ThemeManager.plainFont(-2)
                isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
                foreground = JBColor(Color(100, 100, 100), Color(170, 170, 170))
                cursor = Cursor(Cursor.HAND_CURSOR); toolTipText = "查看群成员"
                isRolloverEnabled = true
                addActionListener { showGroupManageDialog() }
            })
        }
        headerButtonPanel.add(createHeaderButton(AllIcons.General.Settings, "个人信息设置") { showProfileDialog() })
        headerButtonPanel.revalidate(); headerButtonPanel.repaint()
    }

    private fun updateToolbar() {
        toolbarPanel.removeAll()
        toolbarPanel.add(createToolbarIcon(AllIcons.General.Web, "发送图片") { sendImage() })
        toolbarPanel.add(createToolbarIcon(AllIcons.General.InlineAdd, "粘贴剪贴板图片") { handlePasteFromToolbar() })
        toolbarPanel.add(createToolbarIcon(AllIcons.FileTypes.Any_type, "发送文件") { sendFile() })
        if (currentGroup != null) {
            toolbarPanel.add(Box.createHorizontalStrut(4))
            toolbarPanel.add(createToolbarIcon(AllIcons.Actions.GroupByModule, "@成员") { showMentionDialog() })
        }
        toolbarPanel.add(Box.createHorizontalStrut(8))
        toolbarPanel.add(createToolbarIcon(AllIcons.General.Settings, "存储空间管理") { showStorageManager() })
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
            toolbarPanel.add(createToolbarIcon(AllIcons.General.InlineAdd, "粘贴剪贴板图片") { handlePasteFromToolbar() })
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

                sendButton = object : JButton("发送") {
                    override fun paintComponent(g: Graphics) {
                        val g2d = g as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2d.color = background
                        g2d.fillRoundRect(0, 0, width, height, 8, 8)
                        g2d.font = font
                        g2d.color = foreground
                        val fm = g2d.fontMetrics
                        val tx = (width - fm.stringWidth(text)) / 2
                        val ty = (height + fm.ascent - fm.descent) / 2
                        g2d.drawString(text, tx, ty)
                    }
                }.apply {
                    font = ThemeManager.plainFont(-2)
                    background = ThemeManager.sendButtonColor
                    foreground = ThemeManager.sendButtonText
                    isBorderPainted = false; isFocusPainted = false; isOpaque = false
                    isContentAreaFilled = false
                    preferredSize = Dimension(52, 30); cursor = Cursor(Cursor.HAND_CURSOR)
                    addActionListener { sendMessage() }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseEntered(e: MouseEvent) { background = ThemeManager.sendButtonHoverColor; repaint() }
                        override fun mouseExited(e: MouseEvent) { background = ThemeManager.sendButtonColor; repaint() }
                        override fun mousePressed(e: MouseEvent) { background = ThemeManager.sendButtonPressedColor; repaint() }
                        override fun mouseReleased(e: MouseEvent) { background = ThemeManager.sendButtonHoverColor; repaint() }
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

    private var suppressAtDetection = false

    private fun setupShortcuts() {
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send")
        inputArea.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = sendMessage()
        })
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "newline")
        inputArea.actionMap.put("newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) { inputArea.append("\n") }
        })

        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_V && (e.isMetaDown || e.isControlDown)) {
                    if (tryPasteImage()) {
                        e.consume()
                    }
                }
            }
        })

        inputArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                if (suppressAtDetection || currentGroup == null) return
                if (e.length == 1) {
                    try {
                        val inserted = e.document.getText(e.offset, 1)
                        if (inserted == "@") {
                            ApplicationManager.getApplication().invokeLater({
                                try { inputArea.document.remove(e.offset, 1) } catch (_: Exception) {}
                                showMentionDialog()
                            }, ModalityState.defaultModalityState())
                        }
                    } catch (_: Exception) {}
                }
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {}
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
        })

        setupDragAndDrop()
    }

    private fun setupDragAndDrop() {
        val handler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    || support.isDataFlavorSupported(DataFlavor.imageFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false
                try {
                    if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        files.firstOrNull()?.let { file ->
                            val ext = file.extension.lowercase()
                            if (ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")) {
                                val img = ImageIO.read(file)
                                if (img != null) showImagePreviewAndSend(img, file)
                                else { showToast("无法读取图片"); return false }
                            } else {
                                if (file.length() > MAX_FILE_SIZE) { showToast("文件不能超过 100MB"); return false }
                                sendFileDirectly(file)
                            }
                        }
                        return true
                    }
                    if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        val image = support.transferable.getTransferData(DataFlavor.imageFlavor) as? Image
                        if (image != null) { showImagePreviewAndSend(image); return true }
                    }
                } catch (ex: Exception) { showToast("拖入失败: ${ex.message}") }
                return false
            }
        }
        inputArea.transferHandler = handler
    }

    // =============== Public API ===============

    fun setCurrentPeer(peer: Peer?) {
        currentPeer = peer; currentGroup = null
        SwingUtilities.invokeLater {
            if (peer != null) {
                val isAssistant = service.isFileTransferAssistant(peer.id)
                val lastReadAt = service.getLastReadAt(peer.id)
                service.setCurrentChatId(peer.id)
                titleLabel.text = peer.username
                if (isAssistant) {
                    statusLabel.text = "本地文件存储"
                    statusLabel.foreground = ThemeManager.onlineColor
                } else {
                    val onlineText = if (peer.isOnline) "在线 · ${peer.ipAddress}" else "离线 · ${peer.ipAddress}"
                    statusLabel.text = "$onlineText${encryptionTag()}"
                    statusLabel.foreground = if (peer.isOnline) ThemeManager.onlineColor else ThemeManager.offlineColor
                }
                updateHeaderButtons(); updateToolbar()
                inputArea.isEditable = true; sendButton.isEnabled = true
                inputArea.background = ThemeManager.panelBackground; inputArea.toolTipText = null
                cardLayout.show(cardPanel, "chat"); loadChatHistory(peer.id, lastReadAt)
            } else {
                service.setCurrentChatId(null)
                cardLayout.show(cardPanel, "empty")
            }
        }
    }

    fun setCurrentGroup(group: Group?) {
        currentGroup = group; currentPeer = null
        SwingUtilities.invokeLater {
            if (group != null) {
                val lastReadAt = service.getLastReadAt(group.id)
                service.setCurrentChatId(group.id)
                titleLabel.text = group.name
                statusLabel.text = groupStatusText(group); statusLabel.foreground = JBColor.GRAY
                updateHeaderButtons(); updateToolbar()
                updateMuteStatus(group)
                cardLayout.show(cardPanel, "chat"); loadChatHistory(group.id, lastReadAt)
            } else {
                service.setCurrentChatId(null)
                cardLayout.show(cardPanel, "empty")
            }
        }
    }

    fun clearChat() {
        currentPeer = null; currentGroup = null
        service.setCurrentChatId(null)
        SwingUtilities.invokeLater { cardLayout.show(cardPanel, "empty"); clearChatDisplay() }
    }

    fun getCurrentChatName(): String? = currentPeer?.username ?: currentGroup?.name

    // =============== Chat History ===============

    private fun loadChatHistory(chatId: String, lastReadAt: Long = 0L) {
        messagePanel.removeAll(); lastDisplayedTimestamp = 0
        val messages = service.getChatHistory(chatId)
        var unreadDivider: JPanel? = null
        if (messages.isEmpty()) {
            messagePanel.add(JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(JLabel("暂无消息，发送第一条消息吧").apply {
                    font = ThemeManager.plainFont(-1)
                    foreground = JBColor(Color(180, 180, 180), Color(100, 100, 100))
                })
            })
        } else {
            val myId = service.currentUser?.id ?: ""
            val firstUnreadIdx = if (lastReadAt > 0) {
                messages.indexOfFirst { it.timestamp > lastReadAt && it.senderId != myId }
            } else -1
            pendingNewMsgCount = if (firstUnreadIdx > 0) {
                messages.subList(firstUnreadIdx, messages.size).count { it.senderId != myId }
            } else 0
            updateScrollButtonText()
            var prevTs = 0L
            for ((idx, msg) in messages.withIndex()) {
                addTimeSeparatorIfNeeded(msg.timestamp, prevTs); prevTs = msg.timestamp
                if (unreadDivider == null && firstUnreadIdx in 1..idx && idx == firstUnreadIdx) {
                    unreadDivider = createUnreadDivider()
                    messagePanel.add(unreadDivider)
                }
                messagePanel.add(createMessageRow(msg))
            }
            lastDisplayedTimestamp = prevTs
        }
        messagePanel.add(Box.createVerticalGlue())
        messagePanel.revalidate(); messagePanel.repaint()
        val divider = unreadDivider
        if (divider != null) {
            scrollToDivider(divider)
            if (pendingNewMsgCount > 0) {
                SwingUtilities.invokeLater {
                    val btn = scrollToBottomButton ?: return@invokeLater
                    btn.isVisible = true
                    btn.parent?.let { it.doLayout(); it.repaint() }
                }
            }
        } else {
            scrollToBottom()
        }
    }

    private fun createUnreadDivider(): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val y = height / 2
                val labelWidth = 90
                val left = (width - labelWidth) / 2
                g2d.color = JBColor(Color(230, 76, 76, 80), Color(200, 80, 80, 100))
                g2d.stroke = BasicStroke(1f)
                g2d.drawLine(24, y, left - 8, y)
                g2d.drawLine(left + labelWidth + 8, y, width - 24, y)
            }
        }.apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 0)
            preferredSize = Dimension(0, 32)
            add(JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                isOpaque = false
                add(object : JLabel("  以下是新消息  ") {
                    override fun paintComponent(g: Graphics) {
                        val g2d = g as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2d.color = JBColor(Color(230, 76, 76, 30), Color(200, 80, 80, 40))
                        g2d.fillRoundRect(0, 0, width, height, 12, 12)
                        super.paintComponent(g)
                    }
                }.apply {
                    font = ThemeManager.plainFont(-3)
                    foreground = JBColor(Color(220, 70, 70), Color(220, 100, 100))
                    horizontalAlignment = SwingConstants.CENTER
                    isOpaque = false
                })
            }, BorderLayout.CENTER)
        }
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
                            font = ThemeManager.plainFont(-3)
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
                        font = ThemeManager.plainFont(-1)
                        foreground = if (isSentByMe) ThemeManager.primaryButtonColor else ThemeManager.primaryTextColor
                    }, BorderLayout.CENTER)
                    add(JLabel(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))).apply {
                        font = ThemeManager.plainFont(-4)
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
                    font = NAME_FONT; foreground = ThemeManager.timestampColor
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
        if (text.isEmpty()) {
            showToast("不能发送空消息")
            inputArea.requestFocus(); return
        }

        val now = System.currentTimeMillis()
        if (now - lastSendTime < SEND_RATE_LIMIT_MS) {
            showToast("发送过于频繁，请稍后再试")
            return
        }
        lastSendTime = now

        currentGroup?.let { group ->
            if (service.isUserMuted(group.id)) {
                JOptionPane.showMessageDialog(this, "你已被禁言，暂时无法在此群发言", "禁言提示", JOptionPane.WARNING_MESSAGE)
                return
            }
            val mIds = pendingMentionUserIds.toList(); val mAll = pendingMentionAll
            pendingMentionUserIds = emptyList(); pendingMentionAll = false
            service.sendGroupMessage(group.id, text, mIds, mAll); inputArea.text = ""
            val type = when { mAll -> MessageType.MENTION_ALL; mIds.isNotEmpty() -> MessageType.MENTION_MEMBER; else -> MessageType.GROUP_CHAT }
            addMessageToPanel(Message(type = type, senderId = service.currentUser?.id ?: "", receiverId = group.id,
                content = text, senderName = service.username, groupId = group.id, mentionedUserIds = mIds, mentionAll = mAll)); return
        }
        currentPeer?.let { peer ->
            if (!service.isFileTransferAssistant(peer.id) && !peer.isOnline) {
                val choice = JOptionPane.showConfirmDialog(this,
                    "对方当前不在线，消息将会丢失。\n确定要发送吗？", "离线提示",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                if (choice != JOptionPane.YES_OPTION) return
            }
            service.sendTextMessage(peer.id, text); inputArea.text = ""
            addMessageToPanel(Message(type = MessageType.TEXT, senderId = service.currentUser?.id ?: "",
                receiverId = peer.id, content = text, senderName = service.username))
        } ?: JOptionPane.showMessageDialog(this, "请先选择一个联系人或群聊", "提示", JOptionPane.WARNING_MESSAGE)
    }

    private fun sendImage() {
        val fc = JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("图片文件", "jpg", "jpeg", "png", "gif", "bmp", "webp")
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val file = fc.selectedFile
        if (file.length() > MAX_IMAGE_SIZE) {
            showToast("图片不能超过 10MB"); return
        }
        currentGroup?.let { group ->
            service.sendGroupImageMessage(group.id, file.absolutePath)
            addMessageToPanel(Message(type = MessageType.IMAGE, senderId = service.currentUser?.id ?: "",
                receiverId = group.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), groupId = group.id, senderName = service.username))
            return
        }
        currentPeer?.let { peer ->
            if (!service.isFileTransferAssistant(peer.id) && !peer.isOnline) {
                val c = JOptionPane.showConfirmDialog(this, "对方当前不在线，文件将无法送达。\n确定要发送吗？",
                    "离线提示", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                if (c != JOptionPane.YES_OPTION) return
            }
            service.sendImageMessage(peer.id, file.absolutePath)
            addMessageToPanel(Message(type = MessageType.IMAGE, senderId = service.currentUser?.id ?: "",
                receiverId = peer.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), senderName = service.username))
        } ?: showToast("请先选择联系人或群聊")
    }

    private fun sendFile() {
        val fc = JFileChooser()
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val file = fc.selectedFile
        if (file.length() > MAX_FILE_SIZE) {
            showToast("文件不能超过 100MB"); return
        }
        currentGroup?.let { group ->
            service.sendGroupFileMessage(group.id, file.absolutePath, file.name)
            addMessageToPanel(Message(type = MessageType.FILE, senderId = service.currentUser?.id ?: "",
                receiverId = group.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), groupId = group.id, senderName = service.username))
            return
        }
        currentPeer?.let { peer ->
            if (!service.isFileTransferAssistant(peer.id) && !peer.isOnline) {
                val c = JOptionPane.showConfirmDialog(this, "对方当前不在线，文件将无法送达。\n确定要发送吗？",
                    "离线提示", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                if (c != JOptionPane.YES_OPTION) return
            }
            service.sendFileMessage(peer.id, file.absolutePath, file.name)
            addMessageToPanel(Message(type = MessageType.FILE, senderId = service.currentUser?.id ?: "",
                receiverId = peer.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), senderName = service.username))
        } ?: showToast("请先选择联系人或群聊")
    }

    private fun tryPasteImage(): Boolean {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        try {
            val flavors = clipboard.availableDataFlavors

            val hasImage = flavors.any { it == DataFlavor.imageFlavor || it.mimeType.startsWith("image/") }
            if (hasImage) {
                val image = try { clipboard.getData(DataFlavor.imageFlavor) as? Image } catch (_: Exception) { null }
                if (image != null) { showImagePreviewAndSend(image); return true }
            }

            val hasFiles = flavors.any { it.isFlavorJavaFileListType }
            if (hasFiles) {
                @Suppress("UNCHECKED_CAST")
                val files = try { clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File> } catch (_: Exception) { null }
                val file = files?.firstOrNull()
                if (file != null) {
                    val ext = file.extension.lowercase()
                    if (ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")) {
                        val img = ImageIO.read(file)
                        if (img != null) { showImagePreviewAndSend(img, file); return true }
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun handlePasteFromToolbar() {
        if (!tryPasteImage()) {
            showToast("剪贴板中没有图片")
        }
    }

    private fun showImagePreviewAndSend(image: Image, sourceFile: File? = null) {
        val bi = toBufferedImage(image)
        if (bi == null) { showToast("无法读取剪贴板图片"); return }

        val maxPrev = 300
        val scale = minOf(maxPrev.toDouble() / bi.width, maxPrev.toDouble() / bi.height, 1.0)
        val pw = (bi.width * scale).toInt(); val ph = (bi.height * scale).toInt()
        val preview = bi.getScaledInstance(pw, ph, Image.SCALE_SMOOTH)

        val previewPanel = JPanel(BorderLayout(0, 8)).apply {
            add(JLabel(ImageIcon(preview)).apply {
                horizontalAlignment = SwingConstants.CENTER
                border = BorderFactory.createLineBorder(JBColor(Color(200, 200, 200), Color(80, 80, 80)))
            }, BorderLayout.CENTER)
            val sizeText = "${bi.width} × ${bi.height} 像素"
            add(JLabel(sizeText).apply {
                foreground = JBColor.GRAY
                font = ThemeManager.plainFont(-2)
                horizontalAlignment = SwingConstants.CENTER
            }, BorderLayout.SOUTH)
        }

        val result = JOptionPane.showConfirmDialog(
            this, previewPanel, "发送图片", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) return

        if (sourceFile != null && sourceFile.exists()) {
            if (sourceFile.length() > MAX_IMAGE_SIZE) { showToast("图片不能超过 10MB"); return }
            sendImageFile(sourceFile)
        } else {
            val tempFile = File(com.lanchat.network.NetworkManager.getLanChatDir(),
                "clipboard_${System.currentTimeMillis()}.png")
            ImageIO.write(bi, "png", tempFile)
            if (tempFile.length() > MAX_IMAGE_SIZE) {
                tempFile.delete(); showToast("图片不能超过 10MB"); return
            }
            sendImageFile(tempFile)
        }
    }

    private fun toBufferedImage(image: Image): BufferedImage? {
        if (image is BufferedImage) return image
        val tracker = MediaTracker(this)
        tracker.addImage(image, 0)
        try { tracker.waitForAll(3000) } catch (_: InterruptedException) {}
        val w = image.getWidth(null); val h = image.getHeight(null)
        if (w <= 0 || h <= 0) return null
        return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().let { g ->
                g.drawImage(image, 0, 0, null); g.dispose()
            }
        }
    }

    private fun sendImageFile(file: File) {
        currentGroup?.let { group ->
            service.sendGroupImageMessage(group.id, file.absolutePath)
            addMessageToPanel(Message(type = MessageType.IMAGE, senderId = service.currentUser?.id ?: "",
                receiverId = group.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), groupId = group.id, senderName = service.username))
            return
        }
        currentPeer?.let { peer ->
            if (!service.isFileTransferAssistant(peer.id) && !peer.isOnline) {
                val c = JOptionPane.showConfirmDialog(this, "对方当前不在线，文件将无法送达。\n确定要发送吗？",
                    "离线提示", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                if (c != JOptionPane.YES_OPTION) return
            }
            service.sendImageMessage(peer.id, file.absolutePath)
            addMessageToPanel(Message(type = MessageType.IMAGE, senderId = service.currentUser?.id ?: "",
                receiverId = peer.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), senderName = service.username))
        } ?: showToast("请先选择联系人或群聊")
    }

    private fun sendFileDirectly(file: File) {
        currentGroup?.let { group ->
            service.sendGroupFileMessage(group.id, file.absolutePath, file.name)
            addMessageToPanel(Message(type = MessageType.FILE, senderId = service.currentUser?.id ?: "",
                receiverId = group.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), groupId = group.id, senderName = service.username))
            return
        }
        currentPeer?.let { peer ->
            if (!service.isFileTransferAssistant(peer.id) && !peer.isOnline) {
                val c = JOptionPane.showConfirmDialog(this, "对方当前不在线，文件将无法送达。\n确定要发送吗？",
                    "离线提示", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                if (c != JOptionPane.YES_OPTION) return
            }
            service.sendFileMessage(peer.id, file.absolutePath, file.name)
            addMessageToPanel(Message(type = MessageType.FILE, senderId = service.currentUser?.id ?: "",
                receiverId = peer.id, content = file.absolutePath, fileName = file.name,
                fileSize = file.length(), senderName = service.username))
        } ?: showToast("请先选择联系人或群聊")
    }

    private fun showMentionDialog() {
        val group = currentGroup ?: return
        val dialog = MentionMembersDialog(project, group.id)
        if (dialog.showAndGet()) {
            pendingMentionUserIds = dialog.selectedMemberIds; pendingMentionAll = dialog.isMentionAll
            val mentionText = if (dialog.isMentionAll) "@全体成员 " else
                dialog.selectedMemberIds.mapNotNull { id -> service.getGroupMembers(group.id).find { it.id == id }?.username }.joinToString(" ") { "@$it " }
            if (mentionText.isNotEmpty()) {
                suppressAtDetection = true
                inputArea.append(mentionText)
                suppressAtDetection = false
                inputArea.requestFocus()
            }
        }
    }

    // =============== Message Display ===============

    fun addMessageToPanel(message: Message) {
        SwingUtilities.invokeLater {
            val sb = messageScrollPane.verticalScrollBar
            val wasAtBottom = sb.value + sb.visibleAmount >= sb.maximum - 50

            addTimeSeparatorIfNeeded(message.timestamp, lastDisplayedTimestamp)
            lastDisplayedTimestamp = message.timestamp
            val count = messagePanel.componentCount
            if (count > 0 && messagePanel.getComponent(count - 1) is Box.Filler) messagePanel.remove(count - 1)
            messagePanel.add(createMessageRow(message))
            messagePanel.add(Box.createVerticalGlue())
            messagePanel.revalidate(); messagePanel.repaint()

            val isMine = message.senderId == service.currentUser?.id
            if (wasAtBottom || isMine) {
                scrollToBottom()
            } else {
                pendingNewMsgCount++
                updateScrollButtonText()
            }
        }
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater { messageScrollPane.verticalScrollBar.let { it.value = it.maximum } }
    }

    private fun showToast(msg: String) {
        val toast = JWindow(SwingUtilities.getWindowAncestor(this))
        val label = JLabel(msg, SwingConstants.CENTER).apply {
            font = ThemeManager.plainFont(-1)
            foreground = Color.WHITE; isOpaque = false
            border = JBUI.Borders.empty(8, 18)
        }
        toast.contentPane = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(0, 0, 0, 180)
                g2.fillRoundRect(0, 0, width, height, 16, 16)
            }
        }.apply { isOpaque = false; add(label) }
        toast.background = Color(0, 0, 0, 0)
        toast.pack()
        val owner = SwingUtilities.getWindowAncestor(this)
        if (owner != null) {
            toast.setLocationRelativeTo(owner)
        }
        toast.isVisible = true
        javax.swing.Timer(1800) { toast.dispose() }.apply { isRepeats = false; start() }
    }

    private fun scrollToDivider(divider: JComponent) {
        SwingUtilities.invokeLater {
            SwingUtilities.invokeLater {
                val rect = divider.bounds
                rect.y = maxOf(0, rect.y - 20)
                messagePanel.scrollRectToVisible(rect)
            }
        }
    }

    private fun updateScrollButtonText() {
        val btn = scrollToBottomButton ?: return
        btn.text = if (pendingNewMsgCount > 0) "↓ ${pendingNewMsgCount}条新消息" else "↓ 最新消息"
        btn.background = if (pendingNewMsgCount > 0)
            JBColor(Color(220, 70, 70, 200), Color(200, 80, 80, 210))
        else
            JBColor(Color(0, 0, 0, 140), Color(200, 200, 200, 160))
        btn.parent?.let { it.doLayout(); it.repaint() }
    }

    // =============== Message Row (fixes vertical stretching) ===============

    /**
     * Creates a message row whose max height = preferred height,
     * preventing BoxLayout from stretching it vertically.
     */
    private fun createMessageRow(message: Message): JPanel {
        val isSentByMe = message.senderId == service.currentUser?.id
        val senderName = message.senderName ?: if (isSentByMe) service.username else "未知用户"
        val avatarPath = if (isSentByMe) service.userAvatar else service.peers.value[message.senderId]?.avatar
        val isGroupChat = currentGroup != null

        val avatarSize = 36
        val avatarIcon = createAvatarIcon(avatarPath, senderName, avatarSize)

        val avatarLabel = JLabel(avatarIcon).apply {
            verticalAlignment = SwingConstants.TOP
            preferredSize = Dimension(avatarSize + 4, avatarSize + 4)
            border = JBUI.Borders.emptyTop(2)
        }

        val bubble = createBubble(message, isSentByMe, senderName, isGroupChat)

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
        isGroupChat: Boolean
    ): JPanel {
        val bubbleColor = if (isSentByMe) ThemeManager.sentBubbleColor
        else ThemeManager.receivedBubbleColor

        if (message.type == MessageType.IMAGE) {
            return createImageBubble(message, isSentByMe, senderName, isGroupChat, bubbleColor)
        }
        if (message.type == MessageType.FILE) {
            return createFileBubble(message, isSentByMe, senderName, isGroupChat, bubbleColor)
        }

        val textColor = ThemeManager.messageTextColor
        val hasMention = message.mentionAll || message.mentionedUserIds.isNotEmpty()

        var displayContent = message.content
        if (hasMention) {
            if (message.mentionAll) {
                displayContent = displayContent.replace("@全体成员 ", "").replace("@全体成员", "").trim()
            } else {
                message.mentionedUserIds.forEach { id ->
                    val name = service.peers.value[id]?.username
                        ?: if (id == service.currentUser?.id) service.username else null
                    if (name != null) {
                        displayContent = displayContent.replace("@$name ", "").replace("@$name", "")
                    }
                }
                displayContent = displayContent.trim()
            }
        }

        val fm = fontMetricsCache
        val lines = displayContent.split("\n")
        val maxLineWidth = lines.maxOfOrNull { fm.stringWidth(it) } ?: 0
        val naturalWidth = maxLineWidth + 8
        val contentWidth = minOf(naturalWidth, MAX_BUBBLE_WIDTH)
        val needsWrap = naturalWidth > MAX_BUBBLE_WIDTH

        val textArea = JTextArea(displayContent).apply {
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

        if (needsWrap) {
            textArea.setSize(contentWidth, Short.MAX_VALUE.toInt())
            val h = textArea.preferredSize.height
            textArea.preferredSize = Dimension(contentWidth, h)
        } else {
            textArea.preferredSize = Dimension(naturalWidth, textArea.preferredSize.height)
        }

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

            if (!isSentByMe && isGroupChat) {
                add(JLabel(senderName).apply { font = NAME_FONT; foreground = ThemeManager.senderNameColor }, BorderLayout.NORTH)
            }

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
                        font = MENTION_FONT; foreground = ThemeManager.mentionColor
                        alignmentX = LEFT_ALIGNMENT; border = JBUI.Borders.emptyBottom(2)
                    })
                }

                textArea.alignmentX = LEFT_ALIGNMENT
                add(textArea)
            }
            add(contentPanel, BorderLayout.CENTER)
            add(createTimeLabel(message, isSentByMe), BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 0, 6)
            add(bubblePanel, BorderLayout.CENTER)
        }
    }

    private fun createTimeLabel(message: Message, isSentByMe: Boolean): JPanel {
        return JPanel(BorderLayout(0, 2)).apply {
            isOpaque = false
            add(JLabel(message.getFormattedTime()).apply {
                font = TIME_FONT
                foreground = if (isSentByMe) ThemeManager.sentTimestampColor else ThemeManager.timestampColor
                horizontalAlignment = SwingConstants.LEFT
            }, BorderLayout.WEST)
        }
    }

    private fun createImageBubble(
        message: Message, isSentByMe: Boolean, senderName: String,
        isGroupChat: Boolean, bubbleColor: Color
    ): JPanel {
        val bubblePanel = object : JPanel(BorderLayout(0, 3)) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width, height, 12, 12)
            }
        }.apply {
            isOpaque = false; background = bubbleColor
            border = JBUI.Borders.empty(6, 8, 4, 8)

            if (!isSentByMe && isGroupChat) {
                add(JLabel(senderName).apply { font = NAME_FONT; foreground = ThemeManager.senderNameColor }, BorderLayout.NORTH)
            }

            val imageLabel = JLabel()
            val imgFile = File(message.content)
            if (imgFile.exists()) {
                try {
                    val icon = ImageIcon(imgFile.path)
                    val maxDim = 200.0
                    val scale = minOf(maxDim / icon.iconWidth, maxDim / icon.iconHeight, 1.0)
                    val sw = (icon.iconWidth * scale).toInt()
                    val sh = (icon.iconHeight * scale).toInt()
                    imageLabel.icon = ImageIcon(icon.image.getScaledInstance(sw, sh, Image.SCALE_SMOOTH))
                    imageLabel.cursor = Cursor(Cursor.HAND_CURSOR)
                    imageLabel.toolTipText = "点击查看大图"
                    imageLabel.addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) {
                            try { Desktop.getDesktop().open(imgFile) } catch (_: Exception) { }
                        }
                    })
                } catch (_: Exception) {
                    imageLabel.text = "[图片] ${message.fileName ?: imgFile.name}"
                    imageLabel.font = MSG_FONT
                }
            } else {
                imageLabel.text = "[图片] ${message.fileName ?: "文件不存在"}"
                imageLabel.font = MSG_FONT
                imageLabel.foreground = JBColor.GRAY
            }
            add(imageLabel, BorderLayout.CENTER)
            add(createTimeLabel(message, isSentByMe), BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false; border = JBUI.Borders.empty(0, 6, 0, 6)
            add(bubblePanel, BorderLayout.CENTER)
        }
    }

    private fun createFileBubble(
        message: Message, isSentByMe: Boolean, senderName: String,
        isGroupChat: Boolean, bubbleColor: Color
    ): JPanel {
        val bubblePanel = object : JPanel(BorderLayout(0, 3)) {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = background
                g2d.fillRoundRect(0, 0, width, height, 12, 12)
            }
        }.apply {
            isOpaque = false; background = bubbleColor
            border = JBUI.Borders.empty(8, 12, 6, 12)

            if (!isSentByMe && isGroupChat) {
                add(JLabel(senderName).apply { font = NAME_FONT; foreground = ThemeManager.senderNameColor }, BorderLayout.NORTH)
            }

            val fileCard = JPanel(BorderLayout(10, 0)).apply {
                isOpaque = false
                val fileIcon = JLabel(AllIcons.FileTypes.Any_type).apply { preferredSize = Dimension(36, 36) }
                add(fileIcon, BorderLayout.WEST)

                val fileName = message.fileName ?: File(message.content).name
                val fileSize = message.getFormattedFileSize().ifEmpty {
                    val f = File(message.content)
                    if (f.exists()) "${f.length() / 1024} KB" else ""
                }
                val infoPanel = JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    add(JLabel(fileName).apply {
                        font = ThemeManager.plainFont(-1)
                        foreground = ThemeManager.messageTextColor
                    }, BorderLayout.NORTH)
                    if (fileSize.isNotEmpty()) {
                        add(JLabel(fileSize).apply { font = NAME_FONT; foreground = JBColor.GRAY }, BorderLayout.SOUTH)
                    }
                }
                add(infoPanel, BorderLayout.CENTER)

                cursor = Cursor(Cursor.HAND_CURSOR)
                toolTipText = "点击打开文件所在目录"
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent?) {
                        val f = File(message.content)
                        try {
                            if (f.exists()) Desktop.getDesktop().open(f.parentFile)
                            else showToast("文件不存在")
                        } catch (_: Exception) { }
                    }
                })
                preferredSize = Dimension(220, 52)
            }
            add(fileCard, BorderLayout.CENTER)
            add(createTimeLabel(message, isSentByMe), BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false; border = JBUI.Borders.empty(0, 6, 0, 6)
            add(bubblePanel, BorderLayout.CENTER)
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

    private fun groupStatusText(group: Group): String {
        val total = group.getMemberCount()
        val online = service.getGroupOnlineCount(group.id)
        val muteInfo = if (group.globalMute) "（全员禁言中）" else ""
        return "${total}人 · ${online}人在线$muteInfo${encryptionTag()}"
    }

    // =============== Dialogs ===============

    private fun showProfileDialog() { ProfileDialog(project).showAndGet() }
    private fun showGroupManageDialog() { currentGroup?.let { GroupManageDialog(project, it.id).show() } }
    private fun showStorageManager() { StorageManagerDialog(project).isVisible = true }

    private fun observeGroupChanges() {
        scope.launch {
            service.groups.collectLatest { groups ->
                val gid = currentGroup?.id ?: return@collectLatest
                val updatedGroup = groups[gid]
                SwingUtilities.invokeLater {
                    if (updatedGroup != null) {
                        currentGroup = updatedGroup
                        statusLabel.text = groupStatusText(updatedGroup)
                        updateHeaderButtons()
                        updateMuteStatus(updatedGroup)
                    } else {
                        currentGroup = null
                        clearChatDisplay()
                        cardLayout.show(cardPanel, "empty")
                    }
                }
            }
        }
        scope.launch {
            service.peers.collectLatest { peers ->
                SwingUtilities.invokeLater {
                    val group = currentGroup
                    if (group != null) {
                        statusLabel.text = groupStatusText(group)
                        return@invokeLater
                    }
                }
                val pid = currentPeer?.id ?: return@collectLatest
                val updatedPeer = peers[pid]
                SwingUtilities.invokeLater {
                    if (updatedPeer != null) {
                        currentPeer = updatedPeer
                        val peerOnline = if (updatedPeer.isOnline) "在线 · ${updatedPeer.ipAddress}" else "离线 · ${updatedPeer.ipAddress}"
                        statusLabel.text = "$peerOnline${encryptionTag()}"
                        statusLabel.foreground = if (updatedPeer.isOnline) ThemeManager.onlineColor else ThemeManager.offlineColor
                    }
                }
            }
        }
    }

    private fun updateMuteStatus(group: com.lanchat.network.Group) {
        val userId = service.currentUser?.id ?: return
        val isMuted = group.isMuted(userId)
        inputArea.isEditable = !isMuted
        sendButton.isEnabled = !isMuted
        if (isMuted) {
            inputArea.text = ""
            inputArea.background = JBColor(Color(240, 240, 240), Color(50, 50, 50))
            inputArea.toolTipText = "你已被禁言，暂时无法发言"
        } else {
            inputArea.background = ThemeManager.panelBackground
            inputArea.toolTipText = null
        }
    }

    private fun encryptionTag(): String = if (CryptoManager.isEnabled) " · 🔒加密" else ""

    private fun clearChatDisplay() {
        titleLabel.text = ""; statusLabel.text = ""
        messagePanel.removeAll(); messagePanel.revalidate(); messagePanel.repaint(); lastDisplayedTimestamp = 0
    }
}

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
import com.lanchat.network.Peer
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 聊天面板 - 仿微信风格
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    
    private val messagePanel: JPanel
    private val messageScrollPane: JBScrollPane
    private val inputArea = JTextArea(3, 30)
    private var currentPeer: Peer? = null
    private var currentBot: Bot? = null
    private val titleLabel = JLabel()
    private val statusLabel = JLabel()
    
    init {
        // 消息面板
        messagePanel = JPanel()
        messagePanel.layout = BoxLayout(messagePanel, BoxLayout.Y_AXIS)
        messagePanel.background = JBColor.PanelBackground
        
        messageScrollPane = JBScrollPane(messagePanel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
        }
        
        // 输入框
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = EmptyBorder(10, 12, 10, 12)
        inputArea.font = Font("Microsoft YaHei", Font.PLAIN, 14)
        
        setupUI()
    }
    
    private fun setupUI() {
        add(createHeader(), BorderLayout.NORTH)
        add(messageScrollPane, BorderLayout.CENTER)
        add(createInputPanel(), BorderLayout.SOUTH)
        setupShortcuts()
    }
    
    private fun createHeader(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(12, 16)
            
            // 左侧：聊天对象信息
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                
                titleLabel.text = "选择联系人开始聊天"
                titleLabel.font = Font("Microsoft YaHei", Font.BOLD, 15)
                add(titleLabel, BorderLayout.NORTH)
                
                statusLabel.text = ""
                statusLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
                statusLabel.foreground = JBColor.GRAY
                add(statusLabel, BorderLayout.SOUTH)
            }
            add(infoPanel, BorderLayout.WEST)
            
            // 右侧：设置按钮
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                
                add(createIconButton(AllIcons.General.Settings, "个人信息") {
                    showProfileDialog()
                })
            }
            add(buttonPanel, BorderLayout.EAST)
        }
    }
    
    private fun createIconButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            margin = Insets(4, 4, 4, 4)
            cursor = Cursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }
    }
    
    private fun createInputPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(8, 16, 12, 16)
            
            // 顶部工具栏
            val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(6)
                
                add(createIconButton(AllIcons.General.InspectionsEye, "表情") {})
                add(createIconButton(AllIcons.FileTypes.Any_type, "发送图片") { sendImage() })
                add(createIconButton(AllIcons.FileTypes.Any_type, "发送文件") { sendFile() })
                add(createIconButton(AllIcons.Actions.Refresh, "刷新联系人") { service.refreshPeers() })
                add(createIconButton(AllIcons.General.Add, "添加联系人") { showAddContactDialog() })
                add(createIconButton(AllIcons.Nodes.Folder, "创建机器人") { showCreateBotDialog() })
            }
            add(toolbar, BorderLayout.NORTH)
            
            // 中间：输入区域
            val inputPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                
                val inputScrollPane = JBScrollPane(inputArea).apply {
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor(Color(220, 220, 220), Color(60, 60, 60)), 1),
                        EmptyBorder(0, 0, 0, 0)
                    )
                }
                add(inputScrollPane, BorderLayout.CENTER)
                
                // 发送按钮 - 美化
                val sendButton = JButton("发送").apply {
                    font = Font("Microsoft YaHei", Font.BOLD, 13)
                    background = Color(7, 193, 96)  // 微信绿色
                    foreground = Color.WHITE
                    isBorderPainted = false
                    isFocusPainted = false
                    margin = Insets(10, 24, 10, 24)
                    cursor = Cursor(Cursor.HAND_CURSOR)
                    addActionListener { sendMessage() }
                }
                add(sendButton, BorderLayout.EAST)
            }
            add(inputPanel, BorderLayout.CENTER)
        }
    }
    
    private fun showProfileDialog() {
        val dialog = ProfileDialog(project)
        if (dialog.showAndGet()) {
            // 更新显示
        }
    }
    
    private fun showAddContactDialog() {
        val dialog = AddContactDialog(project)
        if (dialog.showAndGet()) {
            if (dialog.isAddSelf) {
                val selfPeer = Peer(
                    id = service.currentUser?.id ?: "self",
                    username = "${service.username} (自己)",
                    ipAddress = service.localIp,
                    port = 8889,
                    isOnline = true
                )
                service.addManualPeer(selfPeer.ipAddress, selfPeer.port, selfPeer.username)
            } else {
                dialog.selectedPeer?.let { peer ->
                    service.addManualPeer(peer.ipAddress, peer.port, peer.username)
                }
            }
        }
    }
    
    private fun showCreateBotDialog() {
        val dialog = CreateBotDialog(project)
        if (dialog.showAndGet()) {
            dialog.createdBotId?.let { botId ->
                val bot = service.getBot(botId)
                if (bot != null) {
                    currentBot = bot
                    currentPeer = null
                    titleLabel.text = "${bot.name} 🤖"
                    statusLabel.text = "聊天机器人 - 自动回复"
                }
            }
        }
    }
    
    private fun setupShortcuts() {
        // Enter 发送
        val enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(enterKey, "send")
        inputArea.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                sendMessage()
            }
        })
        
        // Ctrl+Enter 换行
        val ctrlEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK)
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnterKey, "newline")
        inputArea.actionMap.put("newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                inputArea.append("\n")
            }
        })
    }
    
    fun setCurrentPeer(peer: Peer?) {
        currentPeer = peer
        currentBot = null
        SwingUtilities.invokeLater {
            if (peer != null) {
                titleLabel.text = peer.username
                statusLabel.text = "${peer.ipAddress}:${peer.port}"
            } else {
                titleLabel.text = "选择联系人开始聊天"
                statusLabel.text = ""
            }
        }
    }
    
    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) {
            inputArea.requestFocus()
            return
        }
        
        // 发送给机器人
        currentBot?.let { bot ->
            service.sendBotMessage(bot.id, text)
            inputArea.text = ""
            
            // 添加用户消息到面板
            addMessageToPanel(Message(
                type = MessageType.TEXT,
                senderId = service.currentUser?.id ?: "",
                receiverId = bot.id,
                content = text,
                senderName = service.username
            ))
            return
        }
        
        // 发送给普通联系人
        currentPeer?.let { peer ->
            service.sendTextMessage(peer.id, text)
            inputArea.text = ""
            
            addMessageToPanel(Message(
                type = MessageType.TEXT,
                senderId = service.currentUser?.id ?: "",
                receiverId = peer.id,
                content = text,
                senderName = service.username
            ))
        } ?: run {
            JOptionPane.showMessageDialog(this, "请先选择一个联系人或创建机器人", "提示", JOptionPane.WARNING_MESSAGE)
        }
    }
    
    private fun sendImage() {
        val fileChooser = JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "图片文件 (*.jpg, *.jpeg, *.png, *.gif)",
                "jpg", "jpeg", "png", "gif"
            )
        }
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            currentPeer?.let { peer ->
                service.sendImageMessage(peer.id, file.absolutePath)
                addImageMessageToPanel(file)
            } ?: JOptionPane.showMessageDialog(this, "请先选择一个联系人", "提示", JOptionPane.WARNING_MESSAGE)
        }
    }
    
    private fun sendFile() {
        val fileChooser = JFileChooser()
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            currentPeer?.let { peer ->
                service.sendFileMessage(peer.id, file.absolutePath, file.name)
                addFileMessageToPanel(file)
            } ?: JOptionPane.showMessageDialog(this, "请先选择一个联系人", "提示", JOptionPane.WARNING_MESSAGE)
        }
    }
    
    fun addMessageToPanel(message: Message) {
        SwingUtilities.invokeLater {
            val messageComponent = createTextMessageComponent(message)
            messagePanel.add(messageComponent)
            messagePanel.revalidate()
            messagePanel.repaint()
            
            SwingUtilities.invokeLater {
                val vertical = messageScrollPane.verticalScrollBar
                vertical.value = vertical.maximum
            }
        }
    }
    
    private fun addImageMessageToPanel(file: File) {
        SwingUtilities.invokeLater {
            messagePanel.add(createImageMessageComponent(file))
            messagePanel.revalidate()
            messagePanel.repaint()
        }
    }
    
    private fun addFileMessageToPanel(file: File) {
        SwingUtilities.invokeLater {
            messagePanel.add(createFileMessageComponent(file))
            messagePanel.revalidate()
            messagePanel.repaint()
        }
    }
    
    /**
     * 创建头像图标
     */
    private fun createAvatarIcon(avatarPath: String?, name: String, size: Int = 40): Icon {
        if (avatarPath != null) {
            try {
                val file = File(avatarPath)
                if (file.exists()) {
                    val img = ImageIO.read(file)
                    val scaledImg = img.getScaledInstance(size, size, Image.SCALE_SMOOTH)
                    return ImageIcon(scaledImg)
                }
            } catch (e: Exception) {
                // 忽略错误，使用默认头像
            }
        }
        
        // 默认头像：显示名字首字母
        val initial = name.firstOrNull()?.toString() ?: "?"
        return createInitialAvatar(initial, size)
    }
    
    /**
     * 创建首字母头像
     */
    private fun createInitialAvatar(initial: String, size: Int = 40): Icon {
        val colors = listOf(
            Color(76, 175, 80),   // 绿色
            Color(33, 150, 243),  // 蓝色
            Color(156, 39, 176),  // 紫色
            Color(255, 152, 0),   // 橙色
            Color(233, 30, 99),   // 粉色
            Color(0, 150, 136),   // 青色
        )
        val color = colors[initial.hashCode().mod(colors.size)]
        
        return object : Icon {
            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                
                // 绘制圆形背景
                g2d.color = color
                g2d.fillOval(x, y, size, size)
                
                // 绘制文字
                g2d.color = Color.WHITE
                g2d.font = Font("Microsoft YaHei", Font.BOLD, size * 3 / 5)
                val fm = g2d.fontMetrics
                val textX = x + (size - fm.stringWidth(initial)) / 2
                val textY = y + (size + fm.ascent - fm.descent) / 2
                g2d.drawString(initial, textX, textY)
            }
            
            override fun getIconWidth() = size
            override fun getIconHeight() = size
        }
    }
    
    /**
     * 创建文本消息组件 - 仿微信风格（带头像）
     */
    private fun createTextMessageComponent(message: Message): JPanel {
        val isSentByMe = message.senderId == service.currentUser?.id
        val senderName = message.senderName ?: if (isSentByMe) service.username else "未知用户"
        val avatarPath = if (isSentByMe) service.userAvatar else null
        
        // 检查是否是机器人消息
        val bot = service.getBot(message.senderId)
        val isBotMessage = bot != null
        
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 16, 6, 16)
            
            // 头像
            val avatarIcon = if (isBotMessage) {
                // 机器人头像
                createInitialAvatar("🤖", 40)
            } else {
                createAvatarIcon(avatarPath, senderName, 40)
            }
            
            val avatarLabel = JLabel(avatarIcon).apply {
                preferredSize = Dimension(44, 44)
                horizontalAlignment = SwingConstants.CENTER
            }
            
            if (isSentByMe) {
                // 自己的消息：头像在右边
                val messageContent = createMessageBubble(message, isSentByMe, senderName, isBotMessage)
                add(messageContent, BorderLayout.CENTER)
                add(avatarLabel, BorderLayout.EAST)
            } else {
                // 他人的消息：头像在左边
                add(avatarLabel, BorderLayout.WEST)
                val messageContent = createMessageBubble(message, isSentByMe, senderName, isBotMessage)
                add(messageContent, BorderLayout.CENTER)
            }
        }
    }
    
    private fun createMessageBubble(message: Message, isSentByMe: Boolean, senderName: String, isBotMessage: Boolean): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 8, 0, 8)
            
            // 消息气泡
            val bubblePanel = JPanel(BorderLayout(0, 2)).apply {
                val bgColor = when {
                    isBotMessage -> JBColor(Color(240, 240, 255), Color(50, 50, 70)) // 机器人消息淡紫色
                    isSentByMe -> Color(95, 188, 95)  // 自己的消息微信绿色
                    else -> JBColor(Color(255, 255, 255), Color(45, 45, 45)) // 他人消息白色
                }
                background = bgColor
                border = JBUI.Borders.empty(10, 14, 10, 14)
                
                // 发送者名称
                if (!isSentByMe) {
                    val nameLabel = JLabel(if (isBotMessage) "$senderName 🤖" else senderName).apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 11)
                        foreground = if (isBotMessage) JBColor(Color(128, 0, 128), Color(180, 100, 255)) else JBColor.GRAY
                        border = JBUI.Borders.emptyBottom(4)
                    }
                    add(nameLabel, BorderLayout.NORTH)
                }
                
                // 消息文本
                val messageLabel = JLabel("<html>${message.content.replace("\n", "<br>")}</html>").apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 14)
                    foreground = if (isSentByMe) Color.WHITE else JBColor.BLACK
                }
                add(messageLabel, BorderLayout.CENTER)
                
                // 时间
                val timeLabel = JLabel(message.getFormattedTime()).apply {
                    font = Font("Microsoft YaHei", Font.PLAIN, 10)
                    foreground = if (isSentByMe) Color(220, 255, 220) else JBColor.GRAY
                    border = JBUI.Borders.emptyTop(4)
                }
                add(timeLabel, BorderLayout.SOUTH)
            }
            add(bubblePanel, BorderLayout.CENTER)
        }
    }
    
    private fun createImageMessageComponent(file: File): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 16, 8, 16)
            
            val imagePanel = JPanel(BorderLayout()).apply {
                background = JBColor.PanelBackground
                border = EmptyBorder(4, 4, 4, 4)
                
                try {
                    val icon = ImageIcon(file.path)
                    val img = icon.image
                    val scaledImg = img.getScaledInstance(200, 150, Image.SCALE_SMOOTH)
                    add(JLabel(ImageIcon(scaledImg)), BorderLayout.CENTER)
                } catch (e: Exception) {
                    add(JLabel("[图片] ${file.name}"), BorderLayout.CENTER)
                }
            }
            add(imagePanel, BorderLayout.EAST)
        }
    }
    
    private fun createFileMessageComponent(file: File): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 16, 8, 16)
            
            val filePanel = JPanel(BorderLayout()).apply {
                background = JBColor(Color(245, 245, 245), Color(50, 50, 50))
                border = EmptyBorder(12, 16, 12, 16)
                
                add(JLabel(AllIcons.FileTypes.Any_type).apply {
                    horizontalAlignment = SwingConstants.CENTER
                    preferredSize = Dimension(40, 40)
                }, BorderLayout.WEST)
                
                add(JPanel(BorderLayout(0, 2)).apply {
                    isOpaque = false
                    border = EmptyBorder(0, 12, 0, 0)
                    
                    add(JLabel(file.name).apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 13)
                    }, BorderLayout.NORTH)
                    
                    add(JLabel("${file.length() / 1024} KB").apply {
                        font = Font("Microsoft YaHei", Font.PLAIN, 11)
                        foreground = JBColor.GRAY
                    }, BorderLayout.SOUTH)
                }, BorderLayout.CENTER)
            }
            add(filePanel, BorderLayout.EAST)
        }
    }
}

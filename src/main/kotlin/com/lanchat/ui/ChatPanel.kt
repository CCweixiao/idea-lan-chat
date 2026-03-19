package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.message.Message
import com.lanchat.message.MessageType
import com.lanchat.network.Peer
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * 聊天面板
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    
    private val messagePanel = JPanel()
    private val messageScrollPane: JBScrollPane
    private val inputArea = JTextArea(3, 30)
    private var currentPeer: Peer? = null
    private val peerIpLabel = JLabel()
    
    init {
        messagePanel.layout = BoxLayout(messagePanel, BoxLayout.Y_AXIS)
        messagePanel.background = JBColor.PanelBackground
        
        messageScrollPane = JBScrollPane(messagePanel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
        }
        
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = EmptyBorder(8, 8, 8, 8)
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
        val header = JPanel(BorderLayout())
        header.background = JBColor.PanelBackground
        header.border = JBUI.Borders.empty(12)
        
        // 左侧：用户信息和本机IP
        val userInfo = JPanel(BorderLayout())
        userInfo.isOpaque = false
        
        val titleLabel = JLabel("LAN Chat 💬")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        userInfo.add(titleLabel, BorderLayout.NORTH)
        
        // 显示本机IP
        val ipPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        ipPanel.isOpaque = false
        ipPanel.add(JLabel("本机IP:").apply { foreground = JBColor.GRAY })
        peerIpLabel.text = service.localIp
        peerIpLabel.foreground = JBColor(Color(0, 122, 255), Color(100, 150, 255))
        peerIpLabel.font = peerIpLabel.font.deriveFont(Font.BOLD, 12f)
        ipPanel.add(peerIpLabel)
        userInfo.add(ipPanel, BorderLayout.SOUTH)
        
        header.add(userInfo, BorderLayout.WEST)
        
        // 右侧：设置按钮
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightPanel.isOpaque = false
        
        // 个人信息设置按钮
        rightPanel.add(createToolbarButton(AllIcons.General.Settings, "个人信息设置") {
            showProfileDialog()
        })
        
        header.add(rightPanel, BorderLayout.EAST)
        
        return header
    }
    
    private fun showProfileDialog() {
        val dialog = ProfileDialog(project)
        if (dialog.showAndGet()) {
            // 更新显示
            peerIpLabel.text = service.localIp
        }
    }
    
    private fun createToolbarButton(icon: Icon, tooltip: String, action: (() -> Unit)? = null): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            margin = Insets(4, 4, 4, 4)
            action?.let { addActionListener { it() } }
        }
    }
    
    private fun createInputPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = JBColor.PanelBackground
        panel.border = JBUI.Borders.emptyTop(8)
        
        // 顶部工具栏
        val toolbar = JPanel(BorderLayout())
        toolbar.isOpaque = false
        toolbar.border = JBUI.Borders.emptyBottom(4)
        
        // 左侧工具按钮
        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        leftTools.isOpaque = false
        leftTools.add(createToolbarButton(AllIcons.General.InspectionsEye, "表情"))
        leftTools.add(createToolbarButton(AllIcons.FileTypes.Any_type, "发送图片") { sendImage() })
        leftTools.add(createToolbarButton(AllIcons.FileTypes.Any_type, "发送文件") { sendFile() })
        leftTools.add(createToolbarButton(AllIcons.Actions.Refresh, "刷新联系人") { service.refreshPeers() })
        leftTools.add(createToolbarButton(AllIcons.General.Add, "添加联系人") { showAddContactDialog() })
        toolbar.add(leftTools, BorderLayout.WEST)
        
        panel.add(toolbar, BorderLayout.NORTH)
        
        // 中间输入区域
        val inputPanel = JPanel(BorderLayout())
        inputPanel.background = JBColor.PanelBackground
        
        val inputScrollPane = JBScrollPane(inputArea)
        inputScrollPane.border = LineBorder(JBColor.GRAY, 1)
        inputPanel.add(inputScrollPane, BorderLayout.CENTER)
        
        // 美化发送按钮
        val sendButton = JButton("发送").apply {
            font = Font("Microsoft YaHei", Font.BOLD, 13)
            background = JBColor(Color(0, 122, 255), Color(0, 86, 178))
            foreground = Color.WHITE
            isBorderPainted = false
            isFocusPainted = false
            margin = Insets(8, 20, 8, 20)
            cursor = Cursor(Cursor.HAND_CURSOR)
            addActionListener { sendMessage() }
        }
        inputPanel.add(sendButton, BorderLayout.EAST)
        
        panel.add(inputPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun showAddContactDialog() {
        val dialog = AddContactDialog(project)
        if (dialog.showAndGet()) {
            if (dialog.isAddSelf) {
                // 添加自己用于测试
                val selfPeer = Peer(
                    id = service.currentUser?.id ?: "self",
                    username = "${service.username} (自己)",
                    ipAddress = service.localIp,
                    port = 8889,
                    isOnline = true
                )
                setCurrentPeer(selfPeer)
            } else {
                dialog.selectedPeer?.let { peer ->
                    service.addManualPeer(peer.ipAddress, peer.port, peer.username)
                }
            }
        }
    }
    
    private fun setupShortcuts() {
        val enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(enterKey, "send")
        inputArea.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                sendMessage()
            }
        })
        
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
        // 更新标题显示当前聊天对象
    }
    
    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) {
            inputArea.requestFocus()
            return
        }
        
        currentPeer?.let { peer ->
            service.sendTextMessage(peer.id, text)
            inputArea.text = ""
            
            addMessageToPanel(Message(
                type = MessageType.TEXT,
                senderId = service.currentUser?.id ?: "",
                receiverId = peer.id,
                content = text
            ))
        } ?: run {
            // 提示选择联系人
            Messages.showInfoMessage("请先选择一个联系人", "提示")
        }
    }
    
    private fun sendImage() {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "图片文件 (*.jpg, *.jpeg, *.png, *.gif)",
            "jpg", "jpeg", "png", "gif"
        )
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            currentPeer?.let { peer ->
                service.sendImageMessage(peer.id, file.absolutePath)
                addImageMessageToPanel(file)
            } ?: Messages.showInfoMessage("请先选择一个联系人", "提示")
        }
    }
    
    private fun sendFile() {
        val fileChooser = JFileChooser()
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            currentPeer?.let { peer ->
                service.sendFileMessage(peer.id, file.absolutePath, file.name)
                addFileMessageToPanel(file)
            } ?: Messages.showInfoMessage("请先选择一个联系人", "提示")
        }
    }
    
    private fun addMessageToPanel(message: Message) {
        SwingUtilities.invokeLater {
            messagePanel.add(createTextMessageComponent(message))
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
    
    private fun createTextMessageComponent(message: Message): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = JBUI.Borders.empty(8, 16, 8, 16)
        
        val isSentByMe = message.senderId == service.currentUser?.id
        
        val bubblePanel = JPanel(BorderLayout())
        bubblePanel.background = if (isSentByMe) 
            JBColor(Color(0, 122, 255), Color(0, 86, 178))
        else 
            JBColor(Color(240, 240, 240), Color(60, 60, 60))
        bubblePanel.border = JBUI.Borders.empty(12, 16)
        
        val messageLabel = JLabel(message.content)
        messageLabel.foreground = if (isSentByMe) Color.WHITE else JBColor.BLACK
        messageLabel.font = Font("Microsoft YaHei", Font.PLAIN, 14)
        bubblePanel.add(messageLabel, BorderLayout.CENTER)
        
        val timeLabel = JLabel(message.getFormattedTime())
        timeLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10)
        timeLabel.foreground = if (isSentByMe) Color(200, 200, 200) else JBColor.GRAY
        bubblePanel.add(timeLabel, BorderLayout.SOUTH)
        
        if (isSentByMe) {
            panel.add(bubblePanel, BorderLayout.EAST)
        } else {
            panel.add(bubblePanel, BorderLayout.WEST)
        }
        
        return panel
    }
    
    private fun createImageMessageComponent(file: File): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = JBUI.Borders.empty(8, 16, 8, 16)
        
        val innerPanel = JPanel(BorderLayout())
        innerPanel.background = JBColor.PanelBackground
        innerPanel.border = JBUI.Borders.empty()
        
        try {
            val icon = ImageIcon(file.path)
            val img = icon.image
            val scaledImg = img.getScaledInstance(200, 150, 4)
            innerPanel.add(JLabel(ImageIcon(scaledImg)), BorderLayout.CENTER)
        } catch (e: Exception) {
            innerPanel.add(JLabel("[图片] ${file.name}"), BorderLayout.CENTER)
        }
        
        panel.add(innerPanel, BorderLayout.EAST)
        return panel
    }
    
    private fun createFileMessageComponent(file: File): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = JBUI.Borders.empty(8, 16, 8, 16)
        
        val innerPanel = JPanel(BorderLayout())
        innerPanel.background = JBColor(Color(240, 240, 240), Color(60, 60, 60))
        innerPanel.border = JBUI.Borders.empty(12, 16)
        
        val iconLabel = JLabel(AllIcons.FileTypes.Any_type)
        iconLabel.horizontalAlignment = SwingConstants.CENTER
        innerPanel.add(iconLabel, BorderLayout.WEST)
        
        val infoPanel = JPanel(BorderLayout())
        infoPanel.isOpaque = false
        infoPanel.border = JBUI.Borders.emptyLeft(12)
        
        infoPanel.add(JLabel(file.name), BorderLayout.NORTH)
        
        val sizeLabel = JLabel("${file.length() / 1024} KB")
        sizeLabel.foreground = JBColor.GRAY
        sizeLabel.font = Font("Microsoft YaHei", Font.PLAIN, 11)
        infoPanel.add(sizeLabel, BorderLayout.SOUTH)
        
        innerPanel.add(infoPanel, BorderLayout.CENTER)
        panel.add(innerPanel, BorderLayout.EAST)
        
        return panel
    }
}

package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
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

/**
 * 聊天面板
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = LanChatService.getInstance()
    
    private val messagePanel = JPanel()
    private val messageScrollPane: JBScrollPane
    private val inputArea = JTextArea(3, 0)
    private var currentPeer: Peer? = null
    
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
        
        val userInfo = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        userInfo.isOpaque = false
        val label = JLabel("选择联系人开始聊天")
        label.font = label.font.deriveFont(Font.BOLD, 14f)
        userInfo.add(label)
        header.add(userInfo, BorderLayout.WEST)
        
        return header
    }
    
    private fun createToolbarButton(icon: Icon, tooltip: String, action: (() -> Unit)? = null): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            action?.let { addActionListener { it() } }
        }
    }
    
    private fun createInputPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = JBColor.PanelBackground
        panel.border = JBUI.Borders.emptyTop(8)
        
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        toolbar.isOpaque = false
        
        toolbar.add(createToolbarButton(AllIcons.General.InspectionsEye, "表情"))
        toolbar.add(createToolbarButton(AllIcons.FileTypes.Any_type, "发送图片") { sendImage() })
        toolbar.add(createToolbarButton(AllIcons.FileTypes.Any_type, "发送文件") { sendFile() })
        
        panel.add(toolbar, BorderLayout.NORTH)
        
        val inputScrollPane = JBScrollPane(inputArea)
        inputScrollPane.border = JBUI.Borders.empty()
        panel.add(inputScrollPane, BorderLayout.CENTER)
        
        val sendButton = JButton("发送")
        sendButton.addActionListener { sendMessage() }
        panel.add(sendButton, BorderLayout.EAST)
        
        return panel
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
    
    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        
        currentPeer?.let { peer ->
            service.sendTextMessage(peer.id, text)
            inputArea.text = ""
            
            addMessageToPanel(Message(
                type = MessageType.TEXT,
                senderId = service.currentUser?.id ?: "",
                receiverId = peer.id,
                content = text
            ))
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
            }
        }
    }
    
    private fun sendFile() {
        val fileChooser = JFileChooser()
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            currentPeer?.let { peer ->
                service.sendFileMessage(peer.id, file.absolutePath, file.name)
                addFileMessageToPanel(file)
            }
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
        bubblePanel.add(messageLabel, BorderLayout.CENTER)
        
        val timeLabel = JLabel(message.getFormattedTime())
        timeLabel.font = timeLabel.font.deriveFont(10f)
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
            val scaledImg = img.getScaledInstance(200, 150, 4) // SCALE_SMOOTH = 4
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
        sizeLabel.font = sizeLabel.font.deriveFont(11f)
        infoPanel.add(sizeLabel, BorderLayout.SOUTH)
        
        innerPanel.add(infoPanel, BorderLayout.CENTER)
        panel.add(innerPanel, BorderLayout.EAST)
        
        return panel
    }
    
    fun setCurrentPeer(peer: Peer?) {
        currentPeer = peer
    }
}

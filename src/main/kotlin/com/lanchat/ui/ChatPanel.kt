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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
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
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val messagePanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor.PanelBackground
    }
    
    private val messageScrollPane = JBScrollPane(messagePanel).apply {
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        border = JBUI.Borders.empty()
    }
    
    private val inputArea = JTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        border = EmptyBorder(8, 8, 8, 8)
    }
    
    private var currentPeer: Peer? = null
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        // 顶部标题栏
        val header = createHeader()
        add(header, BorderLayout.NORTH)
        
        // 中间消息区域
        add(messageScrollPane, BorderLayout.CENTER)
        
        // 底部输入区域
        val inputPanel = createInputPanel()
        add(inputPanel, BorderLayout.SOUTH)
        
        // 设置快捷键
        setupShortcuts()
    }
    
    private fun createHeader(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(12)
            
            // 左侧：用户信息
            val userInfo = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                
                add(JLabel("选择联系人开始聊天").apply {
                    font = font.deriveFont(Font.BOLD, 14f)
                })
            }
            add(userInfo, BorderLayout.WEST)
            
            // 右侧：操作按钮
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                
                // 语音通话
                add(JButton(AllIcons.Actions.Resume).apply {
                    toolTipText = "语音通话"
                    isBorderPainted = false
                    isContentAreaFilled = false
                })
                
                // 视频通话
                add(JButton(AllIcons.Actions.Preview).apply {
                    toolTipText = "视频通话"
                    isBorderPainted = false
                    isContentAreaFilled = false
                })
                
                // 更多选项
                add(JButton(AllIcons.Actions.More).apply {
                    toolTipText = "更多选项"
                    isBorderPainted = false
                    isContentAreaFilled = false
                })
            }
            add(actions, BorderLayout.EAST)
        }
    }
    
    private fun createInputPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.emptyTop(8)
            
            // 工具栏
            val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                
                // 表情
                add(JButton(AllIcons.General.InspectionsEye).apply {
                    toolTipText = "表情"
                    isBorderPainted = false
                    isContentAreaFilled = false
                })
                
                // 图片
                add(JButton(AllIcons.FileTypes.Image).apply {
                    toolTipText = "发送图片"
                    isBorderPainted = false
                    isContentAreaFilled = false
                    addActionListener {
                        sendImage()
                    }
                })
                
                // 文件
                add(JButton(AllIcons.FileTypes.Any_type).apply {
                    toolTipText = "发送文件"
                    isBorderPainted = false
                    isContentAreaFilled = false
                    addActionListener {
                        sendFile()
                    }
                })
                
                // 代码片段
                add(JButton(AllIcons.Actions.Paste).apply {
                    toolTipText = "发送代码片段"
                    isBorderPainted = false
                    isContentAreaFilled = false
                })
            }
            add(toolbar, BorderLayout.NORTH)
            
            // 输入框
            val inputScrollPane = JBScrollPane(inputArea).apply {
                border = JBUI.Borders.empty()
            }
            add(inputScrollPane, BorderLayout.CENTER)
            
            // 发送按钮
            val sendButton = JButton("发送").apply {
                addActionListener {
                    sendMessage()
                }
            }
            add(sendButton, BorderLayout.EAST)
        }
    }
    
    private fun setupShortcuts() {
        // Enter 发送消息
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
    
    /**
     * 发送文本消息
     */
    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        
        currentPeer?.let { peer ->
            service.sendTextMessage(peer.id, text)
            inputArea.text = ""
            
            // 添加到消息面板
            addMessageToPanel(Message(
                type = MessageType.TEXT,
                senderId = service.currentUser?.id ?: "",
                receiverId = peer.id,
                content = text
            ))
        }
    }
    
    /**
     * 发送图片
     */
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
            }
        }
    }
    
    /**
     * 发送文件
     */
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
    
    /**
     * 添加文本消息到面板
     */
    private fun addMessageToPanel(message: Message) {
        SwingUtilities.invokeLater {
            val messageComponent = createTextMessageComponent(message)
            messagePanel.add(messageComponent)
            messagePanel.revalidate()
            messagePanel.repaint()
            
            // 滚动到底部
            SwingUtilities.invokeLater {
                val vertical = messageScrollPane.verticalScrollBar
                vertical.value = vertical.maximum
            }
        }
    }
    
    /**
     * 添加图片消息到面板
     */
    private fun addImageMessageToPanel(file: File) {
        SwingUtilities.invokeLater {
            val imageComponent = createImageMessageComponent(file)
            messagePanel.add(imageComponent)
            messagePanel.revalidate()
            messagePanel.repaint()
            
            // 滚动到底部
            SwingUtilities.invokeLater {
                val vertical = messageScrollPane.verticalScrollBar
                vertical.value = vertical.maximum
            }
        }
    }
    
    /**
     * 添加文件消息到面板
     */
    private fun addFileMessageToPanel(file: File) {
        SwingUtilities.invokeLater {
            val fileComponent = createFileMessageComponent(file)
            messagePanel.add(fileComponent)
            messagePanel.revalidate()
            messagePanel.repaint()
        }
    }
    
    /**
     * 创建文本消息组件
     */
    private fun createTextMessageComponent(message: Message): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 16, 8, 16)
            
            val isSentByMe = message.senderId == service.currentUser?.id
            
            val bubblePanel = JPanel(BorderLayout()).apply {
                background = if (isSentByMe) 
                    JBColor(Color(0, 122, 255), Color(0, 86, 178))
                else 
                    JBColor(Color(240, 240, 240), Color(60, 60, 60))
                border = JBUI.Borders.empty(12, 16)
                
                val messageLabel = JLabel(message.content).apply {
                    foreground = if (isSentByMe) Color.WHITE else JBColor.BLACK
                }
                add(messageLabel, BorderLayout.CENTER)
                
                val timeLabel = JLabel(message.getFormattedTime()).apply {
                    font = font.deriveFont(10f)
                    foreground = if (isSentByMe) Color(200, 200, 200) else JBColor.GRAY
                }
                add(timeLabel, BorderLayout.SOUTH)
            }
            
            if (isSentByMe) {
                add(bubblePanel, BorderLayout.EAST)
            } else {
                add(bubblePanel, BorderLayout.WEST)
            }
        }
    }
    
    /**
     * 创建图片消息组件
     */
    private fun createImageMessageComponent(file: File): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 16, 8, 16)
            
            add(JPanel(BorderLayout()).apply {
                background = JBColor.PanelBackground
                border = JBUI.Borders.empty()
                
                try {
                    val icon = ImageIcon(file.path)
                    val scaledIcon = icon.image.getScaledInstance(200, 150, Image.SCALE_SMOOTH)
                    add(JLabel(ImageIcon(scaledIcon)), BorderLayout.CENTER)
                } catch (e: Exception) {
                    add(JLabel("[图片] ${file.name}"), BorderLayout.CENTER)
                }
            }, BorderLayout.EAST)
        }
    }
    
    /**
     * 创建文件消息组件
     */
    private fun createFileMessageComponent(file: File): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(8, 16, 8, 16)
            
            add(JPanel(BorderLayout()).apply {
                background = JBColor(Color(240, 240, 240), Color(60, 60, 60))
                border = JBUI.Borders.empty(12, 16)
                
                add(JLabel(AllIcons.FileTypes.Any_type).apply {
                    horizontalAlignment = SwingConstants.CENTER
                }, BorderLayout.WEST)
                
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.emptyLeft(12)
                    
                    add(JLabel(file.name), BorderLayout.NORTH)
                    add(JLabel("${file.length() / 1024} KB").apply {
                        foreground = JBColor.GRAY
                        font = font.deriveFont(11f)
                    }, BorderLayout.SOUTH)
                }, BorderLayout.CENTER)
            }, BorderLayout.EAST)
        }
    }
    
    /**
     * 设置当前聊天对象
     */
    fun setCurrentPeer(peer: Peer?) {
        currentPeer = peer
        // 更新标题栏
        // 加载聊天历史
    }
}

package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.*
import javax.swing.*

/**
 * 创建机器人对话框
 */
class CreateBotDialog(private val project: Project) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    
    private val nameField = JTextField(20)
    private val descriptionField = JTextField(30)
    
    var createdBotId: String? = null
        private set
    
    init {
        title = "创建聊天机器人"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(400, 200)
            
            // 说明文字
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(12)
                
                add(JLabel("🤖 创建一个聊天机器人，可以陪你聊天测试").apply {
                    font = font.deriveFont(Font.PLAIN, 13f)
                }, BorderLayout.NORTH)
                
                add(JLabel("机器人会自动回复你的消息").apply {
                    font = font.deriveFont(Font.PLAIN, 12f)
                    foreground = JBColor.GRAY
                }, BorderLayout.SOUTH)
            }
            add(infoPanel, BorderLayout.NORTH)
            
            // 表单
            val formPanel = JPanel(GridBagLayout()).apply {
                val gbc = GridBagConstraints()
                gbc.fill = GridBagConstraints.HORIZONTAL
                gbc.insets = Insets(6, 4, 6, 4)
                
                gbc.gridx = 0; gbc.gridy = 0
                add(JLabel("机器人名称:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, gbc)
                gbc.gridx = 1
                gbc.weightx = 1.0
                add(nameField, gbc)
                
                gbc.gridx = 0; gbc.gridy = 1
                gbc.weightx = 0.0
                add(JLabel("描述:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, gbc)
                gbc.gridx = 1
                gbc.weightx = 1.0
                add(descriptionField, gbc)
            }
            add(formPanel, BorderLayout.CENTER)
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            return ValidationInfo("请输入机器人名称", nameField)
        }
        if (name.length > 20) {
            return ValidationInfo("名称不能超过20个字符", nameField)
        }
        return null
    }
    
    override fun doOKAction() {
        val name = nameField.text.trim()
        val description = descriptionField.text.trim()
        
        val bot = service.createBot(name, description)
        createdBotId = bot.id
        
        super.doOKAction()
    }
}

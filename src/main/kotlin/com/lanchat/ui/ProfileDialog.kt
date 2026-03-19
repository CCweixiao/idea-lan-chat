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
 * 个人信息编辑对话框
 */
class ProfileDialog(private val project: Project) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    
    // 昵称输入框
    private val nicknameField = JTextField(20)
    
    // 显示当前IP
    private val ipLabel = JLabel()
    
    init {
        title = "个人信息"
        init()
        loadData()
    }
    
    private fun loadData() {
        nicknameField.text = service.username
        ipLabel.text = service.localIp
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(400, 200)
            
            // 头像区域（使用刘亦菲头像）
            val avatarPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyBottom(16)
                
                // 头像标签
                val avatarLabel = JLabel().apply {
                    icon = AllIcons.General.User
                    horizontalAlignment = SwingConstants.CENTER
                    preferredSize = Dimension(80, 80)
                }
                
                add(avatarLabel, BorderLayout.CENTER)
            }
            add(avatarPanel, BorderLayout.NORTH)
            
            // 信息编辑区域
            val infoPanel = JPanel(GridBagLayout()).apply {
                val gbc = GridBagConstraints()
                gbc.fill = GridBagConstraints.HORIZONTAL
                gbc.insets = Insets(8, 4, 8, 4)
                
                // 昵称
                gbc.gridx = 0; gbc.gridy = 0
                add(JLabel("昵称:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, gbc)
                gbc.gridx = 1
                gbc.weightx = 1.0
                add(nicknameField, gbc)
                
                // IP地址（只读）
                gbc.gridx = 0; gbc.gridy = 1
                gbc.weightx = 0.0
                add(JLabel("IP地址:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, gbc)
                gbc.gridx = 1
                ipLabel.foreground = JBColor.GRAY
                add(ipLabel, gbc)
                
                // 提示信息
                gbc.gridx = 0; gbc.gridy = 2
                gbc.gridwidth = 2
                add(JLabel("提示：昵称修改后将在下次发送消息时生效").apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(11f)
                }, gbc)
            }
            add(infoPanel, BorderLayout.CENTER)
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val nickname = nicknameField.text.trim()
        if (nickname.isEmpty()) {
            return ValidationInfo("昵称不能为空", nicknameField)
        }
        if (nickname.length > 20) {
            return ValidationInfo("昵称长度不能超过20个字符", nicknameField)
        }
        return null
    }
    
    override fun doOKAction() {
        val newNickname = nicknameField.text.trim()
        if (newNickname.isNotEmpty()) {
            service.updateUsername(newNickname)
        }
        super.doOKAction()
    }
    
    /**
     * 获取新昵称
     */
    fun getNewNickname(): String = nicknameField.text.trim()
}

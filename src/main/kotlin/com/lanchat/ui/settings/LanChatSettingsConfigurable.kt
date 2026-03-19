package com.lanchat.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * LAN Chat 设置页面
 */
class LanChatSettingsConfigurable : Configurable {
    
    private val service = LanChatService.getInstance()
    private var usernameField: JTextField? = null
    private var mainPanel: JPanel? = null
    
    override fun getDisplayName(): String = "LAN Chat"
    
    override fun createComponent(): JComponent? {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(16)
        
        // 用户名设置
        val namePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        namePanel.add(JLabel("用户名:"))
        usernameField = JTextField(service.username, 20)
        namePanel.add(usernameField)
        panel.add(namePanel)
        
        // 说明
        val notePanel = JPanel()
        notePanel.layout = BoxLayout(notePanel, BoxLayout.Y_AXIS)
        notePanel.border = JBUI.Borders.emptyTop(16)
        
        val noteLabel = JLabel("设置说明:")
        noteLabel.font = noteLabel.font.deriveFont(java.awt.Font.BOLD)
        notePanel.add(noteLabel)
        
        val note1 = JLabel("• 用户名将在局域网中显示给其他用户")
        note1.foreground = JBColor.GRAY
        notePanel.add(note1)
        
        val note2 = JLabel("• 修改后需要重启插件生效")
        note2.foreground = JBColor.GRAY
        notePanel.add(note2)
        
        panel.add(notePanel)
        
        mainPanel = panel
        return panel
    }
    
    override fun isModified(): Boolean {
        return usernameField?.text != service.username
    }
    
    override fun apply() {
        usernameField?.text?.let { newUsername ->
            if (newUsername.isNotBlank()) {
                service.updateUsername(newUsername)
            }
        }
    }
    
    override fun reset() {
        usernameField?.text = service.username
    }
}

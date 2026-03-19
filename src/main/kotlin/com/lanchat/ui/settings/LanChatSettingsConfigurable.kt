package com.lanchat.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
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
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(16)
            
            // 用户名设置
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                border = JBUI.Borders.emptyBottom(8)
                
                add(JLabel("用户名:").apply {
                    preferredSize = Dimension(100, height)
                })
                add(Box.createHorizontalStrut(8))
                
                usernameField = JTextField(service.username).apply {
                    maximumSize = Dimension(Integer.MAX_VALUE, 30)
                }
                add(usernameField)
            })
            
            // 说明
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.emptyTop(16)
                
                add(JLabel("设置说明:").apply {
                    font = font.deriveFont(java.awt.Font.BOLD)
                    border = JBUI.Borders.emptyBottom(8)
                })
                
                add(JLabel("• 用户名将在局域网中显示给其他用户").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.emptyBottom(4)
                })
                
                add(JLabel("• 修改后需要重启插件生效").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.emptyBottom(4)
                })
            })
        }
        
        mainPanel = panel
        return panel
    }
    
    override fun isModified(): Boolean {
        return usernameField?.text != service.username
    }
    
    override fun apply() {
        usernameField?.text?.let { newUsername ->
            if (newUsername.isNotBlank()) {
                service.setUsername(newUsername)
            }
        }
    }
    
    override fun reset() {
        usernameField?.text = service.username
    }
}

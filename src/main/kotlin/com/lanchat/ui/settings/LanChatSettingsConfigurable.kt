package com.lanchat.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.ui.ThemeManager
import java.awt.*
import javax.swing.*

/**
 * LAN Chat 设置页面
 */
class LanChatSettingsConfigurable : Configurable {
    
    private val settings = LanChatSettings()
    private val service = LanChatService.getInstance()
    private var usernameField: JTextField? = null
    private var themeCombo: JComboBox<String>? = null
    private var fontSizeCombo: JComboBox<String>? = null
    private var udpPortField: JTextField? = null
    private var tcpPortField: JTextField? = null
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
        
        // 主题设置
        val themePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        themePanel.border = JBUI.Borders.emptyTop(12)
        themePanel.add(JLabel("主题:"))
        themeCombo = JComboBox(ThemeManager.Theme.values().map { it.displayName }.toTypedArray())
        // 恢复当前设置
        val currentTheme = try { ThemeManager.Theme.valueOf(settings.getTheme()) } catch (_: Exception) { ThemeManager.Theme.LIGHT }
        themeCombo?.selectedItem = currentTheme.displayName
        themeCombo?.addActionListener {
            applyThemePreview()
        }
        themePanel.add(themeCombo)
        panel.add(themePanel)

        // 字体大小设置
        val fontPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        fontPanel.border = JBUI.Borders.emptyTop(12)
        fontPanel.add(JLabel("字体大小:"))
        fontSizeCombo = JComboBox(ThemeManager.FontSize.values().map { it.label }.toTypedArray())
        val currentFontSize = settings.getFontSize()
        val currentSizeOption = ThemeManager.FontSize.values().find { it.size == currentFontSize }
        fontSizeCombo?.selectedItem = currentSizeOption?.label ?: ThemeManager.FontSize.MEDIUM.label
        fontSizeCombo?.addActionListener {
            applyFontSizePreview()
        }
        fontPanel.add(fontSizeCombo)
        panel.add(fontPanel)

        // 端口设置
        val portPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        portPanel.border = JBUI.Borders.emptyTop(12)
        portPanel.add(JLabel("UDP 端口:"))
        udpPortField = JTextField(settings.getUdpPort().toString(), 6)
        portPanel.add(udpPortField)
        portPanel.add(JLabel("TCP 端口:"))
        tcpPortField = JTextField(settings.getTcpPort().toString(), 6)
        portPanel.add(tcpPortField)
        panel.add(portPanel)

        // 主题说明
        val themeNotePanel = JPanel()
        themeNotePanel.layout = BoxLayout(themeNotePanel, BoxLayout.Y_AXIS)
        themeNotePanel.border = JBUI.Borders.empty(8, 0, 0, 0)
        val themeNote1 = JLabel("• 亮色: 绿色气泡 + 白色背景")
        themeNote1.foreground = JBColor.GRAY
        themeNotePanel.add(themeNote1)
        val themeNote2 = JLabel("• 暗色: 低调深色气泡 + 深色背景（与 IDEA 夜间模式一致）")
        themeNote2.foreground = JBColor.GRAY
        themeNotePanel.add(themeNote2)
        panel.add(themeNotePanel)
        
        // 说明
        val notePanel = JPanel()
        notePanel.layout = BoxLayout(notePanel, BoxLayout.Y_AXIS)
        notePanel.border = JBUI.Borders.emptyTop(16)
        
        val noteLabel = JLabel("设置说明:")
        noteLabel.font = noteLabel.font.deriveFont(Font.BOLD)
        notePanel.add(noteLabel)
        
        val note1 = JLabel("• 用户名将在局域网中显示给其他用户")
        note1.foreground = JBColor.GRAY
        notePanel.add(note1)
        
        val note2 = JLabel("• 修改字体大小后需要重启插件生效")
        note2.foreground = JBColor.GRAY
        notePanel.add(note2)

        val note3 = JLabel("• 同一机器上运行多个实例需要使用不同的端口")
        note3.foreground = JBColor.GRAY
        notePanel.add(note3)
        
        panel.add(notePanel)
        
        mainPanel = panel
        return panel
    }

    private fun applyThemePreview() {
        val displayName = themeCombo?.selectedItem as? String ?: return
        val theme = ThemeManager.Theme.values().find { it.displayName == displayName } ?: return
        ThemeManager.setTheme(theme)
        settings.setTheme(theme.name)
    }

    private fun applyFontSizePreview() {
        val label = fontSizeCombo?.selectedItem as? String ?: return
        val fontSize = ThemeManager.FontSize.values().find { it.label == label } ?: return
        ThemeManager.setBaseFontSize(fontSize.size)
        settings.setFontSize(fontSize.size)
    }
    
    override fun isModified(): Boolean {
        val currentFontSize = settings.getFontSize()
        val selectedFontSize = (fontSizeCombo?.selectedItem as? String)?.let { label ->
            ThemeManager.FontSize.values().find { it.label == label }?.size
        }
        return usernameField?.text != service.username ||
               themeCombo?.selectedItem != ThemeManager.Theme.valueOf(settings.getTheme()).displayName ||
               selectedFontSize != currentFontSize ||
               udpPortField?.text?.toIntOrNull() != settings.getUdpPort() ||
               tcpPortField?.text?.toIntOrNull() != settings.getTcpPort()
    }
    
    override fun apply() {
        usernameField?.text?.let { newUsername ->
            if (newUsername.isNotBlank()) {
                service.updateUsername(newUsername)
            }
        }
        applyThemePreview()
        applyFontSizePreview()

        // 保存端口设置
        udpPortField?.text?.toIntOrNull()?.let {
            if (it > 0 && it < 65536) {
                settings.setUdpPort(it)
            }
        }
        tcpPortField?.text?.toIntOrNull()?.let {
            if (it > 0 && it < 65536) {
                settings.setTcpPort(it)
            }
        }
    }
    
    override fun reset() {
        usernameField?.text = service.username
        val currentTheme = try { ThemeManager.Theme.valueOf(settings.getTheme()) } catch (_: Exception) { ThemeManager.Theme.LIGHT }
        themeCombo?.selectedItem = currentTheme.displayName
        val currentFontSize = ThemeManager.FontSize.values().find { it.size == settings.getFontSize() }
        fontSizeCombo?.selectedItem = currentFontSize?.label ?: ThemeManager.FontSize.MEDIUM.label
        udpPortField?.text = settings.getUdpPort().toString()
        tcpPortField?.text = settings.getTcpPort().toString()
    }
}

package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import java.awt.*
import java.awt.event.ActionEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

/**
 * 个人信息编辑对话框
 */
class ProfileDialog(private val project: Project) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    
    private val nicknameField = JTextField(20)
    private val ipLabel = JLabel()
    private var avatarPath: String? = service.userAvatar
    private val avatarButton = JButton()
    private val encryptionCheckbox = JCheckBox("启用消息加密传输")
    private val encryptionKeyField = JPasswordField(20)
    
    init {
        title = "个人信息"
        init()
        loadData()
    }
    
    private fun loadData() {
        nicknameField.text = service.username
        ipLabel.text = service.localIp
        encryptionCheckbox.isSelected = service.isEncryptionEnabled()
        encryptionKeyField.text = service.getEncryptionKey()
        encryptionKeyField.isEnabled = encryptionCheckbox.isSelected
        updateAvatarDisplay()
    }
    
    private fun updateAvatarDisplay() {
        avatarButton.apply {
            preferredSize = Dimension(80, 80)
            icon = if (avatarPath != null) {
                try {
                    val img = ImageIO.read(File(avatarPath!!))
                    val scaledImg = img.getScaledInstance(80, 80, Image.SCALE_SMOOTH)
                    ImageIcon(scaledImg)
                } catch (e: Exception) {
                    AllIcons.General.User
                }
            } else {
                AllIcons.General.User
            }
            text = if (avatarPath == null) "点击上传" else ""
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            cursor = Cursor(Cursor.HAND_CURSOR)
        }
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(450, 400)
            
            // 头像区域
            val avatarPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(16)
                
                // 头像按钮
                avatarButton.apply {
                    addActionListener {
                        chooseAvatar()
                    }
                }
                
                add(avatarButton, BorderLayout.CENTER)
                
                // 提示
                add(JLabel("点击头像更换图片").apply {
                    horizontalAlignment = SwingConstants.CENTER
                    foreground = JBColor.GRAY
                    font = font.deriveFont(11f)
                }, BorderLayout.SOUTH)
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
                ipLabel.foreground = JBColor(Color(0, 122, 255), Color(100, 150, 255))
                add(ipLabel, gbc)
                
                // 分隔线
                gbc.gridx = 0; gbc.gridy = 2
                gbc.gridwidth = 2
                add(JSeparator().apply {
                    foreground = JBColor(Color(220, 220, 220), Color(60, 60, 60))
                }, gbc)

                // 加密开关
                gbc.gridx = 0; gbc.gridy = 3
                gbc.gridwidth = 2
                encryptionCheckbox.apply {
                    font = font.deriveFont(Font.BOLD)
                    isOpaque = false
                    addActionListener { encryptionKeyField.isEnabled = isSelected }
                }
                add(encryptionCheckbox, gbc)

                // 加密密钥
                gbc.gridx = 0; gbc.gridy = 4
                gbc.gridwidth = 1; gbc.weightx = 0.0
                add(JLabel("加密密钥:").apply {
                    font = font.deriveFont(Font.BOLD)
                }, gbc)
                gbc.gridx = 1; gbc.weightx = 1.0
                add(encryptionKeyField, gbc)

                // 加密提示
                gbc.gridx = 0; gbc.gridy = 5
                gbc.gridwidth = 2
                add(JLabel("提示：所有通信方需使用相同密钥，留空则使用内置默认密钥").apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(11f)
                }, gbc)
            }
            add(infoPanel, BorderLayout.CENTER)
        }
    }
    
    private fun chooseAvatar() {
        val fileChooser = JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "图片文件 (*.jpg, *.jpeg, *.png, *.gif)",
                "jpg", "jpeg", "png", "gif"
            )
            dialogTitle = "选择头像"
        }
        
        if (fileChooser.showOpenDialog(avatarButton) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            avatarPath = file.absolutePath
            updateAvatarDisplay()
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val nickname = nicknameField.text.trim()
        if (nickname.isEmpty()) {
            return ValidationInfo("昵称不能为空", nicknameField)
        }
        if (nickname.length > 10) {
            return ValidationInfo("昵称不能超过10个字符", nicknameField)
        }
        return null
    }
    
    override fun doOKAction() {
        val newNickname = nicknameField.text.trim()
        if (newNickname.isNotEmpty()) {
            service.updateUsername(newNickname)
        }
        
        avatarPath?.let {
            service.updateUserAvatar(it)
        }

        service.setEncryptionEnabled(encryptionCheckbox.isSelected)
        val key = String(encryptionKeyField.password).trim()
        service.updateEncryptionKey(key)

        service.broadcastProfileUpdate()
        
        super.doOKAction()
    }
    
    /**
     * 获取新昵称
     */
    fun getNewNickname(): String = nicknameField.text.trim()
}

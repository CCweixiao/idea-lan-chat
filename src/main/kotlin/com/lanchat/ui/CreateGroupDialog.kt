package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Peer
import java.awt.*
import javax.swing.*

/**
 * 创建群聊对话框
 */
class CreateGroupDialog(
    private val project: Project,
    private val availablePeers: List<Peer>
) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    
    private val groupNameField = JTextField(20)
    private val peerListModel = DefaultListModel<Peer>()
    private val peerList = JList(peerListModel)
    
    val groupName: String
        get() = groupNameField.text.trim()
    
    val selectedPeers: List<Peer>
        get() = peerList.selectedValuesList.ifEmpty {
            // 不选择时默认只拉自己
            emptyList()
        }
    
    // 是否创建机器人
    var shouldCreateBot = false
        private set
    
    // 机器人名称
    var botName: String? = null
        private set
    
    private val botCheckBox = JCheckBox("同时创建群机器人")
    private val botNameField = JTextField(15)
    
    init {
        title = "创建群聊"
        init()
        setupUI()
    }
    
    private fun setupUI() {
        // 填充可选联系人
        availablePeers.forEach { peer ->
            peerListModel.addElement(peer)
        }
        
        // 设置列表渲染器
        peerList.cellRenderer = PeerListCellRenderer()
        
        // 机器人选项监听
        botCheckBox.addActionListener {
            botNameField.isEnabled = botCheckBox.isSelected
            if (botCheckBox.isSelected && botNameField.text.isEmpty()) {
                botNameField.text = "小助手"
            }
        }
        botNameField.isEnabled = false
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(450, 380)
            
            // 群名输入
            val namePanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("群名称:"), BorderLayout.WEST)
                add(groupNameField, BorderLayout.CENTER)
            }
            add(namePanel, BorderLayout.NORTH)
            
            // 成员选择
            val memberPanel = JPanel(BorderLayout(0, 8)).apply {
                add(JLabel("选择成员 (可多选，不选则只拉自己):").apply {
                    font = font.deriveFont(Font.BOLD)
                }, BorderLayout.NORTH)
                
                add(JScrollPane(peerList).apply {
                    preferredSize = Dimension(200, 150)
                }, BorderLayout.CENTER)
                
                // 提示信息
                add(JLabel("提示：按住Ctrl可多选，不选择成员时将创建只有自己的群").apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(11f)
                }, BorderLayout.SOUTH)
            }
            add(memberPanel, BorderLayout.CENTER)
            
            // 机器人选项
            val botPanel = JPanel(BorderLayout(0, 8)).apply {
                border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.GRAY),
                    "群机器人 (可选)"
                )
                
                add(botCheckBox, BorderLayout.NORTH)
                
                val botNamePanel = JPanel(BorderLayout(4, 0)).apply {
                    border = JBUI.Borders.emptyLeft(20)
                    add(JLabel("机器人名称:"), BorderLayout.WEST)
                    add(botNameField, BorderLayout.CENTER)
                }
                add(botNamePanel, BorderLayout.CENTER)
            }
            add(botPanel, BorderLayout.SOUTH)
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        if (groupName.isEmpty()) {
            return ValidationInfo("请输入群名称", groupNameField)
        }
        
        if (botCheckBox.isSelected && botNameField.text.trim().isEmpty()) {
            return ValidationInfo("请输入机器人名称", botNameField)
        }
        
        return null
    }
    
    override fun doOKAction() {
        shouldCreateBot = botCheckBox.isSelected
        botName = if (shouldCreateBot) botNameField.text.trim() else null
        super.doOKAction()
    }
    
    /**
     * 联系人列表单元格渲染器
     */
    private class PeerListCellRenderer : JPanel(BorderLayout()), ListCellRenderer<Peer> {
        private val nameLabel = JLabel()
        private val ipLabel = JLabel()
        
        init {
            isOpaque = true
            border = JBUI.Borders.empty(6, 10)
            
            nameLabel.font = Font("Microsoft YaHei", Font.PLAIN, 13)
            add(nameLabel, BorderLayout.NORTH)
            
            ipLabel.font = Font("Microsoft YaHei", Font.PLAIN, 10)
            ipLabel.foreground = JBColor.GRAY
            add(ipLabel, BorderLayout.SOUTH)
        }
        
        override fun getListCellRendererComponent(
            list: JList<out Peer>,
            peer: Peer,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): JPanel {
            nameLabel.text = peer.username
            ipLabel.text = "${peer.ipAddress}:${peer.port}"
            
            background = if (isSelected) {
                JBColor(Color(232, 240, 254), Color(60, 60, 80))
            } else {
                JBColor.PanelBackground
            }
            
            return this
        }
    }
}

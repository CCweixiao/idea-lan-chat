package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
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
    
    private val groupNameField = JTextField(20)
    private val peerListModel = DefaultListModel<Peer>()
    private val peerList = JList(peerListModel)
    
    val groupName: String
        get() = groupNameField.text.trim()
    
    val selectedPeers: List<Peer>
        get() = peerList.selectedValuesList
    
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
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(400, 300)
            
            // 群名输入
            val namePanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("群名称:"), BorderLayout.WEST)
                add(groupNameField, BorderLayout.CENTER)
            }
            add(namePanel, BorderLayout.NORTH)
            
            // 成员选择
            val memberPanel = JPanel(BorderLayout(0, 8)).apply {
                add(JLabel("选择成员:"), BorderLayout.NORTH)
                add(JScrollPane(peerList).apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.CENTER)
            }
            add(memberPanel, BorderLayout.CENTER)
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        if (groupName.isEmpty()) {
            return ValidationInfo("请输入群名称", groupNameField)
        }
        if (selectedPeers.isEmpty()) {
            return ValidationInfo("请选择至少一个成员", peerList)
        }
        return null
    }
}

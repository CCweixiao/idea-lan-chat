package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Group
import com.lanchat.network.Peer
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * 群组管理对话框
 */
class GroupManageDialog(
    private val project: Project,
    private val groupId: String
) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    private var group: Group? = service.getGroup(groupId)
    
    // 群名称
    private val groupNameField = JTextField(20)
    
    // 成员列表
    private val memberListModel = DefaultListModel<Peer>()
    private val memberList = JList(memberListModel)
    
    // 可添加的联系人列表
    private val availableContactsModel = DefaultListModel<Peer>()
    private val availableContactsList = JList(availableContactsModel)
    
    private var isOwner = false
    
    init {
        title = "群组管理"
        init()
        loadData()
    }
    
    private fun loadData() {
        group?.let { g ->
            groupNameField.text = g.name
            isOwner = service.isGroupOwner(groupId)
            
            // 加载群成员
            loadMembers()
            
            // 加载可添加的联系人
            loadAvailableContacts()
            
            // 如果不是群主，禁用编辑功能
            if (!isOwner) {
                groupNameField.isEnabled = false
            }
        }
    }
    
    private fun loadMembers() {
        memberListModel.clear()
        service.getGroupMembers(groupId).forEach { peer ->
            memberListModel.addElement(peer)
        }
    }
    
    private fun loadAvailableContacts() {
        availableContactsModel.clear()
        group?.let { g ->
            service.peers.value.values
                .filter { !g.memberIds.contains(it.id) }
                .forEach { peer ->
                    availableContactsModel.addElement(peer)
                }
        }
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(600, 450)
            
            // 顶部：群组信息
            val topPanel = JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("群名称:"), BorderLayout.WEST)
                add(groupNameField, BorderLayout.CENTER)
                
                // 权限提示
                if (!isOwner) {
                    add(JLabel("(仅群主可编辑)").apply {
                        foreground = JBColor.GRAY
                    }, BorderLayout.EAST)
                }
            }
            add(topPanel, BorderLayout.NORTH)
            
            // 中间：成员管理
            val centerPanel = JPanel(BorderLayout()).apply {
                // 左侧：当前成员
                val leftPanel = JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.emptyRight(8)
                    add(JLabel("群成员 (${memberListModel.size()}):").apply {
                        border = JBUI.Borders.emptyBottom(4)
                    }, BorderLayout.NORTH)
                    
                    val listScrollPane = JScrollPane(memberList).apply {
                        preferredSize = Dimension(250, 250)
                    }
                    add(listScrollPane, BorderLayout.CENTER)
                    
                    // 移除成员按钮（仅群主可见）
                    if (isOwner) {
                        val removeButton = JButton("移除成员", AllIcons.General.Remove).apply {
                            addActionListener {
                                removeSelectedMember()
                            }
                        }
                        add(removeButton, BorderLayout.SOUTH)
                    }
                }
                add(leftPanel, BorderLayout.WEST)
                
                // 右侧：可添加的联系人（仅群主可见）
                if (isOwner) {
                    val rightPanel = JPanel(BorderLayout()).apply {
                        border = JBUI.Borders.emptyLeft(8)
                        add(JLabel("可添加的联系人:").apply {
                            border = JBUI.Borders.emptyBottom(4)
                        }, BorderLayout.NORTH)
                        
                        val listScrollPane = JScrollPane(availableContactsList).apply {
                            preferredSize = Dimension(250, 250)
                        }
                        add(listScrollPane, BorderLayout.CENTER)
                        
                        val addButton = JButton("添加到群组", AllIcons.General.Add).apply {
                            addActionListener {
                                addSelectedMember()
                            }
                        }
                        add(addButton, BorderLayout.SOUTH)
                    }
                    add(rightPanel, BorderLayout.EAST)
                }
            }
            add(centerPanel, BorderLayout.CENTER)
            
            // 底部提示
            if (!isOwner) {
                add(JPanel(BorderLayout()).apply {
                    add(JLabel("提示：您不是群主，无法管理群成员").apply {
                        foreground = JBColor.GRAY
                        font = font.deriveFont(11f)
                    }, BorderLayout.WEST)
                }, BorderLayout.SOUTH)
            }
        }
    }
    
    private fun addSelectedMember() {
        val selectedPeer = availableContactsList.selectedValue ?: return
        
        if (service.addGroupMember(groupId, selectedPeer.id)) {
            loadMembers()
            loadAvailableContacts()
        }
    }
    
    private fun removeSelectedMember() {
        val selectedPeer = memberList.selectedValue ?: return
        
        if (service.removeGroupMember(groupId, selectedPeer.id)) {
            loadMembers()
            loadAvailableContacts()
        }
    }
    
    override fun createActions(): Array<Action> {
        return if (isOwner) {
            arrayOf(
                object : DialogWrapperAction("保存") {
                    override fun doAction(e: ActionEvent?) {
                        close(OK_EXIT_CODE)
                    }
                },
                cancelAction
            )
        } else {
            arrayOf(okAction)
        }
    }
}

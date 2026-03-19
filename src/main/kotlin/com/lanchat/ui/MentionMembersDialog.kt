package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.LanChatService
import com.lanchat.network.Group
import com.lanchat.network.Peer
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * @成员选择对话框
 */
class MentionMembersDialog(
    private val project: Project,
    private val groupId: String? = null
) : DialogWrapper(project) {
    
    private val service = LanChatService.getInstance()
    
    // 成员列表
    private val memberListModel = DefaultListModel<Peer>()
    private val memberList = JList(memberListModel)
    
    // @全体成员选项
    private val mentionAllCheckBox = JCheckBox("@全体成员")
    
    // 选中的成员ID列表
    val selectedMemberIds: List<String>
        get() = if (mentionAllCheckBox.isSelected) {
            emptyList()
        } else {
            memberList.selectedValuesList.map { it.id }
        }
    
    // 是否@全体成员
    val isMentionAll: Boolean
        get() = mentionAllCheckBox.isSelected
    
    init {
        title = "@成员"
        init()
        loadMembers()
    }
    
    private fun loadMembers() {
        memberListModel.clear()
        
        val members = if (groupId != null) {
            // 群聊：加载群成员
            service.getGroupMembers(groupId)
        } else {
            // 私聊：加载所有联系人
            service.peers.value.values.toList()
        }
        
        members.forEach { peer ->
            memberListModel.addElement(peer)
        }
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            border = JBUI.Borders.empty(16)
            preferredSize = Dimension(350, 400)
            
            // 顶部说明
            val topPanel = JPanel(BorderLayout()).apply {
                add(JLabel("选择要@的成员："), BorderLayout.WEST)
            }
            add(topPanel, BorderLayout.NORTH)
            
            // 中间：成员列表
            val listPanel = JPanel(BorderLayout()).apply {
                add(JScrollPane(memberList).apply {
                    preferredSize = Dimension(300, 250)
                }, BorderLayout.CENTER)
            }
            add(listPanel, BorderLayout.CENTER)
            
            // 底部：@全体成员选项
            val bottomPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyTop(8)
                
                mentionAllCheckBox.apply {
                    font = font.deriveFont(Font.BOLD)
                    addActionListener {
                        memberList.isEnabled = !isSelected
                        if (isSelected) {
                            memberList.clearSelection()
                        }
                    }
                }
                add(mentionAllCheckBox, BorderLayout.WEST)
                
                // 提示
                add(JLabel("提示：按住 Ctrl 可多选").apply {
                    foreground = JBColor.GRAY
                    font = font.deriveFont(11f)
                }, BorderLayout.SOUTH)
            }
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("确定") {
                override fun doAction(e: ActionEvent?) {
                    close(OK_EXIT_CODE)
                }
            },
            cancelAction
        )
    }
}

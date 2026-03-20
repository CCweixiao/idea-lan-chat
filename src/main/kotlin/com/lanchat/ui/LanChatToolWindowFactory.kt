package com.lanchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LanChatToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = LanChatMainPanel(project, toolWindow)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        val toggleAction = object : AnAction(
            "隐藏/显示联系人",
            "切换联系人列表的显示和隐藏",
            AllIcons.Actions.Collapseall
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                mainPanel.toggleContactList()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.icon = if (mainPanel.isContactHidden())
                    AllIcons.Actions.Expandall
                else
                    AllIcons.Actions.Collapseall
                e.presentation.text = if (mainPanel.isContactHidden())
                    "显示联系人"
                else
                    "隐藏联系人"
            }
        }
        toolWindow.setTitleActions(listOf(toggleAction))
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "LAN Chat"
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

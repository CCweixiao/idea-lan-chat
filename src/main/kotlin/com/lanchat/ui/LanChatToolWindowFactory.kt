package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * LAN Chat 工具窗口工厂
 */
class LanChatToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val lanChatPanel = LanChatMainPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(lanChatPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "LAN Chat"
    }
    
    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}

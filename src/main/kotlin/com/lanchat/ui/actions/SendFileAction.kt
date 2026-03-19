package com.lanchat.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.lanchat.LanChatService

/**
 * 发送文件操作
 */
class SendFileAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = LanChatService.getInstance()
        
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        val file = FileChooser.chooseFile(descriptor, project, null)
        
        file?.let {
            // 发送文件
            service.sendFileMessage("", it.path, it.name)
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

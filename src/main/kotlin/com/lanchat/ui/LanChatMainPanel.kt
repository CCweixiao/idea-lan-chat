package com.lanchat.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lanchat.ui.settings.LanChatSettings
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class LanChatMainPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    private lateinit var contactListPanel: ContactListPanel
    private lateinit var chatPanel: ChatPanel
    private lateinit var splitter: JSplitPane

    private var isContactListHidden = false
    private var lastDividerLocation = -1
    private var savedToolWindowType: ToolWindowType = ToolWindowType.DOCKED

    init {
        val settings = LanChatSettings.getInstance()
        settings.loadState(settings.state)
        setupUI()
    }

    private fun setupUI() {
        background = JBColor.PanelBackground

        chatPanel = ChatPanel(project)

        contactListPanel = ContactListPanel(project) { chatItem ->
            when (chatItem) {
                is ChatItem.PeerItem -> chatPanel.setCurrentPeer(chatItem.peer)
                is ChatItem.GroupItem -> chatPanel.setCurrentGroup(chatItem.group)
                null -> chatPanel.clearChat()
            }
        }

        splitter = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contactListPanel, chatPanel).apply {
            resizeWeight = 0.3
            dividerSize = 1
            isOneTouchExpandable = false
            isContinuousLayout = true
        }

        add(splitter, BorderLayout.CENTER)

        minimumSize = Dimension(180, 120)
        border = JBUI.Borders.empty()

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (!isContactListHidden && lastDividerLocation < 0) {
                    SwingUtilities.invokeLater {
                        splitter.dividerLocation = (width * 0.30).toInt().coerceAtLeast(160)
                    }
                }
            }
        })
    }

    fun toggleContactList() {
        if (isContactListHidden) {
            restoreFullView()
        } else {
            switchToCompactView()
        }
    }

    private fun switchToCompactView() {
        lastDividerLocation = splitter.dividerLocation
        savedToolWindowType = toolWindow.type

        splitter.leftComponent = null
        splitter.rightComponent = null
        removeAll()
        add(chatPanel, BorderLayout.CENTER)
        isContactListHidden = true
        revalidate()
        repaint()

        try {
            toolWindow.setType(ToolWindowType.FLOATING, null)
        } catch (_: Exception) {}

        // 等待浮动窗口创建完成后再调整大小和位置
        SwingUtilities.invokeLater {
            SwingUtilities.invokeLater {
                positionFloatingWindow()
            }
        }
    }

    private fun restoreFullView() {
        removeAll()
        splitter.leftComponent = contactListPanel
        splitter.rightComponent = chatPanel
        splitter.dividerSize = 1
        add(splitter, BorderLayout.CENTER)
        isContactListHidden = false
        revalidate()
        repaint()

        try {
            toolWindow.setType(savedToolWindowType, null)
        } catch (_: Exception) {}

        SwingUtilities.invokeLater {
            val loc = if (lastDividerLocation > 0) lastDividerLocation
                      else (width * 0.30).toInt().coerceAtLeast(160)
            splitter.dividerLocation = loc
        }
    }

    private fun positionFloatingWindow() {
        try {
            val ancestor = SwingUtilities.getWindowAncestor(this) ?: return
            val ideFrame = WindowManager.getInstance().getFrame(project) ?: return
            val frameBounds = ideFrame.bounds

            val compactWidth = 380
            val compactHeight = (frameBounds.height * 0.35).toInt().coerceIn(280, 500)

            ancestor.size = Dimension(compactWidth, compactHeight)
            ancestor.setLocation(
                frameBounds.x + frameBounds.width - compactWidth - 16,
                frameBounds.y + frameBounds.height - compactHeight - 48
            )
        } catch (_: Exception) {
            // 某些 IDE 布局下可能不支持，静默忽略
        }
    }

    fun isContactHidden(): Boolean = isContactListHidden
}

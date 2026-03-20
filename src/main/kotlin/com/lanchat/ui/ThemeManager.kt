package com.lanchat.ui

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * 主题管理器 - 自动适配 IDEA 主题，暗色模式使用低调配色
 *
 * 当用户选择"暗色"主题时，消息气泡等使用低调颜色；
 * 当选择"亮色"时，使用鲜明的绿色配色。
 * 背景色始终跟随 IDEA 原生主题。
 */
object ThemeManager {

    enum class Theme(val displayName: String) {
        LIGHT("亮色"),
        DARK("暗色")
    }

    private var currentTheme: Theme = Theme.LIGHT

    fun setTheme(theme: Theme) {
        currentTheme = theme
    }

    fun getTheme(): Theme = currentTheme

    fun isDark(): Boolean = currentTheme == Theme.DARK

    // =============== 通用颜色（跟随 IDEA 原生主题）===============

    /** 面板背景色 — 直接用 IDEA 原生 */
    val panelBackground: Color get() = JBColor.PanelBackground

    /** 联系人列表背景色 */
    val listBackground: Color get() = JBColor.PanelBackground

    /** 列表项悬停 */
    val itemHover: Color get() = JBColor(Color(218, 218, 218), Color(55, 55, 55))

    /** 列表项选中 */
    val itemSelected: Color get() = JBColor(Color(196, 196, 196), Color(60, 60, 65))

    /** 输入框背景 */
    val inputBackground: Color get() = JBColor(Color(255, 255, 255), Color(49, 49, 49))

    // =============== 消息气泡 ===============

    /** 我发送的消息气泡（与对方一致） */
    val sentBubbleColor: Color get() = receivedBubbleColor

    /** 对方发送的消息气泡 */
    val receivedBubbleColor: Color get() = if (isDark())
        JBColor(Color(230, 230, 230), Color(55, 55, 55))
    else
        JBColor(Color(255, 255, 255), Color(58, 58, 58))

    /** 消息文字颜色 */
    val messageTextColor: Color get() = JBColor(Color(30, 30, 30), Color(200, 200, 200))

    /** 时间戳文字颜色 */
    val timestampColor: Color get() = JBColor(Color(170, 170, 170), Color(100, 100, 100))

    /** 发送消息时间戳颜色 */
    val sentTimestampColor: Color get() = if (isDark())
        JBColor(Color(120, 140, 120), Color(90, 110, 95))
    else
        JBColor(Color(80, 120, 60), Color(100, 150, 80))

    /** 已读状态文字颜色 */
    val readStatusColor: Color get() = JBColor(Color(100, 149, 237), Color(120, 160, 220))

    // =============== 状态指示 ===============

    /** 在线状态 */
    val onlineColor: Color get() = JBColor(Color(76, 175, 80), Color(80, 160, 90))

    /** 离线状态 */
    val offlineColor: Color get() = JBColor(Color(190, 190, 190), Color(100, 100, 100))

    // =============== 按钮颜色 ===============

    /** 主按钮背景 */
    val primaryButtonColor: Color get() = if (isDark())
        JBColor(Color(180, 200, 180), Color(60, 80, 70))
    else
        JBColor(Color(7, 193, 96), Color(7, 193, 96))

    /** 主按钮悬停 */
    val primaryButtonHoverColor: Color get() = if (isDark())
        JBColor(Color(160, 180, 160), Color(70, 95, 80))
    else
        JBColor(Color(6, 173, 86), Color(6, 173, 86))

    /** 主按钮文字 */
    val primaryButtonText: Color get() = if (isDark())
        JBColor(Color(40, 60, 40), Color(180, 200, 190))
    else
        JBColor(Color.WHITE, Color.WHITE)

    /** 危险按钮文字 */
    val dangerTextColor: Color get() = JBColor(Color(220, 50, 50), Color(200, 100, 100))

    // =============== 发送按钮（低调风格）===============

    val sendButtonColor: Color get() = JBColor(Color(228, 228, 228), Color(62, 62, 62))

    val sendButtonHoverColor: Color get() = JBColor(Color(210, 210, 210), Color(75, 75, 75))

    val sendButtonPressedColor: Color get() = JBColor(Color(195, 195, 195), Color(85, 85, 85))

    val sendButtonText: Color get() = JBColor(Color(80, 80, 80), Color(180, 180, 180))

    // =============== 强调色 ===============

    /** @提及文字 */
    val mentionColor: Color get() = JBColor(Color(33, 150, 243), Color(100, 180, 255))

    /** 群组图标 */
    val groupIconColor: Color get() = JBColor(Color(87, 137, 213), Color(70, 120, 190))

    // =============== 未读徽章 ===============

    /** 未读徽章背景 */
    val badgeColor: Color get() = JBColor(Color(250, 81, 81), Color(180, 70, 70))

    // =============== 头像颜色 ===============

    val avatarColors: List<Color> get() = if (isDark())
        listOf(
            JBColor(Color(76, 175, 80), Color(60, 100, 70)),
            JBColor(Color(33, 150, 243), Color(70, 90, 130)),
            JBColor(Color(156, 39, 176), Color(120, 70, 120)),
            JBColor(Color(255, 152, 0), Color(130, 90, 50)),
            JBColor(Color(233, 30, 99), Color(120, 50, 70)),
            JBColor(Color(0, 150, 136), Color(50, 110, 100))
        )
    else
        listOf(Color(76, 175, 80), Color(33, 150, 243), Color(156, 39, 176),
            Color(255, 152, 0), Color(233, 30, 99), Color(0, 150, 136))

    // =============== 文字颜色 ===============

    val primaryTextColor: Color get() = JBColor(Color(30, 30, 30), Color(200, 200, 200))

    val secondaryTextColor: Color get() = JBColor(Color(120, 120, 120), Color(130, 130, 130))

    val tertiaryTextColor: Color get() = JBColor(Color(160, 160, 160), Color(100, 100, 100))

    // =============== 分割线 ===============

    val borderColor: Color get() = JBColor(Color(220, 220, 220), Color(60, 60, 60))

    // =============== 搜索框 ===============

    val searchBorderColor: Color get() = JBColor(Color(210, 210, 210), Color(70, 70, 70))

    // =============== 系统消息 ===============

    val systemMessageColor: Color get() = JBColor(Color(140, 140, 140), Color(120, 120, 120))

    /** 发送者名称（群聊） */
    val senderNameColor: Color get() = JBColor(Color(120, 120, 120), Color(140, 140, 140))

    /** Bot名称 */
    val botNameColor: Color get() = JBColor(Color(100, 100, 200), Color(120, 120, 170))
}

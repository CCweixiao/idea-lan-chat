package com.lanchat.ui

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * 主题管理器 - 支持亮色/暗色主题切换
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

    // =============== 通用颜色 ===============

    /** 面板背景色 */
    val panelBackground: Color get() = if (isDark()) Color(43, 43, 43) else Color(240, 240, 240)

    /** 联系人列表背景色 */
    val listBackground: Color get() = if (isDark()) Color(43, 43, 43) else Color(240, 240, 240)

    /** 列表项悬停 */
    val itemHover: Color get() = if (isDark()) Color(55, 55, 55) else Color(218, 218, 218)

    /** 列表项选中 */
    val itemSelected: Color get() = if (isDark()) Color(60, 60, 65) else Color(196, 196, 196)

    /** 输入框背景 */
    val inputBackground: Color get() = if (isDark()) Color(50, 50, 50) else Color(255, 255, 255)

    // =============== 消息气泡 ===============

    /** 我发送的消息气泡 */
    val sentBubbleColor: Color get() = if (isDark()) Color(60, 70, 65) else Color(149, 236, 105)

    /** 对方发送的消息气泡 */
    val receivedBubbleColor: Color get() = if (isDark()) Color(55, 55, 55) else Color(255, 255, 255)

    /** 消息文字颜色 */
    val messageTextColor: Color get() = if (isDark()) Color(200, 200, 200) else Color(30, 30, 30)

    /** 时间戳文字颜色 */
    val timestampColor: Color get() = if (isDark()) Color(100, 100, 100) else Color(170, 170, 170)

    /** 发送消息时间戳颜色 */
    val sentTimestampColor: Color get() = if (isDark()) Color(90, 110, 95) else Color(80, 120, 60)

    /** 已读状态文字颜色 */
    val readStatusColor: Color get() = if (isDark()) Color(90, 120, 140) else Color(100, 149, 237)

    // =============== 状态指示 ===============

    /** 在线状态 */
    val onlineColor: Color get() = if (isDark()) Color(80, 160, 90) else Color(76, 175, 80)

    /** 离线状态 */
    val offlineColor: Color get() = if (isDark()) Color(100, 100, 100) else Color(190, 190, 190)

    // =============== 按钮颜色 ===============

    /** 主按钮背景 */
    val primaryButtonColor: Color get() = if (isDark()) Color(60, 80, 70) else Color(7, 193, 96)

    /** 主按钮悬停 */
    val primaryButtonHoverColor: Color get() = if (isDark()) Color(70, 95, 80) else Color(6, 173, 86)

    /** 主按钮文字 */
    val primaryButtonText: Color get() = if (isDark()) Color(180, 200, 190) else Color.WHITE

    /** 危险按钮文字 */
    val dangerTextColor: Color get() = if (isDark()) Color(200, 100, 100) else Color(220, 50, 50)

    // =============== 强调色 ===============

    /** @提及文字 */
    val mentionColor: Color get() = if (isDark()) Color(80, 140, 200) else Color(33, 150, 243)

    /** 群组图标 */
    val groupIconColor: Color get() = if (isDark()) Color(70, 100, 140) else Color(87, 137, 213)

    // =============== 未读徽章 ===============

    /** 未读徽章背景 */
    val badgeColor: Color get() = if (isDark()) Color(180, 70, 70) else Color(250, 81, 81)

    // =============== 头像颜色 ===============

    val avatarColors: List<Color> get() = if (isDark())
        listOf(Color(60, 100, 70), Color(70, 90, 130), Color(120, 70, 120), Color(130, 90, 50), Color(120, 50, 70), Color(50, 110, 100))
    else
        listOf(Color(76, 175, 80), Color(33, 150, 243), Color(156, 39, 176), Color(255, 152, 0), Color(233, 30, 99), Color(0, 150, 136))

    // =============== 文字颜色 ===============

    val primaryTextColor: Color get() = if (isDark()) Color(200, 200, 200) else Color(30, 30, 30)

    val secondaryTextColor: Color get() = if (isDark()) Color(130, 130, 130) else Color(120, 120, 120)

    val tertiaryTextColor: Color get() = if (isDark()) Color(100, 100, 100) else Color(160, 160, 160)

    // =============== 分割线 ===============

    val borderColor: Color get() = if (isDark()) Color(60, 60, 60) else Color(220, 220, 220)

    // =============== 搜索框 ===============

    val searchBorderColor: Color get() = if (isDark()) Color(70, 70, 70) else Color(210, 210, 210)

    // =============== 系统消息 ===============

    val systemMessageColor: Color get() = if (isDark()) Color(120, 120, 120) else Color(140, 140, 140)

    /** 发送者名称（群聊） */
    val senderNameColor: Color get() = if (isDark()) Color(140, 140, 140) else Color(120, 120, 120)

    /** Bot名称 */
    val botNameColor: Color get() = if (isDark()) Color(120, 120, 170) else Color(100, 100, 200)
}

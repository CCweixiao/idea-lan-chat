package com.lanchat.ui.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.lanchat.ui.ThemeManager

/**
 * LAN Chat 持久化设置
 */
@State(name = "LanChatSettings", storages = [Storage("lanchat.xml")])
class LanChatSettings : PersistentStateComponent<LanChatSettings.State> {

    data class State(
        var username: String = "",
        var theme: String = ThemeManager.Theme.LIGHT.name,
        var udpPort: Int = 8888,
        var tcpPort: Int = 8889
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        // 恢复主题
        try {
            val theme = ThemeManager.Theme.valueOf(state.theme)
            ThemeManager.setTheme(theme)
        } catch (_: Exception) {}
    }

    fun getUsername(): String = state.username
    fun setUsername(name: String) { state.username = name }

    fun getTheme(): String = state.theme
    fun setTheme(theme: String) { state.theme = theme }

    fun getUdpPort(): Int = state.udpPort
    fun setUdpPort(port: Int) { state.udpPort = port }

    fun getTcpPort(): Int = state.tcpPort
    fun setTcpPort(port: Int) { state.tcpPort = port }
}

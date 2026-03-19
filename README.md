# IDEA LAN Chat

<p align="center">
  <img src="src/main/resources/META-INF/icons/chat.svg" alt="Logo" width="120" height="120">
  <h3 align="center">IntelliJ IDEA 局域网聊天插件</h3>
  <p align="center">
    一个专为开发者设计的局域网聊天插件，支持同事之间的即时通讯，让你在编码时也能轻松摸鱼聊天！
  </p>
</p>

<p align="center">
  <a href="#功能特性">功能特性</a> •
  <a href="#安装">安装</a> •
  <a href="#使用说明">使用说明</a> •
  <a href="#技术架构">技术架构</a> •
  <a href="#贡献">贡献</a>
</p>

---

## 功能特性

- ✅ **局域网自动发现** - 自动发现局域网内使用该插件的所有同事
- ✅ **即时消息发送** - 支持实时文本消息发送与接收
- ✅ **图片发送与预览** - 支持图片发送，并在聊天窗口直接预览
- ✅ **文件传输** - 支持任意文件传输
- ✅ **联系人列表** - 实时显示在线同事列表
- ✅ **创建群聊** - 支持创建群组聊天
- ✅ **优雅的聊天界面** - 仿微信/Slack 风格的现代化 UI

## 截图

> 开发中，即将推出...

## 安装

### 方法一：从 JetBrains Marketplace 安装（推荐）

> 即将发布到 JetBrains Marketplace

### 方法二：手动安装

1. 从 [Releases](https://github.com/jielongping/idea-lan-chat/releases) 页面下载最新的 `.zip` 文件
2. 打开 IntelliJ IDEA，进入 `Settings/Preferences` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. 选择下载的 `.zip` 文件并重启 IDE

### 方法三：从源码构建

```bash
# 克隆项目
git clone https://github.com/jielongping/idea-lan-chat.git
cd idea-lan-chat

# 构建
./gradlew buildPlugin

# 生成的插件位于 build/distributions/idea-lan-chat-*.zip
```

## 使用说明

### 首次使用

1. 安装插件后，重启 IntelliJ IDEA
2. 右侧边栏会出现 "LAN Chat" 工具窗口
3. 点击打开，插件会自动发现局域网内其他用户
4. 在 `Settings` → `Tools` → `LAN Chat` 中设置你的用户名

### 发送消息

1. 在联系人列表中选择聊天对象
2. 在底部输入框输入消息
3. 按 `Enter` 发送，`Ctrl+Enter` 换行

### 发送图片/文件

- 点击输入框上方的图片/文件图标选择要发送的内容
- 或直接拖拽文件到聊天窗口

### 创建群聊

1. 点击联系人列表上方的 `+` 按钮
2. 输入群名称
3. 选择要邀请的成员
4. 点击创建

## 技术架构

### 技术栈

- **语言**: Kotlin 1.9.21
- **框架**: IntelliJ Platform Plugin SDK
- **网络**: UDP 广播 + TCP 点对点通信
- **异步**: Kotlin Coroutines
- **序列化**: Gson

### 项目结构

```
idea-lan-chat/
├── src/main/
│   ├── kotlin/com/lanchat/
│   │   ├── LanChatService.kt           # 核心服务
│   │   ├── network/
│   │   │   ├── NetworkManager.kt       # 网络管理
│   │   │   └── Peer.kt                 # 用户节点
│   │   ├── message/
│   │   │   ├── Message.kt              # 消息实体
│   │   │   └── DiscoveryMessage.kt     # 发现消息
│   │   ├── ui/
│   │   │   ├── LanChatMainPanel.kt     # 主面板
│   │   │   ├── ContactListPanel.kt     # 联系人列表
│   │   │   ├── ChatPanel.kt            # 聊天面板
│   │   │   ├── CreateGroupDialog.kt    # 创建群聊对话框
│   │   │   └── settings/
│   │   │       └── LanChatSettingsConfigurable.kt
│   │   └── util/
│   └── resources/
│       └── META-INF/
│           ├── plugin.xml              # 插件配置
│           └── icons/
└── build.gradle.kts                    # Gradle 构建配置
```

### 通信协议

#### 用户发现 (UDP 广播)

```json
{
  "userId": "uuid",
  "username": "张三",
  "tcpPort": 8889,
  "timestamp": 1703275200000
}
```

#### 消息传输 (TCP)

```json
{
  "id": "message-uuid",
  "type": "TEXT",
  "senderId": "sender-uuid",
  "receiverId": "receiver-uuid",
  "content": "消息内容",
  "timestamp": 1703275200000
}
```

## 开发路线

- [x] 基础项目结构
- [x] UI 框架搭建
- [x] 网络通信模块
- [ ] 完整的消息功能
- [ ] 图片发送与预览
- [ ] 文件传输
- [ ] 群聊功能
- [ ] 消息历史持久化
- [ ] 表情支持
- [ ] 代码片段高亮
- [ ] 语音/视频通话（远期）

## 贡献

欢迎贡献代码！请查看 [Contributing Guide](CONTRIBUTING.md) 了解详情。

### 开发环境

- JDK 17+
- IntelliJ IDEA 2022.3+
- Gradle 8.5+

### 本地开发

```bash
# 克隆项目
git clone https://github.com/jielongping/idea-lan-chat.git
cd idea-lan-chat

# 运行开发实例
./gradlew runIde
```

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

## 联系方式

- 作者: Jie Longping
- Email: jielongping@example.com
- GitHub: [@jielongping](https://github.com/jielongping)

---

<p align="center">
  Made with ❤️ by developers, for developers
</p>

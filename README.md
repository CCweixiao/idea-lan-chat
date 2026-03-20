# LAN Chat

<p align="center">
  <img src="src/main/resources/META-INF/icons/avatar.svg" alt="Logo" width="120" height="120">
  <h3 align="center">IntelliJ IDEA 局域网聊天插件</h3>
  <p align="center">
    纯局域网即时通讯，无需服务器，无需注册。<br>
    UDP 广播发现 + TCP 点对点通信，端到端加密。
  </p>
</p>

<p align="center">
  <a href="#功能特性">功能特性</a> •
  <a href="#安装">安装</a> •
  <a href="#使用说明">使用说明</a> •
  <a href="#技术架构">技术架构</a> •
  <a href="#开发">开发</a>
</p>

---

## 功能特性

### 💬 即时通讯
- **文本消息** — 实时发送与接收，Enter 发送，Ctrl+Enter 换行
- **图片发送与预览** — 支持图片发送，聊天窗口内直接预览
- **文件传输** — 支持任意文件传输
- **@提及** — 群聊中 @成员并通知

### 👥 群聊
- **创建群聊** — 选择好友创建群组
- **群管理** — 群主可管理成员（添加/移除/禁言/屏蔽）
- **退群** — 成员可主动退群并广播通知
- **入群申请** — 非群主可通过申请加入群聊
- **已读回执** — 群消息广播已读状态，显示谁已阅读

### 🔗 好友管理
- **IP 添加** — 通过 IP 探测在线用户，发送好友申请
- **好友申请** — 收到申请后可通过或拒绝
- **申请记录** — 查看所有好友/群聊申请历史

### 🔐 安全与隐私
- **AES-128-GCM 加密** — 端到端加密通信
- **MAC 地址用户 ID** — 基于硬件生成唯一身份，换 IP 不丢失身份
- **纯局域网** — 数据不经过任何服务器

### 🔄 自动维护
- **UDP 心跳** — 每 5 秒广播心跳，自动更新好友 IP
- **离线检测** — 90 秒无心跳自动标记离线
- **IP 变化自适应** — 换 IP 后自动通知好友，无需重新添加

### 🎨 界面
- **亮色/暗色主题** — 自动适配 IDEA 主题，也可手动切换
- **迷你聊天模式** — 最小化到小条，双击恢复
- **未读消息角标** — 联系人列表显示未读数（群聊每用户独立计数）
- **个人签名** — 设置个性签名

### 💾 数据管理
- **SQLite 持久化** — 消息、好友、群聊本地存储
- **存储空间查看** — 查看数据库大小和消息统计
- **清除聊天记录** — 二次确认后清除

### 🤖 机器人
- **创建 Bot** — 在群聊中创建自动回复机器人

---

## 安装

### 下载安装

1. 从 [Releases](https://github.com/CCweixiao/idea-lan-chat/releases) 下载 `LAN-Chat-1.0.0.zip`
2. 打开 IntelliJ IDEA → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. 选择下载的 zip 文件
4. **重启 IDE**

### 兼容性

- IntelliJ IDEA 2022.3 ~ 2025.3.*
- JDK 17+

---

## 使用说明

### 首次启动

1. 安装后重启 IDE，右侧边栏出现 **LAN Chat** 工具窗口
2. 插件自动基于 MAC 地址生成唯一用户 ID
3. 在 `Settings` → `Tools` → `LAN Chat` 中设置用户名
4. 插件自动通过 UDP 广播发现局域网内其他用户

### 添加好友

1. 点击联系人列表上方的 **+** 按钮
2. 切换到 **添加联系人** tab
3. 输入对方 IP 和端口，点击 **搜索用户**
4. 确认找到用户后，输入验证消息，点击 **发送好友申请**
5. 对方在 **好友申请** tab 中点击 **通过** 即可

### 发送消息

- 选择联系人或群聊，在底部输入框输入消息
- `Enter` 发送，`Ctrl+Enter` 换行
- 点击输入框上方的图片/文件图标发送附件

### 创建群聊

1. 点击联系人列表上方的 **+** 按钮
2. 切换到 **创建群聊**
3. 输入群名称，勾选要邀请的好友
4. 点击创建

### 个人设置

- 点击左上角头像打开 **个人资料** 对话框
- 修改用户名、签名
- 查看/复制自己的用户 ID 和群号

### 端口配置

- 在 `Settings` → `Tools` → `LAN Chat` 中可自定义 UDP/TCP 端口
- 默认 UDP: 8888，TCP: 8889

---

## 技术架构

### 技术栈

| 技术 | 说明 |
|------|------|
| Kotlin | 主要开发语言 |
| IntelliJ Platform Plugin SDK | 插件框架 |
| UDP 广播 | 局域网用户发现 + 心跳 |
| TCP | 点对点消息传输 |
| SQLite (JDBC) | 本地数据持久化 |
| Gson | JSON 序列化 |
| Kotlin Coroutines | 异步并发 |
| AES-128-GCM | 端到端加密 |

### 通信协议

#### 心跳广播 (UDP, 每 5 秒)

```json
{
  "userId": "基于MAC地址生成的唯一ID",
  "username": "张三",
  "tcpPort": 8889,
  "avatar": null,
  "timestamp": 1703275200000
}
```

#### 消息传输 (TCP)

```json
{
  "id": "message-uuid",
  "type": "TEXT",
  "senderId": "sender-userId",
  "receiverId": "receiver-userId",
  "content": "消息内容",
  "timestamp": 1703275200000
}
```

#### 消息类型

| type | 说明 |
|------|------|
| `TEXT` | 文本消息 |
| `IMAGE` | 图片消息 |
| `FILE` | 文件消息 |
| `FRIEND_REQUEST` | 好友申请 |
| `FRIEND_ACCEPT` | 接受好友 |
| `FRIEND_REJECT` | 拒绝好友 |
| `GROUP_SYNC` | 群聊同步（创建/删除/成员变更） |
| `GROUP_INVITE` | 群聊邀请 |
| `PROBE` | 探测请求 |
| `PROBE_RESPONSE` | 探测响应 |
| `MESSAGE_READ_ACK` | 已读回执 |
| `MENTION` | @提及 |

### 项目结构

```
src/main/kotlin/com/lanchat/
├── LanChatService.kt              # 核心服务（消息收发、好友管理、群管理）
├── db/DatabaseManager.kt          # 数据库操作（SQLite）
├── network/
│   ├── NetworkManager.kt          # UDP 广播 + TCP 服务端/客户端
│   ├── Peer.kt                    # 用户节点数据类
│   ├── Group.kt                   # 群组数据类
│   ├── FriendRequest.kt           # 好友申请
│   ├── GroupRequest.kt            # 群聊申请
│   └── Bot.kt                     # 机器人
├── message/
│   ├── Message.kt                 # 消息实体
│   └── DiscoveryMessage.kt        # 发现消息
├── ui/
│   ├── LanChatMainPanel.kt        # 主面板（联系人和聊天区域）
│   ├── ContactListPanel.kt        # 联系人列表
│   ├── ChatPanel.kt               # 聊天面板
│   ├── AddContactDialog.kt        # 添加联系人/好友申请
│   ├── CreateGroupDialog.kt       # 创建群聊
│   ├── GroupManageDialog.kt       # 群管理
│   ├── ProfileDialog.kt           # 个人资料
│   ├── StorageManagerDialog.kt    # 存储管理
│   ├── DiscoverPeersDialog.kt     # 发现附近用户
│   ├── ThemeManager.kt            # 主题管理
│   └── settings/                  # 设置页面
└── util/
    ├── UserIdGenerator.kt         # MAC 地址生成用户 ID
    └── CryptoManager.kt           # AES-128-GCM 加密
```

---

## 开发

### 环境要求

- JDK 17+
- IntelliJ IDEA 2022.3+

### 从源码构建

```bash
git clone https://github.com/CCweixiao/idea-lan-chat.git
cd idea-lan-chat
./gradlew buildPlugin
```

构建产物位于 `build/distributions/LAN Chat-*.zip`。

### 运行开发实例

```bash
./gradlew runIde
```

### 运行测试

```bash
./gradlew test
```

---

## 许可证

[MIT License](LICENSE)

---

<p align="center">
  Made with ❤️ for developers
</p>

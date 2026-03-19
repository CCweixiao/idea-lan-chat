# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IDEA LAN Chat is an IntelliJ IDEA plugin for LAN-based instant messaging. It enables developers to chat with colleagues on the same local network without leaving the IDE.

## Build and Development Commands

```bash
# Build the plugin
./gradlew buildPlugin

# Run development IDE instance
./gradlew runIde

# Run tests
./gradlew test

# Run a specific test
./gradlew test --tests "ClassName"
```

The built plugin zip file is located at `build/distributions/idea-lan-chat-*.zip`.

## Architecture

### Core Services

- **LanChatService** (`src/main/kotlin/com/lanchat/LanChatService.kt`): Application-level service that manages peers, messages, groups, bots, and user settings. Uses Kotlin StateFlow for reactive state management and persists data to SQLite via `DatabaseManager`.

- **NetworkManager** (`src/main/kotlin/com/lanchat/network/NetworkManager.kt`): Handles network communication using UDP broadcast (port 8888) for peer discovery and TCP (port 8889) for message delivery. Uses Kotlin coroutines and emits events via SharedFlow.

- **DatabaseManager** (`src/main/kotlin/com/lanchat/db/DatabaseManager.kt`): SQLite-based persistence layer (object singleton). Stores peers, messages, groups, bots, and settings in `~/.lanchat/lanchat.db`.

### Key Data Models

- **Peer**: Represents a user with id, username, ipAddress, port, avatar, isOnline status
- **Message**: Supports TEXT, IMAGE, FILE, SYSTEM, TYPING, READ, GROUP_CHAT, MENTION_MEMBER, MENTION_ALL types
- **Group**: Owner-managed group with member list (only owner can add/remove members)
- **Bot**: Test bot with auto-reply functionality

### UI Structure

- **LanChatToolWindowFactory**: Creates the tool window registered in plugin.xml
- **LanChatMainPanel**: Main container with contact list (left) and chat panel (right)
- **ContactListPanel**: Displays peers, groups, and bots; handles selection
- **ChatPanel**: Message display and input area
- Dialogs: CreateGroupDialog, AddContactDialog, CreateBotDialog, ProfileDialog

### Communication Protocol

Discovery (UDP broadcast to 255.255.255.255:8888):
```json
{"userId": "uuid", "username": "name", "tcpPort": 8889, "timestamp": 123456}
```

Message (TCP to peer:8889):
```json
{"id": "uuid", "type": "TEXT", "senderId": "uuid", "receiverId": "uuid", "content": "...", "timestamp": 123456}
```

## Initialization Order

IMPORTANT: StateFlow properties in `LanChatService` must be initialized before the `init` block runs. The current implementation initializes all StateFlow properties as empty collections before calling `initialize()` in the init block.

## Testing

Unit tests are in `src/test/kotlin/com/lanchat/`. Test files cover:
- Database operations (`DatabaseManagerTest.kt`)
- Message handling (`MessageTest.kt`)
- Group management (`GroupTest.kt`)
- Bot functionality (`BotTest.kt`)

## Platform Configuration

Plugin metadata in `src/main/resources/META-INF/plugin.xml`:
- Tool window anchored to right side
- Application service registration
- Settings configurable under Tools → LAN Chat
- Custom action for sending files

Version compatibility configured in `gradle.properties`.

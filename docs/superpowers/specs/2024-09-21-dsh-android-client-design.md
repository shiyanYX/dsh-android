# DSH Android Remote Client - Design Specification

**Version:** 1.0.0  
**Date:** 2024-09-21  
**Status:** Draft  

---

## 1. Executive Summary

DSH Android Remote Client is a native Android application that connects to remote DeepSeek Harness (DSH) servers. The app provides a mobile-first experience for interacting with DSH's AI coding agent, supporting real-time conversations, tool call management, and session management.

### 1.1 Goals

- Provide native Android experience for DSH remote access
- Support domain + port connection with username/password authentication
- Enable real-time conversations with streaming responses
- Support tool call confirmation workflow
- Maintain feature parity with DSH Web client (file management deferred)
- Design for extensibility to support future plugin compatibility

### 1.2 Non-Goals

- File management (deferred to v1.2)
- UI plugin rendering (considered for v2.0)
- iOS support (future consideration)

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Android App                           │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                             │
│  ├── ConnectionScreen                                   │
│  ├── SessionListScreen                                  │
│  ├── ChatScreen                                         │
│  └── SettingsScreen                                     │
├─────────────────────────────────────────────────────────┤
│  Domain Layer                                           │
│  ├── UseCases                                           │
│  └── Repository                                         │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                             │
│  ├── DshRepository                                      │
│  ├── AuthManager                                        │
│  └── LocalStorage                                       │
├─────────────────────────────────────────────────────────┤
│  Network Layer                                          │
│  ├── DshApiClient (HTTP)                                │
│  ├── DshWebSocketClient (WebSocket)                     │
│  └── AuthInterceptor                                    │
└─────────────────────────────────────────────────────────┘
         │
         │ HTTP + WebSocket
         ▼
┌─────────────────────────────────────────────────────────┐
│                   DSH Server                             │
├─────────────────────────────────────────────────────────┤
│  认证层                                                  │
│  └── /dsh-webui-auth/login                              │
├─────────────────────────────────────────────────────────┤
│  API 层                                                  │
│  ├── /api/sessions (会话管理)                            │
│  ├── /api/chat (对话)                                    │
│  ├── /api/tools (工具列表)                               │
│  └── /api/models (模型列表)                              │
├─────────────────────────────────────────────────────────┤
│  WebSocket 层                                           │
│  └── /api/remote.mux (实时通信)                          │
├─────────────────────────────────────────────────────────┤
│  插件层                                                  │
│  ├── 核心工具                                            │
│  ├── MCP 服务                                            │
│  └── Skills                                              │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Module Structure

```
dsh-android/
├── core/                           # 核心模块
│   ├── protocol/                   # 协议抽象层
│   │   ├── DshProtocol.kt
│   │   ├── impl/
│   │   └── model/
│   ├── network/                    # 网络层
│   │   ├── HttpClient.kt
│   │   ├── WebSocketClient.kt
│   │   └── interceptor/
│   └── auth/                       # 认证模块
│       ├── AuthManager.kt
│       └── AuthProvider.kt
├── feature/                        # 功能模块
│   ├── connection/                 # 连接功能
│   ├── sessions/                   # 会话管理
│   ├── chat/                       # 聊天功能
│   ├── toolcall/                   # 工具调用
│   ├── settings/                   # 设置
│   └── favorites/                  # 收藏
├── plugin/                         # 插件系统（预留）
│   ├── PluginManager.kt
│   └── Plugin接口.kt
├── ui/                             # UI 组件
│   ├── theme/
│   ├── components/
│   └── navigation/
└── app/                            # 应用入口
    └── DshApplication.kt
```

---

## 3. Feature Specification

### 3.1 Connection & Authentication

#### 3.1.1 Server Connection Screen

**Purpose:** Allow users to connect to a DSH server using domain + port and credentials.

**UI Elements:**
- Server address input (e.g., `https://dsh.example.com:3080`)
- Username input
- Password input
- "Remember password" checkbox
- "Connect" button
- Connection status indicator

**Behavior:**
1. User enters server address, username, and password
2. App validates input format
3. App attempts to authenticate via `POST /dsh-webui-auth/login`
4. On success: store session token, navigate to session list
5. On failure: display error message

**API Call:**
```http
POST /dsh-webui-auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "securepassword"
}
```

**Response:**
```json
{
  "ok": true,
  "redirect": "/?token=xxx"
}
```

**Storage:**
- Server address: Encrypted SharedPreferences
- Session token: Encrypted SharedPreferences (HttpOnly cookie simulation)
- Credentials: Optional (if "remember password" is checked)

#### 3.1.2 Session Management

**Purpose:** Allow users to manage their DSH sessions (conversations).

**Features:**
- List all sessions
- Create new session
- Delete session
- Search sessions
- Display session metadata (title, last message, timestamp, model)

**API Calls:**
```http
# List sessions
GET /api/sessions

# Create session
POST /api/sessions
{
  "title": "New Session"
}

# Delete session
DELETE /api/sessions/{sessionId}

# Search sessions
GET /api/sessions/search?q={query}
```

---

### 3.2 Real-time Conversation

#### 3.2.1 Chat Screen

**Purpose:** Provide immersive conversation experience with AI agent.

**UI Elements:**
- Title bar with session title and menu
- Scrollable message list
- Input area with toolbar
- Agent status indicator

**Message Types:**
- User messages
- Assistant messages (with Markdown rendering)
- Tool call cards
- System messages

**Behavior:**
1. User types message and sends
2. Message appears in chat immediately (optimistic update)
3. WebSocket connection streams AI response
4. Tool calls appear as cards with approve/reject buttons
5. Agent status updates in real-time

#### 3.2.2 WebSocket Communication

**Connection:**
```kotlin
// WebSocket endpoint
ws://{server}/api/remote.mux

// Authentication via cookie
Cookie: dsh_wua_session={sessionToken}
```

**Message Protocol:**
```json
// User message
{
  "type": "user/message",
  "sessionId": "xxx",
  "content": "Hello, help me debug this code"
}

// Assistant response (streaming)
{
  "type": "assistant/message",
  "sessionId": "xxx",
  "content": "I'll help you debug...",
  "streaming": true
}

// Tool call request
{
  "type": "tool/call",
  "sessionId": "xxx",
  "callId": "yyy",
  "tool": "read",
  "args": { "path": "/src/main.kt" }
}

// Tool call confirmation
{
  "type": "tool/confirm",
  "sessionId": "xxx",
  "callId": "yyy",
  "approved": true
}
```

---

### 3.3 Tool Call Management

#### 3.3.1 Tool Call Card

**Purpose:** Display tool call requests and allow user approval/rejection.

**UI Elements:**
- Tool name (e.g., "read", "write", "bash")
- Tool arguments (formatted for readability)
- Approve button
- Reject button
- Status indicator (pending/approved/rejected)

**Behavior:**
1. Tool call arrives via WebSocket
2. Card appears in chat stream
3. User reviews and taps Approve or Reject
4. Confirmation sent via WebSocket
5. Agent continues with approved tool or handles rejection

---

### 3.4 Model Selection

#### 3.4.1 Model Selector

**Purpose:** Allow users to switch between available AI models.

**UI Elements:**
- Dropdown or bottom sheet with model list
- Current model indicator
- Model status (available/unavailable)

**API Call:**
```http
GET /api/models
```

**Response:**
```json
{
  "models": [
    { "id": "mimo-v2.5", "name": "Mimo v2.5", "status": "available" },
    { "id": "gpt-4", "name": "GPT-4", "status": "available" }
  ]
}
```

---

### 3.5 Settings Management

#### 3.5.1 Application Settings

**Purpose:** Manage app-level preferences.

**Settings:**
- Language (default: 简体中文)
- Appearance (light/dark/system)
- Font size
- About (version info)

#### 3.5.2 Server Settings

**Purpose:** Manage server-specific settings (visible after connection).

**Settings:**
- Connection status
- Server address
- Current model
- Logout button

---

## 4. Technical Specification

### 4.1 Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Navigation | Navigation Compose |
| HTTP Client | OkHttp + Retrofit |
| WebSocket | OkHttp WebSocket |
| JSON Parsing | Gson / kotlinx.serialization |
| Dependency Injection | Hilt |
| Local Storage | DataStore Preferences |
| Coroutines | Kotlin Coroutines + Flow |

### 4.2 Dependencies

```kotlin
// build.gradle.kts (app)
dependencies {
    // Compose
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    
    // Local Storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Markdown Rendering
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.13.0")
}
```

### 4.3 Data Models

```kotlin
// Session
data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String?
)

// Message
data class Message(
    val id: String,
    val sessionId: String,
    val role: Role, // USER, ASSISTANT, SYSTEM
    val content: String,
    val timestamp: Long,
    val toolCalls: List<ToolCall>?
)

enum class Role { USER, ASSISTANT, SYSTEM }

// Tool Call
data class ToolCall(
    val id: String,
    val toolName: String,
    val args: Map<String, Any>,
    val status: ToolCallStatus
)

enum class ToolCallStatus { PENDING, APPROVED, REJECTED, COMPLETED }

// Model
data class Model(
    val id: String,
    val name: String,
    val status: String
)

// Server Config
data class ServerConfig(
    val address: String,
    val username: String,
    val sessionToken: String?
)
```

### 4.4 API Interface

```kotlin
interface DshApi {
    // Authentication
    @POST("/dsh-webui-auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    // Sessions
    @GET("/api/sessions")
    suspend fun getSessions(): List<Session>
    
    @POST("/api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Session
    
    @DELETE("/api/sessions/{id}")
    suspend fun deleteSession(@Path("id") sessionId: String)
    
    @GET("/api/sessions/search")
    suspend fun searchSessions(@Query("q") query: String): List<Session>
    
    // Models
    @GET("/api/models")
    suspend fun getModels(): List<Model>
    
    // Tools
    @GET("/api/tools")
    suspend fun getTools(): List<Tool>
}

// Request/Response models
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val ok: Boolean, val redirect: String?)
data class CreateSessionRequest(val title: String)
```

---

## 5. UI/UX Design

### 5.1 Design Principles

1. **Mobile-first:** Optimized for phone screens, tablet adaptation later
2. **Immersive:** Full-screen chat experience without bottom navigation
3. **Material 3:** Follow Material Design guidelines
4. **Dark mode:** Support light/dark/system themes

### 5.2 Navigation Structure

```
App
├── Connection Screen (if not connected)
│   └── Login Form
├── Main Screen (after connection)
│   ├── Tab 0: Workspace (Session List)
│   ├── Tab 1: Favorites
│   └── Tab 2: Settings
└── Chat Screen (full-screen overlay)
    ├── Title Bar
    ├── Message List
    └── Input Area
```

### 5.3 Color Scheme

```kotlin
// Light Theme
val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    surface = Color(0xFFFAFAFA),
    background = Color(0xFFF5F5F5)
)

// Dark Theme
val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    surface = Color(0xFF1E1E1E),
    background = Color(0xFF121212)
)
```

---

## 6. Plugin Compatibility Strategy

### 6.1 Current Version (v1.0)

**Approach:** Server-side API proxy

```
Android Client
    │
    ├── Core Tools: Direct support
    ├── MCP Tools: Via server API (auto-compatible)
    ├── Plugin Tools: Via server API (auto-compatible)
    └── UI Plugins: Not supported (deferred)
```

**Implementation:**
- Client requests tool list from server via `GET /api/tools`
- Server returns all available tools (core + MCP + plugin)
- Client displays tool call requests from WebSocket
- Client sends approval/rejection via WebSocket
- Server executes approved tools

### 6.2 Future Version (v2.0)

**Approach:** WebView + JavaScript Bridge for UI plugins

```
Android Client
    │
    ├── Native UI: Jetpack Compose
    └── Plugin UI: WebView
        ├── JavaScript Bridge
        └── Plugin Communication
```

---

## 7. Error Handling

### 7.1 Connection Errors

| Error | Handling |
|-------|----------|
| Invalid server address | Show error message, allow retry |
| Authentication failed | Show error, allow retry |
| Connection timeout | Show timeout message, allow retry |
| Network unavailable | Show offline message, queue requests |

### 7.2 WebSocket Errors

| Error | Handling |
|-------|----------|
| Connection lost | Auto-reconnect with exponential backoff |
| Authentication expired | Redirect to login |
| Message parse error | Log error, continue listening |

### 7.3 API Errors

| Error | Handling |
|-------|----------|
| 401 Unauthorized | Redirect to login |
| 404 Not Found | Show error message |
| 500 Server Error | Show error, allow retry |

---

## 8. Security Considerations

### 8.1 Authentication

- Session tokens stored in encrypted SharedPreferences
- HttpOnly cookie simulation (no JavaScript access)
- Token expiration handling

### 8.2 Network Security

- HTTPS enforced for production
- Certificate pinning (optional)
- No sensitive data in logs

### 8.3 Data Storage

- Credentials encrypted at rest
- No plaintext passwords stored
- Secure deletion of cached data

---

## 9. Testing Strategy

### 9.1 Unit Tests

- Repository tests
- UseCase tests
- ViewModel tests
- Network layer tests

### 9.2 Integration Tests

- API client tests
- WebSocket client tests
- Authentication flow tests

### 9.3 UI Tests

- Compose UI tests
- Navigation tests
- User interaction tests

---

## 10. Version Roadmap

| Version | Features |
|---------|----------|
| v1.0 | Core: Connection, Sessions, Chat, Tool Calls, Models |
| v1.1 | Settings, Favorites |
| v1.2 | File Management |
| v2.0 | Plugin System, UI Plugin Rendering |

---

## 11. Open Questions

1. **WebSocket Protocol:** Need to verify exact message format from DSH server
2. **Streaming Response:** How to handle streaming markdown rendering efficiently
3. **Offline Support:** Should we support offline session history?
4. **Push Notifications:** Should we support notifications for agent status changes?

---

## 12. Appendices

### A. DSH Server API Reference

- Login: `POST /dsh-webui-auth/login`
- Sessions: `GET/POST/DELETE /api/sessions`
- Models: `GET /api/models`
- Tools: `GET /api/tools`
- WebSocket: `ws://{server}/api/remote.mux`

### B. Related Documentation

- DSH Web Client: Reference implementation
- DSH Plugin Manager: Plugin system documentation
- DSH HarmonyOS Client: Parallel native client project

---

**Document End**

# Task 9: Theme & UI Foundation

## Objective
Create Material 3 theme and string resources for the application.

## Files to Create
- `app/src/main/java/com/dsh/android/ui/theme/Color.kt`
- `app/src/main/java/com/dsh/android/ui/theme/Theme.kt`
- `app/src/main/java/com/dsh/android/ui/theme/Type.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`

## Requirements

### 1. Color.kt
```kotlin
package com.dsh.android.ui.theme

import androidx.compose.ui.graphics.Color

val Blue80 = Color(0xFFBBDEFB)
val BlueGrey80 = Color(0xFFCFD8DC)
val Teal80 = Color(0xFFB2DFDB)

val Blue40 = Color(0xFF1976D2)
val BlueGrey40 = Color(0xFF546E7A)
val Teal40 = Color(0xFF00796B)

val SurfaceDark = Color(0xFF1E1E1E)
val BackgroundDark = Color(0xFF121212)
val SurfaceLight = Color(0xFFFAFAFA)
val BackgroundLight = Color(0xFFF5F5F5)
```

### 2. Theme.kt
```kotlin
package com.dsh.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Teal80,
    surface = SurfaceDark,
    background = BackgroundDark
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Teal40,
    surface = SurfaceLight,
    background = BackgroundLight
)

@Composable
fun DshAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### 3. Type.kt
```kotlin
package com.dsh.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### 4. strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">DSH Client</string>
    <string name="connect">连接</string>
    <string name="server_address">服务器地址</string>
    <string name="username">用户名</string>
    <string name="password">密码</string>
    <string name="remember_password">记住密码</string>
    <string name="connecting">连接中...</string>
    <string name="connection_failed">连接失败</string>
    <string name="sessions">会话</string>
    <string name="new_session">新建会话</string>
    <string name="settings">设置</string>
    <string name="favorites">收藏</string>
    <string name="workspace">工作区</string>
    <string name="send">发送</string>
    <string name="input_message">输入消息...</string>
    <string name="approve">允许</string>
    <string name="reject">拒绝</string>
    <string name="tool_call">工具调用</string>
    <string name="agent_running">Agent 运行中...</string>
    <string name="logout">退出登录</string>
    <string name="search_sessions">搜索会话...</string>
</resources>
```

### 5. themes.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DshAndroid" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

## Dependencies
- Uses Compose Material 3
- Uses Compose Runtime
- Uses AndroidX Core

## Verification
- Verify all files have correct package declarations
- Verify theme colors are defined
- Verify string resources are complete

## Commit
```bash
git add app/src/main/java/com/dsh/android/ui/theme/ \
        app/src/main/res/values/
git commit -m "feat: add Material 3 theme and string resources"
```

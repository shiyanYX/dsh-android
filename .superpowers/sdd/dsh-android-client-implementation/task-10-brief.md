# Task 10: Navigation

## Objective
Create navigation graph and MainActivity for the application.

## Files to Create
- `app/src/main/java/com/dsh/android/ui/navigation/NavGraph.kt`
- `app/src/main/java/com/dsh/android/MainActivity.kt`

## Requirements

### 1. NavGraph.kt
```kotlin
package com.dsh.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dsh.android.ui.chat.ChatScreen
import com.dsh.android.ui.connection.ConnectionScreen
import com.dsh.android.ui.sessions.SessionListScreen
import com.dsh.android.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object Sessions : Screen("sessions")
    object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Connection.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Connection.route) {
            ConnectionScreen(
                onConnected = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Connection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Sessions.route) {
            SessionListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Chat.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            ChatScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Connection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
```

### 2. MainActivity.kt
```kotlin
package com.dsh.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.dsh.android.ui.navigation.NavGraph
import com.dsh.android.ui.theme.DshAndroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DshAndroidTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
```

## Dependencies
- Uses Compose Navigation
- Uses all screen composables (will be created in later tasks)
- Uses DshAndroidTheme from Task 9
- Uses Hilt @AndroidEntryPoint annotation

## Verification
- Verify both files have correct package declarations
- Verify navigation routes are defined correctly
- Verify MainActivity uses @AndroidEntryPoint

## Commit
```bash
git add app/src/main/java/com/dsh/android/ui/navigation/ \
        app/src/main/java/com/dsh/android/MainActivity.kt
git commit -m "feat: add navigation graph and MainActivity"
```

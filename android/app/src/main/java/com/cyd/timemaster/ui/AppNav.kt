package com.cyd.timemaster.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cyd.timemaster.ui.log.LogEditScreen
import com.cyd.timemaster.ui.log.LogScreen
import com.cyd.timemaster.ui.me.MeScreen
import com.cyd.timemaster.ui.stats.StatsScreen
import com.cyd.timemaster.ui.todo.TodoScreen
import com.cyd.timemaster.ui.timer.TimerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("timer", "计时", Icons.Filled.AccessTime),
    Tab("stats", "统计", Icons.Filled.BarChart),
    Tab("log", "日志", Icons.Filled.EditNote),
    Tab("ai", "AI", Icons.Filled.AutoAwesome),
    Tab("todo", "待办", Icons.Filled.TaskAlt),
    Tab("me", "我的", Icons.Filled.Person),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "timer",
            modifier = Modifier.padding(padding),
        ) {
            composable("timer") { TimerScreen() }
            composable("stats") { StatsScreen() }
            composable("log") {
                LogScreen(onEditLog = { uuid -> navController.navigate("log_edit/$uuid") })
            }
            composable("ai") { com.cyd.timemaster.ui.ai.ChatScreen() }
            composable("log_edit/{uuid}") { entry ->
                val uuid = entry.arguments?.getString("uuid")
                LogEditScreen(
                    logUuid = uuid?.takeIf { it != "new" },
                    onDone = { navController.popBackStack() },
                )
            }
            composable("todo") { TodoScreen() }
            composable("me") { MeScreen(onOpenSpending = { navController.navigate("spending") }) }
            composable("spending") {
                com.cyd.timemaster.ui.agent.SpendingScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

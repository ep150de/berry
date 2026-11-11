package com.alexanderlingle.berry.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alexanderlingle.berry.data.Child
import com.alexanderlingle.berry.ui.components.ChildAvatar
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.ui.screens.AskBerryScreen
import com.alexanderlingle.berry.ui.screens.ChildrenScreen
import com.alexanderlingle.berry.ui.screens.DashboardScreen
import com.alexanderlingle.berry.ui.screens.DiaperScreen
import com.alexanderlingle.berry.ui.screens.FeedingScreen
import com.alexanderlingle.berry.ui.screens.HistoryScreen
import com.alexanderlingle.berry.ui.screens.SleepScreen
import com.alexanderlingle.berry.ui.screens.StatsScreen
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.ChildViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val SLEEP = "sleep"
    const val FEEDING = "feeding"
    const val DIAPER = "diaper"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val CHILDREN = "children"
    const val ASK_BERRY = "ask-berry"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// Stats and the children manager live outside the bottom bar (reached from
// the dashboard / top bar) to keep the bar at five destinations.
private val bottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    BottomDestination(Routes.SLEEP, "Sleep", Icons.Filled.Bedtime),
    BottomDestination(Routes.FEEDING, "Feeding", Icons.Filled.Restaurant),
    BottomDestination(Routes.DIAPER, "Diaper", Icons.Filled.BabyChangingStation),
    BottomDestination(Routes.HISTORY, "History", Icons.Filled.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BerryNavGraph(
    navController: NavHostController = rememberNavController(),
    startRoute: String = Routes.DASHBOARD
) {
    val factory = rememberBerryFactory()
    val childVm: ChildViewModel = viewModel(factory = factory)
    val children by childVm.children.collectAsStateWithLifecycle()
    val selectedChild by childVm.selectedChild.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Deep-link from predictive notifications: open the tab the alert is about.
    LaunchedEffect(startRoute) {
        if (startRoute != Routes.DASHBOARD) {
            navController.navigate(startRoute) { launchSingleTop = true }
        }
    }

    Scaffold(
        topBar = {
            if (currentRoute != Routes.CHILDREN) {
                ChildTopBar(
                    child = selectedChild,
                    children = children,
                    onSelect = childVm::select,
                    onManage = { navController.navigate(Routes.CHILDREN) }
                )
            }
        },
        bottomBar = {
            if (currentRoute != Routes.CHILDREN) {
                NavigationBar {
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(Routes.SLEEP) { SleepScreen() }
            composable(Routes.FEEDING) { FeedingScreen() }
            composable(Routes.DIAPER) { DiaperScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.ASK_BERRY) { AskBerryScreen() }
            composable(Routes.CHILDREN) {
                ChildrenScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Global header: selected child's photo, name and age; tap to switch child. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildTopBar(
    child: Child?,
    children: List<Child>,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = true }
            ) {
                ChildAvatar(
                    photoPath = child?.photoPath,
                    name = child?.name ?: "Berry",
                    size = 36.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        child?.name ?: "Berry",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        child?.birthdateMillis?.let { TimeUtils.formatAge(it) } ?: " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Switch child"
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                children.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.name) },
                        leadingIcon = {
                            ChildAvatar(photoPath = c.photoPath, name = c.name, size = 32.dp)
                        },
                        trailingIcon = {
                            if (c.id == child?.id) Icon(Icons.Filled.Check, contentDescription = "Selected")
                        },
                        onClick = {
                            onSelect(c.id)
                            expanded = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Manage children") },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onManage()
                    }
                )
            }
        }
    )
}

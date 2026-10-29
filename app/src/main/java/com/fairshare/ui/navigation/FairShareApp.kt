package com.fairshare.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fairshare.ui.components.AddExpenseFab
import com.fairshare.ui.screens.AktywnoscScreen
import com.fairshare.ui.screens.GrupyScreen
import com.fairshare.ui.screens.PulpitScreen
import com.fairshare.ui.screens.StatystykiScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
    val showFab: Boolean = false,
) {
    Pulpit("pulpit", "Pulpit", Icons.Outlined.Home, showFab = true),
    Grupy("grupy", "Grupy", Icons.Outlined.Group, showFab = true),
    Statystyki("statystyki", "Statystyki", Icons.Outlined.BarChart),
    Aktywnosc("aktywnosc", "Aktywność", Icons.Outlined.Notifications, badge = 3),
}

@Composable
fun FairShareApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.entries.firstOrNull { it.route == backStackEntry?.destination?.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FairShareBottomBar(current) { destination ->
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        floatingActionButton = {
            if (current?.showFab == true) AddExpenseFab()
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Pulpit.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Pulpit.route) { PulpitScreen() }
            composable(Destination.Grupy.route) { GrupyScreen() }
            composable(Destination.Statystyki.route) { StatystykiScreen() }
            composable(Destination.Aktywnosc.route) { AktywnoscScreen() }
        }
    }
}

@Composable
private fun FairShareBottomBar(
    current: Destination?,
    onSelect: (Destination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Destination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    if (destination.badge > 0 && !selected) {
                        BadgedBox(badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) { Text(destination.badge.toString()) }
                        }) {
                            Icon(destination.icon, contentDescription = null)
                        }
                    } else {
                        Icon(destination.icon, contentDescription = null)
                    }
                },
                label = {
                    Text(
                        destination.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

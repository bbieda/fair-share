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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.fairshare.FairShareApplication
import com.fairshare.scanner.ScannedReceipt
import com.fairshare.ui.components.AddExpenseFab
import com.fairshare.ui.components.LocalProfilePhoto
import com.fairshare.ui.rememberProfilePhoto
import com.fairshare.ui.screens.AktywnoscScreen
import com.fairshare.ui.screens.DodajWydatekScreen
import com.fairshare.ui.screens.GrupaScreen
import com.fairshare.ui.screens.GrupyScreen
import com.fairshare.ui.screens.PodzielPozycjeScreen
import com.fairshare.ui.screens.ProfilScreen
import com.fairshare.ui.screens.PulpitScreen
import com.fairshare.ui.screens.RozliczScreen
import com.fairshare.ui.screens.SkanujParagonScreen
import com.fairshare.ui.screens.StatystykiScreen
import com.fairshare.ui.screens.summaryLine
import com.fairshare.ui.viewmodel.AddExpenseViewModel
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.GROUP_ID_ARG

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
    Aktywnosc("aktywnosc", "Aktywność", Icons.Outlined.Notifications),
}

/** Screens opened from the tabs; they sit on top of the tab's back stack. */
private object Routes {
    const val GROUP = "grupa/{$GROUP_ID_ARG}"
    /** groupId is optional: -1 means "all groups". Used by the "Statystyki" button inside a group. */
    const val STATS = "statystyki?$GROUP_ID_ARG={$GROUP_ID_ARG}"
    /** groupId is optional: -1 means "pick the group in the form". */
    const val ADD_EXPENSE = "dodaj_wydatek?$GROUP_ID_ARG={$GROUP_ID_ARG}"
    const val SETTLE = "rozlicz"
    const val PROFILE = "profil"
    /** Receipt scanner, opened from "Nowy wydatek"; the result goes back to that form. */
    const val SCAN = "skanuj_paragon"
    /** "Podziel pozycje": who took which receipt item; works on the ViewModel of the "Nowy wydatek" form under it. */
    const val SPLIT_ITEMS = "podziel_pozycje"
}

@Composable
fun FairShareApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // Secondary screens keep the tab they belong to highlighted
    val current = when (route) {
        Routes.GROUP -> Destination.Grupy
        Routes.SETTLE -> Destination.Pulpit
        Routes.STATS -> Destination.Statystyki
        else -> Destination.entries.firstOrNull { it.route == route }
    }

    // Profile photo (shown in every screen header) and the number of unread activity entries
    val app = LocalContext.current.applicationContext as FairShareApplication
    val me by app.repository.me.collectAsStateWithLifecycle(initialValue = null)
    val profilePhoto by rememberProfilePhoto(me?.photoUri)
    val unreadActivity by app.repository.unreadActivityCount.collectAsStateWithLifecycle(initialValue = 0)
    val fullScreen = route == Routes.ADD_EXPENSE || route == Routes.PROFILE || route == Routes.SCAN ||
        route == Routes.SPLIT_ITEMS
    val showFab = route == Routes.GROUP || Destination.entries.any { it.route == route && it.showFab }

    val openTab: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openProfile: () -> Unit = { navController.navigate(Routes.PROFILE) }
    val openSettle: () -> Unit = { navController.navigate(Routes.SETTLE) }
    val back: () -> Unit = { navController.popBackStack() }

    CompositionLocalProvider(LocalProfilePhoto provides profilePhoto) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!fullScreen) FairShareBottomBar(current, unreadActivity, openTab)
            },
            floatingActionButton = {
                if (showFab) {
                    // From a group screen the form opens with that group already selected
                    val groupId = if (route == Routes.GROUP) backStackEntry?.arguments?.getLong(GROUP_ID_ARG) else null
                    AddExpenseFab(
                        onClick = {
                            // a new expense from scratch: receipts left over from a photo with several are dropped
                            ScanHandover.clear()
                            navController.navigate(addExpenseRoute(groupId))
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Pulpit.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Destination.Pulpit.route) {
                    PulpitScreen(
                        onOpenProfile = openProfile,
                        onSettle = openSettle,
                        onOpenStats = { openTab(Destination.Statystyki) },
                    )
                }
                composable(Destination.Grupy.route) {
                    GrupyScreen(
                        onOpenProfile = openProfile,
                        onOpenGroup = { groupId -> navController.navigate("grupa/$groupId") },
                    )
                }
                composable(
                    Routes.STATS,
                    arguments = listOf(
                        navArgument(GROUP_ID_ARG) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) { StatystykiScreen(onOpenProfile = openProfile) }
                composable(Destination.Aktywnosc.route) {
                    AktywnoscScreen(
                        onOpenProfile = openProfile,
                        onOpenGroup = { groupId -> navController.navigate("grupa/$groupId") },
                    )
                }

                composable(
                    Routes.GROUP,
                    arguments = listOf(navArgument(GROUP_ID_ARG) { type = NavType.LongType }),
                ) {
                    GrupaScreen(
                        onBack = back,
                        onSettle = openSettle,
                        // opens the statistics already filtered to this group; "back" returns to the group
                        onOpenStats = { groupId ->
                            navController.navigate("statystyki?$GROUP_ID_ARG=$groupId") { launchSingleTop = true }
                        },
                    )
                }
                composable(
                    Routes.ADD_EXPENSE,
                    arguments = listOf(
                        navArgument(GROUP_ID_ARG) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) {
                    val form: AddExpenseViewModel = viewModel(factory = AppViewModelProvider.Factory)
                    // A receipt from the scanner (or the next one of a photo with several) waits in ScanHandover.
                    // This runs when the form appears and again when a receipt is handed over while it is shown.
                    LaunchedEffect(form, ScanHandover.version) {
                        ScanHandover.take()?.let { (receipt, summary) -> form.applyScan(receipt, summary) }
                    }
                    DodajWydatekScreen(
                        onClose = {
                            ScanHandover.clear()
                            back()
                        },
                        onSave = {
                            if (ScanHandover.giveNext()) {
                                // "Osobno": a fresh form with the next receipt of the photo, in the same group
                                navController.navigate(addExpenseRoute(form.selectedGroupId)) {
                                    popUpTo(Routes.ADD_EXPENSE) { inclusive = true }
                                }
                            } else {
                                back()
                            }
                        },
                        onScanReceipt = { navController.navigate(Routes.SCAN) },
                        onSplitItems = { navController.navigate(Routes.SPLIT_ITEMS) },
                        viewModel = form,
                    )
                }
                composable(Routes.SPLIT_ITEMS) { entry ->
                    // the ViewModel of the form underneath: the items are part of the expense being added
                    val formEntry = remember(entry) { navController.getBackStackEntry(Routes.ADD_EXPENSE) }
                    val form: AddExpenseViewModel = viewModel(
                        viewModelStoreOwner = formEntry,
                        factory = AppViewModelProvider.Factory,
                    )
                    PodzielPozycjeScreen(viewModel = form, onBack = back)
                }
                composable(Routes.SCAN) {
                    SkanujParagonScreen(
                        onBack = back,
                        onUse = { receipt ->
                            ScanHandover.give(receipt to receipt.summaryLine())
                            navController.popBackStack()
                        },
                        onUseSeparately = { receipts ->
                            val labelled = receipts.mapIndexed { index, receipt ->
                                receipt to "${receipt.summaryLine()} · paragon ${index + 1} z ${receipts.size}"
                            }
                            ScanHandover.give(labelled.first(), then = labelled.drop(1))
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.SETTLE) { RozliczScreen(onBack = back) }
                composable(Routes.PROFILE) { ProfilScreen(onBack = back) }
            }
        }
    }
}

/**
 * Receipts on their way from the scanner to the "Nowy wydatek" form: the one the form should take now and, for a
 * photo with several receipts added separately ("Osobno"), the ones that follow, each in its own form after the
 * previous expense is saved. Kept in memory only: the handover takes a moment.
 */
private object ScanHandover {
    private var forForm: Pair<ScannedReceipt, String>? = null
    private val waiting = ArrayDeque<Pair<ScannedReceipt, String>>()

    /** Changes with every handover, so a form that is already on screen notices (it is Compose state). */
    var version by mutableIntStateOf(0)
        private set

    /** [receipt] with its line for the "Paragon" row, for the form; [then] wait their turn. */
    fun give(receipt: Pair<ScannedReceipt, String>, then: List<Pair<ScannedReceipt, String>> = emptyList()) {
        forForm = receipt
        waiting.clear()
        waiting.addAll(then)
        version++
    }

    /**
     * Moves the next waiting receipt up for the form about to open; false when none is left. [version] stays as it
     * is: the form that was just saved is still on screen for a moment and must not take this receipt.
     */
    fun giveNext(): Boolean {
        forForm = waiting.removeFirstOrNull() ?: return false
        return true
    }

    /** The receipt for the form, once. */
    fun take(): Pair<ScannedReceipt, String>? = forForm.also { forForm = null }

    fun clear() {
        forForm = null
        waiting.clear()
    }
}

private fun addExpenseRoute(groupId: Long?): String =
    if (groupId != null && groupId > 0) "dodaj_wydatek?$GROUP_ID_ARG=$groupId" else "dodaj_wydatek"

@Composable
private fun FairShareBottomBar(
    current: Destination?,
    unreadActivity: Int,
    onSelect: (Destination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Destination.entries.forEach { destination ->
            val selected = destination == current
            // the Aktywność tab shows how many entries are still unread
            val badge = if (destination == Destination.Aktywnosc) unreadActivity else destination.badge
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    if (badge > 0 && !selected) {
                        BadgedBox(badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) { Text(if (badge > 99) "99+" else badge.toString()) }
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

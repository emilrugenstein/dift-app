package app.dift.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dift.R
import app.dift.ui.screens.blockeditor.BlockEditorScreen
import app.dift.ui.screens.blocks.BlocksScreen
import app.dift.ui.screens.onboarding.OnboardingScreen
import app.dift.ui.screens.overview.OverviewScreen
import app.dift.ui.screens.settings.SettingsScreen

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val destinations = listOf(
    NavDestination(Routes.OVERVIEW, R.string.nav_overview, Icons.Filled.DateRange),
    NavDestination(Routes.BLOCKS, R.string.nav_blocks, Icons.Filled.Lock),
    NavDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun DiftApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboarded by rootViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        rootViewModel.syncMonitoring()
        onPauseOrDispose { }
    }

    when (onboarded) {
        null -> Unit // preference still loading; render nothing for a frame
        false -> OnboardingScreen(onDone = rootViewModel::completeOnboarding)
        true -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.OVERVIEW,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.OVERVIEW) { OverviewScreen() }
            composable(Routes.BLOCKS) {
                BlocksScreen(
                    onAddBlock = { navController.navigate(Routes.blockEditor()) },
                    onEditBlock = { blockId -> navController.navigate(Routes.blockEditor(blockId)) },
                )
            }
            composable(
                route = Routes.BLOCK_EDITOR_ROUTE,
                arguments = listOf(
                    navArgument(Routes.BLOCK_EDITOR_ARG) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val blockId = entry.arguments?.getLong(Routes.BLOCK_EDITOR_ARG) ?: 0L
                BlockEditorScreen(blockId = blockId, onSaved = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

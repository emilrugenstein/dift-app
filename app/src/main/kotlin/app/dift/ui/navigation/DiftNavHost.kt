package app.dift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.dift.ui.screens.home.HomeScreen

// Route constants live here; screens must not hardcode route strings.
object Routes {
    const val HOME = "home"
}

@Composable
fun DiftNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen() }
    }
}

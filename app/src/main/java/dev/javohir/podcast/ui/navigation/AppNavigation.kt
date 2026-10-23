package dev.javohir.podcast.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import dev.javohir.podcast.ui.episode.EpisodeDetailScreen
import dev.javohir.podcast.ui.feed.FeedDetailScreen
import dev.javohir.podcast.ui.folder.FolderDetailScreen
import dev.javohir.podcast.ui.home.HomeScreen
import dev.javohir.podcast.ui.library.LibraryScreen
import dev.javohir.podcast.ui.player.PlayerScreen
import dev.javohir.podcast.ui.search.SearchScreen
import dev.javohir.podcast.ui.settings.BackupRestoreScreen
import dev.javohir.podcast.ui.settings.SettingsScreen
import dev.javohir.podcast.ui.settings.StorageScreen

object Routes {
    const val HOME = "home"
    const val HOME_MAIN = "home/main"
    const val LIBRARY = "library"
    const val LIBRARY_MAIN = "library/main"
    const val LIBRARY_FEED = "library/feed/{feedId}"
    const val LIBRARY_FOLDER = "library/folder/{folderId}"
    const val SEARCH = "search"
    const val SEARCH_MAIN = "search/main"
    const val SETTINGS = "settings"
    const val SETTINGS_MAIN = "settings/main"
    const val SETTINGS_STORAGE = "settings/storage"
    const val SETTINGS_BACKUP = "settings/backup"
    const val PLAYER = "player"

    val TAB_ROOTS = setOf(HOME_MAIN, LIBRARY_MAIN, SEARCH_MAIN, SETTINGS_MAIN)

    fun tabRoot(graph: String): String = "$graph/main"
    fun homeEpisode(id: Long) = "home/episode/$id"
    fun libraryEpisode(id: Long) = "library/episode/$id"
    fun libraryFeed(id: Long) = "library/feed/$id"
    fun libraryFolder(id: Long) = "library/folder/$id"
    fun searchEpisode(id: Long) = "search/episode/$id"
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen(Routes.HOME, "Home", Icons.Default.Home)
    data object Library : Screen(Routes.LIBRARY, "Library", Icons.Default.LibraryMusic)
    data object Search : Screen(Routes.SEARCH, "Search", Icons.Default.Search)
    data object Settings : Screen(Routes.SETTINGS, "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(Screen.Home, Screen.Library, Screen.Search, Screen.Settings)

@Composable
fun AppNavHost(
    navController: NavHostController,
    onPlayEpisode: (Long, List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        navigation(
            startDestination = Routes.HOME_MAIN,
            route = Routes.HOME,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Routes.HOME_MAIN) {
                HomeScreen(
                    onEpisodeClick = { id -> navController.navigate(Routes.homeEpisode(id)) },
                    onPlayClick = onPlayEpisode
                )
            }
            episodeDestination("home/episode/{episodeId}", navController, onPlayEpisode)
        }
        navigation(
            startDestination = Routes.LIBRARY_MAIN,
            route = Routes.LIBRARY,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Routes.LIBRARY_MAIN) {
                LibraryScreen(
                    onFeedClick = { id -> navController.navigate(Routes.libraryFeed(id)) },
                    onFolderClick = { id -> navController.navigate(Routes.libraryFolder(id)) },
                    onEpisodeClick = { id -> navController.navigate(Routes.libraryEpisode(id)) }
                )
            }
            composable(
                Routes.LIBRARY_FEED,
                arguments = listOf(navArgument("feedId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) }
            ) { back ->
                FeedDetailScreen(
                    feedId = back.arguments!!.getLong("feedId"),
                    onBack = { navController.popBackStack() },
                    onEpisodeClick = { id -> navController.navigate(Routes.libraryEpisode(id)) },
                    onPlayClick = onPlayEpisode
                )
            }
            composable(
                Routes.LIBRARY_FOLDER,
                arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) }
            ) { back ->
                FolderDetailScreen(
                    folderId = back.arguments!!.getLong("folderId"),
                    onBack = { navController.popBackStack() },
                    onEpisodeClick = { id -> navController.navigate(Routes.libraryEpisode(id)) },
                    onPlayClick = onPlayEpisode
                )
            }
            episodeDestination("library/episode/{episodeId}", navController, onPlayEpisode)
        }
        navigation(
            startDestination = Routes.SEARCH_MAIN,
            route = Routes.SEARCH,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Routes.SEARCH_MAIN) {
                SearchScreen(
                    onEpisodeClick = { id -> navController.navigate(Routes.searchEpisode(id)) },
                    onPlayClick = onPlayEpisode
                )
            }
            episodeDestination("search/episode/{episodeId}", navController, onPlayEpisode)
        }
        navigation(
            startDestination = Routes.SETTINGS_MAIN,
            route = Routes.SETTINGS,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Routes.SETTINGS_MAIN) {
                SettingsScreen(
                    onStorageClick = { navController.navigate(Routes.SETTINGS_STORAGE) },
                    onBackupClick = { navController.navigate(Routes.SETTINGS_BACKUP) }
                )
            }
            composable(
                Routes.SETTINGS_STORAGE,
                enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) }
            ) {
                StorageScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.SETTINGS_BACKUP,
                enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) }
            ) {
                BackupRestoreScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(
            Routes.PLAYER,
            enterTransition = { slideInVertically(tween(300)) { it } },
            popExitTransition = { slideOutVertically(tween(300)) { it } },
            popEnterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) }
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavGraphBuilder.episodeDestination(
    route: String,
    navController: NavHostController,
    onPlayEpisode: (Long, List<Long>) -> Unit
) {
    composable(
        route,
        arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
        enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) }
    ) { back: NavBackStackEntry ->
        EpisodeDetailScreen(
            episodeId = back.arguments!!.getLong("episodeId"),
            onBack = { navController.popBackStack() },
            onPlayClick = onPlayEpisode,
            onOpenPlayer = { navController.openPlayer() }
        )
    }
}

private fun NavHostController.openPlayer() {
    navigate(Routes.PLAYER) { launchSingleTop = true }
}

@Composable
fun BottomNavBar(
    navController: NavHostController,
    onTabSelected: (String) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    NavigationBar {
        bottomNavScreens.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                onClick = { onTabSelected(screen.route) }
            )
        }
    }
}

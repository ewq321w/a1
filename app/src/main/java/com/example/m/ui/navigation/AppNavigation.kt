package com.example.m.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.m.ui.home.HomeScreen
import com.example.m.ui.library.HiddenArtistsScreen
import com.example.m.ui.library.HistoryScreen
import com.example.m.ui.library.LibraryScreen
import com.example.m.ui.library.details.ArtistDetailScreen
import com.example.m.ui.library.details.ArtistGroupDetailScreen
import com.example.m.ui.library.details.PlaylistDetailScreen
import com.example.m.ui.library.edit.EditArtistSongsScreen
import com.example.m.ui.library.edit.EditPlaylistScreen
import com.example.m.ui.search.SearchScreen
import com.example.m.ui.search.details.AlbumDetailScreen
import com.example.m.ui.search.details.ArtistAlbumsScreen
import com.example.m.ui.search.details.ArtistSongsScreen
import com.example.m.ui.search.details.SearchedArtistDetailScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object PlaylistDetail : Screen("playlist_detail/{playlistId}", "Playlist", Icons.Default.LibraryMusic)
    object ArtistDetail : Screen("artist_detail/{artistId}", "Artist", Icons.Default.LibraryMusic)
    object EditPlaylist : Screen("edit_playlist/{playlistId}", "Edit Playlist", Icons.Default.Edit)
    object EditArtistSongs : Screen("edit_artist_songs/{artistId}", "Edit Artist Songs", Icons.Default.Edit)
    object HiddenArtists : Screen("hidden_artists", "Hidden Artists", Icons.Default.Visibility)
    object ArtistGroupDetail : Screen("artist_group_detail/{groupId}", "Artist Group", Icons.Default.Folder)
    object SearchedArtistDetail : Screen("searched_artist_detail/{searchType}/{channelUrl}", "Artist Details", Icons.Default.Person)
    object AlbumDetail : Screen("album_detail/{searchType}/{albumUrl}", "Album Details", Icons.Default.Album)
    object ArtistSongsDetail : Screen("artist_songs/{searchType}/{channelUrl}", "Artist Songs", Icons.Default.MusicNote)
    object ArtistAlbumsDetail : Screen("artist_albums/{searchType}/{channelUrl}", "Artist Albums", Icons.Default.Album)
    object History : Screen("history", "History", Icons.Default.History)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(80)) },
        exitTransition = { fadeOut(animationSpec = tween(80)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate("playlist_detail/$playlistId")
                },
                onArtistClick = { artistId ->
                    navController.navigate("artist_detail/$artistId")
                },
                onEditPlaylist = { playlistId ->
                    navController.navigate("edit_playlist/$playlistId")
                },
                onEditArtistSongs = { artistId ->
                    navController.navigate("edit_artist_songs/$artistId")
                },
                onGoToHiddenArtists = {
                    navController.navigate(Screen.HiddenArtists.route)
                },
                onGoToArtistGroup = { groupId ->
                    navController.navigate("artist_group_detail/$groupId")
                },
                onGoToHistory = {
                    navController.navigate(Screen.History.route)
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) {
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onEditPlaylist = { playlistId ->
                    navController.navigate("edit_playlist/$playlistId")
                },
                onArtistClick = { artistId ->
                    navController.navigate("artist_detail/$artistId")
                }
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistId") { type = NavType.LongType })
        ) {
            ArtistDetailScreen(
                onBack = { navController.popBackStack() },
                onEditArtistSongs = { artistId ->
                    navController.navigate("edit_artist_songs/$artistId")
                },
                onArtistClick = { artistId ->
                    navController.navigate("artist_detail/$artistId")
                }
            )
        }

        composable(
            route = Screen.EditPlaylist.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) {
            EditPlaylistScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.EditArtistSongs.route,
            arguments = listOf(navArgument("artistId") { type = NavType.LongType })
        ) {
            EditArtistSongsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.HiddenArtists.route) {
            HiddenArtistsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onArtistClick = { artistId ->
                    navController.navigate("artist_detail/$artistId")
                }
            )
        }

        composable(
            route = Screen.ArtistGroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) {
            ArtistGroupDetailScreen(
                onBack = { navController.popBackStack() },
                onArtistClick = { artistId ->
                    navController.navigate("artist_detail/$artistId")
                },
                onEditArtistSongs = { artistId ->
                    navController.navigate("edit_artist_songs/$artistId")
                }
            )
        }

        composable(
            route = Screen.SearchedArtistDetail.route,
            arguments = listOf(
                navArgument("searchType") { type = NavType.StringType },
                navArgument("channelUrl") { type = NavType.StringType }
            )
        ) {
            SearchedArtistDetailScreen(
                onBack = { navController.popBackStack() },
                onAlbumClick = { searchType, albumUrl ->
                    val encodedUrl = URLEncoder.encode(albumUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("album_detail/$searchType/$encodedUrl")
                },
                onGoToSongs = { searchType, channelUrl ->
                    val encodedUrl = URLEncoder.encode(channelUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("artist_songs/$searchType/$encodedUrl")
                },
                onGoToAlbums = { searchType, channelUrl ->
                    val encodedUrl = URLEncoder.encode(channelUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("artist_albums/$searchType/$encodedUrl")
                }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(
                navArgument("searchType") { type = NavType.StringType },
                navArgument("albumUrl") { type = NavType.StringType }
            )
        ) {
            AlbumDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ArtistSongsDetail.route,
            arguments = listOf(
                navArgument("searchType") { type = NavType.StringType },
                navArgument("channelUrl") { type = NavType.StringType }
            )
        ) {
            ArtistSongsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ArtistAlbumsDetail.route,
            arguments = listOf(
                navArgument("searchType") { type = NavType.StringType },
                navArgument("channelUrl") { type = NavType.StringType }
            )
        ) {
            ArtistAlbumsScreen(
                onBack = { navController.popBackStack() },
                onAlbumClick = { searchType, albumUrl ->
                    val encodedUrl = URLEncoder.encode(albumUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("album_detail/$searchType/$encodedUrl")
                }
            )
        }
    }
}
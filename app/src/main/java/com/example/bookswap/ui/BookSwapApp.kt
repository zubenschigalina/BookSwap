package com.example.bookswap.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bookswap.R
import com.example.bookswap.ui.auth.AuthViewModel
import com.example.bookswap.ui.auth.ForgotPasswordScreen
import com.example.bookswap.ui.auth.LoginScreen
import com.example.bookswap.ui.auth.RegisterScreen
import com.example.bookswap.ui.auth.VerifyEmailScreen
import com.example.bookswap.ui.chat.ChatScreen
import com.example.bookswap.ui.discovery.DiscoveryScreen
import com.example.bookswap.ui.exchange.ExchangeDetailScreen
import com.example.bookswap.ui.favorites.FavoritesScreen
import com.example.bookswap.ui.inbox.InboxScreen
import com.example.bookswap.ui.inbox.InterestedUserScreen
import com.example.bookswap.ui.library.AddEditBookScreen
import com.example.bookswap.ui.library.MyLibraryScreen
import com.example.bookswap.ui.profile.EditProfileScreen
import com.example.bookswap.ui.profile.ProfileScreen
import com.example.bookswap.ui.proposals.ProposeSwapScreen
import com.example.bookswap.ui.session.SessionState
import com.example.bookswap.ui.session.SessionViewModel

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"

    const val DISCOVER = "discover"
    const val FAVORITES = "favorites"
    const val INBOX = "inbox"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val ADD_BOOK = "add_book"
    const val EDIT_BOOK = "edit_book" // "$EDIT_BOOK/{bookId}"
    const val EDIT_PROFILE = "edit_profile"
    const val PROPOSE = "propose" // "$PROPOSE/{bookId}/{ownerId}/{title}"
    const val USER_LIBRARY = "user_library" // "$USER_LIBRARY/{userId}/{name}"
    const val EXCHANGE = "exchange" // "$EXCHANGE/{proposalId}"
    const val CHAT = "chat" // "$CHAT/{proposalId}/{name}"
}

private enum class Tab(val route: String, val labelRes: Int) {
    DISCOVER(Routes.DISCOVER, R.string.tab_discover),
    FAVORITES(Routes.FAVORITES, R.string.tab_favorites),
    INBOX(Routes.INBOX, R.string.tab_inbox),
    LIBRARY(Routes.LIBRARY, R.string.tab_library),
    PROFILE(Routes.PROFILE, R.string.tab_profile),
}

/** Root composable: routes the user by [SessionState] into the correct flow. */
@Composable
fun BookSwapApp(sessionViewModel: SessionViewModel = viewModel()) {
    val state by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is SessionState.Loading -> LoadingScreen()

        is SessionState.SignedOut -> AuthFlow()

        is SessionState.NeedsEmailVerification -> VerifyEmailScreen(
            email = s.email,
            onContinue = { sessionViewModel.reloadSession() },
            onSignOut = { sessionViewModel.signOut() },
        )

        is SessionState.NeedsProfile -> EditProfileScreen(
            isSetup = true,
            // On save the SessionViewModel observes the now-complete profile and
            // transitions to Ready automatically; nothing to do here.
            onSaved = {},
        )

        is SessionState.Ready -> MainFlow(
            onSignOut = { sessionViewModel.signOut() },
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthFlow() {
    val navController = rememberNavController()
    // One AuthViewModel shared across the auth screens so loading/error state is coherent.
    val authViewModel: AuthViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToForgot = { navController.navigate(Routes.FORGOT) },
                viewModel = authViewModel,
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack(Routes.LOGIN, inclusive = false) },
                viewModel = authViewModel,
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack(Routes.LOGIN, inclusive = false) },
                viewModel = authViewModel,
            )
        }
    }
}

@Composable
private fun MainFlow(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Tab.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DISCOVER,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DISCOVER) {
                DiscoveryScreen(
                    onPropose = { bookId, ownerId, title -> navController.navigateToPropose(bookId, ownerId, title) },
                )
            }

            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    onPropose = { bookId, ownerId, title -> navController.navigateToPropose(bookId, ownerId, title) },
                )
            }

            composable(Routes.INBOX) {
                InboxScreen(
                    onViewUser = { userId, name ->
                        navController.navigate("${Routes.USER_LIBRARY}/$userId/${Uri.encode(name)}")
                    },
                    onOpenExchange = { proposalId ->
                        navController.navigate("${Routes.EXCHANGE}/$proposalId")
                    },
                )
            }

            composable(Routes.LIBRARY) {
                MyLibraryScreen(
                    onAddBook = { navController.navigate(Routes.ADD_BOOK) },
                    onEditBook = { bookId -> navController.navigate("${Routes.EDIT_BOOK}/$bookId") },
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onEdit = { navController.navigate(Routes.EDIT_PROFILE) },
                    onSignOut = onSignOut,
                )
            }

            composable(Routes.ADD_BOOK) {
                AddEditBookScreen(bookId = null, onDone = { navController.popBackStack() })
            }
            composable("${Routes.EDIT_BOOK}/{bookId}") { entry ->
                AddEditBookScreen(
                    bookId = entry.arguments?.getString("bookId"),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(isSetup = false, onSaved = { navController.popBackStack() })
            }

            composable("${Routes.PROPOSE}/{bookId}/{ownerId}/{title}") { entry ->
                ProposeSwapScreen(
                    requestedBookId = entry.arguments?.getString("bookId").orEmpty(),
                    recipientId = entry.arguments?.getString("ownerId").orEmpty(),
                    requestedTitle = entry.arguments?.getString("title").orEmpty(),
                    onDone = { navController.popBackStack() },
                )
            }
            composable("${Routes.EXCHANGE}/{proposalId}") { entry ->
                ExchangeDetailScreen(
                    proposalId = entry.arguments?.getString("proposalId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenChat = { proposalId, name ->
                        // A blank name would collapse the path segment and break route
                        // matching, so send a placeholder the screen falls back on.
                        val encoded = Uri.encode(name.ifBlank { "-" })
                        navController.navigate("${Routes.CHAT}/$proposalId/$encoded")
                    },
                )
            }
            composable("${Routes.CHAT}/{proposalId}/{name}") { entry ->
                ChatScreen(
                    proposalId = entry.arguments?.getString("proposalId").orEmpty(),
                    counterpartName = entry.arguments?.getString("name")
                        ?.takeUnless { it == "-" }
                        .orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable("${Routes.USER_LIBRARY}/{userId}/{name}") { entry ->
                InterestedUserScreen(
                    userId = entry.arguments?.getString("userId").orEmpty(),
                    fallbackName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Navigate to the propose flow, URL-encoding the (free-text) requested title. */
private fun androidx.navigation.NavController.navigateToPropose(
    bookId: String,
    ownerId: String,
    title: String,
) {
    navigate("${Routes.PROPOSE}/$bookId/$ownerId/${Uri.encode(title)}")
}

private fun Tab.icon() = when (this) {
    Tab.DISCOVER -> Icons.Default.Style
    Tab.FAVORITES -> Icons.Default.Favorite
    Tab.INBOX -> Icons.Default.SwapHoriz
    Tab.LIBRARY -> Icons.AutoMirrored.Filled.MenuBook
    Tab.PROFILE -> Icons.Default.Person
}

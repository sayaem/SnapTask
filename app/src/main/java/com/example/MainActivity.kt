package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.detail.DetailResultScreen
import com.example.ui.inbox.InboxScreen
import com.example.ui.more.MoreScreen
import com.example.ui.navigation.Screen
import com.example.ui.navigation.SnapTaskBottomBar
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.saved.SavedScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SnapTaskViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SnapTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Proper Android splash screen integration via androidx.core:core-splashscreen
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedImageUri: Uri? = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else {
            null
        }

        setContent {
            MyApplicationTheme {
                SnapTaskApp(
                    viewModel = viewModel,
                    initialSharedUri = sharedImageUri
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAndStartDetector()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopDetector()
    }
}

@Composable
fun SnapTaskApp(
    viewModel: SnapTaskViewModel,
    initialSharedUri: Uri? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isOnboardingCompleted by viewModel.preferences.onboardingCompleted.collectAsState()

    val startDestination = if (isOnboardingCompleted) {
        Screen.Inbox.route
    } else {
        Screen.Onboarding.route
    }

    // Handle shared image import
    LaunchedEffect(initialSharedUri) {
        initialSharedUri?.let { uri ->
            viewModel.processScreenshot(uri) { savedId ->
                navController.navigate(Screen.Detail.createRoute(savedId))
            }
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Inbox.route,
        Screen.Saved.route,
        Screen.More.route
    )

    // Splash screen state: skipped when launched directly to process a shared image
    var showSplash by remember { mutableStateOf(initialSharedUri == null) }

    Crossfade(
        targetState = showSplash,
        animationSpec = tween(durationMillis = 280),
        label = "SplashCrossfade"
    ) { isSplash ->
        if (isSplash) {
            SplashScreen(
                onSplashFinished = { showSplash = false }
            )
        } else {
            // Using WindowInsets(0, 0, 0, 0) avoids double-consuming status bar insets.
            // Bottom padding handles navigation bar/bottom bar cleanly.
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        SnapTaskBottomBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                if (route != currentRoute) {
                                    navController.navigate(route) {
                                        popUpTo(Screen.Inbox.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    composable(Screen.Onboarding.route) {
                        OnboardingScreen(
                            onFinished = {
                                viewModel.preferences.setOnboardingCompleted(true)
                                navController.navigate(Screen.Inbox.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Inbox.route) {
                        InboxScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.Detail.createRoute(id))
                            }
                        )
                    }

                    composable(Screen.Saved.route) {
                        SavedScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.Detail.createRoute(id))
                            }
                        )
                    }

                    composable(Screen.More.route) {
                        MoreScreen(viewModel = viewModel)
                    }

                    composable(
                        route = Screen.Detail.route,
                        arguments = listOf(navArgument("screenshotId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val screenshotId = backStackEntry.arguments?.getLong("screenshotId") ?: 0L
                        DetailResultScreen(
                            screenshotId = screenshotId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

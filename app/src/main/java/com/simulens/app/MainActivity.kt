package com.simulens.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simulens.app.data.tracker.AppUsageTrackerService
import com.simulens.app.data.tracker.InterventionActionReceiver
import com.simulens.app.data.tracker.PermissionUtils
import com.simulens.app.rag.ChatLaunchViewModel
import com.simulens.app.rag.ChatSeedIntent
import com.simulens.app.rag.ChatScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simulens.app.rag.ChatViewModel
import com.simulens.app.ui.apps.AppSelectionScreen
import com.simulens.app.ui.dashboard.DashboardScreen
import com.simulens.app.ui.dashboard.DashboardViewModel
import com.simulens.app.ui.intervention.InterventionScreen
import com.simulens.app.ui.onboarding.OnboardingViewModel
import com.simulens.app.ui.onboarding.PermissionsScreen
import com.simulens.app.ui.onboarding.SplashScreen
import com.simulens.app.ui.profile.ProfileGatewayScreen
import com.simulens.app.ui.profile.ProfileScreen
import com.simulens.app.ui.profile.ProfileSetupHubScreen
import com.simulens.app.ui.theme.SimulensTheme

class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val chatLaunch: ChatLaunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A recreate (rotation, process restore) is not a new launch, so never replay the intent.
        if (savedInstanceState == null) handleLaunchIntent(intent)

        // Ensure tracking service is running if permissions are granted
        if (PermissionUtils.hasUsageStatsPermission(this)) {
            val serviceIntent = Intent(this, AppUsageTrackerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }

        setContent {
            SimulensTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    val pendingSeed by chatLaunch.pending.collectAsState()
                    val backEntry by navController.currentBackStackEntryAsState()
                    val route = backEntry?.destination?.route

                    // Hold the seed until the nav graph is ready and the user is past onboarding.
                    LaunchedEffect(pendingSeed?.id, route) {
                        if (pendingSeed == null || route == null) return@LaunchedEffect
                        chatLaunch.dropIfStale()
                        if (route == OnboardingRoutes.SPLASH || route == OnboardingRoutes.PERMISSIONS ||
                            route == OnboardingRoutes.GATEWAY || route == OnboardingRoutes.SETUP_HUB
                        ) return@LaunchedEffect
                        if (route != MainRoutes.ASSISTANT) {
                            navController.navigate(MainRoutes.ASSISTANT) { launchSingleTop = true }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = OnboardingRoutes.SPLASH
                    ) {
                        composable(OnboardingRoutes.SPLASH) {
                            SplashScreen(
                                viewModel = onboardingViewModel,
                                onNavigateToOnboarding = {
                                    navController.navigate(OnboardingRoutes.PERMISSIONS) {
                                        popUpTo(OnboardingRoutes.SPLASH) { inclusive = true }
                                    }
                                },
                                onNavigateToDashboard = {
                                    navController.navigate(MainRoutes.DASHBOARD) {
                                        popUpTo(OnboardingRoutes.SPLASH) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(OnboardingRoutes.PERMISSIONS) {
                            PermissionsScreen(
                                onNavigateNext = {
                                    navController.navigate(OnboardingRoutes.GATEWAY)
                                }
                            )
                        }

                        composable(OnboardingRoutes.GATEWAY) {
                            ProfileGatewayScreen(
                                onNavigateNext = {
                                    navController.navigate(OnboardingRoutes.SETUP_HUB)
                                }
                            )
                        }

                        composable(OnboardingRoutes.SETUP_HUB) {
                            ProfileSetupHubScreen(
                                viewModel = onboardingViewModel,
                                onComplete = {
                                    navController.navigate(MainRoutes.DASHBOARD) {
                                        popUpTo(OnboardingRoutes.SETUP_HUB) { inclusive = true }
                                    }
                                },
                                onNavigateToProfile = {
                                    navController.navigate(OnboardingRoutes.PROFILE)
                                },
                                onNavigateToAppSelection = {
                                    navController.navigate(MainRoutes.APP_SELECTION)
                                }
                            )
                        }

                        composable(OnboardingRoutes.PROFILE) {
                            ProfileScreen(
                                viewModel = onboardingViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToAppSelection = {
                                    navController.navigate(MainRoutes.APP_SELECTION)
                                },
                                onSignOut = {
                                    navController.navigate(OnboardingRoutes.GATEWAY) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(MainRoutes.DASHBOARD) {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToProfile = {
                                    navController.navigate(OnboardingRoutes.PROFILE)
                                },
                                onNavigateToRag = {
                                    navController.navigate(MainRoutes.ASSISTANT)
                                },
                                onNavigateToInsights = {
                                    navController.navigate(MainRoutes.INTERVENTION)
                                },
                                onNavigateToMode = {
                                    navController.navigate(MainRoutes.APP_SELECTION)
                                },
                                onNavigateToAppSelection = {
                                    navController.navigate(MainRoutes.APP_SELECTION)
                                }
                            )
                        }

                        composable(MainRoutes.ASSISTANT) {
                            val chatViewModel: ChatViewModel = viewModel()
                            val seed by chatLaunch.pending.collectAsState()
                            LaunchedEffect(seed?.id) {
                                seed?.let {
                                    chatViewModel.startFromSeed(it)
                                    chatLaunch.consume(it.id)
                                }
                            }
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(MainRoutes.APP_SELECTION) {
                            AppSelectionScreen(
                                onDone = {
                                    navController.popBackStack()
                                },
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(MainRoutes.INTERVENTION) {
                            InterventionScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToRag = {
                                    navController.navigate(MainRoutes.ASSISTANT)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(i: Intent?) {
        if (i == null || !i.getBooleanExtra(InterventionActionReceiver.EXTRA_OPEN_CHATBOT, false)) return
        i.removeExtra(InterventionActionReceiver.EXTRA_OPEN_CHATBOT) // consume once
        val seed = ChatSeedIntent.from(this, i)
        if (!chatLaunch.offer(seed)) return // already handled this nudge
        // Chatting is engagement, not a dismissal. Start a short break for the app so the overlay
        // does not fire again on return; reopening it shows the break in progress.
        // Don't restart a break that is already running (e.g. chat opened from the break overlay).
        if (seed.packageName.isNotBlank() && seed.packageName != packageName &&
            !InterventionActionReceiver.isBreakActive(this, seed.packageName)
        ) {
            InterventionActionReceiver.startBreak(this, seed.packageName)
        }
    }
}

private object OnboardingRoutes {
    const val SPLASH = "splash"
    const val PERMISSIONS = "permissions"
    const val GATEWAY = "gateway"
    const val SETUP_HUB = "setup_hub"
    const val PROFILE = "profile"
}

private object MainRoutes {
    const val DASHBOARD = "dashboard"
    const val ASSISTANT = "assistant"
    const val INSIGHTS = "insights"
    const val MODE = "mode"
    const val APP_SELECTION = "app_selection"
    const val INTERVENTION = "intervention"
}
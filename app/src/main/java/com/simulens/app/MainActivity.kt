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
import com.simulens.app.data.tracker.AppUsageTrackerService
import com.simulens.app.data.tracker.PermissionUtils
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure tracking service is running if permissions are granted
        if (PermissionUtils.hasUsageStatsPermission(this)) {
            val serviceIntent = Intent(this, AppUsageTrackerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }

        setContent {
            SimulensTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

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
                                }
                            )
                        }
                    }
                }
            }
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
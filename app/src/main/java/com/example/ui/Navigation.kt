package com.example.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.IncidentReport
import com.example.data.SettingsManager
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DefaultDialerPrompt
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.viewmodels.DashboardViewModel
import com.example.ui.viewmodels.DashboardViewModelFactory
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

@Composable
fun MainApp(
    startMonitoring: Boolean = false,
    incomingCall: Boolean = false,
    callerId: String = "",
    onMonitoringStarted: () -> Unit = {},
    onIncomingHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(context))
    val settingsManager = remember { SettingsManager(context) }

    val setupCompleted by settingsManager.setupCompletedFlow.collectAsState(initial = null)

    LaunchedEffect(incomingCall) {
        if (incomingCall) {
            navController.navigate("incoming_call/$callerId") { launchSingleTop = true }
            onIncomingHandled()
        }
    }

    LaunchedEffect(startMonitoring) {
        if (startMonitoring) {
            navController.navigate("active_call/$callerId") { launchSingleTop = true }
            onMonitoringStarted()
        }
    }
    
    if (setupCompleted == null) {
        return // Loading
    }

    NavHost(
        navController = navController, 
        startDestination = if (setupCompleted == true) "auth" else "setup",
        enterTransition = { slideInHorizontally(tween(500)) { it } + fadeIn(tween(500)) },
        exitTransition = { slideOutHorizontally(tween(500)) { -it } + fadeOut(tween(500)) },
        popEnterTransition = { slideInHorizontally(tween(500)) { -it } + fadeIn(tween(500)) },
        popExitTransition = { slideOutHorizontally(tween(500)) { it } + fadeOut(tween(500)) }
    ) {
        composable("setup") {
            SetupWizardScreen(
                onSetupComplete = {
                    navController.navigate("auth") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        composable("auth") {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate("dashboard") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            // Prompt to become the default phone app if we aren't already (required for screening,
            // the incoming-call screen, and call hold to function).
            DefaultDialerPrompt()
            DashboardScreen(
                viewModel = dashboardViewModel,
                onSimulateCall = {
                    navController.navigate("active_call/+91-9876543210")
                },
                onSimulateScamCall = {
                    navController.navigate("active_call/+91-11-2743-0000?scam=true")
                },
                onReportIncident = { id ->
                    navController.navigate("report/$id")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onHistoryClick = {
                    navController.navigate("history")
                }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("history") {
            CallHistoryScreen(navController = navController)
        }
        composable(
            "incoming_call/{callerId}",
            arguments = listOf(navArgument("callerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callerId") ?: "Unknown"
            IncomingCallScreen(
                callerId = callId,
                onAnswered = {
                    navController.navigate("active_call/$callId") {
                        popUpTo("incoming_call/{callerId}") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onDeclined = {
                    navController.navigate("dashboard") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            "active_call/{callerId}?scam={scam}",
            arguments = listOf(
                navArgument("callerId") { type = NavType.StringType },
                navArgument("scam") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callerId") ?: "Unknown"
            val scam = backStackEntry.arguments?.getBoolean("scam") ?: false
            ActiveCallScreen(
                incomingCallerId = callId,
                simulateScam = scam,
                onCallEnded = { endedCallerId, riskLevel, summary ->
                    val newIncident = IncidentReport(
                        callerId = endedCallerId,
                        riskLevel = riskLevel,
                        transcriptSummary = summary
                    )
                    dashboardViewModel.addIncident(newIncident)
                    // Land on the dashboard robustly even when the call flow was launched directly
                    // from the full-screen intent (so "dashboard" isn't already on the back stack).
                    navController.navigate("dashboard") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = "report/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            ReportScreen(
                incidentId = id,
                onBack = { navController.popBackStack() },
                onSubmit = {
                    dashboardViewModel.reportIncident(id)
                    navController.popBackStack()
                }
            )
        }
    }
}

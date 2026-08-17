package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.TalkScreen
import com.example.ui.screens.FaceScreen
import com.example.ui.screens.LiveSessionScreen
import com.example.ui.screens.OnboardingScreen
import com.example.data.SettingsManager
import com.example.ui.screens.WeeklyReviewScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val workRequest = PeriodicWorkRequestBuilder<com.example.domain.NotificationWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("coach_notification", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
        
        setContent {
            MyApplicationTheme {
                MainAppRouter()
            }
        }
    }
}

@Composable
fun MainAppRouter() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val isOnboardingComplete by settingsManager.getSetting("onboarding_complete", fallback = "false").collectAsState(initial = null)
    
    if (isOnboardingComplete == null) {
        // Still loading from DataStore
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        val startDest = if (isOnboardingComplete == "true") "home" else "onboarding"
        MainScreen(startDest)
    }
}

@Composable
fun MainScreen(startDestination: String = "home") {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                onFinish = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        
        composable("home") {
            var selectedTab by remember { mutableStateOf("today") }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { Text(if (selectedTab == "today") "Health Coach" else "", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                        actions = {
                            IconButton(onClick = { navController.navigate("profile") }) {
                                Icon(Icons.Filled.Person, contentDescription = "Profile")
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == "today",
                            onClick = { selectedTab = "today" },
                            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Today") },
                            label = { Text("Today") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == "dashboard",
                            onClick = { selectedTab = "dashboard" },
                            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Dashboard") },
                            label = { Text("Progress") }
                        )
                    }
                },
                floatingActionButton = {
                    if (selectedTab == "today") {
                        FloatingActionButton(
                            onClick = { navController.navigate("chat") },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Chat, contentDescription = "Ask AI Coach")
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    if (selectedTab == "today") {
                        TodayScreen(
                            onNavigateToWeeklyReview = { navController.navigate("weeklyReview") },
                            onNavigateToLiveSession = { exId -> navController.navigate("liveSession/$exId") }
                        )
                    } else {
                        DashboardScreen()
                    }
                }
            }
        }

        composable(
            "liveSession/{exId}",
            arguments = listOf(androidx.navigation.navArgument("exId") { defaultValue = "squat" }),
        ) { backStackEntry ->
            LiveSessionScreen(
                exId = backStackEntry.arguments?.getString("exId") ?: "squat",
                onBack = { navController.popBackStack() },
            )
        }

        composable("chat") {
            Scaffold(
                topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { Text("Ask Coach") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    TalkScreen()
                }
            }
        }
        
        composable("profile") {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        
        composable("weeklyReview") {
            WeeklyReviewScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyApplicationTheme { MainScreen() }
}

package dev.rouzbeh.hybridvpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.rouzbeh.hybridvpn.core.designsystem.OpenNestTheme
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceDark
import dev.rouzbeh.hybridvpn.feature.dashboard.DashboardScreen
import dev.rouzbeh.hybridvpn.feature.splash.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenNestTheme {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceDark
                ) {
                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(onTimeout = {
                                navController.navigate("dashboard") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }
                        composable("dashboard") {
                            DashboardScreen()
                        }
                    }
                }
            }
        }
    }
}
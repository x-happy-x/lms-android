package ru.mrcrubs.lms.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.mrcrubs.lms.android.ui.add.AddDownloadScreen
import ru.mrcrubs.lms.android.ui.jobs.JobsScreen
import ru.mrcrubs.lms.android.ui.settings.SettingsScreen
import ru.mrcrubs.lms.android.ui.theme.LmsTheme
import ru.mrcrubs.lms.core.LinkExtractor

class MainActivity : ComponentActivity() {
    private val container get() = (application as LmsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            LmsTheme {
                AppNavigation(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val link = when (intent?.action) {
            Intent.ACTION_SEND -> LinkExtractor.extract(
                listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                ).joinToString(" "),
            )
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { LinkExtractor.isSupportedUrl(it) }
            else -> null
        }
        if (link != null) container.incomingLinks.value = link
    }
}

private object Routes {
    const val JOBS = "jobs"
    const val SETTINGS = "settings"
    const val ADD = "add?url={url}"

    fun add(url: String? = null): String = if (url == null) "add" else "add?url=${Uri.encode(url)}"
}

@Composable
private fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val incoming by container.incomingLinks.collectAsStateWithLifecycle()

    LaunchedEffect(incoming) {
        val link = incoming ?: return@LaunchedEffect
        container.incomingLinks.value = null
        navController.navigate(Routes.add(link)) {
            popUpTo(Routes.JOBS)
            launchSingleTop = false
        }
    }

    NavHost(navController = navController, startDestination = Routes.JOBS) {
        composable(Routes.JOBS) {
            JobsScreen(
                container = container,
                onAdd = { navController.navigate(Routes.add()) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.ADD,
            arguments = listOf(navArgument("url") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            AddDownloadScreen(
                container = container,
                initialUrl = entry.arguments?.getString("url"),
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack(Routes.JOBS, inclusive = false) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}

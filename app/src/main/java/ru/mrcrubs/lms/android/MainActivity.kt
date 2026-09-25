package ru.mrcrubs.lms.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.mrcrubs.lms.android.ui.MainViewModel
import ru.mrcrubs.lms.android.ui.add.AddDownloadScreen
import ru.mrcrubs.lms.android.ui.factory
import ru.mrcrubs.lms.android.ui.jobs.JobsScreen
import ru.mrcrubs.lms.android.ui.media.MediaScreen
import ru.mrcrubs.lms.android.ui.media.ViewerScreen
import ru.mrcrubs.lms.android.ui.nodes.NodesScreen
import ru.mrcrubs.lms.android.ui.profiles.ProfilesScreen
import ru.mrcrubs.lms.android.ui.settings.SettingsScreen
import ru.mrcrubs.lms.android.ui.theme.LmsTheme
import ru.mrcrubs.lms.core.LinkExtractor

/** App-wide dependencies for composables that need them (image URLs, storage lookups). */
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }

class MainActivity : ComponentActivity() {
    private val container get() = (application as LmsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            LmsTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    AppNavigation(container)
                }
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
    const val MEDIA = "media"
    const val NODES = "nodes"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
    const val ADD = "add?url={url}"
    const val VIEWER = "viewer/{jobId}"

    fun add(url: String? = null): String = if (url == null) "add" else "add?url=${Uri.encode(url)}"
    fun viewer(jobId: String): String = "viewer/${Uri.encode(jobId)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.JOBS, "Загрузки", Icons.Filled.Download),
    Tab(Routes.MEDIA, "Медиа", Icons.Filled.PhotoLibrary),
    Tab(Routes.NODES, "Ноды", Icons.Filled.Dns),
    Tab(Routes.PROFILES, "Профили", Icons.Filled.Tune),
    Tab(Routes.SETTINGS, "Настройки", Icons.Filled.Settings),
)

@Composable
private fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(factory = factory { MainViewModel(container) })
    val snackbar = remember { SnackbarHostState() }
    val incoming by container.incomingLinks.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Poll the router while the app is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startPolling()
                Lifecycle.Event.ON_STOP -> viewModel.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPolling()
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbar.showSnackbar(text)
    }

    LaunchedEffect(incoming) {
        val link = incoming ?: return@LaunchedEffect
        container.incomingLinks.value = null
        navController.navigate(Routes.add(link)) { popUpTo(Routes.JOBS) }
    }

    fun openTab(target: String) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (TABS.any { it.route == route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        snackbarHost = { if (route != Routes.JOBS) SnackbarHost(snackbar) },
    ) { outer ->
        Box(Modifier.padding(bottom = outer.calculateBottomPadding()).consumeWindowInsets(outer)) {
            NavHost(navController = navController, startDestination = Routes.JOBS) {
                composable(Routes.JOBS) {
                    JobsScreen(
                        viewModel = viewModel,
                        snackbar = snackbar,
                        onAdd = { navController.navigate(Routes.add()) },
                        onOpenMedia = { navController.navigate(Routes.viewer(it.id)) },
                        onSettings = { openTab(Routes.SETTINGS) },
                    )
                }
                composable(Routes.MEDIA) {
                    MediaScreen(viewModel = viewModel, onOpen = { navController.navigate(Routes.viewer(it.id)) })
                }
                composable(Routes.NODES) { NodesScreen(viewModel) }
                composable(Routes.PROFILES) { ProfilesScreen(viewModel) }
                composable(Routes.SETTINGS) { SettingsScreen(container = container, onBack = null) }
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
                        onCreated = {
                            viewModel.refresh()
                            navController.popBackStack(Routes.JOBS, inclusive = false)
                        },
                    )
                }
                composable(Routes.VIEWER, arguments = listOf(navArgument("jobId") { type = NavType.StringType })) { entry ->
                    ViewerScreen(
                        viewModel = viewModel,
                        jobId = entry.arguments?.getString("jobId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

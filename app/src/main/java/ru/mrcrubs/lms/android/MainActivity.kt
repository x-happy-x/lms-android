package ru.mrcrubs.lms.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shield
import ru.mrcrubs.lms.android.ui.files.FilesScreen
import ru.mrcrubs.lms.android.ui.network.NetworkScreen
import ru.mrcrubs.lms.android.ui.common.MoreScreen
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
        if (intent?.data?.scheme == "lms" && intent.data?.host == "auth") {
            container.completeAccount(intent.data.toString()); return
        }
        // Links from other apps arrive through LinkReceiverActivity; ACTION_ADD_LINK carries them here.
        val link = when (intent?.action) {
            LinkReceiverActivity.ACTION_ADD_LINK -> intent.getStringExtra(LinkReceiverActivity.EXTRA_LINK)
            else -> LinkReceiverActivity.linkFrom(intent)
        }
        if (link != null) container.incomingLinks.value = link
    }
}

private object Routes {
    const val JOBS = "jobs"
    const val MEDIA = "media"
    const val FILES = "files"
    const val NETWORK = "network"
    const val MORE = "more"
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
    Tab(Routes.FILES, "Файлы", Icons.Filled.Folder),
    Tab(Routes.MEDIA, "Медиа", Icons.Filled.PhotoLibrary),
    Tab(Routes.NETWORK, "HomeNet", Icons.Filled.Shield),
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
    val accountResult by container.accountResult.collectAsStateWithLifecycle()
    LaunchedEffect(accountResult) { accountResult?.let { container.accountResult.value = null; viewModel.refresh(); snackbar.showSnackbar(it) } }
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

    BoxWithConstraints {
    val wide = maxWidth >= 600.dp && maxHeight >= 480.dp
    val compactTabs = TABS.filter { it.route in listOf(Routes.JOBS,Routes.FILES,Routes.MEDIA) } + Tab(Routes.MORE,"Ещё",Icons.Filled.GridView)
    val currentTop = if(route in compactTabs.map{it.route})route else Routes.MORE
    val topLevel = TABS.any { it.route==route } || route==Routes.MORE
    Scaffold(
        bottomBar = {
            if (topLevel && !wide) {
                NavigationBar {
                    compactTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTop == tab.route,
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
        Row(Modifier.padding(bottom = outer.calculateBottomPadding()).consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom=outer.calculateBottomPadding()))) {
            if(wide && topLevel) NavigationRail(modifier=Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("LMS",style=androidx.compose.material3.MaterialTheme.typography.titleLarge,modifier=Modifier.padding(vertical=20.dp))
                TABS.forEach { tab -> NavigationRailItem(selected=route==tab.route,onClick={openTab(tab.route)},icon={Icon(tab.icon,null)},label={Text(tab.label)}) }
            }
            Box(Modifier.weight(1f)) {
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
                composable(Routes.FILES) { FilesScreen(container) }
                composable(Routes.NETWORK) { NetworkScreen(container) }
                composable(Routes.MORE) { MoreScreen(onOpen={openTab(it)}) }
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
    }
}

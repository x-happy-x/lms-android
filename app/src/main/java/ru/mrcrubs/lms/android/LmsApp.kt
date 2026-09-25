package ru.mrcrubs.lms.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.mrcrubs.lms.android.data.DeviceDownloads
import ru.mrcrubs.lms.android.data.JobMonitor
import ru.mrcrubs.lms.android.data.RouterRepository
import ru.mrcrubs.lms.android.data.SettingsRepository
import ru.mrcrubs.lms.android.notify.JobCheckWorker
import ru.mrcrubs.lms.android.notify.Notifier
import ru.mrcrubs.lms.core.RouterApi
import ru.mrcrubs.lms.core.RouterConfig

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(app: Application, val scope: CoroutineScope) {
    val settings = SettingsRepository(app)
    val router = RouterRepository(settings)
    val notifier = Notifier(app)
    val monitor = JobMonitor(settings, notifier)
    val deviceDownloads = DeviceDownloads(app)

    /** Current router config (null until configured), for URL building in the UI. */
    val routerConfig: StateFlow<RouterConfig?> = settings.settings
        .map { it.routerConfig() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun api(): RouterApi? = routerConfig.value?.let(router::api)

    /** Links received via "Share", "Open with" or selected text, consumed by the navigation host. */
    val incomingLinks = MutableStateFlow<String?>(null)
}

class LmsApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, scope)
        container.notifier.createChannel()
        // Keep the background check in sync with the settings.
        scope.launch {
            container.settings.settings
                .map { Triple(it.isConfigured, it.notificationsEnabled, it.backgroundIntervalMinutes) }
                .distinctUntilChanged()
                .collect { JobCheckWorker.schedule(this@LmsApp, container.settings.current()) }
        }
    }

    /** Thumbnails and photos come from the router: add its auth header when one is configured. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val headers = container.api()?.authHeaders().orEmpty()
                    val request = chain.request().newBuilder().apply {
                        headers.forEach { (name, value) -> header(name, value) }
                    }.build()
                    chain.proceed(request)
                }
                .build()
        }
        .crossfade(true)
        .build()
}

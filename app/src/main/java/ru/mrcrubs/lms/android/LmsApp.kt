package ru.mrcrubs.lms.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.mrcrubs.lms.android.data.JobMonitor
import ru.mrcrubs.lms.android.data.RouterRepository
import ru.mrcrubs.lms.android.data.SettingsRepository
import ru.mrcrubs.lms.android.notify.JobCheckWorker
import ru.mrcrubs.lms.android.notify.Notifier

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val router = RouterRepository(settings)
    val notifier = Notifier(app)
    val monitor = JobMonitor(settings, notifier)

    /** Links received via "Share" or magnet intents, consumed by the navigation host. */
    val incomingLinks = MutableStateFlow<String?>(null)
}

class LmsApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannel()
        // Keep the background check in sync with the settings.
        scope.launch {
            container.settings.settings
                .map { Triple(it.isConfigured, it.notificationsEnabled, it.backgroundIntervalMinutes) }
                .distinctUntilChanged()
                .collect { JobCheckWorker.schedule(this@LmsApp, container.settings.current()) }
        }
    }
}

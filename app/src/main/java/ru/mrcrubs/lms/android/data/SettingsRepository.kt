package ru.mrcrubs.lms.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.mrcrubs.lms.core.RouterConfig

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val routerUrl: String = "",
    val username: String = "",
    val password: String = "",
    val notificationsEnabled: Boolean = true,
    val backgroundIntervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val sort: String = "NEWEST",
    val group: String = "STATUS",
    val gridView: Boolean = false,
    /** Links opened or shared into the app are added right away (best node, suggested type). */
    val quickAdd: Boolean = false,
    /** Limit for new downloads in bytes/s; null = unlimited. */
    val defaultSpeedLimit: Long? = null,
) {
    val isConfigured: Boolean get() = routerUrl.isNotBlank()

    fun routerConfig(): RouterConfig? = if (!isConfigured) null else RouterConfig(
        baseUrl = routerUrl,
        username = username.ifBlank { null },
        password = password.ifBlank { null },
    )

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15
        /** WorkManager does not run periodic work more often than every 15 minutes. */
        val INTERVAL_CHOICES = listOf(15, 30, 60, 180)
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ROUTER_URL = stringPreferencesKey("router_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val INTERVAL = intPreferencesKey("background_interval_minutes")
        val STATUS_SNAPSHOT = stringPreferencesKey("status_snapshot")
        val SORT = stringPreferencesKey("jobs_sort")
        val GROUP = stringPreferencesKey("jobs_group")
        val GRID = booleanPreferencesKey("jobs_grid")
        val QUICK_ADD = booleanPreferencesKey("quick_add")
        val SPEED_LIMIT = longPreferencesKey("default_speed_limit")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val previousUrl = prefs[Keys.ROUTER_URL].orEmpty()
            val updated = transform(prefs.toSettings())
            if (previousUrl != updated.routerUrl.trim()) {
                // Statuses of another router must not trigger notifications.
                prefs.remove(Keys.STATUS_SNAPSHOT)
            }
            prefs[Keys.ROUTER_URL] = updated.routerUrl.trim()
            prefs[Keys.USERNAME] = updated.username.trim()
            prefs[Keys.PASSWORD] = updated.password
            prefs[Keys.NOTIFICATIONS] = updated.notificationsEnabled
            prefs[Keys.INTERVAL] = updated.backgroundIntervalMinutes
            prefs[Keys.SORT] = updated.sort
            prefs[Keys.GROUP] = updated.group
            prefs[Keys.GRID] = updated.gridView
            prefs[Keys.QUICK_ADD] = updated.quickAdd
            val limit = updated.defaultSpeedLimit?.takeIf { it > 0 }
            if (limit == null) prefs.remove(Keys.SPEED_LIMIT) else prefs[Keys.SPEED_LIMIT] = limit
        }
    }

    suspend fun statusSnapshot(): String? = context.dataStore.data.first()[Keys.STATUS_SNAPSHOT]

    suspend fun saveStatusSnapshot(value: String) {
        context.dataStore.edit { it[Keys.STATUS_SNAPSHOT] = value }
    }

    suspend fun clearStatusSnapshot() {
        context.dataStore.edit { it.remove(Keys.STATUS_SNAPSHOT) }
    }

    private fun Preferences.toSettings() = AppSettings(
        routerUrl = this[Keys.ROUTER_URL].orEmpty(),
        username = this[Keys.USERNAME].orEmpty(),
        password = this[Keys.PASSWORD].orEmpty(),
        notificationsEnabled = this[Keys.NOTIFICATIONS] ?: true,
        backgroundIntervalMinutes = this[Keys.INTERVAL] ?: AppSettings.DEFAULT_INTERVAL_MINUTES,
        sort = this[Keys.SORT] ?: "NEWEST",
        group = this[Keys.GROUP] ?: "STATUS",
        gridView = this[Keys.GRID] ?: false,
        quickAdd = this[Keys.QUICK_ADD] ?: false,
        defaultSpeedLimit = this[Keys.SPEED_LIMIT],
    )
}

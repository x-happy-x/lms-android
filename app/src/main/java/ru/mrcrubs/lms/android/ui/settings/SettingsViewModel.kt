package ru.mrcrubs.lms.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.android.data.AppSettings
import ru.mrcrubs.lms.core.RouterApi

data class SettingsUiState(
    val loaded: Boolean = false,
    val form: AppSettings = AppSettings(),
    val testing: Boolean = false,
    val accountBusy: Boolean = false,
    val accountMessage: String? = null,
    val testResult: String? = null,
    val testOk: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = container.settings.current()
            _state.update { it.copy(loaded = true, form = current) }
        }
    }

    fun edit(transform: (AppSettings) -> AppSettings) =
        _state.update { it.copy(form = transform(it.form), testResult = null, saved = false, error = null) }

    fun testConnection() {
        val config = _state.value.form.routerConfig()
        if (config == null || RouterApi.normalizeBaseUrl(config.baseUrl) == null) {
            _state.update { it.copy(error = "Укажите корректный адрес роутера") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null, error = null) }
            try {
                val saved = container.settings.current()
                val sameRouter = RouterApi.normalizeBaseUrl(config.baseUrl) == RouterApi.normalizeBaseUrl(saved.routerUrl)
                val version = container.router.testConnection(config.copy(bearerToken = if(sameRouter) saved.bearerToken else null))
                _state.update { it.copy(testing = false, testOk = true, testResult = "Подключено, версия $version") }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(testing = false, testOk = false, testResult = ex.message ?: "Ошибка") }
            }
        }
    }

    fun loginAccount(open: (String) -> Unit) {

        viewModelScope.launch {

            _state.update { it.copy(accountBusy = true, accountMessage = null) }

            try {

                val form = _state.value.form

                require(RouterApi.normalizeBaseUrl(form.routerUrl) != null) { "Укажите корректный адрес LMS" }

                container.settings.update { form }

                open(container.account.begin())

            } catch (error: Exception) { _state.update { it.copy(accountMessage = error.message ?: "Не удалось открыть Account") } }

            finally { _state.update { it.copy(accountBusy = false) } }

        }

    }

    fun logoutAccount() { viewModelScope.launch {

        _state.update { it.copy(accountBusy = true) }

        try { container.account.logout() }

        catch (error: Exception) { _state.update { it.copy(accountMessage = error.message) } }

        finally { _state.update { it.copy(accountBusy = false) } }

    } }



    fun save() {
        val form = _state.value.form
        if (form.routerUrl.isNotBlank() && RouterApi.normalizeBaseUrl(form.routerUrl) == null) {
            _state.update { it.copy(error = "Некорректный адрес роутера") }
            return
        }
        viewModelScope.launch {
            container.settings.update { form }
            _state.update { it.copy(saved = true) }
        }
    }
}

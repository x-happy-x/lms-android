package ru.mrcrubs.lms.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.mrcrubs.lms.core.*

class NativeAccount(private val settings: SettingsRepository) {
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
    suspend fun begin(): String = withContext(Dispatchers.IO) {
        val config = settings.current().routerConfig() ?: throw ApiException("Сначала сохраните адрес LMS")
        val auth = RouterApi(config).authConfig()
        require(auth.enabled) { "Вход через Account не включён на этом LMS" }
        val pending = AccountPkce.create(auth.accountOrigin, RouterApi.normalizeBaseUrl(config.baseUrl).toString().trimEnd('/'))
        settings.secure.write("pending", RouterApi.json.encodeToString(PendingAccount.serializer(), pending))
        AccountPkce.authorize(pending)
    }
    suspend fun complete(callback: String): String = withContext(Dispatchers.IO) {
        val pending = settings.secure.read("pending")?.let { RouterApi.json.decodeFromString(PendingAccount.serializer(), it) }
            ?: throw ApiException("Начните вход из настроек LMS")
        val current = settings.current().routerConfig() ?: throw ApiException("Адрес LMS не настроен")
        require(RouterApi.normalizeBaseUrl(current.baseUrl).toString().trimEnd('/') == pending.router) { "Адрес LMS изменился. Начните вход снова" }
        val code = AccountPkce.code(callback, pending)
        settings.secure.write("pending", null)
        val answer = request(pending.account + "/api/extensions/token", "POST", buildJsonObject {
            put("grant_type", "authorization_code");put("client_id", AccountPkce.CLIENT);put("redirect_uri", AccountPkce.REDIRECT)
            put("code", code);put("code_verifier", pending.verifier)
        }.toString())
        val token = answer["access_token"]?.jsonPrimitive?.content.orEmpty()
        require(token.matches(Regex("kat_[0-9a-f]{48}")) && answer["token_type"]?.jsonPrimitive?.content == "Bearer") { "Некорректный ответ Account" }
        try {
            val expires = answer["expires_in"]?.jsonPrimitive?.longOrNull ?: 0
            require(expires > 0) { "Некорректный срок доступа" }
            val session = RouterApi(current.copy(bearerToken = token)).authSession()
            require(session.enabled && !session.user?.login.isNullOrBlank()) { "LMS не подтвердил вход" }
            val user = session.user!!
            val saved = AccountCredentials(pending.account, pending.router, token, System.currentTimeMillis() + expires.coerceAtMost(30 * 86400) * 1000, user.login, user.role)
            val previous = settings.credentials()
            settings.saveCredentials(saved)
            if (previous != null && previous.token != token) runCatching { request(previous.account+"/api/extensions/session", "DELETE", token=previous.token) }
            user.login
        } catch (error: Exception) {
            runCatching { request(pending.account+"/api/extensions/session", "DELETE", token=token) }
            throw error
        }
    }
    suspend fun logout() = withContext(Dispatchers.IO) {
        val saved = settings.credentials()
        settings.secure.write("pending", null)
        settings.saveCredentials(null)
        if (saved != null) request(saved.account+"/api/extensions/session", "DELETE", token=saved.token)
        Unit
    }
    private fun request(url: String, method: String, body: String? = null, token: String? = null): JsonObject {
        val builder = Request.Builder().url(url).header("Content-Type", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        builder.method(method, body?.toRequestBody("application/json".toMediaType()))
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("Account: HTTP ${response.code}", response.code)
            return if(raw.isBlank()) buildJsonObject {} else RouterApi.json.decodeFromString(JsonObject.serializer(), raw)
        }
    }
}

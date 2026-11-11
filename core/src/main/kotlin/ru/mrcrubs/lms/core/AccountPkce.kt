package ru.mrcrubs.lms.core

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The authorization server sees only a challenge; the verifier stays on this device. */
object AccountPkce {
    const val CLIENT = "lms-android"
    const val REDIRECT = "lms://auth/callback"
    private fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun create(account: String, router: String, now: Long = System.currentTimeMillis()): PendingAccount {
        val origin = account.toHttpUrlOrNull() ?: throw ApiException("Некорректный адрес Account")
        require(origin.username.isEmpty() && origin.password.isEmpty() && origin.encodedPath == "/" && origin.query == null && origin.fragment == null) { "Нужен адрес Account без пути и пароля" }
        require(origin.isHttps || origin.host in listOf("127.0.0.1", "localhost")) { "Account должен использовать HTTPS" }
        return PendingAccount(origin.toString().trimEnd('/'), router, random(), random(), now)
    }

    fun authorize(pending: PendingAccount): String = (pending.account + "/extensions/authorize").toHttpUrlOrNull()!!.newBuilder()
        .addQueryParameter("client_id", CLIENT).addQueryParameter("redirect_uri", REDIRECT)
        .addQueryParameter("state", pending.state).addQueryParameter("code_challenge", challenge(pending.verifier))
        .addQueryParameter("code_challenge_method", "S256").build().toString()

    fun code(callback: String, pending: PendingAccount, now: Long = System.currentTimeMillis()): String {
        val uri = URI(callback)
        require(uri.scheme == "lms" && uri.host == "auth" && uri.rawPath == "/callback" && uri.fragment == null && uri.userInfo == null && uri.port == -1) { "Неверный адрес ответа Account" }
        require(now - pending.createdAt in 0..600_000) { "Время входа истекло. Начните снова" }
        val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { pair ->
            val parts = pair.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }
        require(query.count { it.first == "state" } == 1 && query.single { it.first == "state" }.second == pending.state) { "Account вернул неверный state" }
        require(query.none { it.first == "error" }) { "Вход отменён в Account" }
        require(query.count { it.first == "code" } == 1) { "Account не вернул код входа" }
        return query.single { it.first == "code" }.second.also { require(it.matches(Regex("[A-Za-z0-9_-]{32,128}"))) { "Некорректный код Account" } }
    }
}

@Serializable data class PendingAccount(val account: String, val router: String, val state: String, val verifier: String, val createdAt: Long)
@Serializable data class AccountCredentials(val account: String, val router: String, val token: String, val expiresAt: Long, val login: String, val role: String)

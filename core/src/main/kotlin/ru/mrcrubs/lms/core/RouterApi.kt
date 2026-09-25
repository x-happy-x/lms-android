package ru.mrcrubs.lms.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val statusCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

/** Where the router is and how to reach it. */
data class RouterConfig(
    val baseUrl: String,
    /** Optional HTTP Basic auth, e.g. when the router UI is published through a proxy with auth. */
    val username: String? = null,
    val password: String? = null,
)

/**
 * Blocking client for the router UI API (`/api/ui/...` of lms-client).
 * Call from a background dispatcher.
 */
class RouterApi(
    private val config: RouterConfig,
    client: OkHttpClient = defaultClient(),
) {
    private val http = client
    private val base: HttpUrl = normalizeBaseUrl(config.baseUrl)
        ?: throw ApiException("Некорректный адрес роутера: ${config.baseUrl}")

    fun health(): String = get("system/health", JsonObject.serializer())["status"]?.jsonPrimitive?.content ?: "unknown"

    fun version(): String = get("system/version", JsonObject.serializer())["version"]?.jsonPrimitive?.content ?: ""

    fun jobs(activeOnly: Boolean = false): List<Job> =
        get("jobs?active=$activeOnly", ListSerializer(Job.serializer()))

    fun job(id: String): Job = get("jobs/${encode(id)}", Job.serializer())

    fun preflight(url: String): PreflightResponse =
        post("jobs/preflight", mapBody("url" to url), PreflightResponse.serializer())

    fun createJob(request: CreateJobRequest): Job =
        post("jobs", json.encodeToString(CreateJobRequest.serializer(), request), Job.serializer())

    fun pause(id: String): Job = post("jobs/${encode(id)}/pause", "{}", Job.serializer())
    fun resume(id: String): Job = post("jobs/${encode(id)}/resume", "{}", Job.serializer())
    fun cancel(id: String): Job = post("jobs/${encode(id)}/cancel", "{}", Job.serializer())
    fun retry(id: String): Job = post("jobs/${encode(id)}/retry", "{}", Job.serializer())

    fun updateJobUrl(id: String, url: String): Job =
        post("jobs/${encode(id)}/url", mapBody("url" to url), Job.serializer())

    fun moveJob(id: String, request: MoveJobRequest): Job =
        post("jobs/${encode(id)}/move", json.encodeToString(MoveJobRequest.serializer(), request), Job.serializer())

    fun createNode(request: NodeRequest): NodeItem =
        post("nodes", json.encodeToString(NodeRequest.serializer(), request), NodeItem.serializer())

    fun updateNode(id: String, request: NodeRequest): NodeItem = execute(
        request("nodes/${encode(id)}")
            .put(json.encodeToString(NodeRequest.serializer(), request).toRequestBody(JSON_MEDIA))
            .build(),
        NodeItem.serializer(),
    )

    fun createProfile(request: ProfileRequest): Profile =
        post("profiles", json.encodeToString(ProfileRequest.serializer(), request), Profile.serializer())

    /** URL of the job's file streamed through the router (Range supported); `inline` for viewing. */
    fun fileUrl(jobId: String, inline: Boolean = false): String =
        base.toString() + "api/ui/jobs/${encode(jobId)}/file" + if (inline) "?inline=1" else ""

    /** JPEG thumbnail of a finished photo/video (404 when the node cannot make one). */
    fun previewUrl(jobId: String): String = base.toString() + "api/ui/jobs/${encode(jobId)}/preview"

    /** Headers to add when another component (player, image loader, DownloadManager) fetches router URLs. */
    fun authHeaders(): Map<String, String> =
        if (config.username.isNullOrEmpty()) emptyMap()
        else mapOf("Authorization" to Credentials.basic(config.username, config.password.orEmpty()))

    fun nodes(enabledOnly: Boolean = false): List<NodeItem> =
        get("nodes?enabled=$enabledOnly", ListSerializer(NodeItem.serializer()))

    fun profiles(enabledOnly: Boolean = false): List<Profile> =
        get("profiles?enabled=$enabledOnly", ListSerializer(Profile.serializer()))

    fun storageTargets(nodeId: String, requiredBytes: Long? = null): StorageTargetsResponse {
        val suffix = if (requiredBytes != null && requiredBytes > 0) "?requiredBytes=$requiredBytes" else ""
        return get("nodes/${encode(nodeId)}/storage/targets$suffix", StorageTargetsResponse.serializer())
    }

    private fun <T> get(path: String, serializer: KSerializer<T>): T =
        execute(request(path).get().build(), serializer)

    private fun <T> post(path: String, body: String, serializer: KSerializer<T>): T =
        execute(request(path).post(body.toRequestBody(JSON_MEDIA)).build(), serializer)

    private fun request(path: String): Request.Builder {
        val url = (base.toString() + "api/ui/" + path).toHttpUrlOrNull()
            ?: throw ApiException("Некорректный путь: $path")
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (!config.username.isNullOrEmpty()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password.orEmpty()))
        }
        return builder
    }

    private fun <T> execute(request: Request, serializer: KSerializer<T>): T {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(errorMessage(text) ?: "HTTP ${response.code}", response.code)
                }
                return try {
                    json.decodeFromString(serializer, text)
                } catch (ex: Exception) {
                    throw ApiException("Неожиданный ответ роутера", response.code, ex)
                }
            }
        } catch (ex: IOException) {
            throw ApiException("Роутер недоступен: ${ex.message ?: ex.javaClass.simpleName}", null, ex)
        }
    }

    private fun errorMessage(body: String): String? = try {
        json.decodeFromString(JsonObject.serializer(), body)["error"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    private fun mapBody(vararg pairs: Pair<String, String>): String =
        json.encodeToString(JsonObject.serializer(), JsonObject(pairs.associate { (k, v) ->
            k to kotlinx.serialization.json.JsonPrimitive(v)
        }))

    private fun encode(segment: String): String =
        java.net.URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Accepts "192.168.1.1:8082", "http://router:8082/", "https://host/lms" and
         * returns the base with a trailing slash; plain host input defaults to http.
         */
        fun normalizeBaseUrl(raw: String): HttpUrl? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            val path = url.encodedPath.trimEnd('/').removeSuffix("/api/ui").removeSuffix("/api")
            return url.newBuilder().encodedPath("$path/").query(null).fragment(null).build()
        }
    }
}

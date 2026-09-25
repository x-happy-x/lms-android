package ru.mrcrubs.lms.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouterApiTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun api(username: String? = null) =
        RouterApi(RouterConfig(server.url("/").toString(), username, "secret"))

    @Test
    fun parsesJobsIgnoringUnknownFieldsAndStatuses() {
        server.enqueue(MockResponse().setBody("""
            [{"id":"1","type":"DIRECT","url":"https://example.com/a.iso","status":"RUNNING",
              "percent":42.5,"speedBytes":1048576,"etaSeconds":30,"somethingNew":true},
             {"id":"2","type":"TORRENT","url":"magnet:?xt=urn:btih:abc&dn=Show","status":"WEIRD"}]
        """.trimIndent()))

        val jobs = api().jobs(activeOnly = true)

        assertEquals(2, jobs.size)
        assertEquals(JobStatus.RUNNING, jobs[0].status)
        assertEquals(42.5, jobs[0].percent)
        assertEquals("a.iso", jobs[0].title)
        assertEquals(JobStatus.UNKNOWN, jobs[1].status)
        assertEquals("Show", jobs[1].title)
        assertEquals("/api/ui/jobs?active=true", server.takeRequest().path)
    }

    @Test
    fun createJobSendsExpectedBody() {
        server.enqueue(MockResponse().setBody("""{"id":"9","status":"QUEUED","url":"magnet:?xt=urn:btih:abc"}"""))

        val job = api().createJob(CreateJobRequest(type = "TORRENT", url = "magnet:?xt=urn:btih:abc", nodeId = "n1"))

        assertEquals("9", job.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/ui/jobs", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"type\":\"TORRENT\""), body)
        assertTrue(body.contains("\"nodeId\":\"n1\""), body)
        assertTrue(body.contains("\"startImmediately\":true"), body)
        assertTrue(!body.contains("storagePath"), body)
    }

    @Test
    fun actionsUseJobPath() {
        server.enqueue(MockResponse().setBody("""{"id":"a b","status":"PAUSED"}"""))
        api().pause("a b")
        val request = server.takeRequest()
        assertEquals("/api/ui/jobs/a%20b/pause", request.path)
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun routerErrorMessageIsSurfaced() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"url must start with http:// or https://"}"""))

        val ex = assertThrows<ApiException> { api().preflight("magnet:?xt=urn:btih:abc") }

        assertEquals("url must start with http:// or https://", ex.message)
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun sendsBasicAuthWhenConfigured() {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        assertEquals("ok", api(username = "me").health())
        assertTrue(server.takeRequest().getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun unreachableRouterGivesApiException() {
        val port = server.port
        server.shutdown()
        val ex = assertThrows<ApiException> { RouterApi(RouterConfig("http://127.0.0.1:$port")).health() }
        assertTrue(ex.message!!.startsWith("Роутер недоступен"))
        assertNull(ex.statusCode)
        server = MockWebServer().also { it.start() }
    }

    @Test
    fun normalizesBaseUrl() {
        assertEquals("http://192.168.1.1:8082/", RouterApi.normalizeBaseUrl("192.168.1.1:8082")!!.toString())
        assertEquals("https://lms.example.ru/", RouterApi.normalizeBaseUrl("https://lms.example.ru/api/ui/")!!.toString())
        assertEquals("http://host/prefix/", RouterApi.normalizeBaseUrl("http://host/prefix")!!.toString())
        assertNull(RouterApi.normalizeBaseUrl("  "))
    }
}

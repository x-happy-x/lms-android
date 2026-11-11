package ru.mrcrubs.lms.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NativeFeaturesTest {
    @Test fun pkceUsesS256AndRejectsWrongExpiredOrDuplicateCallbackData() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",AccountPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val pending=AccountPkce.create("https://account.example","https://lms.example",1000)
        val code="a".repeat(43)
        val valid="lms://auth/callback?state=${pending.state}&code=$code"
        assertEquals(code,AccountPkce.code(valid,pending,2000))
        assertTrue(AccountPkce.authorize(pending).contains("code_challenge_method=S256"))
        assertThrows<IllegalArgumentException>{AccountPkce.code(valid.replace("auth/","evil/"),pending,2000)}
        assertThrows<IllegalArgumentException>{AccountPkce.code(valid+"&state=${pending.state}",pending,2000)}
        assertThrows<IllegalArgumentException>{AccountPkce.code(valid,pending,601001)}
        assertThrows<IllegalArgumentException>{AccountPkce.code(valid.replace(pending.state,"wrong"),pending,2000)}
        assertThrows<IllegalArgumentException>{AccountPkce.create("http://account.example","https://lms.example")}
        assertThrows<IllegalArgumentException>{AccountPkce.create("https://user:pass@account.example","https://lms.example")}
    }
    @Test fun credentialsAreScopedToRouterOriginAndBearerTakesPrecedence() {
        val api=RouterApi(RouterConfig("https://lms.example","basic","password","fixture-token"))
        assertEquals(mapOf("Authorization" to "Bearer fixture-token"),api.headersFor("https://lms.example/api/ui/jobs"))
        assertTrue(api.headersFor("https://cdn.example/poster.jpg").isEmpty())
        assertTrue(api.headersFor("http://lms.example/poster.jpg").isEmpty())
        assertTrue(api.headersFor("https://lms.example:444/poster.jpg").isEmpty())
    }
    @Test fun playlistSnapshotAndQualitySurviveWireFormatWithoutInventingChildProgress() {
        val job=RouterApi.json.decodeFromString(Job.serializer(),"""{"id":"p","status":"RUNNING","percent":30,"playlist":{"id":"list","url":"https://course.test/list","title":"Course","entryCount":72,"entries":[{"url":"https://course.test/one","title":"Lesson"}]},"ytdlpOptions":{"playlist":true,"maxHeight":720}}""")
        assertEquals("Course",job.title)
        assertEquals(72,job.playlist?.entryCount)
        assertEquals(720,job.ytdlpOptions?.maxHeight)
        assertEquals(30.0,job.percent)
        assertEquals("Текущий ролик · 30%",Format.jobProgress(job))
        assertEquals("30%",Format.jobProgress(job.copy(ytdlpOptions=YtdlpOptions(playlist=false))))
        assertEquals(1,job.playlist?.entries?.size)
        val done=job.copy(status=JobStatus.DONE,outputPath="/downloads/playlist-p")
        assertFalse(done.isViewableMedia)
        assertEquals("Course.zip",done.downloadFileName)
        assertEquals("100%",Format.jobProgress(done))
    }
    @Test fun filesUseEncodedRootAndRelativePathAndFixedMutationEndpoints() {
        MockWebServer().use { server->
            server.start();val api=RouterApi(RouterConfig(server.url("/").toString(),bearerToken="fixture-token"))
            server.enqueue(MockResponse().setBody("""{"root":"/mnt/disk8tb","path":"Lessons","entries":[{"name":"a.mp4","path":"Lessons/a.mp4","directory":false,"sizeBytes":1024}]}"""))
            val files=api.files("node/one","/mnt/disk8tb","Lessons & tests")
            val request=server.takeRequest();assertEquals("/api/ui/nodes/node%2Fone/files",request.requestUrl?.encodedPath);assertEquals("Lessons & tests",request.requestUrl?.queryParameter("path"));assertEquals("Bearer fixture-token",request.getHeader("Authorization"));assertEquals(1024L,files.entries[0].sizeBytes)
            server.enqueue(MockResponse().setBody("""{"path":"Lessons/new"}"""));assertEquals("Lessons/new",api.mutateFile("node","mkdir","/mnt/disk8tb","Lessons","new"));assertEquals("/api/ui/nodes/node/files/mkdir",server.takeRequest().path)
            assertThrows<IllegalArgumentException>{api.mutateFile("node","exec","/mnt/disk8tb","",null)}
        }
    }
}

package ru.mrcrubs.lms.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JobPlannerTest {
    private val preflight = PreflightResponse(
        bestNodeId = "n2",
        nodes = listOf(
            PreflightNode(nodeId = "n1", nodeName = "Offline", status = "offline", statusText = "timeout"),
            PreflightNode(
                nodeId = "n2", nodeName = "Fast", status = "online",
                supportedTypes = listOf("DIRECT", "ARIA2C"), recommendedType = "ARIA2C", defaultStoragePath = "/d",
            ),
            PreflightNode(
                nodeId = "n3", nodeName = "Tools", status = "online",
                supportedTypes = listOf("DIRECT", "YTDLP"), recommendedType = "DIRECT",
            ),
        ),
    )

    @Test
    fun `best node keeps the suggested type`() {
        assertEquals(JobPlan("n2", "Fast", "DIRECT", "/d"), JobPlanner.plan(preflight, "https://x.ru/a.zip"))
    }

    @Test
    fun `another node is used when the best one lacks the type`() {
        val plan = JobPlanner.plan(preflight, "https://www.youtube.com/watch?v=1")
        assertEquals("n3", plan.nodeId)
        assertEquals("YTDLP", plan.type)
        assertNull(plan.storagePath)
    }

    @Test
    fun `falls back to the node recommendation`() {
        assertEquals("ARIA2C", JobPlanner.plan(preflight, "https://x.ru/a.zip", "TORRENT").type)
    }

    @Test
    fun `no usable node explains why`() {
        val error = assertThrows<ApiException> {
            JobPlanner.plan(PreflightResponse(nodes = listOf(preflight.nodes[0])), "https://x.ru/a.zip")
        }
        assertEquals("timeout", error.message)
    }
}

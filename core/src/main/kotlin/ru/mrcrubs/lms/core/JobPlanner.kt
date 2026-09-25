package ru.mrcrubs.lms.core

/** Node, download type and folder picked for a link without asking the user. */
data class JobPlan(
    val nodeId: String,
    val nodeName: String,
    val type: String,
    val storagePath: String?,
)

/** Picks where a link goes for "add without confirmation" (same rules as the browser extension). */
object JobPlanner {
    /**
     * Uses the router's best node when it supports the type suggested for the link, else another
     * node that does, else the best usable node with its own recommendation. Nodes that errored
     * or support nothing are skipped.
     */
    fun plan(preflight: PreflightResponse, url: String, wantedType: String = LinkExtractor.suggestType(url)): JobPlan {
        val usable = preflight.nodes.filter { it.supportedTypes.isNotEmpty() && it.error == null }
        if (usable.isEmpty()) {
            val reason = preflight.nodes.firstNotNullOfOrNull { node ->
                node.error ?: node.statusText.takeIf { node.status != "online" && it.isNotBlank() }
            }
            throw ApiException(reason ?: "Нет нод, которые могут скачать эту ссылку")
        }
        val pool = usable.filter { wantedType in it.supportedTypes }.ifEmpty { usable }
        val node = pool.firstOrNull { it.nodeId == preflight.bestNodeId } ?: pool.first()
        val type = when {
            wantedType in node.supportedTypes -> wantedType
            node.recommendedType != null -> node.recommendedType
            else -> node.supportedTypes.first()
        }
        return JobPlan(node.nodeId, node.nodeName.ifBlank { node.nodeId }, type, node.defaultStoragePath?.takeIf { it.isNotBlank() })
    }
}

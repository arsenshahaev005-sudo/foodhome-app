package market.foodhome.app.media

data class MediaRequest(
    val acceptedTypes: List<String>,
    val captureEnabled: Boolean,
    val allowMultiple: Boolean,
)

enum class VisualMediaKind {
    Images,
    Videos,
    ImagesAndVideos,
    Documents,
}

data class ResolvedMediaRequest(
    val acceptedTypes: List<String>,
    val kind: VisualMediaKind,
    val offerCamera: Boolean,
    val allowMultiple: Boolean,
)

object MediaRequestPolicy {
    fun resolve(request: MediaRequest): ResolvedMediaRequest {
        val normalized = request.acceptedTypes
            .asSequence()
            .map(String::trim)
            .map(String::lowercase)
            .filter(String::isNotEmpty)
            .distinct()
            .take(8)
            .toList()
            .ifEmpty { listOf("image/*") }
        val acceptsImages = normalized.any { it == "*/*" || it.startsWith("image/") }
        val acceptsVideos = normalized.any { it == "*/*" || it.startsWith("video/") }
        val hasUnsupported = normalized.any {
            it != "*/*" && !it.startsWith("image/") && !it.startsWith("video/")
        }
        val kind = when {
            hasUnsupported -> VisualMediaKind.Documents
            acceptsImages && acceptsVideos -> VisualMediaKind.ImagesAndVideos
            acceptsVideos -> VisualMediaKind.Videos
            acceptsImages -> VisualMediaKind.Images
            else -> VisualMediaKind.Documents
        }
        return ResolvedMediaRequest(
            acceptedTypes = normalized,
            kind = kind,
            offerCamera = acceptsImages && !request.allowMultiple,
            allowMultiple = request.allowMultiple,
        )
    }
}

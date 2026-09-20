package market.foodhome.app.media

import java.util.Locale

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
    // HTML accept permits extensions; Android's picker only accepts MIME types.
    // Keep the business-critical formats stable across Android/OEM MIME databases.
    private val extensionTypes = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "webp" to "image/webp", "heif" to "image/heif", "heic" to "image/heic",
        "gif" to "image/gif", "pdf" to "application/pdf", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )
    private val mimeType = Regex("(?:[a-z0-9!#$&^_.+-]+)/(?:[a-z0-9!#$&^_.+-]+|\\*)")

    fun resolve(
        request: MediaRequest,
        mimeTypeForExtension: (String) -> String? = { null },
    ): ResolvedMediaRequest {
        val normalized = request.acceptedTypes
            .asSequence()
            .flatMap { it.split(',').asSequence() }
            .map(String::trim)
            .map { it.lowercase(Locale.ROOT) }
            .filter(String::isNotEmpty)
            .map { type ->
                if (type.startsWith('.')) {
                    val extension = type.drop(1)
                    extensionTypes[extension] ?: mimeTypeForExtension(extension) ?: "*/*"
                } else type
            }
            .map { if (it == "*/*" || mimeType.matches(it)) it else "*/*" }
            .distinct()
            .toList()
            // accept is a UI hint, not file validation. Unknown/empty filters must
            // not make all documents unselectable. Server validation stays authoritative.
            .let { if (it.isEmpty() || "*/*" in it) listOf("*/*") else it }
        val acceptsImages = normalized.any { it.startsWith("image/") }
        val acceptsVideos = normalized.any { it.startsWith("video/") }
        val hasUnsupported = normalized.any {
            !it.startsWith("image/") && !it.startsWith("video/")
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

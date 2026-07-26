package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.app.bsky.video.JobStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.videoupload.VideoUploadError
import timber.log.Timber
import java.io.File

/** `app.bsky.video.uploadVideo`'s response envelope. */
@kotlinx.serialization.Serializable
internal data class UploadVideoResponseBody(
    val jobStatus: JobStatus,
)

/** Either the accepted job, or why the upload was refused. */
internal sealed interface UploadOutcome {
    data class Accepted(
        val jobId: String,
    ) : UploadOutcome

    data class Failed(
        val error: VideoUploadError,
    ) : UploadOutcome
}

/**
 * Sends the compressed file to `app.bsky.video.uploadVideo`.
 *
 * **Raw Ktor, deliberately.** This is the one call in the app that does not go
 * through the atproto SDK, for two reasons that both apply:
 *
 * 1. `XrpcClient.procedure` exposes no upload-progress callback, and a ~100 MB
 *    body without a progress bar is not shippable.
 * 2. The generated `VideoService.uploadVideo` takes `NoXrpcParams`, so it
 *    cannot send the `did` and `name` query parameters the service requires —
 *    they are absent from the published lexicon and known only from the
 *    reference client.
 *
 * Auth is a **plain bearer**, not DPoP: the service-auth JWT is presented
 * directly. That is a relief rather than an oversight — it means this leg does
 * not have to reproduce the OAuth DPoP proof machinery against a foreign host.
 */
internal class VideoUploader(
    private val httpClient: HttpClient,
    private val serviceAuthProvider: ServiceAuthProvider,
    private val json: Json,
) {
    suspend fun upload(
        file: File,
        did: String,
        onProgress: (Float) -> Unit,
    ): UploadOutcome {
        val token = serviceAuthProvider.videoServiceToken()

        val response =
            httpClient.post("$VIDEO_SERVICE_BASE_URL/xrpc/app.bsky.video.uploadVideo") {
                parameter("did", did)
                parameter("name", file.name)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(StreamedFileBody(file))
                onUpload { sent, total ->
                    if (total != null && total > 0) onProgress(sent.toFloat() / total)
                }
            }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            Timber.tag(TAG).w("uploadVideo rejected status=%d", response.status.value)
            return UploadOutcome.Failed(
                VideoUploadError.UploadFailed(response.status.value, body),
            )
        }

        val parsed =
            runCatching { json.decodeFromString<UploadVideoResponseBody>(response.body<String>()) }
                .getOrElse { cause ->
                    Timber.tag(TAG).w(cause, "uploadVideo response unparseable")
                    return UploadOutcome.Failed(VideoUploadError.UploadFailed(null, cause.message))
                }

        return UploadOutcome.Accepted(parsed.jobStatus.jobId)
    }

    private companion object {
        const val TAG = "VideoUpload"
    }
}

/**
 * Streams [file] as the request body with an explicit `Content-Length`.
 *
 * Written out rather than using a convenience helper because both properties
 * are load-bearing. Reading the file into memory would hold up to 100MB, and a
 * chunked body would omit the length the service requires — which would also
 * leave Ktor's `onUpload` with no total to report progress against.
 */
private class StreamedFileBody(
    private val file: File,
) : OutgoingContent.WriteChannelContent() {
    override val contentType: ContentType = ContentType.Video.MP4
    override val contentLength: Long = file.length()

    override suspend fun writeTo(channel: ByteWriteChannel) {
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                channel.writeFully(buffer, 0, read)
            }
        }
    }
}

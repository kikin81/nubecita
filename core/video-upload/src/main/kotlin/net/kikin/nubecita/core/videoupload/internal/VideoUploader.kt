package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.app.bsky.video.JobStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.content.LocalFileContent
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import net.kikin.nubecita.core.videoupload.VideoUploadError
import timber.log.Timber
import java.io.File

/**
 * `app.bsky.video.uploadVideo`'s response envelope, as **declared** by the
 * lexicon. The live service does not always use it — see [parseJobStatus].
 */
@kotlinx.serialization.Serializable
internal data class UploadVideoResponseBody(
    val jobStatus: JobStatus,
)

/**
 * Read a [JobStatus] from an uploadVideo response, accepting either shape.
 *
 * The published lexicon declares `{"jobStatus": {…}}`, but the live service
 * returns a **bare** JobStatus. Verified against production on 2026-07-25:
 * a successful upload and a 409 both come back unwrapped. Accepting either
 * costs one branch and means a future move back to the declared shape does not
 * break us.
 */
internal fun parseJobStatus(
    json: Json,
    raw: String,
): JobStatus? =
    runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val node = root["jobStatus"] ?: return@runCatching json.decodeFromJsonElement<JobStatus>(root)
        json.decodeFromJsonElement<JobStatus>(node)
    }.getOrNull()

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
        // The PDS-addressed token: this authorises the video service to
        // write a blob into the user's own repository.
        val token = serviceAuthProvider.blobUploadToken()

        val response =
            httpClient.post("$VIDEO_SERVICE_BASE_URL/xrpc/app.bsky.video.uploadVideo") {
                parameter("did", did)
                parameter("name", file.name)
                header(HttpHeaders.Authorization, "Bearer $token")
                // Ktor streams this from disk and supplies Content-Length
                // itself — which onUpload needs a total to report against, and
                // which the service requires. Reading the file into memory
                // would hold up to 100MB.
                setBody(LocalFileContent(file, ContentType.Video.MP4))
                onUpload { sent, total ->
                    if (total != null && total > 0) onProgress(sent.toFloat() / total)
                }
            }

        // Read once: the body is a stream, and consuming it twice would yield
        // an empty string on the second read.
        val raw = runCatching { response.body<String>() }.getOrNull().orEmpty()

        val status = parseJobStatus(json, raw)

        // 409 already_exists is NOT a failure. Re-uploading a clip the service
        // has already processed returns the existing job, so the right move is
        // to carry on and poll it — treating this as fatal would break retry
        // for the one case retry exists to serve. Verified against production.
        if (response.status == HttpStatusCode.Conflict && status?.jobId?.isNotBlank() == true) {
            Timber.tag(TAG).i("uploadVideo: already processed, reusing job %s", status.jobId)
            return UploadOutcome.Accepted(status.jobId)
        }

        if (!response.status.isSuccess()) {
            // Terminal. A policy refusal is supposed to arrive from
            // getUploadLimits before we ever transcode, so a rejection here is
            // something we did not predict.
            Timber.tag(TAG).e("uploadVideo rejected status=%d body=%s", response.status.value, raw.take(400))
            return UploadOutcome.Failed(
                VideoUploadError.UploadFailed(response.status.value, status?.message ?: raw.take(400)),
            )
        }

        if (status == null) {
            // Log the body, not just a type name. A shape mismatch is
            // undiagnosable without seeing what actually came back.
            // Terminal, and a contract mismatch between us and the service.
            Timber.tag(TAG).e("uploadVideo response unparseable: %s", raw.take(400))
            return UploadOutcome.Failed(VideoUploadError.UploadFailed(null, "Unrecognised upload response"))
        }

        return UploadOutcome.Accepted(status.jobId)
    }

    private companion object {
        const val TAG = "VideoUpload"
    }
}

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.videoupload.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.videoupload.VideoSourceProbe
import net.kikin.nubecita.core.videoupload.VideoUploadRepository
import net.kikin.nubecita.core.videoupload.internal.DefaultServiceAuthProvider
import net.kikin.nubecita.core.videoupload.internal.DefaultVideoUploadRepository
import net.kikin.nubecita.core.videoupload.internal.JobStatusPoller
import net.kikin.nubecita.core.videoupload.internal.Media3VideoCompressor
import net.kikin.nubecita.core.videoupload.internal.MediaMetadataVideoSourceProbe
import net.kikin.nubecita.core.videoupload.internal.ServiceAuthProvider
import net.kikin.nubecita.core.videoupload.internal.UploadLimitsProbe
import net.kikin.nubecita.core.videoupload.internal.VideoCompressor
import net.kikin.nubecita.core.videoupload.internal.VideoServiceClientFactory
import net.kikin.nubecita.core.videoupload.internal.VideoUploader
import java.io.File
import kotlin.time.Clock

/**
 * Wires the video upload pipeline.
 *
 * Everything is unscoped. An upload's state lives in its cold [Flow][
 * kotlinx.coroutines.flow.Flow], not in these objects, so a per-injection
 * instance is correct and a singleton would only invite shared mutable state
 * between concurrent composers.
 *
 * The one exception is the token cache inside [DefaultServiceAuthProvider],
 * which is scoped to a single provider instance — and therefore to a single
 * upload. That is intentional: a token shared across uploads would need
 * invalidation on sign-out, and the saving (two PDS round-trips) does not
 * justify the coupling.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoUploadModule {
    @Provides
    fun provideServiceAuthProvider(
        xrpcClientProvider: XrpcClientProvider,
        sessionStateProvider: SessionStateProvider,
        clock: Clock,
    ): ServiceAuthProvider = DefaultServiceAuthProvider(xrpcClientProvider, sessionStateProvider, clock)

    @Provides
    fun provideVideoServiceClientFactory(
        httpClient: HttpClient,
        serviceAuthProvider: ServiceAuthProvider,
    ): VideoServiceClientFactory = VideoServiceClientFactory(httpClient, serviceAuthProvider)

    @Provides
    fun provideUploadLimitsProbe(factory: VideoServiceClientFactory): UploadLimitsProbe = UploadLimitsProbe(factory)

    @Provides
    fun provideJobStatusPoller(factory: VideoServiceClientFactory): JobStatusPoller = JobStatusPoller(factory)

    @Provides
    fun provideVideoSourceProbe(
        @ApplicationContext context: Context,
    ): VideoSourceProbe = MediaMetadataVideoSourceProbe(context, Dispatchers.IO)

    @Provides
    fun provideVideoCompressor(
        @ApplicationContext context: Context,
        sourceProbe: VideoSourceProbe,
    ): VideoCompressor =
        Media3VideoCompressor(
            context = context,
            sourceProbe = sourceProbe,
            // Cache dir, not files dir: a transcode is scratch the OS may
            // reclaim. The pipeline deletes it in a finally regardless.
            outputDir = File(context.cacheDir, "video-upload").apply { mkdirs() },
        )

    @Provides
    fun provideVideoUploader(
        httpClient: HttpClient,
        serviceAuthProvider: ServiceAuthProvider,
    ): VideoUploader = VideoUploader(httpClient, serviceAuthProvider, Json { ignoreUnknownKeys = true })

    @Provides
    fun provideVideoUploadRepository(
        limitsProbe: UploadLimitsProbe,
        sourceProbe: VideoSourceProbe,
        compressor: VideoCompressor,
        uploader: VideoUploader,
        poller: JobStatusPoller,
        sessionStateProvider: SessionStateProvider,
    ): VideoUploadRepository =
        DefaultVideoUploadRepository(
            limitsProbe = limitsProbe,
            sourceProbe = sourceProbe,
            compressor = compressor,
            uploader = uploader,
            poller = poller,
            sessionStateProvider = sessionStateProvider,
        )
}

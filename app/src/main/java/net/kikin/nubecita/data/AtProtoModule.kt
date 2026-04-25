package net.kikin.nubecita.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import javax.inject.Singleton

// Public AppView gateway — serves unauthenticated AT Protocol lexicons without
// requiring per-user PDS discovery. Authenticated flows will use a different
// XrpcClient derived from AtOAuth.createClient() once a session exists.
private const val APPVIEW_URL = "https://public.api.bsky.app"

@Module
@InstallIn(SingletonComponent::class)
object AtProtoModule {
    @Provides
    @Singleton
    fun provideAnonymousXrpcClient(httpClient: HttpClient): XrpcClient = XrpcClient(baseUrl = APPVIEW_URL, httpClient = httpClient)
}

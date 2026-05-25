package net.kikin.nubecita.core.push.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.push.AppLifecycleObserver
import net.kikin.nubecita.core.push.DefaultPushRegistrationRepository
import net.kikin.nubecita.core.push.FcmAutoInit
import net.kikin.nubecita.core.push.FcmTokenProvider
import net.kikin.nubecita.core.push.MutedActorRepository
import net.kikin.nubecita.core.push.NotificationChannelInstaller
import net.kikin.nubecita.core.push.PushDispatcher
import net.kikin.nubecita.core.push.PushNotificationBuilder
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import net.kikin.nubecita.core.push.PushRegistrationRepository
import net.kikin.nubecita.core.push.PushRegistrationStateStore
import net.kikin.nubecita.core.push.internal.FirebaseFcmAutoInit
import net.kikin.nubecita.core.push.internal.FirebaseFcmTokenProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PushModule {
    @Binds
    @Singleton
    abstract fun bindPushRegistrationRepository(
        impl: DefaultPushRegistrationRepository,
    ): PushRegistrationRepository

    @Binds
    @Singleton
    abstract fun bindFcmTokenProvider(impl: FirebaseFcmTokenProvider): FcmTokenProvider

    @Binds
    @Singleton
    abstract fun bindFcmAutoInit(impl: FirebaseFcmAutoInit): FcmAutoInit

    companion object {
        @Provides
        @Singleton
        fun providePushDispatcher(): PushDispatcher = PushDispatcher()

        @Provides
        @Singleton
        fun provideNotificationChannelInstaller(): NotificationChannelInstaller = NotificationChannelInstaller()

        @Provides
        @Singleton
        fun provideDefaultPushRegistrationRepository(
            xrpcClientProvider: XrpcClientProvider,
            appConfig: PushAppConfig,
        ): DefaultPushRegistrationRepository =
            DefaultPushRegistrationRepository(
                xrpcClientProvider = xrpcClientProvider,
                appId = appConfig.applicationId,
            )

        @Provides
        @Singleton
        fun provideFirebaseFcmTokenProvider(): FirebaseFcmTokenProvider = FirebaseFcmTokenProvider()

        @Provides
        @Singleton
        fun provideFirebaseFcmAutoInit(): FirebaseFcmAutoInit = FirebaseFcmAutoInit()

        @Provides
        @Singleton
        fun providePushRegistrationStateStore(
            @PushRegistrationDataStore dataStore: DataStore<Preferences>,
        ): PushRegistrationStateStore = PushRegistrationStateStore(dataStore)

        @Provides
        @Singleton
        fun provideMutedActorRepository(
            xrpcClientProvider: XrpcClientProvider,
            @MutedActorDataStore dataStore: DataStore<Preferences>,
        ): MutedActorRepository = MutedActorRepository(xrpcClientProvider, dataStore)

        @Provides
        @Singleton
        fun providePushNotificationBuilder(
            @PushSmallIconRes smallIconRes: Int,
        ): PushNotificationBuilder = PushNotificationBuilder(smallIconRes)

        @Provides
        @Singleton
        fun providePushRegistrationCoordinator(
            sessionStateProvider: SessionStateProvider,
            repository: PushRegistrationRepository,
            stateStore: PushRegistrationStateStore,
            tokenProvider: FcmTokenProvider,
            fcmAutoInit: FcmAutoInit,
            @ApplicationScope scope: CoroutineScope,
        ): PushRegistrationCoordinator =
            PushRegistrationCoordinator(
                sessionStateProvider = sessionStateProvider,
                repository = repository,
                stateStore = stateStore,
                tokenProvider = tokenProvider,
                fcmAutoInit = fcmAutoInit,
                scope = scope,
            )

        @Provides
        @Singleton
        fun provideAppLifecycleObserver(
            mutedActorRepository: MutedActorRepository,
            @ApplicationScope scope: CoroutineScope,
        ): AppLifecycleObserver = AppLifecycleObserver(mutedActorRepository, scope)
    }
}

/**
 * Configuration passed by the consumer of `:core:push` (typically `:app`)
 * for fields that depend on the host application's identity. Kept as a
 * dedicated holder rather than scattered qualified primitives so the
 * `:app`-side `@Provides` is one focused method.
 */
data class PushAppConfig(
    val applicationId: String,
)

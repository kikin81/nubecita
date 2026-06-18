package net.kikin.nubecita.core.push.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.session.SessionClearable
import net.kikin.nubecita.core.push.AppLifecycleObserver
import net.kikin.nubecita.core.push.DataStoreNotificationsPromptShownStore
import net.kikin.nubecita.core.push.DefaultPushRegistrationRepository
import net.kikin.nubecita.core.push.FcmAutoInit
import net.kikin.nubecita.core.push.FcmTokenProvider
import net.kikin.nubecita.core.push.MutedActorRepository
import net.kikin.nubecita.core.push.NotificationChannelInstaller
import net.kikin.nubecita.core.push.NotificationsEnabledProvider
import net.kikin.nubecita.core.push.NotificationsEnabledSource
import net.kikin.nubecita.core.push.NotificationsPromptDecider
import net.kikin.nubecita.core.push.NotificationsPromptShownStore
import net.kikin.nubecita.core.push.PushDispatcher
import net.kikin.nubecita.core.push.PushGatewayConfig
import net.kikin.nubecita.core.push.PushNotificationBuilder
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import net.kikin.nubecita.core.push.PushRegistrationRepository
import net.kikin.nubecita.core.push.PushRegistrationStateStore
import net.kikin.nubecita.core.push.internal.FirebaseFcmAutoInit
import net.kikin.nubecita.core.push.internal.FirebaseFcmTokenProvider
import net.kikin.nubecita.core.push.internal.PushRegistrationSessionClearable
import javax.inject.Singleton

/**
 * Hilt bindings for `:core:push`'s repository / coordinator / FCM-bridge
 * interfaces, plus singleton provider methods on the companion object.
 *
 * The class itself is publicly addressable (rather than `internal`) so
 * downstream feature modules' instrumentation tests can swap individual
 * bindings via `@TestInstallIn(replaces = [PushModule::class])`. Kotlin's
 * `internal` modifier is per-Gradle-module, so an internal binding module
 * would be invisible to `:core:push/src/androidTest/` and downstream
 * `:feature:*:impl/src/androidTest/` swap targets wouldn't compile. The
 * bound implementations remain `internal` — only the module class itself
 * is addressable. Matches the pattern documented on `:core:auth`'s
 * `AuthBindingsModule` and `:core:preferences`'s
 * `UserPreferencesBindingsModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @Singleton
    internal abstract fun bindPushRegistrationRepository(
        impl: DefaultPushRegistrationRepository,
    ): PushRegistrationRepository

    @Binds
    @Singleton
    internal abstract fun bindFcmTokenProvider(impl: FirebaseFcmTokenProvider): FcmTokenProvider

    @Binds
    @Singleton
    internal abstract fun bindFcmAutoInit(impl: FirebaseFcmAutoInit): FcmAutoInit

    // Contributes the push module's pre-logout hook into the multibound
    // Set<SessionClearable> consumed by DefaultAuthRepository.signOut(). The
    // clearable runs sequentially inside the signOut coroutine, BEFORE
    // atOAuth.logout() revokes the OAuth tokens — so the unregisterPush
    // call can still authenticate. Suspending here is expected (the
    // unregisterPush is a network call); the contract is "complete before
    // atOAuth.logout runs," not "non-suspending." See
    // PushRegistrationSessionClearable's KDoc + nubecita-1fy.8 for the
    // regression this defends against.
    @Binds
    @IntoSet
    internal abstract fun bindPushRegistrationSessionClearable(
        impl: PushRegistrationSessionClearable,
    ): SessionClearable

    @Binds
    @Singleton
    internal abstract fun bindNotificationsEnabledSource(
        impl: NotificationsEnabledProvider,
    ): NotificationsEnabledSource

    companion object {
        @Provides
        @Singleton
        fun providePushDispatcher(): PushDispatcher = PushDispatcher()

        @Provides
        @Singleton
        fun provideNotificationChannelInstaller(): NotificationChannelInstaller = NotificationChannelInstaller()

        // The three @Provides below return `internal` impl classes —
        // mark the providers `internal` so the now-public PushModule
        // doesn't expose them. Hilt accepts internal @Provides on a
        // public module; the bound interface (PushRegistrationRepository,
        // FcmTokenProvider, FcmAutoInit) is what downstream sees.
        @Provides
        @Singleton
        internal fun provideDefaultPushRegistrationRepository(
            xrpcClientProvider: XrpcClientProvider,
            appConfig: PushAppConfig,
            gateway: PushGatewayConfig,
        ): DefaultPushRegistrationRepository =
            DefaultPushRegistrationRepository(
                xrpcClientProvider = xrpcClientProvider,
                appId = appConfig.applicationId,
                gateway = gateway,
            )

        // Gateway identity defaults to the production self-hosted instance
        // at `https://push.nubecita.app`. A future forkability epic can swap
        // this provider's body to read from a build-time-injected source
        // (BuildConfig fields or a Kotlin source generated by a Gradle task
        // that consumes `local.properties` / env vars / CI secrets) without
        // touching [DefaultPushRegistrationRepository] or its tests.
        // `local.properties` itself is NOT available at runtime on Android —
        // it's a Gradle-time artifact; any forkability work would plumb it
        // through codegen before this provider runs.
        @Provides
        @Singleton
        fun providePushGatewayConfig(): PushGatewayConfig = PushGatewayConfig.Nubecita

        @Provides
        @Singleton
        internal fun provideFirebaseFcmTokenProvider(): FirebaseFcmTokenProvider = FirebaseFcmTokenProvider()

        @Provides
        @Singleton
        internal fun provideFirebaseFcmAutoInit(): FirebaseFcmAutoInit = FirebaseFcmAutoInit()

        @Provides
        @Singleton
        fun providePushRegistrationStateStore(
            @PushRegistrationDataStore dataStore: DataStore<Preferences>,
        ): PushRegistrationStateStore = PushRegistrationStateStore(dataStore)

        @Provides
        @Singleton
        fun provideNotificationsPromptShownStore(
            @NotificationsPromptDataStore dataStore: DataStore<Preferences>,
        ): NotificationsPromptShownStore = DataStoreNotificationsPromptShownStore(dataStore)

        @Provides
        @Singleton
        fun provideNotificationsPromptDecider(store: NotificationsPromptShownStore): NotificationsPromptDecider = NotificationsPromptDecider(store = store, sdkInt = android.os.Build.VERSION.SDK_INT)

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

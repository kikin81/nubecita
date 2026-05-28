package net.kikin.nubecita.bootstrap

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * A side-effect-only startup hook fired once during
 * [net.kikin.nubecita.NubecitaApplication.onCreate]. Implementations are
 * collected via Hilt multibinding (`@Provides @IntoSet`) per source set,
 * so the per-flavor wiring decides which initializers actually run.
 *
 * - **Production flavor** — `ProductionBootstrapModule` (under
 *   `app/src/production/`) contributes lambdas that call
 *   `AppLifecycleObserver.start()`, `PushRegistrationCoordinator.start()`,
 *   and `NotificationsPollingObserver.start()`. The three coordinators
 *   collect `SessionStateProvider.state` and drive Firebase / XRPC IO.
 *
 * - **Bench flavor** — no initializers are contributed; the set is empty
 *   (declared via the `@Multibinds` below). With nothing requesting the
 *   coordinators, Hilt never constructs them, the FCM auto-init opt-in
 *   never fires, and the bench APK stays network-silent throughout the
 *   Macrobench measurement window. This is the entire point of the
 *   flavor split — see `nubecita-crmi.6` Section A2 for the broader
 *   fake-network scaffolding that this dovetails with.
 *
 * Iteration order across initializers is **not guaranteed**. The
 * ordering constraint between `NotificationChannelInstaller.install`
 * and `PushRegistrationCoordinator.start` (channels must exist before
 * the coordinator opts FCM auto-init back on) is preserved by leaving
 * the channel installer call site in `NubecitaApplication.onCreate`
 * itself — that call runs before the multibinding loop.
 */
fun interface AppInitializer {
    fun start()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BootstrapModule {
    /**
     * Declares the multibinding so the bench flavor can resolve an empty
     * set without any `@Provides @IntoSet` contributor. Without this
     * declaration, Hilt would fail to satisfy
     * `Set<AppInitializer>` on the bench graph.
     */
    @Multibinds
    abstract fun appInitializers(): Set<@JvmSuppressWildcards AppInitializer>
}

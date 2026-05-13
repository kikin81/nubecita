package net.kikin.nubecita.core.common.coroutines

import javax.inject.Qualifier

/**
 * Hilt qualifier for the application-scoped [kotlinx.coroutines.CoroutineScope].
 * Inject as `@ApplicationScope scope: CoroutineScope` in Singleton components
 * that need to launch coroutines that outlive any individual screen — e.g.,
 * cache mutation jobs that must survive back-stack pops.
 *
 * Lives in `:core:common` so any module can depend on this qualifier without
 * introducing cyclic deps. The binding itself is provided in
 * [net.kikin.nubecita.core.common.coroutines.di.ApplicationScopeModule].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

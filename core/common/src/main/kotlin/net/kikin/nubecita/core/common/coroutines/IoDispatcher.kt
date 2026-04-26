package net.kikin.nubecita.core.common.coroutines

import javax.inject.Qualifier

/**
 * Hilt qualifier for an I/O-bound [kotlinx.coroutines.CoroutineDispatcher].
 * Inject as `@IoDispatcher dispatcher: CoroutineDispatcher` in repositories
 * and other components that wrap blocking I/O (network, disk).
 *
 * Lives here (not in `:core:auth` or per-feature DI) so cross-feature
 * data layers can share the same qualifier without introducing cyclic
 * deps. The binding itself is provided in
 * `net.kikin.nubecita.core.common.coroutines.di.DispatchersModule`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

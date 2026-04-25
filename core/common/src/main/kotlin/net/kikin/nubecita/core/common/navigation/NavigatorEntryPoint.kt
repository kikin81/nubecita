package net.kikin.nubecita.core.common.navigation

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for grabbing the [Navigator] singleton from a
 * Composable (which can't use constructor injection).
 *
 * Typical usage:
 * ```
 * val context = LocalContext.current
 * val navigator = remember(context) {
 *     EntryPointAccessors.fromApplication(
 *         context.applicationContext,
 *         NavigatorEntryPoint::class.java,
 *     ).navigator()
 * }
 * ```
 *
 * Lives in `:core:common` so feature modules can reach it without an
 * illegal back-dep on `:app`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigatorEntryPoint {
    fun navigator(): Navigator
}

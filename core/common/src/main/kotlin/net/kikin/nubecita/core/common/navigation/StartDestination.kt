package net.kikin.nubecita.core.common.navigation

import javax.inject.Qualifier

/**
 * Hilt qualifier for the [androidx.navigation3.runtime.NavKey] the app
 * starts on. `:core:common` can't reference `:app`'s concrete `Main`
 * destination directly, so the start key is injected from `:app` via this
 * qualifier and consumed by [DefaultNavigator] when seeding its back stack.
 *
 * `:app`'s Hilt module:
 * ```
 * @Provides @StartDestination
 * fun provideStartDestination(): NavKey = Main
 * ```
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StartDestination

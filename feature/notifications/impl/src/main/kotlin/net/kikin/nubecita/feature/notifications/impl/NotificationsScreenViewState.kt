package net.kikin.nubecita.feature.notifications.impl

/**
 * Skeleton — concrete branches arrive in `nubecita-1fy.1.8`.
 *
 * Will be a sealed sum of `InitialLoading | Empty | InitialError | Loaded`
 * projected from `NotificationsState.toViewState()` so the screen
 * composable can `when (viewState)` and render a single branch.
 */
internal sealed interface NotificationsScreenViewState

package net.kikin.nubecita.feature.notifications.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * Skeleton MVI contract for the Notifications screen.
 *
 * Real state, events, and effects land in `nubecita-1fy.1.6` (the
 * `NotificationsViewModel` task in the `add-feature-notifications`
 * openspec change). For now these are empty types so the scaffolded
 * [NotificationsViewModel] compiles and the `NotificationsNavigationModule`
 * can register its `@MainShell EntryProviderInstaller`.
 */
internal data object NotificationsState : UiState

internal sealed interface NotificationsEvent : UiEvent

internal sealed interface NotificationsEffect : UiEffect

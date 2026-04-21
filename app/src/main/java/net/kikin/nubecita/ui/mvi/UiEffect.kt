package net.kikin.nubecita.ui.mvi

/**
 * Marker interface for one-shot side effects emitted by an [MviViewModel]
 * (navigation, toasts, snackbars). Effects are delivered to the first collector
 * and are not re-delivered on re-subscription.
 */
interface UiEffect

package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import javax.inject.Inject

internal class DefaultNavigator
    @Inject
    constructor(
        @StartDestination start: NavKey,
    ) : Navigator {
        override val backStack: SnapshotStateList<NavKey> = mutableStateListOf(start)

        override fun goTo(key: NavKey) {
            backStack.add(key)
        }

        override fun goBack() {
            backStack.removeLastOrNull()
        }

        override fun replaceTo(key: NavKey) {
            // No-op when the stack is already a single-entry stack on this key
            // — without the guard, a second `replaceTo(SameKey)` would
            // clear+re-add the entry, dropping any `rememberSaveable` /
            // ViewModel state the destination accumulated since the first call.
            // This matters for the onboarding → login path where both the
            // screen Composable (on persistence success) and `MainActivity`'s
            // reactive collector (on the flag flip) try to navigate to
            // `Login`; with idempotent `replaceTo`, whichever lands first does
            // real work and the second is a no-op.
            if (backStack.size == 1 && backStack[0] == key) return
            backStack.clear()
            backStack.add(key)
        }
    }

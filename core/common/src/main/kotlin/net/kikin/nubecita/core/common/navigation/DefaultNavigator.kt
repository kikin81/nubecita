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
            backStack.clear()
            backStack.add(key)
        }
    }

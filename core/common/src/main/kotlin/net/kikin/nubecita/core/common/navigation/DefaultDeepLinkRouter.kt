package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

internal class DefaultDeepLinkRouter
    @Inject
    constructor() : DeepLinkRouter {
        private val channel = Channel<NavKey>(Channel.BUFFERED)

        override val pendingDeepLinks: Flow<NavKey> = channel.receiveAsFlow()

        override suspend fun publish(target: NavKey) {
            channel.send(target)
        }
    }

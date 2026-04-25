package net.kikin.nubecita.core.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

internal class DefaultOAuthRedirectBroker
    @Inject
    constructor() : OAuthRedirectBroker {
        private val channel = Channel<String>(Channel.BUFFERED)

        override val redirects: Flow<String> = channel.receiveAsFlow()

        override suspend fun publish(redirectUri: String) {
            channel.send(redirectUri)
        }
    }

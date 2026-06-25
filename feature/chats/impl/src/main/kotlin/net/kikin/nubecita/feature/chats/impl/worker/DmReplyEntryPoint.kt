package net.kikin.nubecita.feature.chats.impl.worker

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point exposing [DmReplyHandler] to the manifest-declared
 * [DmReplyReceiver], which can't have its constructor `@Inject`ed. Resolved via
 * `EntryPointAccessors.fromApplication` (mirrors `WidgetEntryPoint`).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DmReplyEntryPoint {
    fun dmReplyHandler(): DmReplyHandler
}

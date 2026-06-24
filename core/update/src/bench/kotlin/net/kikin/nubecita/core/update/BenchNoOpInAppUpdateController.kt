package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Bench-flavor no-op: zero Play/network calls so Macrobench stays offline & deterministic. */
@Singleton
internal class BenchNoOpInAppUpdateController
    @Inject
    constructor() : InAppUpdateController {
        override val state: StateFlow<UpdateState> = MutableStateFlow(UpdateState.Idle).asStateFlow()

        override suspend fun checkAndMaybePrompt(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit

        override suspend fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit

        override suspend fun completeFlexibleUpdate() = Unit
    }

package net.kikin.nubecita.core.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Bench-flavor [CurrentAccountRepository]: a fixed account matching the bench
 * session (`FakeSessionStateProvider`), with a display name so bench builds and
 * screenshots render a realistic identity. No network; the avatar is absent, so
 * avatars render their initial fallback.
 */
internal class BenchCurrentAccountRepository
    @Inject
    constructor() : CurrentAccountRepository {
        override val account: StateFlow<CurrentAccount?> = MutableStateFlow(BENCH_ACCOUNT).asStateFlow()

        companion object {
            val BENCH_ACCOUNT =
                CurrentAccount(
                    did = "did:plc:benchnubecita0000000000000",
                    handle = "bench.nubecita.app",
                    displayName = "Nubecita Bench",
                    avatarUrl = null,
                )
        }
    }

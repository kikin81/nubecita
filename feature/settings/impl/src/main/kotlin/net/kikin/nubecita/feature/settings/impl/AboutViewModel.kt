package net.kikin.nubecita.feature.settings.impl

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.profile.ActorProfile
import net.kikin.nubecita.core.profile.ActorProfileRepository
import javax.inject.Inject

/**
 * Backs the About screen. Holds the curated [Contributor] list (DID + fallback
 * handle + blurb) and hydrates each one's live avatar / display name / handle by
 * **fetching** their profile from the network ([ActorProfileRepository.fetchProfile],
 * not the local actor cache — the curated DIDs are unlikely to already be cached).
 * Each row resolves independently and falls back to the curated handle + a null
 * avatar on failure, so one unreachable profile never blanks the section.
 * Navigation/external-link intents are emitted as effects and resolved by the
 * screen (the VM never touches `LocalMainShellNavState`).
 */
@HiltViewModel
internal class AboutViewModel
    @Inject
    constructor(
        private val actorProfileRepository: ActorProfileRepository,
    ) : MviViewModel<AboutState, AboutEvent, AboutEffect>(
            AboutState(
                thanks = CONTRIBUTORS.map { it.toRow(profile = null) }.toImmutableList(),
                isLoadingThanks = true,
            ),
        ) {
        init {
            hydrateThanks()
        }

        private fun hydrateThanks() {
            viewModelScope.launch {
                CONTRIBUTORS
                    .map { contributor ->
                        launch {
                            // Per-row, independent: a failed fetch leaves the curated
                            // handle + blurb and a null avatar; the row still renders.
                            val profile = actorProfileRepository.fetchProfile(contributor.did).getOrNull()
                            setState {
                                copy(
                                    thanks =
                                        thanks
                                            .map { row ->
                                                if (row.did == contributor.did) contributor.toRow(profile) else row
                                            }.toImmutableList(),
                                )
                            }
                        }
                    }.joinAll()
                setState { copy(isLoadingThanks = false) }
            }
        }

        override fun handleEvent(event: AboutEvent) {
            when (event) {
                AboutEvent.SourceTapped -> sendEffect(AboutEffect.LaunchUri(GITHUB_URL))
                is AboutEvent.ThanksRowTapped -> sendEffect(AboutEffect.NavigateToProfile(event.did))
                AboutEvent.LicensesTapped -> sendEffect(AboutEffect.OpenLicenses)
            }
        }

        private data class Contributor(
            val did: String,
            val fallbackHandle: String,
            @StringRes val blurbRes: Int,
        ) {
            fun toRow(profile: ActorProfile?): ThanksRowUi =
                ThanksRowUi(
                    did = did,
                    handle = profile?.handle ?: fallbackHandle,
                    displayName = profile?.displayName,
                    avatarUrl = profile?.avatarUrl,
                    blurbRes = blurbRes,
                )
        }

        companion object {
            const val GITHUB_URL = "https://github.com/kikin81/nubecita"

            // Curated credits. DIDs (not handles) so a handle change can't break
            // the profile link; the blurb is a string resource (i18n) and the
            // handle here is only a fallback shown until the live fetch lands.
            private val CONTRIBUTORS: ImmutableList<Contributor> =
                persistentListOf(
                    Contributor(
                        did = "did:plc:q46tlww4otcbawdeynycankw",
                        fallbackHandle = "stavfx.com",
                        blurbRes = R.string.about_thanks_stavfx,
                    ),
                    Contributor(
                        did = "did:plc:qvnqnisnr45lpkmtjjkynyxr",
                        fallbackHandle = "vmlara.bsky.social",
                        blurbRes = R.string.about_thanks_vmlara,
                    ),
                    Contributor(
                        did = "did:plc:x7usamgg2p2jjy4mxit2nqfm",
                        fallbackHandle = "zenos00.bsky.social",
                        blurbRes = R.string.about_thanks_zenos,
                    ),
                    Contributor(
                        did = "did:plc:n3xg3j7lqslngdzjb2trmcpz",
                        fallbackHandle = "cameronbanga.com",
                        blurbRes = R.string.about_thanks_cameronbanga,
                    ),
                )
        }
    }

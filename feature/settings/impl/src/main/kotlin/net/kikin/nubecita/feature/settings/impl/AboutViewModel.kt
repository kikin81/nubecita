package net.kikin.nubecita.feature.settings.impl

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject

/**
 * Backs the About screen. Holds the curated [Contributor] list (DID + fallback
 * handle + blurb) and hydrates each one's live avatar / display name / handle
 * from [ActorRepository]. Each row catches its own fetch failure and falls back
 * to the curated handle + a null avatar, so one unreachable profile never blanks
 * the section. Navigation/external-link intents are emitted as effects and
 * resolved by the screen (the VM never touches `LocalMainShellNavState`).
 */
@HiltViewModel
internal class AboutViewModel
    @Inject
    constructor(
        private val actorRepository: ActorRepository,
    ) : MviViewModel<AboutState, AboutEvent, AboutEffect>(
            AboutState(
                thanks = CONTRIBUTORS.map { it.toRow(actor = null) }.toImmutableList(),
                isLoadingThanks = true,
            ),
        ) {
        init {
            hydrateThanks()
        }

        private fun hydrateThanks() {
            val rowFlows =
                CONTRIBUTORS.map { contributor ->
                    actorRepository
                        .getActor(contributor.did)
                        .map { actor -> contributor.toRow(actor) }
                        // Per-row fallback: a failed/empty fetch keeps the curated
                        // handle + blurb and a null avatar; the row still renders.
                        .catch { emit(contributor.toRow(actor = null)) }
                }
            combine(rowFlows) { rows -> rows.toList().toImmutableList() }
                .onEach { rows -> setState { copy(thanks = rows, isLoadingThanks = false) } }
                .launchIn(viewModelScope)
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
            fun toRow(actor: ActorUi?): ThanksRowUi =
                ThanksRowUi(
                    did = did,
                    handle = actor?.handle ?: fallbackHandle,
                    displayName = actor?.displayName,
                    avatarUrl = actor?.avatarUrl,
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

package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import kotlin.time.Instant

private fun link(
    enabled: Boolean = true,
    joinRule: JoinRule = JoinRule.Anyone,
) = JoinLinkUi(
    code = "code-1",
    url = "https://nubecita.app/group/join/code-1",
    enabled = enabled,
    joinRule = joinRule,
    requireApproval = true,
    createdAt = Instant.parse("2026-05-13T12:00:00Z"),
)

@Composable
private fun Fixture(link: JoinLinkUi) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            JoinLinkCard(
                link = link,
                mutationInFlight = false,
                onCopy = {},
                onShare = {},
                onEnabledChange = {},
                onJoinRuleChange = {},
                onRequireApprovalChange = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "enabled-light", showBackground = true)
@Preview(name = "enabled-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinLinkCardEnabled() = Fixture(link(enabled = true))

@PreviewTest
@Preview(name = "disabled-light", showBackground = true)
@Preview(name = "disabled-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinLinkCardDisabled() = Fixture(link(enabled = false))

@PreviewTest
@Preview(name = "unsupported-light", showBackground = true)
@Preview(name = "unsupported-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinLinkCardUnsupported() = Fixture(link(joinRule = JoinRule.Unsupported))

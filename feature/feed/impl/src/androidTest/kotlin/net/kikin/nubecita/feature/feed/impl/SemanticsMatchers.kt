package net.kikin.nubecita.feature.feed.impl

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/**
 * Matches a node whose `OnClick` action carries [label] as its accessibility label.
 *
 * `PostStat` labels its non-toggleable action cells (reply / repost / share / overflow)
 * via `onClickLabel`, NOT `contentDescription`, so `hasContentDescription` never matches
 * them — use this to select those affordances by their action verb. Shared across this
 * module's FeedScreen androidTests.
 */
internal fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("OnClick action label == '$label'") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

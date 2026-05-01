package net.kikin.nubecita.core.testing.android

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Empty Hilt-aware [ComponentActivity] used as the host for
 * `createAndroidComposeRule<HiltTestActivity>()` in instrumentation tests.
 *
 * Tests inject Hilt dependencies into the surrounding test class via
 * `@HiltAndroidTest` + `HiltAndroidRule`, then call `setContent { ... }`
 * on the rule to drive the Composable under test.
 *
 * Registered in this module's `AndroidManifest.xml` so consumer modules'
 * androidTest manifest mergers pick it up automatically — no per-consumer
 * manifest entry required.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()

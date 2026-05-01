package net.kikin.nubecita.testing

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
 * Registered in `app/src/androidTest/AndroidManifest.xml` so the
 * instrumentation APK can launch it.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()

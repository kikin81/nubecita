package net.kikin.nubecita.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold / warm / hot startup of Nubecita's `MainActivity`, reporting
 * `timeToInitialDisplay` (TTID) and `timeToFullDisplay` (TTFD).
 *
 * - **Cold** — process killed between iterations. Measures the
 *   end-to-end path: Application onCreate → Hilt singleton graph
 *   bootstrap (notably the Tink keyset on first-ever launch) →
 *   MainActivity onCreate → splash hand-off → first frame.
 * - **Warm** — activity destroyed but process alive. Measures
 *   re-creating MainActivity with the singleton graph still warm.
 * - **Hot** — activity in back stack, brought forward. Cheapest
 *   path; mostly measures recomposition + first-frame submission.
 *
 * ## Compilation-mode axis
 *
 * Parameterized over two `CompilationMode`s so the bundled baseline
 * profile's impact is directly measurable in one bench run:
 *
 * - `None` — `cmd package compile --reset` runs before each iteration,
 *   so ART starts from a clean slate with no AOT methods. This is the
 *   "no profile" baseline.
 * - `Partial(Require)` — installs the APK-bundled baseline profile via
 *   the profile installer, then `cmd package compile -m speed-profile`
 *   warms ART against it. This is the "with profile" measurement.
 *   `Require` (not the lenient default) makes the test fail if the
 *   APK doesn't ship a profile — which doubles as an assertion that
 *   `:app`'s producer wiring is intact.
 *
 * Compare the same `StartupMode` across the two `CompilationMode`s
 * for the profile's effect; compare the same cell to a historical
 * post-cycles snapshot for regression tracking. The cross-product
 * runs 6 tests, ~5 iterations each — ~3–5 min wall-clock on a Pixel
 * 10 Pro XL.
 *
 * Iteration count is the AndroidX default (5). The reported metrics
 * are aggregated over the iterations as min / median / max.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val startupMode: StartupMode,
    private val compilationMode: CompilationMode,
) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = DEFAULT_ITERATIONS,
            startupMode = startupMode,
        ) {
            pressHome()
            startActivityAndWait()
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<Any>> =
            // Explicit `arrayOf<Any>(...)` — Kotlin arrays are
            // invariant, so the unannotated form would infer as
            // `Array<StartupMode>` / `Array<CompilationMode>`, neither
            // of which is a subtype of `Array<Any>` (variance trap).
            // Spelling the upcast keeps a future second-axis edit from
            // biting silently.
            //
            // CompilationMode labels: the parameterized test name uses
            // the mode's `toString()`. `CompilationMode.None` prints as
            // `None`; `CompilationMode.Partial(BaselineProfileMode.Require)`
            // prints as `BaselineProfile` (not `Partial` — the toString
            // resolves the Require discriminator into a more descriptive
            // label). So the emitted test names are
            // `startup[COLD-None]`, `startup[COLD-BaselineProfile]`, etc.
            // Keep README cell names in sync with this exact label.
            listOf(StartupMode.COLD, StartupMode.WARM, StartupMode.HOT).flatMap { startup ->
                listOf(
                    CompilationMode.None(),
                    CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
                ).map { compile -> arrayOf<Any>(startup, compile) }
            }
    }
}

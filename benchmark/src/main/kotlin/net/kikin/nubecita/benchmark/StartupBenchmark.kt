package net.kikin.nubecita.benchmark

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
 * Compilation mode is fixed to `None` here. A follow-up ticket
 * (`nubecita-crmi.2`) lands a `BaselineProfileGenerator` and
 * parameterizes this benchmark over `CompilationMode.Partial` so we
 * can measure the profile's actual impact. Until then the profile
 * doesn't exist, so parameterizing over compilation modes would
 * produce identical numbers under different labels.
 *
 * Iteration count is the AndroidX default (5). The reported metrics
 * are aggregated over the iterations as min / median / max.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val startupMode: StartupMode,
) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = DEFAULT_ITERATIONS,
            startupMode = startupMode,
        ) {
            pressHome()
            startActivityAndWait()
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun startupModes(): List<Array<Any>> =
            // Explicit `arrayOf<Any>(...)` — Kotlin arrays are
            // invariant, so the unannotated `arrayOf(StartupMode.COLD)`
            // would infer as `Array<StartupMode>` (and `Array<StartupMode>`
            // is NOT a subtype of `Array<Any>`). The expected return type
            // currently coaxes inference to the right shape, but spelling
            // the upcast out keeps the variance trap from biting a future
            // edit that adds a second parameter.
            listOf(
                arrayOf<Any>(StartupMode.COLD),
                arrayOf<Any>(StartupMode.WARM),
                arrayOf<Any>(StartupMode.HOT),
            )
    }
}

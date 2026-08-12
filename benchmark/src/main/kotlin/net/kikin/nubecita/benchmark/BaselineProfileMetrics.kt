package net.kikin.nubecita.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric

/**
 * Metrics that measure whether the baseline profile is actually *working*,
 * rather than whether one is merely present.
 *
 * TTID alone cannot answer that. When `benchBenchmarkRelease` shipped zero app
 * profile rules (nubecita-6row), the COLD-BaselineProfile cell still showed a
 * 17.9% TTID improvement — entirely from library profiles — and the missing
 * app profile went unnoticed for months.
 *
 * Both metrics should DROP when a profile is applied properly, because AOT
 * compilation removes work the runtime would otherwise do at startup.
 *
 * The `%` is a genuine wildcard: `TraceSectionMetric` builds a SQL `LIKE` query
 * against the Perfetto trace, and SQLite's `LIKE` is ASCII case-insensitive —
 * so "JIT Compiling %" matches the runtime's lowercase "JIT compiling …".
 * See androidx.benchmark.macro 1.5.0-alpha07, Metric.kt:527.
 */
object BaselineProfileMetrics {
    /** Time spent JIT-compiling. Falls when methods are AOT-compiled instead. */
    @OptIn(ExperimentalMetricApi::class)
    val jitCompilationMetric = TraceSectionMetric("JIT Compiling %", label = "JIT compilation")

    /** Time spent initialising classes. Falls when classes are pre-resolved. */
    @OptIn(ExperimentalMetricApi::class)
    val classInitMetric = TraceSectionMetric("L%/%;", label = "ClassInit")

    /** Startup timing plus the two effectiveness metrics. */
    @OptIn(ExperimentalMetricApi::class)
    val allMetrics: List<Metric> = listOf(StartupTimingMetric(), jitCompilationMetric, classInitMetric)
}

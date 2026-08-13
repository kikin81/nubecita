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
 * against the Perfetto trace. See androidx.benchmark.macro 1.5.0-alpha07,
 * `Metric.kt:527`.
 *
 * ## Match ART's casing exactly — do not "tidy" these strings
 *
 * The patterns below mirror what ART actually emits, verified by querying a
 * captured trace with `trace_processor` rather than inferred:
 *
 * ```
 * LIKE 'JIT Compiling %'   (capital C, case-insensitive)  ->  44 matches
 * GLOB 'JIT Compiling *'   (capital C, case-sensitive)    ->   0 matches
 * GLOB 'JIT compiling *'   (lowercase c, case-sensitive)  ->  44 matches
 * ```
 *
 * An earlier version used `"JIT Compiling %"` and matched only because SQLite's
 * `LIKE` is ASCII case-insensitive — not because the strings agreed. Were
 * Perfetto ever to move that query to `GLOB`, or make matching case-sensitive,
 * the metric would silently report 0.00 in BOTH cells: build green, chart
 * green, signal dead. That is the precise failure class this whole area exists
 * to eliminate, so the pattern now matches ART's output directly and depends on
 * no case-folding behaviour (nubecita-rmmm).
 */
object BaselineProfileMetrics {
    /**
     * Time spent JIT-compiling. Falls when methods are AOT-compiled instead.
     *
     * Lowercase `compiling` is deliberate — ART emits e.g.
     * `"JIT compiling void vt4.d(xo5) (kind=Baseline) from /data/app/…/base.apk"`.
     */
    @OptIn(ExperimentalMetricApi::class)
    val jitCompilationMetric = TraceSectionMetric("JIT compiling %", label = "JIT compilation")

    /**
     * Time spent initialising classes. Falls when classes are pre-resolved.
     *
     * The pattern matches JVM class descriptors, verified against a trace: it
     * selects 988 slices such as `Landroid/view/FrameRateVelocityPoint;` and
     * `Lnet/kikin/nubecita/NubecitaApplication;`, matching the reported
     * `ClassInitCount` exactly.
     */
    @OptIn(ExperimentalMetricApi::class)
    val classInitMetric = TraceSectionMetric("L%/%;", label = "ClassInit")

    /** Startup timing plus the two effectiveness metrics. */
    @OptIn(ExperimentalMetricApi::class)
    val allMetrics: List<Metric> = listOf(StartupTimingMetric(), jitCompilationMetric, classInitMetric)
}

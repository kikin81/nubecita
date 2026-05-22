package net.kikin.nubecita.benchmark

/**
 * Package name of the target APK. Hardcoded — Macrobench requires a
 * literal, and there's no other consumer of this constant so reading
 * it from BuildConfig would only add ceremony.
 */
internal const val TARGET_PACKAGE: String = "net.kikin.nubecita"

/**
 * Default iteration count for every benchmark. AndroidX's own default
 * is 5; keeping the value here so all benchmark classes share one
 * knob — bumping to 10 (for variance reduction) is a one-line change.
 */
internal const val DEFAULT_ITERATIONS: Int = 5

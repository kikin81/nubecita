package net.kikin.nubecita.core.feedcache

/**
 * A bench fixture could not be read.
 *
 * Deliberately NOT an `IOException`: a build-time packaging miss arrives as
 * `FileNotFoundException`, and letting that reach the feed's error mapping
 * would render "Connection problem" for a missing asset — a misleading
 * diagnosis in the one build where there is no network at all.
 */
internal class FixtureLoadException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)

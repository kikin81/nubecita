package net.kikin.nubecita.core.videoupload.internal

import java.io.File

/**
 * Make sure [dir] exists, returning whether it does afterwards.
 *
 * Split out from the compressor so the rule is testable without a device: the
 * bug this guards against (nubecita-3ug0) broke video upload on every fresh
 * install, and the cost of not testing it was the whole feature.
 *
 * `mkdirs()` returning false is deliberately not treated as failure — it also
 * means "someone else created it first", which is reachable when two composes
 * start a transcode at once. Existence afterwards is the only thing that
 * matters, so that is what decides.
 */
internal fun ensureDirectory(dir: File): Boolean {
    dir.mkdirs()
    return dir.isDirectory
}

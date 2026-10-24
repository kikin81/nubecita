package net.kikin.nubecita.feature.composer.impl.state

/** Why a pasted image wasn't attached. Carried by [ComposerEffect.PasteNotAdded]. */
internal enum class PasteRejection {
    /** The content couldn't be read, wasn't an image, or was too large. */
    Unreadable,

    /**
     * A GIF. An uploaded GIF image renders as a still frame on Bluesky, so it
     * is pointed at the GIF picker instead.
     */
    Gif,

    /** The post already has the maximum number of images. */
    LimitReached,

    /** A GIF or video is attached, and images can't be combined with either. */
    OtherMediaAttached,
}

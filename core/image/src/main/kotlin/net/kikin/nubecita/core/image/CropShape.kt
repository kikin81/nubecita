package net.kikin.nubecita.core.image

/**
 * The fixed crop frame a [CropImage] surface presents. The frame stays
 * centered and the user pans/zooms the image behind it; the image is
 * constrained to always cover the frame.
 *
 * - [AvatarCircle] — a 1:1 frame drawn as a circular mask. The extracted
 *   region is the square bounding box of the circle (Bluesky stores a
 *   square avatar blob and renders it circular).
 * - [Banner] — an exact 3:1 rectangular frame. Owning the frame lets us
 *   use a true 3:1 ratio rather than a fixed-enum approximation; Bluesky
 *   center-crops the banner on display.
 */
enum class CropShape(
    internal val aspectWidth: Int,
    internal val aspectHeight: Int,
    internal val circular: Boolean,
) {
    AvatarCircle(aspectWidth = 1, aspectHeight = 1, circular = true),
    Banner(aspectWidth = 3, aspectHeight = 1, circular = false),
    ;

    /** Frame aspect ratio, width / height. */
    internal val aspect: Float get() = aspectWidth.toFloat() / aspectHeight.toFloat()
}

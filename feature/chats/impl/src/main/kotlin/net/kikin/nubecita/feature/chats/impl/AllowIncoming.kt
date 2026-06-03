package net.kikin.nubecita.feature.chats.impl

/**
 * The account-global "who can send me direct messages" preference, backed by
 * the `chat.bsky.actor.declaration/self` record's `allowIncoming` field.
 *
 * Wire values are `"all"` / `"following"` / `"none"`; an absent record (or an
 * unrecognized value) means [Following] — the AT Protocol default, mirroring
 * the read-side fallback in `:core:profile`'s `DmAvailability`.
 */
enum class AllowIncoming(
    val wireValue: String,
) {
    Everyone("all"),
    Following("following"),
    NoOne("none"),
    ;

    companion object {
        /** Map a wire `allowIncoming` string to the enum, defaulting to [Following]. */
        fun fromWire(value: String?): AllowIncoming = entries.firstOrNull { it.wireValue == value } ?: Following
    }
}

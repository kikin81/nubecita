package net.kikin.nubecita.core.wearcontract

import kotlinx.serialization.Serializable

/**
 * The phone app's session, published at [WearPaths.SESSION].
 *
 * The watch shows content only for [account] when it is non-null; when it is
 * null (signed out) the watch wipes its stored data and shows "Sign in on your
 * phone". A different account DID than the one the watch last saw also wipes
 * the previous account's data.
 */
@Serializable
data class WearSessionSnapshot(
    override val schemaVersion: Int = WearSchema.CURRENT,
    val publishedAtMs: Long,
    val account: WearAccount? = null,
) : WearPayload

/** The signed-in account, as shown on the watch. */
@Serializable
data class WearAccount(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    /** Key of the avatar image `Asset` in the same Data Layer item, if one was sent. */
    val avatarAssetKey: String? = null,
)

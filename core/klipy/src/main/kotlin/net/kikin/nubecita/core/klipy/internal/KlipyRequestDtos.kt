package net.kikin.nubecita.core.klipy.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of the `POST …/view|share/{slug}` engagement-tracking calls. */
@Serializable
internal data class KlipyTrackRequest(
    @SerialName("customer_id") val customerId: String,
)

/** Body of `POST …/report/{slug}`. [reason] is the [net.kikin.nubecita.core.klipy.KlipyReportReason] wire value. */
@Serializable
internal data class KlipyReportRequest(
    @SerialName("customer_id") val customerId: String,
    val reason: String,
)

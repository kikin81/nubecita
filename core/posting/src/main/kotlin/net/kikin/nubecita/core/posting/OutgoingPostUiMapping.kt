package net.kikin.nubecita.core.posting

import net.kikin.nubecita.data.models.OutgoingPostStatusUi
import net.kikin.nubecita.data.models.OutgoingPostUi

fun OutgoingPost.toUi(): OutgoingPostUi =
    OutgoingPostUi(
        localId = localId,
        post = post,
        status =
            when (status) {
                OutgoingPostStatus.Sending -> OutgoingPostStatusUi.Sending
                is OutgoingPostStatus.Sent -> OutgoingPostStatusUi.Sent
                is OutgoingPostStatus.Failed -> OutgoingPostStatusUi.Failed
            },
    )

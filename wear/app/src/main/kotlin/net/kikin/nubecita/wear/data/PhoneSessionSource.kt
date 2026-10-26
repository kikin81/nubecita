package net.kikin.nubecita.wear.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDataKeys
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearSessionSnapshot
import timber.log.Timber
import javax.inject.Inject

/** The phone app's session as the watch last received it. */
sealed interface PhoneSession {
    /** Nothing received yet (or the item was deleted). */
    data object Unknown : PhoneSession

    data object SignedOut : PhoneSession

    data class SignedIn(
        val account: WearAccount,
    ) : PhoneSession

    /** The phone sent a newer major schema than this watch app understands. */
    data object UpdateRequired : PhoneSession
}

interface PhoneSessionSource {
    /** The stored session first (works offline), then every change the phone pushes. */
    val session: Flow<PhoneSession>
}

internal class DataLayerPhoneSessionSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PhoneSessionSource {
        override val session: Flow<PhoneSession> =
            callbackFlow {
                val client = Wearable.getDataClient(context)
                val uri =
                    Uri
                        .Builder()
                        .scheme(PutDataRequest.WEAR_URI_SCHEME)
                        .authority("*")
                        .path(WearPaths.SESSION)
                        .build()
                val listener =
                    DataClient.OnDataChangedListener { events ->
                        events.forEach { event ->
                            if (event.dataItem.uri.path != WearPaths.SESSION) return@forEach
                            trySend(if (event.type == DataEvent.TYPE_DELETED) PhoneSession.Unknown else event.dataItem.toPhoneSession())
                        }
                    }
                client.addListener(listener, uri, DataClient.FILTER_LITERAL).await()
                val stored = client.getDataItems(uri).await()
                try {
                    send(stored.firstOrNull()?.toPhoneSession() ?: PhoneSession.Unknown)
                } finally {
                    stored.release()
                }
                awaitClose { client.removeListener(listener) }
            }

        private fun DataItem.toPhoneSession(): PhoneSession {
            val json = DataMapItem.fromDataItem(this).dataMap.getString(WearDataKeys.PAYLOAD) ?: return PhoneSession.Unknown
            return when (val result = WearCodec.decode(WearSessionSnapshot.serializer(), json)) {
                is WearDecodeResult.Decoded -> result.value.account?.let(PhoneSession::SignedIn) ?: PhoneSession.SignedOut
                is WearDecodeResult.UnsupportedVersion -> PhoneSession.UpdateRequired
                is WearDecodeResult.Malformed -> {
                    Timber.w(result.cause, "malformed wear session payload")
                    PhoneSession.Unknown
                }
            }
        }
    }

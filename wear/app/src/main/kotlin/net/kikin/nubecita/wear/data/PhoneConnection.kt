package net.kikin.nubecita.wear.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.wearcontract.WearCapabilities
import net.kikin.nubecita.core.wearcontract.WearPaths
import javax.inject.Inject

/** Whether the paired phone has Nubecita, and what kind of phone it is. */
enum class PhoneAppStatus {
    Installed,

    /** An Android phone (or unknown) without the app: offer to install it. */
    MissingOnAndroid,

    /** Paired with an iPhone: there is no app to install. */
    IPhone,
}

/** The watch's view of the phone: app presence, sync requests, opening things there. */
interface PhoneConnection {
    suspend fun phoneAppStatus(): PhoneAppStatus

    /** Asks the phone app to republish its snapshots now (the watch app just opened). */
    suspend fun requestSync()

    /** Opens Nubecita's Play Store listing on the phone (install, or "Open" once installed). */
    fun openNubecitaOnPhone()

    /** Opens [url] on the phone; a `bsky.app` link opens in Nubecita through its App Links. */
    fun openUrlOnPhone(url: String)
}

internal class PlayServicesPhoneConnection
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PhoneConnection {
        override suspend fun phoneAppStatus(): PhoneAppStatus {
            // FILTER_ALL, not REACHABLE: a phone that has the app but is out of
            // Bluetooth range must not get an install prompt.
            val nodes =
                Wearable
                    .getCapabilityClient(context)
                    .getCapability(WearCapabilities.PHONE_APP, CapabilityClient.FILTER_ALL)
                    .await()
                    .nodes
            return when {
                nodes.isNotEmpty() -> PhoneAppStatus.Installed
                PhoneTypeHelper.getPhoneDeviceType(context) == PhoneTypeHelper.DEVICE_TYPE_IOS -> PhoneAppStatus.IPhone
                else -> PhoneAppStatus.MissingOnAndroid
            }
        }

        override suspend fun requestSync() {
            val phones =
                Wearable
                    .getCapabilityClient(context)
                    .getCapability(WearCapabilities.PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
            val messages = Wearable.getMessageClient(context)
            phones.forEach { node -> messages.sendMessage(node.id, WearPaths.SYNC_REQUEST, null).await() }
        }

        override fun openNubecitaOnPhone() = openUrlOnPhone("market://details?id=$PHONE_PACKAGE")

        override fun openUrlOnPhone(url: String) {
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setData(url.toUri())
            // Fire-and-forget: the OpenOnPhoneDialog is the user-facing confirmation.
            RemoteActivityHelper(context).startRemoteActivity(intent)
        }

        private companion object {
            const val PHONE_PACKAGE = "net.kikin.nubecita"
        }
    }

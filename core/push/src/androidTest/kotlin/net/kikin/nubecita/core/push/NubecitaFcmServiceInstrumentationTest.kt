package net.kikin.nubecita.core.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.push.testing.FakeSessionStateProvider
import net.kikin.nubecita.core.push.testing.FakeXrpcClientProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §9.1 of the push proposal — drives [NubecitaFcmService.onMessageReceived]
 * end-to-end on a real Android runtime against a real [NotificationManager].
 *
 * Approach: bypass Hilt's `@AndroidEntryPoint` wrapper and instantiate
 * [NubecitaFcmService] directly, setting each `@Inject lateinit var` field
 * by hand. The collaborator graph is composed of either:
 *
 *  - Real implementations whose construction is trivial here
 *    ([PushDispatcher], [PushNotificationBuilder], [MutedActorRepository]
 *    with a fake [XrpcClientProvider])
 *  - In-memory fakes from `testing/Fakes.kt` ([FakeSessionStateProvider]).
 *
 * This keeps the test focused on the orchestration NubecitaFcmService
 * actually performs (`PushDispatcher.dispatch` → `PushNotificationBuilder.build`
 * → `NotificationManagerCompat.notify`) and uses the real Android
 * `NotificationManager` to verify the channel, title, and tap-intent.
 * The full 10-reason × 3-importance-tier matrix is exercised at the unit
 * level in [PushNotificationBuilderTest][PushNotificationBuilderTest in
 * src/test]; here we cover a couple of representative reasons + every
 * drop branch.
 *
 * The Hilt-scaffolded variant (`@HiltAndroidTest` + `@BindValue`) was
 * considered and rejected: it pulls module-replacement plumbing for
 * dependencies that already factor cleanly (PushModule's binding graph
 * compose by ctor in :app's PushAppConfig; the test doesn't need any of
 * those swaps to make this verification work).
 */
@RunWith(AndroidJUnit4::class)
class NubecitaFcmServiceInstrumentationTest {
    private lateinit var context: Context
    private lateinit var managerCompat: NotificationManagerCompat
    private lateinit var systemManager: NotificationManager
    private lateinit var service: NubecitaFcmService
    private lateinit var mutedActorRepository: MutedActorRepository
    private lateinit var sessionStateProvider: FakeSessionStateProvider
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>

    private val viewerDid = "did:plc:viewer-fcm-instr"
    private val actorDid = "did:plc:alice-instr"
    private val verifierDid = TRUSTED_VERIFIERS.first()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        managerCompat = NotificationManagerCompat.from(context)
        systemManager = context.getSystemService(NotificationManager::class.java)

        // Grant POST_NOTIFICATIONS to the test APK's package so
        // NubecitaFcmService.notificationsAllowed() (which gates publish on
        // Android 13+) returns true. The permission is declared in the
        // androidTest/AndroidManifest.xml; UiAutomation.executeShellCommand
        // flips the runtime bit. Idempotent — re-granting an already-granted
        // permission is a no-op.
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .close()

        // Channels must exist before the builder posts — install them
        // explicitly here. Production wires this at NubecitaApplication.onCreate
        // but the test bypasses Application init by instantiating the service
        // directly.
        NotificationChannelInstaller().install(context)

        // Real MutedActorRepository over a fresh temp DataStore. We never
        // call refresh(), so the snapshot stays empty for the happy-path
        // tests; the muted-actor drop test calls a tiny helper that sets the
        // snapshot directly via a refresh against a stub mute payload.
        dataStoreScope = CoroutineScope(Dispatchers.IO)
        dataStoreFile = File(context.cacheDir, "muted-${System.nanoTime()}.preferences_pb")
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            )
        mutedActorRepository =
            MutedActorRepository(
                xrpcClientProvider = FakeXrpcClientProvider(),
                dataStore = dataStore,
            )

        sessionStateProvider =
            FakeSessionStateProvider(SessionState.SignedIn(handle = "alice.bsky.social", did = viewerDid))

        service =
            NubecitaFcmService().also {
                it.attachBaseContextViaReflection(context)
                it.coordinator =
                    PushRegistrationCoordinator(
                        sessionStateProvider = sessionStateProvider,
                        repository =
                            DefaultPushRegistrationRepository(
                                xrpcClientProvider = FakeXrpcClientProvider(),
                                appId = "net.kikin.nubecita",
                                gateway = PushGatewayConfig.Nubecita,
                            ),
                        stateStore =
                            PushRegistrationStateStore(
                                PreferenceDataStoreFactory.create(
                                    scope = dataStoreScope,
                                    produceFile = { File(context.cacheDir, "reg-${System.nanoTime()}.preferences_pb") },
                                ),
                            ),
                        tokenProvider =
                            object : FcmTokenProvider {
                                override suspend fun current(): String = "fcm-token-unused-in-§9.1"
                            },
                        fcmAutoInit =
                            object : FcmAutoInit {
                                override fun enable() = Unit
                            },
                        scope = CoroutineScope(Dispatchers.Main),
                    )
                it.dispatcher = PushDispatcher()
                it.notificationBuilder = PushNotificationBuilder(android.R.drawable.ic_dialog_info)
                it.mutedActorRepository = mutedActorRepository
                it.sessionStateProvider = sessionStateProvider
                it.appScope = CoroutineScope(Dispatchers.Main)
            }
    }

    @After
    fun tearDown() {
        // Clear any notification the test posted so a re-run starts clean.
        managerCompat.cancelAll()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun onMessageReceived_with_a_like_payload_posts_a_notification_on_the_likes_channel() {
        val payload =
            likeRemoteMessage(
                actorDid = actorDid,
                actorHandle = "alice",
                recipientDid = viewerDid,
                postUri = "at://$viewerDid/app.bsky.feed.post/3kxyz",
            )

        service.onMessageReceived(payload)

        val active = waitForActiveNotifications(expectedAtLeast = 1)
        val individual =
            active.firstOrNull { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
        assertNotNull("expected a non-summary notification to appear", individual)
        assertEquals(
            "like notifications must land on the Likes channel (LOW importance per design)",
            NotificationChannelInstaller.CHANNEL_LIKE,
            individual!!.notification.channelId,
        )
        val title =
            individual.notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
        assertNotNull("title is required for screen-reader accessibility", title)
        assertTrue(
            "title should mention the actor handle (got: $title)",
            title!!.contains("alice"),
        )
    }

    @Test
    fun onMessageReceived_with_a_reply_payload_posts_on_the_high_importance_replies_channel() {
        val payload =
            replyRemoteMessage(
                actorDid = actorDid,
                actorHandle = "alice",
                recipientDid = viewerDid,
                postUri = "at://$viewerDid/app.bsky.feed.post/3kxyz-parent",
            )

        service.onMessageReceived(payload)

        val active = waitForActiveNotifications(expectedAtLeast = 1)
        val individual =
            active.firstOrNull { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
        assertNotNull(individual)
        assertEquals(
            "replies must land on the high-importance Replies channel so they head up",
            NotificationChannelInstaller.CHANNEL_REPLY,
            individual!!.notification.channelId,
        )
    }

    @Test
    fun onMessageReceived_with_a_recipient_did_that_does_not_match_the_active_session_is_dropped() {
        val payload =
            likeRemoteMessage(
                actorDid = actorDid,
                actorHandle = "alice",
                recipientDid = "did:plc:someone-else", // not our session
                postUri = "at://did:plc:someone-else/app.bsky.feed.post/3kxyz",
            )

        service.onMessageReceived(payload)

        Thread.sleep(200) // give the system a beat in case it would have posted
        val active = systemManager.activeNotifications.toList()
        assertEquals(
            "recipient-did mismatch must drop the notification (multi-account isolation)",
            0,
            active.size,
        )
    }

    @Test
    fun onMessageReceived_with_an_untrusted_verifier_did_is_dropped() {
        // Verified-status events are the only push reason that requires the
        // actorDid to be on TRUSTED_VERIFIERS. A spoofed verified event with
        // an unknown actorDid must drop, even if it otherwise looks valid.
        val payload =
            verifiedRemoteMessage(
                actorDid = "did:plc:not-a-trusted-verifier",
                recipientDid = viewerDid,
                verificationRecordUri = "at://did:plc:not-a-trusted-verifier/app.bsky.graph.verification/3kxyz",
            )

        service.onMessageReceived(payload)

        Thread.sleep(200)
        val active = systemManager.activeNotifications.toList()
        assertEquals(
            "untrusted-verifier verified events must drop — defense against spoofed verification claims",
            0,
            active.size,
        )
    }

    @Test
    fun onMessageReceived_with_an_actor_in_the_muted_set_is_dropped() =
        runBlocking {
            // Seed the muted-actor snapshot directly so the dispatcher's mute
            // filter sees `actorDid` as muted. The repository's refresh path
            // is unit-tested separately.
            mutedActorRepository.seedSnapshotForTest(setOf(actorDid))

            val payload =
                likeRemoteMessage(
                    actorDid = actorDid,
                    actorHandle = "alice",
                    recipientDid = viewerDid,
                    postUri = "at://$viewerDid/app.bsky.feed.post/3kxyz-muted",
                )

            service.onMessageReceived(payload)

            delay(200)
            val active = systemManager.activeNotifications.toList()
            assertEquals(
                "muted actors must drop client-side even if the gateway sent the push (gateway can't always know fresh mutes)",
                0,
                active.size,
            )
        }

    @Test
    fun verified_payload_from_a_trusted_verifier_DID_posts_a_notification() {
        // Counterpart to the spoofed-verifier test: the SAME shape with a
        // TRUSTED_VERIFIERS member as actorDid should NOT drop.
        val payload =
            verifiedRemoteMessage(
                actorDid = verifierDid,
                recipientDid = viewerDid,
                verificationRecordUri = "at://$verifierDid/app.bsky.graph.verification/3kxyz",
            )

        service.onMessageReceived(payload)

        val active = waitForActiveNotifications(expectedAtLeast = 1)
        val individual =
            active.firstOrNull { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
        assertNotNull(
            "trusted-verifier verified events must produce a notification",
            individual,
        )
        assertEquals(
            NotificationChannelInstaller.CHANNEL_VERIFIED,
            individual!!.notification.channelId,
        )
    }

    private fun waitForActiveNotifications(expectedAtLeast: Int): List<android.service.notification.StatusBarNotification> {
        // Real-time polling because notify→activeNotifications can lag by a
        // tick on some OEMs. 50ms × 40 iters = 2s timeout.
        repeat(40) {
            val active = systemManager.activeNotifications.toList()
            if (active.size >= expectedAtLeast) return active
            Thread.sleep(50)
        }
        error("timed out waiting for at least $expectedAtLeast active notifications")
    }
}

/**
 * `Service.attachBaseContext` is `protected` on the framework's
 * `ContextWrapper`. The service base class is final by design (no
 * production subclasses), so we reach the protected method by
 * reflection. Single test-source seam; nothing in production code
 * changes.
 */
private fun NubecitaFcmService.attachBaseContextViaReflection(base: Context) {
    val method =
        android.content.ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
    method.isAccessible = true
    method.invoke(this, base)
}

/**
 * Test-only seed for the in-memory mute snapshot. The production
 * `MutedActorRepository.refresh()` paginates `app.bsky.graph.getMutes`
 * which is unit-tested in `:core:push/src/test/`. For instrumentation
 * coverage of the dispatcher's mute branch we just need a deterministic
 * snapshot; reaching into the private MutableStateFlow via a tiny
 * extension keeps this surgical without exposing a public setter on
 * production code.
 */
private fun MutedActorRepository.seedSnapshotForTest(actorDids: Set<String>) {
    val field = MutedActorRepository::class.java.getDeclaredField("_snapshot")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val flow = field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<Set<String>>
    flow.value = actorDids
}

private fun baseDataBundle(
    reason: String,
    actorDid: String,
    actorHandle: String? = null,
    recipientDid: String,
    uri: String,
    subject: String? = null,
): RemoteMessage =
    RemoteMessage
        .Builder("test-recipient/topic")
        .addData("reason", reason)
        .addData("uri", uri)
        .addData("actorDid", actorDid)
        .addData("recipientDid", recipientDid)
        .apply {
            if (actorHandle != null) addData("actorHandle", actorHandle)
            if (subject != null) addData("subject", subject)
        }.build()

private fun likeRemoteMessage(
    actorDid: String,
    actorHandle: String,
    recipientDid: String,
    postUri: String,
): RemoteMessage =
    baseDataBundle(
        reason = "like",
        actorDid = actorDid,
        actorHandle = actorHandle,
        recipientDid = recipientDid,
        uri = postUri,
        subject = postUri,
    )

private fun replyRemoteMessage(
    actorDid: String,
    actorHandle: String,
    recipientDid: String,
    postUri: String,
): RemoteMessage =
    baseDataBundle(
        reason = "reply",
        actorDid = actorDid,
        actorHandle = actorHandle,
        recipientDid = recipientDid,
        uri = postUri,
        subject = postUri,
    )

private fun verifiedRemoteMessage(
    actorDid: String,
    recipientDid: String,
    verificationRecordUri: String,
): RemoteMessage =
    baseDataBundle(
        reason = "verified",
        actorDid = actorDid,
        recipientDid = recipientDid,
        uri = verificationRecordUri,
    )

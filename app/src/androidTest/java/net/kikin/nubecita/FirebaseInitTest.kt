package net.kikin.nubecita

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseInitTest {
    @Test
    fun firebaseApp_isInitializedAfterApplicationOnCreate() {
        val app = FirebaseApp.getInstance()
        assertNotNull(app)
        assertEquals("nubecita-2a4c1", app.options.projectId)
    }

    @Test
    fun firebaseAppCheck_isAvailableAfterApplicationOnCreate() {
        val appCheck = FirebaseAppCheck.getInstance()
        assertNotNull(appCheck)
    }

    @Test
    fun firebaseAnalytics_isAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val analytics = FirebaseAnalytics.getInstance(context)
        assertNotNull(analytics)
    }
}

package net.kikin.nubecita

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidManifestXrFeatureTest {
    @Test
    fun manifestDeclaresOptionalXrSpatialFeatureAndResizeableMainActivity() {
        val manifestFile =
            listOf(
                File("src/main/AndroidManifest.xml"),
                File("app/src/main/AndroidManifest.xml"),
            ).firstOrNull { it.exists() }
        assertNotNull(manifestFile, "AndroidManifest.xml not found")

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val androidNs = "http://schemas.android.com/apk/res/android"

        // 1. Check uses-feature for android.software.xr.api.spatial with required="false"
        val features = doc.getElementsByTagName("uses-feature")
        var xrSpatialFeature: Element? = null
        for (i in 0 until features.length) {
            val el = features.item(i) as Element
            if (el.getAttributeNS(androidNs, "name") == "android.software.xr.api.spatial") {
                xrSpatialFeature = el
                break
            }
        }
        assertNotNull(xrSpatialFeature, "Missing <uses-feature android:name=\"android.software.xr.api.spatial\" />")
        assertEquals("false", xrSpatialFeature?.getAttributeNS(androidNs, "required"))

        // 2. Check MainActivity resizeableActivity="true"
        val activities = doc.getElementsByTagName("activity")
        var mainActivity: Element? = null
        for (i in 0 until activities.length) {
            val el = activities.item(i) as Element
            if (el.getAttributeNS(androidNs, "name") == ".MainActivity") {
                mainActivity = el
                break
            }
        }
        assertNotNull(mainActivity, "Missing .MainActivity declaration")
        assertEquals("true", mainActivity?.getAttributeNS(androidNs, "resizeableActivity"))

        // 3. Check MainActivity contains PROPERTY_XR_ACTIVITY_START_MODE = XR_ACTIVITY_START_MODE_HOME_SPACE
        val properties = checkNotNull(mainActivity).getElementsByTagName("property")
        var xrStartModeProperty: Element? = null
        for (i in 0 until properties.length) {
            val el = properties.item(i) as Element
            if (el.getAttributeNS(androidNs, "name") == "android.window.PROPERTY_XR_ACTIVITY_START_MODE") {
                xrStartModeProperty = el
                break
            }
        }
        assertNotNull(xrStartModeProperty, "Missing android.window.PROPERTY_XR_ACTIVITY_START_MODE property on MainActivity")
        assertEquals("XR_ACTIVITY_START_MODE_HOME_SPACE", xrStartModeProperty?.getAttributeNS(androidNs, "value"))
    }
}

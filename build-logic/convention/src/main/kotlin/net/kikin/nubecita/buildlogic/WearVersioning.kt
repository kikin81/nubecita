package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import java.util.Properties

/**
 * Offset that puts every Wear `versionCode` in its own band above every phone
 * code. Play requires a Wear bundle's code to be unique across all form
 * factors, and the Wear packaging docs recommend a scheme independent of the
 * phone's. Phone codes are `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`
 * (`app/build.gradle.kts`), so they stay below 1e9 until major 1000 — the
 * Wear band can never collide, and it rises with every release exactly as the
 * phone code does.
 */
internal const val WEAR_VERSION_CODE_OFFSET = 1_000_000_000

/**
 * `versionCode` for the Wear bundle built from semantic [versionName]:
 * [WEAR_VERSION_CODE_OFFSET] + the phone scheme. The phone scheme is mirrored
 * here (a build script's top-level function can't be imported); keep the two
 * in lockstep, as `VersionCodeParserTest` / `WearVersionCodeTest` pin them.
 */
internal fun wearVersionCode(versionName: String): Int {
    val core = versionName.substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    require(parts.size == 3) { "Expected MAJOR.MINOR.PATCH, got: $versionName" }
    val (major, minor, patch) = parts.map(String::toInt)
    // Max major keeping the Wear code within Int.MAX_VALUE (2_147_483_647).
    require(major in 0..1146) { "Major must be in 0..1146 for the Wear versionCode band: $versionName" }
    require(minor in 0..999 && patch in 0..999) { "Minor and patch must be in 0..999: $versionName" }
    val code = WEAR_VERSION_CODE_OFFSET.toLong() + major * 1_000_000L + minor * 1_000L + patch
    check(code in 0..Int.MAX_VALUE) { "Wear versionCode $code overflowed Int.MAX_VALUE" }
    return code.toInt()
}

/**
 * Release signing for the Wear bundle: the same upload key as the phone app
 * (Play App Signing then signs both with the app signing key — the Data Layer
 * and Play `WO-G7` require one signature). Read from `keystore.properties`
 * or the `KEYSTORE_*` / `KEY_*` env vars, exactly like `:app`. When neither
 * resolves, the release signing config stays empty and `bundleRelease` fails
 * with AGP's "no signing config" error rather than shipping debug-signed.
 */
internal fun Project.configureWearReleaseSigning(extension: ApplicationExtension) {
    val keystoreProps =
        Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use(::load)
        }

    fun value(
        propKey: String,
        envKey: String,
    ): String? =
        keystoreProps.getProperty(propKey)?.takeIf(String::isNotEmpty)
            ?: providers.environmentVariable(envKey).orNull?.takeIf(String::isNotEmpty)

    val release = extension.signingConfigs.create("release")
    value("storeFile", "KEYSTORE_FILE")?.let { raw ->
        release.storeFile = file(if (raw.startsWith("~")) System.getProperty("user.home") + raw.removePrefix("~") else raw)
        release.storePassword = value("storePassword", "KEYSTORE_PASSWORD")
        release.keyAlias = value("keyAlias", "KEY_ALIAS")
        release.keyPassword = value("keyPassword", "KEY_PASSWORD")
        extension.buildTypes.getByName("release").signingConfig = release
    }
}

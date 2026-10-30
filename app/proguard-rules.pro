# Project-specific R8 / ProGuard rules for the :app release build.
#
# This file is intentionally minimal. Modern AndroidX, Compose, Hilt, Firebase,
# Coil, OkHttp, Ktor, and kotlinx.serialization all ship consumer ProGuard
# rules that R8 picks up automatically from their AARs/JARs — duplicating
# those here just rots. Add a rule below only when a release build actually
# breaks (NoSuchMethodError, ClassNotFoundException, missing serializer, etc.)
# and document *why* alongside the rule so we can re-evaluate later.

# --- Crashlytics --------------------------------------------------------------
# Preserve source/line metadata so obfuscated stack traces remain mappable
# back to source lines after R8 renaming. The Crashlytics gradle plugin
# auto-uploads the mapping file during release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Android XR system extensions ---------------------------------------------
# androidx.xr.scenecore's split engine (com.google.androidxr.splitengine,
# com.google.ar.imp) references the XR system extension library, which only
# Android XR devices provide at runtime; the XR AARs ship no rule for it, so R8
# fails with "Missing class com.android.extensions.xr.*" (nubecita-2mnp). The
# classes are never on the classpath and those paths only run on a headset, so
# silencing the warning changes nothing on phones.
-dontwarn com.android.extensions.xr.**
-dontwarn android.extensions.xr.**

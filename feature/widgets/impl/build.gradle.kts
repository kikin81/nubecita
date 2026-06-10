plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.widgets.impl"
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    // head(feedKey, n) / FeedKey / FeedRepository — the cache the widgets render.
    implementation(project(":core:feed-cache"))
    // Saved/pinned feeds for the configurable widget's configuration activity.
    implementation(project(":core:feeds"))
    // The B-side seams this module supplies the real (Glance-backed)
    // implementations for: WidgetUpdater + WidgetImagePrefetcher. Bound via
    // Hilt in :app, never referenced as types downstream, so `implementation`
    // (nothing depends on this :impl except :app for graph assembly).
    implementation(project(":core:widget-sync"))

    // Jetpack Glance — the ONLY Glance surface in the app. Compose-runtime,
    // not Compose-UI: these widget composables compile to RemoteViews and
    // cannot reuse PostCard / Coil composables / Material3.
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

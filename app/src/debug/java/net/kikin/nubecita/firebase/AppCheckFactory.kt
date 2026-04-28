package net.kikin.nubecita.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun appCheckFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()

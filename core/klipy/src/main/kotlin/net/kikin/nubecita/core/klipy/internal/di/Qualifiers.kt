package net.kikin.nubecita.core.klipy.internal.di

import javax.inject.Qualifier

/**
 * The `HttpClient` configured for KLIPY — its own instance (own base URL + key,
 * own key-redacting logger), kept distinct from `:core:auth`'s AT-Protocol
 * client which also lives in `SingletonComponent`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class KlipyClient

/**
 * The preferences `DataStore` backing KLIPY's `customer_id` — qualified so it
 * doesn't collide with `:core:preferences`' unqualified `DataStore<Preferences>`
 * in the same Hilt component.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class KlipyPreferences

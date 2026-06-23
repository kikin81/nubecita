package net.kikin.nubecita.core.review.di

import javax.inject.Qualifier

/**
 * Qualifies the review capability's own `DataStore<Preferences>` so it doesn't
 * collide with `:core:preferences`' unqualified user-preferences DataStore on
 * the `:app` Hilt graph.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ReviewDataStore

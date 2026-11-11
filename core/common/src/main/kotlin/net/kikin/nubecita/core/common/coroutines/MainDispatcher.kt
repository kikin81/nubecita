package net.kikin.nubecita.core.common.coroutines

import javax.inject.Qualifier

/**
 * Hilt qualifier for the main-thread [kotlinx.coroutines.CoroutineDispatcher]
 * (`Dispatchers.Main.immediate`).
 *
 * For app-scoped components that own Compose snapshot state read during
 * composition: writing that state on the main thread keeps every write on the
 * same thread as the reads. Inject it rather than referencing
 * `Dispatchers.Main` directly, so a JVM test can substitute the test
 * dispatcher. The binding lives in
 * [net.kikin.nubecita.core.common.coroutines.di.DispatchersModule].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

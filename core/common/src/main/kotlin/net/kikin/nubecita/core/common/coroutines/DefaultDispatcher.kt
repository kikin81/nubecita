package net.kikin.nubecita.core.common.coroutines

import javax.inject.Qualifier

/**
 * Hilt qualifier for a CPU-bound [kotlinx.coroutines.CoroutineDispatcher].
 * Inject as `@DefaultDispatcher dispatcher: CoroutineDispatcher` in
 * components that do non-trivial in-memory computation (image
 * decode/encode, JSON parsing of large payloads, hashing, etc.).
 *
 * Distinct from [IoDispatcher] so blocking I/O doesn't compete with
 * compute on the same thread pool. The binding itself is provided in
 * [net.kikin.nubecita.core.common.coroutines.di.DispatchersModule].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

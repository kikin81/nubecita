package net.kikin.nubecita.ui.mvi

/**
 * Lifecycle of an asynchronously loaded value.
 *
 * Prefer [Async] over the `isLoading: Boolean + errorMessage: String? + data: T?`
 * triplet in [UiState] fields — it collapses four mutually-exclusive cases into
 * one exhaustive `when`.
 *
 * Adding a new variant requires updating [map] and [getOrNull] in this file.
 */
sealed interface Async<out T> {
    data object Uninitialized : Async<Nothing>

    data object Loading : Async<Nothing>

    data class Success<out T>(
        val value: T,
    ) : Async<T>

    data class Failure(
        val error: Throwable,
    ) : Async<Nothing>
}

/** Returns the success value or `null` for every other variant. */
fun <T> Async<T>.getOrNull(): T? = (this as? Async.Success<T>)?.value

/**
 * Transforms the value inside a [Async.Success], leaving every other variant
 * unchanged. [transform] is NOT invoked for non-success variants.
 */
inline fun <T, R> Async<T>.map(transform: (T) -> R): Async<R> =
    when (this) {
        is Async.Success -> Async.Success(transform(value))
        is Async.Failure -> this
        Async.Loading -> Async.Loading
        Async.Uninitialized -> Async.Uninitialized
    }

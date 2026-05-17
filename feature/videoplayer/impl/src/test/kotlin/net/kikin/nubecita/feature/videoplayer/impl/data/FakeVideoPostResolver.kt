package net.kikin.nubecita.feature.videoplayer.impl.data

internal class FakeVideoPostResolver : VideoPostResolver {
    private val byUri = mutableMapOf<String, Result<ResolvedVideoPost>>()

    fun stub(
        postUri: String,
        resolved: ResolvedVideoPost,
    ) {
        byUri[postUri] = Result.success(resolved)
    }

    fun stubFailure(
        postUri: String,
        throwable: Throwable,
    ) {
        byUri[postUri] = Result.failure(throwable)
    }

    override suspend fun resolve(postUri: String): Result<ResolvedVideoPost> = byUri[postUri] ?: Result.failure(IllegalStateException("No stub for $postUri"))
}

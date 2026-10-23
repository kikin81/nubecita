package net.kikin.nubecita.feature.profile.impl.data

import javax.inject.Inject

/** Bench flavor is network-free: links are added without a fetched icon. */
internal class BenchLinkFaviconFetcher
    @Inject
    constructor() : LinkFaviconFetcher {
        override suspend fun fetch(url: String): ByteArray? = null
    }

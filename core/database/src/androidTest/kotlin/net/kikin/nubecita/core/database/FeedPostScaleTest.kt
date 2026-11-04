package net.kikin.nubecita.core.database

import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * Scale stress test — the **D-A2 gate** for the denormalized `feed_post` schema.
 *
 * Design decision D-A2 (offline feed cache) stores each post **denormalized
 * per feed partition** (a post that appears in N feeds is written N times),
 * trading write amplification + disk for dead-simple, index-friendly reads and
 * a trivially reversible path to a hybrid (post-table + join) schema later. The
 * decision is only sound while:
 *   1. the head/paging reads stay flat — an index prefix-scan, never a full
 *      table scan, regardless of how many partitions/rows the cache holds; and
 *   2. per-partition write ops (REFRESH clear+insert, trimToCap) stay cheap; and
 *   3. the on-disk size stays in a tolerable range at power-user scale.
 *
 * This test populates a power-user-scale cache (>=10 partitions x cap=500 posts,
 * cross-feed overlap, ~1-3 KB representative `post_blob` JSON each) against a
 * **real file-based** Room DB (so the DB file size is measurable), then RECORDS
 * the numbers via [Log] under tag [TAG] and asserts the D-A2 invariants. The
 * recorded numbers (logcat) tell us the real threshold at which migrating
 * denormalized -> hybrid would start to pay off (D-A2 reversibility).
 *
 * Runs only under the instrumented test job (CI `run-instrumented` label / a
 * connected device); it exercises the real Room query planner + SQLite write
 * perf, which an in-memory or JVM test cannot represent faithfully.
 */
@RunWith(AndroidJUnit4::class)
internal class FeedPostScaleTest {
    private lateinit var db: NubecitaDatabase
    private lateinit var dbFile: java.io.File

    @Before
    fun setupFileDatabase() {
        // Use the *target* (app-under-test) context so the DB file lives in the
        // app's data dir and its on-disk size is measurable.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = context.getDatabasePath(DB_NAME)
        // Start from a clean slate (Room creates these lazily).
        context.deleteDatabase(DB_NAME)
        db =
            Room
                .databaseBuilder(context, NubecitaDatabase::class.java, DB_NAME)
                .build()
    }

    @After
    fun teardownFileDatabase() {
        // Guard against a setup failure leaving `db` uninitialized — closing an
        // uninitialized lateinit would throw and mask the real setup exception.
        if (::db.isInitialized) {
            db.close()
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
    }

    @Test
    fun scale_powerUserCache_readsStayFlat_writesStayCheap_sizeBounded() =
        runTest {
            val dao = db.feedPostDao()
            val cachedDao = db.cachedPostDao()
            val itemDao = db.feedItemDao()

            // ---- populate: >=PARTITIONS partitions x CAP posts, cross-feed overlap ----
            val partitions = buildPartitions()
            assertTrue("expected >= 10 partitions", partitions.size >= 10)

            var totalInserted = 0
            partitions.forEach { partition ->
                val generated = (0 until CAP).map { entryAt(partition, it) }
                // distinctBy mirrors the upsert: shared thread context arrives
                // many times and collapses to one row, so counting it once here
                // keeps the reported insert total honest.
                cachedDao.upsert(generated.flatMap { it.posts }.distinctBy { it.accountDid to it.uri })
                dao.upsert(generated.map { it.envelope })
                itemDao.upsert(generated.map { it.item })
                itemDao.upsertMembers(generated.flatMap { it.members })
                // One seam per partition: a caught-up feed has one, and it is
                // part of the shape being measured.
                itemDao.upsert(
                    listOf(
                        FeedItemEntity(
                            accountDid = partition.accountDid,
                            feedType = partition.feedType,
                            feedUri = partition.feedUri,
                            position = -1,
                            rowType = FeedItemEntity.ROW_TYPE_SEAM,
                            itemKey = "seam:${partition.feedUri}",
                            seamExhausted = false,
                        ),
                    ),
                )
                totalInserted += generated.size
            }

            // ---- D-A2 invariant 1: reads use an index, never a full SCAN ----
            val sample = partitions.first()
            val headPlan =
                queryPlan(
                    "EXPLAIN QUERY PLAN SELECT * FROM feed_post " +
                        "WHERE account_did = ? AND feed_type = ? AND feed_uri = ? " +
                        "ORDER BY position LIMIT ?",
                    arrayOf(sample.accountDid, sample.feedType, sample.feedUri, "$CAP"),
                )
            Log.i(TAG, "HEAD query plan: $headPlan")
            assertUsesIndex("head", headPlan)

            // The paging query is the same WHERE+ORDER BY without the LIMIT.
            val pagePlan =
                queryPlan(
                    "EXPLAIN QUERY PLAN SELECT * FROM feed_post " +
                        "WHERE account_did = ? AND feed_type = ? AND feed_uri = ? " +
                        "ORDER BY position",
                    arrayOf(sample.accountDid, sample.feedType, sample.feedUri),
                )
            Log.i(TAG, "PAGE query plan: $pagePlan")
            assertUsesIndex("page", pagePlan)

            // by-uri lookup (deep-link / interactions overlay) must use the uri index.
            val byUriPlan =
                queryPlan(
                    "EXPLAIN QUERY PLAN SELECT * FROM feed_post WHERE uri = ?",
                    arrayOf(SHARED_POST_URIS.first()),
                )
            Log.i(TAG, "BY-URI query plan: $byUriPlan")
            assertTrue(
                "by-uri lookup must use index_feed_post_uri, plan was: $byUriPlan",
                byUriPlan.contains("index_feed_post_uri"),
            )
            assertTrue(
                "by-uri lookup must not full-scan feed_post, plan was: $byUriPlan",
                !byUriPlan.contains("SCAN feed_post"),
            )

            // ---- D-A2 invariant 2: per-partition writes stay cheap ----
            // REFRESH-shaped op: clear the partition + re-insert CAP rows.
            // Row construction is hoisted OUT of the measured block on purpose.
            // representativeBlob builds a 1-3 KB string per row via repeat(), so
            // 500 of them inside measureMillis would charge blob construction to
            // the database and report a write regression that is really a
            // measurement artifact.
            val refreshEntries = (0 until CAP).map { entryAt(sample, it, salt = "refresh-") }
            val refreshPosts = refreshEntries.flatMap { it.posts }.distinctBy { it.accountDid to it.uri }
            val refreshRows = refreshEntries.map { it.envelope }
            val refreshItems = refreshEntries.map { it.item }
            val refreshMembers = refreshEntries.flatMap { it.members }
            var clearMs = 0L
            var postsMs = 0L
            var envelopesMs = 0L
            var projectionMs = 0L
            val refreshMs =
                measureMillis {
                    db.withTransaction {
                        clearMs =
                            measureMillis {
                                dao.clearPartition(sample.accountDid, sample.feedType, sample.feedUri)
                                itemDao.clearPartition(sample.accountDid, sample.feedType, sample.feedUri)
                            }
                        postsMs = measureMillis { cachedDao.upsert(refreshPosts) }
                        envelopesMs = measureMillis { dao.upsert(refreshRows) }
                        projectionMs =
                            measureMillis {
                                itemDao.upsert(refreshItems)
                                itemDao.upsertMembers(refreshMembers)
                            }
                    }
                }
            Log.i(
                TAG,
                "REFRESH (clear + upsert $CAP entries = ${refreshPosts.size} posts, " +
                    "${refreshRows.size} envelopes, ${refreshItems.size} items, " +
                    "${refreshMembers.size} members) took $refreshMs ms " +
                    "(clear=$clearMs posts=$postsMs envelopes=$envelopesMs projection=$projectionMs)",
            )

            val trimMs =
                measureMillis {
                    dao.trimToCap(sample.accountDid, sample.feedType, sample.feedUri, CAP / 2)
                }
            Log.i(TAG, "trimToCap(cap=${CAP / 2}) took $trimMs ms")

            assertTrue("REFRESH write pathological ($refreshMs ms)", refreshMs < WRITE_BOUND_MS)
            assertTrue("trimToCap pathological ($trimMs ms)", trimMs < WRITE_BOUND_MS)

            // ---- D-A2 invariant 3: row count + DB file size recorded ----
            val rowCount = rowCount()
            val perTable =
                listOf("feed_post", "cached_post", "feed_item", "feed_item_member")
                    .joinToString(", ") { "$it=${countOf(it)}" }
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .close()
            val sizeBytes = totalDbBytes()
            Log.i(
                TAG,
                "rows: $perTable (entries inserted=$totalInserted across ${partitions.size} partitions); " +
                    "DB file size=${sizeBytes / 1024} KB (${"%.2f".format(sizeBytes / 1024.0 / 1024.0)} MB)",
            )
            assertTrue("expected >= 5000 rows, got $rowCount", rowCount >= 5_000)
            assertTrue("DB unexpectedly large: ${sizeBytes / 1024 / 1024} MB", sizeBytes < DB_SIZE_BOUND_BYTES)
        }

    // --- helpers ---------------------------------------------------------

    /** >=10 distinct partitions: FOLLOWING + several DISCOVER/CUSTOM/LIST feeds. */
    private fun buildPartitions(): List<Partition> {
        val account = "did:plc:poweruser"
        val list = mutableListOf(Partition(account, "FOLLOWING", ""))
        list += (0 until 4).map { Partition(account, "DISCOVER", "at://did:plc:gen$it/app.bsky.feed.generator/discover$it") }
        list += (0 until 4).map { Partition(account, "CUSTOM", "at://did:plc:gen$it/app.bsky.feed.generator/custom$it") }
        list += (0 until 2).map { Partition(account, "LIST", "at://did:plc:gen$it/app.bsky.graph.list/list$it") }
        return list
    }

    /**
     * Everything one feed entry contributes to the cache: its envelope, the
     * posts it brings, its projected item, and that item's membership rows.
     *
     * Generating all four together is the point of this fixture. A per-entry
     * row count of one understates the real shape badly: a reply entry carries
     * its own post **plus** its thread root and parent, so it writes three
     * posts, three membership rows and a cluster item. Measuring only
     * envelopes-plus-one-post reports a database the app never has.
     */
    private data class GeneratedEntry(
        val envelope: FeedPostEntity,
        val posts: List<CachedPostEntity>,
        val item: FeedItemEntity,
        val members: List<FeedItemMemberEntity>,
    )

    /**
     * One entry at [position], with its variant chosen by position so the mix
     * is deterministic and reproducible across runs.
     *
     * The proportions — roughly 60% plain, 20% repost, 20% reply — are a stated
     * assumption, not a measurement; a Following timeline's real mix varies per
     * reader. What matters for the figure is that replies are a meaningful
     * minority rather than absent, because they are the only variant that
     * multiplies the post count.
     *
     * [salt] namespaces the generated URIs. The REFRESH measurement passes one
     * so its page is genuinely **new** content, as a refresh after any elapsed
     * time would be. Without it the batch regenerates the URIs the bulk seed
     * already wrote, so every upsert becomes an UPDATE of an identical row —
     * measurably cheaper than the inserts production does, and not the number
     * anyone wants.
     *
     * Thread context is drawn from a small shared pool rather than generated
     * fresh per reply. Real threads carry several replies, so their root and
     * parent recur — and the by-URI store collapses the repeats to one row.
     * Unique context per reply would inflate the size figure with rows
     * production would have de-duplicated.
     */
    private fun entryAt(
        partition: Partition,
        position: Int,
        salt: String = "",
    ): GeneratedEntry {
        val ownUri =
            if (position < OVERLAP && salt.isEmpty()) {
                SHARED_POST_URIS[position % SHARED_POST_URIS.size]
            } else {
                "at://did:plc:author$position/app.bsky.feed.post/$salt${partition.feedUri}-$position"
            }
        val own = cachedPostRow(partition, position, ownUri)

        return when (position % 5) {
            3 -> {
                // Repost: one post, but the envelope carries the attribution.
                GeneratedEntry(
                    envelope =
                        envelopeFor(partition, position, ownUri).copy(
                            repostedByDid = "did:plc:reposter${position % 50}",
                            repostedByLabel = "Reposter ${position % 50}",
                        ),
                    posts = listOf(own),
                    item = itemFor(partition, position, variant = "single", key = "single:$ownUri", leaf = ownUri),
                    members = listOf(memberFor(partition, position, 0, ownUri)),
                )
            }
            4 -> {
                // Reply: three posts and a cluster of three members.
                val thread = position / THREAD_FANOUT
                val rootUri = "at://did:plc:threadroot$salt$thread/app.bsky.feed.post/root"
                val parentUri = "at://did:plc:threadroot$salt$thread/app.bsky.feed.post/parent"
                GeneratedEntry(
                    envelope =
                        envelopeFor(partition, position, ownUri).copy(
                            replyRootUri = rootUri,
                            replyParentUri = parentUri,
                        ),
                    posts =
                        listOf(
                            own,
                            cachedPostRow(partition, position, rootUri),
                            cachedPostRow(partition, position, parentUri),
                        ),
                    item = itemFor(partition, position, variant = "cluster", key = "cluster:$rootUri", leaf = ownUri),
                    members =
                        listOf(
                            memberFor(partition, position, 0, rootUri),
                            memberFor(partition, position, 1, parentUri),
                            memberFor(partition, position, 2, ownUri),
                        ),
                )
            }
            else ->
                GeneratedEntry(
                    envelope = envelopeFor(partition, position, ownUri),
                    posts = listOf(own),
                    item = itemFor(partition, position, variant = "single", key = "single:$ownUri", leaf = ownUri),
                    members = listOf(memberFor(partition, position, 0, ownUri)),
                )
        }
    }

    private fun envelopeFor(
        partition: Partition,
        position: Int,
        postUri: String,
    ) = FeedPostEntity(
        accountDid = partition.accountDid,
        feedType = partition.feedType,
        feedUri = partition.feedUri,
        position = position,
        uri = postUri,
    )

    private fun itemFor(
        partition: Partition,
        position: Int,
        variant: String,
        key: String,
        leaf: String,
    ) = FeedItemEntity(
        accountDid = partition.accountDid,
        feedType = partition.feedType,
        feedUri = partition.feedUri,
        position = position,
        rowType = FeedItemEntity.ROW_TYPE_ITEM,
        variant = variant,
        itemKey = key,
        leafUri = leaf,
        suppressedReplyCount = if (variant == "cluster") position % 4 else 0,
    )

    private fun memberFor(
        partition: Partition,
        position: Int,
        ordinal: Int,
        uri: String,
    ) = FeedItemMemberEntity(
        accountDid = partition.accountDid,
        feedType = partition.feedType,
        feedUri = partition.feedUri,
        position = position,
        ordinal = ordinal,
        uri = uri,
    )

    /**
     * The stored post an entry references. The blob is a wire `postView`, which
     * is the right shape: after the split, `cached_post` stores posts and the
     * entry-level `reason` / `reply` live in the envelope's columns.
     */
    private fun cachedPostRow(
        partition: Partition,
        position: Int,
        postUri: String,
    ) = CachedPostEntity(
        accountDid = partition.accountDid,
        uri = postUri,
        cid = "cid-$position",
        authorDid = "did:plc:author${position % 200}",
        indexedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L + position),
        text = "post $position in ${partition.feedType}/${partition.feedUri}",
        postBlob = representativeBlob(postUri, position),
    )

    /** Run a raw `EXPLAIN QUERY PLAN` and concatenate the `detail` column rows. */
    private fun queryPlan(
        sql: String,
        args: Array<String>,
    ): String {
        val cursor = db.openHelper.writableDatabase.query(sql, args)
        return cursor.use { c ->
            val detailCol = c.getColumnIndex("detail")
            buildString {
                while (c.moveToNext()) {
                    if (length > 0) append(" | ")
                    append(if (detailCol >= 0) c.getString(detailCol) else c.getString(c.columnCount - 1))
                }
            }
        }
    }

    private fun assertUsesIndex(
        label: String,
        plan: String,
    ) {
        // A prefix-scan over the composite PRIMARY KEY (which leads with the
        // partition columns + position) satisfies both the WHERE and the
        // ORDER BY. Room may name it PRIMARY KEY or a generated index.
        assertTrue(
            "$label query must SEARCH USING (PRIMARY KEY|INDEX), plan was: $plan",
            Regex("SEARCH .*USING (PRIMARY KEY|INDEX)").containsMatchIn(plan),
        )
        assertTrue(
            "$label query must NOT full-scan feed_post, plan was: $plan",
            !plan.contains("SCAN feed_post"),
        )
    }

    private fun countOf(table: String): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun rowCount(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM feed_post").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    /** Total on-disk size of the DB (main file + WAL + shm). */
    private fun totalDbBytes(): Long {
        val dir = dbFile.parentFile
        val name = dbFile.name
        return listOf(name, "$name-wal", "$name-shm")
            .mapNotNull { dir?.resolve(it) }
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    /** Wall-clock duration of [block] in whole ms, measured via [System.nanoTime]. */
    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private data class Partition(
        val accountDid: String,
        val feedType: String,
        val feedUri: String,
    )

    private companion object {
        const val TAG = "FeedPostScaleTest"
        const val DB_NAME = "feed-scale-test-db"
        const val CAP = 500
        const val OVERLAP = 50

        /** Replies per synthetic thread, so root/parent recur as they really do. */
        const val THREAD_FANOUT = 10
        const val WRITE_BOUND_MS = 1_000L
        const val DB_SIZE_BOUND_BYTES = 256L * 1024 * 1024

        /** A pool of post URIs reused across partitions to force cross-feed overlap. */
        val SHARED_POST_URIS: List<String> =
            (0 until OVERLAP).map { "at://did:plc:shared/app.bsky.feed.post/shared-$it" }

        /**
         * A ~1-3 KB representative serialized PostView-shaped JSON blob. Padded
         * with a deterministic filler so the column carries realistic bytes.
         */
        fun representativeBlob(
            postUri: String,
            position: Int,
        ): String {
            val filler = "lorem ipsum dolor sit amet ".repeat(40 + (position % 40)) // ~1-3 KB
            return """{"${'$'}type":"app.bsky.feed.defs#postView",""" +
                """"uri":"$postUri","cid":"bafyrei$position",""" +
                """"author":{"did":"did:plc:author${position % 200}","handle":"a$position.bsky.social","displayName":"Author $position"},""" +
                """"record":{"${'$'}type":"app.bsky.feed.post","text":"$filler","createdAt":"2026-04-15T19:51:12.861Z","langs":["en"]},""" +
                """"replyCount":${position % 7},"repostCount":${position % 13},"likeCount":${position % 97},""" +
                """"indexedAt":"2026-04-15T19:51:13.000Z"}"""
        }
    }
}

package net.kikin.nubecita.core.database

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.model.FeedPostEntity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

            // ---- populate: >=PARTITIONS partitions x CAP posts, cross-feed overlap ----
            val partitions = buildPartitions()
            assertTrue("expected >= 10 partitions", partitions.size >= 10)

            var totalInserted = 0
            partitions.forEach { partition ->
                val rows =
                    (0 until CAP).map { position ->
                        // Cross-feed overlap: the first OVERLAP positions reuse a
                        // shared pool of post URIs so the same post `uri` recurs
                        // across partitions (denormalization writes it per-partition).
                        val postUri =
                            if (position < OVERLAP) {
                                SHARED_POST_URIS[position % SHARED_POST_URIS.size]
                            } else {
                                "at://did:plc:author$position/app.bsky.feed.post/${partition.feedUri}-$position"
                            }
                        rowFor(partition, position, postUri)
                    }
                dao.upsert(rows)
                totalInserted += rows.size
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
            val refreshRows = (0 until CAP).map { rowFor(sample, it, "at://refresh/$it") }
            val refreshMs =
                measureMillis {
                    // Mirror the mediator REFRESH shape: clear the partition, then
                    // insert page rows. (The real mediator wraps these in one
                    // withTransaction; the timing is dominated by the same SQL.)
                    dao.clearPartition(sample.accountDid, sample.feedType, sample.feedUri)
                    dao.upsert(refreshRows)
                }
            Log.i(TAG, "REFRESH (clearPartition + upsert $CAP rows) took $refreshMs ms")

            val trimMs =
                measureMillis {
                    dao.trimToCap(sample.accountDid, sample.feedType, sample.feedUri, CAP / 2)
                }
            Log.i(TAG, "trimToCap(cap=${CAP / 2}) took $trimMs ms")

            assertTrue("REFRESH write pathological ($refreshMs ms)", refreshMs < WRITE_BOUND_MS)
            assertTrue("trimToCap pathological ($trimMs ms)", trimMs < WRITE_BOUND_MS)

            // ---- D-A2 invariant 3: row count + DB file size recorded ----
            val rowCount = rowCount()
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .close()
            val sizeBytes = totalDbBytes()
            Log.i(
                TAG,
                "feed_post rows=$rowCount (inserted=$totalInserted across ${partitions.size} partitions); " +
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

    private fun rowFor(
        partition: Partition,
        position: Int,
        postUri: String,
    ): FeedPostEntity =
        FeedPostEntity(
            accountDid = partition.accountDid,
            feedType = partition.feedType,
            feedUri = partition.feedUri,
            position = position,
            uri = postUri,
            cid = "bafyrei$position",
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

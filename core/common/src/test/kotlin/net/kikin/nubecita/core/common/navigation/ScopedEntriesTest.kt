package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the `contentKey` scheme of [scopedEntries] (nubecita-owmt.4): tab roots
 * keep their registered key, and every sub-route is unique per tab and per
 * occurrence, deterministically from the stack contents.
 */
class ScopedEntriesTest {
    @Serializable private data object TabA : NavKey

    @Serializable private data object TabB : NavKey

    @Serializable private data class Post(
        val uri: String,
    ) : NavKey

    private val provider: (NavKey) -> NavEntry<NavKey> = { key ->
        NavEntry(key = key, metadata = mapOf("route" to key.toString()), content = {})
    }

    private fun contentKeys(vararg keys: NavKey): List<Any> = scopedEntries(keys.toList(), provider).map { it.contentKey }

    @Test
    fun `tab root keeps the registered contentKey`() {
        assertEquals(provider(TabA).contentKey, contentKeys(TabA, Post("x")).first())
    }

    @Test
    fun `same route on two tabs gets different contentKeys`() {
        val onA = contentKeys(TabA, Post("x"))[1]
        val onB = contentKeys(TabB, Post("x"))[1]

        assertNotEquals(onA, onB)
    }

    @Test
    fun `same route twice on one tab gets different contentKeys`() {
        val keys = contentKeys(TabA, Post("x"), Post("y"), Post("x"))

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `contentKeys are stable across rebuilds of the same stack`() {
        assertEquals(
            contentKeys(TabA, Post("x"), Post("y"), Post("x")),
            contentKeys(TabA, Post("x"), Post("y"), Post("x")),
        )
    }

    @Test
    fun `an entry keeps its contentKey when the entry beneath it is removed`() {
        // replaceTop: [root, picker] -> [root, picker, target] -> [root, target]
        val before = contentKeys(TabA, Post("picker"), Post("target"))[2]
        val after = contentKeys(TabA, Post("target"))[1]

        assertEquals(before, after)
    }

    @Test
    fun `an entry keeps its contentKey when entries are pushed above it`() {
        val alone = contentKeys(TabA, Post("x"))[1]
        val buried = contentKeys(TabA, Post("x"), Post("y"), Post("x"))[1]

        assertEquals(alone, buried)
    }

    @Test
    fun `sub-route entries keep their key and metadata`() {
        val entry = scopedEntries(listOf(TabA, Post("x")), provider)[1]

        assertEquals(mapOf("route" to Post("x").toString()), entry.metadata)
        assertTrue(entry.contentKey is String)
    }

    @Test
    fun `empty stack yields no entries`() {
        assertEquals(emptyList<NavEntry<NavKey>>(), scopedEntries(emptyList(), provider))
    }
}

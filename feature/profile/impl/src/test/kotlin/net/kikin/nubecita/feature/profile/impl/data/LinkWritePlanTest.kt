package net.kikin.nubecita.feature.profile.impl.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LinkWritePlanTest {
    private fun saved(
        rkey: String,
        url: String = "https://$rkey.example",
        title: String? = null,
    ) = EditableProfileLink(localId = rkey, url = url, title = title, uri = "at://me/app.bsky.actor.link/$rkey", cid = "cid-$rkey")

    @Test
    fun `untouched saved links are reused`() {
        val a = saved("a")
        val plan = planLinkWrites(listOf(a), listOf(a))
        assertEquals(listOf(LinkWrite.Reuse(a)), plan.writes)
        assertEquals(emptyList<String>(), plan.deletes)
    }

    @Test
    fun `title, url, or icon changes are puts`() {
        val a = saved("a")
        val retitled = a.copy(title = "A")
        val moved = a.copy(url = "https://elsewhere.example")
        val reiconed = a.copy(pendingIcon = PendingIcon(byteArrayOf(1)))
        assertEquals(listOf(LinkWrite.Put(retitled)), planLinkWrites(listOf(retitled), listOf(a)).writes)
        assertEquals(listOf(LinkWrite.Put(moved)), planLinkWrites(listOf(moved), listOf(a)).writes)
        assertEquals(listOf(LinkWrite.Put(reiconed)), planLinkWrites(listOf(reiconed), listOf(a)).writes)
    }

    @Test
    fun `links without a uri are creates`() {
        val c = EditableProfileLink(localId = "new-1", url = "https://c.example", title = null)
        assertEquals(listOf(LinkWrite.Create(c)), planLinkWrites(listOf(c), emptyList()).writes)
    }

    @Test
    fun `loaded links missing from the draft are deleted, order follows the draft`() {
        val a = saved("a")
        val b = saved("b")
        val d = saved("d")
        val c = EditableProfileLink(localId = "new-1", url = "https://c.example", title = null)
        val plan = planLinkWrites(listOf(b, c, a), listOf(a, b, d))
        assertEquals(listOf(LinkWrite.Reuse(b), LinkWrite.Create(c), LinkWrite.Reuse(a)), plan.writes)
        assertEquals(listOf("at://me/app.bsky.actor.link/d"), plan.deletes)
    }

    @Test
    fun `a reorder alone writes no link record`() {
        val a = saved("a")
        val b = saved("b")
        val plan = planLinkWrites(listOf(b, a), listOf(a, b))
        assertEquals(listOf(LinkWrite.Reuse(b), LinkWrite.Reuse(a)), plan.writes)
        assertEquals(emptyList<String>(), plan.deletes)
    }
}

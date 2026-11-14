package com.omniweb

import com.omniweb.ui.ForceStore
import com.omniweb.ui.ForcedItem
import com.omniweb.ui.GOOGLE_IMAGES
import com.omniweb.ui.KeyValueStore
import com.omniweb.ui.normalizeUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForceStoreTest {
    private class MemoryStore : KeyValueStore {
        val m = HashMap<String, Any>()
        override fun getString(key: String) = m[key] as String?
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun getLong(key: String, default: Long) = m[key] as Long? ?: default
        override fun putString(key: String, value: String) { m[key] = value }
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun putLong(key: String, value: Long) { m[key] = value }
    }

    @Test fun googleImagesAlwaysFirst() {
        assertEquals(GOOGLE_IMAGES, ForceStore(MemoryStore()).sites().first())
    }

    @Test fun addsCustomSitesAndKeepsThem() {
        val mem = MemoryStore()
        val a = ForceStore(mem).addSite("YouTube", "https://m.youtube.com")
        ForceStore(mem).addSite("Amazon", "https://www.amazon.es")
        val sites = ForceStore(mem).sites()
        assertEquals(listOf("google-images", "youtube", a.id, "c2"), sites.map { it.id })
        assertEquals("https://m.youtube.com", sites[2].startUrl)
    }

    @Test fun siteNamesCannotBreakTheEncoding() {
        val mem = MemoryStore()
        ForceStore(mem).addSite("a\tb\nc", "https://x.com/?q=1\t2")
        val s = ForceStore(mem).sites().last()
        assertEquals("a b c", s.name)
        assertEquals("https://x.com/?q=1 2", s.startUrl)
    }

    @Test fun savesAndLoadsAForcedItem() {
        val st = ForceStore(MemoryStore())
        assertNull(st.load("google-images"))
        val item = ForcedItem("data:image/jpeg;base64,AAAA", listOf("Título", "Fuente\ncon salto"), "https://g.co/x?y=1", 1.5f)
        st.save("google-images", item)
        assertEquals(item, st.load("google-images"))
    }

    @Test fun oneForcedItemPerSiteLastWins() {
        val st = ForceStore(MemoryStore())
        st.save("s", ForcedItem("data:a", listOf("1", "2", "3"), "p", 1f))
        st.save("s", ForcedItem("data:b", listOf("x"), "q", 2f))
        assertEquals(listOf("x"), st.load("s")!!.texts)
        assertEquals("data:b", st.load("s")!!.imageData)
    }

    @Test fun clearRemovesTheItem() {
        val st = ForceStore(MemoryStore())
        st.save("s", ForcedItem("data:a", listOf("1"), "p", 1f))
        st.clear("s")
        assertNull(st.load("s"))
    }

    @Test fun normalizeUrlCases() {
        assertEquals("https://a.com", normalizeUrl("https://a.com"))
        assertEquals("https://a.com", normalizeUrl(" a.com "))
        assertEquals("https://www.google.com/search?tbm=isch&q=gatos+negros", normalizeUrl("gatos negros"))
    }
}

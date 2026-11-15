package com.omniweb

import com.omniweb.ui.AMAZON
import com.omniweb.ui.BUILT_IN_SITES
import com.omniweb.ui.BlobStore
import com.omniweb.ui.ForceStore
import com.omniweb.ui.ForcedItem
import com.omniweb.ui.GOOGLE_IMAGES
import com.omniweb.ui.KeyValueStore
import com.omniweb.ui.SiteSettings
import com.omniweb.ui.isExternalScheme
import com.omniweb.ui.normalizeUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private class MemoryBlobs : BlobStore {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, data: String) { m[name] = data }
        override fun delete(name: String) { m.remove(name) }
    }

    private fun store(prefs: MemoryStore = MemoryStore(), blobs: MemoryBlobs = MemoryBlobs()) = ForceStore(prefs, blobs)

    @Test fun builtInSitesFirst() {
        assertEquals(BUILT_IN_SITES, store().sites())
        assertEquals(GOOGLE_IMAGES, store().sites().first())
    }

    @Test fun addsCustomSitesAndKeepsThem() {
        val prefs = MemoryStore()
        val blobs = MemoryBlobs()
        val a = store(prefs, blobs).addSite("Wikipedia", "https://es.wikipedia.org")
        store(prefs, blobs).addSite("Ebay", "https://www.ebay.es")
        val sites = store(prefs, blobs).sites()
        assertEquals(BUILT_IN_SITES.map { it.id } + listOf(a.id, "c2"), sites.map { it.id })
        assertEquals("https://es.wikipedia.org", sites[BUILT_IN_SITES.size].startUrl)
    }

    @Test fun siteNamesCannotBreakTheEncoding() {
        val prefs = MemoryStore()
        store(prefs).addSite("a\tb\nc", "https://x.com/?q=1\t2")
        val s = store(prefs).sites().last()
        assertEquals("a b c", s.name)
        assertEquals("https://x.com/?q=1 2", s.startUrl)
    }

    @Test fun removesOnlyCustomSitesAndTheirItem() {
        val st = store()
        val c = st.addSite("Ebay", "https://www.ebay.es")
        st.save(c.id, ForcedItem("data:a", listOf("t"), null, "p", 1f))
        assertTrue(st.isCustom(c.id))
        assertTrue(st.removeSite(c.id))
        assertFalse(st.sites().any { it.id == c.id })
        assertNull(st.load(c.id))
        assertFalse(st.removeSite(AMAZON.id)) // los predefinidos no se quitan
        assertTrue(st.sites().any { it.id == AMAZON.id })
    }

    @Test fun settingsDefaultsAndPersistence() {
        val prefs = MemoryStore()
        val blobs = MemoryBlobs()
        assertEquals(SiteSettings(auto = true, idleMs = 450, desktop = false), store(prefs, blobs).settings("youtube"))
        store(prefs, blobs).saveSettings("youtube", SiteSettings(auto = false, idleMs = 600, desktop = true))
        assertEquals(SiteSettings(false, 600, true), store(prefs, blobs).settings("youtube"))
        // independientes por sitio
        assertEquals(SiteSettings(), store(prefs, blobs).settings("amazon"))
    }

    @Test fun chromeSettingDefaultsOnAndPersists() {
        val prefs = MemoryStore()
        assertTrue(store(prefs).settings("s").chrome)
        store(prefs).saveSettings("s", SiteSettings(chrome = false))
        assertFalse(store(prefs).settings("s").chrome)
    }

    @Test fun siteNames() {
        assertEquals("wikipedia.org", com.omniweb.ui.siteNameFor("https://www.wikipedia.org/wiki/X"))
        assertEquals("page.html", com.omniweb.ui.siteNameFor("file:///android_asset/test/page.html"))
    }

    @Test fun displayHostShowsOnlyTheDomain() {
        assertEquals("google.com", com.omniweb.ui.displayHost("https://www.google.com/search?q=gatos&udm=2"))
        assertEquals("youtube.com", com.omniweb.ui.displayHost("https://m.youtube.com/results?search_query=x"))
        assertEquals("amazon.es", com.omniweb.ui.displayHost("https://www.amazon.es"))
        assertEquals("localhost:8080", com.omniweb.ui.displayHost("http://localhost:8080/a#b"))
    }

    @Test fun hapticsDefaultOnAndPersists() {
        val prefs = MemoryStore()
        assertTrue(store(prefs).haptics())
        store(prefs).setHaptics(false)
        assertFalse(store(prefs).haptics())
    }

    @Test fun practiceModeFlag() {
        val prefs = MemoryStore()
        assertFalse(store(prefs).practiceMode())
        store(prefs).setPracticeMode(true)
        assertTrue(store(prefs).practiceMode())
    }

    @Test fun savesAndLoadsAForcedItem() {
        val st = store()
        assertNull(st.load("google-images"))
        val item = ForcedItem("data:image/jpeg;base64,AAAA", listOf("Título", "Fuente\ncon salto"), "<div>a\tb\n<b>x</b></div>", "https://g.co/x?y=1", 1.5f)
        st.save("google-images", item)
        assertEquals(item, st.load("google-images"))
    }

    @Test fun bigPayloadsGoToTheBlobStoreNotThePreferences() {
        val prefs = MemoryStore()
        val blobs = MemoryBlobs()
        store(prefs, blobs).save("s", ForcedItem("data:IMG", listOf("t"), "<p>html</p>", "p", 1f))
        assertEquals("data:IMG", blobs.read("f.s.image"))
        assertEquals("<p>html</p>", blobs.read("f.s.html"))
        assertEquals("", prefs.getString("f.s.image"))
        assertEquals("", prefs.getString("f.s.html"))
    }

    @Test fun readsLegacyItemsStoredInPreferences() {
        val prefs = MemoryStore()
        prefs.putString("f.s.image", "data:OLD")
        prefs.putString("f.s.html", "<i>old</i>")
        prefs.putLong("f.s.n", 1L)
        prefs.putString("f.s.t0", "viejo")
        prefs.putString("f.s.page", "p")
        val item = store(prefs).load("s")!!
        assertEquals("data:OLD", item.imageData)
        assertEquals("<i>old</i>", item.html)
        assertEquals(listOf("viejo"), item.texts)
    }

    @Test fun oneForcedItemPerSiteLastWins() {
        val st = store()
        st.save("s", ForcedItem("data:a", listOf("1", "2", "3"), "<b>1</b>", "p", 1f))
        st.save("s", ForcedItem("data:b", listOf("x"), null, "q", 2f))
        assertEquals(listOf("x"), st.load("s")!!.texts)
        assertEquals("data:b", st.load("s")!!.imageData)
        assertNull(st.load("s")!!.html) // el HTML anterior no sobrevive
    }

    @Test fun clearRemovesTheItemEverywhere() {
        val blobs = MemoryBlobs()
        val st = store(blobs = blobs)
        st.save("s", ForcedItem("data:a", listOf("1"), "<b/>", "p", 1f))
        st.clear("s")
        assertNull(st.load("s"))
        assertTrue(blobs.m.isEmpty())
    }

    @Test fun summaryDoesNotNeedTheBlobs() {
        val prefs = MemoryStore()
        val blobs = MemoryBlobs()
        assertNull(store(prefs, blobs).summary("s"))
        store(prefs, blobs).save("s", ForcedItem("data:IMG", listOf("Título", "Fuente"), "<p/>", "p", 1f))
        blobs.m.clear() // aunque los ficheros grandes no estén, el resumen sale de las preferencias
        assertEquals("Título", store(prefs, blobs).summary("s")!!.title)
    }

    @Test fun summaryIsGoneAfterClear() {
        val st = store()
        st.save("s", ForcedItem("data:a", listOf("t"), null, "p", 1f))
        st.clear("s")
        assertNull(st.summary("s"))
    }

    @Test fun normalizeUrlCases() {
        assertEquals("https://a.com", normalizeUrl("https://a.com"))
        assertEquals("https://a.com", normalizeUrl(" a.com "))
        assertEquals("https://www.google.com/search?tbm=isch&q=gatos+negros", normalizeUrl("gatos negros"))
    }

    @Test fun externalSchemesAreBlockedButWebOnesAreNot() {
        assertTrue(isExternalScheme("intent://scan/#Intent;scheme=zxing;end"))
        assertTrue(isExternalScheme("market://details?id=com.amazon.mShop.android.shopping"))
        assertTrue(isExternalScheme("youtube://watch?v=1"))
        assertTrue(isExternalScheme("AMZN://x"))
        assertTrue(isExternalScheme("tel:123"))
        assertFalse(isExternalScheme("https://m.youtube.com/watch?v=1"))
        assertFalse(isExternalScheme("http://a.com"))
        assertFalse(isExternalScheme("about:blank"))
        assertFalse(isExternalScheme("data:image/png;base64,AAAA"))
        assertFalse(isExternalScheme("file:///android_asset/test/page.html"))
        assertFalse(isExternalScheme("sin-esquema"))
    }
}

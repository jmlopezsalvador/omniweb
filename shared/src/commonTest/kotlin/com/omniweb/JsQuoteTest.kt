package com.omniweb

import com.omniweb.ui.DetailItem
import com.omniweb.ui.ForceConfig
import com.omniweb.ui.HomeShortcut
import com.omniweb.ui.jsQuote
import com.omniweb.ui.toJs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsQuoteTest {
    @Test fun escapesQuotesBackslashesAndNewlines() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", jsQuote("a\"b\\c\nd"))
    }

    @Test fun escapesLineSeparatorsAndTagOpeners() {
        val q = jsQuote("x\u2028y\u2029z</script>")
        assertTrue(q.contains("\\u2028") && q.contains("\\u2029"))
        assertFalse(q.contains("<"))
        assertFalse(q.contains('\u2028'))
    }

    @Test fun escapesControlCharacters() {
        assertEquals("\"\\u0001\"", jsQuote("\u0001"))
    }

    @Test fun configLiteralIsWellFormed() {
        val js = ForceConfig("data:image/png;base64,AA", listOf("a\"b", "c"), "<p class=\"x\">hola</p>", 600, false).toJs()
        assertTrue(js.startsWith("{imageUrl:\"data:image/png;base64,AA\",texts:[\"a\\\"b\",\"c\"],html:"))
        assertTrue(js.endsWith("session:0,idleMs:600,mode:'tap',armed:false}"))
        assertFalse(js.contains("\n"))
    }

    @Test fun configWithoutHtmlOmitsTheField() {
        val js = ForceConfig("u", emptyList()).toJs()
        assertEquals("{imageUrl:\"u\",texts:[],session:0,idleMs:450,mode:'tap',armed:true}", js)
    }

    @Test fun detailIsIncludedOnlyWhenPresent() {
        val js = ForceConfig("u", detail = DetailItem("data:d", listOf("a\"b"), "<p>x</p>"), session = 3).toJs()
        assertTrue(js.contains("detail:{imageUrl:\"data:d\",texts:[\"a\\\"b\"],html:\"\\u003cp>x\\u003c/p>\"},"))
        assertTrue(js.contains("session:3,"))
        assertFalse(ForceConfig("u").toJs().contains("detail"))
        assertFalse(ForceConfig("u", detail = DetailItem("d", emptyList(), null)).toJs().contains("html"))
    }

    @Test fun shortcutsAreIncludedOnlyWhenPresent() {
        val js = ForceConfig("", shortcuts = listOf(HomeShortcut("a", "Am\"azon", "data:i"), HomeShortcut("y", "YouTube", null))).toJs()
        assertTrue(js.contains("shortcuts:[{id:\"a\",name:\"Am\\\"azon\",icon:\"data:i\"},{id:\"y\",name:\"YouTube\"}],"))
        assertFalse(ForceConfig("u").toJs().contains("shortcuts"))
    }

    @Test fun reportScrollIsOptIn() {
        assertTrue(ForceConfig("u", reportScroll = true).toJs().contains("reportScroll:true,"))
        assertFalse(ForceConfig("u").toJs().contains("reportScroll"))
    }
}

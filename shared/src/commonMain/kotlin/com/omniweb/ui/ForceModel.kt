package com.omniweb.ui

/** Web añadida por el mago. */
data class Site(val id: String, val name: String, val startUrl: String)

/** Lo que entrega el motor JS al tocar un elemento en modo captura. */
data class Capture(
    val imageUrl: String,
    /** Bytes de la imagen como data: URL si el WebView pudo leerlos; si no, la app la descarga desde `imageUrl`. */
    val imageData: String?,
    val texts: List<String>,
    val pageUrl: String,
    val aspect: Float,
)

/** Forzaje guardado de un sitio: un elemento ya existente en la web, con su imagen en local. */
data class ForcedItem(
    val imageData: String,
    val texts: List<String>,
    /** Página donde se capturó; el modo efecto la abre ya preparada. */
    val pageUrl: String,
    val aspect: Float,
)

val GOOGLE_IMAGES = Site("google-images", "Google Imágenes", "https://www.google.com/imghp?hl=es")
val YOUTUBE = Site("youtube", "YouTube", "https://m.youtube.com")

/** Añade https:// si falta y convierte texto suelto en una búsqueda de Google Imágenes. */
fun normalizeUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
        t.contains('.') && !t.contains(' ') -> "https://$t"
        else -> "https://www.google.com/search?tbm=isch&q=${t.replace(' ', '+')}"
    }
}

/** Sitios y forzajes en almacenamiento local. Un forzaje por sitio (el último capturado). */
class ForceStore(private val prefs: KeyValueStore) {
    fun sites(): List<Site> = listOf(GOOGLE_IMAGES, YOUTUBE) + customSites()

    private fun customSites(): List<Site> =
        (prefs.getString("sites") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
            val p = it.split('\t')
            if (p.size == 3) Site(p[0], p[1], p[2]) else null
        }

    fun addSite(name: String, url: String): Site {
        val n = prefs.getLong("sites.next", 1L)
        val clean = { s: String -> s.replace('\t', ' ').replace('\n', ' ').trim() }
        val site = Site("c$n", clean(name).ifBlank { clean(url) }, clean(url))
        val all = customSites() + site
        prefs.putString("sites", all.joinToString("\n") { "${it.id}\t${it.name}\t${it.startUrl}" })
        prefs.putLong("sites.next", n + 1)
        return site
    }

    fun load(siteId: String): ForcedItem? {
        val image = prefs.getString("f.$siteId.image")
        if (image.isNullOrEmpty()) return null
        val n = prefs.getLong("f.$siteId.n", 0L).toInt()
        val texts = (0 until n).map { prefs.getString("f.$siteId.t$it") ?: "" }
        return ForcedItem(
            imageData = image,
            texts = texts,
            pageUrl = prefs.getString("f.$siteId.page") ?: "",
            aspect = prefs.getLong("f.$siteId.aspect", 1000L) / 1000f,
        )
    }

    fun save(siteId: String, item: ForcedItem) {
        prefs.putString("f.$siteId.image", item.imageData)
        prefs.putLong("f.$siteId.n", item.texts.size.toLong())
        item.texts.forEachIndexed { i, t -> prefs.putString("f.$siteId.t$i", t) }
        prefs.putString("f.$siteId.page", item.pageUrl)
        prefs.putLong("f.$siteId.aspect", (item.aspect * 1000).toLong())
    }

    fun clear(siteId: String) {
        prefs.putString("f.$siteId.image", "")
    }
}

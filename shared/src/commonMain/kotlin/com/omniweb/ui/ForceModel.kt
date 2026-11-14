package com.omniweb.ui

/** Web añadida por el mago. */
data class Site(val id: String, val name: String, val startUrl: String)

/** Lo que entrega el motor JS al tocar un elemento en modo captura. */
data class Capture(
    val imageUrl: String,
    /** Bytes de la imagen como data: URL si el WebView pudo leerlos; si no, la app la descarga desde `imageUrl`. */
    val imageData: String?,
    val texts: List<String>,
    /** Instantánea HTML de la tarjeta (null si no era clonable: en ese caso se usan los textos). */
    val html: String? = null,
    val pageUrl: String,
    val aspect: Float,
)

/** Forzaje guardado de un sitio: un elemento ya existente en la web, con su imagen en local. */
data class ForcedItem(
    val imageData: String,
    val texts: List<String>,
    /** Instantánea HTML de la tarjeta capturada; el efecto la reinserta tal cual (prioridad sobre `texts`). */
    val html: String? = null,
    /** Página donde se capturó; el modo efecto la abre ya preparada. */
    val pageUrl: String,
    val aspect: Float,
)

/** Ajustes de un sitio (valen para preparar y para el efecto). */
data class SiteSettings(
    /** true = fuerza solo al parar el scroll; false = solo con el gesto oculto (esquina inferior derecha / volumen). */
    val auto: Boolean = true,
    /** Tiempo sin scroll para considerar que el espectador ha parado. */
    val idleMs: Int = 450,
    /** Pide la web de escritorio (user agent de PC). */
    val desktop: Boolean = false,
)

val IDLE_OPTIONS = listOf(300, 450, 600)

val GOOGLE_IMAGES = Site("google-images", "Google Imágenes", "https://www.google.com/imghp?hl=es")
val YOUTUBE = Site("youtube", "YouTube", "https://m.youtube.com")
val AMAZON = Site("amazon", "Amazon", "https://www.amazon.es/s?k=auriculares")
val BUILT_IN_SITES = listOf(GOOGLE_IMAGES, YOUTUBE, AMAZON)

/** Añade https:// si falta y convierte texto suelto en una búsqueda de Google Imágenes. */
fun normalizeUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
        t.contains('.') && !t.contains(' ') -> "https://$t"
        else -> "https://www.google.com/search?tbm=isch&q=${t.replace(' ', '+')}"
    }
}

private val INTERNAL_SCHEMES = setOf("http", "https", "about", "data", "blob", "file", "javascript")

/**
 * ¿Es una URL que abriría otra app (intent://, market://, youtube://, amzn://…)? Se bloquea para que un toque
 * del espectador no saque a OmniWeb de la pantalla (enlaces universales / esquemas propios de las apps).
 */
fun isExternalScheme(url: String): Boolean {
    val i = url.indexOf(':')
    if (i <= 0) return false
    return url.substring(0, i).lowercase() !in INTERNAL_SCHEMES
}

/** Almacén de textos grandes (imagen en data: URL, HTML de la tarjeta). Android: ficheros; iOS: ver `actual`. */
interface BlobStore {
    fun read(name: String): String?
    fun write(name: String, data: String)
    fun delete(name: String)
}

/** Sitios, ajustes y forzajes en almacenamiento local. Un forzaje por sitio (el último capturado). */
class ForceStore(private val prefs: KeyValueStore, private val blobs: BlobStore) {
    fun sites(): List<Site> = BUILT_IN_SITES + customSites()

    private fun customSites(): List<Site> =
        (prefs.getString("sites") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
            val p = it.split('\t')
            if (p.size == 3) Site(p[0], p[1], p[2]) else null
        }

    private fun saveCustom(all: List<Site>) =
        prefs.putString("sites", all.joinToString("\n") { "${it.id}\t${it.name}\t${it.startUrl}" })

    fun addSite(name: String, url: String): Site {
        val n = prefs.getLong("sites.next", 1L)
        val clean = { s: String -> s.replace('\t', ' ').replace('\n', ' ').trim() }
        val site = Site("c$n", clean(name).ifBlank { clean(url) }, clean(url))
        saveCustom(customSites() + site)
        prefs.putLong("sites.next", n + 1)
        return site
    }

    /** Quita un sitio añadido por el usuario (y su forzaje). Los predefinidos no se pueden quitar. */
    fun removeSite(id: String): Boolean {
        val custom = customSites()
        if (custom.none { it.id == id }) return false
        saveCustom(custom.filterNot { it.id == id })
        clear(id)
        return true
    }

    fun isCustom(id: String) = customSites().any { it.id == id }

    fun settings(siteId: String) = SiteSettings(
        auto = prefs.getBoolean("s.$siteId.auto", true),
        idleMs = prefs.getLong("s.$siteId.idle", 450L).toInt(),
        desktop = prefs.getBoolean("s.$siteId.desktop", false),
    )

    fun saveSettings(siteId: String, s: SiteSettings) {
        prefs.putBoolean("s.$siteId.auto", s.auto)
        prefs.putLong("s.$siteId.idle", s.idleMs.toLong())
        prefs.putBoolean("s.$siteId.desktop", s.desktop)
    }

    /** Modo práctica: al salir del efecto se borran historial, cookies y caché. */
    fun practiceMode(): Boolean = prefs.getBoolean("practice", false)
    fun setPracticeMode(on: Boolean) = prefs.putBoolean("practice", on)

    fun load(siteId: String): ForcedItem? {
        // Compatibilidad: antes la imagen y el HTML vivían en las preferencias.
        val image = blobs.read("f.$siteId.image") ?: prefs.getString("f.$siteId.image")
        if (image.isNullOrEmpty()) return null
        val n = prefs.getLong("f.$siteId.n", 0L).toInt()
        val texts = (0 until n).map { prefs.getString("f.$siteId.t$it") ?: "" }
        return ForcedItem(
            imageData = image,
            texts = texts,
            html = (blobs.read("f.$siteId.html") ?: prefs.getString("f.$siteId.html"))?.takeIf { it.isNotEmpty() },
            pageUrl = prefs.getString("f.$siteId.page") ?: "",
            aspect = prefs.getLong("f.$siteId.aspect", 1000L) / 1000f,
        )
    }

    fun save(siteId: String, item: ForcedItem) {
        blobs.write("f.$siteId.image", item.imageData)
        if (item.html != null) blobs.write("f.$siteId.html", item.html) else blobs.delete("f.$siteId.html")
        prefs.putString("f.$siteId.image", "") // sin copia en preferencias (pueden ser MB)
        prefs.putString("f.$siteId.html", "")
        prefs.putLong("f.$siteId.n", item.texts.size.toLong())
        item.texts.forEachIndexed { i, t -> prefs.putString("f.$siteId.t$i", t) }
        prefs.putString("f.$siteId.page", item.pageUrl)
        prefs.putLong("f.$siteId.aspect", (item.aspect * 1000).toLong())
    }

    fun clear(siteId: String) {
        blobs.delete("f.$siteId.image")
        blobs.delete("f.$siteId.html")
        prefs.putString("f.$siteId.image", "")
        prefs.putString("f.$siteId.html", "")
    }
}

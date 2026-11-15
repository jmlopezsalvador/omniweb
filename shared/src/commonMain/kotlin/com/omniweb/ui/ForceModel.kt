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
    /** Página de la ficha del elemento (el enlace de su tarjeta), si el motor la encontró. */
    val detailUrl: String? = null,
    val pageUrl: String,
    val aspect: Float,
)

/** Lo capturado en la FICHA del objeto (el panel o la página que abre la web al tocar un elemento de la lista). */
data class DetailItem(
    val imageData: String,
    val texts: List<String>,
    /** Instantánea HTML del contenedor del medio principal de la ficha (prioridad sobre `texts`). */
    val html: String? = null,
)

/** Forzaje guardado de un sitio: un elemento ya existente en la web, con su imagen en local. */
data class ForcedItem(
    val imageData: String,
    val texts: List<String>,
    /** Instantánea HTML de la tarjeta capturada; el efecto la reinserta tal cual (prioridad sobre `texts`). */
    val html: String? = null,
    /**
     * Ficha: lo capturado dentro de la ficha. Al forzar, la web abre SU ficha de forma natural y el motor sustituye en ella el
     * medio principal por este. null = solo se sustituye la tarjeta de la lista y el toque no llega a la web.
     */
    val detail: DetailItem? = null,
    /** Página donde se capturó (informativa: el efecto abre el inicio del sitio). */
    val pageUrl: String,
    val aspect: Float,
)

/** Lo mínimo de un forzaje para pintar la lista: sin leer la imagen (pueden ser MB). */
data class ForcedSummary(val title: String, val hasDetail: Boolean = false)

/** Ajustes de un sitio (valen para preparar y para el efecto). */
data class SiteSettings(
    /** Armado: true = el efecto arranca armado (la siguiente pulsación que no sea scroll fuerza); false = se arma con el gesto secreto (esquina inferior derecha / volumen), con vibración. */
    val auto: Boolean = true,
    /** Tiempo sin scroll para considerar que el espectador ha parado. */
    val idleMs: Int = 450,
    /** Pide la web de escritorio (user agent de PC). */
    val desktop: Boolean = false,
    /** Marco de navegador decorativo en el efecto (barra de URL y, en iOS, barra inferior). */
    val chrome: Boolean = true,
)

val IDLE_OPTIONS = listOf(300, 450, 600)

val GOOGLE_IMAGES = Site("google-images", "Google Imágenes", "https://www.google.com/imghp?hl=es")
val YOUTUBE = Site("youtube", "YouTube", "https://m.youtube.com")
val AMAZON = Site("amazon", "Amazon", "https://www.amazon.es")
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

/** Nombre por defecto de un sitio añadido: el dominio (sin www) o, en archivos locales, el nombre del fichero. */
fun siteNameFor(url: String): String =
    if (url.startsWith("file://")) url.substringAfterLast('/').ifBlank { url } else displayHost(url)

/** URL abreviada para mostrar en textos: sin esquema ni `www.`, recortada. */
fun shortUrl(url: String, max: Int = 46): String {
    val t = url.substringAfter("://", url).removePrefix("www.")
    return if (t.length > max) t.take(max) + "…" else t
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
        chrome = prefs.getBoolean("s.$siteId.chrome", true),
    )

    fun saveSettings(siteId: String, s: SiteSettings) {
        prefs.putBoolean("s.$siteId.auto", s.auto)
        prefs.putLong("s.$siteId.idle", s.idleMs.toLong())
        prefs.putBoolean("s.$siteId.desktop", s.desktop)
        prefs.putBoolean("s.$siteId.chrome", s.chrome)
    }

    /** Vibración al armar/desarmar el efecto con el gesto secreto. */
    fun haptics(): Boolean = prefs.getBoolean("haptics", true)
    fun setHaptics(on: Boolean) = prefs.putBoolean("haptics", on)

    /** Modo práctica: al salir del efecto se borran historial, cookies y caché. */
    /** Aspecto de la pestaña Favoritos: [HOME_GRID] (cuadrícula de iconos) o [HOME_GOOGLE] (web de Google con la fila de sitios). */
    fun homeStyle(): String = prefs.getString("homeStyle")?.takeIf { it == HOME_GOOGLE } ?: HOME_GRID
    fun setHomeStyle(style: String) = prefs.putString("homeStyle", if (style == HOME_GOOGLE) HOME_GOOGLE else HOME_GRID)

    fun practiceMode(): Boolean = prefs.getBoolean("practice", false)
    fun setPracticeMode(on: Boolean) = prefs.putBoolean("practice", on)

    /**
     * ¿Hay forzaje y cómo se llama? Solo mira las preferencias (número de textos y el primero): la pantalla de inicio
     * lo usa para todos los sitios y leer las imágenes completas la congelaba ~1 s con unos pocos MB guardados.
     */
    fun summary(siteId: String): ForcedSummary? {
        if (prefs.getLong("f.$siteId.n", 0L) <= 0L) return null
        return ForcedSummary(
            title = prefs.getString("f.$siteId.t0")?.takeIf { it.isNotBlank() } ?: "imagen",
            hasDetail = prefs.getBoolean("f.$siteId.d.has", false),
        )
    }

    private fun loadDetail(siteId: String): DetailItem? {
        if (!prefs.getBoolean("f.$siteId.d.has", false)) return null
        val image = blobs.read("f.$siteId.d.image")
        if (image.isNullOrEmpty()) return null
        val n = prefs.getLong("f.$siteId.d.n", 0L).toInt()
        return DetailItem(
            imageData = image,
            texts = (0 until n).map { prefs.getString("f.$siteId.d.t$it") ?: "" },
            html = blobs.read("f.$siteId.d.html")?.takeIf { it.isNotEmpty() },
        )
    }

    private fun saveDetail(siteId: String, d: DetailItem?) {
        if (d == null) {
            prefs.putBoolean("f.$siteId.d.has", false)
            blobs.delete("f.$siteId.d.image")
            blobs.delete("f.$siteId.d.html")
            return
        }
        blobs.write("f.$siteId.d.image", d.imageData)
        if (d.html != null) blobs.write("f.$siteId.d.html", d.html) else blobs.delete("f.$siteId.d.html")
        prefs.putLong("f.$siteId.d.n", d.texts.size.toLong())
        d.texts.forEachIndexed { i, t -> prefs.putString("f.$siteId.d.t$i", t) }
        prefs.putBoolean("f.$siteId.d.has", true)
    }

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
            detail = loadDetail(siteId),
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
        saveDetail(siteId, item.detail)
        prefs.putLong("f.$siteId.aspect", (item.aspect * 1000).toLong())
    }

    fun clear(siteId: String) {
        prefs.putLong("f.$siteId.n", 0L)
        saveDetail(siteId, null)
        blobs.delete("f.$siteId.image")
        blobs.delete("f.$siteId.html")
        prefs.putString("f.$siteId.image", "")
        prefs.putString("f.$siteId.html", "")
    }
}

const val HOME_GRID = "grid"
const val HOME_GOOGLE = "google"

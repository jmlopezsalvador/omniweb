package com.omniweb.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Icono de cada sitio. NO se incluye ningún logo en la app: se descarga el que publica el propio sitio (como hace cualquier
 * navegador) directamente desde su dominio (sin servicios intermedios) y se guarda en local. Si no hay, se pinta la inicial.
 */

/** Decodifica una imagen (png, jpg, ico…) a un `ImageBitmap`; null si no se puede. Cada plataforma usa su decodificador. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** Orígenes donde probar: el del sitio y, si es la versión móvil (`m.`), también la de escritorio (`www.`). */
internal fun faviconOrigins(startUrl: String): List<String> {
    if (!startUrl.startsWith("http://") && !startUrl.startsWith("https://")) return emptyList()
    val scheme = startUrl.substringBefore("://")
    val host = startUrl.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    val origins = mutableListOf("$scheme://$host")
    if (host.startsWith("m.")) origins += "$scheme://www.${host.removePrefix("m.")}"
    return origins
}

@OptIn(ExperimentalEncodingApi::class)
internal fun dataUrlBytes(dataUrl: String): ByteArray? =
    runCatching { Base64.decode(dataUrl.substringAfter("base64,")) }.getOrNull()

private const val NO_ICON = "none"

/** data: URL del icono del sitio (descargándolo y cacheándolo en `blobs` la primera vez); null si no tiene. */
suspend fun faviconData(site: Site, blobs: BlobStore): String? {
    val key = "fav.${site.id}"
    var data = blobs.read(key)
    if (data == null) {
        for (origin in faviconOrigins(site.startUrl)) {
            data = fetchImageDataUrl("$origin/apple-touch-icon.png", "") ?: fetchImageDataUrl("$origin/favicon.ico", "")
            if (data != null && dataUrlBytes(data)?.let { decodeImageBitmap(it) } != null) break
            data = null
        }
        blobs.write(key, data ?: NO_ICON) // «none» evita reintentar en cada apertura
    }
    return data?.takeIf { it != NO_ICON }
}

/** Icono del sitio (null mientras carga o si no tiene). Se descarga una vez y se cachea en `blobs` (`fav.<id>`). */
@Composable
fun rememberFavicon(site: Site, blobs: BlobStore): ImageBitmap? {
    var bitmap by remember(site.id, site.startUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(site.id, site.startUrl) {
        val data = faviconData(site, blobs)
        if (data != null) bitmap = dataUrlBytes(data)?.let { decodeImageBitmap(it) }
    }
    return bitmap
}

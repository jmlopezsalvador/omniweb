package com.omniweb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Configuración que se entrega al motor JS (`OmniWeb.configure`). */
data class ForceConfig(
    /** data: URL (o URL) de la imagen que se pone en lugar del elemento central. */
    val imageUrl: String,
    /** Textos de la tarjeta capturada, por posición; sustituyen a los de la tarjeta forzada. */
    val texts: List<String> = emptyList(),
    /** Instantánea HTML de la tarjeta capturada; si hay, tiene prioridad sobre `texts`. */
    val html: String? = null,
    val idleMs: Int = 450,
    /** Armado: true = el truco está armado (la siguiente pulsación que no sea scroll fuerza); false = hay que armarlo con el gesto secreto. */
    val auto: Boolean = true,
    /** El motor avisa de la dirección del scroll (la barra inferior decorativa se esconde al bajar). */
    val reportScroll: Boolean = false,
    /** Ficha capturada: si hay, el toque llega a la web (abre su ficha) y el motor sustituye dentro el medio principal. */
    val detail: DetailItem? = null,
    /** Sesión del efecto: al cambiar («Reiniciar») el motor olvida lo ya forzado, aunque la página no se recargue por completo. */
    val session: Int = 0,
    /** «Inicio Google»: si no está vacío, el motor no fuerza nada y muestra bajo el buscador de Google esta fila de sitios. */
    val shortcuts: List<HomeShortcut> = emptyList(),
)

/** Un sitio de la fila del «Inicio Google». `icon` = data: URL del icono (null: se pinta la inicial). */
data class HomeShortcut(val id: String, val name: String, val icon: String?)

/**
 * Navegador in-app con el motor de forzaje inyectado (bundle `web/dist/omniweb.js`).
 * - `captureMode`: pantalla de preparación (hay puente nativo). Solo cuando `armed` es true el toque NO activa la web y el
 *   motor entrega el elemento tocado por `onCapture`; con `armed` false se navega y se busca con normalidad.
 * - si no, aplica `config` (modo efecto).
 * La tecla atrás navega hacia atrás y, si no hay historial, llama a `onExit` (null = no hace nada: en el efecto el
 * espectador no debe poder salir de la pantalla).
 * Se bloquean las URL que abrirían otra app (`isExternalScheme`).
 * Android: `WebView`. iOS: `WKWebView` (Fase 4).
 */
@Composable
expect fun ForcerWebView(
    url: String,
    config: ForceConfig?,
    captureMode: Boolean,
    armed: Boolean,
    onCapture: (Capture) -> Unit,
    onExit: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Cambiarlo fuerza recargar `url` aunque sea igual que la anterior (botón «Ir»). */
    navId: Int = 0,
    /** Pide la web de escritorio (user agent de PC). Se aplica al crear el navegador. */
    desktop: Boolean = false,
    /** Estado de armado del efecto (activación secreta): al cambiar, el motor se arma o se desarma (`OmniWeb.setArmed`). */
    effectArmed: Boolean = true,
    /** Gestos ocultos NATIVOS del efecto (iOS: esquinas dentro del navegador, porque Compose queda por debajo). */
    onHiddenTap: (() -> Unit)? = null,
    onHiddenLongPress: (() -> Unit)? = null,
    /** Un diálogo de Compose está encima: en iOS el navegador es una capa nativa y se oculta para no taparlo. */
    covered: Boolean = false,
    /** URL y título actuales de la página (para la barra de URL decorativa). */
    onPageInfo: ((url: String, title: String) -> Unit)? = null,
    /** Dirección del scroll: true = bajando. Solo si la config lleva `reportScroll`. */
    onScroll: ((down: Boolean) -> Unit)? = null,
    /** El motor ha forzado: `url` = ficha del objeto (vacía si no hay). */
    onForced: ((url: String) -> Unit)? = null,
    /** Capa del navegador: 0 = el principal, 1 = la ficha encima (en iOS son capas nativas distintas). */
    layer: Int = 0,
)

/** Descarga una imagen y la devuelve como data: URL (null si falla). Android: HttpURLConnection. */
expect suspend fun fetchImageDataUrl(url: String, referer: String): String?

/** Literal de cadena JS entre comillas dobles; escapa lo que rompería el script (comillas, saltos, U+2028/9, `<`). */
internal fun jsQuote(s: String): String {
    val sb = StringBuilder("\"")
    for (c in s) {
        when {
            c == '\\' -> sb.append("\\\\")
            c == '"' -> sb.append("\\\"")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c == '<' -> sb.append("\\u003c")
            c == '\u2028' -> sb.append("\\u2028")
            c == '\u2029' -> sb.append("\\u2029")
            c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> sb.append(c)
        }
    }
    return sb.append('"').toString()
}

/** Config → literal de objeto JS para `OmniWeb.configure(...)`. Común a Android e iOS. */
internal fun ForceConfig.toJs(): String =
    "{imageUrl:${jsQuote(imageUrl)},texts:[${texts.joinToString(",") { jsQuote(it) }}]," +
        (if (html != null) "html:${jsQuote(html)}," else "") +
        (if (reportScroll) "reportScroll:true," else "") +
        (if (detail != null) {
            "detail:{imageUrl:${jsQuote(detail.imageData)},texts:[${detail.texts.joinToString(",") { jsQuote(it) }}]" +
                (if (detail.html != null) ",html:${jsQuote(detail.html)}" else "") + "},"
        } else "") +
        (if (shortcuts.isNotEmpty()) {
            "shortcuts:[${shortcuts.joinToString(",") { "{id:${jsQuote(it.id)},name:${jsQuote(it.name)}" + (if (it.icon != null) ",icon:${jsQuote(it.icon)}" else "") + "}" }}],"
        } else "") +
        "session:$session,idleMs:$idleMs,mode:'tap',armed:$auto}"

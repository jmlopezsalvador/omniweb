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
    /** true = automático al parar el scroll; false = solo manual (gesto oculto). */
    val auto: Boolean = true,
)

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
    /** Cada incremento dispara el forzaje a mano (`OmniWeb.trigger()`): gesto oculto en el efecto. */
    triggerNonce: Int = 0,
)

/** Descarga una imagen y la devuelve como data: URL (null si falla). Android: HttpURLConnection. */
expect suspend fun fetchImageDataUrl(url: String, referer: String): String?

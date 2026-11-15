package com.omniweb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIView

/**
 * iOS: el `WKWebView` lo crea Swift (`OmniWebHost`) y vive en una capa nativa por encima de Compose. Esta función solo
 * reserva el hueco (un `Box`) y le dice a Swift dónde está. Antes se embebía con `UIKitView` y el scroll no llegaba al
 * WKWebView en ninguna web: con la capa nativa el toque no pasa por Compose.
 * - El motor JS se inyecta al empezar cada documento y `startScript` (configurar / armar captura) al terminar.
 * - No hay tecla atrás: gesto de borde del WKWebView; `onExit` no se usa.
 * - Las esquinas ocultas del efecto son vistas nativas (`onHiddenTap` / `onHiddenLongPress`).
 */
@Composable
actual fun ForcerWebView(
    url: String,
    config: ForceConfig?,
    captureMode: Boolean,
    armed: Boolean,
    onCapture: (Capture) -> Unit,
    onExit: (() -> Unit)?,
    modifier: Modifier,
    navId: Int,
    desktop: Boolean,
    effectArmed: Boolean,
    onHiddenTap: (() -> Unit)?,
    onHiddenLongPress: (() -> Unit)?,
    covered: Boolean,
    onPageInfo: ((url: String, title: String) -> Unit)?,
    onScroll: ((down: Boolean) -> Unit)?,
    onForced: ((url: String) -> Unit)?,
    layer: Int,
) {
    val host = iosWebHost
    if (host == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Falta el navegador de iOS (OmniWebHost)") }
        return
    }

    val latestCapture by rememberUpdatedState(onCapture)
    val latestArmed by rememberUpdatedState(armed)
    val latestTap by rememberUpdatedState(onHiddenTap)
    val latestLong by rememberUpdatedState(onHiddenLongPress)
    val latestInfo by rememberUpdatedState(onPageInfo)
    val latestScroll by rememberUpdatedState(onScroll)
    val latestForced by rememberUpdatedState(onForced)
    val latestExit by rememberUpdatedState(onExit)
    val density = LocalDensity.current.density
    var view by remember { mutableStateOf<UIView?>(null) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var requested by remember { mutableStateOf("$url#$navId") }

    fun startScript(): String = when {
        captureMode -> if (latestArmed) "OmniWeb.startCapture();" else ""
        config != null -> "OmniWeb.configure(${config.toJs()});"
        else -> ""
    }

    val callbacks = remember {
        object : WebViewCallbacks {
            override fun onCapture(imageUrl: String, imageData: String?, texts: List<String>, html: String?, detailUrl: String?, pageUrl: String, aspect: Double) {
                // Solo se atiende en la pantalla de preparación y con la captura armada
                if (!captureMode || !latestArmed) return
                latestCapture(Capture(imageUrl, imageData, texts, html, detailUrl, pageUrl, aspect.toFloat()))
            }

            override fun onPageInfo(url: String, title: String) {
                latestInfo?.invoke(url, title)
            }

            override fun onScroll(down: Boolean) {
                if (!captureMode) latestScroll?.invoke(down)
            }

            override fun onForced(url: String) {
                if (!captureMode) latestForced?.invoke(url)
            }

            override fun onBack() {
                latestExit?.invoke()
            }

            override fun onHiddenTap() {
                latestTap?.invoke()
            }

            override fun onHiddenLongPress() {
                latestLong?.invoke()
            }
        }
    }

    // El navegador nace y muere con esta pantalla
    DisposableEffect(Unit) {
        val v = host.createWebView(desktop, !captureMode && config != null && layer == 0, layer, callbacks)
        host.setStartScript(v, startScript())
        host.loadUrl(v, url)
        view = v
        onDispose {
            host.destroyWebView(v)
            view = null
        }
    }

    // Colocar la capa nativa sobre el hueco de Compose (px → puntos)
    LaunchedEffect(view, bounds, covered) {
        val v = view ?: return@LaunchedEffect
        val b = bounds ?: return@LaunchedEffect
        host.layoutWebView(
            v,
            (b.left / density).toDouble(),
            (b.top / density).toDouble(),
            (b.width / density).toDouble(),
            (b.height / density).toDouble(),
            covered,
        )
    }

    // «Ir» / reiniciar: nueva carga aunque la URL sea la misma
    LaunchedEffect(view, url, navId) {
        val v = view ?: return@LaunchedEffect
        val key = "$url#$navId"
        if (key != requested) {
            requested = key
            host.setStartScript(v, startScript())
            host.loadUrl(v, url)
        }
    }

    // Armar/desarmar la captura sin recargar; el script de arranque se actualiza para las próximas cargas
    LaunchedEffect(armed, view) {
        val v = view ?: return@LaunchedEffect
        if (captureMode) {
            host.setStartScript(v, startScript())
            host.evaluate(v, "window.OmniWeb&&OmniWeb." + (if (armed) "startCapture()" else "endCapture()"))
        }
    }

    // Activación secreta: armar / desarmar sin recargar; el script de arranque lleva el estado vigente para las próximas cargas
    LaunchedEffect(effectArmed, view) {
        val v = view ?: return@LaunchedEffect
        if (!captureMode) {
            host.setStartScript(v, startScript())
            host.evaluate(v, "window.OmniWeb&&OmniWeb.setArmed($effectArmed)")
        }
    }

    Box(modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
}

/** iOS: descarga con `URLSession` (en Swift). Las data: URL se devuelven tal cual. */
actual suspend fun fetchImageDataUrl(url: String, referer: String): String? {
    if (url.startsWith("data:")) return url
    val host = iosWebHost ?: return null
    return suspendCancellableCoroutine { cont ->
        host.fetchImage(
            url,
            referer,
            object : StringCallback {
                override fun invoke(value: String?) {
                    if (cont.isActive) cont.resume(value)
                }
            },
        )
    }
}

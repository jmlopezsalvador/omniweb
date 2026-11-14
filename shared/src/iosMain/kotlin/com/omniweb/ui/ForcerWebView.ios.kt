package com.omniweb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIView

/**
 * iOS: `WKWebView` creado por Swift (`OmniWebHost`) y embebido con `UIKitView`.
 * - El motor JS se inyecta al empezar cada documento y `startScript` (configurar / armar captura) al terminar.
 * - No hay tecla atrás: en iOS se usa el gesto de borde del propio WKWebView; `onExit` no se usa aquí.
 * - Compose queda POR DEBAJO del navegador: los gestos ocultos del efecto los dan las esquinas nativas de Swift
 *   (`onHiddenTap` / `onHiddenLongPress`).
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
    triggerNonce: Int,
    onHiddenTap: (() -> Unit)?,
    onHiddenLongPress: (() -> Unit)?,
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
    var view by remember { mutableStateOf<UIView?>(null) }
    var requested by remember { mutableStateOf("$url#$navId") }

    fun startScript(): String = when {
        captureMode -> if (latestArmed) "OmniWeb.startCapture();" else ""
        config != null -> "OmniWeb.configure(${config.toJs()});"
        else -> ""
    }

    val callbacks = remember {
        object : WebViewCallbacks {
            override fun onCapture(imageUrl: String, imageData: String?, texts: List<String>, html: String?, pageUrl: String, aspect: Double) {
                // Solo se atiende en la pantalla de preparación y con la captura armada
                if (!captureMode || !latestArmed) return
                latestCapture(Capture(imageUrl, imageData, texts, html, pageUrl, aspect.toFloat()))
            }

            override fun onHiddenTap() {
                latestTap?.invoke()
            }

            override fun onHiddenLongPress() {
                latestLong?.invoke()
            }
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

    // Disparo manual (volumen / esquina)
    LaunchedEffect(triggerNonce, view) {
        val v = view ?: return@LaunchedEffect
        if (triggerNonce > 0) host.evaluate(v, "window.OmniWeb&&OmniWeb.trigger()")
    }

    UIKitView(
        factory = {
            val v = host.createWebView(desktop, !captureMode && config != null, callbacks)
            host.setStartScript(v, startScript())
            host.loadUrl(v, url)
            view = v
            v
        },
        modifier = modifier,
        update = { v ->
            val key = "$url#$navId"
            if (key != requested) {
                requested = key
                host.setStartScript(v, startScript())
                host.loadUrl(v, url)
            }
        },
    )
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

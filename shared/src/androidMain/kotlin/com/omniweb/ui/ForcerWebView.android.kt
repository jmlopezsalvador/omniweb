package com.omniweb.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal fun parseCapture(json: String): Capture {
    val o = JSONObject(json)
    val texts = o.optJSONArray("texts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    return Capture(
        imageUrl = o.getString("imageUrl"),
        imageData = if (o.isNull("imageData")) null else o.getString("imageData"),
        texts = texts,
        html = if (o.isNull("html")) null else o.optString("html").takeIf { it.isNotEmpty() },
        pageUrl = o.optString("pageUrl"),
        aspect = o.optDouble("aspect", 1.0).toFloat(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
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
    covered: Boolean,
    onPageInfo: ((String, String) -> Unit)?,
    onScroll: ((Boolean) -> Unit)?,
) {
    val latestCapture by rememberUpdatedState(onCapture)
    val latestArmed by rememberUpdatedState(armed)
    val latestInfo by rememberUpdatedState(onPageInfo)
    val latestScroll by rememberUpdatedState(onScroll)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    // Armar/desarmar la captura sin recargar la página
    LaunchedEffect(armed, webView) {
        if (captureMode) {
            webView?.evaluateJavascript("window.OmniWeb&&OmniWeb." + (if (armed) "startCapture()" else "endCapture()"), null)
        }
    }

    // Disparo manual oculto (esquina / volumen): el motor ya sabe si hay un candidato y si ya forzó
    LaunchedEffect(triggerNonce, webView) {
        if (triggerNonce > 0) webView?.evaluateJavascript("window.OmniWeb&&OmniWeb.trigger()", null)
    }

    BackHandler {
        val wv = webView
        if (wv != null && canGoBack) wv.goBack() else onExit?.invoke()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // El bundle vive en assets (web/dist/omniweb.js, copiado por Gradle).
            val bundle = ctx.assets.open("omniweb.js").bufferedReader().use { it.readText() }
            WebView(ctx).apply {
                WebView.setWebContentsDebuggingEnabled(true) // chrome://inspect en depuración
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                if (desktop) {
                    settings.userAgentString = DESKTOP_UA
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                }
                // Puente del modo captura. Solo se atiende si estamos en captura (una página no puede forzar un guardado).
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onCapture(json: String) {
                        if (!captureMode || !latestArmed) return
                        val c = runCatching { parseCapture(json) }.getOrNull() ?: return
                        post { latestCapture(c) }
                    }
                    @JavascriptInterface
                    fun onScroll(dir: String) {
                        if (captureMode) return
                        post { latestScroll?.invoke(dir == "down") }
                    }
                }, "OmniBridge")
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String?) {
                        latestInfo?.invoke(view.url ?: "", title ?: "")
                    }
                }
                webViewClient = object : WebViewClient() {
                    // Esquemas que abrirían otra app (intent://, market://, youtube://…): se ignoran
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        isExternalScheme(request.url.toString())
                    // El motor (~20 KB, idempotente) entra al empezar la página; la configuración, que puede llevar
                    // MB de imagen, solo al terminar (antes se evaluaba dos veces por página).
                    override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                        if (captureMode || config != null) view.evaluateJavascript("if(!window.OmniWeb){$bundle}", null)
                    }
                    override fun onPageFinished(view: WebView, u: String?) {
                        val start = when {
                            captureMode -> if (latestArmed) "OmniWeb.startCapture();" else ""
                            config != null -> "OmniWeb.configure(${config.toJs()});"
                            else -> ""
                        }
                        if (start.isNotEmpty()) view.evaluateJavascript("if(!window.OmniWeb){$bundle}$start", null)
                    }
                    override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                        latestInfo?.invoke(u ?: view.url ?: "", view.title ?: "")
                    }
                }
                loadUrl(url)
                tag = "$url#$navId" // última navegación pedida desde la barra
                webView = this
            }
        },
        update = { wv ->
            if (wv.tag != "$url#$navId") {
                wv.tag = "$url#$navId"
                wv.loadUrl(url)
            }
        },
    )
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
private const val DOWNLOAD_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

actual suspend fun fetchImageDataUrl(url: String, referer: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        if (url.startsWith("data:")) return@runCatching url
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", DOWNLOAD_UA)
            if (referer.isNotEmpty()) c.setRequestProperty("Referer", referer)
            if (c.responseCode !in 200..299) return@runCatching null
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@runCatching null
            val mime = c.contentType?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

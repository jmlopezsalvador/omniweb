package com.omniweb.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/** Config → literal JS: `JSONObject.quote` escapa bien comillas, saltos de línea y U+2028. */
internal fun ForceConfig.toJs(): String =
    "{imageUrl:${JSONObject.quote(imageUrl)}," +
        "title:${if (title == null) "undefined" else JSONObject.quote(title)}," +
        "idleMs:$idleMs,mode:'${if (auto) "auto" else "manual"}'}"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun ForcerWebView(url: String, config: ForceConfig, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // El bundle vive en assets (web/dist/omniweb.js, copiado por Gradle).
            val bundle = ctx.assets.open("omniweb.js").bufferedReader().use { it.readText() }
            WebView(ctx).apply {
                WebView.setWebContentsDebuggingEnabled(true) // chrome://inspect en depuración
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    // Inyectar lo antes posible; se repite en onPageFinished por si la página tardó en montar el DOM.
                    override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) = inject(view)
                    override fun onPageFinished(view: WebView, u: String?) = inject(view)
                    private fun inject(view: WebView) =
                        view.evaluateJavascript("$bundle;OmniWeb.configure(${config.toJs()});", null)
                }
                loadUrl(url)
                tag = url // última URL pedida desde la barra
            }
        },
        update = { wv ->
            if (wv.tag != url) {
                wv.tag = url
                wv.loadUrl(url)
            }
        },
    )
}

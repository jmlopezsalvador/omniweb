package com.omniweb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** iOS: pendiente (Fase 4). Irá con `WKWebView` + `WKUserScript` + `WKScriptMessageHandler` y el mismo bundle JS. */
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
) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("Navegador iOS: Fase 4") }
}

/** iOS: pendiente (Fase 4), con NSURLSession. */
actual suspend fun fetchImageDataUrl(url: String, referer: String): String? = if (url.startsWith("data:")) url else null

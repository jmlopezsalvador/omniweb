package com.omniweb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** iOS: pendiente (Fase 4). Irá con `WKWebView` + `WKUserScript` + el mismo bundle JS. */
@Composable
actual fun ForcerWebView(url: String, config: ForceConfig, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("Navegador iOS: Fase 4") }
}

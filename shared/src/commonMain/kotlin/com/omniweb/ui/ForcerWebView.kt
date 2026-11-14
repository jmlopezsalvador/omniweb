package com.omniweb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Configuración que se entrega al motor JS (`OmniWeb.configure`). */
data class ForceConfig(
    val imageUrl: String,
    val title: String? = null,
    val idleMs: Int = 450,
    /** true = automático al parar el scroll; false = solo manual (gesto oculto). */
    val auto: Boolean = true,
)

/**
 * Navegador in-app con el motor de forzaje inyectado (bundle `web/dist/omniweb.js`).
 * Android: `WebView`. iOS: `WKWebView` (Fase 4).
 */
@Composable
expect fun ForcerWebView(url: String, config: ForceConfig, modifier: Modifier = Modifier)

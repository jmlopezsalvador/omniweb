package com.omniweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Imagen de prueba del spike (SVG en data URL; `#` va como %23). Se sustituirá por la imagen del mago. */
private const val SPIKE_IMAGE =
    "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='400' height='220'>" +
        "<rect width='400' height='220' fill='red'/>" +
        "<text x='200' y='125' font-size='40' text-anchor='middle' fill='white'>FORZADA</text></svg>"

/** Raíz Compose común. Fase 0: spike con la página local de scroll infinito. */
@Composable
fun OmniWebRoot(store: SettingsStore) {
    SystemBarsEffect()
    MaterialTheme {
        ForcerWebView(
            url = "file:///android_asset/test/page.html",
            config = ForceConfig(imageUrl = SPIKE_IMAGE, title = "Predicción"),
            modifier = Modifier.fillMaxSize().background(Color.Black),
        )
    }
}

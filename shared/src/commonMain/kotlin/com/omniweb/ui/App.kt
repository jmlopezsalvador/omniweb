package com.omniweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Imagen de prueba (SVG en data URL). Se sustituirá por la imagen elegida por el mago. */
private const val SPIKE_IMAGE =
    "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='400' height='400'>" +
        "<rect width='400' height='400' fill='red'/>" +
        "<text x='200' y='215' font-size='56' text-anchor='middle' fill='white'>FORZADA</text></svg>"

/** Añade https:// si falta y convierte texto suelto en búsqueda de Google Imágenes. */
internal fun normalizeUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
        t.contains('.') && !t.contains(' ') -> "https://$t"
        else -> "https://www.google.com/search?tbm=isch&q=${t.replace(' ', '+')}"
    }
}

/** Raíz Compose común. Fase 1: barra de URL mínima + navegador con el motor inyectado. */
@Composable
fun OmniWebRoot(store: SettingsStore) {
    SystemBarsEffect()
    var text by remember { mutableStateOf("https://www.google.com/search?tbm=isch&q=gatos") }
    var url by remember { mutableStateOf(text) }
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { url = normalizeUrl(text) }),
                )
                Button(onClick = { url = normalizeUrl(text) }, modifier = Modifier.padding(start = 4.dp)) { Text("Ir") }
            }
            ForcerWebView(
                url = url,
                config = ForceConfig(imageUrl = SPIKE_IMAGE, title = "Predicción"),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

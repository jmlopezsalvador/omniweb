package com.omniweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Home : Screen
    data class CaptureOf(val site: Site) : Screen
    data class EffectOf(val site: Site, val item: ForcedItem) : Screen
}

/** Raíz Compose común: inicio (sitios) → Preparar (captura) / Efecto. */
@Composable
fun OmniWebRoot(prefs: KeyValueStore) {
    SystemBarsEffect()
    val store = remember { ForceStore(prefs) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) } // fuerza releer el almacén tras guardar

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    store = store,
                    version = version,
                    message = message,
                    onPrepare = { message = null; screen = Screen.CaptureOf(it) },
                    onEffect = { site, item -> message = null; screen = Screen.EffectOf(site, item) },
                    onAdded = { version++ },
                )
                is Screen.CaptureOf -> CaptureScreen(
                    site = s.site,
                    store = store,
                    onDone = { saved ->
                        message = if (saved) "Forzaje guardado en ${s.site.name}." else null
                        version++
                        screen = Screen.Home
                    },
                )
                is Screen.EffectOf -> ForcerWebView(
                    url = s.item.pageUrl.ifBlank { s.site.startUrl },
                    config = ForceConfig(imageUrl = s.item.imageData, texts = s.item.texts),
                    captureMode = false,
                    armed = false,
                    onCapture = {},
                    onExit = { screen = Screen.Home },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    store: ForceStore,
    version: Int,
    message: String?,
    onPrepare: (Site) -> Unit,
    onEffect: (Site, ForcedItem) -> Unit,
    onAdded: () -> Unit,
) {
    var newUrl by remember { mutableStateOf("") }
    val sites = remember(version) { store.sites() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OmniWeb", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        if (message != null) Text(message, color = Color(0xFF00E676))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sites, key = { it.id }) { site ->
                val item = remember(version, site.id) { store.load(site.id) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(site.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (item == null) "Sin preparar" else "Preparado: «${item.texts.firstOrNull() ?: "imagen"}»",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onPrepare(site) }) { Text("Preparar") }
                            Button(onClick = { if (item != null) onEffect(site, item) }, enabled = item != null) { Text("Efecto") }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                label = { Text("Añadir web (URL)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )
            Button(
                onClick = {
                    if (newUrl.isNotBlank()) {
                        val url = normalizeUrl(newUrl)
                        store.addSite(url.removePrefix("https://").removePrefix("http://").substringBefore('/'), url)
                        newUrl = ""
                        onAdded()
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Añadir") }
        }
    }
}

@Composable
private fun CaptureScreen(site: Site, store: ForceStore, onDone: (saved: Boolean) -> Unit) {
    var text by remember { mutableStateOf(site.startUrl) }
    var url by remember { mutableStateOf(site.startUrl) }
    var navId by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Capture?>(null) }
    var saving by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { url = normalizeUrl(text); navId++ }),
            )
            Button(onClick = { url = normalizeUrl(text); navId++ }, modifier = Modifier.padding(start = 4.dp)) { Text("Ir") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                error ?: if (armed) "Toca el elemento que quieres forzar." else "Navega y busca; cuando lo veas, pulsa Capturar.",
                color = if (error == null) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (armed) {
                OutlinedButton(onClick = { armed = false; error = null }) { Text("Cancelar") }
            } else {
                Button(onClick = { armed = true; error = null }) { Text("Capturar elemento") }
            }
        }
        ForcerWebView(
            url = url,
            config = null,
            captureMode = true,
            armed = armed,
            onCapture = { pending = it; error = null },
            onExit = { onDone(false) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            navId = navId,
        )
    }

    val cap = pending
    if (cap != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) pending = null },
            title = { Text("¿Guardar este elemento?") },
            text = {
                Text(
                    (if (cap.texts.isEmpty()) "(sin textos)" else cap.texts.joinToString("\n")) +
                        "\n\nSe guardará la imagen en el móvil para usarla sin conexión.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val data = cap.imageData ?: fetchImageDataUrl(cap.imageUrl, cap.pageUrl)
                            saving = false
                            pending = null
                            if (data == null) {
                                error = "No se pudo obtener la imagen. Elige otro elemento."
                            } else {
                                store.save(site.id, ForcedItem(data, cap.texts, cap.pageUrl, cap.aspect))
                                onDone(true)
                            }
                        }
                    },
                ) { Text(if (saving) "Guardando…" else "Guardar") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { pending = null }) { Text("Elegir otro") } },
        )
    }
}

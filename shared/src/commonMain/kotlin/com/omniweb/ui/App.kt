package com.omniweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface Screen {
    data object Home : Screen
    data class CaptureOf(val site: Site) : Screen
    data class EffectOf(val site: Site, val item: ForcedItem) : Screen
}

/** Raíz Compose común: inicio (sitios) → Preparar (captura) / Efecto. */
@Composable
fun OmniWebRoot(prefs: KeyValueStore, blobs: BlobStore) {
    val store = remember { ForceStore(prefs, blobs) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) } // fuerza releer el almacén tras guardar

    // En el efecto: sin barras del sistema y pantalla encendida
    SystemBarsEffect(immersive = screen is Screen.EffectOf)

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black).then(if (screen is Screen.EffectOf) Modifier else Modifier.statusBarsPadding())) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    store = store,
                    version = version,
                    message = message,
                    onPrepare = { message = null; screen = Screen.CaptureOf(it) },
                    onEffect = { site, item -> message = null; screen = Screen.EffectOf(site, item) },
                    onChanged = { version++ },
                    onMessage = { message = it },
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
                is Screen.EffectOf -> EffectScreen(
                    site = s.site,
                    item = s.item,
                    settings = store.settings(s.site.id),
                    onExit = {
                        // Modo práctica: no dejar rastros del ensayo
                        if (store.practiceMode()) {
                            clearBrowsingData()
                            message = "Efecto cerrado. Rastros borrados."
                        } else {
                            message = null
                        }
                        screen = Screen.Home
                    },
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
    onChanged: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    var newUrl by remember { mutableStateOf("") }
    var settingsFor by remember { mutableStateOf<Site?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val sites = remember(version) { store.sites() }
    val practice = remember(version) { store.practiceMode() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("OmniWeb", color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }) { Text("Borrar rastros") }
        }
        if (message != null) Text(message, color = Color(0xFF00E676))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sites, key = { it.id }) { site ->
                val item = remember(version, site.id) { store.load(site.id) }
                val st = remember(version, site.id) { store.settings(site.id) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(site.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (item == null) "Sin preparar" else "Preparado: «${item.texts.firstOrNull() ?: "imagen"}»") +
                                " · " + (if (st.auto) "automático" else "manual") + (if (st.desktop) " · escritorio" else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { onPrepare(site) }) { Text("Preparar") }
                            Button(onClick = { if (item != null) onEffect(site, item) }, enabled = item != null) { Text("Efecto") }
                            TextButton(onClick = { settingsFor = site }) { Text("Ajustes") }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modo práctica", color = Color.White)
                Text("Al salir del efecto se borran historial, cookies y caché.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = practice, onCheckedChange = { store.setPracticeMode(it); onChanged() })
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
                        onChanged()
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Añadir") }
        }
    }

    settingsFor?.let { site ->
        SiteSettingsDialog(
            site = site,
            settings = store.settings(site.id),
            hasItem = store.load(site.id) != null,
            isCustom = store.isCustom(site.id),
            onChange = { store.saveSettings(site.id, it); onChanged() },
            onDeleteItem = { store.clear(site.id); onMessage("Forzaje de ${site.name} borrado."); onChanged(); settingsFor = null },
            onRemoveSite = { store.removeSite(site.id); onMessage("${site.name} quitado."); onChanged(); settingsFor = null },
            onClose = { settingsFor = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar rastros?") },
            text = { Text("Se borran cookies, historial y caché de todos los sitios. Se cerrarán tus sesiones iniciadas y los avisos de cookies volverán a salir.") },
            confirmButton = { TextButton(onClick = { clearBrowsingData(); confirmClear = false; onMessage("Rastros borrados.") }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SiteSettingsDialog(
    site: Site,
    settings: SiteSettings,
    hasItem: Boolean,
    isCustom: Boolean,
    onChange: (SiteSettings) -> Unit,
    onDeleteItem: () -> Unit,
    onRemoveSite: () -> Unit,
    onClose: () -> Unit,
) {
    // estado local para que los chips respondan al instante
    var s by remember { mutableStateOf(settings) }
    fun update(n: SiteSettings) { s = n; onChange(n) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ajustes de ${site.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Disparo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.auto, onClick = { update(s.copy(auto = true)) }, label = { Text("Automático") })
                    FilterChip(selected = !s.auto, onClick = { update(s.copy(auto = false)) }, label = { Text("Manual") })
                }
                Text(
                    if (s.auto) "Fuerza al parar el scroll. También vale el gesto oculto." else "Solo fuerza con el gesto oculto: toque en la esquina inferior derecha o botón de volumen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Parada del scroll", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IDLE_OPTIONS.forEach { ms ->
                        FilterChip(selected = s.idleMs == ms, onClick = { update(s.copy(idleMs = ms)) }, label = { Text("$ms ms") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Web de escritorio", modifier = Modifier.weight(1f))
                    Switch(checked = s.desktop, onCheckedChange = { update(s.copy(desktop = it)) })
                }
                Text("Si cambias esto, vuelve a preparar el forzaje con el mismo modo.", style = MaterialTheme.typography.bodySmall)
                if (hasItem) TextButton(onClick = onDeleteItem) { Text("Borrar forzaje guardado") }
                if (isCustom) TextButton(onClick = onRemoveSite) { Text("Quitar este sitio") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

@Composable
private fun CaptureScreen(site: Site, store: ForceStore, onDone: (saved: Boolean) -> Unit) {
    val siteSettings = remember { store.settings(site.id) }
    var text by remember { mutableStateOf(site.startUrl) }
    var url by remember { mutableStateOf(site.startUrl) }
    var navId by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Capture?>(null) }
    var saving by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var showBar by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        if (showBar) {
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
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                error ?: if (armed) "Toca el elemento que quieres forzar." else "Navega y busca; cuando lo veas, pulsa Capturar.",
                color = if (error == null) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { onDone(false) }) { Text("Inicio") }
            TextButton(onClick = { showBar = !showBar }) { Text(if (showBar) "Ocultar barra" else "Mostrar barra") }
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
            desktop = siteSettings.desktop,
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
                                store.save(site.id, ForcedItem(data, cap.texts, cap.html, cap.pageUrl, cap.aspect))
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

/**
 * Pantalla del efecto: solo la web a pantalla completa. Gestos ocultos (invisibles para el espectador):
 * - toque en la esquina INFERIOR DERECHA o botón de volumen: fuerza ahora (disparo manual);
 * - pulsación larga en la esquina SUPERIOR IZQUIERDA: menú oculto (reiniciar, web real, salir).
 * «Atrás» solo navega por la web; no sale del efecto.
 */
@Composable
private fun EffectScreen(site: Site, item: ForcedItem, settings: SiteSettings, onExit: () -> Unit) {
    var navId by remember { mutableStateOf(0) }
    var trigger by remember { mutableStateOf(0) }
    var realWeb by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    VolumeKeyEffect(enabled = !realWeb && !menu) { trigger++ }

    Box(Modifier.fillMaxSize()) {
        ForcerWebView(
            url = item.pageUrl.ifBlank { site.startUrl },
            config = if (realWeb) null else ForceConfig(
                imageUrl = item.imageData,
                texts = item.texts,
                html = item.html,
                idleMs = settings.idleMs,
                auto = settings.auto,
            ),
            captureMode = false,
            armed = false,
            onCapture = {},
            onExit = null,
            modifier = Modifier.fillMaxSize(),
            navId = navId,
            desktop = settings.desktop,
            triggerNonce = trigger,
            onHiddenTap = { trigger++ },
            onHiddenLongPress = { menu = true },
        )
        // Esquina inferior derecha: disparo manual
        HiddenCorner(Modifier.align(Alignment.BottomEnd), onTap = { trigger++ })
        // Esquina superior izquierda: menú oculto
        HiddenCorner(Modifier.align(Alignment.TopStart), onLongPress = { menu = true })
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Opciones ocultas") },
            text = {
                Column {
                    TextButton(onClick = { realWeb = false; navId++; menu = false }) { Text("Reiniciar el efecto (recarga y vuelve a armar)") }
                    TextButton(onClick = { realWeb = !realWeb; navId++; menu = false }) {
                        Text(if (realWeb) "Reanudar el forzaje" else "Mostrar la web real (sin forzar)")
                    }
                    TextButton(onClick = { menu = false; onExit() }) { Text("Salir") }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Cerrar") } },
        )
    }
}

/**
 * Esquina invisible que se TRAGA el contacto completo (si no, el toque llegaría también a la web de debajo).
 * `onTap`: toque corto. `onLongPress`: pulsación larga.
 */
@Composable
private fun HiddenCorner(modifier: Modifier, onTap: (() -> Unit)? = null, onLongPress: (() -> Unit)? = null) {
    Box(
        modifier.size(72.dp).pointerInput(onTap, onLongPress) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                if (up != null) {
                    up.consume()
                    onTap?.invoke()
                } else {
                    onLongPress?.invoke()
                    waitForUpOrCancellation()?.consume()
                }
            }
        },
    )
}

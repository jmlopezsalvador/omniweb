package com.omniweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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

    OmniTheme {
        // Surface (no Box+background): fija también el color del TEXTO; si no, el texto por defecto sale negro sobre oscuro
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize().then(if (screen is Screen.EffectOf) Modifier else Modifier.statusBarsPadding()),
        ) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    store = store,
                    version = version,
                    message = message,
                    onPrepare = { message = null; screen = Screen.CaptureOf(it) },
                    onEffect = { site ->
                        val item = store.load(site.id)
                        if (item == null) {
                            store.clear(site.id)
                            message = "El forzaje de ${site.name} no se pudo leer. Vuelve a prepararlo."
                            version++
                        } else {
                            message = null
                            screen = Screen.EffectOf(site, item)
                        }
                    },
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
                    haptics = store.haptics(),
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

// ───────────────────────────── Inicio ─────────────────────────────

private val AvatarColors = listOf(0xFF6C5CE7, 0xFF00A383, 0xFFE17055, 0xFF0984E3, 0xFFD6336C, 0xFFB7791F)

@Composable
private fun HomeScreen(
    store: ForceStore,
    version: Int,
    message: String?,
    onPrepare: (Site) -> Unit,
    onEffect: (Site) -> Unit,
    onChanged: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    var newUrl by remember { mutableStateOf("") }
    var settingsFor by remember { mutableStateOf<Site?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val sites = remember(version) { store.sites() }
    val practice = remember(version) { store.practiceMode() }
    val haptics = remember(version) { store.haptics() }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OmniWeb", style = MaterialTheme.typography.headlineLarge)
                Text("Prepara un elemento y actúa", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { confirmClear = true }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Borrar rastros") }
        }
        Spacer(Modifier.height(16.dp))
        if (message != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(message, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sites, key = { it.id }) { site ->
                val item = remember(version, site.id) { store.summary(site.id) }
                val st = remember(version, site.id) { store.settings(site.id) }
                SiteCard(site, item, st, onPrepare = { onPrepare(site) }, onEffect = { onEffect(site) }, onSettings = { settingsFor = site })
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo práctica", style = MaterialTheme.typography.titleMedium)
                    Text("Al salir del efecto se borran historial, cookies y caché.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = practice, onCheckedChange = { store.setPracticeMode(it); onChanged() })
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vibración al armar", style = MaterialTheme.typography.titleMedium)
                    Text("El móvil vibra al armar (un pulso) o desarmar (dos) el efecto con el gesto secreto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = haptics, onCheckedChange = { store.setHaptics(it); onChanged() })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                placeholder = { Text("Añadir web (ej. wikipedia.org)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )
            Button(
                onClick = {
                    if (newUrl.isNotBlank()) {
                        val url = normalizeUrl(newUrl)
                        store.addSite(siteNameFor(url), url)
                        newUrl = ""
                        onChanged()
                    }
                },
                modifier = Modifier.padding(start = 8.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Añadir") }
        }
    }

    settingsFor?.let { site ->
        SiteSettingsDialog(
            site = site,
            settings = store.settings(site.id),
            hasItem = store.summary(site.id) != null,
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
private fun SiteCard(
    site: Site,
    item: ForcedSummary?,
    settings: SiteSettings,
    onPrepare: () -> Unit,
    onEffect: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color(AvatarColors[abs(site.id.hashCode()) % AvatarColors.size])),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(site.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (item == null) "Sin preparar" else "Preparado · «${item.title}»",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onSettings) { MoreIcon(MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (settings.auto) "Armado" else "Arma con gesto")
                Pill(if (settings.desktop) "Escritorio" else "Móvil")
                if (!settings.chrome) Pill("Sin marco")
                Pill("${settings.idleMs} ms")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPrepare, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) { Text("Preparar") }
                Button(onClick = onEffect, enabled = item != null, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) { Text("Efecto") }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Tres puntos verticales dibujados (sin depender de iconos de Material). */
@Composable
private fun MoreIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val r = 2.2.dp.toPx()
        for (i in 0..2) drawCircle(color, r, Offset(size.width / 2, size.height * (0.2f + 0.3f * i)))
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
                Text("Armado", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.auto, onClick = { update(s.copy(auto = true)) }, label = { Text("Desde el inicio") })
                    FilterChip(selected = !s.auto, onClick = { update(s.copy(auto = false)) }, label = { Text("Con gesto secreto") })
                }
                Text(
                    if (s.auto) "El efecto arranca armado: cuando se desliza, se para y se pulsa un elemento de la lista, se fuerza."
                    else "El efecto arranca como una web normal (para hacer una demostración). Al armarlo con el gesto secreto (botón de volumen o toque abajo a la derecha) el móvil vibra y la siguiente pulsación que no sea scroll fuerza.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Parada del scroll", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IDLE_OPTIONS.forEach { ms ->
                        FilterChip(selected = s.idleMs == ms, onClick = { update(s.copy(idleMs = ms)) }, label = { Text("$ms ms") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Web de escritorio", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Pide a la web su versión de ordenador (más ancha, letra más pequeña). Prepara y actúa con el mismo modo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.desktop, onCheckedChange = { update(s.copy(desktop = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Marco de navegador", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "En el efecto añade la barra de URL (y la barra inferior en iPhone) para que parezca un navegador. Se puede cambiar en directo desde el menú oculto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.chrome, onCheckedChange = { update(s.copy(chrome = it)) })
                }
                if (hasItem) TextButton(onClick = onDeleteItem) { Text("Borrar forzaje guardado") }
                if (isCustom) TextButton(onClick = onRemoveSite) { Text("Quitar este sitio") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

// ───────────────────────────── Preparar ─────────────────────────────

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
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showBar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { url = normalizeUrl(text); navId++ }),
                        )
                        Button(
                            onClick = { url = normalizeUrl(text); navId++ },
                            modifier = Modifier.padding(start = 8.dp).height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Ir") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDone(false) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("‹ Inicio") }
                    TextButton(onClick = { showBar = !showBar }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (showBar) "Ocultar barra" else "Mostrar barra")
                    }
                    Box(Modifier.weight(1f))
                    if (armed) {
                        OutlinedButton(onClick = { armed = false; error = null }, shape = RoundedCornerShape(12.dp)) { Text("Cancelar") }
                    } else {
                        Button(onClick = { armed = true; error = null }, shape = RoundedCornerShape(12.dp)) { Text("Capturar elemento") }
                    }
                }
                Text(
                    error ?: if (armed) "Toca el elemento que quieres forzar." else "Navega y busca; cuando lo veas, pulsa Capturar.",
                    color = if (error == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
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
            covered = pending != null,
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
                        "\n\nSe guarda el elemento (imagen y aspecto de su tarjeta), no la búsqueda. La imagen queda en el móvil.",
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

// ───────────────────────────── Efecto ─────────────────────────────

/**
 * Pantalla del efecto: la web **abierta en el inicio del sitio** (sin la búsqueda con la que se preparó) dentro de un marco que
 * parece un navegador: barra de estado visible, barra de URL arriba y, en iPhone, barra inferior que se esconde al bajar.
 * El marco es decorativo y se puede apagar. El mago o el espectador busca lo que quiera.
 * Gestos ocultos:
 * - pulsación larga en la **barra de URL** (o, sin marco, en la zona superior CENTRAL): menú oculto;
 * - toque en la esquina INFERIOR DERECHA o botón de volumen: ARMA/DESARMA el truco (vibra). Armado, la siguiente pulsación que no
 *   sea scroll sobre un elemento de la lista (con la lista en reposo) lo fuerza;
 * «Atrás» solo navega por la web; no sale del efecto.
 */
@Composable
private fun EffectScreen(site: Site, item: ForcedItem, settings: SiteSettings, haptics: Boolean, onExit: () -> Unit) {
    var navId by remember { mutableStateOf(0) }
    // Armado: arranca según el ajuste y la activación secreta lo conmuta (con vibración de confirmación)
    var armedNow by remember { mutableStateOf(settings.auto) }
    val activate: () -> Unit = {
        armedNow = !armedNow
        if (haptics) vibrateFeedback(armedNow)
    }
    var realWeb by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(settings.chrome) }
    var pageUrl by remember { mutableStateOf(site.startUrl) }
    var bottomVisible by remember { mutableStateOf(true) }
    val ios = isIosPlatform()

    VolumeKeyEffect(enabled = !realWeb && !menu) { activate() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (chrome) {
            BrowserTopBar(ios = ios, url = pageUrl, onMenu = { menu = true })
        } else {
            Box(Modifier.fillMaxWidth().statusBarsPadding()) // respeta la barra de estado aunque no haya marco
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ForcerWebView(
                url = site.startUrl,
                config = if (realWeb) null else ForceConfig(
                    imageUrl = item.imageData,
                    texts = item.texts,
                    html = item.html,
                    idleMs = settings.idleMs,
                    auto = armedNow,
                    reportScroll = chrome && ios,
                ),
                captureMode = false,
                armed = false,
                onCapture = {},
                onExit = null,
                modifier = Modifier.fillMaxSize(),
                navId = navId,
                desktop = settings.desktop,
                effectArmed = armedNow,
                onHiddenTap = { activate() },
                onHiddenLongPress = { menu = true },
                covered = menu,
                onPageInfo = { url, _ -> if (url.isNotBlank()) pageUrl = url },
                onScroll = { down -> bottomVisible = !down },
            )
            // Zonas ocultas en Android (en iOS son vistas nativas dentro de la capa del navegador)
            HiddenCorner(Modifier.align(Alignment.BottomEnd), onTap = { activate() })
            if (!chrome) HiddenCorner(Modifier.align(Alignment.TopCenter), onLongPress = { menu = true }, width = 180.dp, height = 64.dp)
        }
        if (chrome && ios) BrowserBottomBar(visible = bottomVisible) else Box(Modifier.fillMaxWidth().navigationBarsPadding())
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Opciones ocultas") },
            text = {
                Column {
                    TextButton(onClick = { realWeb = false; armedNow = settings.auto; navId++; menu = false }) { Text("Reiniciar el efecto (recarga y vuelve al estado inicial)") }
                    TextButton(onClick = { realWeb = !realWeb; navId++; menu = false }) {
                        Text(if (realWeb) "Reanudar el forzaje" else "Mostrar la web real (sin forzar)")
                    }
                    TextButton(onClick = { chrome = !chrome; menu = false }) {
                        Text(if (chrome) "Ocultar el marco de navegador" else "Mostrar el marco de navegador")
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
private fun HiddenCorner(
    modifier: Modifier,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    width: Dp = 72.dp,
    height: Dp = 72.dp,
) {
    Box(
        modifier.size(width, height).pointerInput(onTap, onLongPress) {
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

package com.omniweb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
                    blobs = blobs,
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

@Composable
internal fun Pill(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun SiteSettingsDialog(
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
    var pageUrl by remember { mutableStateOf(site.startUrl) }
    // Ficha fijada a mano («abre el objeto como lo haría el espectador y pulsa Fijar ficha»): pisa a la detectada
    var fixedDetail by remember { mutableStateOf<String?>(null) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error ?: when {
                            armed -> "Toca el elemento que quieres forzar."
                            fixedDetail != null -> "Ficha fijada: ${shortUrl(fixedDetail!!)}"
                            else -> "Navega y busca; cuando lo veas, pulsa Capturar."
                        },
                        color = if (error == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    if (!armed) {
                        TextButton(
                            onClick = { fixedDetail = if (fixedDetail == null) pageUrl else null },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text(if (fixedDetail == null) "Fijar ficha" else "Quitar ficha") }
                    }
                }
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
            onPageInfo = { u, _ -> if (u.isNotBlank()) pageUrl = u },
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
                        "\n\nFicha: " + ((fixedDetail ?: cap.detailUrl)?.let { shortUrl(it) } ?: "ninguna (se sustituirá sin abrir nada)") +
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
                                store.save(site.id, ForcedItem(data, cap.texts, cap.html, fixedDetail ?: cap.detailUrl, cap.pageUrl, cap.aspect))
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
    // Ficha del objeto: se abre al forzar, en una segunda capa por encima de la lista
    var detailOpen by remember { mutableStateOf(false) }
    var detailUrl by remember { mutableStateOf("") }
    val ios = isIosPlatform()

    VolumeKeyEffect(enabled = !realWeb && !menu && !detailOpen) { activate() }

    Box(Modifier.fillMaxSize()) {
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
                    detailUrl = item.detailUrl,
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
                onForced = { url ->
                    if (url.isNotBlank()) {
                        detailUrl = url
                        detailOpen = true
                    }
                },
            )
            // Zonas ocultas en Android (en iOS son vistas nativas dentro de la capa del navegador)
            HiddenCorner(Modifier.align(Alignment.BottomEnd), onTap = { activate() })
            if (!chrome) HiddenCorner(Modifier.align(Alignment.TopCenter), onLongPress = { menu = true }, width = 180.dp, height = 64.dp)
        }
        if (chrome && ios) BrowserBottomBar(visible = bottomVisible) else Box(Modifier.fillMaxWidth().navigationBarsPadding())
    }
    AnimatedVisibility(visible = detailOpen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
        DetailLayer(url = detailUrl, ios = ios, chrome = chrome, desktop = settings.desktop, onClose = { detailOpen = false })
    }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Opciones ocultas") },
            text = {
                Column {
                    TextButton(onClick = { realWeb = false; armedNow = settings.auto; detailOpen = false; navId++; menu = false }) { Text("Reiniciar el efecto (recarga y vuelve al estado inicial)") }
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

/**
 * Ficha del objeto: la página real del elemento forzado, en un navegador por ENCIMA de la lista (que queda intacta detrás, con el
 * elemento ya sustituido). Se cierra con «atrás» (botón del marco, tecla atrás de Android o arrastre desde el borde en iOS).
 */
@Composable
private fun DetailLayer(url: String, ios: Boolean, chrome: Boolean, desktop: Boolean, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(url) }
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, // no deja pasar toques a la lista
    ) {
        if (chrome) {
            BrowserTopBar(ios = ios, url = shown, onMenu = {}, onBack = onClose)
        } else {
            Box(Modifier.fillMaxWidth().statusBarsPadding())
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ForcerWebView(
                url = url,
                config = null,
                captureMode = false,
                armed = false,
                onCapture = {},
                onExit = onClose,
                modifier = Modifier.fillMaxSize(),
                desktop = desktop,
                onPageInfo = { u, _ -> if (u.isNotBlank()) shown = u },
                layer = 1,
            )
        }
        if (chrome && ios) BrowserBottomBar(visible = true, onBack = onClose) else Box(Modifier.fillMaxWidth().navigationBarsPadding())
    }
}

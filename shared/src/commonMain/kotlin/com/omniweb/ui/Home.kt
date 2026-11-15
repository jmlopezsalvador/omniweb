package com.omniweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


internal enum class HomeTab { Favorites, Sites, Settings }

private val AvatarColors = listOf(0xFF6C5CE7, 0xFF00A383, 0xFFE17055, 0xFF0984E3, 0xFFD6336C, 0xFFB7791F)

// Aspecto de «Favoritos» de un navegador móvil: claro, lo ve el espectador (no usa el tema oscuro de la app).
private val FavBg = Color(0xFFF2F2F7)
private val FavText = Color(0xFF1C1C1E)
private val FavSub = Color(0xFF8E8E93)
private val FavBar = Color(0xFFF9F9F9)
private val FavBlue = Color(0xFF007AFF)

/**
 * Inicio de la app. Barra inferior con tres pestañas:
 * - Favoritos: la ve el espectador. Solo iconos de los sitios; tocar uno lanza el efecto (si está preparado).
 * - Sitios: la zona del mago. Lista con logo, estado y botones Preparar / Efecto / ajustes.
 * - Ajustes: opciones generales.
 */
@Composable
internal fun HomeScreen(
    store: ForceStore,
    blobs: BlobStore,
    version: Int,
    message: String?,
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    onPrepare: (Site) -> Unit,
    onEffect: (Site) -> Unit,
    onChanged: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(if (tab == HomeTab.Favorites) FavBg else MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                HomeTab.Favorites -> FavoritesTab(store, blobs, version, onEffect)
                HomeTab.Sites -> SitesTab(store, blobs, version, message, onPrepare, onEffect, onChanged, onMessage)
                HomeTab.Settings -> SettingsTab(store, version, onChanged, onMessage)
            }
        }
        BottomTabs(tab, onTab)
    }
}

// ───────────────────────── Favoritos (lo ve el espectador) ─────────────────────────

@Composable
private fun FavoritesTab(store: ForceStore, blobs: BlobStore, version: Int, onEffect: (Site) -> Unit) {
    val style = remember(version) { store.homeStyle() }
    if (style == HOME_GOOGLE) {
        GoogleHome(store, blobs, version, onEffect)
        return
    }
    val sites = remember(version) { store.sites() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Favoritos",
            color = FavText,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 20.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sites, key = { it.id }) { site ->
                // Sin marcas de «preparado»: el espectador no debe ver nada del efecto. Si no hay forzaje, no hace nada.
                FavoriteTile(site, blobs) { if (store.summary(site.id) != null) onEffect(site) }
            }
        }
    }
}

/**
 * «Inicio Google»: la web real de Google (con lo que Google muestre en ese momento) y, bajo el buscador, la fila de sitios de la
 * app (la inserta el motor). Tocar un sitio lanza su efecto si está preparado.
 */
@Composable
private fun GoogleHome(store: ForceStore, blobs: BlobStore, version: Int, onEffect: (Site) -> Unit) {
    val sites = remember(version) { store.sites() }
    var icons by remember(sites) { mutableStateOf<Map<String, String?>?>(null) }
    LaunchedEffect(sites) {
        // Iconos listos antes de abrir la web (el motor recibe la fila al terminar la carga); sin red no se espera más de unos segundos
        withTimeoutOrNull(3500) { coroutineScope { sites.map { async { faviconData(it, blobs) } }.awaitAll() } }
        icons = sites.associate { it.id to blobs.read("fav.${it.id}")?.takeIf { d -> d != "none" } }
    }
    val ready = icons
    if (ready == null) {
        Box(Modifier.fillMaxSize().background(FavBg))
        return
    }
    val config = remember(ready, sites) {
        ForceConfig(imageUrl = "", auto = false, shortcuts = sites.map { HomeShortcut(it.id, it.name, ready[it.id]) })
    }
    ForcerWebView(
        url = GOOGLE_HOME_URL,
        config = config,
        captureMode = false,
        armed = false,
        onCapture = {},
        onExit = null,
        modifier = Modifier.fillMaxSize(),
        effectArmed = false,
        onForced = { msg ->
            // El motor avisa con «omni:launch:<id>»; sin forzaje preparado no se hace nada (el espectador no ve nada raro)
            if (msg.startsWith(LAUNCH_PREFIX)) {
                val id = msg.removePrefix(LAUNCH_PREFIX)
                sites.firstOrNull { it.id == id }?.let { if (store.summary(it.id) != null) onEffect(it) }
            }
        },
    )
}

private const val GOOGLE_HOME_URL = "https://www.google.com/?hl=es"
private const val LAUNCH_PREFIX = "omni:launch:"

@Composable
private fun FavoriteTile(site: Site, blobs: BlobStore, onClick: () -> Unit) {
    val icon = rememberFavicon(site, blobs)
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(15.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = site.name, modifier = Modifier.size(38.dp), contentScale = ContentScale.Fit)
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(AvatarColors[abs(site.id.hashCode()) % AvatarColors.size])),
                    contentAlignment = Alignment.Center,
                ) { Text(site.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(site.name, color = FavText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ───────────────────────── Sitios (zona del mago) ─────────────────────────

@Composable
private fun SitesTab(
    store: ForceStore,
    blobs: BlobStore,
    version: Int,
    message: String?,
    onPrepare: (Site) -> Unit,
    onEffect: (Site) -> Unit,
    onChanged: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    val sites = remember(version) { store.sites() }
    var settingsFor by remember { mutableStateOf<Site?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sitios", style = MaterialTheme.typography.headlineLarge)
                    Text("Prepara aquí el efecto de cada web", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { adding = true }, shape = RoundedCornerShape(14.dp)) { Text("Añadir web") }
            }
        }
        if (message != null) {
            item(key = "message") {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(message, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
        }
        items(sites, key = { it.id }) { site ->
            val summary = remember(version, site.id) { store.summary(site.id) }
            val st = remember(version, site.id) { store.settings(site.id) }
            SiteCard(site, blobs, summary, st, onPrepare = { onPrepare(site) }, onEffect = { onEffect(site) }, onSettings = { settingsFor = site })
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

    if (adding) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Añadir web") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("ej. wikipedia.org") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isNotBlank()) {
                        val full = normalizeUrl(url)
                        store.addSite(siteNameFor(full), full)
                        adding = false
                        onChanged()
                    }
                }) { Text("Añadir") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SiteCard(
    site: Site,
    blobs: BlobStore,
    summary: ForcedSummary?,
    st: SiteSettings,
    onPrepare: () -> Unit,
    onEffect: () -> Unit,
    onSettings: () -> Unit,
) {
    val icon = rememberFavicon(site, blobs)
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = site.name, modifier = Modifier.size(30.dp), contentScale = ContentScale.Fit)
                    } else {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(AvatarColors[abs(site.id.hashCode()) % AvatarColors.size])),
                            contentAlignment = Alignment.Center,
                        ) { Text(site.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium) }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (summary == null) "Sin preparar" else "Preparado · «${summary.title}»",
                        color = if (summary == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                    val dot = MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.size(width = 4.dp, height = 20.dp)) {
                        val r = 2.dp.toPx()
                        for (k in 0 until 3) drawCircle(dot, r, Offset(size.width / 2, r + k * (size.height - 2 * r) / 2))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (st.auto) "Armado" else "Arma con gesto")
                Pill(if (st.desktop) "Escritorio" else "Móvil")
                Pill("${st.idleMs} ms")
                if (summary?.hasDetail == true) Pill("Con ficha")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPrepare, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) { Text("Preparar") }
                Button(onClick = onEffect, enabled = summary != null, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)) { Text("Efecto") }
            }
        }
    }
}

// ───────────────────────── Ajustes ─────────────────────────

@Composable
private fun SettingsTab(store: ForceStore, version: Int, onChanged: () -> Unit, onMessage: (String?) -> Unit) {
    val practice = remember(version) { store.practiceMode() }
    val haptics = remember(version) { store.haptics() }
    val homeStyle = remember(version) { store.homeStyle() }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineLarge)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aspecto de Favoritos", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lo que verá el espectador al abrir la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StyleOption("Cuadrícula de favoritos", "Iconos de los sitios sobre fondo claro.", homeStyle == HOME_GRID) { store.setHomeStyle(HOME_GRID); onChanged() }
                StyleOption("Inicio Google", "La web real de Google con tus sitios bajo el buscador.", homeStyle == HOME_GOOGLE) { store.setHomeStyle(HOME_GOOGLE); onChanged() }
            }
        }
        SettingRow("Modo práctica", "Al salir del efecto se borran historial, cookies y caché.", practice) { store.setPracticeMode(it); onChanged() }
        SettingRow("Vibración al armar", "El móvil vibra al armar (un pulso) o desarmar (dos) el efecto con el gesto secreto.", haptics) {
            store.setHaptics(it); onChanged()
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rastros", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Borra cookies, historial y caché de todos los sitios. Se cerrarán las sesiones iniciadas y los avisos de cookies volverán a salir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { confirmClear = true }, shape = RoundedCornerShape(14.dp)) { Text("Borrar rastros ahora") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cómo se usa", style = MaterialTheme.typography.titleMedium)
                Text(
                    """1. Sitios → Preparar: busca en el sitio y toca el elemento de la lista. Después entra en su ficha (como lo haría el espectador) y captura su imagen principal.
2. Favoritos (lo que ve el espectador): al tocar un icono se lanza el efecto del sitio (si está preparado). Se abre el inicio del sitio. Con el truco armado, quien desliza, para y pulsa un elemento de la lista lo ve sustituido; la web abre su ficha con lo que capturaste.
3. Gestos ocultos: pulsación larga en la barra de URL (menú) y botón de volumen o esquina inferior derecha (armar).""",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar rastros?") },
            text = { Text("Se borran cookies, historial y caché de todos los sitios.") },
            confirmButton = { TextButton(onClick = { clearBrowsingData(); confirmClear = false; onMessage("Rastros borrados.") }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StyleOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.background).padding(2.dp).clip(RoundedCornerShape(12.dp))
            .background(border).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

// ───────────────────────── Barra inferior ─────────────────────────

@Composable
private fun BottomTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val light = selected == HomeTab.Favorites // en Favoritos la barra también es clara (la ve el espectador)
    val active = if (light) FavBlue else MaterialTheme.colorScheme.primary
    val idle = if (light) FavSub else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = if (light) FavBar else MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.navigationBarsPadding().height(62.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TabItem("Favoritos", if (selected == HomeTab.Favorites) active else idle, { onSelect(HomeTab.Favorites) }) { c -> StarIcon(c) }
            TabItem("Sitios", if (selected == HomeTab.Sites) active else idle, { onSelect(HomeTab.Sites) }) { c -> ListIcon(c) }
            TabItem("Ajustes", if (selected == HomeTab.Settings) active else idle, { onSelect(HomeTab.Settings) }) { c -> GearIcon(c) }
        }
    }
}

@Composable
private fun TabItem(label: String, color: Color, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ListIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = 2.2.dp.toPx()
        for (k in 0 until 3) {
            val y = size.height * (0.22f + 0.28f * k)
            drawCircle(color, w * 0.8f, Offset(size.width * 0.1f, y))
            drawLine(color, Offset(size.width * 0.3f, y), Offset(size.width * 0.96f, y), w, StrokeCap.Round)
        }
    }
}

@Composable
private fun StarIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2 + 1.dp.toPx()
        val outer = size.width / 2
        val inner = outer * 0.42f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

@Composable
private fun GearIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.width * 0.28f
        val w = 2.2.dp.toPx()
        drawCircle(color, r, c, style = Stroke(w))
        for (i in 0 until 8) {
            val a = i * PI / 4
            val from = Offset(c.x + (r * 1.15f * cos(a)).toFloat(), c.y + (r * 1.15f * sin(a)).toFloat())
            val to = Offset(c.x + (size.width * 0.46f * cos(a)).toFloat(), c.y + (size.width * 0.46f * sin(a)).toFloat())
            drawLine(color, from, to, w * 1.4f, StrokeCap.Round)
        }
    }
}

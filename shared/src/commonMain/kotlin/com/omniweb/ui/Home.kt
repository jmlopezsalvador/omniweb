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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class HomeTab { Favorites, Settings }

private val AvatarColors = listOf(0xFF6C5CE7, 0xFF00A383, 0xFFE17055, 0xFF0984E3, 0xFFD6336C, 0xFFB7791F)

/** Inicio de la app: como la página de inicio de un navegador. Barra inferior con Favoritos (iconos de los sitios) y Ajustes. */
@Composable
internal fun HomeScreen(
    store: ForceStore,
    blobs: BlobStore,
    version: Int,
    message: String?,
    onPrepare: (Site) -> Unit,
    onEffect: (Site) -> Unit,
    onChanged: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    var tab by remember { mutableStateOf(HomeTab.Favorites) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                HomeTab.Favorites -> FavoritesTab(store, blobs, version, message, onPrepare, onEffect, onChanged, onMessage)
                HomeTab.Settings -> SettingsTab(store, version, onChanged, onMessage)
            }
        }
        BottomTabs(tab) { tab = it }
    }
}

// ───────────────────────── Favoritos ─────────────────────────

@Composable
private fun FavoritesTab(
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
    var sheetFor by remember { mutableStateOf<Site?>(null) }
    var settingsFor by remember { mutableStateOf<Site?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text("OmniWeb", style = MaterialTheme.typography.headlineLarge)
        Text("Favoritos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (message != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(message, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sites, key = { it.id }) { site ->
                val summary = remember(version, site.id) { store.summary(site.id) }
                SiteTile(site, blobs, prepared = summary != null) { sheetFor = site }
            }
            item(key = "add") { AddTile { adding = true } }
        }
    }

    sheetFor?.let { site ->
        val summary = store.summary(site.id)
        val st = store.settings(site.id)
        AlertDialog(
            onDismissRequest = { sheetFor = null },
            title = { Text(site.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (summary == null) "Sin preparar" else "Preparado · «${summary.title}»",
                        color = if (summary == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(if (st.auto) "Armado" else "Arma con gesto")
                        Pill(if (st.desktop) "Escritorio" else "Móvil")
                        if (summary?.hasDetail == true) Pill("Con ficha")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { sheetFor = null; onPrepare(site) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Preparar") }
                        Button(
                            onClick = { sheetFor = null; onEffect(site) },
                            enabled = summary != null,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Efecto") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sheetFor = null; settingsFor = site }) { Text("Ajustes") } },
            dismissButton = { TextButton(onClick = { sheetFor = null }) { Text("Cerrar") } },
        )
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
private fun SiteTile(site: Site, blobs: BlobStore, prepared: Boolean, onClick: () -> Unit) {
    val icon = rememberFavicon(site, blobs)
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = site.name, modifier = Modifier.size(40.dp), contentScale = ContentScale.Fit)
                } else {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(AvatarColors[abs(site.id.hashCode()) % AvatarColors.size])),
                        contentAlignment = Alignment.Center,
                    ) { Text(site.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge) }
                }
            }
            if (prepared) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(3.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(site.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val line = MaterialTheme.colorScheme.onSurfaceVariant
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(26.dp)) {
                val w = 2.5.dp.toPx()
                drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
                drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Añadir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────────── Ajustes ─────────────────────────

@Composable
private fun SettingsTab(store: ForceStore, version: Int, onChanged: () -> Unit, onMessage: (String?) -> Unit) {
    val practice = remember(version) { store.practiceMode() }
    val haptics = remember(version) { store.haptics() }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineLarge)
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
                    """1. Preparar: busca en el sitio y toca el elemento a forzar.
2. Efecto: se abre el inicio del sitio. Con el truco armado, quien desliza, para y pulsa un elemento de la lista lo ve sustituido y se abre su ficha.
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
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.navigationBarsPadding().height(62.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TabItem("Favoritos", selected == HomeTab.Favorites, { onSelect(HomeTab.Favorites) }) { c -> StarIcon(c) }
            TabItem("Ajustes", selected == HomeTab.Settings, { onSelect(HomeTab.Settings) }) { c -> GearIcon(c) }
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
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

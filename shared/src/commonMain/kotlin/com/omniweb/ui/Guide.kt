package com.omniweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Paleta de las ilustraciones (la del emblema de la app + la del tema)
private val Violet = Color(0xFFA394FF)
private val Teal = Color(0xFF55D6C8)
private val EmblemRed = Color(0xFFF5392A)
private val EmblemYellow = Color(0xFFFFC40D)
private val EmblemTeal = Color(0xFF05B6AB)
private val EmblemCore = Color(0xFF059B8C)
private val Ink = Color(0xFF0E0F14)
private val CardGrey = Color(0xFF3A3F55)
private val CardGreyLight = Color(0xFF50567A)

// ───────────────────────── Dibujos reutilizables ─────────────────────────

/** Estrella de cuatro puntas (el destello del emblema). */
private fun DrawScope.sparkle(c: Offset, a: Float, color: Color) {
    val k = a * 0.18f
    val p = Path().apply {
        moveTo(c.x, c.y - a)
        quadraticBezierTo(c.x + k, c.y - k, c.x + a, c.y)
        quadraticBezierTo(c.x + k, c.y + k, c.x, c.y + a)
        quadraticBezierTo(c.x - k, c.y + k, c.x - a, c.y)
        quadraticBezierTo(c.x - k, c.y - k, c.x, c.y - a)
        close()
    }
    drawPath(p, color)
}

/** Emblema de la app: círculo tricolor con anillo blanco, centro verde y destello. */
@Composable
internal fun Emblem(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val box = Size(r * 2, r * 2)
        val tl = Offset(c.x - r, c.y - r)
        drawArc(EmblemYellow, startAngle = -20f, sweepAngle = 125f, useCenter = true, topLeft = tl, size = box)
        drawArc(EmblemTeal, startAngle = 105f, sweepAngle = 95f, useCenter = true, topLeft = tl, size = box)
        drawArc(EmblemRed, startAngle = 200f, sweepAngle = 140f, useCenter = true, topLeft = tl, size = box)
        drawCircle(Color.White, r * 0.47f, c)
        drawCircle(EmblemCore, r * 0.37f, c)
        sparkle(c, r * 0.27f, Color.White)
    }
}

/** Contorno de un móvil (portrait) con la pantalla pintada con `content`. */
private fun DrawScope.phone(topLeft: Offset, size: Size, screen: Color = Ink, frame: Color = Color(0xFF6B7194)) {
    drawRoundRect(frame, topLeft, size, CornerRadius(size.width * 0.14f))
    val b = size.width * 0.045f
    drawRoundRect(
        screen,
        Offset(topLeft.x + b, topLeft.y + b),
        Size(size.width - 2 * b, size.height - 2 * b),
        CornerRadius(size.width * 0.11f),
    )
}

/** Círculo con halo que representa un dedo tocando. */
private fun DrawScope.finger(c: Offset, r: Float, color: Color = Color.White) {
    drawCircle(color.copy(alpha = 0.22f), r * 1.9f, c)
    drawCircle(color.copy(alpha = 0.40f), r * 1.35f, c)
    drawCircle(color, r, c)
}

/** Tarjeta de lista (imagen + dos líneas de texto). */
private fun DrawScope.card(topLeft: Offset, size: Size, image: Color, text: Color) {
    drawRoundRect(image, topLeft, Size(size.width, size.height * 0.68f), CornerRadius(size.width * 0.1f))
    val h = size.height * 0.07f
    drawRoundRect(text, Offset(topLeft.x, topLeft.y + size.height * 0.76f), Size(size.width * 0.85f, h), CornerRadius(h / 2))
    drawRoundRect(text, Offset(topLeft.x, topLeft.y + size.height * 0.9f), Size(size.width * 0.55f, h), CornerRadius(h / 2))
}

// ───────────────────────── Ilustraciones de cada página ─────────────────────────

/** Móvil con una lista: una tarjeta sustituida (violeta con destello) y el dedo que la toca. */
@Composable
private fun TrickIllustration() {
    Canvas(Modifier.size(width = 250.dp, height = 210.dp)) {
        val w = size.width
        val h = size.height
        val pw = w * 0.5f
        val left = (w - pw) / 2f
        phone(Offset(left, 0f), Size(pw, h))
        val pad = pw * 0.1f
        val cw = (pw - pad * 3f) / 2f
        val ch = h * 0.34f
        val x1 = left + pad
        val x2 = left + pad * 2f + cw
        val y1 = h * 0.08f
        val y2 = y1 + ch + h * 0.08f
        card(Offset(x1, y1), Size(cw, ch), CardGrey, CardGreyLight)
        card(Offset(x2, y1), Size(cw, ch), CardGreyLight, CardGrey)
        card(Offset(x1, y2), Size(cw, ch), CardGreyLight, CardGrey)
        // la tarjeta forzada
        card(Offset(x2, y2), Size(cw, ch), Violet, Teal)
        sparkle(Offset(x2 + cw / 2f, y2 + ch * 0.34f), cw * 0.24f, Color.White)
        finger(Offset(x2 + cw * 0.7f, y2 + ch * 0.62f), w * 0.022f)
        // flechas de scroll a la izquierda
        val ax = w * 0.12f
        drawLine(Teal, Offset(ax, h * 0.3f), Offset(ax, h * 0.7f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(Teal, Offset(ax - 12f, h * 0.38f), Offset(ax, h * 0.28f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(Teal, Offset(ax + 12f, h * 0.38f), Offset(ax, h * 0.28f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(Teal, Offset(ax - 12f, h * 0.62f), Offset(ax, h * 0.72f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(Teal, Offset(ax + 12f, h * 0.62f), Offset(ax, h * 0.72f), strokeWidth = 5f, cap = StrokeCap.Round)
    }
}

/** Móvil con una tarjeta enmarcada por las esquinas de captura y el dedo que la toca. */
@Composable
private fun CaptureIllustration() {
    Canvas(Modifier.size(width = 250.dp, height = 170.dp)) {
        val w = size.width
        val h = size.height
        val pw = w * 0.34f
        val left = (w - pw) / 2f
        phone(Offset(left, 0f), Size(pw, h))
        val cw = pw * 0.62f
        val ch = h * 0.5f
        val cx = left + (pw - cw) / 2f
        val cy = h * 0.2f
        card(Offset(cx, cy), Size(cw, ch), Violet, Teal)
        sparkle(Offset(cx + cw / 2f, cy + ch * 0.33f), cw * 0.22f, Color.White)
        // esquinas de captura
        val m = 10f
        val l = cw * 0.22f
        val st = 5f
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(EmblemYellow, Offset(x, y), Offset(x + dx * l, y), strokeWidth = st, cap = StrokeCap.Round)
            drawLine(EmblemYellow, Offset(x, y), Offset(x, y + dy * l), strokeWidth = st, cap = StrokeCap.Round)
        }
        corner(cx - m, cy - m, 1f, 1f)
        corner(cx + cw + m, cy - m, -1f, 1f)
        corner(cx - m, cy + ch + m, 1f, -1f)
        corner(cx + cw + m, cy + ch + m, -1f, -1f)
        finger(Offset(cx + cw * 0.75f, cy + ch * 0.62f), w * 0.02f)
    }
}

/** Un paso numerado (círculo con número + texto). */
@Composable
private fun StepRow(n: Int, title: String, text: String, color: Color = Violet) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text("$n", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cuatro iconos en fila: abrir, deslizar, parar, tocar. */
@Composable
private fun ActIllustration() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        ActIcon("Abre") { c, s ->
            // cuadrícula de favoritos
            val g = s * 0.3f
            for (i in 0..1) for (j in 0..1) {
                drawRoundRect(
                    if (i == 0 && j == 0) Violet else CardGreyLight,
                    Offset(c.x - g - s * 0.04f + i * (g + s * 0.08f), c.y - g - s * 0.04f + j * (g + s * 0.08f)),
                    Size(g, g),
                    CornerRadius(g * 0.28f),
                )
            }
        }
        ActIcon("Desliza") { c, s ->
            val a = s * 0.42f
            drawLine(Teal, Offset(c.x, c.y - a), Offset(c.x, c.y + a), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(Teal, Offset(c.x - a * 0.35f, c.y - a * 0.6f), Offset(c.x, c.y - a), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(Teal, Offset(c.x + a * 0.35f, c.y - a * 0.6f), Offset(c.x, c.y - a), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(Teal, Offset(c.x - a * 0.35f, c.y + a * 0.6f), Offset(c.x, c.y + a), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(Teal, Offset(c.x + a * 0.35f, c.y + a * 0.6f), Offset(c.x, c.y + a), strokeWidth = 7f, cap = StrokeCap.Round)
        }
        ActIcon("Para") { c, s ->
            val bw = s * 0.15f
            val bh = s * 0.6f
            drawRoundRect(EmblemYellow, Offset(c.x - bw * 1.5f, c.y - bh / 2), Size(bw, bh), CornerRadius(bw / 2))
            drawRoundRect(EmblemYellow, Offset(c.x + bw * 0.5f, c.y - bh / 2), Size(bw, bh), CornerRadius(bw / 2))
        }
        ActIcon("Toca") { c, s ->
            finger(c, s * 0.14f, Violet)
        }
    }
}

@Composable
private fun ActIcon(label: String, draw: DrawScope.(Offset, Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(56.dp)) { draw(Offset(size.width / 2f, size.height / 2f), size.minDimension) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Móvil con las zonas ocultas marcadas: arriba (pulsación larga en la barra de URL) y abajo a la derecha (armar). */
@Composable
private fun GesturesIllustration() {
    Canvas(Modifier.size(width = 250.dp, height = 210.dp)) {
        val w = size.width
        val h = size.height
        val pw = w * 0.42f
        val left = (w - pw) / 2f
        phone(Offset(left, 0f), Size(pw, h))
        // barra de URL
        val bx = left + pw * 0.1f
        drawRoundRect(CardGrey, Offset(bx, h * 0.07f), Size(pw * 0.8f, h * 0.09f), CornerRadius(h * 0.045f))
        // contenido
        drawRoundRect(CardGreyLight, Offset(bx, h * 0.22f), Size(pw * 0.8f, h * 0.3f), CornerRadius(14f))
        drawRoundRect(CardGrey, Offset(bx, h * 0.56f), Size(pw * 0.8f, h * 0.3f), CornerRadius(14f))
        // zona superior: halo + dedo
        finger(Offset(left + pw / 2f, h * 0.115f), w * 0.02f, Violet)
        // zona inferior derecha: halo + ondas de vibración
        val c = Offset(left + pw * 0.82f, h * 0.9f)
        finger(c, w * 0.02f, Teal)
        for (k in 1..2) {
            val rr = w * (0.04f + 0.035f * k)
            drawArc(
                Teal.copy(alpha = 0.75f), startAngle = 150f, sweepAngle = 60f, useCenter = false,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                style = Stroke(4f, cap = StrokeCap.Round),
            )
        }
        // etiquetas con líneas de referencia
        drawLine(Violet, Offset(left + pw / 2f + w * 0.05f, h * 0.115f), Offset(w * 0.9f, h * 0.115f), strokeWidth = 3f)
        drawLine(Teal, Offset(c.x + w * 0.05f, c.y), Offset(w * 0.9f, c.y), strokeWidth = 3f)
    }
}

/** Candado: todo queda en el móvil. */
@Composable
private fun PrivacyIllustration() {
    Canvas(Modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val body = Size(w * 0.6f, h * 0.45f)
        val bl = Offset((w - body.width) / 2f, h * 0.42f)
        drawArc(
            Violet, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(bl.x + body.width * 0.15f, h * 0.12f), size = Size(body.width * 0.7f, h * 0.6f),
            style = Stroke(w * 0.08f, cap = StrokeCap.Round),
        )
        drawRoundRect(Violet, bl, body, CornerRadius(w * 0.08f))
        drawCircle(Ink, w * 0.06f, Offset(w / 2f, bl.y + body.height * 0.4f))
        drawRoundRect(Ink, Offset(w / 2f - w * 0.02f, bl.y + body.height * 0.4f), Size(w * 0.04f, body.height * 0.3f), CornerRadius(w * 0.02f))
    }
}

// ───────────────────────── Guía de inicio rápido ─────────────────────────

private class GuidePage(val title: String, val body: String, val art: (@Composable () -> Unit)?, val extra: (@Composable () -> Unit)? = null)

private val guidePages: List<GuidePage> = listOf(
    GuidePage(
        "Bienvenido a OmniWeb",
        "Un navegador con un secreto: tú preparas un elemento real de una web y, en la actuación, tu público lo ve aparecer justo donde toca. Todo ocurre solo en tu móvil, sin cuentas ni conexión a ningún servidor.",
        { Emblem(150.dp) },
    ),
    GuidePage(
        "El efecto",
        "El espectador desliza una lista de resultados (Google Imágenes, Amazon, YouTube…), se detiene y toca el elemento que quiere. Ese elemento es el tuyo: la web abre SU ficha con lo que tú has preparado. Solo se fuerza una vez por sitio.",
        { TrickIllustration() },
    ),
    GuidePage(
        "1 · Prepara el sitio",
        "En la pestaña Sitios pulsa Preparar en la web que vayas a usar. Es un paso privado, antes de la actuación.",
        { CaptureIllustration() },
        {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StepRow(1, "Busca y captura", "Navega hasta ver el elemento en la lista y pulsa «Capturar elemento»; toca el elemento.")
                StepRow(2, "Captura su ficha", "Ábrelo como lo haría el espectador y pulsa «Capturar ficha»: la imagen principal de su ficha.", Teal)
                StepRow(3, "Guarda", "Confirma y listo: el sitio queda marcado como «Preparado».", EmblemYellow)
            }
        },
    ),
    GuidePage(
        "2 · Actúa",
        "Entrega el móvil con la app abierta en Favoritos: parece un navegador normal con sus accesos directos.",
        { ActIllustration() },
        {
            Text(
                "Toca el icono del sitio preparado. Cuando el espectador deslice, pare y toque un elemento de la lista, verá el tuyo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
    ),
    GuidePage(
        "Gestos secretos",
        "Pulsación larga en la barra de URL: abre las opciones ocultas (reiniciar, ver la web real, salir). En Favoritos te lleva a Ajustes.\n\nBotón de volumen o toque en la esquina inferior derecha: arma o desarma el truco; el móvil vibra para confirmarlo.",
        { GesturesIllustration() },
    ),
    GuidePage(
        "Todo queda en tu móvil",
        "Sin cuentas, sin telemetría y sin enviar nada. Las imágenes y los ajustes se guardan solo en este dispositivo. Encontrarás el manual completo en Ajustes → Instrucciones y podrás volver a ver esta guía cuando quieras.",
        { PrivacyIllustration() },
    ),
)

/**
 * Guía de inicio rápido: varias páginas deslizables con dibujos. En la última se puede cerrar y, además, desactivarla para que no
 * vuelva a salir al arrancar la app. `disableInitially` = estado actual de «no volver a mostrar».
 */
@Composable
internal fun QuickGuideScreen(disableInitially: Boolean, fromSettings: Boolean, onClose: (disable: Boolean) -> Unit) {
    val pager = rememberPagerState(pageCount = { guidePages.size })
    val scope = rememberCoroutineScope()
    var disable by remember { mutableStateOf(disableInitially) }
    val last = pager.currentPage == guidePages.size - 1
    BackPressEffect(enabled = true) { onClose(disable) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(guidePages.size - 1) } }) { Text("Saltar") }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = guidePages[i]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                p.art?.let { art -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { art() } }
                Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                p.extra?.invoke()
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            for (i in guidePages.indices) {
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (on) 10.dp else 8.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        if (last) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable { disable = !disable }, verticalAlignment = Alignment.CenterVertically) {
                Text("No volver a mostrar esta guía al iniciar", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = disable, onCheckedChange = { disable = it })
            }
            Button(
                onClick = { onClose(disable) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (fromSettings) "Cerrar" else "Empezar") }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    enabled = pager.currentPage > 0,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Atrás") }
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Siguiente") }
            }
        }
    }
}

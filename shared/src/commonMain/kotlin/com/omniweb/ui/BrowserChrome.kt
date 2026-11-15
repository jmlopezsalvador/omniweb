package com.omniweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Marco de navegador DECORATIVO del efecto. No es un navegador: solo acompaña el aspecto (barra de estado visible, barra
 * de URL arriba y, en iOS, la barra de herramientas inferior que se esconde al bajar). Estilo Safari en iOS y Chrome en
 * Android. La pulsación larga sobre la barra de URL abre el menú oculto (zona grande y centrada).
 */

/** Dominio para mostrar en la barra: sin esquema, `www.` ni ruta (como Safari). */
fun displayHost(url: String): String {
    val noScheme = url.substringAfter("://", url)
    return noScheme.substringBefore('/').substringBefore('?').substringBefore('#').removePrefix("www.").removePrefix("m.")
        .ifBlank { url }
}

private val IosBar = Color(0xFF1C1C1E)
private val IosField = Color(0xFF2C2C2E)
private val IosText = Color(0xFFF2F2F7)
private val IosIcon = Color(0xFF0A84FF)
private val ChromeBar = Color(0xFF202124)
private val ChromeField = Color(0xFF35363A)
private val ChromeText = Color(0xFFE8EAED)

/** Barra superior. Ocupa también la zona de la barra de estado (hora, batería) con su mismo color. */
@Composable
fun BrowserTopBar(ios: Boolean, url: String, onMenu: () -> Unit) {
    val bar = if (ios) IosBar else ChromeBar
    Box(Modifier.fillMaxWidth().background(bar).statusBarsPadding()) {
        if (ios) {
            Row(
                Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(IosField)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { onMenu() }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LockIcon(IosText.copy(alpha = 0.7f), 12.dp)
                        Text(displayHost(url), color = IosText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(ChromeField)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { onMenu() }) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LockIcon(ChromeText.copy(alpha = 0.8f), 14.dp)
                    Text(displayHost(url), color = ChromeText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                TabsBadge(ChromeText)
                Spacer(Modifier.width(6.dp))
                DotsIcon(ChromeText)
            }
        }
    }
}

/** Barra inferior de Safari (solo iOS). `visible=false` la esconde con la altura a 0 (al bajar). */
@Composable
fun BrowserBottomBar(visible: Boolean) {
    Box(Modifier.fillMaxWidth().background(IosBar).navigationBarsPadding()) {
        if (visible) {
            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Chevron(left = true, color = IosIcon)
                Chevron(left = false, color = IosIcon.copy(alpha = 0.35f))
                ShareIcon(IosIcon)
                BookIcon(IosIcon)
                TabsIcon(IosIcon)
            }
        }
    }
}

// ───────────── iconos dibujados (sin material-icons-extended) ─────────────

private fun DrawScope.stroke(w: Float = 2.dp.toPx()) = Stroke(width = w, cap = StrokeCap.Round)

@Composable
private fun LockIcon(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(color, Offset(w * 0.15f, h * 0.45f), Size(w * 0.7f, h * 0.5f), CornerRadius(w * 0.12f))
        drawArc(color, 180f, 180f, false, Offset(w * 0.28f, h * 0.08f), Size(w * 0.44f, h * 0.7f), style = Stroke(w * 0.14f, cap = StrokeCap.Round))
    }
}

@Composable
private fun Chevron(left: Boolean, color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val x1 = if (left) w * 0.62f else w * 0.38f
        val x2 = if (left) w * 0.36f else w * 0.64f
        drawLine(color, Offset(x1, h * 0.2f), Offset(x2, h * 0.5f), 2.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(x2, h * 0.5f), Offset(x1, h * 0.8f), 2.5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ShareIcon(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.22f, h * 0.4f), Size(w * 0.56f, h * 0.5f), CornerRadius(w * 0.08f), style = stroke())
        drawLine(color, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.6f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.36f, h * 0.24f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.64f, h * 0.24f), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun BookIcon(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.12f, h * 0.22f), Size(w * 0.76f, h * 0.56f), CornerRadius(w * 0.06f), style = stroke())
        drawLine(color, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.76f), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun TabsIcon(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.28f, h * 0.14f), Size(w * 0.58f, h * 0.5f), CornerRadius(w * 0.08f), style = stroke())
        drawRoundRect(color, Offset(w * 0.14f, h * 0.34f), Size(w * 0.58f, h * 0.5f), CornerRadius(w * 0.08f), style = stroke())
    }
}

@Composable
private fun TabsBadge(color: Color) {
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            drawRoundRect(color, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), CornerRadius(5.dp.toPx()), style = stroke())
        }
        Text("1", color = color, fontSize = 12.sp)
    }
}

@Composable
private fun DotsIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val r = 2.2.dp.toPx()
        for (i in 0..2) drawCircle(color, r, Offset(size.width / 2, size.height * (0.2f + 0.3f * i)))
    }
}

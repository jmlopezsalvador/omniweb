package com.omniweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Un apartado del manual. Líneas que empiezan por «• » = viñeta; por «» (cadena vacía) = separador. */
private class Section(val title: String, val lines: List<String>, val highlight: Boolean = false)

private val manual: List<Section> = listOf(
    Section(
        "El objetivo de la aplicación",
        listOf(
            "OmniWeb es un navegador pensado para el mentalismo y la magia con el móvil. Funciona como un navegador normal, con una web real cargada en un WebView, pero te permite preparar de antemano UN elemento de esa web para que aparezca cuando el espectador elija.",
            "No se sube ninguna imagen a ninguna parte y no se modifica ninguna web: la sustitución se hace solo en la pantalla de tu móvil, mientras la página se muestra. No hay servidor propio, cuentas ni telemetría; todo se guarda en el dispositivo.",
        ),
    ),
    Section(
        "El objetivo del efecto",
        listOf(
            "Que el espectador crea que elige libremente un resultado de una lista real (una imagen de Google, un producto de Amazon, un vídeo de YouTube…) y que, al tocarlo, la propia web abra SU ficha con el elemento que tú habías preparado: su imagen, su título, su precio, etc.",
            "El efecto está pensado para que parezca que nada ha cambiado: el móvil se ve como un navegador normal, con barra de dirección, botones atrás y adelante, y la web hace lo que haría siempre.",
        ),
    ),
    Section(
        "Cómo funciona, en general",
        listOf(
            "Hay dos momentos: la preparación (en privado) y la actuación (delante del público).",
            "• Preparación: abres un sitio, encuentras en su lista el elemento que quieres forzar y lo capturas. La app guarda su imagen, el aspecto de su tarjeta y, si quieres, lo que muestra su ficha.",
            "• Actuación: el espectador abre el navegador, desliza la lista y se detiene. Con el truco armado, el elemento que toque se sustituye por el tuyo. Solo se fuerza una vez por sitio.",
            "La ficha puede ser de dos tipos. Si la web abre OTRA página (Amazon, YouTube), la app guarda su dirección y, al tocar, lleva directamente a esa página real. Si la ficha es un panel dentro de la misma página (Google Imágenes), la app guarda la dirección que tiene ese panel y también navega a ella; si el panel no cambiara la dirección, se sustituye la imagen principal sobre el propio panel.",
        ),
    ),
    Section(
        "Las tres pestañas",
        listOf(
            "• Favoritos: es lo que ve el espectador. Parece la pantalla de favoritos de un navegador, con el marco de navegador y sin ninguna marca del efecto. Tocar el icono de un sitio preparado lanza el efecto; si el sitio no está preparado, no ocurre nada. Se sale a Ajustes con una pulsación larga en la barra de dirección.",
            "• Sitios: tu zona privada. Una tarjeta por sitio con su estado («Sin preparar» o «Preparado»), botones Preparar y Efecto, y el menú ⋮ con los ajustes del sitio. «Añadir web» permite usar cualquier otra dirección.",
            "• Ajustes: opciones generales de la app, estas instrucciones y la guía de inicio rápido.",
        ),
    ),
    Section(
        "Preparar un sitio, paso a paso",
        listOf(
            "En Sitios pulsa Preparar en la tarjeta del sitio. Se abre el navegador de preparación:",
            "• Barra de dirección y botón «Ir»: para navegar a la búsqueda o a la página que necesites.",
            "• «‹ Inicio»: sales sin guardar. «Ocultar barra» / «Mostrar barra»: libera espacio quitando la barra de dirección.",
            "• «Capturar elemento»: arma la captura. Mientras está armada, tocar un elemento NO abre la web: lo captura. Pulsa «Cancelar» para desarmarla y poder navegar.",
            "",
            "Paso 1 — el elemento de la lista. Navega hasta ver la lista con el elemento que quieres forzar, pulsa «Capturar elemento» y toca el elemento. Aparece un diálogo con su título y tres salidas:",
            "• «Capturar su ficha»: continúa con el paso 2 (lo recomendado).",
            "• «Guardar solo la lista (sin ficha)»: el efecto solo sustituirá el elemento en la lista; al tocarlo la web abrirá su propia ficha.",
            "• «Elegir otro»: descarta y vuelves a elegir.",
            "",
            "Paso 2 — la ficha. La captura queda desarmada para que puedas abrir la ficha como lo haría el espectador: toca el elemento y se abre su panel o su página. Entonces pulsa «Capturar ficha» y toca su imagen principal. En la barra tienes también «Sin ficha» (guardar ya solo la lista) y «Otro» (volver a elegir el elemento de la lista).",
            "Si la ficha es otra página, o un panel con su propia dirección, el diálogo avisa: «La ficha es una PÁGINA distinta: al forzar, el elemento de la lista abrirá directamente esa página real». Pulsa Guardar y volverás a Sitios con el mensaje «Forzaje guardado».",
            "",
            "Consejos para preparar bien:",
            "• Captura el MISMO tipo de tarjeta donde se detendrá el espectador (por ejemplo, un resultado normal de la lista y no uno del carrusel patrocinado).",
            "• Prepara y actúa con los mismos ajustes (móvil o escritorio) y, si puedes, en la misma búsqueda.",
            "• Si la web cambia mucho su aspecto, vuelve a preparar el sitio.",
            "• Los avisos de cookies los gestionas tú: acéptalos o recházalos antes de la actuación; la app nunca lo hace por ti.",
        ),
    ),
    Section(
        "Ajustes de cada sitio (menú ⋮ de la tarjeta)",
        listOf(
            "• Armado — «Desde el inicio»: el efecto arranca armado; en cuanto el espectador deslice, pare y toque un elemento de la lista, se fuerza. «Con gesto secreto»: el efecto arranca como una web normal (útil para una demostración) y se arma con el gesto secreto; el móvil vibra al armarlo (un pulso) y al desarmarlo (dos).",
            "• Parada del scroll — 300, 450 o 600 ms: cuánto tiempo sin deslizar se considera que el espectador se ha detenido. Con 300 ms el efecto reacciona antes; con 600 ms es más tolerante con quien va despacio. 450 ms va bien casi siempre.",
            "• Web de escritorio: pide a la web su versión de ordenador (más ancha, letra más pequeña). Se usa tanto al preparar como al actuar.",
            "• Marco de navegador: añade la barra de dirección (y la barra inferior con atrás/adelante en iPhone) durante el efecto, para que parezca un navegador. Se puede cambiar en directo desde las opciones ocultas.",
            "• Borrar forzaje guardado: elimina lo preparado de ese sitio.",
            "• Quitar este sitio: solo aparece en los sitios que has añadido tú.",
            "Las «pastillas» de la tarjeta resumen estos ajustes: Armado / Arma con gesto, Móvil / Escritorio, los milisegundos y «Con ficha» si se capturó su ficha.",
        ),
    ),
    Section(
        "Ajustes generales (pestaña Ajustes)",
        listOf(
            "• Aspecto de Favoritos — «Cuadrícula de favoritos»: iconos de tus sitios sobre fondo claro. «Inicio Google»: la web real de Google con tus sitios en una fila bajo el buscador.",
            "• Modo práctica: al salir del efecto se borran historial, cookies y caché, para ensayar sin dejar rastro. Ojo: al borrar cookies los avisos de consentimiento vuelven a aparecer.",
            "• Vibración al armar: activa o desactiva la vibración de confirmación del gesto secreto.",
            "• Rastros — «Borrar rastros ahora»: borra cookies, historial y caché de todos los sitios; se cerrarán las sesiones iniciadas.",
            "• Instrucciones: este manual.",
            "• Guía de inicio rápido: vuelve a ver la guía de bienvenida. El interruptor «Mostrar la guía al iniciar» decide si aparece cada vez que arrancas la app.",
        ),
    ),
    Section(
        "Durante la actuación: gestos y opciones ocultas",
        listOf(
            "• Armar o desarmar el truco: botón de volumen, o un toque en la esquina inferior derecha de la pantalla. El móvil vibra para confirmarlo (si la vibración está activada).",
            "• Opciones ocultas: pulsación larga en la barra de dirección (si has ocultado el marco, en el centro de la parte superior de la pantalla). Ofrece: «Reiniciar el efecto» (recarga y vuelve al estado inicial), «Mostrar la web real (sin forzar)» / «Reanudar el forzaje», «Ocultar / Mostrar el marco de navegador» y «Salir».",
            "• Atrás y adelante funcionan como en cualquier navegador: si la ficha es otra página, el espectador puede volver a la lista y verá su elemento ya sustituido.",
            "• En Favoritos, una pulsación larga en la barra de dirección te lleva a Ajustes.",
        ),
    ),
    Section(
        "Ejemplo guiado: preparar Google Imágenes",
        listOf(
            "1. Abre la pestaña Sitios y pulsa Preparar en «Google Imágenes». Si sale el aviso de cookies, resuélvelo tú.",
            "2. En la barra de dirección escribe una búsqueda (por ejemplo, «gatos»; también sirve cualquier otra) y pulsa «Ir». Verás la rejilla de imágenes.",
            "3. Pulsa «Capturar elemento» y toca la imagen que quieras forzar. Se abre el diálogo «¿Guardar este elemento?».",
            "4. Pulsa «Capturar su ficha». La barra pasa a «Paso 2».",
            "5. Toca la imagen en la rejilla, como haría el espectador: Google abre su panel con la imagen grande.",
            "6. Pulsa «Capturar ficha» y toca la imagen grande del panel. Aparece «¿Guardar la lista y la ficha?».",
            "7. Pulsa Guardar. Vuelves a Sitios y la tarjeta muestra «Preparado» y «Con ficha».",
            "8. Para probarlo: pestaña Favoritos → toca el icono de Google Imágenes → busca lo mismo → desliza, para y toca CUALQUIER otra imagen. Se abrirá la ficha de la que preparaste. Pulsa atrás: en la lista, la imagen tocada aparece ya sustituida.",
            "9. Para repetir el ensayo, usa «Reiniciar el efecto» en las opciones ocultas (pulsación larga en la barra de dirección).",
        ),
        highlight = true,
    ),
    Section(
        "Límites y recomendaciones",
        listOf(
            "• El efecto se fuerza una sola vez por sitio y por sesión; «Reiniciar el efecto» lo vuelve a armar.",
            "• Funciona sobre webs reales, que pueden cambiar: si algún día deja de reconocer la lista o la ficha, vuelve a preparar el sitio.",
            "• Una ficha que depende de una sesión muy concreta puede no abrirse igual semanas después: ensaya antes de actuar.",
            "• No uses webs con inicio de sesión que no controles; no necesitas cuenta para ninguno de los sitios predefinidos.",
            "• Practica varias veces con «Modo práctica» activado y desactívalo antes de actuar si quieres que los avisos de cookies ya estén resueltos.",
        ),
    ),
    Section(
        "Privacidad",
        listOf(
            "Todos los datos (imágenes capturadas, textos, ajustes) se guardan únicamente en este dispositivo. La app no tiene cuentas, no envía información a ningún servidor propio y no incluye telemetría. Las únicas conexiones son las que hace el navegador a las webs que tú abres.",
        ),
    ),
)

/** Página de instrucciones detalladas: se abre desde Ajustes. */
@Composable
internal fun InstructionsScreen(onBack: () -> Unit) {
    BackPressEffect(enabled = true, onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(onClick = onBack) { Text("‹ Ajustes") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Instrucciones", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Qué es OmniWeb, cómo se prepara un sitio, qué hace cada ajuste y un ejemplo completo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(manual, key = { it.title }) { sec ->
                Surface(
                    color = if (sec.highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sec.title, style = MaterialTheme.typography.titleLarge)
                        sec.lines.forEach { line ->
                            when {
                                line.isEmpty() -> Spacer(Modifier.height(4.dp))
                                line.startsWith("• ") -> Text(
                                    line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                                else -> Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

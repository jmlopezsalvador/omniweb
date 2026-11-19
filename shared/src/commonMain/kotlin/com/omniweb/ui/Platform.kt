package com.omniweb.ui

import androidx.compose.runtime.Composable

/**
 * Lo que depende del sistema se declara aquí (`expect`) y se implementa en androidMain / iosMain (`actual`).
 * Todo lo demás va en común.
 */

/** `immersive` = mantiene la pantalla encendida durante el efecto. Ya NO oculta las barras del sistema: se ve la hora y la batería como en un navegador. */
@Composable
expect fun SystemBarsEffect(immersive: Boolean)

/** Mientras `enabled`, las teclas de volumen llaman a `onPress` y NO cambian el volumen (disparo manual oculto). */
@Composable
expect fun VolumeKeyEffect(enabled: Boolean, onPress: () -> Unit)

/** Borra cookies, almacenamiento web, caché e historial de navegación de todos los sitios. */
expect fun clearBrowsingData()

/** true en iOS: elige el estilo del marco de navegador (Safari con barra inferior) frente al de Android (Chrome). */
expect fun isIosPlatform(): Boolean

/** Vibración de confirmación al armar (`armed=true`: un pulso) o desarmar (`false`: dos pulsos cortos). */
expect fun vibrateFeedback(armed: Boolean)

/** Mientras `enabled`, la tecla/gesto atrás del sistema llama a `onBack` (en iOS no hay tecla atrás: no hace nada). */
@Composable
expect fun BackPressEffect(enabled: Boolean, onBack: () -> Unit)

package com.omniweb.ui

import androidx.compose.runtime.Composable

/**
 * Lo que depende del sistema se declara aquí (`expect`) y se implementa en androidMain / iosMain (`actual`).
 * Todo lo demás va en común.
 */

/** `immersive` = oculta las barras del sistema y mantiene la pantalla encendida (pantalla del efecto). */
@Composable
expect fun SystemBarsEffect(immersive: Boolean)

/** Mientras `enabled`, las teclas de volumen llaman a `onPress` y NO cambian el volumen (disparo manual oculto). */
@Composable
expect fun VolumeKeyEffect(enabled: Boolean, onPress: () -> Unit)

/** Borra cookies, almacenamiento web, caché e historial de navegación de todos los sitios. */
expect fun clearBrowsingData()

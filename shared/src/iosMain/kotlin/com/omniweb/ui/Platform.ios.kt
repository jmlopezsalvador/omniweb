@file:OptIn(ExperimentalForeignApi::class)

package com.omniweb.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/** iOS: la barra de estado queda visible; en el efecto Swift mantiene la pantalla encendida. */
@Composable
actual fun SystemBarsEffect(immersive: Boolean) {
    val host = iosWebHost
    DisposableEffect(immersive) {
        host?.setImmersive(immersive)
        onDispose { if (immersive) host?.setImmersive(false) }
    }
}

/**
 * iOS: las teclas de volumen se detectan en Swift observando `AVAudioSession.outputVolume`. Ojo: con el volumen al
 * máximo (o al mínimo) pulsar más no cambia nada y no hay evento; el gesto fiable es la esquina inferior derecha.
 */
@Composable
actual fun VolumeKeyEffect(enabled: Boolean, onPress: () -> Unit) {
    val host = iosWebHost
    val latest by rememberUpdatedState(onPress)
    DisposableEffect(enabled) {
        if (enabled) {
            host?.setVolumeListener(
                object : VoidCallback {
                    override fun invoke() = latest()
                },
            )
        }
        onDispose { host?.setVolumeListener(null) }
    }
}

/** iOS: `WKWebsiteDataStore` + `URLCache` + cookies, en Swift. */
actual fun clearBrowsingData() {
    iosWebHost?.clearBrowsingData()
}

/** Textos grandes (imagen, HTML de la tarjeta) como ficheros en Application Support, gestionados por Swift. */
class HostBlobStore : BlobStore {
    override fun read(name: String): String? = iosWebHost?.readBlob(name)
    override fun write(name: String, data: String) {
        iosWebHost?.writeBlob(name, data)
    }
    override fun delete(name: String) {
        iosWebHost?.deleteBlob(name)
    }
}

class UserDefaultsStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = d.stringForKey(key)
    override fun getBoolean(key: String, default: Boolean) =
        if (d.objectForKey(key) == null) default else d.boolForKey(key)
    override fun getLong(key: String, default: Long) =
        if (d.objectForKey(key) == null) default else d.integerForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, key)
    override fun putBoolean(key: String, value: Boolean) = d.setBool(value, key)
    override fun putLong(key: String, value: Long) = d.setInteger(value, key)
}

actual fun isIosPlatform(): Boolean = true

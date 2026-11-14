@file:OptIn(ExperimentalForeignApi::class)

package com.omniweb.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/** iOS: la barra de estado y el indicador de inicio se ocultan en ContentView.swift (Fase 4: inmersivo + idleTimerDisabled). */
@Composable
actual fun SystemBarsEffect(immersive: Boolean) {}

/** iOS: pendiente (Fase 4): no hay botón de volumen programable sin APIs privadas; se usará otro gesto. */
@Composable
actual fun VolumeKeyEffect(enabled: Boolean, onPress: () -> Unit) {}

/** iOS: pendiente (Fase 4): WKWebsiteDataStore.defaultDataStore().removeData(...). */
actual fun clearBrowsingData() {}

/** iOS: de momento en NSUserDefaults (Fase 4: ficheros en Application Support). */
class UserDefaultsBlobStore : BlobStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun read(name: String): String? = d.stringForKey("blob.$name")
    override fun write(name: String, data: String) = d.setObject(data, "blob.$name")
    override fun delete(name: String) = d.removeObjectForKey("blob.$name")
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

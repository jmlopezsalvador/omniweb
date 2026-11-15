package com.omniweb.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

/** Contexto de aplicación, lo fija `MainActivity` (hace falta para borrar la caché del WebView). */
object AndroidApp {
    lateinit var context: Context
}

/** Puente de las teclas de volumen: `MainActivity` las entrega aquí y, si hay escucha, se consumen. */
object VolumeKeyBus {
    @Volatile
    var listener: (() -> Unit)? = null

    /** true si alguien las ha tratado (la actividad no debe subir/bajar el volumen). */
    fun dispatch(): Boolean {
        val l = listener ?: return false
        l()
        return true
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Android: solo mantiene la pantalla encendida durante el efecto. Las barras del sistema se quedan visibles. */
@Composable
actual fun SystemBarsEffect(immersive: Boolean) {
    val view = LocalView.current
    DisposableEffect(immersive) {
        if (immersive) view.keepScreenOn = true
        onDispose { if (immersive) view.keepScreenOn = false }
    }
}

@Composable
actual fun VolumeKeyEffect(enabled: Boolean, onPress: () -> Unit) {
    val latest by rememberUpdatedState(onPress)
    DisposableEffect(enabled) {
        if (enabled) VolumeKeyBus.listener = { latest() }
        onDispose { VolumeKeyBus.listener = null }
    }
}

actual fun clearBrowsingData() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
    // La caché de disco es común a todos los WebView: basta una instancia temporal.
    WebView(AndroidApp.context).apply {
        clearCache(true)
        clearHistory()
        destroy()
    }
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val p = context.getSharedPreferences("omniweb", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = p.getString(key, null)
    override fun getBoolean(key: String, default: Boolean) = p.getBoolean(key, default)
    override fun getLong(key: String, default: Long) = p.getLong(key, default)
    override fun putString(key: String, value: String) = p.edit().putString(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = p.edit().putBoolean(key, value).apply()
    override fun putLong(key: String, value: Long) = p.edit().putLong(key, value).apply()
}

/** Textos grandes (imagen, HTML de la tarjeta) como ficheros internos de la app. */
class FileBlobStore(context: Context) : BlobStore {
    private val dir = File(context.filesDir, "blobs").apply { mkdirs() }
    private fun file(name: String) = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    override fun read(name: String): String? = file(name).takeIf { it.exists() }?.readText()
    override fun write(name: String, data: String) {
        val f = file(name)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(data)
        tmp.renameTo(f)
    }
    override fun delete(name: String) {
        file(name).delete()
    }
}

actual fun isIosPlatform(): Boolean = false

/** Un pulso largo al armar; dos pulsos cortos al desarmar. */
actual fun vibrateFeedback(armed: Boolean) {
    val ctx = AndroidApp.context
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (!vibrator.hasVibrator()) return
    val effect = if (armed) {
        VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
    } else {
        VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1)
    }
    vibrator.vibrate(effect)
}

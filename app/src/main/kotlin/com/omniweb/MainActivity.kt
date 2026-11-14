package com.omniweb

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.omniweb.ui.AndroidApp
import com.omniweb.ui.FileBlobStore
import com.omniweb.ui.OmniWebRoot
import com.omniweb.ui.SharedPrefsStore
import com.omniweb.ui.VolumeKeyBus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidApp.context = applicationContext
        val prefs = SharedPrefsStore(applicationContext)
        val blobs = FileBlobStore(applicationContext)
        setContent { OmniWebRoot(prefs, blobs) }
    }

    // Botones de volumen = disparo manual oculto en el efecto (si nadie escucha, funcionan con normalidad)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && VolumeKeyBus.listener != null) {
            if (event.repeatCount == 0) VolumeKeyBus.dispatch()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && VolumeKeyBus.listener != null) return true
        return super.onKeyUp(keyCode, event)
    }
}

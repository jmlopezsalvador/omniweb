package com.omniweb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.omniweb.ui.OmniWebRoot
import com.omniweb.ui.SharedPrefsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = SharedPrefsStore(applicationContext)
        setContent { OmniWebRoot(store) }
    }
}

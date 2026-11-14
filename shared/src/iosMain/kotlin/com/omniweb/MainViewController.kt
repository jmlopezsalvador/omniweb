package com.omniweb

import androidx.compose.ui.window.ComposeUIViewController
import com.omniweb.ui.OmniWebRoot
import com.omniweb.ui.SettingsStore
import com.omniweb.ui.UserDefaultsStore
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    OmniWebRoot(SettingsStore(UserDefaultsStore()))
}

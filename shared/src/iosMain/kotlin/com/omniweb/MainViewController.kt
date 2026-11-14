package com.omniweb

import androidx.compose.ui.window.ComposeUIViewController
import com.omniweb.ui.HostBlobStore
import com.omniweb.ui.OmniWebRoot
import com.omniweb.ui.UserDefaultsStore
import com.omniweb.ui.WebViewHost
import com.omniweb.ui.iosWebHost
import platform.UIKit.UIViewController

/** Punto de entrada desde Swift: `MainViewControllerKt.MainViewController(host: OmniWebHost())`. */
fun MainViewController(host: WebViewHost): UIViewController {
    iosWebHost = host
    return ComposeUIViewController {
        OmniWebRoot(UserDefaultsStore(), HostBlobStore())
    }
}

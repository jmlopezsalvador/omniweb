package com.omniweb.ui

import platform.UIKit.UIView

/*
 * Puente Kotlin ⇄ Swift del navegador de iOS.
 *
 * El `WKWebView` y todo lo del sistema (datos web, ficheros, volumen, temporizador de bloqueo) viven en Swift
 * (`iosApp/iosApp/OmniWebHost.swift`), que implementa `WebViewHost` y se entrega en `MainViewController(host)`.
 * Así el código de WebKit lo escribe quien lo conoce bien y Kotlin solo lo orquesta. Cualquier cambio en estas
 * firmas obliga a cambiar también el Swift (los nombres de las etiquetas salen de los de los parámetros).
 */

/** Eventos del navegador hacia Compose. Swift los llama siempre en el hilo principal. */
interface WebViewCallbacks {
    /** El motor JS entregó el elemento tocado en modo captura. */
    fun onCapture(imageUrl: String, imageData: String?, texts: List<String>, html: String?, pageUrl: String, aspect: Double)

    /** Esquina inferior derecha del efecto (disparo manual). Compose queda por debajo del WKWebView: lo gestiona Swift. */
    fun onHiddenTap()

    /** Pulsación larga en la esquina superior izquierda del efecto (menú oculto). */
    fun onHiddenLongPress()
}

/** Callback con un texto opcional (resultado de una descarga). */
interface StringCallback {
    fun invoke(value: String?)
}

/** Callback sin datos. */
interface VoidCallback {
    fun invoke()
}

interface WebViewHost {
    /** Crea el navegador. `effectGestures`: añade las esquinas ocultas nativas (solo en el efecto). */
    fun createWebView(desktop: Boolean, effectGestures: Boolean, callbacks: WebViewCallbacks): UIView

    fun loadUrl(view: UIView, url: String)

    /**
     * Script que se ejecuta al terminar de cargar cada página, DESPUÉS de inyectar el motor (`omniweb.js`, que Swift
     * inyecta al empezar el documento). Vacío = solo el motor, sin configurarlo.
     */
    fun setStartScript(view: UIView, js: String)

    fun evaluate(view: UIView, js: String)

    fun clearBrowsingData()

    /** Descarga una imagen y la devuelve como data: URL (null si falla). */
    fun fetchImage(url: String, referer: String, completion: StringCallback)

    fun readBlob(name: String): String?
    fun writeBlob(name: String, data: String)
    fun deleteBlob(name: String)

    /** Pantalla encendida mientras dura el efecto. */
    fun setImmersive(on: Boolean)

    /** Escucha las teclas de volumen (null = dejar de escuchar). */
    fun setVolumeListener(listener: VoidCallback?)
}

/** Lo fija `MainViewController(host)` al arrancar. */
var iosWebHost: WebViewHost? = null

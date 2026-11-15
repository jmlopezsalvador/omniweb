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
    fun onCapture(imageUrl: String, imageData: String?, texts: List<String>, html: String?, detailUrl: String?, pageUrl: String, aspect: Double)

    /** El motor ha forzado: ficha del objeto (vacía si no hay). */
    fun onForced(url: String)

    /** Gesto de «atrás» (arrastre desde el borde izquierdo) en la capa de la ficha. */
    fun onBack()

    /** URL y título actuales (barra de URL decorativa). */
    fun onPageInfo(url: String, title: String)

    /** Dirección del scroll (true = bajando), solo si la config lleva `reportScroll`. */
    fun onScroll(down: Boolean)

    /** Esquina inferior derecha del efecto (disparo manual). Compose queda por debajo del navegador: lo gestiona Swift. */
    fun onHiddenTap()

    /** Pulsación larga en la zona superior CENTRAL del efecto (menú oculto). */
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
    /** Crea el navegador. `effectGestures`: añade las esquinas ocultas nativas (solo en el efecto). `layer`: 0 principal, 1 ficha (encima). */
    fun createWebView(desktop: Boolean, effectGestures: Boolean, layer: Int, callbacks: WebViewCallbacks): UIView

    /**
     * El navegador es una capa NATIVA (SwiftUI) por encima de Compose, no un `UIKitView`: así los toques y el scroll van
     * directos al WKWebView sin pasar por Compose. Compose reserva el hueco y avisa de su rectángulo (en puntos, respecto a
     * la pantalla). `hidden`: un diálogo de Compose debe quedar visible por encima.
     */
    fun layoutWebView(view: UIView, x: Double, y: Double, width: Double, height: Double, hidden: Boolean)

    /** Quita el navegador de la pantalla. */
    fun destroyWebView(view: UIView)

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

    /** Vibración de confirmación: `armed=true` un pulso, `false` dos pulsos cortos. */
    fun vibrate(armed: Boolean)

    /** Pantalla encendida mientras dura el efecto. */
    fun setImmersive(on: Boolean)

    /** Escucha las teclas de volumen (null = dejar de escuchar). */
    fun setVolumeListener(listener: VoidCallback?)
}

/** Lo fija `MainViewController(host)` al arrancar. */
var iosWebHost: WebViewHost? = null

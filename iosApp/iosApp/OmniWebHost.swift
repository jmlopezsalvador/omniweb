import AVFoundation
import Shared
import UIKit
import WebKit

// Navegador de iOS: WKWebView + motor JS compartido (omniweb.js). Implementa el protocolo `WebViewHost` que declara
// Kotlin en shared/src/iosMain/.../IosWebHost.kt; Compose lo embebe con UIKitView. Si cambias una firma allí,
// cámbiala aquí (las etiquetas salen de los nombres de los parámetros de Kotlin).

private let webSchemes: Set<String> = ["http", "https", "about", "data", "blob", "file", "javascript"]

private let desktopUserAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

private let downloadUserAgent =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

/// El motor llama a `window.OmniBridge.onCapture(json)` (así es en Android); aquí se reenvía al manejador nativo.
private let bridgeShim = """
window.OmniBridge = { onCapture: function (json) { window.webkit.messageHandlers.OmniBridge.postMessage(json); } };
"""

/// Cargado una vez: el motor compilado, empaquetado como recurso (web/dist/omniweb.js, ver project.yml).
private let engineJS: String = {
    guard let path = Bundle.main.path(forResource: "omniweb", ofType: "js"),
          let text = try? String(contentsOfFile: path, encoding: .utf8) else { return "" }
    return text
}()

/// WKUserContentController retiene a su manejador: se usa un intermediario débil para no crear un ciclo.
private final class WeakScriptHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}

final class OmniWebView: WKWebView, WKNavigationDelegate, WKScriptMessageHandler {
    private let callbacks: WebViewCallbacks
    private let cornerTap = UIView()
    private let cornerMenu = UIView()
    private let hasCorners: Bool

    init(desktop: Bool, effectGestures: Bool, callbacks: WebViewCallbacks) {
        self.callbacks = callbacks
        self.hasCorners = effectGestures
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        // Sin esto el user agent no lleva «Safari» y algunas webs (Google) sirven otra maquetación
        config.applicationNameForUserAgent = "Version/17.0 Mobile/15E148 Safari/604.1"
        if desktop {
            config.defaultWebpagePreferences.preferredContentMode = .desktop
        }
        super.init(frame: .zero, configuration: config)
        navigationDelegate = self
        allowsBackForwardNavigationGestures = true // atrás = gesto de borde (no hay tecla atrás en iOS)
        if desktop { customUserAgent = desktopUserAgent }
        configuration.userContentController.add(WeakScriptHandler(self), name: "OmniBridge")
        isOpaque = false
        backgroundColor = .black
        scrollView.backgroundColor = .black
        if effectGestures { installCorners() }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) no se usa") }

    /// Esquina inferior derecha: disparo manual. Esquina superior izquierda (pulsación larga): menú oculto.
    /// Son vistas nativas por encima de la web: se tragan el contacto y la página no llega a verlo.
    private func installCorners() {
        for corner in [cornerTap, cornerMenu] {
            corner.backgroundColor = .clear
            corner.isUserInteractionEnabled = true
            addSubview(corner)
        }
        cornerTap.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapCorner)))
        let long = UILongPressGestureRecognizer(target: self, action: #selector(longCorner(_:)))
        long.minimumPressDuration = 0.8
        cornerMenu.addGestureRecognizer(long)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard hasCorners else { return }
        let size: CGFloat = 72
        cornerTap.frame = CGRect(x: bounds.width - size, y: bounds.height - size, width: size, height: size)
        cornerMenu.frame = CGRect(x: 0, y: 0, width: size, height: size)
        bringSubviewToFront(cornerTap)
        bringSubviewToFront(cornerMenu)
    }

    @objc private func tapCorner() { callbacks.onHiddenTap() }

    @objc private func longCorner(_ gesture: UILongPressGestureRecognizer) {
        if gesture.state == .began { callbacks.onHiddenLongPress() }
    }

    /// Motor al empezar cada documento + script de arranque al terminar de montarse el DOM.
    func setStartScript(_ js: String) {
        let controller = configuration.userContentController
        controller.removeAllUserScripts()
        controller.addUserScript(WKUserScript(source: bridgeShim + engineJS, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        if !js.isEmpty {
            controller.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        }
    }

    // MARK: WKScriptMessageHandler: captura entregada por el motor

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "OmniBridge",
              let json = message.body as? String,
              let data = json.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let imageUrl = obj["imageUrl"] as? String else { return }
        let imageData = obj["imageData"] as? String // NSNull si no hay → nil
        let texts = (obj["texts"] as? [String]) ?? []
        let html = obj["html"] as? String
        let pageUrl = (obj["pageUrl"] as? String) ?? ""
        let aspect = (obj["aspect"] as? Double) ?? 1.0
        callbacks.onCapture(imageUrl: imageUrl, imageData: imageData, texts: texts, html: html, pageUrl: pageUrl, aspect: aspect)
    }

    // MARK: WKNavigationDelegate: no salir a otras apps

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let scheme = navigationAction.request.url?.scheme?.lowercased() else {
            decisionHandler(.allow)
            return
        }
        decisionHandler(webSchemes.contains(scheme) ? .allow : .cancel)
    }
}

final class OmniWebHost: NSObject, WebViewHost {
    private var volumeObservation: NSKeyValueObservation?

    private lazy var blobDirectory: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("blobs", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }()

    // MARK: Navegador

    func createWebView(desktop: Bool, effectGestures: Bool, callbacks: WebViewCallbacks) -> UIView {
        OmniWebView(desktop: desktop, effectGestures: effectGestures, callbacks: callbacks)
    }

    func loadUrl(view: UIView, url: String) {
        guard let web = view as? OmniWebView, let target = URL(string: url) else { return }
        web.load(URLRequest(url: target))
    }

    func setStartScript(view: UIView, js: String) {
        (view as? OmniWebView)?.setStartScript(js)
    }

    func evaluate(view: UIView, js: String) {
        (view as? OmniWebView)?.evaluateJavaScript(js, completionHandler: nil)
    }

    // MARK: Datos del navegador

    func clearBrowsingData() {
        let since = Date(timeIntervalSince1970: 0)
        WKWebsiteDataStore.default().removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: since) {}
        URLCache.shared.removeAllCachedResponses()
        HTTPCookieStorage.shared.removeCookies(since: since)
    }

    func fetchImage(url: String, referer: String, completion: StringCallback) {
        guard let target = URL(string: url) else {
            completion.invoke(value: nil)
            return
        }
        var request = URLRequest(url: target, timeoutInterval: 15)
        request.setValue(downloadUserAgent, forHTTPHeaderField: "User-Agent")
        if !referer.isEmpty { request.setValue(referer, forHTTPHeaderField: "Referer") }
        URLSession.shared.dataTask(with: request) { data, response, _ in
            var result: String?
            if let data = data, !data.isEmpty, data.count <= 8_000_000,
               let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
                let declared = http.mimeType ?? "image/jpeg"
                let mime = declared.hasPrefix("image/") ? declared : "image/jpeg"
                result = "data:\(mime);base64," + data.base64EncodedString()
            }
            DispatchQueue.main.async { completion.invoke(value: result) }
        }.resume()
    }

    // MARK: Ficheros (imagen e HTML de la tarjeta)

    private func blobURL(_ name: String) -> URL {
        var safe = ""
        for ch in name {
            let ok: Bool = ch.isASCII && (ch.isLetter || ch.isNumber || ch == "." || ch == "-" || ch == "_")
            safe.append(ok ? ch : Character("_"))
        }
        return blobDirectory.appendingPathComponent(safe)
    }

    func readBlob(name: String) -> String? {
        try? String(contentsOf: blobURL(name), encoding: .utf8)
    }

    func writeBlob(name: String, data: String) {
        try? data.write(to: blobURL(name), atomically: true, encoding: .utf8)
    }

    func deleteBlob(name: String) {
        try? FileManager.default.removeItem(at: blobURL(name))
    }

    // MARK: Sistema

    func setImmersive(on: Bool) {
        UIApplication.shared.isIdleTimerDisabled = on
    }

    func setVolumeListener(listener: VoidCallback?) {
        volumeObservation?.invalidate()
        volumeObservation = nil
        guard let listener = listener else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)
        volumeObservation = session.observe(\.outputVolume, options: [.new]) { _, _ in
            DispatchQueue.main.async { listener.invoke() }
        }
    }
}

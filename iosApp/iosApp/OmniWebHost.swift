import AVFoundation
import Combine
import Shared
import SwiftUI
import UIKit
import WebKit

// Navegador de iOS: WKWebView + motor JS compartido (omniweb.js). Implementa el protocolo `WebViewHost` que declara
// Kotlin en shared/src/iosMain/.../IosWebHost.kt. Si cambias una firma allí, cámbiala aquí (las etiquetas salen de los
// nombres de los parámetros de Kotlin).
//
// IMPORTANTE: el WKWebView NO está dentro de Compose. Vive en una capa nativa (SwiftUI, `WebOverlay`) por encima de la
// vista de Compose: Compose reserva el hueco y avisa de su rectángulo (`layoutWebView`). Embebido con UIKitView el scroll
// no llegaba al WKWebView en ninguna web; así los toques van directos al navegador sin pasar por Compose.

private let webSchemes: Set<String> = ["http", "https", "about", "data", "blob", "file", "javascript"]

private let desktopUserAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

private let downloadUserAgent =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

/// El motor llama a `window.OmniBridge.onCapture(json)` (así es en Android); aquí se reenvía al manejador nativo.
private let bridgeShim = """
window.OmniBridge = { onCapture: function (json) { window.webkit.messageHandlers.OmniBridge.postMessage(json); }, onScroll: function (dir) { window.webkit.messageHandlers.OmniBridge.postMessage("scroll:" + dir); }, onForced: function (url) { window.webkit.messageHandlers.OmniBridge.postMessage("forced:" + url); } };
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

// MARK: - Navegador

final class OmniWebView: WKWebView, WKNavigationDelegate, WKScriptMessageHandler {
    let callbacks: WebViewCallbacks
    let effectGestures: Bool
    private var observations: [NSKeyValueObservation] = []

    init(desktop: Bool, effectGestures: Bool, callbacks: WebViewCallbacks) {
        self.callbacks = callbacks
        self.effectGestures = effectGestures
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
        // URL y título para la barra de URL decorativa
        observations.append(observe(\.url, options: [.new]) { [weak self] _, _ in self?.reportPage() })
        observations.append(observe(\.title, options: [.new]) { [weak self] _, _ in self?.reportPage() })
    }

    private func reportPage() {
        callbacks.onPageInfo(url: url?.absoluteString ?? "", title: title ?? "")
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) no se usa") }

    /// Motor al empezar cada documento + script de arranque al terminar de montarse el DOM.
    func setStartScript(_ js: String) {
        let controller = configuration.userContentController
        controller.removeAllUserScripts()
        controller.addUserScript(WKUserScript(source: bridgeShim + engineJS, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        if !js.isEmpty {
            // La configuración del efecto se inyecta al EMPEZAR el documento: la ficha puede ser OTRA página (Amazon) que tarda en
            // terminar de cargar y el motor tiene que estar listo antes. La captura se arma al terminar de montarse el DOM.
            let when: WKUserScriptInjectionTime = js.hasPrefix("OmniWeb.configure") ? .atDocumentStart : .atDocumentEnd
            controller.addUserScript(WKUserScript(source: js, injectionTime: when, forMainFrameOnly: true))
        }
    }

    // MARK: WKScriptMessageHandler: captura entregada por el motor

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "OmniBridge", let text = message.body as? String, text.hasPrefix("forced:") {
            callbacks.onForced(url: String(text.dropFirst(7)))
            return
        }
        if message.name == "OmniBridge", let text = message.body as? String, text.hasPrefix("scroll:") {
            callbacks.onScroll(down: text == "scroll:down")
            return
        }
        guard message.name == "OmniBridge",
              let json = message.body as? String,
              let data = json.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let imageUrl = obj["imageUrl"] as? String else { return }
        let imageData = obj["imageData"] as? String // NSNull si no hay → nil
        let texts = (obj["texts"] as? [String]) ?? []
        let html = obj["html"] as? String
        let detailUrl = obj["detailUrl"] as? String
        let pageUrl = (obj["pageUrl"] as? String) ?? ""
        let aspect = (obj["aspect"] as? Double) ?? 1.0
        callbacks.onCapture(imageUrl: imageUrl, imageData: imageData, texts: texts, html: html, detailUrl: detailUrl, pageUrl: pageUrl, aspect: aspect)
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

// MARK: - Capa nativa (SwiftUI) por encima de Compose

/// Una capa de navegador: 0 = el principal, 1 = la ficha del objeto (encima).
struct WebLayer {
    var web: OmniWebView
    var frame: CGRect
    var hidden: Bool
}

/// Estado que Kotlin actualiza (vía `OmniWebHost`) y que SwiftUI pinta.
final class WebOverlayModel: ObservableObject {
    @Published var layers: [Int: WebLayer] = [:]
}

/// Contenedor a pantalla completa. Deja pasar todos los toques que no caen en un navegador ni en las esquinas ocultas, de modo
/// que Compose (por debajo) sigue recibiendo los suyos. Las esquinas del efecto están AQUÍ, por encima del navegador:
/// dentro del WKWebView el propio WebKit podía taparlas (sobre todo en modo escritorio, que redibuja las capas con el zoom).
final class WebOverlayContainer: UIView {
    private var webs: [Int: OmniWebView] = [:]
    private let cornerTap = UIView()
    private let cornerMenu = UIView()
    private let cornerSize: CGFloat = 72
    private let menuWidth: CGFloat = 180
    private let menuHeight: CGFloat = 64
    private let edgePan = UIScreenEdgePanGestureRecognizer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        for corner in [cornerTap, cornerMenu] {
            corner.backgroundColor = .clear
            corner.isHidden = true
            addSubview(corner)
        }
        cornerTap.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapCorner)))
        let long = UILongPressGestureRecognizer(target: self, action: #selector(longCorner(_:)))
        long.minimumPressDuration = 0.8
        cornerMenu.addGestureRecognizer(long)
        // Arrastre desde el borde izquierdo = cerrar la ficha (como «atrás» en un navegador)
        edgePan.edges = .left
        edgePan.addTarget(self, action: #selector(edgeBack(_:)))
        edgePan.isEnabled = false
        addGestureRecognizer(edgePan)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) no se usa") }

    func update(layers: [Int: WebLayer]) {
        // Quitar las capas que ya no existen (o cuya vista cambió)
        for (key, web) in webs where layers[key]?.web !== web {
            web.removeFromSuperview()
            webs[key] = nil
        }
        // Añadir y colocar por orden: la capa mayor queda encima
        for key in layers.keys.sorted() {
            guard let layer = layers[key] else { continue }
            if webs[key] == nil {
                webs[key] = layer.web
                addSubview(layer.web)
            }
            layer.web.frame = layer.frame
            layer.web.isHidden = layer.hidden
            bringSubviewToFront(layer.web)
        }
        edgePan.isEnabled = layers[1] != nil
        // Esquinas: solo las del navegador principal y solo cuando no hay ficha encima
        if let base = layers[0], base.web.effectGestures, !base.hidden, layers[1] == nil {
            let target = base.frame
            cornerTap.isHidden = false
            cornerMenu.isHidden = false
            cornerTap.frame = CGRect(x: target.maxX - cornerSize, y: target.maxY - cornerSize, width: cornerSize, height: cornerSize)
            // Menú oculto: arriba en el CENTRO, ancho y alto para que sea fácil de acertar
            cornerMenu.frame = CGRect(x: target.midX - menuWidth / 2, y: target.minY, width: menuWidth, height: menuHeight)
        } else {
            cornerTap.isHidden = true
            cornerMenu.isHidden = true
        }
        bringSubviewToFront(cornerTap)
        bringSubviewToFront(cornerMenu)
    }

    @objc private func tapCorner() { webs[0]?.callbacks.onHiddenTap() }

    @objc private func longCorner(_ gesture: UILongPressGestureRecognizer) {
        if gesture.state == .began { webs[0]?.callbacks.onHiddenLongPress() }
    }

    @objc private func edgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        // Si la ficha tiene historial propio, el gesto de borde del WKWebView ya navega hacia atrás: no se cierra la capa
        if gesture.state == .ended, let detail = webs[1], !detail.canGoBack { detail.callbacks.onBack() }
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        return hit === self ? nil : hit
    }
}

struct WebOverlay: UIViewRepresentable {
    @ObservedObject var model: WebOverlayModel

    func makeUIView(context: Context) -> WebOverlayContainer { WebOverlayContainer(frame: .zero) }

    func updateUIView(_ container: WebOverlayContainer, context: Context) {
        container.update(layers: model.layers)
    }
}

// MARK: - Host (lo que Kotlin llama)

final class OmniWebHost: NSObject, WebViewHost {
    static let shared = OmniWebHost()
    let overlay = WebOverlayModel()
    private var volumeObservation: NSKeyValueObservation?

    private lazy var blobDirectory: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("blobs", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }()

    // MARK: Navegador

    func createWebView(desktop: Bool, effectGestures: Bool, layer: Int32, callbacks: WebViewCallbacks) -> UIView {
        let created = OmniWebView(desktop: desktop, effectGestures: effectGestures, callbacks: callbacks)
        overlay.layers[Int(layer)] = WebLayer(web: created, frame: .zero, hidden: false)
        return created
    }

    func layoutWebView(view: UIView, x: Double, y: Double, width: Double, height: Double, hidden: Bool) {
        // Solo los navegadores vigentes (al cambiar de pantalla puede llegar un aviso tardío del anterior)
        guard let web = view as? OmniWebView,
              let key = overlay.layers.first(where: { $0.value.web === web })?.key else { return }
        overlay.layers[key]?.frame = CGRect(x: x, y: y, width: width, height: height)
        overlay.layers[key]?.hidden = hidden
    }

    func destroyWebView(view: UIView) {
        guard let web = view as? OmniWebView,
              let key = overlay.layers.first(where: { $0.value.web === web })?.key else { return }
        overlay.layers[key] = nil
    }

    func loadUrl(view: UIView, url: String) {
        guard let web = view as? OmniWebView, let target = URL(string: url) else { return }
        web.load(URLRequest(url: target))
    }

    func goBack(view: UIView) {
        if let web = view as? OmniWebView, web.canGoBack { web.goBack() }
    }

    func goForward(view: UIView) {
        if let web = view as? OmniWebView, web.canGoForward { web.goForward() }
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

    func vibrate(armed: Bool) {
        if armed {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } else {
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.impactOccurred()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { generator.impactOccurred() }
        }
    }

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

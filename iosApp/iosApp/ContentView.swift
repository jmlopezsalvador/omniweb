import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(host: OmniWebHost.shared)
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}

/// Oculta el indicador de inicio (la barra de abajo) para el modo inmersivo; iOS 16 o posterior.
struct HideSystemOverlays: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.persistentSystemOverlays(.hidden)
        } else {
            content
        }
    }
}

struct ContentView: View {
    var body: some View {
        // Compose abajo; encima, la capa nativa del navegador (los toques del navegador no pasan por Compose)
        ZStack {
            ComposeView()
                .ignoresSafeArea(.all)
            WebOverlay(model: OmniWebHost.shared.overlay)
                .ignoresSafeArea(.all)
        }
        .background(Color.black)
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
        .modifier(HideSystemOverlays())
    }
}

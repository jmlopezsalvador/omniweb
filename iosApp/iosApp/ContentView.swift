import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(host: OmniWebHost.shared)
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
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
        .statusBarHidden(false) // se ve la hora y la batería, como en un navegador
    }
}

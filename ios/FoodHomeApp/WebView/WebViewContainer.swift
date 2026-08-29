import SwiftUI
import WebKit

struct WebViewContainer: UIViewRepresentable {
    @ObservedObject var store: WebViewStore

    func makeUIView(context: Context) -> WKWebView {
        store.startIfNeeded()
        return store.webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}
}

import SwiftUI
import WebKit

@MainActor
struct BilibiliLoginView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var capture = UUID()
    @State private var message: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text("在 B 站页面登录后，点“完成登录”。登录状态保存在这台 iPhone 的钥匙串中。")
                    .font(.footnote).foregroundStyle(.secondary).padding()
                if let message { Text(message).font(.footnote).foregroundStyle(.red).padding(.horizontal) }
                LoginWebView(capture: capture) { cookie in
                    guard !cookie.isEmpty else { message = "还没有检测到登录状态，请先完成网页登录。"; return }
                    Task { await model.login(cookie: cookie) }
                }
            }
            .navigationTitle("登录 B 站").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(model.accountLoading ? "验证中…" : "完成登录") { capture = UUID() }
                        .disabled(model.accountLoading)
                }
            }
            .alert("登录提示", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
                Button("知道了", role: .cancel) { model.error = nil }
            } message: { Text(model.error ?? "") }
        }
    }
}

private struct LoginWebView: UIViewRepresentable {
    let capture: UUID
    let onCookie: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(capture: capture, onCookie: onCookie) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: URL(string: "https://passport.bilibili.com/login")!))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard context.coordinator.capture != capture else { return }
        context.coordinator.capture = capture
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
            let valid = cookies.filter {
                let domain = $0.domain.trimmingCharacters(in: CharacterSet(charactersIn: "."))
                return (domain == "bilibili.com" || domain.hasSuffix(".bilibili.com")) && ($0.expiresDate ?? .distantFuture) > Date()
            }
            guard valid.contains(where: { $0.name == "SESSDATA" && !$0.value.isEmpty }) else {
                onCookie(""); return
            }
            var pairs: [String: String] = [:]
            valid.forEach { pairs[$0.name] = $0.value }
            onCookie(pairs.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "; "))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var capture: UUID
        let onCookie: (String) -> Void
        init(capture: UUID, onCookie: @escaping (String) -> Void) { self.capture = capture; self.onCookie = onCookie }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            // Keep login and captcha in the browser; do not launch app/deep-link schemes.
            decisionHandler(navigationAction.request.url?.scheme == "https" ? .allow : .cancel)
        }
    }
}

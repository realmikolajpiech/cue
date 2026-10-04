import UIKit
import WebKit

final class CueMessengerLogin: UIViewController, WKNavigationDelegate {
  private let browser = WKWebView(frame: .zero, configuration: {
    let config = WKWebViewConfiguration(); config.websiteDataStore = .nonPersistent(); return config
  }())
  private let connect: (String) async throws -> Void
  private var connecting = false
  init(connect: @escaping (String) async throws -> Void) { self.connect = connect; super.init(nibName: nil, bundle: nil) }
  required init?(coder: NSCoder) { fatalError("init(coder:) unavailable") }
  override func viewDidLoad() {
    super.viewDidLoad(); title = "Połącz Messengera"; view.backgroundColor = .systemBackground
    navigationItem.leftBarButtonItem = UIBarButtonItem(systemItem: .cancel, primaryAction: UIAction { [weak self] _ in self?.dismiss(animated: true) })
    navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Połącz", primaryAction: UIAction { [weak self] _ in self?.capture() })
    browser.navigationDelegate = self; browser.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(browser)
    NSLayoutConstraint.activate([browser.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor), browser.bottomAnchor.constraint(equalTo: view.bottomAnchor), browser.leadingAnchor.constraint(equalTo: view.leadingAnchor), browser.trailingAnchor.constraint(equalTo: view.trailingAnchor)])
    browser.load(URLRequest(url: URL(string: "https://www.facebook.com/login/")!))
  }
  private func capture() {
    guard !connecting else { return }
    browser.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
      guard let self else { return }
      let cookies = cookies.filter { let host = $0.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")); return host == "facebook.com" || host.hasSuffix(".facebook.com") || host == "messenger.com" || host.hasSuffix(".messenger.com") }
      let values = cookies.reduce(into: [String: String]()) { result, cookie in result[cookie.name] = cookie.value }
      guard values["c_user"] != nil, values["xs"] != nil else { self.show("Dokończ logowanie do Facebooka, a potem stuknij Połącz."); return }
      self.connecting = true; self.navigationItem.rightBarButtonItem?.isEnabled = false
      Task { @MainActor in
        do { try await self.connect(CueJSON.encode(values)); self.dismiss(animated: true) }
        catch { self.connecting = false; self.navigationItem.rightBarButtonItem?.isEnabled = true; self.show("Nie udało się połączyć Messengera. Spróbuj ponownie.") }
      }
    }
  }
  private func show(_ message: String) {
    let alert = UIAlertController(title: "Messenger", message: message, preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
  }
  func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
    guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
    if url.scheme == "https" { decisionHandler(.allow) }
    else { decisionHandler(.cancel) }
  }
  func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
    if (error as NSError).code != NSURLErrorCancelled { show("Nie udało się otworzyć strony. Sprawdź internet i spróbuj ponownie.") }
  }
}

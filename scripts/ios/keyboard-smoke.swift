// Runs the real keyboard controller with a fake document inside a disposable simulator app.
// No messages are sent, and no user's host-app document is modified.
import UIKit

final class Document: NSObject, UITextDocumentProxy {
  var text = ""
  let documentIdentifier = UUID()
  var documentContextBeforeInput: String? { text }
  var documentContextAfterInput: String? { "" }
  var selectedText: String? { nil }
  var documentInputMode: UITextInputMode? { nil }
  var hasText: Bool { !text.isEmpty }
  var keyboardType: UIKeyboardType = .default
  var autocapitalizationType: UITextAutocapitalizationType = .sentences
  func insertText(_ text: String) { self.text += text }
  func deleteBackward() { if !text.isEmpty { text.removeLast() } }
  func adjustTextPosition(byCharacterOffset offset: Int) {}
  func setMarkedText(_ markedText: String, selectedRange: NSRange) {}
  func unmarkText() {}
}
final class Keyboard: CueKeyboardViewController {
  let document = Document()
  var access = true
  override var textDocumentProxy: UITextDocumentProxy { document }
  override var hasFullAccess: Bool { access }
}
@main final class App: UIResponder, UIApplicationDelegate {
  func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool { true }
}
final class Scene: UIResponder, UIWindowSceneDelegate {
  var window: UIWindow?
  func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
    guard let scene = scene as? UIWindowScene else { return }
    do { let store = try CueStore.shared()
      _ = try store.update { $0["keyboardRoom"] = NSNull() }
      for (id, name) in [("m", "Marta · test"), ("a", "Anna · test")] { try store.upsert(["id": id, "name": name, "network": "messenger", "updatedAt": 1, "messages": [], "profile": NSNull()]) }
    } catch { print("CUE_FIXTURE_ERROR " + error.localizedDescription) }
    let root = UIViewController(); root.view.backgroundColor = .systemBackground
    let window = UIWindow(windowScene: scene); window.rootViewController = root; self.window = window; window.makeKeyAndVisible()
    let keyboard = Keyboard(); root.addChild(keyboard); root.view.addSubview(keyboard.view); keyboard.didMove(toParent: root)
    keyboard.view.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([keyboard.view.leadingAnchor.constraint(equalTo: root.view.leadingAnchor), keyboard.view.trailingAnchor.constraint(equalTo: root.view.trailingAnchor), keyboard.view.bottomAnchor.constraint(equalTo: root.view.safeAreaLayoutGuide.bottomAnchor)])
    let label = UILabel(); label.text = "Cue · Keyboard integration test"; label.textAlignment = .center; label.font = .systemFont(ofSize: 18, weight: .semibold); label.numberOfLines = 3; label.translatesAutoresizingMaskIntoConstraints = false; root.view.addSubview(label)
    NSLayoutConstraint.activate([label.topAnchor.constraint(equalTo: root.view.safeAreaLayoutGuide.topAnchor, constant: 28), label.leadingAnchor.constraint(equalTo: root.view.leadingAnchor, constant: 20), label.trailingAnchor.constraint(equalTo: root.view.trailingAnchor, constant: -20)])
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self.test(keyboard, label) }
  }
  private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
  private func tap(_ title: String, _ keyboard: Keyboard) throws {
    guard let button = descendants(keyboard.view).compactMap({ $0 as? UIButton }).first(where: { $0.title(for: .normal) == title }) else { throw CueError("Missing key: " + title) }
    button.sendActions(for: .touchUpInside); keyboard.view.layoutIfNeeded()
  }
  private func test(_ keyboard: Keyboard, _ label: UILabel) {
    var checks: [String] = []
    do {
      try tap("A", keyboard); try tap("b", keyboard); try tap("spacja", keyboard); try tap("c", keyboard)
      guard keyboard.document.text == "Ab c" else { throw CueError("Typing failed: " + keyboard.document.text) }; checks.append("typing")
      try tap("⌫", keyboard); guard keyboard.document.text == "Ab " else { throw CueError("Delete failed") }; checks.append("delete")
      try tap("123", keyboard); try tap("1", keyboard); try tap("ABC", keyboard); guard keyboard.document.text == "Ab 1" else { throw CueError("Symbol mode failed") }; checks.append("symbols")
      keyboard.document.text = "hello"; try tap("spacja", keyboard); try tap("spacja", keyboard)
      guard keyboard.document.text == "hello. " else { throw CueError("Double space failed") }; checks.append("double-space")
      let keyCount = descendants(keyboard.view).compactMap { $0 as? UIButton }.filter { $0.title(for: .normal)?.count == 1 }.count
      try tap("Cue · Wybierz rozmowę ⌄", keyboard)
      let keyCountAfter = descendants(keyboard.view).compactMap { $0 as? UIButton }.filter { $0.title(for: .normal)?.count == 1 }.count
      guard keyCountAfter >= keyCount else { throw CueError("Cue hid typing keys") }; checks.append("keys remain visible")
      try tap("m", keyboard)
      guard keyboard.document.text == "hello. " else { throw CueError("Search typed into host document") }
      guard let table = descendants(keyboard.view).compactMap({ $0 as? UITableView }).first, keyboard.tableView(table, numberOfRowsInSection: 0) == 1 else { throw CueError("Person filtering failed") }
      keyboard.tableView(table, didSelectRowAt: IndexPath(row: 0, section: 0)); checks.append("local person search")
      let snapshot = keyboard.currentSnapshot()
      keyboard.showReplies([["action": "reply", "text": "dzięki 👋🏽", "tone": "Naturalnie"]], snapshot)
      try tap("Wstaw przy kursorze", keyboard)
      guard keyboard.document.text == "hello. dzięki 👋🏽" else { throw CueError("Reply insert failed") }; checks.append("reply insertion")
      try tap("Wstawiono · Cofnij", keyboard)
      guard keyboard.document.text == "hello. " else { throw CueError("Undo failed") }; checks.append("undo")
      keyboard.showReplies([["action": "reply", "text": "stale", "tone": "Naturalnie"]], snapshot)
      keyboard.document.text = "edited"; try tap("Wstaw przy kursorze", keyboard)
      guard keyboard.document.text == "edited" else { throw CueError("Stale reply overwrote draft") }; checks.append("changed draft rejected")
      keyboard.showReplies([["action": "no_reply", "text": "", "reason": "Poczekaj na odpowiedź", "tone": "Nie odpisuj"]], keyboard.currentSnapshot())
      try tap("Zostaw bez odpowiedzi", keyboard)
      guard keyboard.document.text == "edited" else { throw CueError("No-reply altered draft") }; checks.append("no reply")
      keyboard.showReplies([["action": "reply", "text": "jasne, daj znać jak będziesz miała chwilę 🙂", "tone": "Naturalnie"]], keyboard.currentSnapshot())
      keyboard.access = false
      label.text = "Passed: " + checks.joined(separator: ", ")
      let result = try CueJSON.encode(["finishedAt": CueJSON.now, "passed": checks, "document": keyboard.document.text, "keyboardHeight": keyboard.view.bounds.height])
      let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
      try Data(result.utf8).write(to: directory.appendingPathComponent("smoke.json"))
      print("CUE_KEYBOARD_SMOKE " + result)
    } catch {
      label.text = error.localizedDescription; print("CUE_KEYBOARD_SMOKE_FAILED " + error.localizedDescription)
      let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
      try? Data((try? CueJSON.encode(["finishedAt": CueJSON.now, "failure": error.localizedDescription]))!.utf8).write(to: directory.appendingPathComponent("smoke.json"))
    }
  }
}

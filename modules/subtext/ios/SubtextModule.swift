import ExpoModulesCore
import UIKit

public final class SubtextModule: Module {
  private func runtime() throws -> CueRuntime { try CueRuntime.shared.get() }
  public func definition() -> ModuleDefinition {
    Name("Subtext")
    Events("onChanged")
    OnCreate { [weak self] in
      guard let self, let runtime = try? self.runtime() else { return }
      runtime.changed = { [weak self] in self?.sendEvent("onChanged", [:]) }
    }
    OnDestroy { [weak self] in (try? self?.runtime())?.changed = nil }
    OnAppEntersForeground { [weak self] in
      guard let runtime = try? self?.runtime() else { return }
      Task { try? await runtime.messaging.background { try runtime.messaging.restore(); try runtime.messaging.refresh() } }
    }
    AsyncFunction("status") { () throws -> String in try self.runtime().status() }
    AsyncFunction("loadDemo") { () throws -> String in try self.runtime().demo() }
    AsyncFunction("setDemoStage") { (stage: Int) throws -> String in try self.runtime().demoStage(stage) }
    AsyncFunction("setConversationAI") { (id: String, enabled: Bool) throws in try self.runtime().conversationAI(id, enabled) }
    AsyncFunction("conversations") { () throws -> String in try self.runtime().summaries() }
    AsyncFunction("conversation") { (id: String) throws -> String in try CueJSON.encode(self.runtime().store.room(id)) }
    AsyncFunction("syncConversation") { (id: String) async throws -> String in
      let runtime = try self.runtime()
      return try await runtime.messaging.background { try CueJSON.encode(runtime.messaging.sync(id)) }
    }
    AsyncFunction("conversationImage") { (id: String, message: String) async throws -> String in
      let runtime = try self.runtime(); return try await runtime.messaging.background { try runtime.messaging.image(id, message) }
    }
    AsyncFunction("refresh") { () async throws in
      let runtime = try self.runtime(); try await runtime.messaging.background { try runtime.messaging.restore(); try runtime.messaging.refresh() }
    }
    AsyncFunction("analyze") { (id: String, draft: String) async throws -> String in try await self.runtime().analyze(id, draft) }
    AsyncFunction("setCloudEnabled") { (enabled: Bool) throws in try self.runtime().cloud(enabled) }
    AsyncFunction("clearHistory") { () throws in let runtime = try self.runtime(); try runtime.store.clear(); runtime.changed?() }
    AsyncFunction("disconnect") { (network: String) async throws in
      guard ["messenger", "whatsapp"].contains(network) else { throw CueError("Nieznany komunikator.") }
      let runtime = try self.runtime(); try await runtime.messaging.background { try runtime.messaging.disconnect(network) }
    }
    AsyncFunction("pairWhatsApp") { (phone: String) async throws -> String in try await self.runtime().messaging.pair(phone) }
    AsyncFunction("connectMessenger") { () throws in
      guard let presenter = self.appContext?.utilities?.currentViewController() else { throw CueError("Nie można otworzyć logowania.") }
      let runtime = try self.runtime()
      let login = CueMessengerLogin { cookies in try await runtime.messaging.background { try runtime.messaging.connectMessenger(cookies) } }
      presenter.present(UINavigationController(rootViewController: login), animated: true)
    }.runOnQueue(.main)
    AsyncFunction("openKeyboardSettings") { self.keyboardSettings() }.runOnQueue(.main)
    Function("selectKeyboard") { self.keyboardInstructions() }
    AsyncFunction("writingStyle") { () throws -> String in try CueJSON.encode(CueMemory.style(store: self.runtime().store, id: nil)) }
    AsyncFunction("conversationWritingStyle") { (id: String) throws -> String in
      let store = try self.runtime().store; _ = try store.room(id); return try CueJSON.encode(CueMemory.style(store: store, id: id))
    }
    AsyncFunction("previewWritingStyle") { () async throws -> String in try await self.runtime().previewStyle(nil) }
    AsyncFunction("previewConversationWritingStyle") { (id: String) async throws -> String in try await self.runtime().previewStyle(id) }
    AsyncFunction("setWritingTone") { (id: String?, tone: String) throws -> String in try self.runtime().tone(id, tone) }
    AsyncFunction("conversationMemory") { (id: String) throws -> String in
      let store = try self.runtime().store; _ = try store.room(id); let memory = try store.memory(id)
      return try CueJSON.encode(["conversationId": id, "storedMemory": memory, "aiMemory": ["relationship": memory["relationship"] ?? [], "reminders": memory["reminders"] ?? []]])
    }
    AsyncFunction("conversationReminders") { (id: String) throws -> String in
      let store = try self.runtime().store; _ = try store.room(id); return try CueJSON.encode(CueMemory.reminders(store.memory(id)))
    }
    AsyncFunction("refreshConversationReminders") { (id: String) async throws -> String in
      let runtime = try self.runtime(); _ = try await runtime.analyze(id, memoryOnly: true)
      return try CueJSON.encode(CueMemory.reminders(runtime.store.memory(id)))
    }
    AsyncFunction("editConversationReminder") { (id: String, reminder: String, patch: String) throws in try self.runtime().editReminder(id, reminder, patch) }
  }
  private func keyboardSettings() {
    UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
  }
  private func keyboardInstructions() {
    DispatchQueue.main.async { [weak self] in
      guard let presenter = self?.appContext?.utilities?.currentViewController() else { return }
      let alert = UIAlertController(title: "Klawiatura Cue", message: "Ustawienia → Ogólne → Klawiatura → Klawiatury → Dodaj klawiaturę → Cue.\n\nWłącz pełny dostęp dla podpowiedzi AI. Podczas pisania przytrzymaj 🌐 i wybierz Cue.", preferredStyle: .alert)
      alert.addAction(UIAlertAction(title: "OK", style: .default)); presenter.present(alert, animated: true)
    }
  }
}

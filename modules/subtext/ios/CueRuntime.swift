import Foundation

final class CueRuntime {
  static let shared: Result<CueRuntime, Error> = Result { try CueRuntime() }
  let store: CueStore
  let messaging: CueMessaging
  var changed: (() -> Void)?
  private let lock = NSLock()
  private var analyzing: String?
  private init() throws {
    store = try CueStore.shared(); messaging = try CueMessaging(store: store)
    messaging.changed = { [weak self] in self?.changed?() }
  }
  func status() throws -> String {
    let state = try store.read(), connections = messaging.status()
    lock.lock(); let active = analyzing; lock.unlock()
    return try CueJSON.encode(["available": true, "hasApiKey": true, "cloudEnabled": state["cloud"] as? Bool ?? false,
      "backgroundEnabled": false, "model": "deepseek-flash", "analyzing": active.map { $0 as Any } ?? NSNull(),
      "messenger": connections["messenger"] ?? [:], "whatsapp": connections["whatsapp"] ?? [:]])
  }
  func cloud(_ enabled: Bool) throws {
    try store.update { state in state["cloud"] = enabled; state["epoch"] = (state["epoch"] as? Int ?? 0) + 1 }
    changed?()
  }
  func analyze(_ id: String, _ draft: String = "", memoryOnly: Bool = false) async throws -> String {
    try startAnalysis(id)
    defer { finishAnalysis() }
    if (try store.room(id)["demo"] as? Bool) != true { _ = try await messaging.background { try self.messaging.sync(id) } }
    let profile = try await CueAI.shared.analyze(store: store, id: id, draft: draft, memoryOnly: memoryOnly)
    changed?(); return try CueJSON.encode(profile)
  }
  private func startAnalysis(_ id: String) throws {
    lock.lock(); defer { lock.unlock() }
    guard analyzing == nil else { throw CueError("Cue już analizuje rozmowę. Poczekaj chwilę.") }
    analyzing = id; changed?()
  }
  private func finishAnalysis() { lock.lock(); analyzing = nil; lock.unlock(); changed?() }
  func summaries() throws -> String {
    try CueJSON.encode(store.rooms().map { room in var result = room; result.removeValue(forKey: "messages"); result["messageCount"] = (room["messages"] as? [Any])?.count ?? 0; return result })
  }
  func demo() throws -> String {
    let id = "messenger:subtext-demo"
    if (try? store.room(id)["demoStage"]) == nil { return try demoStage(0) }
    return id
  }
  func demoStage(_ stage: Int) throws -> String {
    guard (0...3).contains(stage) else { throw CueError("Nieprawidłowy krok demo.") }
    let id = "messenger:subtext-demo", previous = try? store.room(id)
    guard stage == 0 || (previous?["demoStage"] as? Int ?? -1) + 1 == stage else { throw CueError("Otwórz kolejne kroki po kolei.") }
    if stage == 0 { try store.removeRoom(id) }
    let base = stage == 0 ? CueJSON.now : CueJSON.number(previous?["demoBase"])
    let texts = ["Dzięki za bilet! Wiszę Ci 50 zł. Oddam jutro.", "spoko, dzięki za wspólny wieczór :)", "Było super, musimy to powtórzyć!", "no pewnie, daj znać jak będziesz miała czas", "Jednak przeleję pojutrze, pasuje?", "jasne, bez pośpiechu", "Przelałam teraz 20 zł z tych 50 zł. Resztę oddam pojutrze.", "mam te 20, dzięki!", "Wysłałam też pozostałe 30 zł.", "dotarło, jesteśmy rozliczeni :)"]
    let messages = Array(texts.prefix([4, 6, 8, 10][stage])).enumerated().map { index, text -> [String: Any] in
      ["id": "cue-demo-\(index + 1)", "sender": index % 2 == 0 ? "Marta" : "Ty", "text": text, "timestamp": base - 600000 + Double(index) * 30000, "isMe": index % 2 == 1]
    }
    var value: [String: Any] = ["id": id, "remoteId": "subtext-demo", "network": "messenger", "name": "Marta · demo", "kind": "PRIVATE", "demo": true, "demoStage": stage, "demoBase": base, "updatedAt": messages.last?["timestamp"] ?? base, "snippet": messages.last?["text"] ?? "", "messages": messages]
    if stage == 0 { value["profile"] = NSNull() }
    try store.upsert(value); changed?(); return id
  }
  func conversationAI(_ id: String, _ enabled: Bool) throws {
    _ = try store.room(id)
    try store.update { state in
      var rooms = state["rooms"] as? [[String: Any]] ?? []
      if let index = rooms.firstIndex(where: { $0["id"] as? String == id }) { rooms[index]["aiExcluded"] = !enabled }
      state["rooms"] = rooms
      var memories = state["memories"] as? [String: Any] ?? [:]; memories.removeValue(forKey: "__general__"); state["memories"] = memories
      state["epoch"] = (state["epoch"] as? Int ?? 0) + 1
    }
    changed?()
  }
  func tone(_ id: String?, _ tone: String) throws -> String {
    guard ["natural", "flirt", "assertive", "empathetic", "calming"].contains(tone) else { throw CueError("Nieznany ton.") }
    try store.update { state in
      if let id { var tones = state["tones"] as? [String: String] ?? [:]; tones[id] = tone; state["tones"] = tones }
      else { state["tone"] = tone }
      state["epoch"] = (state["epoch"] as? Int ?? 0) + 1
    }
    changed?(); return try CueJSON.encode(CueMemory.style(store: store, id: id))
  }
  func previewStyle(_ id: String?) async throws -> String {
    guard let target = try id ?? store.rooms().filter({ $0["demo"] as? Bool != true && !($0["messages"] as? [Any] ?? []).isEmpty }).first?["id"] as? String else { throw CueError("Najpierw odśwież wiadomości z rozmowy.") }
    _ = try await analyze(target, memoryOnly: true)
    if id == nil {
      let memory = try store.memory(target)
      try store.update { state in var memories = state["memories"] as? [String: [String: Any]] ?? [:]; memories["__general__"] = memory; state["memories"] = memories }
    }
    return try CueJSON.encode(CueMemory.style(store: store, id: id))
  }
  func editReminder(_ id: String, _ reminder: String, _ patchJSON: String) throws {
    let patch = try CueJSON.object(patchJSON)
    _ = try store.room(id)
    try store.update { state in
      var memories = state["memories"] as? [String: [String: Any]] ?? [:], memory = memories[id] ?? [:]
      var reminders = memory["reminders"] as? [[String: Any]] ?? []
      guard let index = reminders.firstIndex(where: { $0["id"] as? String == reminder }) else { throw CueError("Nie znaleziono ustalenia.") }
      if patch["delete"] as? Bool == true { reminders.remove(at: index) }
      else {
        if let text = patch["text"] as? String { guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw CueError("Wpisz treść ustalenia.") }; reminders[index]["text"] = String(text.prefix(400)) }
        if let date = patch["dueDate"] as? String { guard date.isEmpty || CueMemory.due(date) != nil else { throw CueError("Wpisz datę RRRR-MM-DD.") }; reminders[index]["dueDate"] = date }
        if let status = patch["status"] as? String { guard ["open", "tentative", "done", "cancelled"].contains(status) else { throw CueError("Nieprawidłowy status.") }; reminders[index]["status"] = status }
        reminders[index]["updatedAt"] = CueJSON.now; reminders[index]["manualAt"] = CueJSON.now
      }
      memory["reminders"] = reminders; memories[id] = memory; state["memories"] = memories
      state["epoch"] = (state["epoch"] as? Int ?? 0) + 1
    }
    changed?()
  }
}

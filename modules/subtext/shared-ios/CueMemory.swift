import Foundation

enum CueMemory {
  static func due(_ text: String) -> Double? {
    let iso = ISO8601DateFormatter()
    if let date = iso.date(from: text) { return date.timeIntervalSince1970 * 1000 }
    let date = DateFormatter(); date.locale = Locale(identifier: "en_US_POSIX"); date.dateFormat = "yyyy-MM-dd"; date.isLenient = false
    guard text.count == 10, let result = date.date(from: text), date.string(from: result) == text else { return nil }
    return result.timeIntervalSince1970 * 1000
  }
  static func reminders(_ memory: [String: Any]) -> [[String: Any]] {
    (memory["reminders"] as? [[String: Any]] ?? []).map { item in
      var item = item
      let timestamp = due(item["dueDate"] as? String ?? "")
      item["dueAt"] = timestamp.map { $0 as Any } ?? NSNull()
      let status = item["status"] as? String ?? "open"
      let kind = item["kind"] as? String ?? "important"
      item["effectiveStatus"] = ["done", "cancelled", "tentative"].contains(status) ? status :
        (timestamp.map { $0 < CueJSON.now - 86_400_000 ? (kind == "meeting" ? "past" : "overdue") : "upcoming" } ?? (kind == "waiting" ? "waiting" : "open"))
      return item
    }
  }
  static func merge(_ memory: [String: Any], raw: [String: Any], messages: [[String: Any]]) -> [String: Any] {
    var memory = memory
    let sources = Dictionary(messages.compactMap { message -> (String, [String: Any])? in guard let id = message["id"] as? String else { return nil }; return (id, message) }, uniquingKeysWith: { _, new in new })
    let ids = Set(messages.compactMap { $0["id"] as? String })
    for (source, destination) in [("memoryUpdates", "relationship"), ("reminderUpdates", "reminders")] {
      var items = memory[destination] as? [[String: Any]] ?? []
      for update in Array((raw[source] as? [[String: Any]] ?? []).prefix(8)) {
        let evidence = (update["evidenceIds"] as? [String] ?? []).filter { ids.contains($0) }
        guard let text = update["text"] as? String, !text.isEmpty, !evidence.isEmpty else { continue }
        let replace = update["replaceId"] as? String ?? ""
        let existing = items.firstIndex { $0["id"] as? String == replace || $0["text"] as? String == text }
        var item = existing.map { items[$0] } ?? ["id": UUID().uuidString, "createdAt": CueJSON.now]
        if destination == "reminders", let manual = item["manualAt"] as? Double, !evidence.contains(where: { CueJSON.number(sources[$0]?["timestamp"]) > manual }) { continue }
        item["sources"] = Array(evidence.prefix(8)).compactMap { sources[$0] }
        item["text"] = String(text.prefix(600)); item["evidenceIds"] = Array(Set(evidence)); item["updatedAt"] = CueJSON.now
        if destination == "reminders" {
          let kind = update["kind"] as? String ?? "important", owner = update["owner"] as? String ?? "both", status = update["status"] as? String ?? "open"
          guard ["meeting", "commitment", "waiting", "important"].contains(kind), ["me", "other", "both"].contains(owner), ["open", "tentative", "done", "cancelled"].contains(status) else { continue }
          let date = update["dueDate"] as? String ?? ""
          guard date.isEmpty || due(date) != nil else { continue }
          item.merge(["kind": kind, "owner": owner, "status": status, "dueDate": date]) { _, new in new }
        }
        if let existing { items[existing] = item } else { items.append(item) }
      }
      memory[destination] = Array(items.suffix(100))
    }
    if let style = raw["writingStyle"] as? [String: Any] { memory["writingStyle"] = style }
    memory["updatedAt"] = CueJSON.now
    return memory
  }
  static func style(store: CueStore, id: String?) throws -> [String: Any] {
    let rooms = try store.rooms().filter { id == nil ? $0["demo"] as? Bool != true && $0["aiExcluded"] as? Bool != true : $0["id"] as? String == id }
    let memory = try store.memory(id ?? "__general__")
    let style = memory["writingStyle"] as? [String: Any] ?? [:]
    var examples: [[String: Any]] = []
    for room in rooms {
      let messages = room["messages"] as? [[String: Any]] ?? []
      for (index, message) in messages.enumerated() where message["isMe"] as? Bool == true && CueAI.usable(message["text"] as? String ?? "") {
        let incoming = index > 0 && messages[index - 1]["isMe"] as? Bool != true ? messages[index - 1]["text"] as? String ?? "" : ""
        examples.append(["id": message["id"] ?? "", "incoming": incoming, "reply": message["text"] ?? "", "timestamp": message["timestamp"] ?? 0])
      }
    }
    return ["selectedTone": try store.tone(id), "sampleCount": examples.count, "conversationCount": rooms.count,
      "summary": style[id == nil ? "general" : "conversation"] as? String ?? style["summary"] as? String ?? "",
      "habits": style["habits"] as? [String] ?? [], "examples": Array(examples.suffix(20)), "generated": !style.isEmpty,
      "relationship": memory["relationship"] ?? [], "reminders": reminders(memory), "updatedAt": memory["updatedAt"] ?? 0]
  }
}

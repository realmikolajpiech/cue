import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

actor CueAI {
  static let shared = CueAI()
  private let base = "https://qajdybynwafehizuaxad.supabase.co"
  private let publicKey = "sb_publishable_25tSEu3C4wIMY7Q5e0HqAg_ZPeFV_a8"
  private func request(_ path: String, body: [String: Any], token: String? = nil) async throws -> [String: Any] {
    var request = URLRequest(url: URL(string: base + path)!)
    request.httpMethod = "POST"; request.timeoutInterval = 70
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.setValue(publicKey, forHTTPHeaderField: "apikey")
    if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
    request.httpBody = try JSONSerialization.data(withJSONObject: body)
    let (data, response) = try await URLSession.shared.data(for: request)
    guard let response = response as? HTTPURLResponse else { throw CueError("Brak odpowiedzi AI.") }
    if response.statusCode == 401 { throw CueError("AUTH_EXPIRED") }
    guard (200...299).contains(response.statusCode) else { throw CueError("AI jest teraz niedostępne (\(response.statusCode)). Spróbuj ponownie.") }
    return try CueJSON.object(String(decoding: data, as: UTF8.self))
  }
  private func session(refresh: Bool = false) async throws -> String {
    #if canImport(Security)
    let saved = CueSecrets.get("supabase-session").flatMap { try? CueJSON.object($0) }
    if !refresh, let token = saved?["access_token"] as? String, (saved?["expires_at"] as? Double ?? 0) > Date().timeIntervalSince1970 + 90 { return token }
    let response: [String: Any]
    if let token = saved?["refresh_token"] as? String {
      do { response = try await request("/auth/v1/token?grant_type=refresh_token", body: ["refresh_token": token]) }
      catch { response = try await request("/auth/v1/signup", body: [:]) }
    } else { response = try await request("/auth/v1/signup", body: [:]) }
    guard let token = response["access_token"] as? String else { throw CueError("Nie udało się połączyć z AI.") }
    try CueSecrets.set("supabase-session", CueJSON.encode(response))
    return token
    #else
    throw CueError("AI wymaga urządzenia iOS.")
    #endif
  }
  func analyze(store: CueStore, id: String, draft: String, memoryOnly: Bool = false) async throws -> [String: Any] {
    let state = try store.read()
    guard state["cloud"] as? Bool == true else { throw CueError("Włącz AI w ustawieniach Cue.") }
    let room = try store.room(id)
    guard room["aiExcluded"] as? Bool != true else { throw CueError("AI jest wyłączone dla tej rozmowy. Zmień ustawienie w jej profilu.") }
    let messages = Array((room["messages"] as? [[String: Any]] ?? []).suffix(100))
    guard !messages.isEmpty else { throw CueError("Najpierw odśwież wiadomości w Cue.") }
    let fingerprint = try CueJSON.encode(messages)
    let epoch = state["epoch"] as? Int ?? 0
    let tone = try store.tone(id)
    let local = messages.filter { $0["isMe"] as? Bool == true }.compactMap { $0["text"] as? String }.filter(Self.usable)
    let general: [String] = room["demo"] as? Bool == true ? [] : (try store.rooms()).filter { $0["demo"] as? Bool != true && $0["aiExcluded"] as? Bool != true }.flatMap {
      Array(($0["messages"] as? [[String: Any]] ?? []).filter { $0["isMe"] as? Bool == true }.compactMap { $0["text"] as? String }.filter(Self.usable).suffix(10))
    }
    let enough = local.count >= 8 && local.reduce(0, { $0 + $1.count }) >= 160
    let tonePrompt = ["flirt": "Lekko flirtujący ton, tylko gdy pasuje do kontekstu.", "assertive": "Ton asertywny: jasne granice, bez agresji.", "empathetic": "Ton empatyczny, naturalny i ciepły.", "calming": "Spokojny ton, bez eskalowania emocji."][tone] ?? ""
    let memory = try store.memory(id)
    let body: [String: Any] = ["messages": messages, "draft": String((draft + (tonePrompt.isEmpty ? "" : "\nInstrukcja tonu: " + tonePrompt)).prefix(4000)),
      "personMemory": ["relationship": memory["relationship"] ?? [], "reminders": memory["reminders"] ?? []], "memoryOnly": memoryOnly,
      "styleInput": ["conversationExamples": Array(local.suffix(40)), "generalExamples": Array(general.suffix(80)), "activeSource": enough ? "conversation" : (general.isEmpty ? "insufficient" : "general")]]
    let raw: [String: Any]
    do { raw = try await request("/functions/v1/deepseek-analyze", body: body, token: session()) }
    catch let error as CueError where error.message == "AUTH_EXPIRED" {
      raw = try await request("/functions/v1/deepseek-analyze", body: body, token: session(refresh: true))
    }
    try Task.checkCancellation()
    let profile = try Self.validate(raw, messages: messages, draft: draft, memoryOnly: memoryOnly)
    try store.update { latest in
      guard latest["cloud"] as? Bool == true, (latest["epoch"] as? Int ?? 0) == epoch else { throw CueError("Analiza została anulowana.") }
      var rooms = latest["rooms"] as? [[String: Any]] ?? []
      guard let index = rooms.firstIndex(where: { $0["id"] as? String == id }),
        try CueJSON.encode(Array((rooms[index]["messages"] as? [[String: Any]] ?? []).suffix(100))) == fingerprint,
        ((latest["tones"] as? [String: String])?[id] ?? latest["tone"] as? String ?? "natural") == tone else { throw CueError("Rozmowa się zmieniła. Poproś o nową podpowiedź.") }
      rooms[index]["profile"] = profile; latest["rooms"] = rooms
      var memories = latest["memories"] as? [String: [String: Any]] ?? [:]
      memories[id] = CueMemory.merge(memories[id] ?? [:], raw: raw, messages: messages)
      latest["memories"] = memories
    }
    return profile
  }
  static func usable(_ text: String) -> Bool { !text.isEmpty && text.count <= 500 && text.rangeOfCharacter(from: .letters) != nil && !text.contains("https://") && !text.hasPrefix("[Zdjęcie]") }
  static func validate(_ raw: [String: Any], messages: [[String: Any]], draft: String, memoryOnly: Bool) throws -> [String: Any] {
    let ids = Set(messages.compactMap { $0["id"] as? String })
    guard let summary = raw["summary"] as? String, let before = raw["beforeReply"] as? String else { throw CueError("AI zwróciło niepełną odpowiedź.") }
    var profile: [String: Any] = ["summary": String(summary.prefix(1200)), "beforeReply": String(before.prefix(1200)), "replyDraft": draft,
      "createdAt": CueJSON.now, "model": "deepseek-flash", "messageCount": messages.count]
    for field in ["observations", "commitments"] {
      profile[field] = Array((raw[field] as? [[String: Any]] ?? []).prefix(8)).compactMap { item -> [String: Any]? in
        let evidence = (item["evidenceIds"] as? [String] ?? []).filter { ids.contains($0) }
        guard let text = item["text"] as? String, !evidence.isEmpty else { return nil }
        return ["text": String(text.prefix(600)), "evidenceIds": evidence]
      }
    }
    let suggestions = Array((raw["suggestions"] as? [[String: Any]] ?? []).prefix(3)).compactMap { item -> [String: Any]? in
      let action = item["action"] as? String ?? "reply"
      if action == "no_reply", let reason = item["reason"] as? String, !reason.isEmpty { return ["action": action, "tone": "Nie odpisuj", "text": "", "reason": String(reason.prefix(600))] }
      guard action == "reply", let text = item["text"] as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
      return ["action": action, "tone": item["tone"] as? String ?? "Naturalnie", "text": String(text.prefix(2000))]
    }
    guard memoryOnly || !suggestions.isEmpty else { throw CueError("AI nie zwróciło podpowiedzi. Spróbuj ponownie.") }
    profile["suggestions"] = suggestions
    return profile
  }
}

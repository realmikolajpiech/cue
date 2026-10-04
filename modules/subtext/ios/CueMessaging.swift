import Foundation
import Messagebridges

private final class CueBridgeSink: NSObject, FbmessagebridgeEventSinkProtocol, WhatsappbridgeEventSinkProtocol {
  weak var owner: CueMessaging?
  let network: String
  let generation: UUID
  init(_ owner: CueMessaging, _ network: String, _ generation: UUID) { self.owner = owner; self.network = network; self.generation = generation }
  func onEvent(_ eventType: String?, jsonPayload: String?) { owner?.receive(network, generation, eventType ?? "", jsonPayload ?? "{}") }
}

final class CueMessaging {
  let store: CueStore
  var changed: (() -> Void)?
  private let stateQueue = DispatchQueue(label: "cue.messaging.state")
  private let workQueue = DispatchQueue(label: "cue.messaging.work", qos: .userInitiated)
  private var fb: FbmessagebridgeBridge?
  private var wa: WhatsappbridgeBridge?
  private var sinks: [String: CueBridgeSink] = [:]
  private var generations: [String: UUID] = [:]
  private var states: [String: [String: Any]] = [:]
  private var qrReady = false
  private var contacts: [String: String] = [:]
  private var mappings: [String: [String: Any]] = [:]
  private var pending: [String: [[String: Any]]] = [:]
  let directory: URL
  init(store: CueStore) throws {
    self.store = store
    directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("CueSessions")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
    var values = URLResourceValues(); values.isExcludedFromBackup = true
    var url = directory; try url.setResourceValues(values)
    for network in ["messenger", "whatsapp"] {
      let configured = network == "messenger" ? CueSecrets.get("messenger-session") != nil : FileManager.default.fileExists(atPath: directory.appendingPathComponent("whatsapp.db").path)
      states[network] = ["phase": configured ? "DISCONNECTED" : "NOT_CONFIGURED", "detail": configured ? "Otwórz Cue, aby odświeżyć rozmowy." : "", "pairingCode": NSNull()]
    }
  }
  func status() -> [String: [String: Any]] { stateQueue.sync { states } }
  private func phase(_ network: String, _ phase: String, _ detail: String = "", code: String? = nil) {
    states[network] = ["phase": phase, "detail": detail, "pairingCode": code.map { $0 as Any } ?? NSNull()]; changed?()
  }
  func receive(_ network: String, _ generation: UUID, _ type: String, _ payload: String) {
    stateQueue.async { [weak self] in
      guard let self, self.generations[network] == generation else { return }
      do {
        let value = (try? CueJSON.object(payload)) ?? [:]
        switch type {
        case "READY", "CONNECTED":
          if network == "whatsapp", self.wa?.isPaired() != true { return }
          self.phase(network, "CONNECTED", "Połączono na tym urządzeniu.")
          self.workQueue.async { [weak self] in
            guard let self else { return }
            if network == "messenger", let bridge = self.stateQueue.sync(execute: { self.fb }), let session = try? self.bridgeString({ bridge.exportSession($0) }) { try? CueSecrets.set("messenger-session", session) }
            try? self.refresh(network)
          }
        case "QR": self.qrReady = true
        case "PAIRED":
          self.phase(network, "CONNECTING", "Sparowano. Pobieram rozmowy…")
          self.workQueue.async { [weak self] in
            guard let self, let bridge = self.stateQueue.sync(execute: { self.wa }) else { return }
            bridge.disconnect(); try? bridge.connect()
          }
        case "CONTACT":
          let id = Self.string(value["contactId"]), name = value["name"] as? String ?? ""
          if !id.isEmpty && Self.usableName(name) {
            self.contacts[id] = name
            for room in try self.store.rooms() where room["network"] as? String == "messenger" {
              let remote = room["remoteId"] as? String ?? ""
              if Self.string(self.mappings[remote]?["contactId"]) == id || remote == id {
                try self.store.upsert(["id": room["id"] ?? "", "name": name])
              }
            }
          }
        case "THREAD_MAPPING":
          let id = Self.string(value["threadKey"])
          if !id.isEmpty {
            self.mappings[id] = value
            if value["isGroup"] as? Bool == true { try self.store.removeRoom("messenger:" + id); self.pending.removeValue(forKey: id) }
            else { try self.applyPending(id) }
          }
        case "FBE2EE":
          if value["step"] as? String == "MESSAGE", let chat = value["chat"] as? String, chat.hasSuffix("@msgr") {
            let id = self.canonical(chat.components(separatedBy: "@")[0].components(separatedBy: ":")[0])
            self.mappings[id] = (self.mappings[id] ?? [:]).merging(["isGroup": false, "contactId": id]) { old, _ in old }
            if (try? self.store.room("messenger:" + id)) == nil {
              try self.store.upsert(self.room("messenger", id, name: self.contacts[id] ?? "Rozmowa", snippet: "", timestamp: Self.number(value["ts"]) * 1000))
            }
            try self.applyPending(id)
          }
        case "CONVERSATION": try self.mergeConversation(network, value)
        case "MESSAGE": try self.mergeMessage(network, value)
        case "CHATS_UPDATED": self.workQueue.async { [weak self] in try? self?.refresh("whatsapp") }
        case "LOGGED_OUT", "REAUTH_REQUIRED": self.phase(network, "SESSION_EXPIRED", "Połącz konto ponownie.")
        case "PAIR_ERROR", "CONNECT_ERROR", "PAIR_TIMEOUT": self.phase(network, "DISCONNECTED", "Nie udało się połączyć. Spróbuj ponownie.")
        case "DISCONNECTED": self.phase(network, "DISCONNECTED", "Otwórz Cue, aby odświeżyć rozmowy.")
        case "USER_ALERT":
          if payload.contains("RECONNECTED") { self.phase(network, "CONNECTED") }
          else if payload.contains("SOCKET_ERROR") { self.phase(network, "DISCONNECTED", "Połączenie przerwane.") }
        default: break
        }
        self.changed?()
      } catch { self.changed?() }
    }
  }
  private func bridgeString(_ call: (AutoreleasingUnsafeMutablePointer<NSError?>) -> String) throws -> String {
    var error: NSError?; let value = call(&error)
    if let error { throw error }; return value
  }
  func restore() throws {
    if stateQueue.sync(execute: { fb == nil }), let session = CueSecrets.get("messenger-session") { try connectMessenger(session, cookies: false) }
    if stateQueue.sync(execute: { wa == nil }), FileManager.default.fileExists(atPath: directory.appendingPathComponent("whatsapp.db").path) { try connectWhatsApp() }
  }
  func connectMessenger(_ session: String, cookies: Bool = true) throws {
    let generation = UUID(); let sink = CueBridgeSink(self, "messenger", generation)
    guard let bridge = FbmessagebridgeBridge(cookies ? "" : session, sink: sink) else { throw CueError("Nie udało się uruchomić Messengera.") }
    if cookies { try bridge.setCookies(session) }
    bridge.setE2EEStorePath(directory.appendingPathComponent("messenger-e2ee.db").path)
    let old = stateQueue.sync { () -> FbmessagebridgeBridge? in
      let old = fb; fb = bridge; sinks["messenger"] = sink; generations["messenger"] = generation
      phase("messenger", "CONNECTING", "Łączę z Messengerem…"); return old
    }
    old?.disconnect()
    do { try bridge.connect() }
    catch { stateQueue.sync { phase("messenger", "DISCONNECTED", "Nie udało się połączyć. Zaloguj się ponownie.") }; throw error }
    armTimeout("messenger", generation)
  }
  private func connectWhatsApp() throws {
    let generation = UUID(); let sink = CueBridgeSink(self, "whatsapp", generation)
    guard let bridge = WhatsappbridgeBridge(directory.appendingPathComponent("whatsapp.db").path, sink: sink) else { throw CueError("Nie udało się uruchomić WhatsApp.") }
    stateQueue.sync { wa = bridge; qrReady = false; sinks["whatsapp"] = sink; generations["whatsapp"] = generation; phase("whatsapp", "CONNECTING", "Łączę z WhatsApp…") }
    do { try bridge.connect() }
    catch { stateQueue.sync { phase("whatsapp", "DISCONNECTED", "Nie udało się połączyć.") }; throw error }
    armTimeout("whatsapp", generation)
  }
  private func armTimeout(_ network: String, _ generation: UUID) {
    stateQueue.asyncAfter(deadline: .now() + 60) { [weak self] in
      guard let self, self.generations[network] == generation, self.states[network]?["phase"] as? String == "CONNECTING" else { return }
      self.phase(network, "DISCONNECTED", "Połączenie trwało zbyt długo. Spróbuj ponownie.")
    }
  }
  func pair(_ phone: String) async throws -> String {
    var digits = phone.filter(\.isNumber)
    if digits.hasPrefix("00") { digits = String(digits.dropFirst(2)) }
    if digits.count == 9 { digits = "48" + digits }
    guard (7...15).contains(digits.count), !digits.hasPrefix("0") else { throw CueError("Wpisz numer z kodem kraju, np. +48.") }
    try await background { try self.disconnect("whatsapp"); try self.connectWhatsApp() }
    for _ in 0..<100 {
      if stateQueue.sync(execute: { qrReady }) { break }
      try await Task.sleep(nanoseconds: 200_000_000)
    }
    guard stateQueue.sync(execute: { qrReady }), let bridge = stateQueue.sync(execute: { wa }) else { throw CueError("WhatsApp nie jest gotowy do parowania. Spróbuj ponownie.") }
    let phone = digits
    let code = try await background { try self.bridgeString { bridge.requestPairCode(phone, error: $0) } }
    stateQueue.sync { phase("whatsapp", "CONNECTING", "Wpisz kod w WhatsApp → Połączone urządzenia.", code: code) }
    return code
  }
  func background<T>(_ action: @escaping () throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { continuation in workQueue.async { continuation.resume(with: Result { try action() }) } }
  }
  func refresh(_ network: String? = nil) throws {
    if network == nil || network == "messenger", let bridge = stateQueue.sync(execute: { fb }), bridge.isLoggedIn() {
      let raw = try CueJSON.object(bridgeString { bridge.listConversations(Int(Int32.max), error: $0) })
      try stateQueue.sync { for value in raw["conversations"] as? [[String: Any]] ?? [] { try mergeConversation("messenger", value) } }
    }
    if network == nil || network == "whatsapp", let bridge = stateQueue.sync(execute: { wa }), bridge.isLoggedIn() {
      let raw = try CueJSON.object(bridgeString { bridge.listConversations(150, error: $0) })
      try stateQueue.sync { for value in raw["conversations"] as? [[String: Any]] ?? [] { try mergeConversation("whatsapp", value) } }
    }
    changed?()
  }
  func sync(_ id: String) throws -> [String: Any] {
    let room = try store.room(id)
    if room["demo"] as? Bool == true { return room }
    try restore()
    let network = room["network"] as? String ?? "", remote = room["remoteId"] as? String ?? ""
    let raw: [String: Any]
    if network == "messenger", let bridge = stateQueue.sync(execute: { fb }) {
      raw = try CueJSON.object(bridgeString { bridge.fetchMessages(remote, count: 100, cursorJSON: "", error: $0) })
    } else if let bridge = stateQueue.sync(execute: { wa }) {
      raw = try CueJSON.object(bridgeString { bridge.fetchMessages(remote, count: 100, error: $0) })
    } else { throw CueError("Połącz konto w ustawieniach Cue.") }
    try stateQueue.sync { for value in raw["messages"] as? [[String: Any]] ?? [] { try mergeMessage(network, value) } }
    changed?(); return try store.room(id)
  }
  func image(_ id: String, _ messageID: String) throws -> String {
    let room = try store.room(id)
    guard let message = (room["messages"] as? [[String: Any]])?.first(where: { $0["id"] as? String == messageID }), let media = message["mediaId"] as? String else { throw CueError("Nie znaleziono zdjęcia.") }
    let data: Data?
    if room["network"] as? String == "messenger" { data = try stateQueue.sync { fb }?.downloadImage(forCue: media) }
    else { data = try stateQueue.sync { wa }?.downloadImage(forCue: media) }
    guard let data, data.count < 8 * 1024 * 1024 else { throw CueError("Zdjęcie niedostępne. Odśwież rozmowę.") }
    let url = directory.appendingPathComponent(UUID().uuidString + ".jpg")
    try data.write(to: url, options: .atomic); return url.absoluteString
  }
  func disconnect(_ network: String) throws {
    let bridges = stateQueue.sync { () -> (FbmessagebridgeBridge?, WhatsappbridgeBridge?) in
      generations[network] = UUID(); phase(network, "NOT_CONFIGURED")
      if network == "messenger" { let old = fb; fb = nil; contacts = [:]; mappings = [:]; pending = [:]; return (old, nil) }
      let old = wa; wa = nil; qrReady = false; return (nil, old)
    }
    bridges.0?.disconnect(); bridges.1?.close()
    if network == "messenger" { try CueSecrets.set("messenger-session", nil) }
    let prefix = network == "messenger" ? "messenger-e2ee.db" : "whatsapp.db"
    for url in try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) where url.lastPathComponent.hasPrefix(prefix) { try FileManager.default.removeItem(at: url) }
    try store.clear(network: network); changed?()
  }
  private func room(_ network: String, _ id: String, name: String, snippet: String, timestamp: Double) -> [String: Any] {
    ["id": network + ":" + id, "remoteId": id, "network": network, "name": name, "kind": "PRIVATE", "updatedAt": timestamp, "snippet": snippet, "profile": NSNull()]
  }
  private func canonical(_ id: String) -> String {
    mappings.first { key, value in value["isGroup"] as? Bool == false && (Self.string(value["contactId"]) == id || (value["e2eeRecipientId"] as? String ?? "").components(separatedBy: "@")[0] == id) }?.key ?? id
  }
  private func mergeConversation(_ network: String, _ value: [String: Any]) throws {
    let id = Self.string(value[network == "messenger" ? "threadKey" : "conversationID"])
    guard !id.isEmpty else { return }
    let isGroup = value["isGroup"] as? Bool ?? mappings[id]?["isGroup"] as? Bool
    let contact = Self.string(value["contactId"] ?? mappings[id]?["contactId"])
    if isGroup == true || id.hasSuffix("@g.us") { try store.removeRoom(network + ":" + id); return }
    guard network == "whatsapp" || isGroup == false || !contact.isEmpty else { return }
    let name = value[network == "messenger" ? "threadName" : "name"] as? String ?? ""
    let previous = try? store.room(network + ":" + id)
    let resolved = Self.usableName(name) ? name : contacts[contact] ?? previous?["name"] as? String ?? "Rozmowa"
    let timestamp = network == "messenger" ? Self.number(value["timestamp"]) : Self.millis(value["timestamp"])
    guard timestamp == 0 || timestamp > CueJSON.now - 365 * 86_400_000 else { return }
    let snippet = network == "messenger" ? value["snippet"] as? String ?? "" : (value["latestMessage"] as? [String: Any])?["displayContent"] as? String ?? ""
    var incoming = room(network, id, name: resolved, snippet: snippet, timestamp: timestamp)
    if previous != nil { incoming.removeValue(forKey: "profile") }
    try store.upsert(incoming)
    if network == "messenger" {
      mappings[id] = (mappings[id] ?? [:]).merging(value) { _, new in new }
      if !contact.isEmpty { try store.rehome("messenger:" + contact, to: "messenger:" + id) }
      try applyPending(id)
    }
  }
  private func applyPending(_ id: String) throws {
    let keys = pending.keys.filter { canonical($0) == id }
    for key in keys { let values = pending.removeValue(forKey: key) ?? []; for value in values { try mergeMessage("messenger", value) } }
  }
  private func mergeMessage(_ network: String, _ value: [String: Any]) throws {
    let rawID = Self.string(value[network == "messenger" ? "threadKey" : "conversationID"])
    let id = network == "messenger" ? canonical(rawID) : rawID
    guard !id.isEmpty, !id.hasSuffix("@g.us") else { return }
    guard (try? store.room(network + ":" + id)) != nil else {
      if network == "messenger" { pending[rawID] = Array(((pending[rawID] ?? []) + [value]).suffix(500)) }; return
    }
    let sender = value["senderParticipant"] as? [String: Any] ?? [:]
    let infos = value["messageInfo"] as? [[String: Any]] ?? []
    let media = infos.compactMap { $0["mediaContent"] as? [String: Any] }.first { ($0["mimeType"] as? String ?? "").hasPrefix("image/") || $0["format"] as? String == "IMAGE" }
    let photo = network == "messenger" ? (value["imageMime"] as? String ?? "").hasPrefix("image/") : media != nil
    let text = network == "messenger" ? value["text"] as? String ?? "" : infos.compactMap { ($0["messageContent"] as? [String: Any])?["content"] as? String }.joined(separator: "\n")
    let messageID = Self.string(value[network == "messenger" ? "messageId" : "messageID"])
    var message: [String: Any] = ["id": messageID.isEmpty ? "\(Self.string(value["timestamp"])):\(Self.string(value["senderId"]))" : messageID,
      "sender": network == "messenger" ? value["senderName"] as? String ?? contacts[Self.string(value["senderId"])] ?? "" : sender["fullName"] as? String ?? sender["firstName"] as? String ?? "",
      "text": photo ? "[Zdjęcie]" + (text.isEmpty ? "" : " " + text) : text,
      "timestamp": network == "messenger" ? Self.number(value["timestamp"]) : Self.millis(value["timestamp"]),
      "isMe": value["isMe"] as? Bool == true || value["fromMe"] as? Bool == true || sender["isMe"] as? Bool == true]
    if photo { message["mediaId"] = media?["mediaID"] as? String ?? messageID }
    try store.mergeMessages(network + ":" + id, [message])
  }
  static func string(_ value: Any?) -> String { value.map { $0 is NSNull ? "" : String(describing: $0) } ?? "" }
  static func number(_ value: Any?) -> Double { (value as? NSNumber)?.doubleValue ?? Double(string(value)) ?? 0 }
  static func millis(_ value: Any?) -> Double { let n = number(value); return n > 1e13 ? n / 1000 : (n > 1e10 ? n : n * 1000) }
  static func usableName(_ name: String) -> Bool { !name.isEmpty && name.rangeOfCharacter(from: .letters) != nil }
}

import Foundation
#if canImport(Security)
import Security
#endif

struct CueError: LocalizedError {
  let message: String
  init(_ message: String) { self.message = message }
  var errorDescription: String? { message }
}

enum CueJSON {
  static func encode(_ value: Any) throws -> String {
    String(data: try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), encoding: .utf8)!
  }
  static func object(_ text: String) throws -> [String: Any] {
    guard let value = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any] else { throw CueError("Nieprawidłowa odpowiedź.") }
    return value
  }
  static func number(_ value: Any?) -> Double { (value as? NSNumber)?.doubleValue ?? 0 }
  static var now: Double { Date().timeIntervalSince1970 * 1000 }
}

// Both processes coordinate read-modify-write operations. Never persist an old in-memory snapshot.
final class CueStore {
  let fileURL: URL
  private let lock = NSRecursiveLock()
  init(directory: URL) throws {
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    fileURL = directory.appendingPathComponent("cue-state.json")
  }
  #if os(iOS)
  static func shared() throws -> CueStore {
    let group = Bundle.main.object(forInfoDictionaryKey: "CueAppGroup") as? String ?? "group.com.mikolajpiech.guardian"
    guard let directory = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
      throw CueError("Otwórz Cue i włącz pełny dostęp do klawiatury w Ustawieniach.")
    }
    return try CueStore(directory: directory)
  }
  #endif
  private func readFile() throws -> [String: Any] {
    guard FileManager.default.fileExists(atPath: fileURL.path) else { return ["rooms": [], "memories": [:], "cloud": false, "epoch": 0] }
    return try CueJSON.object(String(contentsOf: fileURL, encoding: .utf8))
  }
  func read() throws -> [String: Any] {
    lock.lock(); defer { lock.unlock() }
    var result: Result<[String: Any], Error> = .failure(CueError("Nie można odczytać pamięci Cue."))
    #if os(iOS)
    var error: NSError?
    NSFileCoordinator().coordinate(readingItemAt: fileURL, options: [], error: &error) { _ in result = Result { try self.readFile() } }
    if let error { throw error }
    #else
    result = Result { try readFile() }
    #endif
    return try result.get()
  }
  @discardableResult func update(_ transform: @escaping (inout [String: Any]) throws -> Void) throws -> [String: Any] {
    lock.lock(); defer { lock.unlock() }
    var result: Result<[String: Any], Error> = .failure(CueError("Nie można zapisać pamięci Cue."))
    let write = {
      result = Result {
        var state = try self.readFile()
        try transform(&state)
        let data = try JSONSerialization.data(withJSONObject: state)
        try data.write(to: self.fileURL, options: .atomic)
        #if os(iOS)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: self.fileURL.path)
        #endif
        return state
      }
    }
    #if os(iOS)
    var error: NSError?
    NSFileCoordinator().coordinate(writingItemAt: fileURL, options: .forReplacing, error: &error) { _ in write() }
    if let error { throw error }
    #else
    write()
    #endif
    return try result.get()
  }
  func rooms() throws -> [[String: Any]] { (try read()["rooms"] as? [[String: Any]] ?? []).sorted { CueJSON.number($0["updatedAt"]) > CueJSON.number($1["updatedAt"]) } }
  func room(_ id: String) throws -> [String: Any] {
    guard let room = try rooms().first(where: { $0["id"] as? String == id }) else { throw CueError("Nie znaleziono rozmowy. Otwórz Cue i odśwież rozmowy.") }
    return room
  }
  func upsert(_ incoming: [String: Any]) throws {
    try update { state in
      var rooms = state["rooms"] as? [[String: Any]] ?? []
      if let index = rooms.firstIndex(where: { $0["id"] as? String == incoming["id"] as? String }) {
        rooms[index].merge(incoming) { _, new in new }
      } else { rooms.append(incoming) }
      state["rooms"] = rooms
    }
  }
  func mergeMessages(_ id: String, _ incoming: [[String: Any]]) throws {
    try update { state in
      var rooms = state["rooms"] as? [[String: Any]] ?? []
      guard let index = rooms.firstIndex(where: { $0["id"] as? String == id }) else { return }
      var messages = rooms[index]["messages"] as? [[String: Any]] ?? []
      for message in incoming where !(message["id"] as? String ?? "").isEmpty {
        messages.removeAll { $0["id"] as? String == message["id"] as? String }; messages.append(message)
      }
      messages.sort { CueJSON.number($0["timestamp"]) < CueJSON.number($1["timestamp"]) }
      rooms[index]["messages"] = Array(messages.suffix(500))
      rooms[index]["messageCount"] = min(messages.count, 500)
      if let latest = messages.last, CueJSON.number(latest["timestamp"]) >= CueJSON.number(rooms[index]["updatedAt"]) {
        rooms[index]["updatedAt"] = latest["timestamp"]; rooms[index]["snippet"] = latest["text"]
      }
      state["rooms"] = rooms
    }
  }
  func memory(_ id: String) throws -> [String: Any] { (try read()["memories"] as? [String: [String: Any]])?[id] ?? [:] }
  func tone(_ id: String?) throws -> String {
    let state = try read()
    return (id.flatMap { (state["tones"] as? [String: String])?[$0] } ?? state["tone"] as? String) ?? "natural"
  }
  func removeRoom(_ id: String) throws {
    try update { state in
      state["rooms"] = (state["rooms"] as? [[String: Any]] ?? []).filter { $0["id"] as? String != id }
      var memories = state["memories"] as? [String: Any] ?? [:]; memories.removeValue(forKey: id); state["memories"] = memories
      state["epoch"] = (state["epoch"] as? Int ?? 0) + 1
    }
  }
  func rehome(_ source: String, to target: String) throws {
    guard source != target, let old = try? room(source), (try? room(target)) != nil else { return }
    try mergeMessages(target, old["messages"] as? [[String: Any]] ?? [])
    try update { state in
      var memories = state["memories"] as? [String: Any] ?? [:]
      if memories[target] == nil { memories[target] = memories[source] }
      memories.removeValue(forKey: source); state["memories"] = memories
      state["rooms"] = (state["rooms"] as? [[String: Any]] ?? []).filter { $0["id"] as? String != source }
      state["epoch"] = (state["epoch"] as? Int ?? 0) + 1
    }
  }
  func clear(network: String? = nil) throws {
    try update { state in
      state["epoch"] = (state["epoch"] as? Int ?? 0) + 1
      if let network {
        let ids = Set((state["rooms"] as? [[String: Any]] ?? []).filter { $0["network"] as? String == network }.compactMap { $0["id"] as? String })
        state["rooms"] = (state["rooms"] as? [[String: Any]] ?? []).filter { !ids.contains($0["id"] as? String ?? "") }
        state["memories"] = (state["memories"] as? [String: Any] ?? [:]).filter { !ids.contains($0.key) }
        state["tones"] = (state["tones"] as? [String: Any] ?? [:]).filter { !ids.contains($0.key) }
      } else { state["rooms"] = []; state["memories"] = [:]; state["tones"] = [:] }
    }
  }
}

// Tokens and Messenger cookies never go into the shared conversation cache.
enum CueSecrets {
  #if canImport(Security)
  private static func query(_ name: String) -> [String: Any] {
    var query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "Cue", kSecAttrAccount as String: name]
    if let group = Bundle.main.object(forInfoDictionaryKey: "CueKeychainGroup") as? String, !group.contains("$(") { query[kSecAttrAccessGroup as String] = group }
    return query
  }
  static func get(_ name: String) -> String? {
    var query = query(name); query[kSecReturnData as String] = true
    var item: CFTypeRef?
    guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
    return String(data: data, encoding: .utf8)
  }
  static func set(_ name: String, _ value: String?) throws {
    let query = query(name)
    SecItemDelete(query as CFDictionary)
    guard let value else { return }
    var insert = query; insert[kSecValueData as String] = Data(value.utf8)
    insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw CueError("Nie udało się bezpiecznie zapisać sesji.") }
  }
  #endif
}

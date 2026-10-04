import XCTest
@testable import CueShared

final class CueSharedTests: XCTestCase {
  func store() throws -> CueStore {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("cue-test-" + UUID().uuidString)
    addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
    return try CueStore(directory: directory)
  }
  func room(_ id: String, _ network: String = "messenger") -> [String: Any] { ["id": id, "network": network, "messages": [], "updatedAt": 0, "profile": NSNull()] }
  func testTwoProcessesDoNotOverwriteEachOther() throws {
    let a = try store(), b = try CueStore(directory: a.fileURL.deletingLastPathComponent())
    try a.upsert(room("one")); try b.upsert(room("two"))
    try a.update { $0["cloud"] = true }; try b.update { $0["keyboardRoom"] = "two" }
    XCTAssertEqual(try a.rooms().count, 2); XCTAssertEqual(try b.read()["cloud"] as? Bool, true)
  }
  func testMessagesMergeDeduplicateAndPreserveProfile() throws {
    let store = try store(); var value = room("a"); value["profile"] = ["summary": "saved"]; try store.upsert(value)
    try store.mergeMessages("a", [["id": "2", "text": "new", "timestamp": 200], ["id": "1", "text": "old", "timestamp": 100]])
    try store.mergeMessages("a", [["id": "2", "text": "updated", "timestamp": 200]])
    let cached = try store.room("a"), messages = cached["messages"] as! [[String: Any]]
    XCTAssertEqual(messages.count, 2); XCTAssertEqual(messages[1]["text"] as? String, "updated")
    XCTAssertEqual((cached["profile"] as? [String: Any])?["summary"] as? String, "saved")
  }
  func testDisconnectOnlyClearsItsNetworkAndInvalidatesAnalysis() throws {
    let store = try store(); try store.upsert(room("m")); try store.upsert(room("w", "whatsapp"))
    try store.update { $0["memories"] = ["m": ["summary": "m"], "w": ["summary": "w"]] }
    try store.clear(network: "messenger")
    XCTAssertEqual(try store.rooms().map { $0["id"] as! String }, ["w"])
    XCTAssertEqual(try store.read()["epoch"] as? Int, 1); XCTAssertTrue(try store.memory("m").isEmpty); XCTAssertFalse(try store.memory("w").isEmpty)
  }
  func testFullClearRemovesMemoriesAndKeepsCloudChoice() throws {
    let store = try store(); try store.upsert(room("m")); try store.update { $0["cloud"] = true; $0["memories"] = ["m": ["text": "memory"]] }
    try store.clear(); XCTAssertTrue(try store.rooms().isEmpty); XCTAssertTrue(try store.memory("m").isEmpty); XCTAssertEqual(try store.read()["cloud"] as? Bool, true)
  }
  func testCursorSnapshotRejectsChangedDraftSelectionOrDocument() {
    let id = UUID(), a = CueDraftSnapshot(document: id, before: "Hi", after: "", selection: nil)
    XCTAssertTrue(a.matches(a))
    XCTAssertFalse(a.matches(CueDraftSnapshot(document: id, before: "Hi!", after: "", selection: nil)))
    XCTAssertFalse(a.matches(CueDraftSnapshot(document: id, before: "Hi", after: "", selection: "Hi")))
    XCTAssertFalse(a.matches(CueDraftSnapshot(document: UUID(), before: "Hi", after: "", selection: nil)))
  }
  func testUndoOnlyDeletesAnUnchangedKnownInsertion() {
    let id = UUID(), insertion = CueInsertion(document: id, text: "Cześć 👋🏽", before: "Start Cześć 👋🏽", after: "end")
    XCTAssertTrue(insertion.canUndo(CueDraftSnapshot(document: id, before: "Start Cześć 👋🏽", after: "end", selection: nil)))
    XCTAssertFalse(insertion.canUndo(CueDraftSnapshot(document: id, before: "Start Cześć 👋🏽!", after: "end", selection: nil)))
    XCTAssertFalse(insertion.canUndo(CueDraftSnapshot(document: UUID(), before: "Start Cześć 👋🏽", after: "end", selection: nil)))
    XCTAssertFalse(insertion.canUndo(CueDraftSnapshot(document: id, before: "👋🏽", after: "end", selection: nil)))
  }
  func testEvidenceMustReferToMessagesActuallySentToAI() throws {
    let raw: [String: Any] = ["summary": "s", "beforeReply": "b", "observations": [["text": "unsupported", "evidenceIds": ["invented"]]], "commitments": [], "suggestions": [["text": "Hi", "tone": "Natural"]]]
    let profile = try CueAI.validate(raw, messages: [["id": "actual"]], draft: "", memoryOnly: false)
    XCTAssertTrue((profile["observations"] as! [Any]).isEmpty)
  }
  func testNoReplyRequiresReasonAndDoesNotBecomeInsertableText() throws {
    let raw: [String: Any] = ["summary": "s", "beforeReply": "b", "suggestions": [["action": "no_reply", "reason": "Wait"]]]
    let profile = try CueAI.validate(raw, messages: [], draft: "", memoryOnly: false)
    XCTAssertEqual((profile["suggestions"] as! [[String: Any]])[0]["text"] as? String, "")
    XCTAssertThrowsError(try CueAI.validate(["summary": "s", "beforeReply": "b", "suggestions": [["action": "no_reply"]]], messages: [], draft: "", memoryOnly: false))
  }
  func testReminderRejectsInvalidDateAndUnsupportedEvidence() {
    let raw: [String: Any] = ["reminderUpdates": [["text": "bad date", "kind": "meeting", "owner": "both", "status": "open", "dueDate": "2026-02-31", "evidenceIds": ["m"]], ["text": "unsupported", "evidenceIds": ["fake"]]]]
    let memory = CueMemory.merge([:], raw: raw, messages: [["id": "m"]])
    XCTAssertTrue((memory["reminders"] as! [Any]).isEmpty)
  }
  func testReminderDatesAndEffectiveStatus() {
    let memory: [String: Any] = ["reminders": [["id": "1", "kind": "commitment", "status": "open", "dueDate": "2020-01-01"], ["id": "2", "kind": "waiting", "status": "open", "dueDate": ""], ["id": "3", "kind": "meeting", "status": "done", "dueDate": "2020-01-01"]]]
    XCTAssertEqual(CueMemory.reminders(memory).map { $0["effectiveStatus"] as! String }, ["overdue", "waiting", "done"])
  }
}

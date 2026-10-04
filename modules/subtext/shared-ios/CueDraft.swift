import Foundation

// iOS only exposes a window around the cursor. Never delete an assumed whole draft.
struct CueDraftSnapshot: Equatable {
  let document: UUID
  let before: String?
  let after: String?
  let selection: String?
  func matches(_ other: CueDraftSnapshot) -> Bool { self == other }
}
struct CueInsertion {
  let document: UUID
  let text: String
  let before: String?
  let after: String?
  func canUndo(_ current: CueDraftSnapshot) -> Bool {
    document == current.document && (current.selection ?? "").isEmpty &&
      current.before == before && current.after == after && (current.before?.hasSuffix(text) ?? false)
  }
}

import UIKit

class CueKeyboardViewController: UIInputViewController, UITableViewDataSource, UITableViewDelegate {
  private let root = UIStackView()
  private let toolbar = UIStackView()
  private let panel = UIStackView()
  private let keys = UIStackView()
  private let person = UIButton(type: .system)
  private let suggest = UIButton(type: .system)
  private let search = UILabel()
  private let table = UITableView(frame: .zero, style: .plain)
  private var height: NSLayoutConstraint?
  private var store: CueStore?
  private var rooms: [[String: Any]] = []
  private var selectedID: String?
  private var picker = false
  private var query = ""
  private var shifted = true
  private var capsLock = false
  private var symbols = 0
  private var lastShift = Date.distantPast
  private var lastSpace = Date.distantPast
  private var deleteTimer: Timer?
  private var request: Task<Void, Never>?
  private var requestID = UUID()
  private var replySnapshot: CueDraftSnapshot?
  private var insertion: CueInsertion?
  private var replacedSelection = ""
  private var variants: UIStackView?
  private let feedback = UISelectionFeedbackGenerator()
  private let accent: [String: [String]] = ["a": ["ą", "á", "à", "ä"], "c": ["ć", "ç"], "e": ["ę", "é", "è", "ë"], "l": ["ł"], "n": ["ń", "ñ"], "o": ["ó", "ö", "ò"], "s": ["ś", "ß"], "z": ["ż", "ź"], "u": ["ü", "ú"]]

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.12, green: 0.13, blue: 0.17, alpha: 1) : UIColor(red: 0.88, green: 0.9, blue: 0.94, alpha: 1) }
    root.axis = .vertical; root.spacing = 6; root.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(root)
    NSLayoutConstraint.activate([root.topAnchor.constraint(equalTo: view.topAnchor, constant: 2), root.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 5), root.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -5), root.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -4)])
    toolbar.axis = .horizontal; toolbar.spacing = 8; toolbar.alignment = .center
    toolbar.heightAnchor.constraint(equalToConstant: 44).isActive = true
    let mascot = UIImageView(image: UIImage(named: "CueMascot")); mascot.contentMode = .scaleAspectFit; mascot.widthAnchor.constraint(equalToConstant: 36).isActive = true; mascot.heightAnchor.constraint(equalToConstant: 36).isActive = true
    mascot.isAccessibilityElement = false; toolbar.addArrangedSubview(mascot)
    person.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold); person.contentHorizontalAlignment = .leading
    person.setTitleColor(.label, for: .normal); person.accessibilityLabel = "Wybierz rozmowę dla Cue"
    person.addAction(UIAction { [weak self] _ in self?.togglePicker() }, for: .touchUpInside); toolbar.addArrangedSubview(person)
    suggest.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold); suggest.layer.cornerRadius = 17
    suggest.backgroundColor = UIColor(red: 0.28, green: 0.41, blue: 0.7, alpha: 1); suggest.setTitleColor(.white, for: .normal)
    suggest.widthAnchor.constraint(equalToConstant: 102).isActive = true; suggest.heightAnchor.constraint(equalToConstant: 34).isActive = true
    suggest.addAction(UIAction { [weak self] _ in self?.analyze() }, for: .touchUpInside); toolbar.addArrangedSubview(suggest)
    root.addArrangedSubview(toolbar)
    panel.axis = .vertical; panel.spacing = 4; panel.isHidden = true; root.addArrangedSubview(panel)
    keys.axis = .vertical; keys.spacing = 7; root.addArrangedSubview(keys)
    table.dataSource = self; table.delegate = self; table.rowHeight = 43; table.backgroundColor = .clear
    table.separatorInset = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14); table.keyboardDismissMode = .none
    table.register(UITableViewCell.self, forCellReuseIdentifier: "person")
    reloadStore(); renderKeys(); refreshToolbar()
  }
  override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); reloadStore(); refreshToolbar(); updateHeight() }
  override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); cancelRequest(); deleteTimer?.invalidate() }
  override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); updateHeight() }
  override func textDidChange(_ textInput: UITextInput?) {
    super.textDidChange(textInput)
    if let snapshot = replySnapshot, !snapshot.matches(currentSnapshot()) { invalidateReply() }
    if !picker && !capsLock && symbols == 0 { autocapitalize(); renderKeys() }
  }
  private func reloadStore() {
    guard hasFullAccess else { store = nil; rooms = []; selectedID = nil; return }
    do {
      store = try CueStore.shared(); rooms = try store?.rooms() ?? []
      let state = try store?.read() ?? [:]
      let saved = state["keyboardRoom"] as? String
      if selectedID == nil { selectedID = saved }
      if !rooms.contains(where: { $0["id"] as? String == selectedID }) { selectedID = nil }
    } catch { store = nil; rooms = [] }
  }
  private func refreshToolbar() {
    let name = rooms.first { $0["id"] as? String == selectedID }?["name"] as? String
    person.setTitle(picker ? "Cue · Wybierz osobę" : (name.map { $0 + " ⌄" } ?? "Cue · Wybierz rozmowę ⌄"), for: .normal)
    suggest.setTitle(request != nil ? "Anuluj" : "Podpowiedz", for: .normal)
    suggest.alpha = request != nil || selectedID != nil ? 1 : 0.65
  }
  private func resetPanel() {
    for view in panel.arrangedSubviews { panel.removeArrangedSubview(view); view.removeFromSuperview() }
    panel.isHidden = true
  }
  private func showMessage(_ text: String) {
    resetPanel(); panel.isHidden = false
    let label = UILabel(); label.text = text; label.numberOfLines = 2; label.font = .systemFont(ofSize: 13); label.textColor = .secondaryLabel
    label.accessibilityTraits = .staticText; panel.addArrangedSubview(label)
    label.heightAnchor.constraint(equalToConstant: 42).isActive = true; updateHeight()
    UIAccessibility.post(notification: .announcement, argument: text)
  }
  private func togglePicker() {
    cancelRequest(); replySnapshot = nil; insertion = nil; picker.toggle(); query = ""
    if !picker { resetPanel(); refreshToolbar(); updateHeight(); return }
    reloadStore()
    guard hasFullAccess else { picker = false; showMessage("Włącz pełny dostęp do klawiatury Cue w Ustawieniach, aby wybrać rozmowę."); return }
    guard !rooms.isEmpty else { picker = false; showMessage("Połącz komunikator w Cue i odśwież rozmowy. Możesz dalej pisać."); return }
    resetPanel(); panel.isHidden = false
    let row = UIStackView(); row.spacing = 4; row.alignment = .center
    search.font = .systemFont(ofSize: 14); search.textColor = .secondaryLabel; search.text = "Szukaj osoby — wpisz imię poniżej"
    row.addArrangedSubview(search)
    let close = UIButton(type: .system); close.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal); close.accessibilityLabel = "Zamknij wybór rozmowy"
    close.addAction(UIAction { [weak self] _ in self?.togglePicker() }, for: .touchUpInside); row.addArrangedSubview(close)
    row.heightAnchor.constraint(equalToConstant: 28).isActive = true; panel.addArrangedSubview(row)
    panel.addArrangedSubview(table); table.heightAnchor.constraint(equalToConstant: 129).isActive = true
    table.reloadData(); refreshToolbar(); updateHeight()
  }
  private var filtered: [[String: Any]] {
    let normalized = query.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pl_PL")).replacingOccurrences(of: "ł", with: "l")
    return rooms.filter { query.isEmpty || ($0["name"] as? String ?? "").folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pl_PL")).replacingOccurrences(of: "ł", with: "l").contains(normalized) }
  }
  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { filtered.count }
  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    let cell = tableView.dequeueReusableCell(withIdentifier: "person", for: indexPath), room = filtered[indexPath.row]
    var content = cell.defaultContentConfiguration(); content.text = room["name"] as? String; content.textProperties.font = .systemFont(ofSize: 15, weight: .medium)
    content.secondaryText = room["network"] as? String == "whatsapp" ? "WhatsApp" : "Messenger"; content.secondaryTextProperties.font = .systemFont(ofSize: 11)
    cell.contentConfiguration = content; cell.backgroundColor = .clear; cell.accessoryType = room["id"] as? String == selectedID ? .checkmark : .none; return cell
  }
  func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
    selectedID = filtered[indexPath.row]["id"] as? String; _ = try? store?.update { $0["keyboardRoom"] = self.selectedID }
    picker = false; query = ""; resetPanel(); refreshToolbar(); autocapitalize(); renderKeys(); updateHeight()
  }
  func currentSnapshot() -> CueDraftSnapshot {
    CueDraftSnapshot(document: textDocumentProxy.documentIdentifier, before: textDocumentProxy.documentContextBeforeInput, after: textDocumentProxy.documentContextAfterInput, selection: textDocumentProxy.selectedText)
  }
  private func analyze() {
    if request != nil { cancelRequest(); resetPanel(); updateHeight(); return }
    if picker { togglePicker() }
    reloadStore()
    guard hasFullAccess, let store else { showMessage("Włącz pełny dostęp do Cue w ustawieniach klawiatury. Samo pisanie działa bez niego."); return }
    guard let id = selectedID else { togglePicker(); return }
    guard (try? store.read()["cloud"] as? Bool) == true else { showMessage("Włącz podpowiedzi AI w ustawieniach Cue."); return }
    let snapshot = currentSnapshot(), token = UUID(); requestID = token; replySnapshot = snapshot
    let draft = snapshot.selection ?? snapshot.before ?? ""
    showMessage("Cue przygotowuje podpowiedzi…")
    request = Task { [weak self] in
      do {
        let profile = try await CueAI.shared.analyze(store: store, id: id, draft: draft)
        guard !Task.isCancelled else { return }
        await MainActor.run {
          guard let self, self.requestID == token, self.selectedID == id else { return }
          self.request = nil; self.refreshToolbar()
          guard snapshot.matches(self.currentSnapshot()) else { self.invalidateReply(); return }
          self.showReplies(profile["suggestions"] as? [[String: Any]] ?? [], snapshot)
        }
      } catch {
        guard !Task.isCancelled else { return }
        await MainActor.run {
          guard let self, self.requestID == token else { return }
          self.request = nil; self.replySnapshot = nil; self.refreshToolbar(); self.showMessage(error.localizedDescription)
        }
      }
    }
    refreshToolbar()
  }
  private func cancelRequest() { request?.cancel(); request = nil; requestID = UUID(); replySnapshot = nil; refreshToolbar() }
  private func invalidateReply() {
    cancelRequest(); replySnapshot = nil; showMessage("Tekst się zmienił. Stuknij Podpowiedz, aby użyć aktualnego tekstu.")
  }
  func showReplies(_ replies: [[String: Any]], _ snapshot: CueDraftSnapshot) {
    resetPanel(); panel.isHidden = false
    let scroll = UIScrollView(); scroll.showsHorizontalScrollIndicator = true; scroll.heightAnchor.constraint(equalToConstant: 126).isActive = true
    let cards = UIStackView(); cards.axis = .horizontal; cards.spacing = 10; cards.translatesAutoresizingMaskIntoConstraints = false; scroll.addSubview(cards)
    NSLayoutConstraint.activate([cards.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor), cards.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor), cards.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor), cards.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor), cards.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor)])
    for (index, reply) in replies.enumerated() {
      let card = UIStackView(); card.axis = .vertical; card.spacing = 4; card.isLayoutMarginsRelativeArrangement = true; card.layoutMargins = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
      card.backgroundColor = .secondarySystemGroupedBackground; card.layer.cornerRadius = 12
      cards.addArrangedSubview(card)
      card.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -22).isActive = true
      let tone = UILabel(); tone.font = .systemFont(ofSize: 11, weight: .semibold); tone.textColor = .secondaryLabel; tone.text = "\(index + 1)/\(replies.count) · \(reply["tone"] as? String ?? "Naturalnie")"; card.addArrangedSubview(tone)
      let noReply = reply["action"] as? String == "no_reply", text = reply["text"] as? String ?? ""
      let preview = UILabel(); preview.text = noReply ? reply["reason"] as? String : text; preview.numberOfLines = 3; preview.font = .systemFont(ofSize: 14); preview.textColor = .label; card.addArrangedSubview(preview)
      let insert = UIButton(type: .system); insert.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
      insert.setTitle(noReply ? "Zostaw bez odpowiedzi" : ((snapshot.selection ?? "").isEmpty ? "Wstaw przy kursorze" : "Zastąp zaznaczenie"), for: .normal)
      insert.addAction(UIAction { [weak self] _ in
        guard let self else { return }
        if noReply { self.resetPanel(); self.replySnapshot = nil; self.updateHeight() }
        else { self.insertReply(text, snapshot) }
      }, for: .touchUpInside); card.addArrangedSubview(insert)
    }
    panel.addArrangedSubview(scroll); updateHeight(); UIAccessibility.post(notification: .announcement, argument: "Podpowiedzi gotowe")
  }
  private func insertReply(_ text: String, _ snapshot: CueDraftSnapshot) {
    guard snapshot.matches(currentSnapshot()) else { invalidateReply(); return }
    replySnapshot = nil; replacedSelection = snapshot.selection ?? ""
    textDocumentProxy.insertText(text)
    let current = currentSnapshot()
    insertion = CueInsertion(document: current.document, text: text, before: current.before, after: current.after)
    resetPanel(); panel.isHidden = false
    let undo = UIButton(type: .system); undo.setTitle("Wstawiono · Cofnij", for: .normal); undo.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
    undo.addAction(UIAction { [weak self] _ in
      guard let self, let insertion = self.insertion, insertion.canUndo(self.currentSnapshot()) else { self?.showMessage("Tekst się zmienił. Użyj usuń, aby go edytować."); return }
      for _ in insertion.text { self.textDocumentProxy.deleteBackward() }
      if !self.replacedSelection.isEmpty { self.textDocumentProxy.insertText(self.replacedSelection) }
      self.insertion = nil; self.resetPanel(); self.updateHeight()
    }, for: .touchUpInside); panel.addArrangedSubview(undo); undo.heightAnchor.constraint(equalToConstant: 36).isActive = true; updateHeight()
  }
  private func autocapitalize() {
    guard !capsLock, !picker else { return }
    let before = textDocumentProxy.documentContextBeforeInput ?? ""
    switch textDocumentProxy.autocapitalizationType ?? .sentences {
    case .none: shifted = false
    case .allCharacters: shifted = true
    case .words: shifted = before.isEmpty || before.last?.isWhitespace == true
    default: shifted = before.isEmpty || before.hasSuffix("\n") || [". ", "! ", "? "].contains(where: before.hasSuffix)
    }
  }
  private func renderKeys() {
    for view in keys.arrangedSubviews { keys.removeArrangedSubview(view); view.removeFromSuperview() }
    let numeric = textDocumentProxy.keyboardType == .numberPad || textDocumentProxy.keyboardType == .decimalPad
    let rows: [[String]] = numeric ? [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["🌐", ".", "0", "⌫"]] :
      symbols == 0 ? [Array("qwertyuiop").map(String.init), Array("asdfghjkl").map(String.init), ["⇧"] + Array("zxcvbnm").map(String.init) + ["⌫"], ["123", "🌐", "spacja", returnTitle()]] :
      symbols == 1 ? [Array("1234567890").map(String.init), ["-", "/", ":", ";", "(", ")", "$", "&", "@", "\""], ["#+=", ".", ",", "?", "!", "'", "⌫"], ["ABC", "🌐", "spacja", returnTitle()]] :
      [["[", "]", "{", "}", "#", "%", "^", "*", "+", "="], ["_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"], ["123", ".", ",", "?", "!", "'", "⌫"], ["ABC", "🌐", "spacja", returnTitle()]]
    for (index, values) in rows.enumerated() {
      let row = UIStackView(); row.axis = .horizontal; row.spacing = 5; row.distribution = .fill
      for value in values {
        let button = UIButton(type: .custom)
        let letter = value.count == 1 && value.rangeOfCharacter(from: .letters) != nil
        let title = letter && shifted ? value.uppercased() : value
        button.setTitle(title == "⇧" && capsLock ? "⇪" : title, for: .normal)
        button.setTitleColor(.label, for: .normal); button.titleLabel?.font = .systemFont(ofSize: letter ? 24 : (value.count > 2 ? 14 : 20), weight: .regular)
        let special = ["⇧", "⌫", "123", "ABC", "#+=", "🌐"].contains(value) || value == returnTitle()
        button.backgroundColor = UIColor { traits in
          if traits.userInterfaceStyle == .dark { return special ? UIColor(white: 0.22, alpha: 1) : UIColor(white: 0.32, alpha: 1) }
          return special ? UIColor(red: 0.72, green: 0.76, blue: 0.83, alpha: 1) : .white
        }
        button.layer.cornerRadius = 6; button.layer.shadowColor = UIColor.black.cgColor; button.layer.shadowOpacity = 0.12; button.layer.shadowRadius = 0; button.layer.shadowOffset = CGSize(width: 0, height: 1)
        button.accessibilityLabel = ["⇧": "Shift", "⌫": "Usuń", "🌐": "Zmień klawiaturę", "spacja": "Spacja"][value] ?? title
        button.addAction(UIAction { [weak self] _ in self?.key(value) }, for: .touchUpInside)
        if value == "🌐" { button.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents) }
        if value == "⌫" {
          let hold = UILongPressGestureRecognizer(target: self, action: #selector(deleteHold(_:))); hold.minimumPressDuration = 0.35; button.addGestureRecognizer(hold)
        } else if accent[value] != nil {
          button.accessibilityHint = "Przytrzymaj, aby wybrać znak z akcentem"
          button.accessibilityIdentifier = value
          let hold = UILongPressGestureRecognizer(target: self, action: #selector(accentHold(_:))); hold.minimumPressDuration = 0.35; button.addGestureRecognizer(hold)
        }
        row.addArrangedSubview(button)
        if let first = row.arrangedSubviews.first, first !== button {
          let firstValue = values.first!
          let width = value == "spacja" ? 4.0 : value == returnTitle() ? 1.65 : value == "⇧" || value == "⌫" ? 1.4 : 1.0
          let base = firstValue == "⇧" || firstValue == "#+=" || firstValue == "123" || firstValue == "ABC" ? 1.4 : 1.0
          button.widthAnchor.constraint(equalTo: first.widthAnchor, multiplier: width / base).isActive = true
        }
      }
      row.heightAnchor.constraint(equalToConstant: view.bounds.width > 600 ? 44 : 42).isActive = true
      if symbols == 0 && !numeric && index == 1 { row.isLayoutMarginsRelativeArrangement = true; row.layoutMargins = UIEdgeInsets(top: 0, left: 15, bottom: 0, right: 15) }
      keys.addArrangedSubview(row)
    }
  }
  private func returnTitle() -> String {
    if picker { return "gotowe" }
    switch textDocumentProxy.returnKeyType ?? .default { case .search: return "szukaj"; case .send: return "wyślij"; case .go: return "idź"; case .done: return "gotowe"; case .next: return "dalej"; default: return "return" }
  }
  private func key(_ value: String) {
    feedback.selectionChanged()
    if value == "⇧" {
      if Date().timeIntervalSince(lastShift) < 0.3 { capsLock = true; shifted = true }
      else { capsLock = false; shifted.toggle() }; lastShift = Date(); renderKeys(); return
    }
    if value == "123" { symbols = 1; renderKeys(); return }
    if value == "#+=" { symbols = 2; renderKeys(); return }
    if value == "ABC" { symbols = 0; autocapitalize(); renderKeys(); return }
    if value == "🌐" { return }
    if value == "⌫" { deleteOne(); return }
    if value == returnTitle() { if picker { togglePicker() } else { type("\n") }; return }
    if value == "spacja" {
      if !picker, Date().timeIntervalSince(lastSpace) < 0.3, let before = textDocumentProxy.documentContextBeforeInput, before.hasSuffix(" "), before.dropLast().last?.isLetter == true {
        textDocumentProxy.deleteBackward(); type(". ")
      } else { type(" ") }; lastSpace = Date(); return
    }
    type(shifted && symbols == 0 ? value.uppercased() : value)
    if !capsLock && symbols == 0 { shifted = false; renderKeys() }
  }
  private func type(_ text: String) {
    if picker { query += text; search.text = query.isEmpty ? "Szukaj osoby — wpisz imię poniżej" : query; table.reloadData(); return }
    if replySnapshot != nil { invalidateReply() }
    insertion = nil; textDocumentProxy.insertText(text); autocapitalize()
  }
  private func deleteOne() {
    if picker { if !query.isEmpty { query.removeLast() }; search.text = query.isEmpty ? "Szukaj osoby — wpisz imię poniżej" : query; table.reloadData() }
    else { if replySnapshot != nil { invalidateReply() }; insertion = nil; textDocumentProxy.deleteBackward(); autocapitalize() }
  }
  @objc private func deleteHold(_ recognizer: UILongPressGestureRecognizer) {
    if recognizer.state == .began { deleteOne(); deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in self?.deleteOne() } }
    else if recognizer.state == .ended || recognizer.state == .cancelled || recognizer.state == .failed { deleteTimer?.invalidate(); deleteTimer = nil; renderKeys() }
  }
  @objc private func accentHold(_ recognizer: UILongPressGestureRecognizer) {
    guard recognizer.state == .began, let key = recognizer.view?.accessibilityIdentifier, let choices = accent[key] else { return }
    variants?.removeFromSuperview()
    let row = UIStackView(); row.axis = .horizontal; row.distribution = .fillEqually; row.backgroundColor = .secondarySystemBackground; row.layer.cornerRadius = 10
    for choice in choices {
      let button = UIButton(type: .system); button.setTitle(shifted ? choice.uppercased() : choice, for: .normal); button.titleLabel?.font = .systemFont(ofSize: 24)
      button.addAction(UIAction { [weak self, weak row] _ in self?.type(self?.shifted == true ? choice.uppercased() : choice); row?.removeFromSuperview(); self?.variants = nil; self?.autocapitalize(); self?.renderKeys() }, for: .touchUpInside); row.addArrangedSubview(button)
    }
    let close = UIButton(type: .system); close.setTitle("×", for: .normal); close.accessibilityLabel = "Zamknij znaki"; close.addAction(UIAction { [weak self, weak row] _ in row?.removeFromSuperview(); self?.variants = nil }, for: .touchUpInside); row.addArrangedSubview(close)
    row.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(row); NSLayoutConstraint.activate([row.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10), row.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10), row.topAnchor.constraint(equalTo: view.topAnchor, constant: 3), row.heightAnchor.constraint(equalToConstant: 48)])
    variants = row
  }
  private func updateHeight() {
    let panelHeight: CGFloat = panel.isHidden ? 0 : panel.systemLayoutSizeFitting(CGSize(width: max(200, view.bounds.width - 10), height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height + 6
    let total = 44 + 6 + CGFloat(keys.arrangedSubviews.count) * (view.bounds.width > 600 ? 44 : 42) + 21 + 6 + panelHeight + view.safeAreaInsets.bottom
    if height == nil { height = view.heightAnchor.constraint(equalToConstant: total); height?.priority = .init(999); height?.isActive = true }
    if abs((height?.constant ?? 0) - total) > 1 { height?.constant = total }
  }
}

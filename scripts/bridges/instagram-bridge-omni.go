package instagrambridge

import (
	"context"
	"encoding/json"
	"fmt"
	"hash/fnv"
	"sort"
	"strconv"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-meta/pkg/instameow"
	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
	"go.mau.fi/mautrix-meta/pkg/messagix/cookies"
	"go.mau.fi/mautrix-meta/pkg/messagix/types"
)

type EventSink interface { OnEvent(string, string) }

type conversationTarget struct { threadIGID string }

type Bridge struct {
	ctx context.Context
	cancel context.CancelFunc
	sink EventSink
	mu sync.Mutex
	client *instameow.Client
	cookies *cookies.Cookies
	viewerID string
	viewerFBID int64
	targets map[string]conversationTarget
}

func NewBridge(sessionJSON string, sink EventSink) (*Bridge, error) {
	ctx, cancel := context.WithCancel(context.Background())
	b := &Bridge{ctx: ctx, cancel: cancel, sink: sink, targets: make(map[string]conversationTarget)}
	if sessionJSON != "" { if err := b.SetCookies(sessionJSON); err != nil { cancel(); return nil, err } }
	return b, nil
}

func (b *Bridge) emit(kind string, value any) {
	if b.sink == nil { return }
	data, _ := json.Marshal(value)
	b.sink.OnEvent(kind, string(data))
}

func (b *Bridge) SetCookies(raw string) error {
	var values map[string]string
	if err := json.Unmarshal([]byte(raw), &values); err != nil { return fmt.Errorf("invalid cookies JSON: %w", err) }
	mapped := make(map[cookies.MetaCookieName]string, len(values))
	for key, value := range values { mapped[cookies.MetaCookieName(key)] = value }
	jar := &cookies.Cookies{Platform: types.Instagram}
	jar.UpdateValues(mapped)
	if missing := jar.GetMissingCookieNames(); len(missing) > 0 { return fmt.Errorf("missing required cookies: %v", missing) }
	b.mu.Lock(); defer b.mu.Unlock()
	b.cookies = jar
	b.client = instameow.NewClient(instameow.ClientParams{Cookies: jar, Log: zerolog.Nop(), EventHandler: b.handleEvent})
	return nil
}

func (b *Bridge) Connect() error {
	b.mu.Lock(); client := b.client; b.mu.Unlock()
	if client == nil { return fmt.Errorf("Instagram client is not configured") }
	viewer, mailbox, err := client.LoadIndex(b.ctx)
	if err != nil { return fmt.Errorf("load Instagram inbox: %w", err) }
	if viewer == nil || mailbox == nil { return fmt.Errorf("Instagram returned an incomplete inbox response") }
	b.viewerID = viewer.ID
	b.viewerFBID = viewer.GetFBID()
	b.mergeThreads(mailbox.ThreadsByFolder)
	go client.Connect(b.ctx)
	return nil
}

func (b *Bridge) Disconnect() { b.cancel(); b.mu.Lock(); if b.client != nil { b.client.Disconnect() }; b.mu.Unlock() }

func (b *Bridge) ExportSession() (string, error) {
	b.mu.Lock(); defer b.mu.Unlock()
	if b.cookies == nil { return "", nil }
	data, err := json.Marshal(b.cookies)
	return string(data), err
}

func (b *Bridge) ListConversations(_ int64) (string, error) {
	b.mu.Lock(); client := b.client; viewerFBID := b.viewerFBID; b.mu.Unlock()
	if client == nil { return "", fmt.Errorf("Instagram is not connected") }
	resp, err := client.GetMailbox(b.ctx)
	if err != nil { return "", err }
	if resp == nil || resp.Mailbox == nil { return "", fmt.Errorf("Instagram returned an incomplete mailbox response") }
	all := resp.Mailbox.ThreadsByFolder
	b.mergeThreads(all)
	cursor := all.PageInfo.EndCursor
	cutoff := time.Now().AddDate(-1, 0, 0)
	for all.PageInfo.HasNextPage {
		page, pageErr := client.PaginateMailbox(b.ctx, slidetypes.MakePaginateMailboxRequest(viewerFBID, cursor, "INBOX", nil))
		if pageErr != nil { return "", pageErr }
		if page == nil || page.Mailbox == nil { return "", fmt.Errorf("Instagram returned an incomplete mailbox page") }
		b.mergeThreads(page.Mailbox.ThreadsByFolder)
		for _, edge := range page.Mailbox.ThreadsByFolder.Edges { all.Edges = append(all.Edges, edge) }
		allOlderThanCutoff := len(page.Mailbox.ThreadsByFolder.Edges) > 0
		for _, edge := range page.Mailbox.ThreadsByFolder.Edges {
			t := edge.Node.AsIGDirectThread
			if t == nil || t.LastActivityTimestampMS.IsZero() || !t.LastActivityTimestampMS.Before(cutoff) { allOlderThanCutoff = false; break }
		}
		if allOlderThanCutoff { break }
		if page.Mailbox.ThreadsByFolder.PageInfo.EndCursor == cursor { break }
		cursor = page.Mailbox.ThreadsByFolder.PageInfo.EndCursor
		all.PageInfo = page.Mailbox.ThreadsByFolder.PageInfo
	}
	items := make([]map[string]any, 0, len(all.Edges))
	for _, edge := range all.Edges { if item := b.conversation(edge.Node.AsIGDirectThread); item != nil { items = append(items, item) } }
	data, _ := json.Marshal(map[string]any{"conversations": items})
	return string(data), nil
}

func (b *Bridge) mergeThreads(edges slidetypes.Edged[slidetypes.Node[slidetypes.WrappedThreadInfo]]) {
	for _, edge := range edges.Edges {
		t := edge.Node.AsIGDirectThread
		if t == nil { continue }
		b.targets[t.ThreadFBID] = conversationTarget{threadIGID: t.ID}
		if out := b.conversation(t); out != nil { b.emit("CONVERSATION", out) }
		b.emitMessages(t.ThreadFBID, t.SlideMessages)
	}
}

func (b *Bridge) conversation(t *slidetypes.ThreadInfo) map[string]any {
	if t == nil || t.ThreadFBID == "" { return nil }
	names, ids := []string{}, []string{}
	for _, user := range t.Users { if user != nil && user.ID != b.viewerID { names = append(names, first(user.FullName, user.Username)); ids = append(ids, user.ID) } }
	name := t.ThreadTitle
	if name == "" && len(names) > 0 { name = names[0] }
	var snippet string
	if t.SlideMessages != nil && len(t.SlideMessages.Edges) > 0 { snippet = t.SlideMessages.Edges[0].Node.TextBody }
	return map[string]any{"threadKey": t.ThreadFBID, "threadName": name, "snippet": snippet, "timestamp": t.LastActivityTimestampMS.UnixMilli(), "isGroup": t.IsGroup, "participantIds": ids, "participantNames": names}
}

func (b *Bridge) FetchMessages(threadID string, count int64, _ string) (string, error) {
	b.mu.Lock(); client := b.client; target := b.targets[threadID]; b.mu.Unlock()
	if client == nil { return "", fmt.Errorf("Instagram is not connected") }
	resp, err := client.GetThread(b.ctx, slidetypes.MakeGetThreadInfoRequest(threadID))
	if err != nil { return "", err }
	t := resp.ThreadInfo.AsIGDirectThread
	if t == nil { return `{"messages":[]}`, nil }
	for t.SlideMessages != nil && int64(len(t.SlideMessages.Edges)) < count && t.SlideMessages.PageInfo.HasNextPage {
		cursor := t.SlideMessages.PageInfo.EndCursor
		page, pageErr := client.PaginateMessages(b.ctx, &slidetypes.PaginateMessagesRequest{
			AfterCursor: &cursor, ThreadID: target.threadIGID, FirstN: 20, InitialMessagePageCount: 20,
		})
		if pageErr != nil { return "", fmt.Errorf("paginate Instagram messages: %w", pageErr) }
		messages := page.ThreadInfo.AsIGDirectThread.Messages
		if messages == nil || messages.PageInfo.EndCursor == cursor { break }
		t.SlideMessages.Edges = append(t.SlideMessages.Edges, messages.Edges...)
		t.SlideMessages.PageInfo = messages.PageInfo
	}
	items := b.messageMaps(threadID, t.SlideMessages, int(count))
	data, _ := json.Marshal(map[string]any{"messages": items})
	return string(data), nil
}

func (b *Bridge) emitMessages(threadID string, messages *slidetypes.SlideMessages) { for _, item := range b.messageMaps(threadID, messages, 100) { b.emit("MESSAGE", item) } }

func (b *Bridge) messageMaps(threadID string, messages *slidetypes.SlideMessages, limit int) []map[string]any {
	if messages == nil { return nil }
	items := append([]slidetypes.Node[*slidetypes.Message](nil), messages.Edges...)
	sort.Slice(items, func(i, j int) bool { return items[i].Node.TimestampMS.Before(items[j].Node.TimestampMS.Time) })
	start := 0; if len(items) > limit { start = len(items)-limit }
	out := make([]map[string]any, 0, len(items)-start)
	for _, edge := range items[start:] { m := edge.Node; if m == nil { continue }; senderID, senderName := strconv.FormatInt(m.SenderFBID,10), ""; if m.Sender != nil { senderID, senderName = first(m.Sender.IGID,m.Sender.ID), m.Sender.Name }; out = append(out, map[string]any{"threadKey": threadID, "messageId": first(m.MessageID,m.ID), "senderId": senderID, "senderName": senderName, "text": m.TextBody, "timestamp": m.TimestampMS.UnixMilli(), "isMe": senderID == b.viewerID}) }
	return out
}

func (b *Bridge) SendMessageIdempotent(threadID, text, transactionID string) (string, error) {
	b.mu.Lock(); client := b.client; target, ok := b.targets[threadID]; b.mu.Unlock()
	if client == nil { return "", fmt.Errorf("Instagram is not connected") }
	if !ok { return "", fmt.Errorf("Instagram conversation target not found") }
	otid := transactionID
	if _, err := strconv.ParseInt(otid, 10, 64); err != nil { h:=fnv.New64a(); _,_=h.Write([]byte(otid)); otid=strconv.FormatUint(h.Sum64()&0x7fffffffffffffff,10) }
	attribution := "igd_web_chat_tab:in_thread"
	resp, err := client.SendMessage(b.ctx, &slidetypes.SendTextRequest{
		IGThreadIGID: &target.threadIGID, OfflineThreadingID: otid,
		Text: slidetypes.SensitiveString{Value:text}, SendAttribution: &attribution,
	})
	if err != nil { return "", err }
	messageID := first(resp.Message.MessageID, resp.Message.ID)
	if messageID == "" { return "", fmt.Errorf("Instagram did not confirm the message") }
	data,_ := json.Marshal(map[string]string{"messageId":messageID,"conversationId":threadID})
	return string(data),nil
}

func (b *Bridge) handleEvent(_ context.Context, event slidetypes.ClientEvent) error {
	switch e := event.(type) {
	case *slidetypes.Connected:
		b.emit("READY", map[string]any{"userId": b.viewerID})
	case *slidetypes.AuthError:
		b.emit("LOGGED_OUT", map[string]any{"reason": e.Error.Error()})
	case *slidetypes.Disconnected:
		reason := "disconnected"; if e.Error != nil { reason = e.Error.Error() }
		b.emit("USER_ALERT", map[string]any{"code":"SOCKET_ERROR", "reason":reason, "failureCount":e.FailureCount})
	case *slidetypes.Delta:
		if messageEvent, ok := e.Data.(*slidetypes.NewMessageEvent); ok && messageEvent.Message != nil {
			m := messageEvent.Message
			senderID, senderName := strconv.FormatInt(m.SenderFBID, 10), ""
			if m.Sender != nil { senderID, senderName = first(m.Sender.IGID, m.Sender.ID), m.Sender.Name }
			b.emit("MESSAGE", map[string]any{"threadKey":e.ThreadIGID, "messageId":first(m.MessageID,m.ID), "senderId":senderID, "senderName":senderName, "text":m.TextBody, "timestamp":m.TimestampMS.UnixMilli(), "isMe":senderID == b.viewerID})
		}
	default:
		b.emit("INSTAGRAM_EVENT", map[string]any{"type":fmt.Sprintf("%T",event)})
	}
	return nil
}
func first(values ...string) string { for _, value := range values { if value != "" { return value } }; return "" }

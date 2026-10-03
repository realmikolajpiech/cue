package telegrambridge

import (
	"context"
	"encoding/json"
	"fmt"
	"hash/fnv"
	"time"

	"github.com/gotd/td/tg"
)

// ListActiveConversations is Omni's unbounded dialog discovery entrypoint. It
// follows Telegram pagination until exhaustion or the one-year boundary and
// retains records whose top-message timestamp cannot be resolved.
func (b *Bridge) ListActiveConversations() string {
	return b.call(90*time.Second, func(ctx context.Context) string {
		const pageSize = 100
		cutoff := time.Now().AddDate(-1, 0, 0).UnixMilli()
		all := make([]outConvo, 0, pageSize)
		offsetPeer := tg.InputPeerClass(&tg.InputPeerEmpty{})
		offsetID, offsetDate := 0, 0
		for {
			res, err := b.client.API().MessagesGetDialogs(ctx, &tg.MessagesGetDialogsRequest{
				OffsetPeer: offsetPeer,
				OffsetID:   offsetID,
				OffsetDate: offsetDate,
				Limit:      pageSize,
			})
			if err != nil {
				if len(all) == 0 { return emptyConvos("getDialogs: " + err.Error()) }
				break
			}
			var dialogs []tg.DialogClass
			var messages []tg.MessageClass
			var chats []tg.ChatClass
			var users []tg.UserClass
			switch page := res.(type) {
			case *tg.MessagesDialogs:
				dialogs, messages, chats, users = page.Dialogs, page.Messages, page.Chats, page.Users
			case *tg.MessagesDialogsSlice:
				dialogs, messages, chats, users = page.Dialogs, page.Messages, page.Chats, page.Users
			default:
				return emptyConvos("getDialogs: unexpected response type")
			}
			converted := b.dialogsToConvos(dialogs, messages, chats, users, false)
			allTimestampedOld := len(converted) > 0
			for _, conversation := range converted {
				if conversation.Timestamp == 0 || conversation.Timestamp >= cutoff {
					all = append(all, conversation)
					allTimestampedOld = false
				}
			}
			if len(dialogs) < pageSize || allTimestampedOld { break }
			last, ok := dialogs[len(dialogs)-1].(*tg.Dialog)
			if !ok { break }
			peer, ok := b.inputPeerFor(peerToConvoID(last.Peer))
			if !ok { break }
			offsetPeer, offsetID, offsetDate = peer, last.TopMessage, 0
			for _, message := range messages {
				if item, ok := message.(*tg.Message); ok && item.ID == last.TopMessage {
					offsetDate = item.Date
					break
				}
			}
		}
		encoded, _ := json.Marshal(outConvos{Conversations: all})
		return string(encoded)
	})
}

// SendMessageIdempotent maps a stable local transaction id to MTProto's
// random_id. Telegram deduplicates retries carrying the same random_id.
func (b *Bridge) SendMessageIdempotent(conversationID, text, transactionID string) string {
	return b.call(40*time.Second, func(ctx context.Context) string {
		peer, ok := b.inputPeerFor(conversationID)
		if !ok { return errJSON("unknown conversation (list first)") }
		h := fnv.New64a()
		_, _ = h.Write([]byte(transactionID))
		randomID := int64(h.Sum64() & 0x7fffffffffffffff)
		if randomID == 0 { randomID = 1 }
		updates, err := b.client.API().MessagesSendMessage(ctx, &tg.MessagesSendMessageRequest{
			Peer: peer, Message: text, RandomID: randomID,
		})
		if err != nil { return errJSON("sendMessage: " + err.Error()) }
		messageID := extractSentID(updates)
		if messageID == 0 { return errJSON("server response did not contain a message id") }
		encoded, _ := json.Marshal(map[string]string{
			"messageId": fmt.Sprintf("%d", messageID),
			"conversationId": conversationID,
		})
		return string(encoded)
	})
}

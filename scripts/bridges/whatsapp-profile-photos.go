package whatsappbridge

import (
	"context"
	"errors"
	"fmt"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/types"
)

func (b *Bridge) GetProfilePictureURL(conversationID string) (string, error) {
	jid, err := types.ParseJID(conversationID)
	if err != nil {
		return "", err
	}
	if jid.Server != types.DefaultUserServer && jid.Server != types.HiddenUserServer {
		return "", fmt.Errorf("profile photos require a private chat")
	}
	if b.client == nil || !b.client.IsConnected() {
		return "", fmt.Errorf("WhatsApp not connected")
	}
	ctx, cancel := context.WithTimeout(b.ctx, 8*time.Second)
	defer cancel()
	photo, err := b.client.GetProfilePictureInfo(ctx, jid, &whatsmeow.GetProfilePictureParams{Preview: true})
	if errors.Is(err, whatsmeow.ErrProfilePictureNotSet) || errors.Is(err, whatsmeow.ErrProfilePictureUnauthorized) {
		return "", nil
	}
	if err != nil {
		return "", err
	}
	if photo == nil {
		return "", nil
	}
	return photo.URL, nil
}

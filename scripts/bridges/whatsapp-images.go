package whatsappbridge

import (
 "context"
 "encoding/base64"
 "fmt"
 "time"
 waE2E "go.mau.fi/whatsmeow/proto/waE2E"
 "google.golang.org/protobuf/proto"
)

func (b *Bridge) DownloadImageForCue(mediaID string) ([]byte, error) {
 b.imgMu.Lock(); encoded := b.imgStore[mediaID]; b.imgMu.Unlock()
 if encoded == "" { return nil, fmt.Errorf("image unavailable") }
 data, err := base64.StdEncoding.DecodeString(encoded)
 if err != nil { return nil, err }
 image := &waE2E.ImageMessage{}
 if err := proto.Unmarshal(data, image); err != nil { return nil, err }
 b.mu.Lock(); client := b.client; b.mu.Unlock()
 if client == nil { return nil, fmt.Errorf("image connection unavailable") }
 ctx, cancel := context.WithTimeout(b.ctx, 25*time.Second)
 defer cancel()
 raw, err := client.Download(ctx, image)
 if err != nil { return nil, err }
 if len(raw) > 8*1024*1024 { return nil, fmt.Errorf("image too large") }
 return raw, nil
}

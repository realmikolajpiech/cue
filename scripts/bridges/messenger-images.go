package fbmessagebridge

import (
 "context"
 "fmt"
 "io"
 "net/http"
 "strings"
 "time"
 "go.mau.fi/whatsmeow"
)

// DownloadImageForCue returns decrypted image bytes, never encryption keys or signed URLs.
func (b *Bridge) DownloadImageForCue(mediaID string) ([]byte, error) {
 ctx, cancel := context.WithTimeout(b.ctx, 25*time.Second)
 defer cancel()
 b.e2eeImgMu.Lock()
 xport := b.e2eeImgXport[mediaID]
 b.e2eeImgMu.Unlock()
 if xport == nil { xport = b.loadImgXport(mediaID) }
 if xport != nil {
  b.e2eeMu.Lock(); client := b.e2eeClient; b.e2eeMu.Unlock()
  if client == nil { return nil, fmt.Errorf("image connection unavailable") }
  raw, err := client.DownloadFB(ctx, xport, whatsmeow.MediaImage)
  if err != nil { return nil, err }
  if len(raw) > 8*1024*1024 { return nil, fmt.Errorf("image too large") }
  return raw, nil
 }
 b.imgMu.Lock(); im, ok := b.imgURLs[mediaID]; b.imgMu.Unlock()
 if !ok || !strings.HasPrefix(im.URL, "https://") { return nil, fmt.Errorf("image unavailable") }
 req, err := http.NewRequestWithContext(ctx, http.MethodGet, im.URL, nil)
 if err != nil { return nil, err }
 req.Header.Set("User-Agent", "Mozilla/5.0")
 response, err := http.DefaultClient.Do(req)
 if err != nil { return nil, err }
 defer response.Body.Close()
 if response.StatusCode != http.StatusOK { return nil, fmt.Errorf("image download failed") }
 raw, err := io.ReadAll(io.LimitReader(response.Body, 8*1024*1024+1))
 if len(raw) > 8*1024*1024 { return nil, fmt.Errorf("image too large") }
 return raw, err
}

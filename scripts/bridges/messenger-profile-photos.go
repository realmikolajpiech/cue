package fbmessagebridge

import (
	"context"
	"encoding/base64"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"

	"go.mau.fi/mautrix-meta/pkg/messagix/socket"
	"go.mau.fi/mautrix-meta/pkg/messagix/table"
 "go.mau.fi/mautrix-meta/pkg/messagix/useragent"
)

type profilePhoto struct {
	url     string
	expires int64
}

// Contact metadata can precede or follow the encrypted thread mapping. Store
// photos by contact, then resolve the thread at lookup time.
func (b *Bridge) harvestProfilePhotos(tbl *table.LSTable) {
	for _, row := range tbl.LSDeleteThenInsertThread {
		if row != nil && row.ThreadType.IsOneToOne() && (row.ThreadPictureUrl != "" || row.ThreadPictureUrlFallback != "") {
			b.threadProfilePhotos.Store(row.ThreadKey, profilePhoto{photoURL(row.ThreadPictureUrl, row.ThreadPictureUrlFallback), row.ThreadPictureUrlExpirationTimestampMs})
		}
	}
	for _, row := range tbl.LSUpdateOrInsertThread {
		if row != nil && row.ThreadType.IsOneToOne() && (row.ThreadPictureUrl != "" || row.ThreadPictureUrlFallback != "") {
			b.threadProfilePhotos.Store(row.GetThreadKey(), profilePhoto{photoURL(row.ThreadPictureUrl, row.ThreadPictureUrlFallback), row.ThreadPictureUrlExpirationTimestampMs})
		}
	}
	for _, row := range tbl.LSVerifyContactRowExists {
		if row != nil && (row.ProfilePictureUrl != "" || row.ProfilePictureFallbackUrl != "") {
			b.profilePhotos.Store(row.ContactId, profilePhoto{photoURL(row.ProfilePictureUrl, row.ProfilePictureFallbackUrl), row.ProfilePictureUrlExpirationTimestampMs})
		}
	}
	for _, row := range tbl.LSDeleteThenInsertContact {
		if row != nil {
			expires := row.ProfilePictureUrlExpirationTimestampMs
			if row.ProfilePictureLargeUrl != "" {
				expires = row.ProfilePictureLargeUrlExpirationTimestampMs
			}
			b.profilePhotos.Store(row.Id, profilePhoto{photoURL(row.GetAvatarURL(), row.ProfilePictureFallbackUrl), expires})
		}
	}
}

// GetProfilePictureURL returns the authenticated contact photo URL. A known
// empty photo is distinct from metadata that has not arrived yet (an error).
func (b *Bridge) GetProfilePictureURL(threadKey, contactID string) (string, error) {
	tk, err := parseInt64(threadKey)
	if err != nil {
		return "", err
	}
	id := tk
	if mapped, ok := b.conversationContactID(tk); ok {
		id = mapped
	}
	if contactID != "" {
		id, err = parseInt64(contactID)
		if err != nil {
			return "", err
		}
	}
	lookup := func() (string, bool) {
 valid := func(value any) bool {
 expires := value.(profilePhoto).expires
 if expires > 0 && expires < 100000000000 { expires *= 1000 }
 return expires == 0 || expires > time.Now().UnixMilli()
 }

		if value, ok := b.profilePhotos.Load(id); ok && valid(value) {
			return value.(profilePhoto).url, true
		}
		if value, ok := b.threadProfilePhotos.Load(tk); ok && valid(value) {
			return value.(profilePhoto).url, true
		}
		return "", false
	}
	if photo, ok := lookup(); ok {
		if resolved, err := b.resolveProfilePhoto(photo); err == nil {
			return resolved, nil
		}
	}
	b.mu.Lock()
	client := b.client
	b.mu.Unlock()
	if client == nil {
		return "", fmt.Errorf("Messenger not connected")
	}
	ctx, cancel := context.WithTimeout(b.ctx, 8*time.Second)
	defer cancel()
	tbl, err := client.ExecuteTasks(ctx, &socket.GetContactsFullTask{ContactID: id})
	if err != nil {
		return "", err
	}
	if tbl != nil {
		b.harvestProfilePhotos(tbl)
	}
	if url, ok := lookup(); ok {
		return b.resolveProfilePhoto(url)
	}
	contacts, threads := 0, 0
	b.profilePhotos.Range(func(_, _ any) bool { contacts++; return true })
	b.threadProfilePhotos.Range(func(_, _ any) bool { threads++; return true })
	return "", fmt.Errorf("contact photo metadata pending (contacts=%d threads=%d)", contacts, threads)
}

func photoURL(primary, fallback string) string {
	raw := strings.TrimSpace(primary)
	if raw == "" {
		raw = strings.TrimSpace(fallback)
	}
	if strings.HasPrefix(raw, "//") {
		return "https:" + raw
	}
	if strings.HasPrefix(raw, "/") {
		return "https://www.facebook.com" + raw
	}
	return raw
}

// Facebook fallback endpoints require the existing signed-in session. Resolve
// them here; cookies never enter JS or get forwarded to the image CDN.
func (b *Bridge) resolveProfilePhoto(raw string) (string, error) {
	parsed, err := url.Parse(raw)
	if err != nil {
		return "", fmt.Errorf("invalid photo URL")
	}
	b.mu.Lock()
	active := b.client
	b.mu.Unlock()
	if raw == "" || active == nil {
		return raw, nil
	}
	if parsed.Scheme != "https" { return "", fmt.Errorf("invalid photo URL") }
	ctx, cancel := context.WithTimeout(b.ctx, 8*time.Second)
	defer cancel()
	client := *active.GetHTTP().HTTP
	client.CheckRedirect = func(req *http.Request, via []*http.Request) error {
 if len(via) >= 4 || req.URL.Scheme != "https" { return fmt.Errorf("invalid photo redirect") }; req.Header.Del("Cookie"); return nil
 }
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, raw, nil)
	if err != nil {
		return "", fmt.Errorf("invalid photo request")
	}
	req.Header = http.Header{}
 if parsed.Hostname() == "www.facebook.com" {
 req.Header.Set("Cookie", active.GetHTTP().BuildHeaders(true, false).Get("Cookie"))
 }
 req.Header.Set("sec-ch-ua", useragent.SecCHUserAgent)
 req.Header.Set("sec-ch-ua-platform", useragent.SecCHPlatform)
	req.Header.Set("Accept", "image/avif,image/webp,*/*")
 req.Header.Del("Referer")
 req.Header.Set("Sec-Fetch-Dest", "image")
 req.Header.Set("Sec-Fetch-Mode", "no-cors")
 req.Header.Set("Sec-Fetch-Site", "cross-site")
 req.Header.Set("User-Agent", useragent.UserAgent)
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("authenticated photo request failed")
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 && resp.StatusCode < 400 {
		target, err := resp.Location()
		if err != nil || target.Scheme != "https" {
			return "", fmt.Errorf("invalid photo redirect")
		}
		// Native code follows the CDN URL with no session headers.
		return target.String(), nil
	}
	if resp.StatusCode != 200 || !strings.HasPrefix(resp.Header.Get("Content-Type"), "image/") {
		keys := make([]string, 0)
		for key := range parsed.Query() {
			keys = append(keys, key)
		}
		sort.Strings(keys)
		kind := "other"
		for _, prefix := range []string{"/profile/picture", "/rsrc.php", "/images/", "/messenger_media/", "/messaging/lightspeed/media_fallback/"} {
			if strings.HasPrefix(parsed.Path, prefix) {
				kind = prefix
			}
		}
		return "", fmt.Errorf("authenticated photo request failed (%d, path=%s, keys=%s)", resp.StatusCode, kind, strings.Join(keys, ","))
	}
	bytes, err := io.ReadAll(io.LimitReader(resp.Body, 2*1024*1024+1))
	if err != nil || len(bytes) > 2*1024*1024 {
		return "", fmt.Errorf("invalid photo response")
	}
	return "data:image/jpeg;base64," + base64.StdEncoding.EncodeToString(bytes), nil
}

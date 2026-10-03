package fbmessagebridge

import (
	"go.mau.fi/mautrix-meta/pkg/messagix/table"
	"testing"
	"time"
)

func TestProfilePhotoUsesContactMapping(t *testing.T) {
	b := &Bridge{}
	b.harvestContacts(&table.LSTable{LSVerifyContactRowExists: []*table.LSVerifyContactRowExists{{ContactId: 42, ProfilePictureUrl: "https://example.com/avatar.jpg"}}})
	b.conversationNameState().contactByID.Store(int64(99), int64(42))
	url, err := b.GetProfilePictureURL("99", "")
	if err != nil || url != "https://example.com/avatar.jpg" {
		t.Fatalf("url=%q err=%v", url, err)
	}
}

func TestProfilePhotoRemovalAndExpiration(t *testing.T) {
	b := &Bridge{}
	b.harvestContacts(&table.LSTable{LSDeleteThenInsertContact: []*table.LSDeleteThenInsertContact{{Id: 42, ProfilePictureUrl: "https://example.com/expired", ProfilePictureUrlExpirationTimestampMs: time.Now().Add(-time.Hour).UnixMilli()}}})
	if _, err := b.GetProfilePictureURL("42", ""); err == nil {
		t.Fatal("expired URL must not be returned")
	}
	b.harvestContacts(&table.LSTable{LSDeleteThenInsertContact: []*table.LSDeleteThenInsertContact{{Id: 42}}})
	if url, err := b.GetProfilePictureURL("42", ""); err != nil || url != "" {
		t.Fatalf("removed photo: %q %v", url, err)
	}
}

func TestSparseContactVerificationDoesNotErasePhoto(t *testing.T) {
	b := &Bridge{}
	b.harvestContacts(&table.LSTable{LSDeleteThenInsertContact: []*table.LSDeleteThenInsertContact{{Id: 42, ProfilePictureUrl: "https://example.com/photo"}}})
	b.harvestContacts(&table.LSTable{LSVerifyContactRowExists: []*table.LSVerifyContactRowExists{{ContactId: 42}}})
	if url, err := b.GetProfilePictureURL("42", ""); err != nil || url != "https://example.com/photo" {
		t.Fatalf("sparse verification erased photo: %q %v", url, err)
	}
}

func TestPrivateThreadPhotoWithoutContactMetadata(t *testing.T) {
	b := &Bridge{}
	b.harvestContacts(&table.LSTable{LSDeleteThenInsertThread: []*table.LSDeleteThenInsertThread{
		{ThreadKey: 99, ThreadType: table.ENCRYPTED_OVER_WA_ONE_TO_ONE, ThreadPictureUrl: "https://example.com/thread-photo"},
		{ThreadKey: 100, ThreadType: table.GROUP_THREAD, ThreadPictureUrl: "https://example.com/group-photo"},
	}})
	if url, err := b.GetProfilePictureURL("99", "42"); err != nil || url != "https://example.com/thread-photo" {
		t.Fatalf("thread photo %q %v", url, err)
	}
	if _, err := b.GetProfilePictureURL("100", ""); err == nil {
		t.Fatal("group photo must not be used as a person photo")
	}
}

func TestRelativeFallbackPhotoURL(t *testing.T) {
	b := &Bridge{}
	b.harvestContacts(&table.LSTable{LSVerifyContactRowExists: []*table.LSVerifyContactRowExists{{ContactId: 42, ProfilePictureFallbackUrl: "/profile/picture/?profile_id=42"}}})
	if url, err := b.GetProfilePictureURL("42", ""); err != nil || url != "https://www.facebook.com/profile/picture/?profile_id=42" {
		t.Fatalf("relative fallback %q %v", url, err)
	}
	if url := photoURL("//cdn.example.com/photo", ""); url != "https://cdn.example.com/photo" {
		t.Fatal(url)
	}
}

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
work_dir="$(mktemp -d /tmp/omni-messaging-bridges.XXXXXX)"
trap 'rm -rf "$work_dir"' EXIT

mirror_repo="https://github.com/killdano/mirrormsg.git"
mirror_commit="6c748af0b69867928638f246f1cd715cb1d77cfb"
mobile_version="v0.0.0-20260611195102-4dd8f1dbf5d2"

if ! command -v go >/dev/null 2>&1; then
    echo "Go 1.26 or newer is required. Install it from https://go.dev/dl/" >&2
    exit 1
fi

export GOBIN="$work_dir/bin"
export PATH="$GOBIN:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

git clone --filter=blob:none "$mirror_repo" "$work_dir/mirrormsg"
git -C "$work_dir/mirrormsg" checkout "$mirror_commit"
git -C "$work_dir/mirrormsg" apply "$repo_root/scripts/bridges/messenger-bridge-minimal.patch"
git -C "$work_dir/mirrormsg" apply "$repo_root/scripts/bridges/messenger-bridge-names.patch"
git -C "$work_dir/mirrormsg" apply "$repo_root/scripts/bridges/whatsapp-bridge-reliability.patch"
git -C "$work_dir/mirrormsg" apply "$repo_root/scripts/bridges/whatsapp-bridge-names.patch"
git -C "$work_dir/mirrormsg" apply "$repo_root/scripts/bridges/telegram-bridge-reliability.patch"
cp "$repo_root/scripts/bridges/telegram-bridge-omni.go" "$work_dir/mirrormsg/combined-go/telegrambridge/omni.go"
mkdir -p "$work_dir/mirrormsg/combined-go/instagrambridge"
cp "$repo_root/scripts/bridges/instagram-bridge-omni.go" "$work_dir/mirrormsg/combined-go/instagrambridge/bridge.go"

telegram_api_id="${TELEGRAM_API_ID:-}"
telegram_api_hash="${TELEGRAM_API_HASH:-}"
if [[ -z "$telegram_api_id" && -f "$repo_root/local.properties" ]]; then
    telegram_api_id="$(sed -n 's/^TELEGRAM_API_ID=//p' "$repo_root/local.properties" | tail -n 1)"
    telegram_api_hash="$(sed -n 's/^TELEGRAM_API_HASH=//p' "$repo_root/local.properties" | tail -n 1)"
fi
if [[ -n "$telegram_api_id" && -n "$telegram_api_hash" ]]; then
    [[ "$telegram_api_id" =~ ^[0-9]+$ ]] || { echo "TELEGRAM_API_ID must be numeric" >&2; exit 1; }
    [[ "$telegram_api_hash" =~ ^[0-9A-Fa-f]+$ ]] || { echo "TELEGRAM_API_HASH must be hexadecimal" >&2; exit 1; }
    printf 'package telegrambridge\n\nfunc init() { apiID = %s; apiHash = "%s" }\n' \
        "$telegram_api_id" "$telegram_api_hash" \
        > "$work_dir/mirrormsg/combined-go/telegrambridge/credentials.local.go"
else
    echo "Telegram API credentials not supplied; bridge will build but Telegram login will report not configured." >&2
fi
cp "$repo_root/scripts/bridges/messenger-images.go" "$work_dir/mirrormsg/combined-go/fbmessagebridge/cue_images.go"
cp "$repo_root/scripts/bridges/whatsapp-images.go" "$work_dir/mirrormsg/combined-go/whatsappbridge/cue_images.go"

go install "golang.org/x/mobile/cmd/gomobile@$mobile_version"
go install "golang.org/x/mobile/cmd/gobind@$mobile_version"

mkdir -p "$repo_root/modules/subtext/android/libs"
(
    cd "$work_dir/mirrormsg/combined-go"
    # Meta now returns LSExecuteFirstBlockForSyncTransactionV4. v0.2604.0 did
    # not advance the sync cursor for it, so every socket connection timed out
    # while waiting for database 1. v0.2609.0 adds that protocol variant.
    go mod edit -go=1.26.0
    go mod edit -require=go.mau.fi/mautrix-meta@v0.2609.0
    go mod edit -replace=github.com/imroc/req/v3=github.com/beeper/req/v3@v3.0.0-20260808092153-100cef0a2fbd
    go mod tidy
    # v0.2609 renamed the messagix lifecycle/table event types.
    perl -0pi -e 's/messagix\.Event_Ready/messagix.ConnectedEvent/g; s/messagix\.Event_PublishResponse/table.LSTable/g; s/b\.emitTableDeltas\(e\.Table, true\)/b.emitTableDeltas(e, true)/g; s/messagix\.Event_SocketError/messagix.TransientDisconnectEvent/g; s/messagix\.Event_PermanentError/messagix.PermanentErrorEvent/g; s/messagix\.Event_Reconnected/messagix.ReconnectedEvent/g; s/ fmt\.Sprintf\(`\{"fbid":%d,"newSession":%t\}`, b\.fbid, e\.IsNewSession\)/ fmt.Sprintf(`{"fbid":%d,"newSession":false}`, b.fbid)/g' fbmessagebridge/bridge_fb.go
    # E2EE cutover threads use updateOrInsertThread instead of the legacy
    # deleteThenInsertThread row. Fan those rows into the same conversation
    # stream so the Kotlin cache can resolve private 1:1 chats.
    perl -0pi -e 's#\n\t}\n}\n\n// harvestContacts#\n\t}\n\tfor _, t := range tbl.LSUpdateOrInsertThread {\n\t\tname := b.resolveConversationName(t.GetThreadKey(), t.GetThreadName())\n\t\tb.taMu.Lock()\n\t\tif b.threadActivity != nil { b.threadActivity[t.GetThreadKey()] = t.LastActivityTimestampMs }\n\t\tb.taMu.Unlock()\n\t\tb.emit("CONVERSATION", map[string]any{"threadKey": t.GetThreadKey(), "threadName": name, "snippet": t.Snippet, "timestamp": t.LastActivityTimestampMs})\n\t}\n}\n\n// harvestContacts#' fbmessagebridge/bridge_fb.go
    perl -0pi -e 's#b\.harvestContacts\(tbl\)#b.harvestContacts(tbl)\n\tb.harvestThreadContactMappings(tbl)#g; s#b\.lookupContact\(t\.ThreadKey\)#b.resolveConversationName(t.ThreadKey, "")#g; s#(\t\tb\.emit\("CONVERSATION", out\))#\t\tb.rememberConversationMeta(t.ThreadKey, t.Snippet, t.LastActivityTimestampMs)\n$1#; s#(\t\tb\.emit\("CONVERSATION", map\[string\]any\{"threadKey": t\.GetThreadKey\(\), "threadName": name, "snippet": t\.Snippet, "timestamp": t\.LastActivityTimestampMs\}\))#\t\tb.rememberConversationMeta(t.GetThreadKey(), t.Snippet, t.LastActivityTimestampMs)\n$1#; s#(\t\tb\.contactNames\[id\] = n\n\t\tb\.contactMu\.Unlock\(\))#$1\n\t\tb.reemitNamedConversation(id, n)#' fbmessagebridge/bridge_fb.go
    git -C "$work_dir/mirrormsg" apply "$repo_root/scripts/bridges/messenger-bridge-reliability.patch"
    cp "$repo_root/scripts/bridges/messenger-profile-photos.go" fbmessagebridge/profile_photos.go
    cp "$repo_root/scripts/bridges/whatsapp-profile-photos.go" whatsappbridge/profile_photos.go
    cp "$repo_root/scripts/bridges/messenger-profile-photos_test.go" fbmessagebridge/profile_photos_test.go
    perl -0pi -e 's/contactNames map\[int64\]string/contactNames map[int64]string\n\tprofilePhotos sync.Map\n\tthreadProfilePhotos sync.Map/; s/func \(b \*Bridge\) harvestContacts\(tbl \*table.LSTable\) \{/func (b *Bridge) harvestContacts(tbl *table.LSTable) {\n\tb.harvestProfilePhotos(tbl)/' fbmessagebridge/bridge_fb.go
    gofmt -w fbmessagebridge/bridge_fb.go fbmessagebridge/conversation_names.go fbmessagebridge/profile_photos*.go whatsappbridge/profile_photos.go instagrambridge/bridge.go
    if [[ "${BRIDGE_PLATFORM:-android}" == "android" ]]; then
        go test ./fbmessagebridge ./whatsappbridge ./telegrambridge ./instagrambridge
    fi
    if [[ "${BRIDGE_PLATFORM:-android}" == "ios" ]]; then
        mkdir -p "$repo_root/modules/subtext/ios/Frameworks"
        "$GOBIN/gomobile" bind -target ios/arm64,iossimulator/arm64 -iosversion 16.4 \
            -ldflags="-s -w" -o "$repo_root/modules/subtext/ios/Frameworks/Messagebridges.xcframework" \
            fi.mirrormsg/fbmessagebridge fi.mirrormsg/whatsappbridge
    else
    "$GOBIN/gomobile" bind \
        -target android/arm64 \
        -androidapi 24 \
        -javapkg fi.mirrormsg \
        -o "$repo_root/modules/subtext/android/libs/messagebridges.aar" \
        fi.mirrormsg/fbmessagebridge fi.mirrormsg/whatsappbridge fi.mirrormsg/telegrambridge fi.mirrormsg/instagrambridge
    fi
)

echo "Built ${BRIDGE_PLATFORM:-android} messaging bridges"

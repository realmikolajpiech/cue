# Subtext on-device messaging

The Kotlin adapters and `android/libs/messagebridges.aar` are adapted/copied from
[realmikolajpiech/arie](https://github.com/realmikolajpiech/arie), commit
`ff217daadf321eca73e5245d6a85e96b059f8e8d`.

The AAR embeds the MirrorMsg Go wrappers from
[killdano/mirrormsg](https://github.com/killdano/mirrormsg), commit
`6c748af0b69867928638f246f1cd715cb1d77cfb`, with Arie's patches and
`mautrix-meta v0.2609.0`. Its Messenger implementation uses
[mautrix-meta/messagix](https://github.com/mautrix/meta) and its WhatsApp
implementation uses [whatsmeow](https://github.com/tulir/whatsmeow).
This is the combined Arie artifact; it also contains unused Instagram/Telegram
bindings. Subtext exposes only Messenger and WhatsApp.

Rebuild sources and patches: `scripts/bridges/build-messaging-bridges.sh`.
The checked-in Java bindings source archive is alongside the AAR.
The current binary supports Android **arm64-v8a** only. It is not a Matrix server
and does not require a separate host.

MirrorMsg and mautrix-meta are AGPL-3.0; whatsmeow is MPL-2.0. Preserve upstream
notices and supply corresponding sources when distributing derived binaries.
See Arie's `docs/messenger-on-device.md` for provenance and licensing notes.

These are experimental private-protocol integrations. Real login, available
history and reconnect behavior depend on the service and account. No automatic
sending is exposed by Subtext's Expo module or keyboard.

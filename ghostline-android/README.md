# Ghostline Android

Android conversion of Ghostline 0.4.1.

## Build on GitHub

1. Create a new GitHub repository.
2. Upload the contents of this directory.
3. Open **Actions → Build Ghostline APK → Run workflow**.
4. Download the `ghostline-android-debug` artifact.

The workflow also runs on push and pull request.

## Android implementation

This build is intentionally Android-native. The Windows-only WinDivert, GoodbyeDPI,
zapret2, Windows DNS adapter management, Windows service management, and Wails desktop
shell are not bundled into the APK.

The Android backend uses `VpnService` only for the virtual DNS endpoint
`10.0.0.2`; normal application traffic is not routed through the VPN. DNS queries
are forwarded to Cloudflare 1.1.1.1 by the service.

## Project

- `app/src/main/java/.../MainActivity.kt` — UI and VPN permission flow.
- `GhostlineVpnService.kt` — foreground DNS VPN service.
- `DnsPacket.kt` — IPv4/UDP DNS packet handling.
- `.github/workflows/build-apk.yml` — reproducible GitHub Actions build.

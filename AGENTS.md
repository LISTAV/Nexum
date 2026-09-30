# Nexum Workspace Instructions & Memory

## Project Overview
- **Application:** Nexum (`com.kairav.nexum`)
- **Repository:** https://github.com/LISTAV/Nexum
- **F-Droid Recipe:** `metadata/com.kairav.nexum.yml`
- **F-Droid MR:** https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48799

## F-Droid Reproducible Builds & Signing Rules

### 1. Release Keystore & Fingerprint
- Keystore: `D:\Android\PROJECT\SMSLOG\nexum-release.jks`
- SHA-256 Fingerprint: `06be24a157849f871074cc2f386b33367cc6bff71e1cdfc1183e9f5841291fbb`
- Always verify `AllowedAPKSigningKeys` in `metadata/com.kairav.nexum.yml` matches this fingerprint.

### 2. Mandatory Signing Command
Whenever signing release APKs for GitHub / F-Droid, **always** use:
```powershell
& "C:\Users\KAIRAV\AppData\Local\Android\Sdk\build-tools\35.0.1\apksigner.bat" sign `
    --ks "D:\Android\PROJECT\SMSLOG\nexum-release.jks" `
    --v1-signing-enabled false `
    --v2-signing-enabled true `
    --alignment-preserved `
    --out "D:\Android\PROJECT\SMSLOG\nexum-vX.X.apk" `
    "D:\Android\PROJECT\SMSLOG\app\build\outputs\apk\release\app-release-unsigned.apk"
```
**CRITICAL:** `--alignment-preserved` and `--v1-signing-enabled false` are mandatory to prevent byte offset shifts that break F-Droid signature-copy verification.

### 3. Metadata Formatting
- File `metadata/com.kairav.nexum.yml` MUST use Unix LF (`\n`) line endings (never CRLF).
- Field order must place `AllowedAPKSigningKeys:` AFTER `Builds:` and before `AutoUpdateMode:`.
- Detailed reference guide: [docs/FDROID_PIPELINE_MEMORY.md](docs/FDROID_PIPELINE_MEMORY.md).

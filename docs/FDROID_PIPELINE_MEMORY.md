# 📜 F-Droid Reproducible Builds & Pipeline Memory

> **Project:** Nexum (`com.kairav.nexum`)  
> **Repository:** [LISTAV/Nexum](https://github.com/LISTAV/Nexum)  
> **F-Droid MR:** [!48799 in fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48799)  
> **Last Verified Passing Pipeline:** [#2898175920](https://gitlab.com/listav3/fdroid-data/-/pipelines/2898175920)

---

## 🔑 Critical Cryptographic Keys & Hashes

- **Keystore Location:** `D:\Android\PROJECT\SMSLOG\nexum-release.jks`
- **Key Alias:** `nexum` (or custom configured)
- **Certificate DN:** `CN=dutta, OU=himangshu, O=himangshu, L=assam, ST=assam, C=IN`
- **Certificate SHA-256 Fingerprint (Lowercase Hex):**
  ```text
  06be24a157849f871074cc2f386b33367cc6bff71e1cdfc1183e9f5841291fbb
  ```
- **AllowedAPKSigningKeys Field in `metadata/com.kairav.nexum.yml`:**
  ```yaml
  AllowedAPKSigningKeys: 06be24a157849f871074cc2f386b33367cc6bff71e1cdfc1183e9f5841291fbb
  ```

---

## ⚡ The Golden Signing Command

To guarantee a **100% byte-for-byte reproducible build match** with F-Droid's builder, **always** use this command:

```powershell
& "C:\Users\KAIRAV\AppData\Local\Android\Sdk\build-tools\35.0.1\apksigner.bat" sign `
    --ks "D:\Android\PROJECT\SMSLOG\nexum-release.jks" `
    --v1-signing-enabled false `
    --v2-signing-enabled true `
    --alignment-preserved `
    --out "D:\Android\PROJECT\SMSLOG\nexum-v1.0.apk" `
    "D:\Android\PROJECT\SMSLOG\app\build\outputs\apk\release\app-release-unsigned.apk"
```

### Why each flag is mandatory:
1. **`--v1-signing-enabled false`**:
   - Disables legacy JAR signing (`MANIFEST.MF`, `NEXUM.SF`, `NEXUM.RSA`).
   - Prevents injecting extra files into the ZIP archive. Modern Android (`minSdk 30`) exclusively uses v2/v3 signing.
2. **`--v2-signing-enabled true`**:
   - Enables standard APK Signature Scheme v2/v3 block appended outside the central directory.
3. **`--alignment-preserved`**:
   - **CRITICAL**: In Android SDK Build-Tools 35+, `apksigner` tries to re-align zip entries for Android 15 page alignment by adding 4 bytes of padding. This flag tells `apksigner` to preserve Gradle's internal alignment, avoiding a 4-byte offset shift in `assets/dexopt/baseline.prof`.
4. **Input File**: Must be `app-release-unsigned.apk` directly from Gradle output.

---

## 🛠️ Step-by-Step Release Workflow (For Future Updates)

When releasing `v1.1`, `v1.2`, etc.:

### 1. Update Version Numbers
In `app/build.gradle.kts`:
```kotlin
versionCode = 2
versionName = "1.1"
```
In `metadata/com.kairav.nexum.yml`:
```yaml
CurrentVersion: '1.1'
CurrentVersionCode: 2
```

### 2. Clean & Compile Release APK
```powershell
cd D:\Android\PROJECT\SMSLOG
.\gradlew.bat clean assembleRelease
```
*(Produces `app/build/outputs/apk/release/app-release-unsigned.apk`)*.

### 3. Sign the APK
```powershell
& "C:\Users\KAIRAV\AppData\Local\Android\Sdk\build-tools\35.0.1\apksigner.bat" sign `
    --ks "D:\Android\PROJECT\SMSLOG\nexum-release.jks" `
    --v1-signing-enabled false `
    --v2-signing-enabled true `
    --alignment-preserved `
    --out "D:\Android\PROJECT\SMSLOG\nexum-v1.1.apk" `
    "D:\Android\PROJECT\SMSLOG\app\build\outputs\apk\release\app-release-unsigned.apk"
```

### 4. Generate Checksum
```powershell
(Get-FileHash -Algorithm SHA256 .\nexum-v1.1.apk).Hash.ToLower() + "  nexum-v1.1.apk" | Out-File -Encoding utf8 SHA256SUMS.txt
```

### 5. Tag and Push Git Release
```powershell
git add .
git commit -m "chore: release version 1.1"
git push origin main
git tag -a v1.1 -m "Release version 1.1"
git push origin v1.1
```

### 6. Attach Assets to GitHub Release
Upload `nexum-v1.1.apk` and `SHA256SUMS.txt` to the GitHub release.

---

## 🔍 Root Cause Analysis & Lessons Learned

| # | Obstacle | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **1** | `Missing META-INF/MANIFEST.MF` | Release workflow uploaded unsigned APK. | Always sign the APK before uploading to GitHub Releases. |
| **2** | `fdroid rewritemeta` failure | Windows CRLF (`\r\n`) line endings in `.yml`. | Keep all `.yml` files in pure Unix LF (`\n`) format. |
| **3** | `version-control-info.textproto` diff | Commit hash in recipe did not match the Git commit when APK was built. | Align recipe `commit:` hash with the exact Git commit used to build the APK. |
| **4** | `CHUNKED_SHA256 digest mismatch` (v1) | Default `apksigner` injected 3 extra legacy v1 files into ZIP. | Use `--v1-signing-enabled false`. |
| **5** | `CHUNKED_SHA256 digest mismatch` (offset) | Build-Tools 35 re-aligned files with 4 extra padding bytes. | Use `--alignment-preserved`. |
| **6** | `supplied reference binary signed with ... instead of ['...']` | Recipe had outdated placeholder SHA-256 fingerprint. | Update `AllowedAPKSigningKeys` to match `nexum-release.jks` (`06be24a1...`). |
| **7** | APK size 60 MB | R8 code shrinking was disabled. | Enabled `isMinifyEnabled = true` with ProGuard rules in `app/proguard-rules.pro` (reduced size to 6.7 MB). |

---

## 🤖 GitLab CI/CD Automation & MCP Details

- **GitLab MCP Config Path:** `c:\Users\KAIRAV\.gemini\config\mcp_config.json`
- **GitLab Fork Project ID:** `86419676` (`listav3/fdroid-data`)
- **Target Upstream Repo:** `36528` (`fdroid/fdroiddata`)
- **Merge Request URL:** `https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48799`

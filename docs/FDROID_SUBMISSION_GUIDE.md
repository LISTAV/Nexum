# 🚀 F-Droid & IzzyOnDroid Deployment Guide

This guide details the complete process to publish **Nexum** (`com.kairav.nexum`) to the F-Droid ecosystem.

---

## 📋 Pre-Submission Checklist

- [x] **Open Source License:** GNU GPL v3.0 ([`LICENSE`](../LICENSE)).
- [x] **No Proprietary Trackers / Bloat:** All dependencies are pure FOSS (Jetpack Compose, Room, Hilt, Retrofit, OkHttp).
- [x] **Fastlane Metadata:** Located at `fastlane/metadata/android/en-US/`:
  - `title.txt` ("Nexum")
  - `short_description.txt`
  - `full_description.txt`
  - `changelogs/1.txt`
  - `images/icon.png` (512x512)
  - `images/featureGraphic.png` (1024x500)
- [x] **F-Droid Build Recipe:** [`metadata/com.kairav.nexum.yml`](../metadata/com.kairav.nexum.yml) generated.
- [x] **GitHub Actions CI/CD:** [`.github/workflows/release.yml`](../.github/workflows/release.yml) configured.

---

## 🛠 Step 1: Push Code and Tag Release on GitHub

1. Initialize Git (if not already done) and commit the files:
   ```bash
   git init
   git add .
   git commit -m "feat: initial release v1.0 with F-Droid metadata and release workflow"
   ```

2. Link your remote repository and push to GitHub:
   ```bash
   git remote add origin https://github.com/LISTAV/Nexum.git
   git branch -M main
   git push -u origin main
   ```

3. Create and push the release tag:
   ```bash
   git tag -a v1.0 -m "Release version 1.0"
   git push origin v1.0
   ```

> [!TIP]
> Once you push `v1.0`, the GitHub Action workflow will automatically trigger, build the release APK, compute SHA-256 checksums, and publish a new GitHub Release with the APK attached.

---

## 🌐 Step 2: Submit to Official F-Droid Main Repository

F-Droid builds all apps from source on their GitLab infrastructure (`fdroiddata`).

1. **Fork the `fdroiddata` repository:**
   - Go to [https://gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata)
   - Click **Fork** (top right) to create a fork under your GitLab account.

2. **Add the metadata recipe file:**
   - In your fork, create a new branch: `git checkout -b add-nexum`
   - Copy the recipe file `metadata/com.kairav.nexum.yml` to the `metadata/` directory in your `fdroiddata` clone:
     ```bash
     cp /path/to/nexum/metadata/com.kairav.nexum.yml metadata/
     ```
   - Commit and push the new recipe:
     ```bash
     git add metadata/com.kairav.nexum.yml
     git commit -m "Add com.kairav.nexum"
     git push origin add-nexum
     ```

3. **Open a Merge Request (MR):**
   - Go to [https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new)
   - Select your source branch (`add-nexum`) and target branch (`fdroiddata:master`).
   - Title: `Add com.kairav.nexum`
   - Fill out the MR template checklist (confirming GPL-3.0 license and no non-free network services/trackers).
   - Submit the MR! The `fdroid-bot` will run automated lint and build tests.

---

## ⚡ Step 3: Submit to IzzyOnDroid (Fast Community Repository)

IzzyOnDroid is the most popular community repository for F-Droid, indexing thousands of FOSS Android apps directly from GitHub Releases. It usually indexes apps within **24–48 hours**.

1. Go to the IzzyOnDroid Repository issue tracker at Codeberg:
   👉 **[https://codeberg.org/IzzyOnDroid/repodata/issues](https://codeberg.org/IzzyOnDroid/repodata/issues)**
   *(Click **New Issue** -> select **App Inclusion Request**)*.

2. Select the template **`App Inclusion Request`** (or fill in the details):
   - **Application Name:** Nexum
   - **Package ID:** `com.kairav.nexum`
   - **Repository URL:** `https://github.com/LISTAV/Nexum`
   - **License:** GNU General Public License v3.0 (`GPL-3.0-or-later`)
   - **Release Tag Pattern:** `v*` (e.g. `v1.0`)
   - **Description:** Real-time SMS and call log synchronization to Telegram.
   - **Trackers:** 0 trackers (Pure FOSS).

3. Submit the issue. The maintainer (Izzy) will review and add Nexum to the repository.

---

## 🔄 Future Updates & Maintenance

Whenever you make improvements or fixes:
1. Increment `versionCode` (e.g. `2`) and `versionName` (e.g. `"1.1"`) in `app/build.gradle.kts`.
2. Add a new changelog file `fastlane/metadata/android/en-US/changelogs/2.txt`.
3. Commit, push, and create a new tag:
   ```bash
   git tag -a v1.1 -m "Release version 1.1"
   git push origin v1.1
   ```
4. **IzzyOnDroid** and **F-Droid** bots will automatically detect the new tag, build/index the update, and distribute it to all users!

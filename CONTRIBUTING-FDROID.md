# Publishing Doomscroller on F-Droid — checklist

F-Droid builds your tagged Git source itself. There is no upload-binary step.

## 1. Prepare the repo

- [ ] Public Git URL (GitHub/GitLab/Codeberg).
- [ ] `LICENSE` = GPL-3.0-or-later (done in this scaffold).
- [ ] No proprietary deps: only AndroidX / Kotlinx / Jetpack Compose
      (all Apache-2.0). Run `./gradlew dependencies` and check for
      `com.google.firebase`, `com.google.android.gms`, crashlytics, etc.
      None are present here.
- [ ] No `INTERNET` permission in `AndroidManifest.xml` (verifiable).
- [ ] `versionCode` bumped every release, `versionName` matches Git tag.
- [ ] `fastlane/metadata/android/en-US/` filled (done).
- [ ] Tag the release: `git tag -a v0.2.0 -m "v0.1.0" && git push --tags`.

## 2. Submit to F-Droid

1. Fork https://gitlab.com/fdroid/fdroiddata
2. Copy `fdroid/app.doomscroller.yml` in this repo to
   `metadata/app.doomscroller.yml` in your fork (replace YOU URLs).
3. Open a merge request titled `New App: Doomscroller`.
4. F-Droid CI (`fdroid lint`, `fdroid build`, `fdroid scanner`) runs.
   Common failures and fixes:
   - `Found proprietary blob`: remove the dependency.
   - `Binary in repo`: delete APKs, keep source only.
   - `Old targetSdk`: bump `targetSdk` to the current F-Droid minimum
     (34+ as of 2025/2026; this scaffold already targets 34).

## 3. Updates

- Bump `versionCode` + `versionName`, tag `vX.Y.Z`, F-Droid auto-updates
  via `UpdateCheckMode: Tags` + `AutoUpdateMode: Version v%v`.

## 4. Anti-features (declare honestly)

This app uses an AccessibilityService. F-Droid reviewers will ask why.
Answer lives in `strings.xml` + README: scroll-gesture detection only,
`canRetrieveWindowContent=false`, no content read. No ads/tracking, so no
Anti-Features needed.

---
name: sds-verification-env
description: The "SDS PC" environment (C:SERSSDSPROJECTGET-YOUR-GUITAR, WINDOWS 11, ANDROID STUDIO + ADB) IS THE DEVICE-VERIFICATION ENVIRONMENT; JDK 21 JBR BUILDS M0 FINE, AGP AUTO-DOWNLOADS SDK PLATFORMS HERE
metadata:
  type: project
---

This machine (`C:\Users\SDS\project\get-your-guitar`, Windows 11 Pro, user `SDS`) is the **검증 환경** — it has Android Studio 2025.3.1 and `adb` on PATH, so real devices can be attached here. It is *not* 아키랩 (that one is `E:\yjane.kim\...` on Windows Server 2019, see [[archilab-dev-workflow]]).

Facts (2026-09-18, full table in README "SDS PC" section):
- `JAVA_HOME` = Android Studio's bundled JBR, **OpenJDK 21** (아키랩 is Temurin 17). `java` is not on PATH; `gradlew.bat` uses `JAVA_HOME` so builds work.
- SDK at `C:\Users\SDS\AppData\Local\Android\Sdk`: platforms android-34/35/**36.1**, build-tools 34.0.0/35.0.0/36.1.0, platform-tools. **No `android-36` (36.0), no cmdline-tools.** `ANDROID_HOME` is unset → rely on `local.properties` `sdk.dir`.
- `GRADLE_USER_HOME` unset → default `~/.gradle`. C: has ~270 GB free, so no disk concern here (unlike 아키랩).
- `gh` logged in as `codeSproutGreen`. Claude memory junction linked to `.claude/memory` on 2026-09-18.
- **Test phone: Galaxy S10e (SM-G970N), Android 12 / API 31**, serial R39M207CM4H, 1080x2280. It is a test device, not the phone the user will actually practice on (that one is assumed Android 13+). App minSdk was lowered 33→31 for it on 2026-09-21.
- **M0 verified on this phone 2026-09-21**: installDebug OK, landscape, centered text, no crash.
- adb gotchas here: authorization reverts to `unauthorized` on replug unless "always allow" was ticked; in Git Bash prefix `export MSYS_NO_PATHCONV=1` before `adb shell` commands that take `/sdcard/...` paths.

**Why:** Toolchain differs from 아키랩 in JDK major version and SDK platform revision; the first M0 build here is where those differences will surface.

**How to apply:** M0 built here 2026-09-18 with JDK 21: AGP auto-downloaded `platforms;android-36` and `android-37.0` (licenses dir present), `:core:test`/`assembleDebug`/`lintDebug` all pass — no toolchain pinning needed so far. `local.properties` has `sdk.dir=C:/Users/SDS/AppData/Local/Android/Sdk`. Still to do here: attach a phone and run `installDebug`. The user wants project state kept in GitHub, not in local-only memory — always update `docs/STATUS.md` and commit memory changes with code, push before ending a session.

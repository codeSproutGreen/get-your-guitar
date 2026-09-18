---
name: sds-verification-env
description: The "SDS PC" environment (C:\Users\SDS\project\get-your-guitar, Windows 11, Android Studio + adb) is the device-verification environment; its toolchain differs from 아키랩 (JDK 21 JBR, SDK android-36.1 only, no cmdline-tools)
metadata:
  type: project
---

This machine (`C:\Users\SDS\project\get-your-guitar`, Windows 11 Pro, user `SDS`) is the **검증 환경** — it has Android Studio 2025.3.1 and `adb` on PATH, so real devices can be attached here. It is *not* 아키랩 (that one is `E:\yjane.kim\...` on Windows Server 2019, see [[archilab-dev-workflow]]).

Facts (2026-09-18, full table in README "SDS PC" section):
- `JAVA_HOME` = Android Studio's bundled JBR, **OpenJDK 21** (아키랩 is Temurin 17). `java` is not on PATH; `gradlew.bat` uses `JAVA_HOME` so builds work.
- SDK at `C:\Users\SDS\AppData\Local\Android\Sdk`: platforms android-34/35/**36.1**, build-tools 34.0.0/35.0.0/36.1.0, platform-tools. **No `android-36` (36.0), no cmdline-tools.** `ANDROID_HOME` is unset → rely on `local.properties` `sdk.dir`.
- `GRADLE_USER_HOME` unset → default `~/.gradle`. C: has ~270 GB free, so no disk concern here (unlike 아키랩).
- `gh` logged in as `codeSproutGreen`. Claude memory junction linked to `.claude/memory` on 2026-09-18.
- No device was attached at the time of the check (`adb devices` empty).

**Why:** Toolchain differs from 아키랩 in JDK major version and SDK platform revision; the first M0 build here is where those differences will surface.

**How to apply:** When building here for the first time, check whether AGP resolves compileSdk 36 against `android-36.1` or needs `platforms;android-36` from SDK Manager, and consider pinning a JDK 17 toolchain if 아키랩/SDS builds diverge. Record the outcome in README and this memory. The user wants project state kept in GitHub, not in local-only memory — always commit memory changes with code and push before ending a session.

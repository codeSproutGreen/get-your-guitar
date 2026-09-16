---
name: archilab-dev-workflow
description: This environment is named "아키랩(Archilab)"; Android app dev with GitHub remote as SSOT, no device connection here, verification happens in other environments; C: drive nearly full, tools installed on E:
metadata:
  type: project
---

This machine (E:\yjane.kim\project\get-your-guitar, Windows Server 2019) is called **아키랩 (Archilab)** by the user. It is one of several environments used to develop the "get-your-guitar" Android application.

- GitHub remote `codeSproutGreen/get-your-guitar` (private) is the **single source of truth (SSOT)**. Multiple environments pull from / push to it. Repo created 2026-09-16 from 아키랩.
- 아키랩 cannot connect Android devices, so its role is **initial development** (code, structure, build, unit tests) — not on-device verification. adb is intentionally not installed here.
- Other environments clone from GitHub, verify on real devices, fix, and push back; 아키랩 then pulls and continues.
- Repo contains **code only** — JDK/Android SDK/Gradle caches are assumed to be installed per environment, never committed. Per-environment dependency records live in README.md.
- Claude auto-memory lives in the repo at `.claude/memory/` and is git-tracked; `~/.claude/projects/<encoded>/memory` is a junction to it (set up by `scripts/link-claude-memory.ps1`).

**아키랩 machine facts (2026-09-16):**
- **C: drive has ~1 GB free** — never install tools or let caches grow on C:. Everything goes under `E:\yjane.kim\tools` (E: has ~460 GB free), user-scope env vars only.
- Installed 2026-09-16 (see README for the table): Temurin JDK 17.0.20.1+1, Android cmdline-tools 22.0, `platforms;android-37.0` (compileSdk 37; AGP auto-installed it during M0), `platforms;android-36` (unused), `build-tools;36.0.0`. No platform-tools/adb, no emulator, no Android Studio. Gradle arrives via the project wrapper into `GRADLE_USER_HOME = E:\yjane.kim\tools\gradle-user-home`.
- System PATH still puts Oracle JRE 1.8 first, so bare `java` reports 1.8; gradlew/sdkmanager use JAVA_HOME (17) so builds are fine.
- In a Claude Code session started before the env vars were set (or if `JAVA_HOME` shows empty in Bash), export `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME` inline for Gradle/sdkmanager commands.
- `gh` CLI is installed and logged in as `codeSproutGreen`.

**Why:** Device testing is impossible here, so work must be pushed frequently in a clean, pullable state for other environments to verify. C: is nearly full so any tooling must go to E:.

**How to apply:** Always `git pull --rebase` before starting work and keep commits small and pushable. Don't claim on-device behavior is verified from 아키랩; mark such items as "검증 환경에서 확인 필요". Commit memory changes together with code. Related: [[get-your-guitar-project]]

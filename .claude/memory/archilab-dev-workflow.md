---
name: archilab-dev-workflow
description: This environment is named "아키랩(Archilab)"; Android app dev with GitHub remote as SSOT, no device connection here, verification happens in other environments; C: drive nearly full
metadata:
  type: project
---

This machine (E:\yjane.kim\project\get-your-guitar, Windows Server 2019) is called **아키랩 (Archilab)** by the user. It is one of several environments used to develop the "get-your-guitar" Android application.

- GitHub remote `codeSproutGreen/get-your-guitar` (private) is the **single source of truth (SSOT)**. Multiple environments pull from / push to it. Repo created 2026-09-16 from 아키랩.
- 아키랩 cannot connect Android devices, so its role is **initial development** (code, structure, build-level work) — not on-device verification. adb is intentionally not installed here.
- Other environments clone from GitHub, verify on real devices, fix, and push back; 아키랩 then pulls and continues.
- Repo contains **code only** — JDK/Android SDK/Gradle caches are assumed to be installed per environment, never committed.
- Claude auto-memory lives in the repo at `.claude/memory/` and is git-tracked; `~/.claude/projects/<encoded>/memory` is a junction to it (set up by `scripts/link-claude-memory.ps1`).

**아키랩 machine facts (2026-09-16):**
- **C: drive has ~1 GB free** — never install tools or let caches grow on C:. Use E: (462 GB free) and set `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME` to E: paths.
- Only JRE 1.8 present at `C:\Program Files\Java\jre-1.8`; no JDK, no Android SDK, no Gradle yet.
- `gh` CLI is installed and logged in as `codeSproutGreen`.

**Why:** Device testing is impossible here, so work must be pushed frequently in a clean, pullable state for other environments to verify. C: is nearly full so any tooling must go to E:.

**How to apply:** Always `git pull --rebase` before starting work and keep commits small and pushable. Don't claim on-device behavior is verified from 아키랩; mark such items as "검증 환경에서 확인 필요". Commit memory changes together with code. Related: [[get-your-guitar-project]]

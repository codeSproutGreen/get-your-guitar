---
name: get-your-guitar-project
description: What the get-your-guitar app is — a bass guitar fretboard simulator modeled on the Play Store app "Bass Guitar Solo"; stack/architecture not decided yet
metadata:
  type: project
---

- The app is a **bass guitar fretboard simulator**, modeled on the existing app **"Bass Guitar Solo"**. User confirmed this on 2026-09-16.
- Reference screenshot: `reference.webp` in the repo root — landscape phone, "Full 24-fret experience", top toolbar (menu, play, record, instrument, metronome-like icon), left/right arrows to scroll the fretboard, "SOLO" mode badge, fret markers on a wood-textured neck.
- Tech stack, minSdk/compileSdk, architecture, and feature scope are **not decided yet**. Run superpowers:brainstorming with the user before scaffolding any project files.
- Toolchain available in 아키랩: JDK 17, `platforms;android-36`, `build-tools;36.0.0`. sdkmanager also lists `platforms;android-37.0/37.1/37.2` (new minor-version platform naming) — not installed; revisit compileSdk choice when picking the AGP version.

**Why:** The user's goal is a clone-style app of a known reference; decisions should be checked against that reference rather than invented.

**How to apply:** Look at `reference.webp` before designing any screen. Keep the first milestone small (fretboard render + touch → sound) and get it to the verification environment early. Related: [[archilab-dev-workflow]]

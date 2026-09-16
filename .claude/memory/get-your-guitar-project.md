---
name: get-your-guitar-project
description: What the get-your-guitar app is (bass fretboard simulator like "Bass Guitar Solo"), the approved v1 design/spec location, key decisions, and v2 backlog with hooks
metadata:
  type: project
---

- The app is a **bass guitar fretboard simulator** for the user's own practice (not for store release), modeled on the app **"Bass Guitar Solo"**. Reference screenshot: `reference.webp` in repo root.
- **v1 design spec (user-approved 2026-09-16):** `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md`. Read it before any implementation work; it has milestones M0–M3 in section 9.
- Key decisions: Kotlin + Jetpack Compose (Canvas fretboard); `:core` pure Kotlin/JVM module (music theory, Karplus-Strong synth, metronome, engine — unit-tested in 아키랩) + `:app` Android module; audio via `AudioTrack` low-latency (approach "C": output behind `AudioOutput` interface so Oboe can replace it later); minSdk 33, compileSdk 36; 4-string EADG fixed; tap = pluck with natural decay, fretboard drag = slide/rake, strip drag = scroll with snap; 12 equal-width frets per view.
- **v2 backlog with hooks already in the v1 design:** bending (gesture only; `Voice.setPitch(hz)` is continuous), real fret spacing with toggle (`FretLayout` interface, v1 = `EqualFretLayout`), sample-based voice, 5-string/tunings, recording.
- Implementation plan documents go in `docs/superpowers/plans/`.

**Why:** The user explicitly asked that v2 features (bending, real spacing) be designed for now but built later; the spec records the hooks so they aren't lost.

**How to apply:** Follow the spec's milestone order (M0 scaffold → M1 first sound → M2 settings → M3 metronome). Every milestone ends with push + verification in the device environment; 아키랩 only verifies `:core:test` and `:app:assembleDebug`. Related: [[archilab-dev-workflow]]

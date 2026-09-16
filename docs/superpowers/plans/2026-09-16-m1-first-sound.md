# M1 첫 소리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지판을 탭하면 베이스 음이 난다 — 순수 Kotlin `:core`에 음악이론·Karplus-Strong 합성·커맨드 큐·엔진을 TDD로 만들고, `:app`에 AudioTrack 출력과 Compose 지판(탭/슬라이드/레이크/띠 스크롤/음이름 토글)을 붙인다.

**Architecture:** `:core`는 Android 의존 0인 순수 Kotlin. 오디오 스레드가 `SynthEngine.render(out, frames)`를 pull 하고, UI 스레드는 `CommandQueue`(SPSC 링버퍼, 원시값 인코딩)로 커맨드를 넣는다. 줄당 `KarplusStrongVoice` 1개(소수점 딜레이 라인 + 1극 로우패스 + 피드백), `Mixer`가 합산·소프트클립. 지판 좌표 계산(`FretLayout`/`FretboardGeometry`)도 순수 Kotlin이라 `:core`에 두고 JVM 테스트한다. `:app`은 `AudioTrackOutput`(저지연 모드, 전용 스레드)과 Compose Canvas 지판·멀티터치 제스처만 담당한다.

**Tech Stack:** Kotlin 2.4.20, Gradle 9.7.0 wrapper, AGP 9.4.0 (built-in Kotlin), Compose BOM 2026.09.00 (foundation/material3/ui-text), JUnit Jupiter 6.1.3 + kotlin.test, JDK 17 (아키랩) / 21 (검증 환경)

**Spec:** `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` — 4장(아키텍처), 5장(`:core` 상세), 6.1·6.2(`:app` 오디오 출력·지판 화면), 8.1(테스트 기준), 9장 "M1 — 첫 소리"

## Global Constraints

- **브랜치:** `m1-first-sound`. PR #1(`m0-scaffold` → `main`)이 아직 OPEN이면 `m0-scaffold`에서 분기하고 PR은 base `m0-scaffold`로 stacked; PR #1이 merge됐으면 `main`에서 분기(또는 `git rebase main`)하고 base `main`. Task 1 Step 0과 Task 12에서 확인한다.
- **환경 프리앰블** (아키랩 Claude Code Bash는 환경변수가 비어 있음). 모든 Gradle 명령 앞에:
  ```bash
  export JAVA_HOME='E:\yjane.kim\tools\jdk-17.0.20.1+1' GRADLE_USER_HOME='E:\yjane.kim\tools\gradle-user-home'; cd /e/yjane.kim/project/get-your-guitar
  ```
  `./gradlew.bat` 사용. `:core` 테스트: `./gradlew.bat :core:test --console=plain`, 특정 클래스만: `./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.music.PitchTest" --console=plain`. 앱 컴파일: `./gradlew.bat :app:assembleDebug :app:lintDebug --console=plain` (10분 타임아웃).
- **패키지:** `:core` = `com.sproutgreen.getyourguitar.core.{music,synth,engine,fretboard}`, 테스트 유틸 `core.testutil` (src/test에만). `:app` = `com.sproutgreen.getyourguitar.{audio,ui.theme,ui.fretboard}`.
- **`:core`에 Android/Compose 의존 금지.** `core/build.gradle.kts`는 건드리지 않는다 (JUnit·kotlin.test 이미 있음).
- **오디오 스레드 규칙 (스펙 4.3):** `SynthEngine.render`, `CommandHandler.handle`, `Voice.render`, `Mixer.process`, `CommandQueue.drain` 경로에서 **객체 할당·락·로그·예외 금지.** 배열 for 루프(`for (v in voices)` on `Array`)는 OK, `List`/람다 캡처/문자열 템플릿/`data class copy`는 금지. 리뷰 항목.
- **스펙 수치 (그대로):** velocity 0.8 · 딜레이 버퍼 `sampleRate/25` 샘플 · 루프 컷오프 500 Hz~6 kHz(로그) · 피드백 0.990~0.9995(선형) · 글라이드 8 ms · 재피킹 딥 2 ms · 비활성 판정 피크 < 1e-4 × 4청크 연속 · 믹서 프리게인 0.5, 소프트클립 `x/(1+|x|)` · 큐 용량 256 슬롯 × Int 4개 · 기본 밝기 0.6, 감쇠 0.7, 마스터 0.8 · 뷰포트 12셀, scroll ∈ [−1, 12], 스냅 150 ms · 하이라이트 1.5 s · 화면 세로 비율 상단바 12% / 프렛번호 띠 8% / 지판 72% / 하단 띠 8% · 줄은 위에서 G·D·A·E(E가 가장 굵음) · 포지션 마크 3·5·7·9·15·17·19·21 단점, 12·24 이중점.
- **스펙 대비 의도된 편차 (플랜 결정):** ① `FretLayout`·지판 기하를 `app/ui/fretboard`가 아니라 `:core/fretboard`에 둠 — 순수 계산이라 아키랩에서 테스트 가능. ② `AudioOutput.start`는 람다 대신 `fun interface AudioRenderer`(Int 파라미터 박싱 방지). ③ `ToneParams` 객체 대신 `ToneMapping` 순수 함수 + `Voice.setTone(brightness, decay)` — 렌더 경로 무할당. ④ 스펙 M3의 `FLAG_KEEP_SCREEN_ON`·오디오 스레드 예외 배너를 M1에 포함(몇 줄). ⑤ `ViewModel` 대신 `remember`된 `FretboardUiState` (M2에서 설정 연결 시 재검토). ⑥ 슬라이드 시 0.85 게인 딥은 스펙대로 M2.
- **M1 사전결정 반영 (메모리):** 다크 `MaterialTheme` + 같은 색 `windowBackground`, `windowLayoutInDisplayCutoutMode=shortEdges` + `WindowInsets.safeDrawing` 패딩, `FLAG_KEEP_SCREEN_ON`.
- **테스트 규약:** kotlin.test (`@Test`, `assertEquals(expected, actual, tolerance)`), 백틱 테스트명, 48 kHz 고정(`SR = 48000`), 랜덤은 `java.util.Random(seed)` 주입. 오디오 분석은 `core.testutil.SignalAnalysis`.
- 커밋은 Task마다 1개(Task 12는 2개). 명시적 `git add` 경로만. 메시지 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 기기 동작은 "검증 환경에서 확인 필요"로 표기.

---

## 파일 구조

| 경로 | 책임 |
|---|---|
| `core/src/main/kotlin/.../core/music/Tuning.kt` | 개방현 MIDI 배열, `STANDARD_BASS_4` |
| `.../core/music/Pitch.kt` | MIDI → Hz |
| `.../core/music/NoteName.kt` | MIDI → 음이름(샤프) |
| `.../core/music/Fretboard.kt` | `FretPosition`, (줄, 프렛) → MIDI/Hz/이름, 유효성 |
| `.../core/synth/ToneMapping.kt` | 밝기/감쇠 → 컷오프·피드백·1극 계수·필터 지연 (순수 함수) |
| `.../core/synth/Voice.kt` | 보이스 인터페이스 |
| `.../core/synth/KarplusStrongVoice.kt` | 확장 KS 현 모델 (소수점 딜레이, 글라이드, 클릭 없는 재피킹, 비활성 판정) |
| `.../core/synth/StringVoices.kt` | 줄당 보이스 1개 라우팅 |
| `.../core/synth/Mixer.kt` | 프리게인·마스터·소프트클립 |
| `.../core/engine/Command.kt` | `CommandType` 상수, `CommandHandler`, `AudioRenderer` |
| `.../core/engine/CommandQueue.kt` | SPSC 링버퍼 |
| `.../core/engine/EngineStats.kt` | volatile 상태 |
| `.../core/engine/SynthEngine.kt` | 큐 → 보이스 → 믹서 렌더 |
| `.../core/fretboard/FretLayout.kt` | `FretLayout`, `EqualFretLayout` |
| `.../core/fretboard/FretboardGeometry.kt` | 히트테스트, 스크롤 클램프/스냅, 마커, 가시 범위 |
| `core/src/test/kotlin/.../core/testutil/SignalAnalysis.kt` | RMS·피크·최대 차분·자기상관 기본주파수 (테스트 전용) |
| `core/src/test/kotlin/.../core/{music,synth,engine,fretboard}/*Test.kt` | 위 각각의 테스트 |
| `app/src/main/kotlin/.../audio/AudioOutput.kt` | 출력 백엔드 인터페이스 |
| `.../audio/AudioTrackOutput.kt` | AudioTrack 저지연 출력 + 전용 스레드 |
| `.../audio/AudioController.kt` | 엔진·출력 소유, UI용 API, 에러 상태 |
| `app/src/main/res/values/colors.xml`, `themes.xml` | 다크 윈도우 배경, 컷아웃 모드 |
| `.../ui/theme/Theme.kt` | 다크 `MaterialTheme` |
| `.../ui/fretboard/FretboardColors.kt` | 지판 색상 상수 |
| `.../ui/fretboard/FretboardUiState.kt` | scroll·음이름 토글·하이라이트 상태 |
| `.../ui/fretboard/FretboardCanvas.kt` | 지판 그리기 |
| `.../ui/fretboard/FretNumberStrip.kt` | 프렛 번호 띠 그리기 |
| `.../ui/fretboard/FretboardGestures.kt` | 지판 멀티터치, 띠 드래그+스냅 |
| `.../ui/fretboard/FretboardScreen.kt` | 상단바 + 띠 + 지판 + 띠 레이아웃, 에러 배너, 디버그 정보 |
| `app/src/main/kotlin/.../MainActivity.kt` | 컨트롤러 수명주기, 화면 켜짐 유지, 테마 |
| `app/src/main/AndroidManifest.xml` | 테마 교체 |
| `docs/verification-checklist.md` | 실기기 체크리스트 |
| `README.md`, `CLAUDE.md`, `.claude/memory/get-your-guitar-project.md` | 절차·규칙·상태 |

---

### Task 1: 브랜치 + `core/music` (Tuning, Pitch, NoteName, Fretboard)

**Files:**
- Create: `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/music/Tuning.kt`, `Pitch.kt`, `NoteName.kt`, `Fretboard.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/music/PitchTest.kt`, `NoteNameTest.kt`, `FretboardTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `Tuning(openMidi: IntArray)` / `Tuning.STANDARD_BASS_4` / `stringCount`; `Pitch.hz(midi: Int): Double`; `NoteName.of(midi: Int): String`; `data class FretPosition(string: Int, fret: Int)`; `Fretboard(tuning, fretCount = 24)` with `isValid(string, fret): Boolean`, `midiAt(string, fret): Int`, `hzAt(string, fret): Double`, `nameAt(string, fret): String` (+ `FretPosition` 오버로드). Task 7·8·10이 사용.

- [ ] **Step 0: 브랜치 확인**

브랜치 `m1-first-sound`는 이 플랜을 커밋할 때 `m0-scaffold`에서 이미 만들어졌다 (플랜 문서가 그 첫 커밋).
```bash
cd /e/yjane.kim/project/get-your-guitar
git status --short                      # 비어 있어야 함
git checkout m1-first-sound
git branch --show-current               # m1-first-sound
gh pr view 1 --json state --jq .state   # 참고용: MERGED 면 Task 12에서 main 위로 rebase
```

- [ ] **Step 1: 실패하는 테스트 작성**

`core/src/test/kotlin/com/sproutgreen/getyourguitar/core/music/PitchTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

class PitchTest {
    @Test
    fun `A4 is 440 Hz`() {
        assertEquals(440.0, Pitch.hz(69), 1e-9)
    }

    @Test
    fun `E1 is 41_203 Hz`() {
        assertEquals(41.203, Pitch.hz(28), 0.001)
    }

    @Test
    fun `G4 is 391_995 Hz`() {
        assertEquals(391.995, Pitch.hz(67), 0.001)
    }

    @Test
    fun `octave doubles frequency`() {
        assertEquals(Pitch.hz(57) * 2, Pitch.hz(69), 1e-9)
        assertEquals(880.0, Pitch.hz(81), 1e-9)
    }
}
```

`NoteNameTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteNameTest {
    @Test
    fun `open bass strings are E A D G`() {
        assertEquals(listOf("E", "A", "D", "G"), listOf(28, 33, 38, 43).map { NoteName.of(it) })
    }

    @Test
    fun `uses sharps only`() {
        assertEquals("C", NoteName.of(60))
        assertEquals("C#", NoteName.of(61))
        assertEquals("A#", NoteName.of(70))
        assertEquals("B", NoteName.of(71))
    }

    @Test
    fun `wraps every octave including negative midi`() {
        assertEquals(NoteName.of(28), NoteName.of(40))
        assertEquals("B", NoteName.of(-1))
    }
}
```

`FretboardTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FretboardTest {
    private val fb = Fretboard(Tuning.STANDARD_BASS_4)

    @Test
    fun `open strings are E1 A1 D2 G2`() {
        assertEquals(listOf(28, 33, 38, 43), (0..3).map { fb.midiAt(it, 0) })
        assertEquals(listOf("E", "A", "D", "G"), (0..3).map { fb.nameAt(it, 0) })
    }

    @Test
    fun `every position is open note plus fret`() {
        for (s in 0..3) for (f in 0..24) {
            assertEquals(Tuning.STANDARD_BASS_4.openMidi[s] + f, fb.midiAt(s, f), "string $s fret $f")
        }
    }

    @Test
    fun `G string fret 24 is G4`() {
        assertEquals(67, fb.midiAt(3, 24))
        assertEquals(391.995, fb.hzAt(3, 24), 0.001)
        assertEquals("G", fb.nameAt(3, 24))
    }

    @Test
    fun `fifth fret matches the next open string`() {
        for (s in 0..2) assertEquals(fb.midiAt(s + 1, 0), fb.midiAt(s, 5))
    }

    @Test
    fun `known note names`() {
        assertEquals("F", fb.nameAt(0, 1))
        assertEquals("C", fb.nameAt(1, 3))
        assertEquals("F#", fb.nameAt(2, 4))
    }

    @Test
    fun `validity and rejection of bad positions`() {
        assertTrue(fb.isValid(0, 0))
        assertTrue(fb.isValid(3, 24))
        assertFalse(fb.isValid(4, 0))
        assertFalse(fb.isValid(-1, 0))
        assertFalse(fb.isValid(0, 25))
        assertFailsWith<IllegalArgumentException> { fb.midiAt(0, 25) }
    }

    @Test
    fun `FretPosition overloads agree with int overloads`() {
        val pos = FretPosition(2, 7)
        assertEquals(fb.midiAt(2, 7), fb.midiAt(pos))
        assertEquals(fb.hzAt(2, 7), fb.hzAt(pos))
        assertEquals(fb.nameAt(2, 7), fb.nameAt(pos))
    }

    @Test
    fun `string count comes from the tuning`() {
        assertEquals(4, fb.stringCount)
        assertEquals(24, fb.fretCount)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
export JAVA_HOME='E:\yjane.kim\tools\jdk-17.0.20.1+1' GRADLE_USER_HOME='E:\yjane.kim\tools\gradle-user-home'; cd /e/yjane.kim/project/get-your-guitar
./gradlew.bat :core:test --console=plain
```
Expected: FAIL — `compileTestKotlin` 에서 `Unresolved reference 'Pitch'` 등.

- [ ] **Step 3: 구현**

`core/src/main/kotlin/com/sproutgreen/getyourguitar/core/music/Tuning.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

/** Open-string MIDI notes; index 0 is the lowest (thickest) string. */
class Tuning(val openMidi: IntArray) {
    init {
        require(openMidi.isNotEmpty()) { "a tuning needs at least one string" }
    }

    val stringCount: Int get() = openMidi.size

    companion object {
        /** E1 A1 D2 G2 — 4-string bass standard tuning. String 0 = E (bottom of the screen), 3 = G (top). */
        val STANDARD_BASS_4: Tuning = Tuning(intArrayOf(28, 33, 38, 43))
    }
}
```

`Pitch.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

object Pitch {
    const val A4_MIDI = 69
    const val A4_HZ = 440.0

    /** Equal temperament: 440 · 2^((midi − 69) / 12). */
    fun hz(midi: Int): Double = A4_HZ * Math.pow(2.0, (midi - A4_MIDI) / 12.0)
}
```

`NoteName.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

object NoteName {
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Sharps only (spec 5.1). */
    fun of(midi: Int): String = NAMES[Math.floorMod(midi, 12)]
}
```

`Fretboard.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.music

/** A place on the neck. fret 0 = open string. */
data class FretPosition(val string: Int, val fret: Int)

class Fretboard(val tuning: Tuning, val fretCount: Int = 24) {
    val stringCount: Int get() = tuning.stringCount

    fun isValid(string: Int, fret: Int): Boolean =
        string >= 0 && string < stringCount && fret >= 0 && fret <= fretCount

    fun midiAt(string: Int, fret: Int): Int {
        require(isValid(string, fret)) { "invalid position string=$string fret=$fret" }
        return tuning.openMidi[string] + fret
    }

    fun hzAt(string: Int, fret: Int): Double = Pitch.hz(midiAt(string, fret))

    fun nameAt(string: Int, fret: Int): String = NoteName.of(midiAt(string, fret))

    fun midiAt(pos: FretPosition): Int = midiAt(pos.string, pos.fret)
    fun hzAt(pos: FretPosition): Double = hzAt(pos.string, pos.fret)
    fun nameAt(pos: FretPosition): String = nameAt(pos.string, pos.fret)
}
```
(`require`의 메시지 람다는 실패할 때만 평가되므로 성공 경로에 할당이 없다. 엔진은 어차피 `isValid`를 먼저 검사한다.)

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --console=plain
```
Expected: `PitchTest` 4, `NoteNameTest` 3, `FretboardTest` 8, 기존 `CoreInfoTest` 1 → 전부 PASSED, BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/sproutgreen/getyourguitar/core/music core/src/test/kotlin/com/sproutgreen/getyourguitar/core/music
git commit -m "$(cat <<'MSG'
core/music: Tuning, Pitch, NoteName, Fretboard

Equal-temperament pitch, sharp-only note names, 4-string bass tuning
(E1 A1 D2 G2) and (string, fret) → midi/Hz/name with validity checks.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 2: 테스트 유틸 `SignalAnalysis`

**Files:**
- Create: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/testutil/SignalAnalysis.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/testutil/SignalAnalysisTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces (테스트 전용): `SignalAnalysis.rms(x, from, to): Double`, `peak(x, from, to): Float`, `maxDelta(x, from, to): Float`, `fundamentalHz(x, sampleRate, from, to, minHz = 30.0, maxHz = 1000.0): Double`. Task 3·4·7이 사용.

- [ ] **Step 1: 유틸의 테스트 먼저 작성**

`SignalAnalysisTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.testutil

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class SignalAnalysisTest {
    private val sr = 48000

    private fun tone(hz: Double, seconds: Double, decayPerSecond: Double = 1.0, harmonics: DoubleArray = doubleArrayOf(1.0)): FloatArray {
        val n = (sr * seconds).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            var s = 0.0
            for (k in harmonics.indices) s += harmonics[k] * sin(2 * PI * hz * (k + 1) * t)
            out[i] = (s * decayPerSecond.pow(t)).toFloat()
        }
        return out
    }

    @Test
    fun `finds the fundamental of a low sine`() {
        val x = tone(41.203, 0.5)
        assertEquals(41.203, SignalAnalysis.fundamentalHz(x, sr, 0, x.size), 0.2)
    }

    @Test
    fun `finds the fundamental of a high sine`() {
        val x = tone(392.0, 0.5)
        assertEquals(392.0, SignalAnalysis.fundamentalHz(x, sr, 0, x.size), 2.0)
    }

    @Test
    fun `picks the fundamental even when a harmonic is louder`() {
        val x = tone(110.0, 0.5, harmonics = doubleArrayOf(0.5, 1.0, 0.7))
        assertEquals(110.0, SignalAnalysis.fundamentalHz(x, sr, 0, x.size), 0.6)
    }

    @Test
    fun `works on a decaying signal and a sub-window`() {
        val x = tone(110.0, 0.5, decayPerSecond = 0.1)
        assertEquals(110.0, SignalAnalysis.fundamentalHz(x, sr, sr / 10, x.size), 0.6)
    }

    @Test
    fun `rms peak and maxDelta`() {
        val x = FloatArray(100) { 0.5f }
        assertEquals(0.5, SignalAnalysis.rms(x, 0, x.size), 1e-6)
        x[50] = -0.5f
        assertEquals(0.5f, SignalAnalysis.peak(x, 0, x.size))
        assertEquals(1.0f, SignalAnalysis.maxDelta(x, 0, x.size))
        assertEquals(0.0f, SignalAnalysis.maxDelta(x, 0, 50))
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.testutil.SignalAnalysisTest" --console=plain
```
Expected: FAIL — `Unresolved reference 'SignalAnalysis'`.

- [ ] **Step 3: 구현**

`SignalAnalysis.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.testutil

import kotlin.math.abs
import kotlin.math.sqrt

/** Offline measurements for audio tests. Not optimised — fine for a few seconds of audio. */
object SignalAnalysis {
    fun rms(x: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += x[i].toDouble() * x[i]
        return sqrt(s / (to - from))
    }

    fun peak(x: FloatArray, from: Int, to: Int): Float {
        var p = 0f
        for (i in from until to) p = maxOf(p, abs(x[i]))
        return p
    }

    /** Largest |x[i] − x[i−1]| inside [from, to). A click shows up as a delta far above the signal's normal deltas. */
    fun maxDelta(x: FloatArray, from: Int, to: Int): Float {
        var d = 0f
        for (i in (from + 1) until to) d = maxOf(d, abs(x[i] - x[i - 1]))
        return d
    }

    /**
     * Fundamental frequency of x[from, to) by normalised autocorrelation.
     * Searches periods between minHz and maxHz, takes the SMALLEST lag that is a local maximum
     * within 0.01 of the global maximum (so a perfectly periodic signal returns the period, not 2×),
     * then refines with a parabola for sub-sample accuracy.
     */
    fun fundamentalHz(x: FloatArray, sampleRate: Int, from: Int, to: Int, minHz: Double = 30.0, maxHz: Double = 1000.0): Double {
        val n = to - from
        val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minHz).toInt().coerceAtMost(n / 2)
        require(maxLag > minLag + 1) { "window too short: $n samples" }
        val corr = DoubleArray(maxLag + 2)
        var globalMax = Double.NEGATIVE_INFINITY
        var argMax = minLag
        for (lag in (minLag - 1)..(maxLag + 1)) {
            var s = 0.0
            var e0 = 0.0
            var e1 = 0.0
            for (i in from until to - lag) {
                val a = x[i].toDouble()
                val b = x[i + lag].toDouble()
                s += a * b
                e0 += a * a
                e1 += b * b
            }
            corr[lag] = if (e0 > 0 && e1 > 0) s / sqrt(e0 * e1) else 0.0
            if (lag in minLag..maxLag && corr[lag] > globalMax) {
                globalMax = corr[lag]
                argMax = lag
            }
        }
        var bestLag = argMax
        val threshold = globalMax - 0.01
        for (lag in minLag..maxLag) {
            if (corr[lag] >= threshold && corr[lag] >= corr[lag - 1] && corr[lag] >= corr[lag + 1]) {
                bestLag = lag
                break
            }
        }
        var lag = bestLag.toDouble()
        val y0 = corr[bestLag - 1]
        val y1 = corr[bestLag]
        val y2 = corr[bestLag + 1]
        val denom = y0 - 2 * y1 + y2
        if (denom != 0.0) lag = bestLag + 0.5 * (y0 - y2) / denom
        return sampleRate / lag
    }
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.testutil.SignalAnalysisTest" --console=plain
```
Expected: 5 PASSED.

- [ ] **Step 5: 커밋**

```bash
git add core/src/test/kotlin/com/sproutgreen/getyourguitar/core/testutil
git commit -m "$(cat <<'MSG'
core/test: SignalAnalysis helper (rms, peak, maxDelta, autocorrelation pitch)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 3: `ToneMapping`, `Voice`, `KarplusStrongVoice` — 피킹·감쇠·비활성

**Files:**
- Create: `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/ToneMapping.kt`, `Voice.kt`, `KarplusStrongVoice.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/ToneMappingTest.kt`, `KarplusStrongVoiceTest.kt`

**Interfaces:**
- Consumes: Task 2 `SignalAnalysis`
- Produces: `interface Voice { val isActive: Boolean; fun noteOn(hz: Float, velocity: Float); fun setPitch(hz: Float); fun setTone(brightness: Float, decay: Float); fun silence(); fun render(out: FloatArray, offset: Int, frames: Int) }` (render는 **가산**); `KarplusStrongVoice(sampleRate: Int, random: java.util.Random = Random())`; `ToneMapping.cutoffHz(brightness)`, `feedback(decay)`, `onePoleCoefficient(cutoffHz, sampleRate)`, `onePoleDelaySamples(a)`, `DEFAULT_BRIGHTNESS = 0.6f`, `DEFAULT_DECAY = 0.7f`. Task 4가 같은 클래스에 글라이드·재피킹 테스트를 추가한다(구현은 이 Task에 전부 들어 있음).

- [ ] **Step 1: 실패하는 테스트 작성**

`ToneMappingTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneMappingTest {
    @Test
    fun `cutoff spans 500 Hz to 6 kHz on a log scale`() {
        assertEquals(500f, ToneMapping.cutoffHz(0f), 0.01f)
        assertEquals(6000f, ToneMapping.cutoffHz(1f), 0.5f)
        val mid = ToneMapping.cutoffHz(0.5f)
        assertEquals(Math.sqrt(500.0 * 6000.0).toFloat(), mid, 1f) // geometric midpoint
    }

    @Test
    fun `feedback spans 0_990 to 0_9995 linearly`() {
        assertEquals(0.990f, ToneMapping.feedback(0f), 1e-6f)
        assertEquals(0.9995f, ToneMapping.feedback(1f), 1e-6f)
        assertEquals(0.99475f, ToneMapping.feedback(0.5f), 1e-6f)
    }

    @Test
    fun `inputs are clamped to 0..1`() {
        assertEquals(ToneMapping.cutoffHz(0f), ToneMapping.cutoffHz(-3f))
        assertEquals(ToneMapping.feedback(1f), ToneMapping.feedback(7f))
    }

    @Test
    fun `one-pole coefficient and its delay`() {
        val a = ToneMapping.onePoleCoefficient(2220f, 48000)
        assertTrue(a > 0.2f && a < 0.3f, "a=$a")
        assertEquals((1f - a) / a, ToneMapping.onePoleDelaySamples(a), 1e-6f)
        assertTrue(ToneMapping.onePoleCoefficient(6000f, 48000) > a)
    }
}
```

`KarplusStrongVoiceTest.kt` (Task 4에서 테스트를 더 추가하므로 클래스 이름·헬퍼를 그대로 유지):
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import com.sproutgreen.getyourguitar.core.testutil.SignalAnalysis
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KarplusStrongVoiceTest {
    companion object {
        const val SR = 48000

        /** Renders [seconds] of audio in [chunk]-frame calls into a fresh buffer. */
        fun renderSeconds(v: Voice, seconds: Double, chunk: Int = 256): FloatArray {
            val total = (SR * seconds).toInt()
            val out = FloatArray(total)
            var i = 0
            while (i < total) {
                val n = minOf(chunk, total - i)
                v.render(out, i, n)
                i += n
            }
            return out
        }

        fun sec(s: Double): Int = (SR * s).toInt()
    }

    private fun voice(seed: Long = 1L) = KarplusStrongVoice(SR, Random(seed))

    @Test
    fun `plucked E1 rings at 41_2 Hz`() {
        val v = voice()
        v.noteOn(41.203f, 0.8f)
        val out = renderSeconds(v, 0.6)
        assertEquals(41.203, SignalAnalysis.fundamentalHz(out, SR, sec(0.2), sec(0.6)), 0.412) // ±1 %
    }

    @Test
    fun `plucked A2 rings at 110 Hz`() {
        val v = voice()
        v.noteOn(110f, 0.8f)
        val out = renderSeconds(v, 0.5)
        assertEquals(110.0, SignalAnalysis.fundamentalHz(out, SR, sec(0.1), sec(0.5)), 1.1)
    }

    @Test
    fun `plucked G4 rings at 392 Hz`() {
        val v = voice()
        v.noteOn(391.995f, 0.8f)
        val out = renderSeconds(v, 0.4)
        assertEquals(391.995, SignalAnalysis.fundamentalHz(out, SR, sec(0.05), sec(0.4)), 3.92)
    }

    @Test
    fun `energy decays monotonically in 100 ms windows`() {
        val v = voice()
        v.noteOn(110f, 0.8f)
        val out = renderSeconds(v, 1.0)
        var previous = SignalAnalysis.rms(out, sec(0.1), sec(0.2))
        for (k in 2 until 10) {
            val current = SignalAnalysis.rms(out, sec(k * 0.1), sec((k + 1) * 0.1))
            assertTrue(current < previous, "window $k: $current !< $previous")
            previous = current
        }
    }

    @Test
    fun `becomes inactive after decaying below threshold and then adds nothing`() {
        val v = voice()
        v.setTone(0.6f, 0f) // shortest decay
        v.noteOn(110f, 0.8f)
        assertTrue(v.isActive)
        val chunk = FloatArray(512)
        var rendered = 0
        while (v.isActive && rendered < SR * 15) {
            v.render(chunk, 0, chunk.size)
            rendered += chunk.size
        }
        assertFalse(v.isActive, "still active after ${rendered / SR} s")
        java.util.Arrays.fill(chunk, 0f)
        v.render(chunk, 0, chunk.size)
        assertEquals(0f, SignalAnalysis.peak(chunk, 0, chunk.size))
    }

    @Test
    fun `silence stops output immediately`() {
        val v = voice()
        v.noteOn(110f, 0.8f)
        renderSeconds(v, 0.1)
        v.silence()
        assertFalse(v.isActive)
        val out = renderSeconds(v, 0.05)
        assertEquals(0f, SignalAnalysis.peak(out, 0, out.size))
    }

    @Test
    fun `fresh voice is inactive and silent`() {
        val v = voice()
        assertFalse(v.isActive)
        val out = renderSeconds(v, 0.05)
        assertEquals(0f, SignalAnalysis.peak(out, 0, out.size))
    }

    @Test
    fun `render adds into the buffer instead of overwriting`() {
        val a = voice(5L).also { it.noteOn(110f, 0.8f) }
        val b = voice(5L).also { it.noteOn(110f, 0.8f) }
        val zeros = FloatArray(1024)
        val ones = FloatArray(1024) { 1f }
        a.render(zeros, 0, 1024)
        b.render(ones, 0, 1024)
        for (i in 0 until 1024) assertEquals(1f + zeros[i], ones[i], 1e-6f, "sample $i")
        assertTrue(SignalAnalysis.peak(zeros, 0, 1024) > 0.01f)
    }

    @Test
    fun `velocity scales loudness`() {
        val loud = voice(3L).also { it.noteOn(110f, 0.8f) }
        val quiet = voice(3L).also { it.noteOn(110f, 0.2f) }
        val l = renderSeconds(loud, 0.2)
        val q = renderSeconds(quiet, 0.2)
        assertEquals(4.0, SignalAnalysis.rms(l, 0, l.size) / SignalAnalysis.rms(q, 0, q.size), 0.05)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.synth.*" --console=plain
```
Expected: FAIL — `Unresolved reference 'ToneMapping'`, `'Voice'`, `'KarplusStrongVoice'`.

- [ ] **Step 3: 구현**

`ToneMapping.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.exp
import kotlin.math.pow

/** Maps the two user-facing tone knobs (0..1) to DSP coefficients. Pure functions — safe on the audio thread. */
object ToneMapping {
    const val DEFAULT_BRIGHTNESS = 0.6f
    const val DEFAULT_DECAY = 0.7f
    private const val TWO_PI = 6.2831855f

    /** Loop low-pass cutoff: 500 Hz at 0 → 6 kHz at 1, log scale (spec 5.2). */
    fun cutoffHz(brightness: Float): Float = 500f * 12f.pow(brightness.coerceIn(0f, 1f))

    /** Loop feedback: 0.990 at 0 → 0.9995 at 1, linear (spec 5.2). */
    fun feedback(decay: Float): Float = 0.990f + 0.0095f * decay.coerceIn(0f, 1f)

    /** Coefficient a of the one-pole low-pass `y += a * (x - y)` for a given cutoff. */
    fun onePoleCoefficient(cutoffHz: Float, sampleRate: Int): Float = 1f - exp(-TWO_PI * cutoffHz / sampleRate)

    /**
     * Phase delay (samples, at low frequency) of that one-pole filter. The string's delay line is shortened by this
     * amount so the loop period — and therefore the pitch — stays true (≈3 samples at the default brightness, which
     * would otherwise flatten a G4 by more than 2 %).
     */
    fun onePoleDelaySamples(a: Float): Float = (1f - a) / a
}
```

`Voice.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

/** One sounding string. All methods are called from the audio thread and must not allocate. */
interface Voice {
    val isActive: Boolean

    /** Pluck at [hz]. While already ringing this is a re-pluck: the previous note is cut without a click. */
    fun noteOn(hz: Float, velocity: Float)

    /** Continuous pitch change without re-plucking (slide now, bending in v2). */
    fun setPitch(hz: Float)

    fun setTone(brightness: Float, decay: Float)

    /** Hard stop: clears the string and deactivates. */
    fun silence()

    /** ADDS this voice's next [frames] samples into out[offset until offset + frames]. Does nothing when inactive. */
    fun render(out: FloatArray, offset: Int, frames: Int)
}
```

`KarplusStrongVoice.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import java.util.Random
import kotlin.math.abs

/**
 * Extended Karplus-Strong plucked string (spec 5.2).
 *
 * Circular delay line read at a fractional distance behind the write index (linear interpolation)
 * → one-pole low-pass → × feedback → written back. noteOn fills the line with a low-passed noise
 * burst. setPitch glides the delay length over GLIDE_MS. A noteOn while ringing dips the output
 * envelope to zero over REPLUCK_DIP_MS, refills the line, then ramps back — no click.
 * The line is shortened by the filter's phase delay so the pitch stays true (ToneMapping).
 */
class KarplusStrongVoice(
    private val sampleRate: Int,
    private val random: Random = Random(),
) : Voice {
    companion object {
        const val LOWEST_HZ = 25f
        const val GLIDE_MS = 8f
        const val REPLUCK_DIP_MS = 2f
        const val SILENCE_THRESHOLD = 1e-4f
        const val SILENT_CHUNKS_TO_DEACTIVATE = 4
        private const val MIN_DELAY = 2f
        private const val DENORMAL_FLOOR = 1e-15f
    }

    private val buffer = FloatArray((sampleRate / LOWEST_HZ).toInt() + 4)
    private var writeIndex = 0

    private var currentHz = 110f
    private var delayLength = 100f   // samples behind writeIndex that we read from (fractional)
    private var delayTarget = 100f
    private var delayStep = 0f
    private var glideSamplesLeft = 0

    private var lpCoefficient = 0f
    private var lpDelay = 0f
    private var lpState = 0f
    private var feedback = 0f

    private var envelope = 1f        // output gain; dips to 0 during a re-pluck
    private var envelopeTarget = 1f
    private var envelopeStep = 0f
    private var pendingPluck = false
    private var pendingHz = 0f
    private var pendingVelocity = 0f

    private var silentChunks = 0

    override var isActive: Boolean = false
        private set

    init {
        setTone(ToneMapping.DEFAULT_BRIGHTNESS, ToneMapping.DEFAULT_DECAY)
    }

    override fun setTone(brightness: Float, decay: Float) {
        lpCoefficient = ToneMapping.onePoleCoefficient(ToneMapping.cutoffHz(brightness), sampleRate)
        lpDelay = ToneMapping.onePoleDelaySamples(lpCoefficient)
        feedback = ToneMapping.feedback(decay)
        // Re-derive the delay so the current pitch stays true under the new filter delay.
        delayTarget = delayFor(currentHz)
        if (glideSamplesLeft == 0) delayLength = delayTarget
    }

    override fun noteOn(hz: Float, velocity: Float) {
        if (isActive) {
            pendingPluck = true
            pendingHz = hz
            pendingVelocity = velocity
            envelopeTarget = 0f
            envelopeStep = 1f / msToSamples(REPLUCK_DIP_MS)
        } else {
            pluck(hz, velocity)
            envelope = 1f
            envelopeTarget = 1f
            envelopeStep = 0f
        }
    }

    override fun setPitch(hz: Float) {
        currentHz = hz
        delayTarget = delayFor(hz)
        if (!isActive) {
            delayLength = delayTarget
            glideSamplesLeft = 0
            return
        }
        glideSamplesLeft = msToSamples(GLIDE_MS).toInt().coerceAtLeast(1)
        delayStep = (delayTarget - delayLength) / glideSamplesLeft
    }

    override fun silence() {
        java.util.Arrays.fill(buffer, 0f)
        lpState = 0f
        isActive = false
        pendingPluck = false
        envelope = 1f
        envelopeTarget = 1f
        envelopeStep = 0f
        glideSamplesLeft = 0
        silentChunks = 0
    }

    override fun render(out: FloatArray, offset: Int, frames: Int) {
        if (!isActive) return
        val size = buffer.size
        var peak = 0f
        for (i in 0 until frames) {
            if (glideSamplesLeft > 0) {
                delayLength += delayStep
                glideSamplesLeft--
                if (glideSamplesLeft == 0) delayLength = delayTarget
            }
            if (envelope != envelopeTarget) {
                envelope = if (envelopeTarget < envelope) {
                    maxOf(envelopeTarget, envelope - envelopeStep)
                } else {
                    minOf(envelopeTarget, envelope + envelopeStep)
                }
                if (envelope == 0f && pendingPluck) {
                    pendingPluck = false
                    pluck(pendingHz, pendingVelocity)
                    envelopeTarget = 1f
                }
            }
            var readPos = writeIndex - delayLength
            if (readPos < 0f) readPos += size
            val i0 = readPos.toInt()
            val frac = readPos - i0
            val s0 = buffer[i0]
            val s1 = buffer[if (i0 + 1 == size) 0 else i0 + 1]
            val delayed = s0 + (s1 - s0) * frac
            lpState += lpCoefficient * (delayed - lpState)
            if (abs(lpState) < DENORMAL_FLOOR) lpState = 0f
            val next = lpState * feedback
            buffer[writeIndex] = next
            writeIndex = if (writeIndex + 1 == size) 0 else writeIndex + 1
            val sample = next * envelope
            out[offset + i] += sample
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
        }
        if (peak < SILENCE_THRESHOLD) {
            silentChunks++
            if (silentChunks >= SILENT_CHUNKS_TO_DEACTIVATE) {
                isActive = false
                silentChunks = 0
            }
        } else {
            silentChunks = 0
        }
    }

    /** Fill the N samples behind writeIndex with a low-passed noise burst and (re)start the loop at [hz]. */
    private fun pluck(hz: Float, velocity: Float) {
        currentHz = hz
        delayTarget = delayFor(hz)
        delayLength = delayTarget
        glideSamplesLeft = 0
        delayStep = 0f
        val n = delayLength.toInt() + 1
        var lp = 0f
        for (k in 1..n) {
            val idx = Math.floorMod(writeIndex - k, buffer.size)
            val noise = random.nextFloat() * 2f - 1f
            lp += lpCoefficient * (noise - lp)
            buffer[idx] = lp * velocity
        }
        lpState = 0f
        isActive = true
        silentChunks = 0
    }

    private fun delayFor(hz: Float): Float =
        (sampleRate / hz - lpDelay).coerceIn(MIN_DELAY, (buffer.size - 2).toFloat())

    private fun msToSamples(ms: Float): Float = sampleRate * ms / 1000f
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.synth.*" --console=plain
```
Expected: `ToneMappingTest` 4 + `KarplusStrongVoiceTest` 9 → PASSED.

**실패 시 (주파수 오차):** `plucked G4` 가 ±1 %를 벗어나면 `delayFor`가 `lpDelay`를 빼고 있는지, `ToneMapping.onePoleDelaySamples`가 `(1−a)/a`인지 확인. `energy decays` 가 실패하면 첫 창(0.1 s)이 아직 여기 버스트 영향권인지 — 창 시작을 0.15 s로 옮기지 말고 원인을 먼저 본다(피드백 > 1 등).

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/ToneMapping.kt core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/Voice.kt core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/KarplusStrongVoice.kt core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/ToneMappingTest.kt core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/KarplusStrongVoiceTest.kt
git commit -m "$(cat <<'MSG'
core/synth: Voice interface, ToneMapping, KarplusStrongVoice (pluck, decay, deactivate)

Fractional delay line with one-pole loop filter; delay shortened by the
filter's phase delay so pitch is within 1 % from E1 to G4. Velocity,
monotonic decay, silence() and auto-deactivation covered by tests.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 4: `KarplusStrongVoice` — 글라이드(슬라이드), 클릭 없는 재피킹, 음색 변경 시 피치 유지

**Files:**
- Modify: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/KarplusStrongVoiceTest.kt` (테스트 추가)
- Modify (필요 시): `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/KarplusStrongVoice.kt`

**Interfaces:**
- Consumes: Task 3 전부
- Produces: 동작 보장 — `setPitch`는 8 ms 글라이드·클릭 없음·목표 주파수 도달, 재피킹은 에너지 회복·이전 음 절단, `setTone`은 피치 불변.

- [ ] **Step 1: 테스트 추가** (`KarplusStrongVoiceTest` 클래스 안에)

```kotlin
    @Test
    fun `setPitch glides to the new pitch without a click`() {
        val v = voice()
        v.noteOn(110f, 0.8f)
        val before = renderSeconds(v, 0.3)
        v.setPitch(123.471f) // two frets up
        val after = renderSeconds(v, 0.4)
        val steadyDelta = SignalAnalysis.maxDelta(before, sec(0.2), sec(0.3))
        val glideDelta = SignalAnalysis.maxDelta(after, 0, sec(0.05))
        assertTrue(glideDelta <= 1.5f * steadyDelta, "glide delta $glideDelta vs steady $steadyDelta")
        assertEquals(123.471, SignalAnalysis.fundamentalHz(after, SR, sec(0.15), sec(0.4)), 1.24)
    }

    @Test
    fun `setPitch on an inactive voice just sets the pitch for the next pluck`() {
        val v = voice()
        v.setPitch(196f)
        assertFalse(v.isActive)
        v.noteOn(196f, 0.8f)
        val out = renderSeconds(v, 0.4)
        assertEquals(196.0, SignalAnalysis.fundamentalHz(out, SR, sec(0.1), sec(0.4)), 1.96)
    }

    @Test
    fun `re-pluck restores energy and cuts the old note`() {
        val v = voice()
        v.setTone(0.6f, 0f)
        v.noteOn(110f, 0.8f)
        val first = renderSeconds(v, 2.0)
        val faded = SignalAnalysis.rms(first, sec(1.95), sec(2.0))
        v.noteOn(196f, 0.8f)
        val second = renderSeconds(v, 0.4)
        val dipDelta = SignalAnalysis.maxDelta(second, 0, sec(0.002))
        assertTrue(dipDelta <= 1.5f * SignalAnalysis.maxDelta(first, sec(1.95), sec(2.0)), "dip clicked: $dipDelta")
        val restored = SignalAnalysis.rms(second, sec(0.01), sec(0.06))
        assertTrue(restored > 3 * faded, "restored $restored !> 3 × faded $faded")
        assertEquals(196.0, SignalAnalysis.fundamentalHz(second, SR, sec(0.1), sec(0.4)), 1.96)
    }

    @Test
    fun `setTone keeps the pitch true`() {
        for (brightness in floatArrayOf(0f, 1f)) {
            val v = voice()
            v.noteOn(110f, 0.8f)
            renderSeconds(v, 0.2)
            v.setTone(brightness, 0.7f)
            val out = renderSeconds(v, 0.5)
            assertEquals(110.0, SignalAnalysis.fundamentalHz(out, SR, sec(0.2), sec(0.5)), 1.1, "brightness $brightness")
        }
    }
```
필요한 import는 이미 파일 상단에 있다 (`assertTrue`, `assertFalse`, `assertEquals`).

- [ ] **Step 2: 실행**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.synth.KarplusStrongVoiceTest" --console=plain
```
Expected: Task 3 구현에 글라이드·재피킹·setTone 재계산이 모두 들어 있으므로 **13개 전부 PASSED**여야 한다. 하나라도 FAIL이면 Step 3.

- [ ] **Step 3: (실패한 경우에만) 원인별 수정**
- `glide ... clicked`: `render`에서 `glideSamplesLeft`가 0이 되는 순간 `delayLength = delayTarget`으로 정확히 맞추는지, `delayStep`이 `(target − current) / samples`인지.
- `re-pluck ... clicked` 또는 `restored`: `noteOn`이 활성 상태에서 `pendingPluck`만 세우고 `envelopeTarget = 0`, `envelopeStep = 1/(2 ms 샘플)` 를 설정하는지; `render`에서 `envelope == 0f && pendingPluck` 일 때 `pluck()` 후 `envelopeTarget = 1f`인지.
- `setTone keeps the pitch`: `setTone`이 `delayTarget = delayFor(currentHz)` 를 다시 계산하는지, `pluck`/`setPitch`가 `currentHz`를 갱신하는지.
수정 후 Step 2 재실행.

- [ ] **Step 4: 커밋**

```bash
git add core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/KarplusStrongVoiceTest.kt core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/KarplusStrongVoice.kt
git commit -m "$(cat <<'MSG'
core/synth: cover glide, click-free re-pluck and tone changes on KarplusStrongVoice

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```
(구현 파일이 바뀌지 않았으면 `git add`에서 그냥 무시된다.)

---

### Task 5: `StringVoices` + `Mixer`

**Files:**
- Create: `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/StringVoices.kt`, `Mixer.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/StringVoicesTest.kt`, `MixerTest.kt`

**Interfaces:**
- Consumes: Task 3 `Voice`, `KarplusStrongVoice`
- Produces: `StringVoices(voices: Array<Voice>)` + 보조 생성자 `StringVoices(sampleRate: Int, stringCount: Int, random: Random = Random())`; `count`, `noteOn(string, hz, velocity)`, `setPitch(string, hz)`, `setTone(brightness, decay)`, `silenceAll()`, `activeCount(): Int`, `render(out, offset, frames)`. `Mixer(masterGain = 0.8f)`: `var masterGain` (0..1 클램프), `process(buf: FloatArray, frames: Int)`, `PRE_GAIN = 0.5f`, `DEFAULT_MASTER_GAIN = 0.8f`. Task 7이 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`StringVoicesTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import kotlin.test.Test
import kotlin.test.assertEquals

class StringVoicesTest {
    /** Records calls; render adds a constant so contributions can be counted. */
    private class RecordingVoice(private val value: Float) : Voice {
        val calls = mutableListOf<String>()
        override var isActive: Boolean = false
        override fun noteOn(hz: Float, velocity: Float) { calls += "noteOn($hz,$velocity)"; isActive = true }
        override fun setPitch(hz: Float) { calls += "setPitch($hz)" }
        override fun setTone(brightness: Float, decay: Float) { calls += "setTone($brightness,$decay)" }
        override fun silence() { calls += "silence"; isActive = false }
        override fun render(out: FloatArray, offset: Int, frames: Int) {
            if (!isActive) return
            for (i in 0 until frames) out[offset + i] += value
        }
    }

    private val a = RecordingVoice(0.25f)
    private val b = RecordingVoice(0.5f)
    private val voices = StringVoices(arrayOf<Voice>(a, b))

    @Test
    fun `routes note commands to the voice of that string`() {
        voices.noteOn(1, 110f, 0.8f)
        voices.setPitch(1, 123f)
        assertEquals(listOf("noteOn(110.0,0.8)", "setPitch(123.0)"), b.calls)
        assertEquals(emptyList(), a.calls)
    }

    @Test
    fun `tone and silence are broadcast`() {
        voices.setTone(0.3f, 0.9f)
        voices.silenceAll()
        assertEquals(listOf("setTone(0.3,0.9)", "silence"), a.calls)
        assertEquals(listOf("setTone(0.3,0.9)", "silence"), b.calls)
    }

    @Test
    fun `render sums active voices and counts them`() {
        val out = FloatArray(8)
        voices.render(out, 0, 8)
        assertEquals(0f, out[0])
        assertEquals(0, voices.activeCount())
        voices.noteOn(0, 110f, 0.8f)
        voices.noteOn(1, 110f, 0.8f)
        voices.render(out, 2, 4)
        assertEquals(0f, out[1])
        assertEquals(0.75f, out[2], 1e-6f)
        assertEquals(0.75f, out[5], 1e-6f)
        assertEquals(0f, out[6])
        assertEquals(2, voices.activeCount())
        assertEquals(2, voices.count)
    }

    @Test
    fun `real voices constructor builds one KarplusStrong voice per string`() {
        val real = StringVoices(48000, 4, java.util.Random(1))
        assertEquals(4, real.count)
        real.noteOn(0, 41.2f, 0.8f)
        real.noteOn(3, 98f, 0.8f)
        assertEquals(2, real.activeCount())
    }
}
```

`MixerTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixerTest {
    @Test
    fun `silence stays silent`() {
        val buf = FloatArray(16)
        Mixer().process(buf, 16)
        for (v in buf) assertEquals(0f, v)
    }

    @Test
    fun `output is always inside -1 and 1 even for a huge input`() {
        val buf = floatArrayOf(4f, -4f, 1000f, -1000f)
        Mixer(masterGain = 1f).process(buf, 4)
        for (v in buf) assertTrue(v > -1f && v < 1f, "$v")
        assertTrue(buf[2] > 0.99f)
        assertTrue(buf[3] < -0.99f)
        assertEquals(2f / 3f, buf[0], 1e-6f) // 4 · 0.5 = 2 → 2 / (1 + 2)
    }

    @Test
    fun `small signals are nearly linear and scale with master gain`() {
        val buf = floatArrayOf(0.1f, 0.1f)
        val m = Mixer(masterGain = 1f)
        m.process(buf, 1)
        assertEquals(0.05f / 1.05f, buf[0], 1e-6f)
        m.masterGain = 0.5f
        m.process(buf, 2)
        assertEquals(0.025f / 1.025f, buf[1], 1e-6f)
    }

    @Test
    fun `master gain is clamped and zero mutes`() {
        val m = Mixer(masterGain = 3f)
        assertEquals(1f, m.masterGain)
        m.masterGain = -1f
        assertEquals(0f, m.masterGain)
        val buf = floatArrayOf(0.7f, -0.7f)
        m.process(buf, 2)
        assertEquals(0f, buf[0])
        assertEquals(0f, buf[1])
    }

    @Test
    fun `only the first frames are processed`() {
        val buf = floatArrayOf(1f, 1f, 1f)
        Mixer(masterGain = 1f).process(buf, 2)
        assertEquals(1f, buf[2])
        assertTrue(buf[1] < 1f)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.synth.StringVoicesTest" --tests "com.sproutgreen.getyourguitar.core.synth.MixerTest" --console=plain
```
Expected: FAIL — `Unresolved reference 'StringVoices'`, `'Mixer'`.

- [ ] **Step 3: 구현**

`StringVoices.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import java.util.Random

/** One voice per string. A new command for a string always wins over what that string was doing (spec 5.2). */
class StringVoices(private val voices: Array<Voice>) {
    constructor(sampleRate: Int, stringCount: Int, random: Random = Random()) :
        this(Array<Voice>(stringCount) { KarplusStrongVoice(sampleRate, random) })

    val count: Int get() = voices.size

    fun noteOn(string: Int, hz: Float, velocity: Float) {
        voices[string].noteOn(hz, velocity)
    }

    fun setPitch(string: Int, hz: Float) {
        voices[string].setPitch(hz)
    }

    fun setTone(brightness: Float, decay: Float) {
        for (v in voices) v.setTone(brightness, decay)
    }

    fun silenceAll() {
        for (v in voices) v.silence()
    }

    fun activeCount(): Int {
        var n = 0
        for (v in voices) if (v.isActive) n++
        return n
    }

    /** Adds every active voice into out[offset until offset + frames]. */
    fun render(out: FloatArray, offset: Int, frames: Int) {
        for (v in voices) v.render(out, offset, frames)
    }
}
```

`Mixer.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.synth

import kotlin.math.abs

/** Pre-gain × master gain, then soft clip y = x / (1 + |x|) so four ringing strings never hard-clip (spec 5.2). */
class Mixer(masterGain: Float = DEFAULT_MASTER_GAIN) {
    companion object {
        const val DEFAULT_MASTER_GAIN = 0.8f
        const val PRE_GAIN = 0.5f
    }

    var masterGain: Float = masterGain.coerceIn(0f, 1f)
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /** In place on buf[0 until frames]. Output is always in (−1, 1). */
    fun process(buf: FloatArray, frames: Int) {
        val g = PRE_GAIN * masterGain
        for (i in 0 until frames) {
            val x = buf[i] * g
            buf[i] = x / (1f + abs(x))
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --console=plain
```
Expected: 전체 PASSED (`StringVoicesTest` 4, `MixerTest` 5 포함).

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/StringVoices.kt core/src/main/kotlin/com/sproutgreen/getyourguitar/core/synth/Mixer.kt core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/StringVoicesTest.kt core/src/test/kotlin/com/sproutgreen/getyourguitar/core/synth/MixerTest.kt
git commit -m "$(cat <<'MSG'
core/synth: StringVoices (one voice per string) and Mixer (soft clip)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 6: `CommandQueue` (SPSC 링버퍼) + 커맨드 정의

**Files:**
- Create: `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/engine/Command.kt`, `CommandQueue.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/engine/CommandQueueTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `object CommandType { NOTE_ON=1, SLIDE=2, ALL_NOTES_OFF=3, SET_MASTER_GAIN=4, SET_BRIGHTNESS=5, SET_DECAY=6, METRONOME_START=7, METRONOME_STOP=8, SET_BPM=9, SET_METRONOME_GAIN=10 }`; `fun interface CommandHandler { fun handle(type: Int, a: Int, b: Int, f: Float) }`; `fun interface AudioRenderer { fun render(out: FloatArray, frames: Int) }`; `CommandQueue(capacity = 256)`: `offer(type, a = 0, b = 0, f = 0f): Boolean`, `drain(handler): Int`, `val dropped: Int`, `val isEmpty: Boolean`. Task 7·9가 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`CommandQueueTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandQueueTest {
    private fun drainAll(q: CommandQueue): List<String> {
        val seen = mutableListOf<String>()
        q.drain { type, a, b, f -> seen += "$type:$a:$b:$f" }
        return seen
    }

    @Test
    fun `commands come out in order with exact arguments`() {
        val q = CommandQueue()
        assertTrue(q.isEmpty)
        assertTrue(q.offer(CommandType.NOTE_ON, 2, 7))
        assertTrue(q.offer(CommandType.SET_MASTER_GAIN, f = 0.123f))
        assertTrue(q.offer(CommandType.ALL_NOTES_OFF))
        assertFalse(q.isEmpty)
        assertEquals(listOf("1:2:7:0.0", "4:0:0:0.123", "3:0:0:0.0"), drainAll(q))
        assertTrue(q.isEmpty)
    }

    @Test
    fun `drain returns the number handled and zero when empty`() {
        val q = CommandQueue()
        assertEquals(0, q.drain { _, _, _, _ -> })
        q.offer(CommandType.SLIDE, 1, 2)
        q.offer(CommandType.SLIDE, 1, 3)
        assertEquals(2, q.drain { _, _, _, _ -> })
        assertEquals(0, q.drain { _, _, _, _ -> })
    }

    @Test
    fun `capacity minus one commands fit and the next one is dropped and counted`() {
        val q = CommandQueue(capacity = 4)
        assertTrue(q.offer(1)); assertTrue(q.offer(2)); assertTrue(q.offer(3))
        assertFalse(q.offer(4))
        assertEquals(1, q.dropped)
        assertEquals(listOf("1:0:0:0.0", "2:0:0:0.0", "3:0:0:0.0"), drainAll(q))
        assertTrue(q.offer(5))
        assertEquals(1, q.dropped)
    }

    @Test
    fun `wraps around indefinitely`() {
        val q = CommandQueue(capacity = 4)
        for (round in 0 until 1000) {
            assertTrue(q.offer(CommandType.NOTE_ON, round, round + 1, round.toFloat()))
            assertTrue(q.offer(CommandType.SLIDE, round, round + 2))
            assertEquals(listOf("1:$round:${round + 1}:$round.0", "2:$round:${round + 2}:0.0"), drainAll(q))
        }
        assertEquals(0, q.dropped)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.engine.CommandQueueTest" --console=plain
```
Expected: FAIL — `Unresolved reference 'CommandQueue'`.

- [ ] **Step 3: 구현**

`Command.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.engine

/** Command types carried through [CommandQueue]. Argument use per type (spec 5.4). */
object CommandType {
    const val NOTE_ON = 1            // a = string, b = fret
    const val SLIDE = 2              // a = string, b = fret
    const val ALL_NOTES_OFF = 3
    const val SET_MASTER_GAIN = 4    // f
    const val SET_BRIGHTNESS = 5     // f
    const val SET_DECAY = 6          // f
    const val METRONOME_START = 7    // handled from M3
    const val METRONOME_STOP = 8     // handled from M3
    const val SET_BPM = 9            // a (M3)
    const val SET_METRONOME_GAIN = 10 // f (M3)
}

/** Consumer-side callback. Implemented by the engine itself so draining allocates nothing. */
fun interface CommandHandler {
    fun handle(type: Int, a: Int, b: Int, f: Float)
}

/** Pull renderer: fills out[0 until frames]. Implemented by SynthEngine; called by the audio output thread. */
fun interface AudioRenderer {
    fun render(out: FloatArray, frames: Int)
}
```

`CommandQueue.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.engine

/**
 * Single-producer (UI thread) / single-consumer (audio thread) ring of fixed-size slots.
 * Each slot is four ints: type, a, b, float bits. Capacity − 1 commands fit; a full queue drops
 * the new command and counts it. No allocation after construction (spec 4.3).
 */
class CommandQueue(private val capacity: Int = DEFAULT_CAPACITY) {
    companion object {
        const val DEFAULT_CAPACITY = 256
        private const val INTS_PER_SLOT = 4
    }

    init {
        require(capacity >= 2) { "capacity must be at least 2" }
    }

    private val slots = IntArray(capacity * INTS_PER_SLOT)

    @Volatile private var head = 0 // next slot to read (consumer owns)
    @Volatile private var tail = 0 // next slot to write (producer owns)

    /** Commands refused because the queue was full. Producer-side counter. */
    @Volatile var dropped: Int = 0
        private set

    val isEmpty: Boolean get() = head == tail

    /** Producer side. Returns false (and counts a drop) when full. */
    fun offer(type: Int, a: Int = 0, b: Int = 0, f: Float = 0f): Boolean {
        val t = tail
        val next = if (t + 1 == capacity) 0 else t + 1
        if (next == head) {
            dropped++
            return false
        }
        val i = t * INTS_PER_SLOT
        slots[i] = type
        slots[i + 1] = a
        slots[i + 2] = b
        slots[i + 3] = f.toRawBits()
        tail = next // volatile write publishes the slot
        return true
    }

    /** Consumer side. Handles every pending command in FIFO order; returns how many. */
    fun drain(handler: CommandHandler): Int {
        var h = head
        val t = tail // volatile read acquires the producer's writes
        var n = 0
        while (h != t) {
            val i = h * INTS_PER_SLOT
            handler.handle(slots[i], slots[i + 1], slots[i + 2], Float.fromBits(slots[i + 3]))
            h = if (h + 1 == capacity) 0 else h + 1
            n++
        }
        head = h
        return n
    }
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.engine.CommandQueueTest" --console=plain
```
Expected: 4 PASSED.

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/sproutgreen/getyourguitar/core/engine/Command.kt core/src/main/kotlin/com/sproutgreen/getyourguitar/core/engine/CommandQueue.kt core/src/test/kotlin/com/sproutgreen/getyourguitar/core/engine/CommandQueueTest.kt
git commit -m "$(cat <<'MSG'
core/engine: CommandType, CommandHandler, AudioRenderer, SPSC CommandQueue

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 7: `SynthEngine` + `EngineStats`

**Files:**
- Create: `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/engine/EngineStats.kt`, `SynthEngine.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/engine/SynthEngineTest.kt`

**Interfaces:**
- Consumes: Task 1 `Fretboard`/`Tuning`, Task 5 `StringVoices`/`Mixer`, Task 6 큐·`AudioRenderer`·`CommandHandler`
- Produces: `SynthEngine(sampleRate: Int, tuning = Tuning.STANDARD_BASS_4, voices = StringVoices(sampleRate, tuning.stringCount)) : AudioRenderer, CommandHandler`; `send(type, a = 0, b = 0, f = 0f): Boolean` (UI 스레드), `render(out, frames)` (오디오 스레드), `val stats: EngineStats`, `val droppedCommands: Int`, `VELOCITY = 0.8f`. `EngineStats { @Volatile var currentBeat = -1; @Volatile var activeVoices = 0 }`. Task 9(앱)가 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`SynthEngineTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.engine

import com.sproutgreen.getyourguitar.core.synth.StringVoices
import com.sproutgreen.getyourguitar.core.testutil.SignalAnalysis
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthEngineTest {
    private val sr = 48000
    private fun engine() = SynthEngine(sr, voices = StringVoices(sr, 4, Random(7)))
    private fun sec(s: Double) = (sr * s).toInt()

    /** Renders [seconds] through the engine in 256-frame chunks and concatenates. */
    private fun render(e: SynthEngine, seconds: Double): FloatArray {
        val total = sec(seconds)
        val out = FloatArray(total)
        val chunk = FloatArray(256)
        var i = 0
        while (i < total) {
            val n = minOf(256, total - i)
            e.render(chunk, n)
            System.arraycopy(chunk, 0, out, i, n)
            i += n
        }
        return out
    }

    @Test
    fun `renders silence when nothing was sent`() {
        val out = render(engine(), 0.1)
        assertEquals(0f, SignalAnalysis.peak(out, 0, out.size))
        assertEquals(0, engine().stats.activeVoices)
    }

    @Test
    fun `NOTE_ON on the open E string sounds at 41_2 Hz`() {
        val e = engine()
        assertTrue(e.send(CommandType.NOTE_ON, 0, 0))
        val out = render(e, 0.6)
        assertTrue(SignalAnalysis.peak(out, 0, out.size) > 0.01f)
        assertEquals(41.203, SignalAnalysis.fundamentalHz(out, sr, sec(0.2), sec(0.6)), 0.412)
        assertEquals(1, e.stats.activeVoices)
    }

    @Test
    fun `SLIDE moves the pitch of the ringing string`() {
        val e = engine()
        e.send(CommandType.NOTE_ON, 1, 5) // A1 + 5 = D2 73.4 Hz
        render(e, 0.3)
        e.send(CommandType.SLIDE, 1, 7)  // E2 82.4 Hz
        val out = render(e, 0.5)
        assertEquals(82.407, SignalAnalysis.fundamentalHz(out, sr, sec(0.2), sec(0.5)), 0.83)
    }

    @Test
    fun `ALL_NOTES_OFF silences immediately`() {
        val e = engine()
        e.send(CommandType.NOTE_ON, 2, 3)
        e.send(CommandType.NOTE_ON, 3, 3)
        render(e, 0.1)
        e.send(CommandType.ALL_NOTES_OFF)
        val out = render(e, 0.05)
        assertEquals(0f, SignalAnalysis.peak(out, 0, out.size))
        assertEquals(0, e.stats.activeVoices)
    }

    @Test
    fun `invalid positions are ignored without throwing`() {
        val e = engine()
        e.send(CommandType.NOTE_ON, 9, 0)
        e.send(CommandType.NOTE_ON, 0, 99)
        e.send(CommandType.SLIDE, -1, 0)
        e.send(99, 1, 2, 3f) // unknown type
        val out = render(e, 0.05)
        assertEquals(0f, SignalAnalysis.peak(out, 0, out.size))
    }

    @Test
    fun `master gain zero mutes and tone commands are accepted`() {
        val e = engine()
        e.send(CommandType.SET_MASTER_GAIN, f = 0f)
        e.send(CommandType.SET_BRIGHTNESS, f = 1f)
        e.send(CommandType.SET_DECAY, f = 0f)
        e.send(CommandType.NOTE_ON, 0, 0)
        val muted = render(e, 0.1)
        assertEquals(0f, SignalAnalysis.peak(muted, 0, muted.size))
        e.send(CommandType.SET_MASTER_GAIN, f = 0.8f)
        val audible = render(e, 0.1)
        assertTrue(SignalAnalysis.peak(audible, 0, audible.size) > 0.01f)
    }

    @Test
    fun `output stays inside -1 and 1 with all four strings ringing`() {
        val e = engine()
        for (s in 0..3) e.send(CommandType.NOTE_ON, s, 0)
        val out = render(e, 0.2)
        assertTrue(SignalAnalysis.peak(out, 0, out.size) < 1f)
        assertEquals(4, e.stats.activeVoices)
    }

    @Test
    fun `dropped commands are counted`() {
        val e = engine()
        for (i in 0 until 300) e.send(CommandType.SLIDE, 0, 1)
        assertEquals(300 - (CommandQueue.DEFAULT_CAPACITY - 1), e.droppedCommands)
        render(e, 0.01)
        assertTrue(e.send(CommandType.SLIDE, 0, 1))
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.engine.SynthEngineTest" --console=plain
```
Expected: FAIL — `Unresolved reference 'SynthEngine'`.

- [ ] **Step 3: 구현**

`EngineStats.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.engine

/** Written by the audio thread, read by the UI once per frame. No callbacks cross the thread boundary (spec 4.3). */
class EngineStats {
    /** 0..3 while the metronome runs (M3), −1 otherwise. */
    @Volatile var currentBeat: Int = -1

    @Volatile var activeVoices: Int = 0
}
```

`SynthEngine.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.engine

import com.sproutgreen.getyourguitar.core.music.Fretboard
import com.sproutgreen.getyourguitar.core.music.Tuning
import com.sproutgreen.getyourguitar.core.synth.Mixer
import com.sproutgreen.getyourguitar.core.synth.StringVoices
import com.sproutgreen.getyourguitar.core.synth.ToneMapping

/**
 * The pull renderer behind the audio output (spec 4.2 boundary 1).
 * render(): drain queue → voices add into the buffer → mixer. Audio-thread code path: no allocation.
 */
class SynthEngine(
    val sampleRate: Int,
    tuning: Tuning = Tuning.STANDARD_BASS_4,
    private val voices: StringVoices = StringVoices(sampleRate, tuning.stringCount),
) : AudioRenderer, CommandHandler {
    companion object {
        /** Touch has no velocity in v1 (spec 5.2). */
        const val VELOCITY = 0.8f
    }

    private val fretboard = Fretboard(tuning)
    private val queue = CommandQueue()
    private val mixer = Mixer()
    val stats = EngineStats()
    private var brightness = ToneMapping.DEFAULT_BRIGHTNESS
    private var decay = ToneMapping.DEFAULT_DECAY

    val droppedCommands: Int get() = queue.dropped

    /** Producer side (UI thread). False if the queue was full. */
    fun send(type: Int, a: Int = 0, b: Int = 0, f: Float = 0f): Boolean = queue.offer(type, a, b, f)

    /** Consumer side (audio thread). Fills out[0 until frames]. */
    override fun render(out: FloatArray, frames: Int) {
        queue.drain(this)
        java.util.Arrays.fill(out, 0, frames, 0f)
        voices.render(out, 0, frames)
        mixer.process(out, frames)
        stats.activeVoices = voices.activeCount()
    }

    override fun handle(type: Int, a: Int, b: Int, f: Float) {
        when (type) {
            CommandType.NOTE_ON -> if (fretboard.isValid(a, b)) {
                voices.noteOn(a, fretboard.hzAt(a, b).toFloat(), VELOCITY)
            }
            CommandType.SLIDE -> if (fretboard.isValid(a, b)) {
                voices.setPitch(a, fretboard.hzAt(a, b).toFloat())
            }
            CommandType.ALL_NOTES_OFF -> voices.silenceAll()
            CommandType.SET_MASTER_GAIN -> mixer.masterGain = f
            CommandType.SET_BRIGHTNESS -> {
                brightness = f.coerceIn(0f, 1f)
                voices.setTone(brightness, decay)
            }
            CommandType.SET_DECAY -> {
                decay = f.coerceIn(0f, 1f)
                voices.setTone(brightness, decay)
            }
            else -> Unit // metronome commands are handled from M3; unknown types are ignored
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --console=plain
```
Expected: 전체 PASSED (`SynthEngineTest` 8 포함).

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/sproutgreen/getyourguitar/core/engine/EngineStats.kt core/src/main/kotlin/com/sproutgreen/getyourguitar/core/engine/SynthEngine.kt core/src/test/kotlin/com/sproutgreen/getyourguitar/core/engine/SynthEngineTest.kt
git commit -m "$(cat <<'MSG'
core/engine: SynthEngine pull renderer with command handling and EngineStats

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 8: 지판 기하 — `FretLayout`, `EqualFretLayout`, `FretboardGeometry`

**Files:**
- Create: `core/src/main/kotlin/com/sproutgreen/getyourguitar/core/fretboard/FretLayout.kt`, `FretboardGeometry.kt`
- Test: `core/src/test/kotlin/com/sproutgreen/getyourguitar/core/fretboard/FretboardGeometryTest.kt`

**Interfaces:**
- Consumes: Task 1 `FretPosition`
- Produces: `interface FretLayout { val visibleCells: Float; fun xOf(u: Float, scroll: Float, widthPx: Float): Float; fun uAt(x: Float, scroll: Float, widthPx: Float): Float }`; `EqualFretLayout(visibleCells = 12f)`; `FretboardGeometry(stringCount = 4, fretCount = 24, layout = EqualFretLayout())` with `minScroll`, `maxScroll`, `clampScroll(s)`, `snapScroll(s)`, `fretAtU(u): Int` (−1 = off neck), `stringAtY(y, heightPx): Int`, `stringCenterY(string, heightPx): Float`, `cellCenterU(fret): Float`, `visibleFretRange(scroll): IntRange`, `hitTest(x, y, scroll, widthPx, heightPx): FretPosition?`, `SINGLE_MARKERS`, `DOUBLE_MARKERS`, `OPEN_CELL_START = -1f`. Task 10·11이 사용.

좌표계(스펙 6.2): 지판 좌표 `u`(단위 셀). 개방현 셀 = [−1, 0), 프렛 n 셀 = [n−1, n), 프렛 와이어는 u = n (u = 0이 너트). 뷰포트 폭 12셀, scroll ∈ [−1, 12]. 줄 인덱스 0 = E(맨 아래), 3 = G(맨 위).

- [ ] **Step 1: 실패하는 테스트 작성**

`FretboardGeometryTest.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.fretboard

import com.sproutgreen.getyourguitar.core.music.FretPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FretboardGeometryTest {
    private val g = FretboardGeometry()
    private val w = 1200f // 12 cells → 100 px each
    private val h = 400f  // 4 strings → 100 px bands

    @Test
    fun `equal layout maps u to x and back`() {
        val l = EqualFretLayout()
        assertEquals(0f, l.xOf(-1f, -1f, w))
        assertEquals(100f, l.xOf(0f, -1f, w))
        assertEquals(1200f, l.xOf(11f, -1f, w))
        assertEquals(0f, l.xOf(12f, 12f, w))
        assertEquals(3.5f, l.uAt(l.xOf(3.5f, 2f, w), 2f, w), 1e-5f)
        assertEquals(-0.5f, l.uAt(50f, -1f, w), 1e-5f)
    }

    @Test
    fun `fret from u follows the cell convention`() {
        assertEquals(0, g.fretAtU(-1f))
        assertEquals(0, g.fretAtU(-0.01f))
        assertEquals(1, g.fretAtU(0f))
        assertEquals(1, g.fretAtU(0.99f))
        assertEquals(12, g.fretAtU(11.5f))
        assertEquals(24, g.fretAtU(23.99f))
        assertEquals(-1, g.fretAtU(24f))
        assertEquals(-1, g.fretAtU(-1.01f))
    }

    @Test
    fun `strings are G on top and E at the bottom`() {
        assertEquals(3, g.stringAtY(0f, h))
        assertEquals(3, g.stringAtY(99f, h))
        assertEquals(2, g.stringAtY(100f, h))
        assertEquals(0, g.stringAtY(399f, h))
        assertEquals(0, g.stringAtY(1000f, h)) // clamped
        assertEquals(50f, g.stringCenterY(3, h))
        assertEquals(350f, g.stringCenterY(0, h))
    }

    @Test
    fun `hit test at the open position`() {
        assertEquals(FretPosition(3, 0), g.hitTest(50f, 10f, -1f, w, h))
        assertEquals(FretPosition(0, 1), g.hitTest(150f, 390f, -1f, w, h))
        assertEquals(FretPosition(1, 11), g.hitTest(1199f, 250f, -1f, w, h))
    }

    @Test
    fun `hit test scrolled to the top of the neck`() {
        assertEquals(FretPosition(2, 13), g.hitTest(10f, 150f, 12f, w, h))
        assertEquals(FretPosition(2, 24), g.hitTest(1199f, 150f, 12f, w, h))
    }

    @Test
    fun `hit test outside the neck is null`() {
        assertNull(g.hitTest(-1f, 10f, -1f, w, h))
        assertNull(g.hitTest(1200f, 10f, -1f, w, h))
        assertNull(g.hitTest(10f, -1f, -1f, w, h))
        assertNull(g.hitTest(10f, 400f, -1f, w, h))
    }

    @Test
    fun `scroll clamps to -1..12 and snaps to whole cells`() {
        assertEquals(-1f, g.minScroll)
        assertEquals(12f, g.maxScroll)
        assertEquals(-1f, g.clampScroll(-5f))
        assertEquals(12f, g.clampScroll(20f))
        assertEquals(3.4f, g.clampScroll(3.4f))
        assertEquals(3f, g.snapScroll(3.4f))
        assertEquals(4f, g.snapScroll(3.6f))
        assertEquals(12f, g.snapScroll(12.7f))
        assertEquals(-1f, g.snapScroll(-1.4f))
    }

    @Test
    fun `visible fret range and cell centres`() {
        assertEquals(0..11, g.visibleFretRange(-1f))
        assertEquals(13..24, g.visibleFretRange(12f))
        assertEquals(1..12, g.visibleFretRange(0f))
        assertEquals(-0.5f, g.cellCenterU(0))
        assertEquals(4.5f, g.cellCenterU(5))
    }

    @Test
    fun `marker frets`() {
        assertEquals(listOf(3, 5, 7, 9, 15, 17, 19, 21), FretboardGeometry.SINGLE_MARKERS.toList())
        assertEquals(listOf(12, 24), FretboardGeometry.DOUBLE_MARKERS.toList())
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.fretboard.FretboardGeometryTest" --console=plain
```
Expected: FAIL — `Unresolved reference 'FretboardGeometry'`.

- [ ] **Step 3: 구현**

`FretLayout.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.fretboard

/**
 * Neck coordinate u (in cells) ↔ screen x. Open-string cell = [−1, 0), fret n cell = [n − 1, n),
 * fret wire n at u = n (u = 0 is the nut). scroll = u at the left edge of the viewport.
 * v2 adds a real-spacing implementation behind this interface (spec 3, "실제 프렛 간격").
 */
interface FretLayout {
    val visibleCells: Float
    fun xOf(u: Float, scroll: Float, widthPx: Float): Float
    fun uAt(x: Float, scroll: Float, widthPx: Float): Float
}

/** Every cell has the same width (spec 6.2, v1). */
class EqualFretLayout(override val visibleCells: Float = DEFAULT_VISIBLE_CELLS) : FretLayout {
    companion object {
        const val DEFAULT_VISIBLE_CELLS = 12f
    }

    fun cellWidth(widthPx: Float): Float = widthPx / visibleCells

    override fun xOf(u: Float, scroll: Float, widthPx: Float): Float = (u - scroll) * cellWidth(widthPx)

    override fun uAt(x: Float, scroll: Float, widthPx: Float): Float = scroll + x / cellWidth(widthPx)
}
```

`FretboardGeometry.kt`:
```kotlin
package com.sproutgreen.getyourguitar.core.fretboard

import com.sproutgreen.getyourguitar.core.music.FretPosition
import kotlin.math.floor

/** Pure geometry of the playable neck: hit testing, scroll limits, marker and label positions. */
class FretboardGeometry(
    val stringCount: Int = 4,
    val fretCount: Int = 24,
    val layout: FretLayout = EqualFretLayout(),
) {
    companion object {
        const val OPEN_CELL_START = -1f
        val SINGLE_MARKERS = intArrayOf(3, 5, 7, 9, 15, 17, 19, 21)
        val DOUBLE_MARKERS = intArrayOf(12, 24)
    }

    val minScroll: Float get() = OPEN_CELL_START
    val maxScroll: Float get() = fretCount - layout.visibleCells

    fun clampScroll(scroll: Float): Float = scroll.coerceIn(minScroll, maxScroll)

    fun snapScroll(scroll: Float): Float = clampScroll(Math.round(scroll).toFloat())

    /** Fret whose cell contains u, or −1 when u is off the neck. */
    fun fretAtU(u: Float): Int {
        if (u < OPEN_CELL_START || u >= fretCount) return -1
        return floor(u).toInt() + 1
    }

    /** String index for a y inside a neck of [heightPx]: top band = highest string (G = 3), bottom = E = 0. */
    fun stringAtY(y: Float, heightPx: Float): Int {
        val band = floor(y / (heightPx / stringCount)).toInt().coerceIn(0, stringCount - 1)
        return stringCount - 1 - band
    }

    fun stringCenterY(string: Int, heightPx: Float): Float {
        val band = stringCount - 1 - string
        return (band + 0.5f) * heightPx / stringCount
    }

    /** Centre u of a fret's cell (open-string cell is centred at −0.5). */
    fun cellCenterU(fret: Int): Float = fret - 0.5f

    /** Frets whose cells are at least partly visible at [scroll]. */
    fun visibleFretRange(scroll: Float): IntRange {
        val first = fretAtU(scroll).coerceAtLeast(0)
        val lastCandidate = fretAtU(scroll + layout.visibleCells - 0.001f)
        val last = if (lastCandidate < 0) fretCount else lastCandidate
        return first..last
    }

    /** (string, fret) under a point inside a neck of widthPx × heightPx, or null if off the neck. */
    fun hitTest(x: Float, y: Float, scroll: Float, widthPx: Float, heightPx: Float): FretPosition? {
        if (x < 0f || x >= widthPx || y < 0f || y >= heightPx) return null
        val fret = fretAtU(layout.uAt(x, scroll, widthPx))
        if (fret < 0) return null
        return FretPosition(stringAtY(y, heightPx), fret)
    }
}
```

- [ ] **Step 4: 통과 확인**

```bash
./gradlew.bat :core:test --tests "com.sproutgreen.getyourguitar.core.fretboard.FretboardGeometryTest" --console=plain
```
Expected: 9 PASSED.

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/sproutgreen/getyourguitar/core/fretboard core/src/test/kotlin/com/sproutgreen/getyourguitar/core/fretboard
git commit -m "$(cat <<'MSG'
core/fretboard: FretLayout, EqualFretLayout, FretboardGeometry (hit test, scroll, markers)

Pure geometry lives in :core so it is unit-tested in 아키랩; v2's real fret
spacing plugs in behind FretLayout.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 9: `:app` 오디오 출력 — `AudioOutput`, `AudioTrackOutput`, `AudioController`, 수명주기

**Files:**
- Create: `app/src/main/kotlin/com/sproutgreen/getyourguitar/audio/AudioOutput.kt`, `AudioTrackOutput.kt`, `AudioController.kt`
- Modify: `app/src/main/kotlin/com/sproutgreen/getyourguitar/MainActivity.kt`

**Interfaces:**
- Consumes: Task 6 `AudioRenderer`, `CommandType`; Task 7 `SynthEngine`
- Produces: `interface AudioOutput { val sampleRate: Int; val framesPerBuffer: Int; val underrunCount: Int; val isRunning: Boolean; val lastError: String?; fun start(renderer: AudioRenderer); fun stop() }`; `AudioTrackOutput(context, bufferChunks = 2)`; `AudioController(context, bufferChunks = 2)` with `engine`, `errorMessage: String?` (Compose state), `sampleRate`, `framesPerBuffer`, `underrunCount`, `isRunning`, `start()`, `stop()`, `noteOn(string, fret)`, `slide(string, fret)`, `refreshError()`. Task 10·11이 사용.

이 Task는 실기기 없이는 실행 검증이 불가능하다 — **`assembleDebug` + `lintDebug` 통과가 완료 기준**이고 소리는 검증 환경 몫이다.

- [ ] **Step 1: 실패 확인 (컴파일)** — 아직 파일이 없으므로 이 Step은 "Step 3 후 빌드"로 대체. 먼저 구현.

- [ ] **Step 2: 구현**

`AudioOutput.kt`:
```kotlin
package com.sproutgreen.getyourguitar.audio

import com.sproutgreen.getyourguitar.core.engine.AudioRenderer

/**
 * Output backend boundary (spec 4.2 boundary 1). v1 = AudioTrack; Oboe could replace it later.
 * sampleRate / framesPerBuffer are known at construction so the engine can be built before start().
 */
interface AudioOutput {
    val sampleRate: Int
    val framesPerBuffer: Int
    val underrunCount: Int
    val isRunning: Boolean

    /** Set when the audio thread stopped on its own (write error / exception). */
    val lastError: String?

    /** @throws IllegalStateException when the platform refuses to create a track. */
    fun start(renderer: AudioRenderer)

    fun stop()
}
```

`AudioTrackOutput.kt`:
```kotlin
package com.sproutgreen.getyourguitar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.sproutgreen.getyourguitar.core.engine.AudioRenderer

/**
 * AudioTrack in low-latency mode, mono float, native sample rate and frames-per-buffer,
 * driven by a dedicated URGENT_AUDIO thread that pulls the renderer one native buffer at a time
 * (spec 6.1). Buffer = framesPerBuffer × bufferChunks (never below getMinBufferSize).
 */
class AudioTrackOutput(
    context: Context,
    private val bufferChunks: Int = DEFAULT_BUFFER_CHUNKS,
) : AudioOutput {
    companion object {
        const val DEFAULT_BUFFER_CHUNKS = 2
        private const val FALLBACK_SAMPLE_RATE = 48000
        private const val FALLBACK_FRAMES_PER_BUFFER = 256
        private const val BYTES_PER_FLOAT = 4
        private const val UNDERRUN_POLL_EVERY = 50
    }

    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    override val sampleRate: Int =
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.takeIf { it > 0 }
            ?: FALLBACK_SAMPLE_RATE

    override val framesPerBuffer: Int =
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()?.takeIf { it > 0 }
            ?: FALLBACK_FRAMES_PER_BUFFER

    @Volatile override var underrunCount: Int = 0
        private set

    @Volatile override var isRunning: Boolean = false
        private set

    @Volatile override var lastError: String? = null
        private set

    private var thread: Thread? = null

    override fun start(renderer: AudioRenderer) {
        if (isRunning) return
        val track = createTrack(lowLatency = true)
            ?: createTrack(lowLatency = false)
            ?: throw IllegalStateException("AudioTrack could not be initialised ($sampleRate Hz, $framesPerBuffer frames/buffer)")
        lastError = null
        underrunCount = 0
        isRunning = true
        val buffer = FloatArray(framesPerBuffer)
        thread = Thread({ runLoop(track, renderer, buffer) }, "gyg-audio").also { it.start() }
    }

    override fun stop() {
        isRunning = false
        thread?.join(1000)
        thread = null
    }

    private fun createTrack(lowLatency: Boolean): AudioTrack? {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) return null
        val wantedBytes = framesPerBuffer * bufferChunks * BYTES_PER_FLOAT
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(if (lowLatency) maxOf(minBytes, wantedBytes) else minBytes * 2)
        if (lowLatency) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        val track = try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: UnsupportedOperationException) {
            return null
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        return track
    }

    private fun runLoop(track: AudioTrack, renderer: AudioRenderer, buffer: FloatArray) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            track.play()
            var iterations = 0
            while (isRunning) {
                renderer.render(buffer, buffer.size)
                val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    lastError = "AudioTrack.write returned $written"
                    break
                }
                iterations++
                if (iterations % UNDERRUN_POLL_EVERY == 0) underrunCount = track.underrunCount
            }
        } catch (t: Throwable) {
            lastError = t.toString()
        } finally {
            isRunning = false
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }
    }
}
```

`AudioController.kt`:
```kotlin
package com.sproutgreen.getyourguitar.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sproutgreen.getyourguitar.core.engine.CommandType
import com.sproutgreen.getyourguitar.core.engine.SynthEngine

/** Owns the output backend and the engine for the Activity's lifetime; the UI talks only to this (spec 6.1). */
class AudioController(
    context: Context,
    bufferChunks: Int = AudioTrackOutput.DEFAULT_BUFFER_CHUNKS,
) {
    private val output: AudioOutput = AudioTrackOutput(context.applicationContext, bufferChunks)
    val engine: SynthEngine = SynthEngine(output.sampleRate)

    /** Non-null when audio could not start or the audio thread died; the screen shows it as a banner. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    val sampleRate: Int get() = output.sampleRate
    val framesPerBuffer: Int get() = output.framesPerBuffer
    val underrunCount: Int get() = output.underrunCount
    val isRunning: Boolean get() = output.isRunning

    fun start() {
        try {
            output.start(engine)
            errorMessage = null
        } catch (e: IllegalStateException) {
            errorMessage = "오디오 초기화 실패: ${e.message}"
        }
    }

    fun stop() {
        engine.send(CommandType.ALL_NOTES_OFF) // drained by the first render after the next start
        output.stop()
    }

    fun noteOn(string: Int, fret: Int) {
        engine.send(CommandType.NOTE_ON, string, fret)
    }

    fun slide(string: Int, fret: Int) {
        engine.send(CommandType.SLIDE, string, fret)
    }

    /** Poll from the UI (about once a second) to surface an audio-thread failure. */
    fun refreshError() {
        val error = output.lastError
        if (error != null && !output.isRunning) errorMessage = "오디오 스레드 중단: $error"
    }
}
```

`MainActivity.kt` (전체 교체 — Task 10에서 다시 화면을 바꾼다):
```kotlin
package com.sproutgreen.getyourguitar

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.core.CoreInfo

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // spec 6.5
        audio = AudioController(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = CoreInfo.APP_NAME)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audio.start()
    }

    override fun onStop() {
        audio.stop()
        super.onStop()
    }
}
```

- [ ] **Step 3: 컴파일·린트 확인**

```bash
export JAVA_HOME='E:\yjane.kim\tools\jdk-17.0.20.1+1' GRADLE_USER_HOME='E:\yjane.kim\tools\gradle-user-home'; cd /e/yjane.kim/project/get-your-guitar
./gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
tail -1 app/build/reports/lint-results-debug.txt
```
Expected: BUILD SUCCESSFUL, lint `0 errors` (경고는 M0의 4개 그대로). 컴파일 에러가 나면 import 누락(`androidx.compose.runtime.getValue/setValue`) 또는 `AudioRenderer` 패키지 경로를 확인.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/kotlin/com/sproutgreen/getyourguitar/audio app/src/main/kotlin/com/sproutgreen/getyourguitar/MainActivity.kt
git commit -m "$(cat <<'MSG'
app/audio: AudioTrack low-latency output on a dedicated thread, AudioController, lifecycle

Engine is pulled one native buffer at a time; start/stop follow
onStart/onStop; screen stays on. Runtime behaviour 검증 환경에서 확인 필요.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 10: 테마 + 지판 그리기 + 화면 레이아웃 (제스처 제외)

**Files:**
- Create: `app/src/main/res/values/colors.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/kotlin/com/sproutgreen/getyourguitar/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/com/sproutgreen/getyourguitar/ui/fretboard/FretboardColors.kt`, `FretboardUiState.kt`, `FretboardCanvas.kt`, `FretNumberStrip.kt`, `FretboardScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` (테마), `app/src/main/kotlin/com/sproutgreen/getyourguitar/MainActivity.kt` (setContent)

**Interfaces:**
- Consumes: Task 1 `Fretboard`/`Tuning`, Task 8 `FretboardGeometry`/`EqualFretLayout`, Task 9 `AudioController`
- Produces: `GetYourGuitarTheme { }`; `FretboardUiState(geometry = FretboardGeometry())` with `scroll: Float` (state), `showNoteNames: Boolean` (state), `highlights: SnapshotStateList<Highlight>`, `frameNanos: Long` (state), `addHighlight(string, fret, nowNanos)`, `pruneHighlights(nowNanos)`, `HIGHLIGHT_NANOS`; `FretboardCanvas(state, fretboard, modifier)`, `FretNumberStrip(state, modifier)`, `FretboardScreen(audio, state)`. Task 11이 제스처 Modifier를 붙일 자리: `FretboardScreen` 안의 `neckModifier`, `stripModifier` 파라미터.

- [ ] **Step 1: 리소스·테마**

`app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Same as ui/theme/Theme.kt WindowBackground so the launch window and Compose surface match (no light→dark flash). -->
    <color name="window_background">#FF1B1410</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.GetYourGuitar" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
    </style>
</resources>
```

`AndroidManifest.xml`: `android:theme="@android:style/Theme.Material.NoActionBar"` 를 `android:theme="@style/Theme.GetYourGuitar"` 로 교체 (그 줄만).

`ui/theme/Theme.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Must equal res/values/colors.xml window_background. */
val WindowBackground = Color(0xFF1B1410)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0B36A),
    onPrimary = Color(0xFF2A1F0E),
    background = WindowBackground,
    onBackground = Color(0xFFEDE3D6),
    surface = Color(0xFF2A2119),
    onSurface = Color(0xFFEDE3D6),
    surfaceVariant = Color(0xFF3A2E24),
    onSurfaceVariant = Color(0xFFCDBFAF),
    error = Color(0xFFFF6E6E),
)

@Composable
fun GetYourGuitarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
```

- [ ] **Step 2: 지판 색·상태**

`ui/fretboard/FretboardColors.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.ui.graphics.Color

object FretboardColors {
    val woodTop = Color(0xFF7A4A22)
    val woodBottom = Color(0xFF5A3418)
    val openZone = Color(0x33000000)
    val nut = Color(0xFFEDE3D6)
    val fretWire = Color(0xFFB8B8B8)
    val marker = Color(0xFFE8DCC8)
    val string = Color(0xFFD9D9D9)
    val stringShadow = Color(0x66000000)
    val highlight = Color(0xFFFFC857)
    val noteName = Color(0xFFFFFFFF)
    val noteNameBackdrop = Color(0x99000000)
    val fretNumber = Color(0xFFCDBFAF)
}
```

`ui/fretboard/FretboardUiState.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sproutgreen.getyourguitar.core.fretboard.FretboardGeometry

/** A touched cell, drawn as a fading circle (spec 6.2). One per string — the string re-highlights on slide/rake. */
class Highlight(val string: Int, val fret: Int, val startNanos: Long)

/** UI-thread state of the fretboard screen. Audio state never lives here. */
class FretboardUiState(val geometry: FretboardGeometry = FretboardGeometry()) {
    companion object {
        const val HIGHLIGHT_NANOS = 1_500_000_000L
    }

    var scroll: Float by mutableFloatStateOf(geometry.minScroll)
    var showNoteNames: Boolean by mutableStateOf(false)
    val highlights = mutableStateListOf<Highlight>()

    /** Advanced once per frame while highlights exist so the canvas redraws their fade. */
    var frameNanos: Long by mutableLongStateOf(0L)

    fun addHighlight(string: Int, fret: Int, nowNanos: Long) {
        highlights.removeAll { it.string == string }
        highlights.add(Highlight(string, fret, nowNanos))
        frameNanos = nowNanos
    }

    fun pruneHighlights(nowNanos: Long) {
        highlights.removeAll { nowNanos - it.startNanos > HIGHLIGHT_NANOS }
    }
}
```

- [ ] **Step 3: 지판 캔버스와 번호 띠**

`ui/fretboard/FretboardCanvas.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sproutgreen.getyourguitar.core.fretboard.FretboardGeometry
import com.sproutgreen.getyourguitar.core.music.Fretboard
import kotlin.math.max
import kotlin.math.min

/** Draws the neck: wood, open-string zone + nut, fret wires, position markers, strings, highlights, note names. */
@Composable
fun FretboardCanvas(state: FretboardUiState, fretboard: Fretboard, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val noteStyle = TextStyle(color = FretboardColors.noteName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val geometry = state.geometry
    val layout = geometry.layout

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val scroll = state.scroll
        val cellWidth = layout.xOf(1f, 0f, w) - layout.xOf(0f, 0f, w)
        val bandHeight = h / geometry.stringCount

        drawRect(Brush.verticalGradient(listOf(FretboardColors.woodTop, FretboardColors.woodBottom)))

        // Open-string zone and nut (only when the nut is on screen).
        val nutX = layout.xOf(0f, scroll, w)
        if (nutX > 0f) {
            val left = max(layout.xOf(FretboardGeometry.OPEN_CELL_START, scroll, w), 0f)
            drawRect(FretboardColors.openZone, topLeft = Offset(left, 0f), size = Size(nutX - left, h))
            drawLine(FretboardColors.nut, Offset(nutX, 0f), Offset(nutX, h), strokeWidth = 8.dp.toPx())
        }

        // Fret wires at u = 1..fretCount.
        val wireWidth = 3.dp.toPx()
        for (fret in 1..geometry.fretCount) {
            val x = layout.xOf(fret.toFloat(), scroll, w)
            if (x < -wireWidth || x > w + wireWidth) continue
            drawLine(FretboardColors.fretWire, Offset(x, 0f), Offset(x, h), strokeWidth = wireWidth)
        }

        // Position markers in the cell centre.
        val markerRadius = min(cellWidth, bandHeight) * 0.12f
        for (fret in FretboardGeometry.SINGLE_MARKERS) {
            val x = layout.xOf(geometry.cellCenterU(fret), scroll, w)
            if (x in 0f..w) drawCircle(FretboardColors.marker, markerRadius, Offset(x, h / 2f))
        }
        for (fret in FretboardGeometry.DOUBLE_MARKERS) {
            val x = layout.xOf(geometry.cellCenterU(fret), scroll, w)
            if (x in 0f..w) {
                drawCircle(FretboardColors.marker, markerRadius, Offset(x, h * 0.3f))
                drawCircle(FretboardColors.marker, markerRadius, Offset(x, h * 0.7f))
            }
        }

        // Strings: E (index 0) at the bottom and thickest.
        for (string in 0 until geometry.stringCount) {
            val y = geometry.stringCenterY(string, h)
            val thickness = (6 - string).dp.toPx()
            drawLine(FretboardColors.stringShadow, Offset(0f, y + thickness * 0.6f), Offset(w, y + thickness * 0.6f), strokeWidth = thickness)
            drawLine(FretboardColors.string, Offset(0f, y), Offset(w, y), strokeWidth = thickness)
        }

        // Highlights fade over HIGHLIGHT_NANOS.
        val highlightRadius = min(cellWidth, bandHeight) * 0.35f
        val now = state.frameNanos
        for (hl in state.highlights) {
            val age = (now - hl.startNanos).toFloat() / FretboardUiState.HIGHLIGHT_NANOS
            val alpha = (1f - age).coerceIn(0f, 1f)
            if (alpha == 0f) continue
            val cx = layout.xOf(geometry.cellCenterU(hl.fret), scroll, w)
            val cy = geometry.stringCenterY(hl.string, h)
            drawCircle(FretboardColors.highlight.copy(alpha = 0.8f * alpha), highlightRadius, Offset(cx, cy))
        }

        // Note names.
        if (state.showNoteNames) {
            val backdropRadius = min(cellWidth, bandHeight) * 0.22f
            for (string in 0 until geometry.stringCount) {
                val cy = geometry.stringCenterY(string, h)
                for (fret in geometry.visibleFretRange(scroll)) {
                    val cx = layout.xOf(geometry.cellCenterU(fret), scroll, w)
                    if (cx < 0f || cx > w) continue
                    val measured = textMeasurer.measure(fretboard.nameAt(string, fret), noteStyle)
                    drawCircle(FretboardColors.noteNameBackdrop, backdropRadius, Offset(cx, cy))
                    drawText(measured, topLeft = Offset(cx - measured.size.width / 2f, cy - measured.size.height / 2f))
                }
            }
        }
    }
}
```

`ui/fretboard/FretNumberStrip.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Fret numbers above the neck. Also a scroll handle (gesture attached by the caller). */
@Composable
fun FretNumberStrip(state: FretboardUiState, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val style = TextStyle(color = FretboardColors.fretNumber, fontSize = 12.sp)
    val geometry = state.geometry
    val layout = geometry.layout
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (fret in geometry.visibleFretRange(state.scroll)) {
            if (fret == 0) continue
            val cx = layout.xOf(geometry.cellCenterU(fret), state.scroll, w)
            if (cx < 0f || cx > w) continue
            val measured = textMeasurer.measure(fret.toString(), style)
            drawText(measured, topLeft = Offset(cx - measured.size.width / 2f, (h - measured.size.height) / 2f))
        }
    }
}
```

- [ ] **Step 4: 화면 조립**

`ui/fretboard/FretboardScreen.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.core.CoreInfo
import com.sproutgreen.getyourguitar.core.music.Fretboard
import com.sproutgreen.getyourguitar.core.music.Tuning
import kotlinx.coroutines.delay

/**
 * Landscape layout (spec 6.2): top bar 12 % / fret-number strip 8 % / neck 72 % / bottom strip 8 %.
 * [neckModifier] and [stripModifier] receive the gesture handling (Task 11); Task 10 passes none.
 */
@Composable
fun FretboardScreen(
    audio: AudioController,
    state: FretboardUiState = remember { FretboardUiState() },
    neckModifier: Modifier = Modifier,
    stripModifier: Modifier = Modifier,
) {
    val fretboard = remember { Fretboard(Tuning.STANDARD_BASS_4) }

    // Drive highlight fading: one frame loop while any highlight exists.
    val hasHighlights = state.highlights.isNotEmpty()
    LaunchedEffect(hasHighlights) {
        while (hasHighlights && state.highlights.isNotEmpty()) {
            withFrameNanos { now ->
                state.frameNanos = now
                state.pruneHighlights(now)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(audio, state, Modifier.fillMaxWidth().weight(0.12f))
        FretNumberStrip(state, Modifier.fillMaxWidth().weight(0.08f).then(stripModifier))
        FretboardCanvas(state, fretboard, Modifier.fillMaxWidth().weight(0.72f).then(neckModifier))
        Spacer(Modifier.fillMaxWidth().weight(0.08f).then(stripModifier))
    }
}

@Composable
private fun TopBar(audio: AudioController, state: FretboardUiState, modifier: Modifier) {
    var underruns by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            underruns = audio.underrunCount
            audio.refreshError()
        }
    }
    Row(modifier = modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(CoreInfo.APP_NAME, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { state.showNoteNames = !state.showNoteNames }) {
            Text(if (state.showNoteNames) "♪ 이름 끄기" else "♪ 이름 켜기")
        }
        Spacer(Modifier.weight(1f))
        val error = audio.errorMessage
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "${audio.sampleRate} Hz · ${audio.framesPerBuffer} f/b · underrun $underruns",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

`MainActivity.kt` — import에서 `Box`, `fillMaxSize`, `MaterialTheme`, `Surface`, `Text`, `Alignment`, `Modifier`, `CoreInfo` 를 제거하고 아래를 추가:
```kotlin
import com.sproutgreen.getyourguitar.ui.fretboard.FretboardScreen
import com.sproutgreen.getyourguitar.ui.theme.GetYourGuitarTheme
```
`setContent { ... }` 블록을 다음으로 교체:
```kotlin
        setContent {
            GetYourGuitarTheme {
                FretboardScreen(audio)
            }
        }
```

- [ ] **Step 5: 컴파일·린트 확인**

```bash
./gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
tail -1 app/build/reports/lint-results-debug.txt
```
Expected: BUILD SUCCESSFUL, lint `0 errors`. `kotlinx.coroutines.delay` 는 Compose runtime의 코루틴 의존으로 이미 클래스패스에 있다(`kotlinx-coroutines-android`를 추가하지 않는다). 새 lint 경고가 생기면 ID를 커밋 메시지에 적는다.

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml app/src/main/AndroidManifest.xml app/src/main/kotlin/com/sproutgreen/getyourguitar/ui app/src/main/kotlin/com/sproutgreen/getyourguitar/MainActivity.kt
git commit -m "$(cat <<'MSG'
app/ui: dark theme, fretboard canvas, fret-number strip, screen layout

Wood neck with nut/open zone, 24 fret wires, position markers, four
strings (E thickest at the bottom), fading touch highlights, note-name
toggle, debug line (sample rate / frames per buffer / underruns) and an
error banner. safeDrawing insets + shortEdges cutout. No gestures yet.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 11: 제스처 — 멀티터치 연주(탭/슬라이드/레이크), 띠 드래그 스크롤 + 스냅

**Files:**
- Create: `app/src/main/kotlin/com/sproutgreen/getyourguitar/ui/fretboard/FretboardGestures.kt`
- Modify: `app/src/main/kotlin/com/sproutgreen/getyourguitar/MainActivity.kt` (Modifier 전달)

**Interfaces:**
- Consumes: Task 8 `FretboardGeometry.hitTest/clampScroll/snapScroll`, `FretLayout.uAt`; Task 9 `AudioController.noteOn/slide`; Task 10 `FretboardUiState`, `FretboardScreen(neckModifier, stripModifier)`
- Produces: `Modifier.neckPlayInput(state, audio)`, `Modifier.scrollStripInput(state, scope: CoroutineScope)`, `suspend fun snapScroll(state)`.

제스처 표(스펙 6.2): 지판 down → `NOTE_ON`; move 같은 줄·다른 프렛 → `SLIDE`; move 다른 줄 → 새 줄 `NOTE_ON`(레이크, 이전 줄은 계속 감쇠); 같은 셀 안 move → 무시; up → 아무것도 안 함; 같은 줄 두 포인터 → 마지막 이벤트가 이김; 띠 drag → `scroll −= Δu`, up 시 150 ms 스냅; 띠 tap → 무시.

- [ ] **Step 1: 구현**

`ui/fretboard/FretboardGestures.kt`:
```kotlin
package com.sproutgreen.getyourguitar.ui.fretboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.sproutgreen.getyourguitar.audio.AudioController
import com.sproutgreen.getyourguitar.core.music.FretPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Multi-touch neck input: every pointer plays independently (spec 6.2 gesture table).
 * down → noteOn; move to another fret on the same string → slide; move onto another string → noteOn there
 * (the previous string keeps ringing); up → nothing (natural decay).
 */
fun Modifier.neckPlayInput(state: FretboardUiState, audio: AudioController): Modifier =
    pointerInput(state, audio) {
        val geometry = state.geometry
        val cellByPointer = HashMap<PointerId, FretPosition>()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                for (change in event.changes) {
                    val id = change.id
                    when {
                        change.changedToDown() -> {
                            val hit = geometry.hitTest(change.position.x, change.position.y, state.scroll, w, h)
                            if (hit != null) {
                                cellByPointer[id] = hit
                                audio.noteOn(hit.string, hit.fret)
                                state.addHighlight(hit.string, hit.fret, System.nanoTime())
                            }
                            change.consume()
                        }
                        change.changedToUp() -> {
                            cellByPointer.remove(id)
                            change.consume()
                        }
                        change.pressed && change.positionChanged() -> {
                            val hit = geometry.hitTest(change.position.x, change.position.y, state.scroll, w, h)
                            val previous = cellByPointer[id]
                            if (hit != null && hit != previous) {
                                if (previous != null && previous.string == hit.string) {
                                    audio.slide(hit.string, hit.fret)
                                } else {
                                    audio.noteOn(hit.string, hit.fret)
                                }
                                state.addHighlight(hit.string, hit.fret, System.nanoTime())
                                cellByPointer[id] = hit
                            }
                            change.consume()
                        }
                    }
                }
            }
        }
    }

/** Horizontal drag on a strip scrolls the neck by the dragged distance in cells; release snaps to a whole cell. */
fun Modifier.scrollStripInput(state: FretboardUiState, scope: CoroutineScope): Modifier =
    pointerInput(state) {
        val geometry = state.geometry
        val layout = geometry.layout
        detectHorizontalDragGestures(
            onDragEnd = { scope.launch { snapScroll(state) } },
            onDragCancel = { scope.launch { snapScroll(state) } },
        ) { change, dragAmountPx ->
            change.consume()
            val w = size.width.toFloat()
            val deltaU = layout.uAt(dragAmountPx, state.scroll, w) - layout.uAt(0f, state.scroll, w)
            state.scroll = geometry.clampScroll(state.scroll - deltaU)
        }
    }

/** Animates scroll to the nearest whole cell over 150 ms (spec 6.2). */
suspend fun snapScroll(state: FretboardUiState) {
    val target = state.geometry.snapScroll(state.scroll)
    if (target == state.scroll) return
    animate(initialValue = state.scroll, targetValue = target, animationSpec = tween(durationMillis = 150)) { value, _ ->
        state.scroll = value
    }
}
```

`MainActivity.kt` — `setContent` 블록을 다음으로 교체하고 import 3개 추가:
```kotlin
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sproutgreen.getyourguitar.ui.fretboard.FretboardUiState
import com.sproutgreen.getyourguitar.ui.fretboard.neckPlayInput
import com.sproutgreen.getyourguitar.ui.fretboard.scrollStripInput
```
```kotlin
        setContent {
            GetYourGuitarTheme {
                val state = remember { FretboardUiState() }
                val scope = rememberCoroutineScope()
                FretboardScreen(
                    audio = audio,
                    state = state,
                    neckModifier = Modifier.neckPlayInput(state, audio),
                    stripModifier = Modifier.scrollStripInput(state, scope),
                )
            }
        }
```
(`androidx.compose.ui.Modifier` import는 다시 필요하다 — Task 10에서 지웠다면 되돌린다.)

- [ ] **Step 2: 컴파일·린트 확인**

```bash
./gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
tail -1 app/build/reports/lint-results-debug.txt
```
Expected: BUILD SUCCESSFUL, lint `0 errors`. `changedToDown`/`changedToUp`/`positionChanged` 는 `androidx.compose.ui.input.pointer` 의 확장 함수다 — unresolved 면 import를 확인.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/kotlin/com/sproutgreen/getyourguitar/ui/fretboard/FretboardGestures.kt app/src/main/kotlin/com/sproutgreen/getyourguitar/MainActivity.kt
git commit -m "$(cat <<'MSG'
app/ui: multi-touch neck play (tap / slide / rake) and strip drag scroll with snap

Per-pointer cell tracking → NOTE_ON / SLIDE commands; strips drag the
viewport in cell units and snap to whole cells in 150 ms.
터치 반응·지연·멀티터치는 검증 환경에서 확인 필요.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

---

### Task 12: 체크리스트·문서·메모리, 전체 검증, push + PR

**Files:**
- Create: `docs/verification-checklist.md`
- Modify: `README.md`, `CLAUDE.md`, `.claude/memory/get-your-guitar-project.md`

**Interfaces:**
- Consumes: Task 1~11 전부
- Produces: `origin/m1-first-sound` + PR. 검증 환경이 따라갈 체크리스트.

- [ ] **Step 1: 체크리스트 작성**

`docs/verification-checklist.md`:
```markdown
# 실기기 검증 체크리스트

아키랩에서는 기기 동작을 확인할 수 없다. 검증 환경은 아래를 실제 폰에서 확인하고, 결과(OK / 문제 + 현상)와 상단 바의 디버그 숫자(샘플레이트·프레임/버퍼·언더런)를 이 파일의 "기록" 절에 날짜·기종과 함께 적어 push한다.

## 준비
```bash
git fetch && git checkout m1-first-sound     # 또는 merge 후 main
./gradlew :core:test :app:assembleDebug        # 아키랩과 같아야 함: 테스트 전부 PASSED
./gradlew :app:installDebug
```

## M0 — 스캐폴드
- [ ] 앱이 설치되고 켜진다
- [ ] 가로(landscape)로 뜬다 (폰 기준; 600dp 이상 화면은 OS가 무시할 수 있음)

## M1 — 첫 소리
- [ ] 지판을 탭하면 베이스 음이 난다. 터치 → 소리 체감 지연: 즉각 / 약간 늦음 / 거슬림 중 하나로 기록
- [ ] 개방현 존(맨 왼쪽, 너트 왼쪽)을 탭하면 개방현 음(E·A·D·G)이 난다
- [ ] 줄마다 다른 음높이: 아래(E)가 가장 낮고 위(G)가 가장 높다; 프렛이 오른쪽일수록 높다
- [ ] 같은 줄을 다시 탭하면 이전 음이 끊기고 새 음이 난다 (클릭 노이즈 없이)
- [ ] 멀티터치: 두 줄을 동시에 누르면 두 음이 함께 울린다 (3~4줄도)
- [ ] 슬라이드: 손가락을 같은 줄 위에서 옆으로 끌면 음이 프렛 단위로 올라가고/내려간다 (재피킹 없이)
- [ ] 레이크: 손가락을 세로로 다른 줄로 끌면 새 줄이 울리고 이전 줄은 계속 감쇠한다
- [ ] 손가락을 떼도 음은 자연스럽게 감쇠한다 (뚝 끊기지 않음)
- [ ] 프렛 번호 띠 / 하단 여백 띠를 가로로 끌면 지판이 이동하고, 떼면 프렛 경계에 맞춰 멈춘다. 지판 자체를 끌 때는 스크롤되지 않는다
- [ ] 스크롤 범위: 왼쪽 끝은 개방현+1~11프렛, 오른쪽 끝은 13~24프렛
- [ ] "♪ 이름 켜기"를 누르면 각 위치에 음이름이 보이고, 다시 누르면 사라진다
- [ ] 탭한 위치에 노란 원이 생겼다가 1.5초에 걸쳐 사라진다
- [ ] 상단 바 디버그 숫자를 기록: `____ Hz · ____ f/b · underrun ____` (5분 연주 후 underrun 값도)
- [ ] 홈 버튼 → 다시 열기: 소리가 다시 난다. 화면이 꺼지지 않는다(연주 중 화면 켜짐 유지)
- [ ] 콜드 스타트 시 밝은 화면이 잠깐 번쩍이지 않는다 (어두운 배경으로 바로 뜸)
- [ ] 노치/펀치홀·내비게이션 바 아래에 지판이 가려지지 않는다
- [ ] 음색 인상 (자유 기술): 베이스 같은가, 너무 밝은가/둔한가, 감쇠가 너무 빠른가/긴가

## 기록
| 날짜 | 기종 · Android | 결과 요약 | 디버그 숫자 |
|---|---|---|---|
| | | | |
```

- [ ] **Step 2: README·CLAUDE.md·메모리**

`README.md` — "### 검증 환경에서 M0 확인 절차" 절 전체(제목부터 3번 항목까지)를 다음으로 교체:
```markdown
### 검증 환경 확인 절차
`docs/verification-checklist.md` 를 따른다 (마일스톤별 항목, 결과 기록 표 포함). 환경 정보는 아래 "환경별 의존성"에 섹션으로 추가한다.
```

`CLAUDE.md` — "## 빌드·테스트 명령" 절 끝에 추가:
```markdown
- **오디오 스레드 규칙:** `:core`의 `SynthEngine.render`/`handle`, `Voice.render`, `Mixer.process`, `CommandQueue.drain` 경로에서 객체 할당·락·로그·예외 금지. 배열 for 루프만. 리뷰 항목.
- `:core`는 Android/Compose 의존 0. 지판 기하(`core/fretboard`)도 순수 계산이라 여기 둔다.
- 실기기 체크리스트: `docs/verification-checklist.md`. 아키랩에서는 "검증 환경에서 확인 필요"로만 표기.
```

`.claude/memory/get-your-guitar-project.md` — `**Status (2026-09-16):**` 로 시작하는 문장부터 그 bullet 끝까지를 다음으로 교체:
```
**Status (2026-09-16):** M0 on branch `m0-scaffold` (PR #1). M1 "first sound" implemented on branch `m1-first-sound` (stacked on m0-scaffold until PR #1 merges): core music/synth/engine/fretboard geometry with JVM tests, AudioTrack output, Compose fretboard with tap/slide/rake/strip-scroll/note names. On-device verification pending — see `docs/verification-checklist.md`. Next: M2 (tone params UI, settings + DataStore, debug info, slide energy dip) after the M1 device report; do not start M2 UI before the M1 latency/feel feedback arrives. A GitHub Actions CI job (ubuntu + JDK 21, `:core:test` + `assembleDebug`) was recommended and left to the user's decision.
```

- [ ] **Step 3: 클린 전체 검증**

```bash
export JAVA_HOME='E:\yjane.kim\tools\jdk-17.0.20.1+1' GRADLE_USER_HOME='E:\yjane.kim\tools\gradle-user-home'; cd /e/yjane.kim/project/get-your-guitar
./gradlew.bat clean :core:test :app:assembleDebug :app:lintDebug --console=plain 2>&1 | tee /e/yjane.kim/tools/m1-verify.log | grep -E "PASSED|FAILED|BUILD|warning:|error:" | tail -60
grep -c "PASSED" /e/yjane.kim/tools/m1-verify.log
tail -1 app/build/reports/lint-results-debug.txt
ls -la app/build/outputs/apk/debug/app-debug.apk
git status --short
```
Expected: 테스트 **약 57개** 전부 PASSED (music 15, testutil 5, synth 13+4+4+5, engine 4+8, fretboard 9, CoreInfo 1 — 정확한 수는 로그로 확인), BUILD SUCCESSFUL, lint 0 errors, APK 존재, 작업 트리 clean(문서 3개 + 체크리스트만 수정됨). Kotlin 컴파일 경고가 있으면 커밋 메시지에 그대로 적는다.

- [ ] **Step 4: 문서 커밋**

```bash
git add docs/verification-checklist.md README.md CLAUDE.md .claude/memory/get-your-guitar-project.md
git commit -m "$(cat <<'MSG'
Docs: device verification checklist (M0+M1), audio-thread rule, memory status

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
MSG
)"
```

- [ ] **Step 5: push + PR**

```bash
git push -u origin m1-first-sound
gh pr view 1 --json state --jq .state      # OPEN → base m0-scaffold (stacked) / MERGED → base main
gh pr create --base m0-scaffold --head m1-first-sound --title "M1 first sound: core synth/engine (JVM-tested), AudioTrack output, Compose fretboard" --body "$(cat <<'EOF'
## Summary
- `:core` (pure Kotlin, JVM-tested): music theory (Tuning/Pitch/NoteName/Fretboard), Karplus-Strong voice (fractional delay, filter-delay-compensated pitch, 8 ms glide, click-free re-pluck), StringVoices, Mixer (soft clip), SPSC CommandQueue, SynthEngine, fretboard geometry (FretLayout/EqualFretLayout/FretboardGeometry)
- `:app`: AudioTrack low-latency output on a dedicated thread + AudioController; dark theme; Compose fretboard (wood/nut/wires/markers/strings/highlights/note names), fret-number strip; multi-touch tap/slide/rake, strip drag scroll with snap; debug line + error banner
- Docs: `docs/verification-checklist.md`, README/CLAUDE.md updates, memory status

Spec: `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` §5, §6.1–6.2, §9 M1 · Plan: `docs/superpowers/plans/2026-09-16-m1-first-sound.md`

## Verified in 아키랩 (no device)
- `gradlew clean :core:test :app:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL, all core tests PASSED (pitch accuracy ±1 % E1–G4, monotonic decay, glide without click, re-pluck, queue FIFO/wraparound/drop, engine commands, hit testing), lint 0 errors

## 검증 환경에서 확인 필요
`docs/verification-checklist.md` M0 + M1 항목 전부. **먼저 보고할 것:** 터치→소리 체감 지연, 멀티터치, 슬라이드, 띠 스크롤. 디버그 숫자(Hz · f/b · underrun)를 기록 표에 적어 push.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
PR #1이 이미 MERGED면 `--base main`으로 바꾸고, 그 전에 `git rebase main` 후 `git push --force-with-lease origin m1-first-sound`.

- [ ] **Step 6: 인수인계 (대화로 사용자에게)**
- PR URL, 테스트 수, APK 크기, lint 결과.
- 검증 환경: `git fetch && git checkout m1-first-sound && ./gradlew :app:installDebug` → 체크리스트 M0+M1.
- M2는 M1 기기 피드백(지연·음색)이 온 뒤 계획한다.

---

## 셀프 리뷰 기록

- **스펙 커버리지:** 5.1 music → Task 1. 5.2 ToneParams 매핑·KS 보이스(소수점 딜레이·여기·재피킹 2 ms 딥·글라이드 8 ms·isActive·velocity 0.8) → Task 3·4 (0.85 슬라이드 딥은 스펙 M2). StringVoices·Mixer(프리게인 0.5, 소프트클립) → Task 5. 5.4 커맨드 표(M1 범위 6종, M3 4종은 상수만)·큐 256×4·드롭 카운트 → Task 6·7. 6.1 AudioTrack(모노 float, 네이티브 레이트/프레임, 저지연 모드, 버퍼 배수, URGENT_AUDIO 스레드, 언더런 카운트, 생성 실패 재시도 → 배너) → Task 9. 6.2 좌표계·뷰포트·줄 배치·마커·음이름·하이라이트·제스처 표·띠 스냅 150 ms → Task 8·10·11. 6.5 가로 고정(M0)·화면 켜짐·onStart/onStop → Task 9. 7장 큐 가득·잘못된 위치·오디오 스레드 예외 → Task 6·7·9. 8.1 테스트 표 전부 → Task 1~8 (메트로놈 제외 = M3). 8.3 체크리스트 → Task 12. 9장 M1 1~6 → Task 1~12. 스펙에 없는 것: 디버그 줄을 상단 바에 임시 노출(M2가 설정 화면으로 옮김) — 검증 보고용.
- **자리표시 스캔:** 없음. 모든 코드 스텝에 전체 코드, 모든 실행 스텝에 명령·기대 출력.
- **타입·이름 일관성:** `Voice.setTone(brightness, decay)` — Task 3 정의, Task 5·7 사용 ✓. `StringVoices(sampleRate, stringCount, random)` — Task 5 정의, Task 7 테스트 `StringVoices(sr, 4, Random(7))` ✓. `SynthEngine(sampleRate, tuning, voices)` + `send/render/stats/droppedCommands` — Task 7 정의, Task 9 `AudioController` 사용 ✓. `AudioRenderer`(Task 6) ← `SynthEngine`(Task 7) ← `AudioOutput.start(renderer)`(Task 9) ✓. `FretboardGeometry.hitTest(x, y, scroll, w, h): FretPosition?`·`clampScroll`·`snapScroll`·`visibleFretRange`·`cellCenterU`·`stringCenterY`·`layout` — Task 8 정의, Task 10·11 사용 ✓. `FretboardUiState.scroll/showNoteNames/highlights/frameNanos/addHighlight/pruneHighlights` — Task 10 정의, Task 11 사용 ✓. `FretboardScreen(audio, state, neckModifier, stripModifier)` — Task 10 정의, Task 11 호출 ✓. `AudioController.noteOn/slide/errorMessage/refreshError/underrunCount/sampleRate/framesPerBuffer` — Task 9 정의, Task 10·11 사용 ✓. `SignalAnalysis.rms/peak/maxDelta(x, from, to)`, `fundamentalHz(x, sr, from, to)` — Task 2 정의, Task 3·4·7 사용 ✓.

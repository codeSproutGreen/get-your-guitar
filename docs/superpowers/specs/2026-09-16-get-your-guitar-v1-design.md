# get-your-guitar v1 설계 스펙

- 작성일: 2026-09-16
- 상태: 사용자 승인 대기
- 후속 문서: `docs/superpowers/plans/` 의 구현 계획(이 스펙 9장의 마일스톤을 작업 단위로 분해)

## 1. 목적

"Bass Guitar Solo"(참고 스크린샷 `reference.webp`)와 같은 **베이스 기타 지판 시뮬레이터** Android 앱.
사용자 본인이 베이스가 손에 없을 때 **악기 대용으로 연주**하는 개인 연습 도구다.
스토어 출시·과금·다국어는 고려하지 않는다.

성공 기준: 최신 폰(Android 13+) 한 대에서 지판을 탭하면 지연이 거슬리지 않게 베이스 음이 나고,
멀티터치·슬라이드가 자연스러우며, 메트로놈과 함께 연습할 수 있다.

## 2. 확정된 결정

| 항목 | 결정 |
|---|---|
| 목적 | 개인용 베이스 연습 도구 (출시 아님) |
| 핵심 장면 | 악기 대용으로 연주 — 저지연·멀티터치·자연 감쇠가 최우선 |
| 음원 | 합성(Karplus-Strong)으로 시작, `Voice` 인터페이스 뒤에서 샘플 재생으로 교체 가능 |
| 대상 기기 | 최신 Galaxy/Pixel 1대, Android 13+. **minSdk는 31** — 검증 환경의 테스트 폰이 Galaxy S10e(Android 12, API 31)라서 2026-09-21에 33→31로 내림. v1 기능 중 API 32·33 전용은 없다 |
| 제스처 | 탭 = 피킹, 자연 감쇠. 지판 드래그 = 슬라이드(같은 줄) / 레이크(다른 줄). 지판 바깥 띠 드래그 = 스크롤 |
| 줄/튜닝 | 4현 표준 E1 A1 D2 G2 고정 (튜닝은 데이터로 두어 확장 가능) |
| 지판 보기 | 등간격 12프렛 창, 띠 드래그 스크롤 + 프렛 경계 스냅 |
| v1 부가기능 | 음이름 표시 토글, 메트로놈, 볼륨/음색 설정 화면 |
| 오디오 스택 | Kotlin `AudioTrack` 저지연 모드. 출력 백엔드는 인터페이스 뒤 (Oboe로 교체 가능) — 접근법 C |
| UI 스택 | Kotlin + Jetpack Compose, 지판은 Canvas 직접 그리기 |
| 모듈 | `:core`(순수 Kotlin/JVM, Android 의존 0) + `:app`(Android) |

## 3. 범위

### v1에 포함
- 지판 렌더(4줄·24프렛·포지션 마크·너트), 12프렛 창 스크롤+스냅, 음이름 토글
- 탭 피킹, 슬라이드, 레이크, 멀티터치(줄당 1보이스), 자연 감쇠
- Karplus-Strong 합성 보이스, 밝기/감쇠 음색 파라미터, 마스터 볼륨, 소프트 클립
- 메트로놈(BPM 40~240, 4/4, 1박 강세, 비트 표시)
- 설정 화면 + DataStore 영속성, 디버그 정보(샘플레이트·프레임/버퍼·언더런)
- 오디오 포커스·수명주기 처리

### v2 백로그 (v1 설계가 훅을 제공해야 하는 항목)
| 항목 | v1에 심어두는 훅 |
|---|---|
| ~~**벤딩**~~ → **v1에 구현됨 (2026-09-21, 사용자 요청으로 앞당김)** | 예고대로 보이스는 그대로 두고 `Command.Bend(string, cents)` + 제스처 계층만 추가했다. 규칙: 짚은 줄의 밴드 안 세로 이동 = 벤딩(위·아래 같음, 데드존 0.1밴드, 0.5밴드에서 최대 200센트), 터치 후 80 ms 뒤에 걸림, **벤딩이 시작된 손가락은 손을 뗄 때까지 그 줄에 고정**(옆 줄 영역으로 넘어가도 레이크로 바뀌지 않고 최대 벤딩 유지), 벤딩 시작 전에 다른 줄 밴드로 들어가면 레이크, 레이크로 들어간 줄은 벤딩 안 함, 손 떼면 복귀. 즉 "짚고 나서 밀면 벤딩, 짚자마자 훑으면 레이크". 첫 구현은 밴드 경계를 넘으면 벤딩을 풀었는데 최대 벤딩 지점이 곧 경계라 실기기에서 바로 불편이 드러나 같은 날 고쳤다. 벤딩 폭 1음/2음 선택은 M2 설정에 추가 |
| **실제 프렛 간격** — 바디로 갈수록 좁아지는 간격, 등간격과 토글 | `FretLayout` 인터페이스(`xOf`, `uAt`)와 연속 좌표 뷰포트. v2에 `RealFretLayout`(`x_n = L − L / 2^(n/12)`) 추가 + 설정 토글 |
| 샘플 기반 음원 | `Voice` 인터페이스 + 보이스 팩토리 |
| 5현 / 다른 튜닝 | `Tuning` 데이터 객체, 줄 수에 의존하지 않는 지판 렌더 |
| 녹음/재생 | 없음 (v2에서 설계) |

### 범위 밖
Play Store 출시 준비, 광고/과금, 다국어, 태블릿 전용 레이아웃, 에뮬레이터 지원, Oboe/NDK.

## 4. 아키텍처

### 4.1 모듈 구조
```
get-your-guitar/
├─ core/   Kotlin/JVM 라이브러리 — Android 의존 0, 아키랩에서 단위테스트 실행
│   ├─ music/      Tuning, Pitch, NoteName, FretPosition, Fretboard
│   ├─ synth/      Voice(인터페이스), KarplusStrongVoice, StringVoices, Mixer, ToneParams
│   ├─ metronome/  Metronome, ClickVoice
│   └─ engine/     Command, CommandQueue, SynthEngine, EngineStats
└─ app/    Android 애플리케이션 (Compose)
    ├─ audio/         AudioOutput(인터페이스), AudioTrackOutput, AudioController
    ├─ ui/fretboard/  FretboardScreen, FretboardCanvas, FretLayout, EqualFretLayout, FretboardGestures, FretboardViewModel
    ├─ ui/metronome/  MetronomeBar
    ├─ ui/settings/   SettingsScreen, SettingsViewModel
    ├─ data/          Settings, SettingsRepository (DataStore Preferences)
    └─ MainActivity, App(Application)
```
의존 방향은 `app → core` 단방향. `core`는 `kotlin("jvm")` 플러그인만 사용한다.

### 4.2 핵심 경계
1. **`SynthEngine.render(out: FloatArray, frames: Int)`** — 순수 pull 렌더러. 출력 백엔드(v1 `AudioTrackOutput`, 이후 Oboe)는 이 함수를 호출하는 쪽만 교체한다.
2. **`Voice`** — `noteOn(hz, velocity)`, `setPitch(hz)`, `render(out, offset, frames)`(가산), `isActive`, `silence()`. 합성 → 샘플 교체 지점.
3. **`FretLayout`** — 프렛 번호 ↔ 지판 좌표 변환. 등간격 → 실제 간격 교체 지점.

### 4.3 데이터 흐름과 스레딩
```
터치 (UI 스레드) ─▶ FretboardGestures ─▶ AudioController.send(Command)
                                              │  CommandQueue (SPSC 링버퍼, 원시값 인코딩)
오디오 스레드: loop { engine.render(buf) ; audioTrack.write(buf, BLOCKING) }
               render = 큐 비우기 → 보이스 합산 → 메트로놈 → 마스터 게인 → 소프트 클립
오디오 → UI: 콜백 없음. EngineStats(volatile: 현재 비트, 드롭 수)와 AudioOutput의 언더런 수를 UI가 프레임마다 읽음
```
- 생산자는 UI(메인) 스레드 하나, 소비자는 오디오 스레드 하나다. 설정 변경(볼륨·음색·BPM)도 같은 큐를 쓴다.
- **오디오 스레드 규칙**: 렌더 경로에서 객체 할당·락·로그 금지. 모든 버퍼는 생성 시 할당한다. 코드 리뷰 항목으로 관리한다.

## 5. `:core` 상세

### 5.1 music
- `Tuning(openMidi: IntArray)`; `Tuning.STANDARD_BASS_4 = [28, 33, 38, 43]` (E1 A1 D2 G2). 줄 인덱스 0 = E(최저음), 3 = G.
- `Pitch.hz(midi) = 440 · 2^((midi−69)/12)`. E1 = 41.203 Hz, G4(G줄 24프렛, MIDI 67) = 392.0 Hz.
- `NoteName.of(midi)` — 샤프 표기만 사용: C C# D D# E F F# G G# A A# B.
- `FretPosition(string: Int, fret: Int)`, 프렛 0 = 개방현, 최대 24.
- `Fretboard(tuning, fretCount = 24)`: `midiAt(pos) = tuning.openMidi[string] + fret`, `hzAt(pos)`, `nameAt(pos)`.

### 5.2 synth
**`ToneParams(brightness: Float, decay: Float)`** — 둘 다 0~1.
- brightness → 루프 로우패스 컷오프 500 Hz ~ 6 kHz (로그 보간) → 1극 계수.
- decay → 루프 피드백 0.990 ~ 0.9995 (선형 보간).

**`KarplusStrongVoice(sampleRate)`**
- 딜레이 라인: 길이 `sampleRate / 25` 샘플로 할당(25 Hz까지 여유), 읽기 길이 `N = sampleRate / hz`는 **소수점**, 선형 보간으로 읽는다.
- 루프 필터: 1극 로우패스 `y += a·(x − y)`, 피드백 `g`.
- 여기(excitation): 길이 N의 백색 노이즈 버스트를 같은 로우패스로 거른 뒤 velocity 배로 주입.
- `noteOn(hz, velocity)`: 이미 울리는 중이면 **출력 게인을 2 ms 동안 0으로 내렸다가 2 ms 동안 복귀**하면서 그 사이에 딜레이 라인을 새 버스트로 채운다(클릭 없는 재피킹). 아니면 즉시 주입.
- `setPitch(hz)`: 읽기 길이를 **8 ms 선형 램프**로 목표값까지 글라이드. 재여기 없음. 램프 동안 출력 게인에 0.85 딥을 주어 슬라이드의 미세한 에너지 손실을 흉내낸다.
- `isActive`: 최근 청크의 피크가 −80 dBFS(1e-4) 미만인 청크가 4번 연속이면 false. 비활성 보이스는 렌더를 건너뛴다.
- velocity: v1은 상수 0.8 (터치 벨로시티 없음).

**`StringVoices(count = 4)`** — 줄당 보이스 1개. `noteOn(string, hz)`, `setPitch(string, hz)`, `silenceAll()`. 같은 줄에 대한 새 커맨드는 항상 마지막 것이 이긴다.

**`Mixer`** — 보이스 합 × 0.5(프리게인) × 마스터 게인 → 소프트 클립 `y = x / (1 + |x|)`. 출력은 항상 (−1, 1).

### 5.3 metronome
- `Metronome(sampleRate)`: `bpm`(40~240 클램프), 4/4 고정, `running`, `gain`.
- 렌더 루프 안에서 **샘플 단위**로 다음 클릭 위치를 유지한다(`nextClickSample`, 청크 경계와 무관). 시작 시 즉시 1박부터.
- `ClickVoice`: 20 ms 지수 감쇠 사인. 1박 1500 Hz, 나머지 1000 Hz.
- `EngineStats.currentBeat`(0~3, running이 아니면 −1)를 volatile로 갱신.

### 5.4 engine
**커맨드** — 슬롯당 `Int × 4`(type, a, b, floatBits), 큐 용량 256, 가득 차면 새 커맨드를 버리고 `EngineStats.droppedCommands++`.

| 커맨드 | 인자 |
|---|---|
| `NoteOn` | string, fret |
| `Slide` | string, fret |
| `AllNotesOff` | — |
| `SetMasterGain` | float |
| `SetBrightness` | float |
| `SetDecay` | float |
| `MetronomeStart` / `MetronomeStop` | — |
| `SetBpm` | int |
| `SetMetronomeGain` | float |

`SynthEngine(sampleRate, tuning)`는 `Fretboard`로 (string, fret) → Hz를 계산해 보이스에 전달한다. 렌더 순서: 큐 비우기 → `StringVoices` 가산 렌더 → `Metronome` 가산 렌더 → `Mixer`.

## 6. `:app` 상세

### 6.1 오디오 출력
- `interface AudioOutput { val sampleRate: Int; val framesPerBuffer: Int; val underrunCount: Int; fun start(render: (FloatArray, Int) -> Unit); fun stop() }` — 샘플레이트·프레임/버퍼는 생성 시점에 `AudioManager`에서 읽어 두므로 `start` 전에 알 수 있다.
- `AudioTrackOutput`:
  - 샘플레이트·프레임/버퍼는 `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`, `PROPERTY_OUTPUT_FRAMES_PER_BUFFER`.
  - `AudioTrack.Builder`: `ENCODING_PCM_FLOAT`, 모노, `PERFORMANCE_MODE_LOW_LATENCY`, `USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`, 버퍼 = 프레임/버퍼 × `audioBufferChunks`(2/3/4) — 단 `getMinBufferSize`보다 작지 않게.
  - 전용 스레드(`Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)`): `engine.render(buf, framesPerBuffer)` → `write(buf, 0, n, WRITE_BLOCKING)`. 렌더 청크 = 프레임/버퍼.
  - 언더런은 `AudioTrack.getUnderrunCount()`를 주기적으로 읽어 `stats`에 반영.
- `AudioController`(Activity 수명 동안 1개): `AudioOutput`을 만들고 `SynthEngine(output.sampleRate, tuning)`을 생성해 소유. `send(command)`, `start()`(= `output.start(engine::render)`)/`stop()`, 설정 값을 엔진 커맨드로 변환. 버퍼 배수가 바뀌면 출력을 정지·재생성·재시작하고 엔진은 그대로 둔다.

### 6.2 지판 화면
**레이아웃 (가로 고정)**
```
┌──────────────────────────────────────────────────┐
│ [⚙] [♪]           [−] 120 [+] [▶] ● ○ ○ ○        │ 상단 바 (높이 12%)
├──────────────────────────────────────────────────┤
│  1   2   3   4   5   6   7   8   9  10  11  12   │ 프렛 번호 띠 = 스크롤 핸들 (8%)
├──────────────────────────────────────────────────┤
│ G ───┼───┼───●───┼───●───┼───●───┼───●───┼───┼── │
│ D ───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼── │ 지판 = 연주 영역
│ A ───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼── │
│ E ═══┼═══┼═══┼═══┼═══┼═══┼═══┼═══┼═══┼═══┼═══┼══ │
├──────────────────────────────────────────────────┤
│                                                  │ 하단 여백 띠 = 스크롤 핸들 (8%)
└──────────────────────────────────────────────────┘
```
**좌표계** — 지판 좌표 `u`(단위: 셀). 개방현 셀 = [−1, 0), 프렛 n 셀 = [n−1, n), 프렛 와이어는 `u = n`(n = 0..24, u = 0이 너트).
- `interface FretLayout { fun xOf(u: Float): Float; fun uAt(x: Float): Float }` — v1 `EqualFretLayout`은 `x = (u − scroll) · cellWidth`.
- 뷰포트: 폭 12셀, `scroll ∈ [−1, 12]`(실수). 기본값 −1(개방현 + 1~11프렛). 최대 12(13~24프렛).
- 줄 밴드: 지판 높이 / 4. 위에서 G(3)·D(2)·A(1)·E(0). 줄 굵기는 E가 가장 굵다.
- 포지션 마크: 3·5·7·9·15·17·19·21 단점, 12·24 이중점(셀 중앙). 배경은 갈색 그라디언트(이미지 없음).
- 음이름 토글 ON: 각 (줄, 프렛) 교차점에 이름 표시.
- 하이라이트: 터치한 (줄, 프렛) 셀 중앙에 원, 1.5 s에 걸쳐 알파 감소. 슬라이드·레이크로 셀이 바뀌면 새 셀에 다시 표시. UI 상태로만 관리(오디오와 무관).

**제스처 → 커맨드** (포인터별 독립, `pointerInput` + `awaitPointerEventScope`)
| 이벤트 | 동작 |
|---|---|
| 지판 안 down | (string, fret) 판정 → `NoteOn` |
| move, 같은 줄·다른 프렛 | `Slide(string, fret)` |
| move, 다른 줄로 진입 | 새 줄에 `NoteOn`(레이크). 이전 줄은 계속 감쇠 |
| move, 짚은 줄의 밴드 안에서 세로로 | `Bend(string, cents)` — 2026-09-21 추가. 3장 v2 백로그 표의 벤딩 행 참고 |
| move, 같은 셀 안 | 무시 |
| up / cancel | 아무것도 안 함 |
| 같은 줄에 두 포인터 | 마지막 이벤트가 이김 |
| 띠에서 drag | `scroll −= dx / cellWidth`, 클램프. up 시 가장 가까운 정수로 150 ms 애니메이션 스냅 |
| 띠 tap | 무시 |

판정 허용 범위는 넉넉하다: 줄은 밴드 전체 높이, 프렛은 셀 전체 폭.

### 6.3 메트로놈 UI (상단 바)
- `[−] BPM [+]`: ±1, 길게 누르면 100 ms 간격 연속 증감. BPM 숫자 탭 → 슬라이더 다이얼로그(40~240).
- `[▶/■]` 시작·정지. 비트 점 4개, `stats.currentBeat`를 프레임마다 읽어 현재 비트 강조.
- 설정 화면으로 이동해도 계속 울린다. 앱이 `onStop`되면 정지한다.

### 6.4 설정
**모델** (`Settings`, DataStore Preferences)
| 키 | 범위 / 기본 | 반영 |
|---|---|---|
| `masterVolume` | 0~1 / 0.8 | `SetMasterGain` 즉시 |
| `brightness` | 0~1 / 0.6 | `SetBrightness` 즉시 |
| `decay` | 0~1 / 0.7 | `SetDecay` 즉시 |
| `metronomeVolume` | 0~1 / 0.7 | `SetMetronomeGain` 즉시 |
| `bpm` | 40~240 / 100 | `SetBpm` 즉시 |
| `showNoteNames` | Bool / false | 지판 렌더 |
| `audioBufferChunks` | 2·3·4 / 2 | 출력 재시작 |
| `fretLayout` | `EQUAL` (v2: `REAL`) | 지판 |

`SettingsRepository`: `val settings: Flow<Settings>`, `suspend fun update(transform: (Settings) -> Settings)`. 읽기 실패 시 기본값, 범위 밖 값은 클램프.

**화면**: 슬라이더 4개(마스터·밝기·감쇠·메트로놈), 음이름 토글, 버퍼 배수 선택(2/3/4), 디버그 정보(샘플레이트·프레임/버퍼·버퍼 배수·언더런 수·드롭 수·엔진 실행 여부).

**내비게이션**: 화면은 지판/설정 둘뿐. `MainActivity`의 `screen` 상태 + `BackHandler`로 전환(라이브러리 없음).

### 6.5 수명주기·조립
- `MainActivity`: `screenOrientation = landscape`, `FLAG_KEEP_SCREEN_ON`.
- `onStart`: 오디오 포커스 요청(`AUDIOFOCUS_GAIN`) → `AudioController.start()` → 저장된 설정을 엔진에 적용.
- `onStop`: `MetronomeStop`, `AllNotesOff` → `stop()` → 포커스 반환.
- 포커스 일시 손실: 출력 정지, 회복 시 재개. 영구 손실: 정지(메트로놈도).
- DI 프레임워크 없음. `MainActivity`에서 `SettingsRepository`, `AudioController`를 생성하고 ViewModel 팩토리로 넘긴다.

## 7. 에러 처리
| 상황 | 대응 |
|---|---|
| `AudioTrack` 생성 실패 | `getMinBufferSize` 기반 + 기본 성능 모드로 1회 재시도. 실패 시 상단 배너 "오디오 초기화 실패", UI는 소리 없이 유지 |
| 언더런 | 카운트만, 디버그 정보 표시. 자동 조정 없음 |
| 커맨드 큐 가득 참 | 새 커맨드 드롭 + 카운트 |
| 오디오 포커스 손실 | 6.5 참고 |
| DataStore 실패 / 범위 밖 값 | 기본값 / 클램프 |
| 오디오 스레드 예외 | 잡아서 출력 정지 + 배너. 앱은 죽지 않는다 |

## 8. 테스트 전략

### 8.1 아키랩 — `gradlew :core:test` (JVM, JUnit 5)
| 대상 | 검증 |
|---|---|
| `Pitch`/`NoteName` | E1 = 41.20 Hz, A4 = 440, 옥타브 배수, 12음 이름 |
| `Fretboard` | 4줄 × 25프렛 전 위치의 MIDI·이름이 기대표와 일치 |
| `KarplusStrongVoice` | 48 kHz에서 E1·A2·G4 렌더 → 자기상관 기본 주파수 오차 ±1% 이내; 100 ms 창별 RMS 단조 감소; `setPitch` 5→7프렛 시 인접 샘플 최대 차이가 임계값 미만(클릭 없음)이고 램프 후 목표 주파수 도달; 재피킹 시 RMS 회복; 충분히 감쇠하면 `isActive = false` |
| `Mixer` | 4보이스 만렙 합산이 (−1, 1) 안, 마스터 게인 비례 |
| `Metronome` | 120 BPM·48 kHz에서 클릭 온셋 간격 정확히 24000 샘플 — 청크 크기 97·256·1000 혼합으로 렌더해도; 1박 강세 주파수; 정지→시작 시 위상 리셋 |
| `CommandQueue` | FIFO, wraparound, 용량 초과 드롭 카운트 |
| `SynthEngine` | 커맨드 없으면 무음(전부 0); `NoteOn` 후 소리; `AllNotesOff` 후 감쇠 |

"렌더 루프 내 할당 0"은 테스트가 아니라 코드 리뷰 규칙이다.

### 8.2 아키랩 — 컴파일 검증
`gradlew :app:assembleDebug :app:lintDebug`. 통과가 push 조건.

### 8.3 검증 환경 — 실기기 체크리스트 (`docs/verification-checklist.md`, M1에서 작성)
설치·실행 / 터치→소리 체감 지연 / 멀티터치 화음 / 슬라이드 / 레이크 / 띠 스크롤+스냅 / 음이름 / 메트로놈 정확도(다른 메트로놈과 1분 비교) / 설정 유지(재시작) / 5분 연주 후 언더런 수 / 백그라운드 복귀 / 전화 인터럽트.
결과는 디버그 정보 숫자와 함께 체크리스트 파일에 기록해 push한다. 아키랩에서는 기기 동작을 "확인됨"으로 표기하지 않는다.

## 9. 구현 단계

각 마일스톤은 "아키랩 구현 → core 테스트·assembleDebug 통과 → push → 검증 환경 확인 → 피드백 반영" 한 바퀴다.
세부 작업 순서와 TDD 단계는 후속 구현 계획 문서에서 분해한다.

### M0 — 스캐폴드
목표: 두 모듈이 빌드되고 빈 앱이 기기에서 켜진다.
1. 최신 안정 버전 확인·고정: AGP, Gradle, Kotlin, Compose BOM, DataStore, JUnit 5 → `gradle/libs.versions.toml`
2. Gradle wrapper 생성(배포판은 `GRADLE_USER_HOME`으로), `.gitattributes`(`gradlew`·`*.sh`는 LF)
3. `:core` (`kotlin("jvm")`, JUnit 5) — 자리표시 테스트 1개로 파이프라인 확인
4. `:app` (compileSdk 36→37(M0에서 변경, 10장 참고), minSdk 33→31(2장 참고), Compose, 가로 고정) — "get-your-guitar" 텍스트만 있는 화면
5. `local.properties`는 gitignore, README에 빌드 명령 기록
완료 기준: 아키랩 `:core:test`·`:app:assembleDebug` 통과. 검증 환경: 설치·실행 확인.

### M1 — 첫 소리
목표: 지판을 탭하면 베이스 음이 난다.
1. `core/music` (TDD) — Tuning, Pitch, NoteName, Fretboard
2. `core/synth` (TDD) — KarplusStrongVoice(주파수·감쇠·재피킹·글라이드), StringVoices, Mixer
3. `core/engine` (TDD) — CommandQueue, SynthEngine(NoteOn/Slide/AllNotesOff/게인)
4. `app/audio` — AudioTrackOutput, AudioController, 수명주기 연결
5. `app/ui/fretboard` — EqualFretLayout, FretboardCanvas(렌더·하이라이트·음이름), FretboardGestures(탭/슬라이드/레이크/띠 스크롤+스냅), 상단 바에 음이름 토글
6. `docs/verification-checklist.md` 작성
완료 기준: core 테스트 전부 통과, assembleDebug 통과. 검증 환경: 체감 지연·멀티터치·슬라이드·스크롤 피드백.

### M2 — 연주감과 설정
목표: 음색을 조절할 수 있고 설정이 유지된다.
1. `core/synth` — ToneParams 매핑(밝기·감쇠), 소프트 클립 검증, 슬라이드 에너지 딥
2. `app/data` — Settings, SettingsRepository(DataStore)
3. `app/ui/settings` — 설정 화면, 디버그 정보, 버퍼 배수(출력 재시작)
4. 설정 → 엔진 커맨드 연결, 화면 전환(`screen` + BackHandler)
5. M1 피드백 반영(지연·판정 범위·하이라이트 등)
완료 기준: 설정 변경이 즉시 소리에 반영되고 재시작 후 유지. 검증 환경: 5분 연주 언더런 수 보고.

### M3 — 메트로놈과 마무리
목표: 메트로놈과 함께 연습할 수 있고 인터럽트에 안전하다.
1. `core/metronome` (TDD) — Metronome 샘플 단위 스케줄, ClickVoice, EngineStats.currentBeat
2. `core/engine` — 메트로놈 커맨드 4종
3. `app/ui/metronome` — MetronomeBar(±, 길게 누르기, 슬라이더 다이얼로그, 비트 점)
4. 오디오 포커스 처리, 오디오 스레드 예외 처리·배너
5. 체크리스트 전 항목 재검증
완료 기준: 체크리스트 전 항목 통과 기록. 이후 v2 백로그 착수.

## 10. 스택·환경
- Kotlin 2.x, AGP 최신 안정판(compileSdk 36 / targetSdk 36 / minSdk 33 — 실제 고정값은 37 / 36 / 31, 아래 기록과 2장 참고), Compose BOM 최신, kotlinx.coroutines, DataStore Preferences, JUnit 5. 정확한 버전은 M0에서 확인해 `libs.versions.toml`에 고정. **[M0 실행 기록 2026-09-18]** androidx.core 1.19.0·Compose 1.12.1이 API 37 이상 컴파일을 요구해 **compileSdk는 37**로 고정했다(targetSdk 36·minSdk 33은 유지). AGP가 `platforms;android-37`을 자동 다운로드한다.
- 개발 환경 워크플로우와 아키랩 의존성은 `CLAUDE.md`, `README.md` 참고. 아키랩은 코드·JVM 테스트·컴파일까지, 실기기 검증은 검증 환경.

## 11. 가정과 열린 값
- 지연 수치(30~45 ms)는 추정이며 실측은 검증 환경에서. 불만족 시 버퍼 배수 조정 → 그래도 안 되면 Oboe 백엔드 추가(4.2 경계 1).
- 밝기·감쇠 매핑 범위, 램프 길이(8 ms), 에너지 딥(0.85), 하이라이트 감쇠(1.5 s)는 초기값이며 검증 피드백으로 조정한다.
- 터치 벨로시티는 v1에서 상수. 기기에서 압력/면적이 유의미하면 v2에서 검토.

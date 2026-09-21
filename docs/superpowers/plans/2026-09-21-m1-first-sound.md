# M1 "첫 소리" 구현 계획

- 작성일: 2026-09-21 (SDS PC)
- 스펙: `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` — 4장(아키텍처), 5장(`:core`), 6.1·6.2·6.5(`:app`), 8장(테스트), 9장 M1
- 목표: 지판을 탭하면 베이스 음이 난다. 슬라이드·레이크·멀티터치·띠 스크롤·음이름 토글 포함.
- 완료 기준: `:core:test`·`:app:testDebugUnitTest` 전부 통과, `:app:assembleDebug`·`lintDebug` 통과(린트 에러 0). 검증 환경: 체감 지연·멀티터치·슬라이드·스크롤 피드백.

M0 계획과 달리 이 문서는 전체 코드를 싣지 않는다. 작업 단위·인터페이스·테스트 목록·설계 결정만 적고, 코드는 저장소가 정본이다.

## Global Constraints

- 패키지: `com.sproutgreen.getyourguitar.core.{music,synth,engine}`, `com.sproutgreen.getyourguitar.{audio,ui.fretboard}`.
- `:core`는 Android 의존 0. `kotlin("jvm")`만.
- **오디오 스레드 규칙** (스펙 4.3): `SynthEngine.render` 에서 호출되는 모든 경로는 객체 할당·락·로그 금지. 버퍼는 생성자에서 할당. 난수도 자체 xorshift(할당 없음).
- TDD: 각 Task는 테스트 먼저 → 실패 확인 → 구현 → 통과 → 커밋.
- M1에 넣지 않는 것 (스펙 9장 순서 유지): ToneParams를 바꾸는 커맨드·설정 화면·DataStore·슬라이드 에너지 딥(M2), 메트로놈·오디오 포커스·오디오 스레드 예외 배너(M3).
- 기기에서 귀로 확인해야 하는 항목은 "검증 환경에서 확인 필요"로 남긴다. adb로 확인 가능한 것(설치, 렌더, 커맨드 로그, FAST 트랙 여부)은 SDS PC에서 확인하고 그렇게 기록한다.

## Task 1 — `core/music`

| 타입 | 인터페이스 |
|---|---|
| `Tuning(openMidi: IntArray)` | `stringCount`, `openMidi(string)`, `STANDARD_BASS_4 = [28,33,38,43]`. 줄 0 = E(최저), 3 = G |
| `Pitch` | `hz(midi: Int): Float` = 440·2^((midi−69)/12) |
| `NoteName` | `of(midi): String` — 샤프 표기 12음, 옥타브 없음 |
| `FretPosition(string, fret)` | 데이터 클래스 (UI용. 오디오 스레드는 Int 오버로드 사용) |
| `Fretboard(tuning, fretCount = 24)` | `midiAt(string, fret)`, `hzAt(string, fret)`, `nameAt(string, fret)` + `FretPosition` 오버로드. 범위 밖이면 `IllegalArgumentException` |

테스트: E1 = 41.20 Hz(±0.01), A4 = 440, 옥타브 = 2배, 12음 이름, 음수 아닌 모든 MIDI의 이름 주기성, 4줄×25프렛 전 위치 MIDI·이름 기대표, 범위 밖 예외.

## Task 2 — `core/synth`

| 타입 | 인터페이스 |
|---|---|
| `ToneParams(brightness, decay)` | 0~1 클램프. `cutoffHz()` = 500·12^brightness (500 Hz~6 kHz 로그), `feedback()` = 0.990 + decay·0.0095. 기본값 (0.6, 0.7). M1에서는 기본값만 쓴다 |
| `Voice` | `noteOn(hz, velocity)`, `setPitch(hz)`, `render(out, offset, frames)`(가산), `isActive`, `silence()` |
| `KarplusStrongVoice(sampleRate, tone, seed)` | 아래 설계 |
| `StringVoices(count, sampleRate, tone)` | `noteOn(string, hz, velocity)`, `setPitch(string, hz)`, `silenceAll()`, `render(out, offset, frames)`, `anyActive` |
| `Mixer` | `process(buf, frames, masterGain)`: x·0.5·master → `x/(1+|x|)` |

**`KarplusStrongVoice` 설계**
- 딜레이 라인 `FloatArray(sampleRate/25 + 8)`, 쓰기 인덱스 `w`. 샘플마다: `x = interp(buf, w − delayLen)` → `lp += a·(x − lp)` → `buf[w] = g·lp` → 출력 `lp · env`.
- **튜닝 보정**: 루프 총 지연 = `delayLen` + 1극 LP의 위상 지연. `delayLen = sampleRate/hz − phaseDelay(hz)`, `phaseDelay = atan2((1−a)·sin ω, 1 − (1−a)·cos ω) / ω`. 보정 없이는 G4가 ~2.4%(41센트) 낮다.
- 여기: 버퍼 전체 0 → 최근 `floor(delayLen)+2` 샘플을 LP 거른 xorshift 노이즈로 채움 → 평균 제거 → **피크를 velocity로 정규화**(밝기·음높이와 무관하게 음량 일정).
- 재피킹(`noteOn` 중 활성): `env`를 2 ms에 0으로 → 그 시점에 새 버스트 주입 → 2 ms에 1로. 비활성이면 즉시 주입.
- `setPitch`: `delayLen`을 8 ms 선형 램프로 목표까지. 재여기 없음. 페이드아웃 중이면 대기 중인 목표만 갱신. 비활성이면 무시.
- `silence()`: 2 ms 페이드아웃 후 비활성(버퍼 0).
- `isActive`: `render` 호출(청크) 피크가 1e-4 미만인 청크가 4번 연속이면 false.

**알려진 특성 (검증 환경에서 확인 필요)**: 1극 LP가 기음도 주기마다 깎아서 감쇠율이 음높이의 세제곱에 비례한다. 기본 톤(컷오프 ≈2.2 kHz) 추정: E1 ≈ −1.3 dB/s(피드백 g 지배), G3 ≈ −8 dB/s, G4 ≈ −60 dB/s. G줄 15프렛 이상이 너무 짧게 끊기면 M2에서 컷오프를 기음에 연동(key-tracking)하는 것을 검토한다.

테스트 (48 kHz): E1·A2·G4 자기상관 기본 주파수 ±1%; 창별 RMS 감소(허용 오차 2%, 전체로는 뚜렷한 감소); `setPitch` 5→7프렛에서 인접 샘플 최대 차이가 글라이드 전 최대치의 2배 미만이고 램프 후 목표 주파수 ±1%; 재피킹 시 RMS 회복 + 경계에서 클릭 없음; 충분히 감쇠하면 `isActive=false`; `silence()` 후 비활성; 비활성 보이스는 out을 건드리지 않음; `render`는 가산. `Mixer`: 4보이스 최대 합산이 (−1,1) 안, 게인 단조, 0 게인 = 무음.

## Task 3 — `core/engine`

| 타입 | 인터페이스 |
|---|---|
| `Command` (sealed) | `NoteOn(string, fret)`, `Slide(string, fret)`, `AllNotesOff`, `SetMasterGain(gain)`. M2·M3에서 추가 |
| `CommandQueue(capacity = 256)` | SPSC 링버퍼, 슬롯 = Int×4(type, a, b, floatBits). `offer(cmd): Boolean`(생산자, 가득 차면 false), `poll(slot: IntArray): Boolean`(소비자, 할당 없음) |
| `EngineStats` | `@Volatile droppedCommands` |
| `SynthEngine(sampleRate, tuning)` | `send(cmd)`(UI 스레드), `render(out, frames)`(오디오 스레드): 큐 비우기 → out 0 → 보이스 가산 → Mixer. `stats` |

테스트: FIFO, wraparound(용량의 몇 배를 넣고 빼기), 용량 초과 시 드롭+카운트, float 인자 비트 보존; 엔진: 커맨드 없으면 전부 0, `NoteOn` 후 소리, 음높이가 (string, fret)에 맞음, `Slide` 후 음높이 변화, `AllNotesOff` 후 무음 도달, 범위 밖 string/fret 커맨드는 무시(크래시 없음), `SetMasterGain` 비례.

## Task 4 — `app/audio`

- `AudioOutput` 인터페이스 (스펙 6.1 그대로).
- `AudioTrackOutput(context, bufferChunks = 2)`: `AudioManager` 속성에서 샘플레이트·프레임/버퍼(파싱 실패 시 48000/256). `ENCODING_PCM_FLOAT`·모노·`PERFORMANCE_MODE_LOW_LATENCY`·`USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`. 용량은 `max(getMinBufferSize, fpb·chunks·4바이트)`로 만들고 생성 후 `setBufferSizeInFrames(fpb·chunks)`로 실효 버퍼를 줄인다. 생성 실패 시 기본 성능 모드로 1회 재시도(스펙 7장). 전용 스레드 `THREAD_PRIORITY_URGENT_AUDIO`, `write(..., WRITE_BLOCKING)`. 시작 시 실제 `performanceMode`·버퍼 크기를 `Log.i`로 1회 남긴다(오디오 스레드 밖).
- `AudioController(context)`: 출력·엔진 소유, `send`, `start`, `stop`, `error: String?`.
- `MainActivity`: `FLAG_KEEP_SCREEN_ON`, 시스템 바 숨김(연주 중 뒤로/홈 오터치 방지), `onStart` → `start()`, `onStop` → `AllNotesOff` + `stop()`. 오디오 포커스는 M3.

## Task 5 — `app/ui/fretboard`

- `FretLayout` / `EqualFretLayout(cellWidth, scroll)`: `xOf(u) = (u − scroll)·cellWidth`, `uAt(x)` 역변환.
- `FretboardGeometry`(순수 Kotlin): 영역 크기 + layout → `(string, fret)` 판정. fret = `floor(u)+1` 클램프 0..24, string = `3 − floor((y−top)/bandH)` 클램프. 지판에서 시작한 포인터가 세로로 벗어나면 가장자리 줄로 클램프.
- `FretboardTouchTracker`(순수 Kotlin): 포인터별 마지막 (string, fret) 기억. down → `NoteOn`; move 같은 줄·다른 프렛 → `Slide`; 다른 줄 진입 → `NoteOn`; 같은 셀 → 무시; up → 제거만.
- `FretboardGestures`(Compose `pointerInput`): down 위치가 띠면 스크롤 포인터, 지판이면 연주 포인터. 스크롤: `scroll −= dx/cellWidth` 클램프 [−1, 12], up 시 가장 가까운 정수로 150 ms tween.
- `FretboardCanvas`: 갈색 그라디언트, 너트(u=0), 프렛 와이어, 포지션 마크(3·5·7·9·15·17·19·21 단점, 12·24 이중점), 줄 굵기 E > A > D > G, 음이름(토글), 하이라이트(1.5 s 알파 감소), 위 띠에 프렛 번호.
- `FretboardScreen`: 상단 바(12%) + 위 띠(8%) + 지판 + 아래 띠(8%). 상단 바에는 음이름 토글만(⚙는 M2, 메트로놈은 M3). 디스플레이 컷아웃 패딩.
- ViewModel은 M2(설정 저장소)와 함께 도입. M1은 컴포저블 상태로 충분하다.

`:app` 단위 테스트(`:app:testDebugUnitTest`, JUnit): `EqualFretLayout` 왕복·스크롤, `FretboardGeometry` 셀 경계·클램프, `FretboardTouchTracker` 제스처 표 전 행. 이 셋은 Android 의존이 없어 아키랩에서도 돈다.

## Task 6 — 검증·문서

- `docs/verification-checklist.md` (스펙 8.3): M1 항목 + M2·M3 항목 자리.
- SDS PC에서 adb로 확인: 설치·실행, 스크린샷으로 지판 렌더, `input tap`/`swipe` 후 logcat의 커맨드 로그(디버그 빌드), `dumpsys media.audio_flinger`에서 FAST 트랙 여부.
- README·CLAUDE.md 빌드 명령에 `:app:testDebugUnitTest` 추가, STATUS.md·메모리 갱신, push.

## 실행 기록 (2026-09-21, SDS PC)

전 Task 완료. 계획과 달랐던 점·측정값만 적는다.

- **튜닝 실측**: E1·A1·G2·D3·G3·D4·G4 모두 +0.3~+0.8센트. 위상 지연 보정이 없었다면 G4가 약 −41센트.
- **감쇠 실측**(기본 톤, 첫 0.5 s RMS 기준): E1 −8.0, A1 −8.1, G2 −7.2, D3 −10.1, G3 −14.7, D4 −35.8, G4 −56.1 dB/s. 저음이 추정(−1.3)보다 빠른 것은 배음이 먼저 타들어가기 때문이고 정상이다. 고음역은 예측대로 짧다 → 체크리스트 M1 #15.
- 버스트 피크를 velocity로 정규화했지만 출력은 루프 LP를 한 번 더 거치므로 실제 피크는 0.42~0.59.
- `AudioOutput.start`가 `(FloatArray, Int) -> Unit` 대신 `AudioRenderer`(fun interface)를 받는다 — 함수 타입은 콜백마다 Int를 박싱한다. `start`는 성공 여부를, `stop`은 오디오 스레드 종료 여부를 Boolean으로 돌려준다(스펙 6.1과의 차이).
- `AudioController.stop()`은 오디오 스레드가 끝난 뒤 메인 스레드에서 20 ms를 렌더해 페이드아웃을 흘려보낸다. 안 그러면 다음 `start` 첫 버퍼에 이전 음의 꼬리가 섞인다.
- 시스템 바를 숨겼다(스펙에 없음). 가로 모드에서 내비게이션 바가 연주 영역 오른쪽에 붙어 오터치가 나기 때문.
- Canvas는 경계 밖을 자르지 않는다. 스크롤 중 화면 밖 셀의 포지션 마크가 컷아웃 여백에 그려져서 `clipToBounds()` 추가.
- ViewModel 없이 컴포저블 상태로 구현(계획대로). M2에서 설정 저장소와 함께 도입.
- S10e 결과: FAST 트랙 F3, float, 48 kHz, 버스트 192, 버퍼 384프레임, 언더런 0, AudioFlinger 보고 지연 16 ms.
- adb로 확인 못 한 것: 멀티터치, 소리 자체, 체감 지연. `docs/verification-checklist.md` 참고.

### 사용자 피드백과 후속 수정 (2026-09-21)

- 소리 남 ✅, 체감 지연 거의 없음 ✅, 멀티터치 화음 ✅, **고음역이 조금 짧음 ❌**.
- 수정: `ToneParams.cutoffHz(noteHz)` = 기본 컷오프 × √(noteHz / 41.2 Hz), 하한 1배, 상한 12 kHz. 보이스가 `noteOn`·`setPitch`마다 로우패스 계수를 다시 계산하고, 위상 지연 보정도 그 계수로 한다.
- 완전 비례(배음 수 고정) 대신 제곱근인 이유: 완전 비례면 밝기 슬라이더가 사실상 "배음 개수"가 되어 스펙의 500 Hz~6 kHz 매핑과 의미가 달라지고, 고음이 저음만큼 밝아져 부자연스럽다.
- 실측(전 → 후, dB/s): E1 −8.0 → −8.0, G2 −7.2 → −7.9, G3 −14.7 → −10.6, D4 −35.8 → −18.2, G4 −56.1 → −19.8. 튜닝 오차는 여전히 +0.2~+0.5센트.
- 그래도 짧다면 다음 손잡이는 피드백 게인 g(감쇠 슬라이더, M2)다. g 손실은 음높이에 비례하므로(G4에서 약 11 dB/s) 감쇠 0.7 → 0.85면 고음역이 더 길어진다.

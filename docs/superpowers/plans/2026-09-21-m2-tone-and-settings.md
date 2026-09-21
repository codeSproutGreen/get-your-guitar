# M2 "연주감과 설정" 구현 계획

> **2026-09-21 갱신: 이 계획은 v2 기능 정의(`../specs/2026-09-21-v2-features.md`)에 흡수돼 그쪽 6장 "구현 기록"대로 구현됐다.** 달라진 점: 커맨드 타입은 6 NoteOff / 7 SetBrightness / 8 SetDecay, 설정 키에서 메트로놈·프렛 간격 제외, `holdToSustain` 추가, ViewModel 대신 `AppState`. 아래 본문은 당시 계획 그대로 남긴다.

- 작성일: 2026-09-21 (SDS PC)
- 스펙: `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` — 5.2(ToneParams·에너지 딥), 5.4(커맨드), 6.4(설정), 6.5(조립), 7장, 9장 M2
- 목표: 음색을 조절할 수 있고 설정이 유지된다.
- 완료 기준: `:core:test`·`:app:testDebugUnitTest`·`:app:assembleDebug`·`:app:lintDebug` 통과(린트 에러 0). 검증 환경: 설정 변경이 즉시 소리에 반영, 재시작 후 유지, 5분 연주 언더런 수 보고.

M1 계획과 같은 형식: 작업 단위·인터페이스·테스트 목록·결정만 적는다. 코드는 저장소가 정본.

## M1 피드백에서 넘어온 것 (2026-09-21, Galaxy S10e)

| 피드백 | 상태 | M2에서 할 일 |
|---|---|---|
| 소리 잘 남, 지연 거의 없음, 화음 남 | ✅ | 없음. 버퍼 배수 기본값 2 유지, Oboe 불필요 |
| 고음역이 조금 짧다 | 컷오프 음높이 연동으로 1차 수정(커밋 1e7e82a), 재청취 대기 | 여전히 짧으면 감쇠 기본값을 0.7 → 0.8~0.85로. 슬라이더가 생기면 사용자가 직접 찾을 수 있으므로 M2 검증 때 "마음에 드는 값"을 물어 기본값으로 삼는다 |
| 벤딩 요청 → M2 전에 구현(커밋 3e17a54, 05b50af) | 사람 확인 대기(체크리스트 M1 #20~23) | 벤딩 폭 설정(1음/2음) 추가. 데드존·80 ms 문턱이 손에 안 맞으면 여기서 조정 |
| 미확인: 슬라이드 매끄러움(#13), 연타 클릭(#14), 음색(#16), 판정 범위(#18), 시스템 바(#19) | ⬜ | M2 검증 때 함께 확인. 문제가 나오면 이 표에 추가 |

## Global Constraints

- M1과 동일: 오디오 스레드 할당·락·로그 금지, TDD, 패키지 규칙, 메인 스레드 단일 생산자.
- 새 의존성은 카탈로그에 이미 고정돼 있다: `androidx-datastore-preferences` 1.2.1, `androidx-lifecycle-viewmodel-compose` 2.11.0, `kotlinx-coroutines-android` 1.11.0. 테스트용으로 `kotlinx-coroutines-test`(같은 버전)를 카탈로그에 추가한다.
- M2에 넣지 않는 것: 메트로놈 관련 설정 2개(`metronomeVolume`, `bpm`)의 **엔진 반영**은 M3. 다만 `Settings` 모델과 저장은 M2에서 8개 키 전부 만든다(스키마를 한 번에 확정).

## Task 1 — `core`: 음색 커맨드와 슬라이드 에너지 딥

| 변경 | 내용 |
|---|---|
| `Command` | `SetBrightness(value)`, `SetDecay(value)` 추가. `CommandCodec` 타입 **6, 7** (5는 벤딩이 이미 씀. 타입 번호는 바꾸지 않는다) |
| `SynthEngine` | 현재 `brightness`·`decay`를 들고 있다가 둘 중 하나가 오면 `ToneParams`를 새로 만들어 `voices.setTone`. **`ToneParams` 생성은 할당이다** → 오디오 스레드 규칙 위반. `StringVoices.setTone(brightness, decay)`처럼 Float 두 개를 받는 경로를 추가하고 `ToneParams`의 매핑 함수는 `companion`의 순수 함수(`cutoffHz(brightness, noteHz)`, `feedback(decay)`)로 옮긴다. `ToneParams` 클래스는 UI·테스트용 값 객체로 남긴다 |
| `KarplusStrongVoice` | `setTone(brightness, decay)`. 울리는 중이면 필터 계수 즉시 반영(M1에 이미 있음). 에너지 딥: **슬라이드의** 글라이드 8 ms 동안 출력 게인을 1 → 0.85 → 1로(전반 4 ms 내려가고 후반 4 ms 복귀). 루프 내부가 아니라 출력에만 곱한다. **벤딩에는 딥을 걸지 않는다** — 벤딩은 `setPitch`가 초당 100번 넘게 오므로 딥을 걸면 벤딩 내내 음량이 0.85로 눌린다. `Voice.setPitch(hz)`와 별도로 `slideTo(hz)`를 두거나 `setPitch(hz, dip: Boolean)`로 구분한다 |

테스트: 새 커맨드 코덱 왕복; `SetBrightness` 0 vs 1에서 같은 음의 고역 에너지(인접 샘플 차이 RMS)가 뚜렷이 다름; `SetDecay` 0 vs 1에서 1초 뒤 RMS 비가 뚜렷이 다름; 울리는 중 `SetBrightness`에 클릭 없음; 범위 밖 값 클램프; 글라이드 중 출력 포락선이 0.85 부근까지 내려갔다 복귀하고 클릭 없음; 글라이드가 끝나면 게인이 정확히 1; 기존 60개 테스트 유지. 소프트 클립 검증은 M1의 `Mixer` 테스트로 충족.

## Task 2 — `app/data`: 설정 모델과 저장소

| 타입 | 내용 |
|---|---|
| `Settings` (data class) | `bendRangeCents 200`(200 또는 400 — 벤딩 폭 1음/2음, 2026-09-21 추가), `masterVolume 0.8`, `brightness 0.6`, `decay 0.7`, `metronomeVolume 0.7`, `bpm 100`, `showNoteNames false`, `audioBufferChunks 2`, `fretLayout EQUAL`. `fun sanitized()`: 범위 밖 값 클램프(`bpm` 40~240, `audioBufferChunks` ∈ {2,3,4} 아니면 2) |
| `FretLayoutKind` (enum) | `EQUAL` (v2: `REAL`). 알 수 없는 저장값 → `EQUAL` |
| `SettingsRepository` (interface) | `val settings: Flow<Settings>`, `suspend fun update(transform: (Settings) -> Settings)` |
| `DataStoreSettingsRepository(dataStore)` | Preferences DataStore. 읽기 `IOException` → 기본값 emit. 쓰기 전에 `sanitized()` |

테스트(JVM, `:app:testDebugUnitTest`): `Settings.sanitized` 경계값 전부; `DataStoreSettingsRepository`는 `PreferenceDataStoreFactory.create(scope, produceFile = 임시 파일)`로 실제 DataStore를 JVM에서 띄워 기본값·왕복·클램프·알 수 없는 enum을 검증(DataStore Preferences는 Android 없이 동작). `kotlinx-coroutines-test`의 `runTest` 사용.

## Task 3 — `app`: 설정 → 엔진 연결

- `AudioController`: `applySettings(s: Settings)` — 직전에 적용한 값과 달라진 항목만 커맨드로 보낸다(`SetMasterGain`, `SetBrightness`, `SetDecay`). `audioBufferChunks`가 바뀌면 출력 정지 → 새 `AudioTrackOutput(chunks)` → 재시작, 엔진은 그대로. 출력 교체 중에는 `send`가 큐에 쌓이기만 하므로 안전하다.
- `AudioController`에 디버그 스냅샷: `sampleRate`, `framesPerBuffer`, `bufferChunks`, `underrunCount`, `droppedCommands`, `isRunning`.
- `MainViewModel(repository, audio)`: `settings: StateFlow<Settings>`, `update { }`. `init`에서 `settings`를 수집해 `audio.applySettings` 호출. `onStart` 직후 저장값이 엔진에 들어가야 하므로 첫 값은 `start()` 뒤에 즉시 적용.
- DI 없음. `MainActivity`가 `dataStore`·저장소·`AudioController`를 만들고 `viewModelFactory`로 넘긴다(스펙 6.5).
- `showNoteNames`는 M1의 `rememberSaveable`에서 설정값으로 옮긴다(상단 바 토글은 저장소를 갱신).

테스트: `applySettings`의 "달라진 것만 보낸다" 로직을 순수 함수 `SettingsDiff.commands(old: Settings?, new: Settings): List<Command>`로 빼서 JVM 테스트. 버퍼 배수 변경 감지도 같은 곳에서(`needsOutputRestart`).

## Task 4 — `app/ui/settings`: 설정 화면과 내비게이션

- `SettingsScreen`: 슬라이더 4개(마스터·밝기·감쇠·메트로놈 볼륨 — 메트로놈은 M3 전까지 저장만 되고 소리에는 영향 없음을 회색 설명으로 표기), 음이름 스위치, 버퍼 배수 2/3/4 선택, 디버그 정보(1초마다 갱신). 가로 화면이므로 2열 배치: 왼쪽 슬라이더, 오른쪽 토글·버퍼·디버그.
- 슬라이더는 드래그 중에도 즉시 엔진에 반영(연주하며 듣기 위해). DataStore 쓰기는 값이 바뀔 때마다 해도 되지만(작은 파일), 드래그 중 초당 수십 번 쓰지 않도록 `onValueChangeFinished`에서 저장하고 드래그 중에는 ViewModel의 메모리 상태만 갱신한다.
- 내비게이션: `MainActivity`의 `var screen by rememberSaveable { FRETBOARD }` + `BackHandler(screen == SETTINGS)`. 상단 바 왼쪽에 ⚙ 버튼. 설정 화면에서도 오디오는 계속 돈다.
- 설정 화면에서 소리를 확인할 수단: 화면 아래에 "E · A · D · G 개방현 시험음" 버튼 4개(각각 `NoteOn(string, 0)`). 스펙에 없지만, 없으면 슬라이더를 만질 때마다 지판 화면을 오가야 한다.

## Task 5 — 검증·문서

- adb로 확인: ⚙ → 설정 화면 스크린샷, 슬라이더 조작 후 logcat의 `SetBrightness`/`SetDecay`/`SetMasterGain`, 앱 강제 종료 후 재실행해 값 유지, 버퍼 배수 변경 후 `gyg-audio` 재시작 로그와 `dumpsys media.audio_flinger`의 FrmCnt(384 → 576 → 768), 뒤로 가기로 지판 복귀.
- 사람: 슬라이더가 귀에 즉시 들리는가, 마음에 드는 밝기·감쇠 값, 5분 연주 후 디버그 정보의 언더런 수, M1 체크리스트 #13~19.
- `docs/verification-checklist.md` M2 절 갱신, STATUS.md·메모리, push.

## 열린 결정 (구현 중 확정)

1. **감쇠 기본값**: 재청취 피드백에 따라 0.7 유지 또는 상향.
2. **ViewModel 범위**: 화면이 둘뿐이라 `MainViewModel` 하나로 충분하다고 본다. 스펙은 `FretboardViewModel`·`SettingsViewModel` 둘을 적었지만 공유 상태(설정)가 전부라 나눌 이유가 없다 — 구현하며 어색하면 나눈다.

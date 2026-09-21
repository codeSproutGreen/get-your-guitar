# get-your-guitar

Android 앱 프로젝트 (베이스 지판 시뮬레이터). Kotlin 2.4 + Jetpack Compose, Gradle 9.7 멀티모듈: `:core`(순수 JVM, JUnit 5) + `:app`(AGP 9.4, compileSdk 37 / targetSdk 36 / minSdk 31). 설계 스펙은 `docs/superpowers/specs/`, 구현 계획은 `docs/superpowers/plans/`.

## 개발 환경 워크플로우

- **GitHub `origin/main`이 SSOT(single source of truth).** 어떤 로컬 작업본도 정본이 아니다.
- 환경 종류
  - **아키랩(Archilab)**: Android 기기 연결 불가. 초기 개발(코드 작성, 구조 설계, 가능하면 빌드/단위테스트)만 담당. 기기 동작 검증은 불가.
  - **검증 환경**: GitHub에서 받아 실기기에서 검증·수정 후 push.
- 규칙
  1. 작업 시작 전 `git pull --rebase origin main`, 그리고 `docs/STATUS.md`(환경 간 진행 상태 로그)를 읽는다. push 전에 STATUS.md를 갱신한다.
  2. 커밋은 작고 언제든 push 가능한 상태로 유지. 세션 종료 전 반드시 push.
  3. 실기기에서 확인되지 않은 동작은 커밋 메시지·문서에 **"검증 환경에서 확인 필요"** 로 명시한다. 아키랩에서 "기기 동작 확인됨"이라고 쓰지 않는다.
  4. 도구(JDK, Android SDK, Gradle 캐시, adb)는 저장소에 넣지 않는다. 각 환경에 설치되어 있다고 가정한다. `local.properties`는 gitignore.

## Claude 메모리 (환경 간 공유)

- Claude Code auto-memory는 저장소의 `.claude/memory/` 에 저장되며 **git으로 추적한다.**
- 각 환경에서 최초 1회 `scripts/link-claude-memory.ps1`(Windows) 또는 `scripts/link-claude-memory.sh`(Linux/macOS)를 실행하면
  `~/.claude/projects/<encoded-project-path>/memory` 가 이 디렉토리로 링크(junction/symlink)된다.
- 메모리 파일을 추가·수정했으면 코드와 함께 커밋한다. 다른 환경의 Claude가 그 메모리를 읽는다.
- `.claude/memory/MEMORY.md` 는 인덱스. 각 메모리는 개별 파일.

## 빌드·테스트 명령

- `./gradlew :core:test` — 순수 Kotlin 로직 테스트. push 전 필수.
- `./gradlew :app:testDebugUnitTest` — `ui/fretboard`의 순수 Kotlin 부분(레이아웃·판정·제스처→커맨드). push 전 필수.
- `./gradlew :app:assembleDebug :app:lintDebug` — 컴파일·린트. push 전 필수. 린트 에러 0 유지(경고는 허용).
- 아키랩(Windows, Claude Code Bash)에서는 환경변수가 세션에 없을 수 있으니 앞에
  `export JAVA_HOME='E:\yjane.kim\tools\jdk-17.0.20.1+1' GRADLE_USER_HOME='E:\yjane.kim\tools\gradle-user-home'` 를 붙이고 `./gradlew.bat`을 쓴다.
- SDS PC(검증 환경)는 `JAVA_HOME`이 Android Studio JBR(21)로 잡혀 있어 프리앰블 없이 `./gradlew.bat`을 바로 쓴다. `local.properties`에 `sdk.dir` 필요.
- 릴리스: `./gradlew :app:assembleRelease` → `gh release create vX.Y.Z <apk>`. R8 축소 + 빌드 PC의 디버그 키 서명. 릴리스 전에 축소된 APK를 실기기에 설치해 실행·소리·크래시를 확인한다(R8은 런타임에서만 드러나는 문제를 만들 수 있다). 절차와 서명 주의사항은 README "릴리스 APK".
- 버전은 `gradle/libs.versions.toml`이 단일 진실. 올릴 때는 그 파일만 고친다.
- `:app`은 AGP built-in Kotlin을 쓴다 — `org.jetbrains.kotlin.android` 플러그인을 적용하지 않는다.
- Claude Code Bash 도구의 heredoc은 연속된 백슬래시 2개를 1개로 깎는다. 백슬래시가 든 Kotlin/정규식 문자열은 Edit 도구로 쓴다.
- 실기기 확인(SDS PC): `./gradlew.bat :app:installDebug` → `adb shell am start -W -n com.sproutgreen.getyourguitar/.MainActivity` → `adb exec-out screencap -p > <scratchpad>/x.png` 로 스크린샷을 읽어 확인한다. Git Bash에서 `adb shell`에 `/sdcard/...` 경로를 넘길 때는 `export MSYS_NO_PATHCONV=1` 필수.
- 실기기 터치 주입: `adb shell input tap X Y` / `input swipe X1 Y1 X2 Y2 ms`, 결과는 `adb logcat -d -s gyg-cmd:D`(디버그 빌드에서 커맨드 로그)와 `gyg-audio:V`(트랙 시작 로그). 멀티터치는 주입 불가 — 사람이 확인한다. S10e 기준 좌표(뮤트 바가 생긴 뒤): 왼쪽 컷아웃 여백 116 px, 셀 폭 ≈180 px, 줄 중심 y = G 305 / D 483 / A 661 / E 840, 스크롤 띠 y≈170, 뮤트 바 y≈1004, 상단 바의 설정 버튼 (230, 64).
- **제스처는 사람 속도로 검증한다.** `input swipe`는 터치하는 순간부터 등속으로 움직이므로 레이크 재현에 맞다 — 지속 시간을 한 줄당 50~600 ms가 되게 여러 개로 돌린다(빠른 것 하나만 돌려서 사람 속도의 레이크가 전부 벤딩이 되는 걸 놓친 적이 있다). "짚고 멈췄다 움직이는" 동작과 시스템 취소는 `adb shell "input motionevent DOWN x y; sleep 0.5; input motionevent MOVE x y2; input motionevent UP x y2"`, `... CANCEL x y`.
- 측정 전에 폰이 비어 있는지 확인한다: `adb logcat -c; sleep 3` 뒤에 `gyg-cmd` 줄이 0개여야 한다. 사용자가 폰을 만지고 있으면 주입과 섞여 결과가 엉망이 되고, 시스템이 제스처를 취소한다.
- 오디오 경로 확인: `adb shell dumpsys media.audio_flinger` 에서 앱 pid의 트랙 Type이 `F`로 시작하면 FAST 트랙.

## 코드 규칙

- **오디오 스레드 규칙**: `SynthEngine.render`에서 닿는 모든 코드는 객체 할당·락·로그 금지. `(FloatArray, Int) -> Unit` 같은 함수 타입도 Int 박싱을 일으키므로 쓰지 않는다(`AudioRenderer` fun interface 사용). 테스트로 강제되지 않으니 리뷰에서 본다.
- 엔진 큐는 단일 생산자다. `AudioController.send/start/stop`은 메인 스레드에서만 부른다.
- UI 로직 중 Android가 필요 없는 부분(좌표 계산, 제스처 해석)은 순수 Kotlin 클래스로 빼서 JVM 단위 테스트를 붙인다 — 아키랩에서 검증할 수 있는 유일한 UI 부분이다.
- 실기기 검증 결과는 `docs/verification-checklist.md`에 기록한다.
- **음색을 바꿀 때는 귀 대신 측정부터.** `ToneDemoRender`로 WAV를 뽑고 대역별 레벨(초저역 30~250 Hz / 중역 250 Hz~2 kHz / 고역)을 전후 비교한다. 폰 스피커는 중역만 재생하므로 중역이 줄면 폰에서는 나빠진 것이다(2026-09-21에 실제로 겪음: 물리적으로 맞는 파형으로 바꾸자 중역이 −4 dB). 최종 판단은 사람이 듣고 한다.
- 커맨드 타입 번호(`CommandCodec`)는 한 번 정하면 바꾸지 않는다: 1 NoteOn, 2 Slide, 3 AllNotesOff, 4 SetMasterGain, 5 Bend, 6 NoteOff, 7 SetBrightness, 8 SetDecay.
- `ToneParams` 같은 값 객체를 오디오 스레드에서 만들지 않는다. 엔진 → 보이스 경로는 Float 인자로 넘긴다.
- 테스트로 설정을 바꿨으면 끝에 `adb shell pm clear com.sproutgreen.getyourguitar`로 폰을 기본값으로 되돌린다.

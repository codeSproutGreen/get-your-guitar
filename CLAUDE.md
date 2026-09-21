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
- 버전은 `gradle/libs.versions.toml`이 단일 진실. 올릴 때는 그 파일만 고친다.
- `:app`은 AGP built-in Kotlin을 쓴다 — `org.jetbrains.kotlin.android` 플러그인을 적용하지 않는다.
- Claude Code Bash 도구의 heredoc은 연속된 백슬래시 2개를 1개로 깎는다. 백슬래시가 든 Kotlin/정규식 문자열은 Edit 도구로 쓴다.
- 실기기 확인(SDS PC): `./gradlew.bat :app:installDebug` → `adb shell am start -W -n com.sproutgreen.getyourguitar/.MainActivity` → `adb exec-out screencap -p > <scratchpad>/x.png` 로 스크린샷을 읽어 확인한다. Git Bash에서 `adb shell`에 `/sdcard/...` 경로를 넘길 때는 `export MSYS_NO_PATHCONV=1` 필수.
- 실기기 터치 주입: `adb shell input tap X Y` / `input swipe X1 Y1 X2 Y2 ms`, 결과는 `adb logcat -d -s gyg-cmd:D`(디버그 빌드에서 커맨드 로그)와 `gyg-audio:V`(트랙 시작 로그). 멀티터치는 주입 불가 — 사람이 확인한다. S10e 기준 좌표: 왼쪽 컷아웃 여백 116 px, 셀 폭 ≈180 px, 줄 중심 y = G 313 / D 508 / A 702 / E 896.
- 오디오 경로 확인: `adb shell dumpsys media.audio_flinger` 에서 앱 pid의 트랙 Type이 `F`로 시작하면 FAST 트랙.

## 코드 규칙

- **오디오 스레드 규칙**: `SynthEngine.render`에서 닿는 모든 코드는 객체 할당·락·로그 금지. `(FloatArray, Int) -> Unit` 같은 함수 타입도 Int 박싱을 일으키므로 쓰지 않는다(`AudioRenderer` fun interface 사용). 테스트로 강제되지 않으니 리뷰에서 본다.
- 엔진 큐는 단일 생산자다. `AudioController.send/start/stop`은 메인 스레드에서만 부른다.
- UI 로직 중 Android가 필요 없는 부분(좌표 계산, 제스처 해석)은 순수 Kotlin 클래스로 빼서 JVM 단위 테스트를 붙인다 — 아키랩에서 검증할 수 있는 유일한 UI 부분이다.
- 실기기 검증 결과는 `docs/verification-checklist.md`에 기록한다.

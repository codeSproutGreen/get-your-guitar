# get-your-guitar

Android 앱 프로젝트. 기술 스택은 확정 후 이 파일에 갱신한다.

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

# 프로젝트 진행 상태 (환경 간 인수인계 로그)

> 어느 환경에서든 세션 시작 시 이 파일을 먼저 읽고, push 전에 갱신한다.
> 대화 기록이나 로컬 메모리에만 있는 정보는 다른 환경에 전달되지 않는다 — 여기에 쓴다.

## 현재 위치 (2026-09-21)

| 마일스톤 | 상태 | 비고 |
|---|---|---|
| 설계 스펙 v1 | ✅ 승인 (2026-09-16) | `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` |
| M0 스캐폴드 — 계획 | ✅ 작성 (2026-09-16) | `docs/superpowers/plans/2026-09-16-m0-scaffold.md` |
| M0 스캐폴드 — 구현 | ✅ 빌드 통과 (2026-09-18, SDS PC) | `:core:test` 1개 PASSED, `assembleDebug`·`lintDebug` 통과(린트 에러 0·경고 4), APK 29.5 MB. **compileSdk 37**(스펙 36에서 변경, 계획서 "실행 기록" 참고) |
| M0 — 실기기 확인 | ⬜ **검증 환경에서 확인 필요** | 2026-09-21 테스트 폰 Galaxy S10e(SM-G970N, Android 12/API 31) 연결. 이 폰 때문에 **minSdk 33→31**. USB 디버깅 승인이 풀려 설치는 아직 못 함 |
| M0 — 아키랩 빌드 확인 | ⬜ 아키랩에서 확인 필요 | JDK 17 + `platforms;android-37` 자동 다운로드 여부 |
| M1 첫 소리 | ⬜ 미착수 | 계획 문서 없음. 스펙 9장 M1 참고 |
| M2 연주감·설정 | ⬜ | |
| M3 메트로놈·마무리 | ⬜ | |

## 환경

| 환경 | 역할 | 세팅 상태 |
|---|---|---|
| 아키랩 (Win Server 2019, `E:\yjane.kim\...`) | 초기 개발·빌드·단위테스트. 기기 없음 | ✅ JDK 17, SDK android-36, 메모리 링크 완료. M0 코드는 아직 여기서 빌드 안 해봄 |
| SDS PC (Win11, `C:\Users\SDS\...`) | 검증 환경 (Android Studio, adb) | ✅ 메모리 링크·M0 빌드 완료 2026-09-18. `local.properties` 작성됨(gitignore) |

## 다음 할 일

1. **SDS PC, 폰 연결 후:** `./gradlew.bat :app:installDebug` → 가로로 뜨고 가운데 "get-your-guitar" 텍스트 확인 → 이 파일 M0 행을 ✅로.
2. **아키랩 pull 후:** 프리앰블 붙여 `./gradlew.bat :core:test :app:assembleDebug :app:lintDebug`. `platforms;android-37` 자동 다운로드 실패 시 `sdkmanager "platforms;android-37"` 후 README 아키랩 표 갱신.
3. **M1 계획 작성** (`docs/superpowers/plans/`): 스펙 9장 M1 1~6을 TDD 단위로 분해. `:core` 작업(music/synth/engine)은 기기 없이 가능하므로 M0 실기기 확인을 기다리지 않고 시작해도 된다.

## 이력

- 2026-09-16 (아키랩): 저장소 생성, 워크플로우 문서, 툴체인 설치 기록, v1 스펙 승인, M0 계획 작성.
- 2026-09-18 (SDS PC): 환경 최초 세팅(메모리 링크), 툴체인 기록, 이 파일 생성.
- 2026-09-18 (SDS PC): **M0 구현** — Gradle 9.7.0 wrapper, 버전 카탈로그, `:core`(JUnit 6), `:app`(빈 Compose 화면). compileSdk 36→37. 커밋 4개(d962f12, 3c3251b, f38fa14, 9c6cac3). 실기기 미확인.
- 2026-09-21 (SDS PC): 테스트 폰 Galaxy S10e(Android 12) 연결 → minSdk 33→31. minSdk 31로 `:core:test`·`assembleDebug`·`lintDebug` 통과. adb 승인이 재연결 때 풀려(`unauthorized`) `installDebug`는 미완.

# 프로젝트 진행 상태 (환경 간 인수인계 로그)

> 어느 환경에서든 세션 시작 시 이 파일을 먼저 읽고, push 전에 갱신한다.
> 대화 기록이나 로컬 메모리에만 있는 정보는 다른 환경에 전달되지 않는다 — 여기에 쓴다.

## 현재 위치 (2026-09-21)

| 마일스톤 | 상태 | 비고 |
|---|---|---|
| 설계 스펙 v1 | ✅ 승인 (2026-09-16) | `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` |
| M0 스캐폴드 — 계획 | ✅ 작성 (2026-09-16) | `docs/superpowers/plans/2026-09-16-m0-scaffold.md` |
| M0 스캐폴드 — 구현 | ✅ 빌드 통과 (2026-09-18, SDS PC) | `:core:test` 1개 PASSED, `assembleDebug`·`lintDebug` 통과(린트 에러 0·경고 4), APK 29.5 MB. **compileSdk 37**(스펙 36에서 변경, 계획서 "실행 기록" 참고) |
| M0 — 실기기 확인 | ✅ 확인 (2026-09-21, SDS PC) | Galaxy S10e(SM-G970N, Android 12/API 31)에 `installDebug` 성공, 콜드 스타트 767 ms, 2280x1080 가로 화면 정중앙에 "get-your-guitar" 텍스트(UI 트리 bounds [958,501][1322,580]), 크래시 0. 이 폰 때문에 **minSdk 33→31** |
| M0 — 아키랩 빌드 확인 | ⬜ 아키랩에서 확인 필요 | JDK 17 + `platforms;android-37` 자동 다운로드 여부 |
| M1 첫 소리 — 구현 | ✅ (2026-09-21, SDS PC) | 계획 `docs/superpowers/plans/2026-09-21-m1-first-sound.md`. `:core` 56 + `:app` 22 테스트 통과, assembleDebug·lintDebug 통과(에러 0). S10e에서 FAST 트랙 획득(보고 지연 16 ms), adb 주입으로 탭·슬라이드·레이크·스크롤 스냅·음이름 확인 |
| M1 — 사람 검증 | ⬜ **검증 환경에서 확인 필요** | `docs/verification-checklist.md` M1 #10~19: 소리·체감 지연·멀티터치 화음·슬라이드 매끄러움·고음역 서스테인·음색 |
| M2 연주감·설정 | ⬜ | |
| M3 메트로놈·마무리 | ⬜ | |

## 환경

| 환경 | 역할 | 세팅 상태 |
|---|---|---|
| 아키랩 (Win Server 2019, `E:\yjane.kim\...`) | 초기 개발·빌드·단위테스트. 기기 없음 | ✅ JDK 17, SDK android-36, 메모리 링크 완료. M0 코드는 아직 여기서 빌드 안 해봄 |
| SDS PC (Win11, `C:\Users\SDS\...`) | 검증 환경 (Android Studio, adb) | ✅ 메모리 링크·M0 빌드 완료 2026-09-18. `local.properties` 작성됨(gitignore) |

## 다음 할 일

1. **사람이 직접 연주해 보기 (SDS PC + 폰):** `./gradlew.bat :app:installDebug` 후 `docs/verification-checklist.md` M1 #10~19를 채운다. 특히 #11 체감 지연, #12 멀티터치, #15 고음역 서스테인.
2. **아키랩 pull 후:** 프리앰블 붙여 `./gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. JDK 17 빌드와 `platforms;android-37` 자동 다운로드는 아직 아키랩에서 확인된 적 없다. 실패 시 `sdkmanager "platforms;android-37"`.
3. **M2 계획 작성** (스펙 9장 M2): ToneParams 커맨드(SetBrightness/SetDecay), 슬라이드 에너지 딥, DataStore 설정, 설정 화면·디버그 정보, 버퍼 배수. 1번의 피드백(지연·판정 범위·서스테인)을 M2 범위에 반영한다. `:core` 쪽은 피드백을 기다리지 않고 시작할 수 있다.

## 이력

- 2026-09-16 (아키랩): 저장소 생성, 워크플로우 문서, 툴체인 설치 기록, v1 스펙 승인, M0 계획 작성.
- 2026-09-18 (SDS PC): 환경 최초 세팅(메모리 링크), 툴체인 기록, 이 파일 생성.
- 2026-09-18 (SDS PC): **M0 구현** — Gradle 9.7.0 wrapper, 버전 카탈로그, `:core`(JUnit 6), `:app`(빈 Compose 화면). compileSdk 36→37. 커밋 4개(d962f12, 3c3251b, f38fa14, 9c6cac3). 실기기 미확인.
- 2026-09-21 (SDS PC): 테스트 폰 Galaxy S10e(Android 12) 연결 → minSdk 33→31. minSdk 31로 `:core:test`·`assembleDebug`·`lintDebug` 통과. adb 승인이 재연결 때 풀려(`unauthorized`) `installDebug`는 미완.
- 2026-09-21 (SDS PC): adb 승인 후 **M0 실기기 검증 완료** — 설치·실행·가로 고정·텍스트 표시 확인. M0는 아키랩 JDK 17 빌드 확인만 남음.
- 2026-09-21 (SDS PC): **M1 구현** — core/music·synth·engine(TDD), app/audio(AudioTrack 저지연), app/ui/fretboard. 커밋 c0e8210~061015e. 기계적으로 확인 가능한 항목은 실기기에서 확인, 귀·손이 필요한 항목은 미확인.

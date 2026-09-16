# get-your-guitar

베이스 기타 지판(fretboard) 시뮬레이터 Android 앱. 참고 앱: "Bass Guitar Solo" (`reference.webp`).
기술 스택은 `gradle/libs.versions.toml`(버전)과 `docs/superpowers/specs/2026-09-16-get-your-guitar-v1-design.md` 10장(결정 이유) 참고.

## 개발 환경 세팅 (모든 환경 공통, 최초 1회)

1. `git clone` 후 저장소 루트에서 Claude 메모리 링크:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts/link-claude-memory.ps1`
   - Linux/macOS: `bash scripts/link-claude-memory.sh`
2. **JDK 17 또는 21** (Android Studio 번들 JDK 21 가능; Kotlin 2.4.20·AGP 9.4.0은 그보다 새 JDK를 데몬 JVM으로 지원하지 않음), Android SDK는 각 환경에 별도 설치 (저장소에 포함하지 않음). 아래 환경별 기록 참고.
   - SDK: cmdline-tools 설치 → `sdkmanager --licenses` 로 라이선스 수락 → `sdkmanager "platforms;android-37.0" "build-tools;36.0.0"` (라이선스만 수락돼 있으면 첫 빌드 때 AGP가 자동 설치하기도 함).
   - Android Studio를 쓰면 AGP 9.4.0을 지원하는 버전이어야 한다 — 호환표: https://developer.android.com/build/releases/gradle-plugin#updating-plugin
3. Gradle은 설치하지 않는다 — 프로젝트의 `gradlew` wrapper가 `GRADLE_USER_HOME` 아래로 자동 다운로드.

워크플로우 규칙은 `CLAUDE.md` 참고.

## 빌드·테스트

```bash
./gradlew :core:test                      # 순수 Kotlin 로직 단위테스트 (모든 환경)
./gradlew :app:assembleDebug :app:lintDebug   # 컴파일·린트 검증
./gradlew :app:installDebug               # 실기기 설치 (검증 환경, USB 디버깅)
```
Windows Git Bash에서는 `./gradlew.bat`. `JAVA_HOME`(JDK 17 또는 21)과 `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`이 필요하다.

### 검증 환경에서 M0 확인 절차
0. 위 세팅(JDK, SDK 라이선스·`platforms;android-37.0`) 완료, 폰 USB 디버깅 연결
1. `git pull` → `./gradlew :app:installDebug` → 폰에서 "get-your-guitar" 실행
2. **가장 먼저 보고할 것:** 가로로 뜨는가 (폰에서는 OS가 `screenOrientation` 고정을 존중해야 함; 600dp 이상 화면에선 targetSdk ≥ 36부터 무시됨), 화면 가운데 "get-your-guitar" 텍스트가 보이는가
3. 결과를 README "환경별 의존성"에 그 환경 섹션(JDK·SDK 버전·경로)과 함께 기록해 push

## 환경별 의존성

### 아키랩 (Windows Server 2019, 기기 연결 불가) — 2026-09-16 설치

모두 `E:\yjane.kim\tools` 아래, **User scope** 환경변수만 사용 (관리자 권한·시스템 변경 없음).
C: 드라이브는 여유가 ~1 GB뿐이므로 어떤 도구·캐시도 C:에 두지 않는다.

| 구성요소 | 버전 | 경로 |
|---|---|---|
| JDK | Eclipse Temurin 17.0.20.1+1 | `E:\yjane.kim\tools\jdk-17.0.20.1+1` |
| Android cmdline-tools | 22.0 | `E:\yjane.kim\tools\android-sdk\cmdline-tools\latest` |
| Android SDK Platform (compileSdk 37) | `platforms;android-37.0` (rev 2, 첫 빌드 때 AGP가 자동 설치) | `E:\yjane.kim\tools\android-sdk\platforms\android-37.0` |
| Android SDK Platform | `platforms;android-36` (rev 2) — 현재 미사용 | `E:\yjane.kim\tools\android-sdk\platforms\android-36` |
| Android Build-Tools | `build-tools;36.0.0` | `E:\yjane.kim\tools\android-sdk\build-tools\36.0.0` |
| Gradle | 9.7.0 — 프로젝트 wrapper가 관리 | `E:\yjane.kim\tools\gradle-user-home\wrapper\dists\gradle-9.7.0-bin` |

환경변수 (User scope):

```
JAVA_HOME        = E:\yjane.kim\tools\jdk-17.0.20.1+1
ANDROID_HOME     = E:\yjane.kim\tools\android-sdk
GRADLE_USER_HOME = E:\yjane.kim\tools\gradle-user-home
PATH            += E:\yjane.kim\tools\jdk-17.0.20.1+1\bin;E:\yjane.kim\tools\android-sdk\cmdline-tools\latest\bin
```

설치하지 않은 것: `platform-tools`(adb), 에뮬레이터/시스템 이미지, Android Studio — 아키랩에는 기기가 없으므로 불필요.

주의:
- 시스템 PATH에 Oracle JRE 1.8이 앞서 있어 새 터미널에서 `java -version`은 1.8이 나온다. `gradlew`·`sdkmanager`는 `JAVA_HOME`을 우선 쓰므로 빌드에는 문제 없음.
- 환경변수는 설치 이후 새로 연 터미널에만 반영된다.
- cmdline-tools 22.0부터 `sdkmanager`는 deprecated 경고를 내고 `android sdk` CLI를 권장하지만 아직 동작한다.

재현 절차 (다른 Windows 환경에 같은 구성을 만들 때):

```powershell
# 1. JDK 17 (Temurin) zip → E:\yjane.kim\tools\ 에 풀기
#    https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk
# 2. cmdline-tools zip → E:\yjane.kim\tools\android-sdk\cmdline-tools\latest 로 풀기 (zip 안의 cmdline-tools 폴더를 latest 로 개명)
#    https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip
# 3. 환경변수 설정 후 새 터미널에서:
sdkmanager --licenses
sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
```

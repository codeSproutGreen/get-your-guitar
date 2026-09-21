# get-your-guitar

베이스 기타 지판(fretboard) 시뮬레이터 Android 앱. 참고 앱: "Bass Guitar Solo" (`reference.webp`).
기술 스택·아키텍처는 확정 후 갱신.

## 개발 환경 세팅 (모든 환경 공통, 최초 1회)

1. `git clone` 후 저장소 루트에서 Claude 메모리 링크:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts/link-claude-memory.ps1`
   - Linux/macOS: `bash scripts/link-claude-memory.sh`
2. JDK 17+, Android SDK는 각 환경에 별도 설치 (저장소에 포함하지 않음). 아래 환경별 기록 참고.
3. Gradle은 설치하지 않는다 — 프로젝트의 `gradlew` wrapper가 `GRADLE_USER_HOME` 아래로 자동 다운로드.

워크플로우 규칙은 `CLAUDE.md` 참고.

## 빌드·테스트

```bash
./gradlew :core:test                          # 순수 Kotlin 로직 단위테스트 (모든 환경)
./gradlew :app:assembleDebug :app:lintDebug   # 컴파일·린트 검증
./gradlew :app:installDebug                   # 실기기 설치 (검증 환경, USB 디버깅)
```
Windows Git Bash에서는 `./gradlew.bat`. `JAVA_HOME`(JDK 17+)과 `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`이 필요하다.
compileSdk 37 / targetSdk 36 / minSdk 31 (테스트 폰 Galaxy S10e가 Android 12라서 31). 필요한 SDK 플랫폼(`platforms;android-37`)은 라이선스가 수락돼 있으면 AGP가 자동 다운로드한다.

### 검증 환경에서 M0 확인 절차
1. `git pull` → `./gradlew :app:installDebug` → 폰에서 "get-your-guitar" 실행
2. 확인: 가로로 뜨고 화면 가운데 "get-your-guitar" 텍스트가 보인다
3. 결과를 `docs/STATUS.md`와 README "환경별 의존성"의 그 환경 섹션에 기록해 push

## 환경별 의존성

### 아키랩 (Windows Server 2019, 기기 연결 불가) — 2026-09-16 설치

모두 `E:\yjane.kim\tools` 아래, **User scope** 환경변수만 사용 (관리자 권한·시스템 변경 없음).
C: 드라이브는 여유가 ~1 GB뿐이므로 어떤 도구·캐시도 C:에 두지 않는다.

| 구성요소 | 버전 | 경로 |
|---|---|---|
| JDK | Eclipse Temurin 17.0.20.1+1 | `E:\yjane.kim\tools\jdk-17.0.20.1+1` |
| Android cmdline-tools | 22.0 | `E:\yjane.kim\tools\android-sdk\cmdline-tools\latest` |
| Android SDK Platform | `platforms;android-36` (rev 2) | `E:\yjane.kim\tools\android-sdk\platforms\android-36` |
| Android Build-Tools | `build-tools;36.0.0` | `E:\yjane.kim\tools\android-sdk\build-tools\36.0.0` |
| Gradle 배포판·의존성 캐시 | wrapper가 관리 | `E:\yjane.kim\tools\gradle-user-home` |

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
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

### SDS PC (Windows 11 Pro, Android Studio 설치, adb 있음) — 2026-09-18 확인

실기기를 연결할 수 있는 환경이므로 **검증 환경** 역할. 저장소 위치 `C:\Users\SDS\project\get-your-guitar`. C: 여유 ~270 GB.
Android Studio가 설치돼 있어 별도 설치 없이 그 번들을 그대로 쓴다.

| 구성요소 | 버전 | 경로 |
|---|---|---|
| JDK | Android Studio 번들 JBR (OpenJDK 21.0.9) | `C:\Program Files\Android\Android Studio\jbr` (`JAVA_HOME`으로 설정됨) |
| Android Studio | 2025.3.1 (AI-253.29346.138) | `C:\Program Files\Android\Android Studio` |
| Android SDK | platforms `android-34`, `android-35`, `android-36.1` | `C:\Users\SDS\AppData\Local\Android\Sdk` |
| Build-Tools | 34.0.0, 35.0.0, 36.1.0 | 위 SDK 아래 |
| platform-tools (adb) | 설치됨, PATH에 있음 | 위 SDK 아래 |
| Gradle 캐시 | wrapper가 관리 (`GRADLE_USER_HOME` 미설정 → 기본 `~/.gradle`) | `C:\Users\SDS\.gradle` |

주의:
- `ANDROID_HOME` 환경변수는 없다. Android Studio가 만든 `local.properties`(`sdk.dir=...`)가 있으면 Gradle이 그걸 쓰고, 없으면 `sdk.dir`을 직접 적는다 (gitignore 됨).
- `cmdline-tools`는 없다 (SDK 관리는 Android Studio SDK Manager로). M0 첫 빌드(2026-09-18) 때 AGP가 `platforms;android-36`·`android-37.0`을 자동 다운로드했다 — 별도 설치 불필요.
- JDK가 17이 아닌 21이다. M0(`:core:test`, `:app:assembleDebug`, `lintDebug`)는 JDK 21로 통과. 아키랩(17)과 결과가 다르면 `java.toolchain`을 17로 고정하는 것을 검토한다.
- `java`는 PATH에 없다. `gradlew.bat`은 `JAVA_HOME`을 쓰므로 빌드에는 문제 없음.
- `gh` CLI 설치·로그인(`codeSproutGreen`) 됨. Claude 메모리 junction은 2026-09-18에 링크 완료.
- 테스트 폰: **Galaxy S10e (SM-G970N), Android 12 / API 31**, 해상도 1080x2280. 이 기기의 마지막 OS라 올릴 수 없어 앱 minSdk를 31로 맞췄다. 2019년 기기이므로 M1의 오디오 지연 평가는 최신 폰보다 불리한 하한선으로 본다.
- M0 실기기 확인 완료 (2026-09-21): `installDebug` 성공, 가로 고정, 화면 정중앙 텍스트, 크래시 없음.
- USB 디버깅 승인 팝업에서 "이 컴퓨터에서 항상 허용"을 체크하지 않으면 재연결 때마다 `unauthorized`로 돌아간다.
- Git Bash에서 `adb shell ... /sdcard/...` 를 쓰면 경로가 `C:/Program Files/Git/sdcard/...`로 바뀐다. 앞에 `export MSYS_NO_PATHCONV=1`을 붙인다.

# 쪽방 (Jjokbang) v0.1

갤럭시(안드로이드)용 **충동 연락 방지 타이머**의 실행 가능한 Android Studio 소스 프로젝트입니다. Kotlin/Compose 의존성 없이 Java와 안드로이드 표준 API로 작성했으며 **서버, GitHub 저장소 동기화, Supabase, 회원가입, 인터넷 권한이 필요 없습니다.** Android 10(API 29) 이상에서 작동하도록 개발했습니다.

> **개발/검증 상태:** 전체 Android Studio 프로젝트 **소스 코드**입니다. 현재 이 환경에는 Android SDK 및 Gradle 실행 환경이 없어 실제 APK 빌드와 갤럭시 기기 테스트까지 완료했다고 주장하지 않습니다. 아래 GitHub Actions 또는 Android Studio에서 APK를 생성해 테스트해야 합니다.

## 반영된 요구사항

| 기능 | 방식 |
| --- | --- |
| 수동 잠금 | 1분~168시간(7일) 숫자로 입력 → `잠금 시작` |
| 최근 기록 | 사용한 시간 5개를 중복 없이 최신순 보존, 터치하면 다시 즉시 시작 |
| 1회 예약 | 시작 날짜/시각과 종료 날짜/시각 직접 지정, 자정 넘어가는 일정 허용 |
| 예약 개수 | ISO 주간(월요일~일요일) **예약 시작일 기준 최대 3개**. 수동 잠금은 횟수 제한 없음 |
| 잠금 취소 | 예약은 시작 **전**에만 취소 가능. 진행 중에는 앱 내 취소·시간 단축 버튼 없음 |
| 자동 해제 | 만료 시각을 저장하고 현재 시각과 비교, 재부팅해도 데이터 유지 |
| 일반 전화 발신 | 안드로이드 `CallRedirectionService` 역할에 동의하면 일반 셀룰러 발신을 취소. **긴급전화는 이 기능 대상 아님** |
| 문자·카카오톡·인스타그램 | 사용자가 허용한 접근성 서비스가 이들 앱이 열린 것을 감지하여 홈으로 돌아감 |
| 오프라인 보관 | 잠금 시간, 최근 기록, 예약을 `SharedPreferences`에만 저장 |

## APK 만들기 ①: GitHub Actions (PC에 Android Studio 없어도 됨)

1. GitHub에서 새 저장소를 만듭니다. (Supabase 프로젝트와 관계없습니다.)
2. **이 ZIP을 압축 해제한 안쪽 파일들** (`app`, `.github`, `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/` 등)을 저장소 **최상위**에 업로드합니다. `.github` 숨김 폴더도 꼭 포함합니다.
3. GitHub → `Actions` → `Build Jjokbang APK` → `Run workflow` (또는 `main` 브랜치로 push).
4. 작업 성공 후 해당 실행 페이지의 `Artifacts` → `Jjokbang-debug-apk`를 내려받아 압축을 풀면 `app-debug.apk`가 있습니다.
5. APK를 본인 갤럭시로 옮겨 설치합니다. 출처를 알 수 없는 앱 설치 허용 안내가 나올 수 있습니다.

**주의:** 이 단계는 코드 업로드와 외부 빌드가 실제로 성공해야 APK가 생성됩니다. 현재 이 프로젝트의 성공 빌드 여부는 검증하지 못했습니다. GitHub Actions 무료 이용 한도/정책은 계정 및 저장소 유형에 따라 달라질 수 있습니다.

## APK 만들기 ②: Android Studio

1. Android Studio 설치 후 `Open`으로 압축 해제한 `Jjokbang` 폴더를 엽니다.
2. SDK Manager에서 Android API 36을 설치하고 Gradle 8.13이 선택되었는지 확인합니다.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`로 디버그 APK를 빌드합니다.
4. 결과: `app/build/outputs/apk/debug/app-debug.apk`.

이 소스 압축에는 **Gradle Wrapper JAR**가 포함되어 있지 않습니다. GitHub Actions는 별도 Gradle 설치 단계가 있으므로 이 파일이 필요 없으며, Android Studio에서도 Gradle 경로 설정이 필요한 경우 설치한 Gradle 8.13을 선택해 빌드하면 됩니다.

## 갤럭시에서 최초 설정

1. 앱을 실행합니다. **접근성 권한** 버튼 → 갤럭시 설정에서 `쪽방 앱 차단 서비스`를 켭니다. (사이드로드 앱은 `설정 > 앱 > 쪽방 > ⋮ > 제한된 설정 허용` 절차가 추가로 필요할 수 있습니다.)
2. **전화 발신 제한 권한** 버튼 → 시스템에서 `통화 리디렉션` 역할을 허용합니다. 기기에서 해당 역할을 제공하지 않으면 일반 통화를 제한할 수 없으며 앱은 잠금 시작을 거부합니다.
3. 예약을 사용한다면 **정확한 알람** 권한을 허용합니다. 알람 및 리마인더 접근 설정을 열어 허용합니다.
4. 먼저 **1분 수동 잠금**으로 문자·카카오톡·인스타그램 접근과 일반 발신 제한 동작을 직접 테스트하세요. **긴급전화는 시험 삼아 걸지 마세요.**
5. 1분 이후 정상적으로 앱 접근이 다시 가능해지는지 확인하고, 이후 실제 사용하는 시간으로 늘립니다.

## 반드시 알아둘 제한

- **100% 해제 불가능한 잠금은 아닙니다.** 휴대폰 사용자는 시스템 설정에서 접근성 서비스나 통화 리디렉션 권한을 해제하고, 앱을 강제 종료·삭제하거나 기기 시각을 바꿀 수 있습니다. 이 앱은 강제 기기 관리(Device Owner) 또는 루팅을 사용하지 않습니다.
- 접근성 서비스는 앱이 열리는 순간을 감지해 홈으로 돌려보내므로 **아주 잠깐 화면이 보일 수 있습니다**. 갤럭시 버전·기본 문자 앱 종류·절전 최적화 상태에 따라 차이가 있습니다. 이 프로젝트에는 **삼성 메시지, 구글 메시지, AOSP 메시지, 카카오톡, 인스타그램** 패키지 목록이 들어 있습니다. 다른 문자 앱을 쓰면 `GuardAccessibilityService.java` 패키지 목록을 추가해야 합니다.
- **전화 수신은 차단하지 않습니다.** 휴대전화 다이얼러는 접근성을 통해 차단하지 않으므로 긴급전화 UI를 사용할 수 있습니다. 전화 발신 제한은 **일반 셀룰러 전화**에만 적용되며, 일부 VoIP/서비스 번호/다른 앱의 발신은 우회될 수 있습니다.
- 브라우저에서 인스타그램 메시지를 보내거나 웹 카카오톡을 사용하는 등 **다른 경로로 연락하는 것은 차단되지 않을 수 있습니다**.
- 정확한 알람을 허용하더라도 Android 절전 정책, 권한 재설정, 접근성 서비스 비활성화에 따라 시작 시각이 지연되거나 앱 차단이 작동하지 않을 수 있습니다. 현재 구현은 예약 시간이 되면 잠금 상태를 계산하며 접근성 서비스가 1.5초 간격으로 현재 앱을 확인합니다.
- 저장한 날짜·시간은 **휴대폰 현지 시간대** 기준입니다. 날짜/시계 변경은 잠금 동작에 영향을 줄 수 있습니다.
- 잠금 시작 전이나 예약 전에는 권한 상태를 검사합니다. 하지만 잠금이 진행 중일 때 사용자가 권한을 철회하면 막을 방법이 없습니다.
- 기기의 실제 상태에서 **112/119 등 긴급전화가 가능한지 제조사 사양을 확인하세요.** Android Telecom은 긴급발신을 `CallRedirectionService`로 넘기지 않는 것으로 문서화되어 있지만, 이 코드는 갤럭시 실기기 테스트 전입니다.
- Google Play 배포 시 접근성 API 사용은 정책 제한이 있으므로 **개인용 사이드로드 APK**를 전제로 설계했습니다.

## 주요 구현 파일

- `app/src/main/java/com/example/contactpause/MainActivity.java`: UI, 직접 잠금, 최근 사용 버튼, 날짜/시간 예약, 권한 설정
- `LockStore.java`: 로컬 데이터 관리, 현재 잠금 여부, 최근 5개, 주 3개 검사
- `GuardAccessibilityService.java`: 문자/카톡/인스타 앱 접근 차단
- `OutgoingCallGuardService.java`: 일반 전화 발신 취소
- `AlarmPlanner.java` / `AlarmReceiver.java`: 예약 시각 및 재부팅 후 알람 재설정
- `.github/workflows/build-apk.yml`: GitHub에서 APK 빌드

## 공식 API 문서

- https://developer.android.com/reference/android/telecom/CallRedirectionService
- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/guide/topics/ui/accessibility/service
- https://developer.android.com/develop/background-work/services/alarms


## 쪽방 앱
- 표시 이름: **쪽방** (쪽팔림 방지)
- 추천 GitHub 저장소 이름: `jjokbang`
- 확정된 캐릭터 이미지를 런처 아이콘으로 적용했습니다.
- 기존 코드 호환을 위해 내부 applicationId는 `com.example.contactpause`로 유지합니다.
- APK 빌드 및 실제 차단 동작은 아직 검증하지 않았습니다.

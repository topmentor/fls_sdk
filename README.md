# FLS SDK

`com.sox.flssdk` — LTE / Wi-Fi / BLE 복합 신호 기반 측위 Android 애플리케이션.

서버 측위를 통해 위치를 받아 표시합니다.
(주)에스오엑스의 측위 서비스(LocationDBService)와 연동됩니다.

---

## 1. 개요

| 항목 | 값 |
|------|----|
| Application ID | `com.sox.flssdk` |
| App Name | FLS SDK |
| versionName | `2.0` (versionCode 1) |
| compileSdk | 36 |
| targetSdk | 32 |
| minSdk | 31 (Android 12) |
| Java | 11 (소스 호환), 빌드 JDK 17 |
| AGP / Gradle | 8.13.2 / 8.13 |
| 언어 | Java |
| MultiDex | 활성화 |

---

## 2. 주요 기능

### 서버 복합측위

측위 서버에 요청을 보내고, 서버가 계산한 위도·경도를 수신합니다.

- 관련 클래스: `PositionScannerManager`, `ServerFLPManager`, `ServerManager`
- 서버 API: `POST /LocationDBService/getPosition.do`

---

## 3. 아키텍처

```
[ Activity ]    IntroActivity (권한 요청) → MainActivity
                                                │
[ Fragment ]                          PositionFragment
                                      PositionSettingFragment
                                                │
[ Manager ]         PositionScannerManager   측위 실행 진입점
                    ServerFLPManager          서버 측위 결과 처리
                    PreferenceManager         설정 저장/로드 (SharedPreferences)
                    FileManager               로그 파일 I/O
                    ServerManager             HTTP 통신 (Volley)
                                                │
[ Scanner ]     단말 신호 수집 (scanner/)
                                                │
[ Utils ]       날짜·시간, 좌표 간 거리 계산 (utils/)
```

### 패키지 구조

```
com.sox.flssdk
├── activity/          IntroActivity, MainActivity
├── fragment/position/ PositionFragment, PositionSettingFragment
├── manager/           *Manager 클래스
├── scanner/           신호 수집
├── model/             데이터 모델
├── utils/             DateTimeUtils
└── utils/geo/         CoordTransformUtil, GPoint (거리 계산)
```

---

## 4. 데이터 흐름

1. **IntroActivity** — 위치·블루투스·저장소 권한 요청 및 GPS 활성화 확인 후 MainActivity 진입.
2. **MainActivity** — `PreferenceManager.readPositionSetting()`으로 SharedPreferences에서 서버 주소 로드, `PositionFragment` 바로 표시.
3. 사용자가 **복합측위 시험** 버튼을 누르면 `PositionScannerManager.startScan()` 호출.
4. Volley HTTP POST → 서버 응답 JSON 수신 → `PositionFragment.ScanReceiveHandler`로 전달.
5. Fragment Handler가 수신한 위도·경도를 화면에 표시합니다.

---

## 5. 외부 연동

| 서비스 | 용도 | 기본 엔드포인트 |
|--------|------|----------------|
| LocationDBService (SOX 서버) | 서버 복합측위 | `POST /LocationDBService/getPosition.do` |
| Google Play Services Location | Fused Location Provider (GPS 보조) | — |

서버 **주소와 포트**, **API 키**는 앱 내 **설정 화면** 또는 `PreferenceManager`로 지정합니다. API 키를 입력하면 측위 요청에 `X-API-KEY` 헤더로 실리고, 비워 두면 헤더를 넣지 않습니다.
API 경로(`/LocationDBService/getPosition.do`)는 `CommonDoc.Position.POSITIONING_API_URI` 상수로 고정되어 있으며, 설정한 `주소:포트` 뒤에 붙습니다.
입력칸을 비운 채 저장하면 hint 값(`http://example.com`, `8080`)이 그대로 저장되므로 실제 서버 주소를 입력해야 합니다.

---

## 6. 주요 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `androidx.appcompat` | 1.7.1 | AndroidX 기반 UI |
| `com.google.android.material` | 1.13.0 | Material 컴포넌트 |
| `androidx.constraintlayout` | 2.2.1 | 레이아웃 |
| `androidx.multidex` | 2.0.1 | MultiDex |
| `com.google.android.gms:play-services-location` | 21.3.0 | Fused Location |
| `com.android.volley` | 1.2.1 | HTTP 통신 |
| `org.locationtech.proj4j` | 1.4.1 | 좌표 간 거리 계산 |

---

## 7. 권한

| 권한 | 용도 |
|------|------|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | GPS 측위, LTE 셀 정보, Wi-Fi 스캔 (targetSdk 32 기준) |
| `READ_PHONE_STATE`, `ACCESS_NETWORK_STATE` | LTE 셀 정보 수집 |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Wi-Fi 스캔 |
| `NEARBY_WIFI_DEVICES` (neverForLocation) | Android 13+ 이면서 targetSdk 33+ 일 때만 Wi-Fi 스캔에 사용 (현재 targetSdk 32 이므로 미사용) |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` | BLE |
| `BLUETOOTH_SCAN` (neverForLocation) | Android 12+ BLE 스캔 |
| `BLUETOOTH_PRIVILEGED` | BLE 확장 권한 |
| `INTERNET` | 서버 통신 |
| `FOREGROUND_SERVICE` | 백그라운드 실행 |
| `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` | 로그 파일 저장 |

---

## 8. 파일 저장 경로

모든 파일은 `Downloads/FLSSDK/` 하위에 저장됩니다 (`MANAGE_EXTERNAL_STORAGE` 권한 필요).

| 경로 | 내용 |
|------|------|
| `Downloads/FLSSDK/Log/` | 측위 로그 텍스트 파일 (`server_*.txt`) |
| `Downloads/FLSSDK/exception/` | 비정상 종료 스택 트레이스 |

앱은 단말에 로컬 DB를 두지 않습니다. 측위 결과는 서버 응답으로만 받습니다.

---

## 9. 템플릿으로 신규 프로젝트 생성

이 저장소를 템플릿으로 새 Android 프로젝트를 만들려면 루트의 `new-project.ps1` 스크립트를 사용합니다. 패키지명·프로젝트명·표시명·저장 디렉토리명을 일괄 치환하고 소스 패키지 디렉토리 구조까지 재편성해, 복사 직후 바로 빌드 가능한 독립 프로젝트를 생성합니다.

```powershell
# 대화형 (프로젝트명·패키지·대상 경로 등을 차례로 입력)
.\new-project.ps1

# 비대화형 (CI/스크립트) — 누락 값은 기본값 사용
.\new-project.ps1 -Dest C:\work\MyApp -ProjectName MyApp -Package com.example.myapp -Yes
```

> Windows PowerShell 5+ / PowerShell 7+ 에서 동작하며 외부 의존성이 없습니다. 패키지를 지정하지 않으면 기본값 `com.example.myapp` 이 적용됩니다.

자세한 입력 항목과 치환 내역은 **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** 의 「2. 신규 프로젝트 생성」을 참고하세요.

---

## 10. 빌드 및 실행

### 사전 요구 사항

- AGP 8.13 을 지원하는 Android Studio (또는 명령줄 빌드)
- Android SDK Platform 36, Build-Tools 35.0.0, Platform-Tools
- JDK 17 (AGP 8.13 요구 사항. 소스/타깃 호환은 Java 11)

> **Gradle Wrapper 주의**: `.gitignore` 의 `*.jar` 규칙 때문에 `gradle/wrapper/gradle-wrapper.jar` 가 저장소에 없습니다.
> clone 직후에는 `gradlew` 가 동작하지 않으므로, Android Studio 로 열거나 Gradle 8.13 을 설치해 `gradle wrapper --gradle-version 8.13` 으로 wrapper 를 생성하거나 `gradle assembleDebug` 로 직접 빌드합니다.

### 로컬 설정

```properties
# local.properties
sdk.dir=C:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
```

### 빌드 명령

```bash
# Debug APK 빌드
./gradlew assembleDebug

# Release APK 빌드 (app/build.gradle 에 서명 설정이 없어 서명되지 않은 APK 가 생성됨)
./gradlew assembleRelease

# 단위 테스트
./gradlew testDebugUnitTest

# 연결된 단말에 Debug 설치
./gradlew installDebug

# 빌드 클린
./gradlew clean
```

빌드된 APK 경로: `app/build/outputs/apk/debug/app-debug.apk`

---

## 11. 디렉터리 구조

```
FLS_SDK/
├── app/
│   ├── src/main/
│   │   ├── java/com/sox/flssdk/
│   │   │   ├── activity/
│   │   │   ├── fragment/position/
│   │   │   ├── manager/
│   │   │   ├── scanner/
│   │   │   ├── model/
│   │   │   └── utils/geo/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
├── gradle.properties
├── new-project.ps1         ← 템플릿 복제 스크립트 (신규 프로젝트 생성)
├── DEVELOPER_GUIDE.md      ← 개발자 연동 매뉴얼
├── LICENSE                 ← GPL-3.0
└── README.md
```

---

## 12. 개발자 연동 가이드

신호 센싱 및 측위 호출 방법은 **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** 를 참고하세요.

- LTE / Wi-Fi / BLE 신호 센싱 방법
- 서버 복합측위 호출 코드 예시
- 요청 / 응답 JSON 구조
- 서버 설정 변경 방법

---

## 13. 라이선스

Copyright (C) 2026 S.O.X Ltd. Co.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3.
It is distributed WITHOUT ANY WARRANTY. See [LICENSE](LICENSE) for the full text.

For commercial licensing outside the terms of the GPL, contact soxcom@naver.com.

- 이 저장소는 **GPL-3.0** 으로 배포됩니다. 수정·재배포한 결과물도 GPL-3.0 으로 소스를 공개해야 합니다.
- 소스 공개 없이 상업 제품에 포함하려면 SOX와 별도 상용 라이선스 계약이 필요합니다 (soxcom@naver.com).
- 의존 라이브러리는 각자의 라이선스를 따릅니다.

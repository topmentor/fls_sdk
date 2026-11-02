# FLS SDK 개발자 매뉴얼

## 목차

1. [개요](#1-개요)
2. [신규 프로젝트 생성](#2-신규-프로젝트-생성)
3. [프로젝트 구조](#3-프로젝트-구조)
4. [필수 권한 설정](#4-필수-권한-설정)
5. [서버 측위 호출](#5-서버-측위-호출)
6. [측위 결과 수신](#6-측위-결과-수신)
7. [서버 설정](#7-서버-설정)
8. [응답 JSON 구조](#8-응답-json-구조)

---

## 1. 개요

FLS SDK는 단말의 신호 정보를 측위 서버로 보내고, 서버가 계산한 위도·경도를 받는 Android 라이브러리입니다.

- **패키지명**: `com.sox.flssdk`
- **최소 SDK**: API 31 (Android 12)
- **빌드 환경**: Java 11 소스 호환, JDK 17 (AGP 8.13.2 / Gradle 8.13), compileSdk 36, targetSdk 32

---

## 2. 신규 프로젝트 생성

이 저장소를 **템플릿**으로 삼아 새 Android 프로젝트를 생성하려면 루트의 `new-project.ps1` 스크립트를 사용합니다. 패키지명·프로젝트명·표시명 등을 일괄 치환하고 소스 패키지 디렉토리 구조까지 재편성하므로, 복사 직후 곧바로 빌드할 수 있는 독립 프로젝트가 만들어집니다.

> Windows PowerShell 5+ 또는 PowerShell 7+ 에서 동작하며 외부 의존성이 없습니다.

### 대화형 실행

```powershell
.\new-project.ps1
```

실행하면 아래 값을 차례로 묻습니다. 대괄호 `[ ]` 안은 그냥 Enter 시 적용되는 기본값입니다.

| 입력 항목 | 설명 | 기본값 |
|----------|------|--------|
| 신규 프로젝트명 | `rootProject.name`, `*.iml` 파일명에 사용 (PascalCase, 공백 불가) | (필수) |
| 신규 앱 표시명 | `strings.xml` 의 `app_name` | 프로젝트명 |
| 신규 패키지 | 메인 소스 패키지 / `namespace` | `com.example.myapp` |
| 신규 저장 디렉토리명 | 단말 `Downloads/` 하위 로그·모델 저장 폴더명 | 프로젝트명(대문자) |
| applicationId | APK 패키지 식별자 | 신규 패키지와 동일 |
| 대상 디렉토리 | 새 프로젝트가 생성될 경로 (원본 밖, 비어 있어야 함) | (필수) |

### 비대화형(CI/스크립트) 실행

`-Yes` 를 주면 확인 프롬프트 없이 진행하며, 누락된 값은 위 표의 기본값으로 채웁니다.

```powershell
.\new-project.ps1 -Dest C:\work\MyApp -ProjectName MyApp -Package com.example.myapp -Yes
```

### 스크립트가 수행하는 작업

1. **소스 복사** — `.git`, 빌드 산출물(`build`, `.gradle`), 런타임 디렉토리(`release`, `odd`) 등은 제외하고 복사합니다.
2. **내용 치환** — 패키지(`com.sox.flssdk`), 프로젝트명(`SOXCollector`), 표시명(`FLS SDK`), 저장 디렉토리명(`FLSSDK`)을 신규 값으로 일괄 치환합니다.
3. **패키지 디렉토리 재편성** — `com/sox/flssdk` 디렉토리 구조를 신규 패키지 경로로 이동합니다.
4. **파일명 변경** — `SOXCollector.app.iml` 처럼 프로젝트명이 포함된 파일명을 변경합니다.

### 생성 후 할 일

```powershell
cd C:\work\MyApp
git init                  # 새 git 저장소로 초기화 (선택)
.\gradlew assembleDebug   # 빌드 확인
```

- `local.properties` 의 `sdk.dir` 경로가 현재 머신에 맞는지 확인합니다.
- `gradle/wrapper/gradle-wrapper.jar` 는 저장소에 없습니다(`.gitignore` 의 `*.jar`). `gradlew` 를 쓰려면 먼저 `gradle wrapper --gradle-version 8.13` 으로 생성합니다.
- 측위 서버 주소·포트는 코드에 박혀 있지 않으므로, 앱 설정 화면(`PositionSettingFragment`) 또는 `PreferenceManager` 로 지정합니다. API 경로만 `CommonDoc.Position.POSITIONING_API_URI` 상수로 고정되어 있습니다. ([7. 서버 설정](#7-서버-설정) 참고)

---

## 3. 프로젝트 구조

```
com.sox.flssdk
├── activity/
│   ├── IntroActivity.java       # 권한 요청, 앱 진입점
│   └── MainActivity.java        # Fragment 컨테이너
├── fragment/position/
│   ├── PositionFragment.java    # 측위 UI, 결과 수신 Handler
│   └── PositionSettingFragment.java  # 서버 주소·포트 설정 화면
├── manager/
│   ├── PositionScannerManager.java  # 측위 실행 진입점
│   ├── ServerFLPManager.java        # 서버 측위 결과 처리
│   └── PreferenceManager.java       # 서버 설정 저장/로드 (SharedPreferences)
├── scanner/                     # 단말 신호 수집
└── CommonDoc.java               # 전역 상수 및 로그 유틸
```

---

## 4. 필수 권한 설정

`AndroidManifest.xml`에 아래 권한이 선언되어 있어야 합니다.

```xml
<!-- 인터넷 (서버 측위 요청) -->
<uses-permission android:name="android.permission.INTERNET"/>

<!-- GPS -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>

<!-- LTE 셀 정보 -->
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

<!-- Wi-Fi 스캔: targetSdk 32 에서는 ACCESS_FINE_LOCATION 으로 판정.
     NEARBY_WIFI_DEVICES 는 Android 13+ 이면서 targetSdk 33+ 일 때만 사용 -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation"/>

<!-- BLE 스캔 (Android 12+: 위치 기반 아님) -->
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
```

런타임 권한은 `IntroActivity`에서 요청합니다. 권한이 거부되어도 앱은 중단되지 않습니다.

---

## 5. 서버 측위 호출

`PositionScannerManager`가 신호 수집과 측위 요청을 일괄 처리합니다.

```java
// 1. 측위 타입 설정
CommonDoc.Position.position_type = "server";

// 2. PositionScannerManager 생성 (서버 전체 URL 전달)
//    CommonDoc.Position.positioningServer : PreferenceManager.readPositionSetting() 이
//    "주소" + ":포트" + POSITIONING_API_URI 로 조립한 값
PositionScannerManager manager = new PositionScannerManager(
        context,
        CommonDoc.Position.positioningServer
);

// 3. 측위 시작
manager.startScan();

// 결과는 PositionFragment.sScanReceiveHandler 로 비동기 수신
// → 6절 "측위 결과 수신" 참고
```

**서버 요청 정책**
- HTTP 메서드: `POST`
- Content-Type: `application/json`
- `X-API-KEY`: 설정에 API 키를 입력한 경우에만 헤더에 넣음
- 타임아웃: 60초 (`CommonDoc.ServerCall.WAIT_TIME`)
- 재시도: 0회 (`CommonDoc.ServerCall.RETRY`)

---

## 6. 측위 결과 수신

측위 결과는 `PositionFragment.sScanReceiveHandler`(정적 Handler)로 전달됩니다. Fragment가 활성화된 상태에서 Handler가 할당되어 있어야 합니다.

### Handler 등록 (Fragment 초기화 시)

```java
// PositionFragment.java
public static ScanReceiveHandler sScanReceiveHandler;

@Override
public View onCreateView(...) {
    sScanReceiveHandler = new ScanReceiveHandler(this);
    // ...
}
```

### 메시지 처리

```java
private final class ScanReceiveHandler extends Handler {
    private final WeakReference<PositionFragment> reference;

    public ScanReceiveHandler(PositionFragment fragment) {
        reference = new WeakReference<>(fragment);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        Bundle bundle = msg.getData();

        // 1. 상태 키 확인
        String status = bundle.getString(CommonDoc.Position.HANDLER_STATUS_KEY);

        // 서버 전송 실패
        if (CommonDoc.Position.HANDLER_FAILED_RESULT.equals(status)) {
            // 오류 처리
            return;
        }

        // 진행 상태 업데이트 (예: "서버 요청 중")
        if (CommonDoc.Position.HANDLER_STATUS_UPDATE.equals(status)) {
            String payload = bundle.getString(CommonDoc.Position.HANDLER_RESULT_KEY);
            JSONObject statusObj = new JSONObject(payload);
            String message = statusObj.optString("message", "");
            // statusTextView.setText(message);
            return;
        }

        // 2. 결과 키로 측위 결과 수신
        String key      = bundle.getString(CommonDoc.Position.HANDLER_KEY);
        String response = bundle.getString(CommonDoc.Position.HANDLER_RESULT_KEY);

        // 서버 측위 결과
        if (CommonDoc.Position.HANDLER_POSITION_RESULT.equals(key)) {
            JSONObject result = new JSONObject(response);
            int    resultCode = result.getInt("result_code");   // 0=성공, 999=실패
            String resultMsg  = result.getString("result_msg");
            double sLatitude  = result.getDouble("s_latitude");
            double sLongitude = result.getDouble("s_longitude");
            // UI 업데이트
        }
    }
}
```

### Handler 상수 정리

| 상수 | 값 | 용도 |
|------|----|------|
| `HANDLER_STATUS_KEY` | `"status"` | 메시지 종류 구분 키 |
| `HANDLER_KEY` | `"key"` | 측위 방식 구분 키 |
| `HANDLER_RESULT_KEY` | `"result"` | JSON 결과 페이로드 키 |
| `HANDLER_SUCCEED_RESULT` | `"succeed"` | 서버 응답 성공 |
| `HANDLER_FAILED_RESULT` | `"failed"` | 서버 응답 실패 |
| `HANDLER_STATUS_UPDATE` | `"status_update"` | 진행 상태 업데이트 |
| `HANDLER_POSITION_RESULT` | `"position"` | 서버 측위 결과 |

---

## 7. 서버 설정

설정 화면(`PositionSettingFragment`)에서는 **서버 주소, 포트, API 키**를 입력합니다. API 키는 비워 둘 수 있고, 입력하면 측위 요청에 `X-API-KEY` 헤더로 실립니다. `PreferenceManager`로 직접 지정할 수도 있습니다.
API 경로는 입력받지 않고 코드 상수로 고정되어 있습니다.

```java
// CommonDoc.Position
public static final String POSITIONING_API_URI = "/LocationDBService/getPosition.do";
```

입력칸을 비운 채 저장하면 hint 값(`http://example.com`, `8080`)이 그대로 저장됩니다. 실제 서버 주소를 반드시 입력하세요.

### SharedPreferences 키

| 설정 항목 | 키 상수 |
|----------|--------|
| 측위 서버 주소 (예: `http://your-server.com`) | `CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP` |
| 측위 서버 Port | `CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_PORT` |
| API 키 (`X-API-KEY` 헤더) | `CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY` |

### 설정 저장 및 로드

```java
// 저장
Map<String, Object> settings = new HashMap<>();
settings.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP,   "http://your-server.com");
settings.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_PORT, "YOUR_PORT");
settings.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY, "YOUR_API_KEY");  // 선택
PreferenceManager.setPreferences(context, CommonDoc.Position.PREFER_SETTING_FILE, settings);

// 로드 (CommonDoc.Position.* 정적 필드에 반영됨)
PreferenceManager.readPositionSetting(context);

// 로드 후 사용 가능한 전체 URL
String serverUrl = CommonDoc.Position.positioningServer;
// → "http://your-server.com:YOUR_PORT/LocationDBService/getPosition.do"
```

---

## 8. 응답 JSON 구조

### 서버 측위 응답 (`HANDLER_POSITION_RESULT`)

```json
{
  "result_code": 0,
  "result_msg": "SUCCESS",
  "s_latitude": 37.123489,
  "s_longitude": 127.123512
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `result_code` | int | `0` = 성공, `999` = 실패 |
| `result_msg` | String | 결과 메시지 |
| `s_latitude` | double | 측위 결과 위도 |
| `s_longitude` | double | 측위 결과 경도 |

---

## 참고: 전체 측위 코드 예시

```java
public class MyPositioningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 앱 컨텍스트 초기화
        CommonDoc.init(this);

        // 2. 저장된 서버 설정 로드
        PreferenceManager.readPositionSetting(this);

        // 3. 결과 수신 Handler 등록
        PositionFragment.sScanReceiveHandler = new ResultHandler(this);

        // 4. 서버 복합측위 실행
        if (CommonDoc.Position.positioningServer != null) {
            CommonDoc.Position.position_type = "server";
            CommonDoc.startLogging("server");

            PositionScannerManager manager = new PositionScannerManager(
                    this,
                    CommonDoc.Position.positioningServer
            );
            manager.startScan();
        }
    }

    // 결과 수신 Handler
    static class ResultHandler extends Handler {
        private final WeakReference<MyPositioningActivity> ref;

        ResultHandler(MyPositioningActivity activity) {
            ref = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            MyPositioningActivity activity = ref.get();
            if (activity == null) return;

            Bundle bundle = msg.getData();
            String status = bundle.getString(CommonDoc.Position.HANDLER_STATUS_KEY);

            if (CommonDoc.Position.HANDLER_FAILED_RESULT.equals(status)) {
                Log.e("TAG", "측위 실패");
                return;
            }

            if (CommonDoc.Position.HANDLER_STATUS_UPDATE.equals(status)) {
                // 진행 상태 메시지 처리 (예: "서버 요청")
                return;
            }

            String key      = bundle.getString(CommonDoc.Position.HANDLER_KEY);
            String response = bundle.getString(CommonDoc.Position.HANDLER_RESULT_KEY);

            try {
                if (CommonDoc.Position.HANDLER_POSITION_RESULT.equals(key)) {
                    JSONObject result = new JSONObject(response);
                    if (result.getInt("result_code") == 0) {
                        double lat = result.getDouble("s_latitude");
                        double lng = result.getDouble("s_longitude");
                        Log.i("TAG", "서버 측위 결과: " + lat + ", " + lng);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
```

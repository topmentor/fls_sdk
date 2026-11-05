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
9. [오류 구분과 조회](#9-오류-구분과-조회)

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
| applicationId | APK 패키지 식별자 | 신규 패키지와 동일 |
| 대상 디렉토리 | 새 프로젝트가 생성될 경로 (원본 밖, 비어 있어야 함) | (필수) |

### 비대화형(CI/스크립트) 실행

`-Yes` 를 주면 확인 프롬프트 없이 진행하며, 누락된 값은 위 표의 기본값으로 채웁니다.

```powershell
.\new-project.ps1 -Dest C:\work\MyApp -ProjectName MyApp -Package com.example.myapp -Yes
```

### 스크립트가 수행하는 작업

1. **소스 복사** — `.git`, 빌드 산출물(`build`, `.gradle`), 런타임 디렉토리(`release`, `odd`) 등은 제외하고 복사합니다.
2. **내용 치환** — 패키지(`com.sox.flssdk`), 프로젝트명(`SOXCollector`), 표시명(`FLS SDK`)을 신규 값으로 일괄 치환합니다.
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
│   └── PositionSettingFragment.java  # 서버 주소(포트 포함)·API 키 설정 화면
├── manager/
│   ├── PositionScannerManager.java  # 측위 실행 진입점
│   ├── ServerFLPManager.java        # 서버 측위 결과 처리
│   └── PreferenceManager.java       # 서버 설정 저장/로드 (SharedPreferences)
├── model/
│   └── PositionError.java       # 측위 요청 오류 정보 (종류·코드·이유)
├── scanner/                     # 단말 신호 수집
└── CommonDoc.java               # 전역 상수
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

앱은 파일을 저장하지 않으므로 저장소 권한(`READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`)은 선언하지 않습니다.

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

        // 서버 응답을 받지 못함 (통신 오류, HTTP 오류, 앱 내부 오류)
        if (CommonDoc.Position.HANDLER_FAILED_RESULT.equals(status)) {
            JSONObject failObj = new JSONObject(bundle.getString(CommonDoc.Position.HANDLER_RESULT_KEY));
            PositionError error = PositionError.fromJson(failObj.optJSONObject(PositionError.KEY));
            // error.type 으로 원인 구분, error.toDisplayText() 로 화면 표시 (9절)
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
            int    resultCode = result.getInt("result_code");   // 0=성공
            String resultMsg  = result.getString("result_msg");
            double sLatitude  = result.getDouble("s_latitude");
            double sLongitude = result.getDouble("s_longitude");

            // 측위 실패·응답 형식 오류면 error 가 들어 있다 (9절)
            PositionError error = PositionError.fromJson(result.optJSONObject(PositionError.KEY));
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
| `HANDLER_SUCCEED_RESULT` | `"succeed"` | 서버 응답 수신 (측위 실패 응답 포함) |
| `HANDLER_FAILED_RESULT` | `"failed"` | 서버 응답을 받지 못함. 페이로드에 `message`, `error` |
| `HANDLER_STATUS_UPDATE` | `"status_update"` | 진행 상태 업데이트 |
| `HANDLER_POSITION_RESULT` | `"position"` | 서버 측위 결과 |

---

## 7. 서버 설정

설정 화면(`PositionSettingFragment`)에서는 **서버 주소(포트 포함)와 API 키**를 입력합니다. 포트는 주소에 함께 적습니다(예: `http://your-server.com:8080`). 포트를 생략하면 http는 80, https는 443으로 접속합니다. API 키는 비워 둘 수 있고, 입력하면 측위 요청에 `X-API-KEY` 헤더로 실립니다. `PreferenceManager`로 직접 지정할 수도 있습니다.
API 경로는 입력받지 않고 코드 상수로 고정되어 있습니다.

```java
// CommonDoc.Position
public static final String POSITIONING_API_URI = "/LocationDBService/getPosition.do";
```

저장할 때 주소 형식을 검사합니다. 비어 있거나, `http://`·`https://`로 시작하지 않거나, 포트가 숫자가 아니면 저장하지 않고 이유를 표시합니다. 입력칸의 hint(`http://example.com:8080`)는 예시일 뿐 저장되지 않습니다.

이전 버전은 포트를 따로 저장했습니다(`positioning_server_option_port`). 이 값이 남아 있으면 `readPositionSetting()`이 처음 읽을 때 주소 뒤에 붙여 옮기고 포트 키를 지웁니다.

### SharedPreferences 키

| 설정 항목 | 키 상수 |
|----------|--------|
| 측위 서버 주소, 포트 포함 (예: `http://your-server.com:8080`) | `CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP` |
| API 키 (`X-API-KEY` 헤더) | `CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY` |

### 설정 저장 및 로드

```java
// 저장
Map<String, Object> settings = new HashMap<>();
settings.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP, "http://your-server.com:YOUR_PORT");
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
| `result_code` | int | `0` = 성공. 그 밖의 값은 측위 실패 (`999` 등) |
| `result_msg` | String | 결과 메시지 |
| `s_latitude` | double | 측위 결과 위도 |
| `s_longitude` | double | 측위 결과 경도 |
| `error` | object | 실패일 때만 있음. 9절의 오류 정보 |

---

## 9. 오류 구분과 조회

측위 요청이 실패하면 `PositionError`(`model/PositionError.java`)로 원인을 구분합니다.

### 오류 종류 (`type`)

| type | 화면 표시 | 뜻 | 대표 code |
|------|-----------|----|-----------|
| `positioning` | 측위 실패 | 서버는 정상 응답했고, 측위 결과가 성공(0)이 아님 | `RESULT_999`, `RESULT_888` |
| `server` | 서버 오류 | 서버까지 연결됐지만 HTTP 오류이거나 응답 형식이 맞지 않음 | `HTTP_401`, `HTTP_404`, `HTTP_500`, `BAD_RESPONSE` |
| `network` | 통신 오류 | 서버에 닿지 못함 | `NO_NETWORK`, `UNKNOWN_HOST`, `CONNECT_FAILED`, `TIMEOUT`, `SSL`, `NETWORK` |
| `app` | 앱 내부 오류 | 설정 또는 앱 처리 오류 | `NO_SERVER`, `BAD_URL`, `CLEARTEXT_BLOCKED`, `EXCEPTION`, `RESULT_PARSE`, `UNKNOWN` |

### 오류 정보 필드

```json
{
  "type": "network",
  "type_label": "통신 오류",
  "code": "CONNECT_FAILED",
  "message": "서버에 연결할 수 없습니다. 주소·포트와 서버 동작 여부를 확인하세요.",
  "detail": "your-server.com:8080 / Failed to connect to ...",
  "http_status": 0,
  "time": 1793845927006
}
```

- `detail`: 원인 예외 메시지, HTTP 응답 본문 앞부분(최대 200자) 등 추가 정보입니다.
- `http_status`: HTTP 응답을 받은 경우에만 값이 있고, 아니면 `0`입니다.

### 받는 방법

1. **Handler 메시지**
   - `HANDLER_FAILED_RESULT`의 페이로드: `{"message": "<표시용 문장>", "error": {...}}`
   - `HANDLER_POSITION_RESULT` 결과 JSON: 측위 실패이거나 응답 형식 오류일 때 `error`가 들어 있습니다.
2. **마지막 오류 조회**: `PositionScannerManager.getLastError()`. 가장 최근 요청의 `PositionError`를 반환하고, 성공했거나 아직 요청하지 않았으면 `null`입니다. `startScan()`을 호출할 때 `null`로 초기화됩니다.

```java
PositionError error = PositionScannerManager.getLastError();
if (error != null) {
    switch (error.type) {
        case PositionError.TYPE_POSITIONING: /* 측위 실패: 서버 결과 코드 error.code */ break;
        case PositionError.TYPE_SERVER:      /* 서버 문제: error.httpStatus */ break;
        case PositionError.TYPE_NETWORK:     /* 통신 문제 */ break;
        case PositionError.TYPE_APP:         /* 앱 설정·처리 문제 */ break;
    }
    String text = error.toDisplayText();  // "[통신 오류] 서버에 연결할 수 없습니다. ... (CONNECT_FAILED)"
}
```

같은 내용은 logcat `ScanTimerTask` 태그에 `Positioning Error: {...}`로도 남습니다. 앱은 로그 파일을 저장하지 않습니다.

### 화면 표시

원본 화면(`PositionFragment`)은 오류가 나면 서버 측위 위도·경도 아래의 **오류 상세 패널**(`layout_position_error`)을 보이고, 종류·코드·내용·HTTP 상태·상세·시각을 표시합니다. 하단 상태 표시줄에는 `[종류] 내용` 요약만 나옵니다. 측위에 성공하거나 새로 측위를 시작하면 패널을 숨깁니다.

---

## 참고: 전체 측위 코드 예시

```java
public class MyPositioningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 저장된 서버 설정 로드
        PreferenceManager.readPositionSetting(this);

        // 2. 결과 수신 Handler 등록
        PositionFragment.sScanReceiveHandler = new ResultHandler(this);

        // 3. 서버 복합측위 실행
        if (CommonDoc.Position.positioningServer != null) {
            CommonDoc.Position.position_type = "server";

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
                Log.e("TAG", "측위 요청 실패: " + PositionScannerManager.getLastError());
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

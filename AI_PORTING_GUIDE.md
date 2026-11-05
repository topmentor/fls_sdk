# FLS SDK 이식 가이드 (AI 코딩 에이전트용)

이 문서는 Claude Code, Codex 같은 AI 코딩 에이전트가 이 저장소(`com.sox.flssdk`)의 서버 측위 기능을 **다른 Android 앱**에 옮겨 넣을 때 따르는 작업 지침이다.
사람 개발자용 설명은 [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)에 있다.

- 기준 커밋: `48a67cc` (2026-11-03). 이후 코드가 바뀌었으면 코드가 기준이고, 이 문서와 다른 점을 사용자에게 알린다.
- 용어: **원본** = 이 저장소, **대상 앱** = 측위 기능을 넣을 앱.

---

## 0. 사용자가 에이전트에게 주는 지시 예시

```text
C:\project\flssdk 의 AI_PORTING_GUIDE.md 를 먼저 끝까지 읽고 그대로 따라라.
대상 앱은 <대상 앱 경로>이다. 1절의 사전 조사를 끝내고, 2절의 확인 질문을 나에게 한 뒤에 코드를 바꿔라.
```

---

## 1. 사전 조사 (코드 수정 전에 반드시)

대상 앱에서 다음을 확인해 표로 정리한다. 추정으로 채우지 말고, 확인하지 못한 항목은 "미확인"으로 적는다.

| 항목 | 확인 위치 | 왜 필요한가 |
|------|-----------|-------------|
| 언어 (Java / Kotlin) | `src/main/java`, `src/main/kotlin` | Kotlin 앱이어도 원본 Java 파일은 그대로 넣는다 (6절 참고) |
| `minSdk`, `targetSdk`, `compileSdk` | `build.gradle(.kts)` | 원본은 minSdk 31, targetSdk 32 기준. 차이가 있으면 4-4절 확인 |
| 패키지명 / `namespace` | `build.gradle`, `AndroidManifest.xml` | 이식 패키지 이름 결정 |
| 기존 의존성 버전 | `dependencies`, version catalog | Volley, Play Services Location 충돌 여부 |
| 런타임 권한 요청 방식 | 기존 Activity / 권한 유틸 | 원본 `IntroActivity`를 가져오지 않고 대상 앱 방식에 맞춘다 |
| 화면 구조 (Activity / Fragment / Compose) | 기존 화면 코드 | 결과 수신 방식 결정 (4-5절) |
| 설정 저장 방식 | SharedPreferences, DataStore, 원격 설정 등 | 서버 주소·API 키 주입 방식 결정 |
| 네트워크 보안 설정 | `network_security_config`, `usesCleartextTraffic` | 측위 서버가 http 인지 https 인지에 따라 다름 |
| 대상 앱의 라이선스 | `LICENSE`, 사용자에게 질문 | 2절 |

---

## 2. 사용자에게 먼저 확인할 것

아래는 에이전트가 임의로 정하지 않는다. 질문해서 답을 받은 뒤 진행한다.

1. **라이선스**: 원본은 **GPL-3.0**이다. GPL 코드를 넣은 앱을 배포하면 그 앱도 GPL-3.0으로 소스를 공개해야 한다. 소스 공개 없이 상업 앱에 넣으려면 S.O.X Ltd. Co.와 별도 상용 라이선스 계약이 필요하다(soxcom@naver.com). 대상 앱이 어느 쪽인지 확인한다. 답을 받기 전에는 코드를 복사하지 않는다.
2. **측위 서버 주소·포트**: 대상 앱에서 어떻게 정할지(고정값, 설정 화면, 원격 설정).
3. **API 키(`X-API-KEY`)**: 어디에 두고 어떻게 넣을지. 에이전트는 키 값을 읽거나 출력하거나 커밋하지 않는다. 값이 필요하면 "필요함"이라고만 적고 사용자가 직접 넣게 한다.
4. **BLE 미지원 단말**을 받을지. 원본 매니페스트는 `android.hardware.bluetooth_le`를 `required="true"`로 선언한다.
5. **결과를 받는 화면**과 결과·오류 상세(5-1절)를 어떻게 보여 줄지.

---

## 3. 원본 구성 요소 분류

패키지 `com.sox.flssdk` 아래 파일을 세 묶음으로 나눈다. 내부 import 관계를 확인해 정한 것이다.

### 3-1. 그대로 가져온다 (패키지 선언만 변경)

| 파일 | 역할 | 의존 |
|------|------|------|
| `scanner/Scanner.java` | 스캐너 공통 인터페이스 | — |
| `scanner/GpsScanner.java` | 신호 수집 | `CommonDoc`, `model/Buffer` |
| `scanner/LteScanner.java` | 신호 수집 | `CommonDoc` |
| `scanner/WifiScanner.java` | 신호 수집 | `CommonDoc` |
| `scanner/BluetoothScanner.java` | 신호 수집 | `CommonDoc` |
| `model/Buffer.java` | 수집값 버퍼 | — |
| `model/PositionError.java` | 측위 요청 오류 정보 (종류·코드·이유) | — |
| `manager/ServerManager.java` | Volley `RequestQueue` 싱글턴 | Volley |
| `manager/ServerFLPManager.java` | 서버 응답 해석 | `requestDevice_II1`, `GpsScanner`, `GoogleFLPScanner`, `DateTimeUtils` |
| `manager/requestDevice_II1.java` | 요청 데이터 모델 | `DateTimeUtils`, `CoordTransformUtil`, `GPoint` |
| `utils/DateTimeUtils.java` | 날짜·시간 | — |
| `utils/geo/CoordTransformUtil.java` | 좌표 간 거리 | `GPoint`, proj4j |
| `utils/geo/dataobject/GPoint.java` | 좌표 객체 | — |

### 3-2. 고쳐서 가져온다

| 파일 | 고칠 점 | 절 |
|------|---------|-----|
| `CommonDoc.java` | 상수만 담고 있다. 대상 앱이 쓰지 않는 필드 정리 | 4-6 |
| `scanner/GoogleFLPScanner.java` | `(MainActivity)mContext` 형변환 제거 | 4-4 |
| `manager/PositionScannerManager.java` | `PositionFragment.sScanReceiveHandler` 직접 참조를 콜백으로 교체 | 4-5 |
| `manager/PreferenceManager.java` | 선택. 대상 앱의 설정 방식을 쓰면 가져오지 않고 4-6절대로 값을 넣는다 | 4-6 |

### 3-3. 가져오지 않는다

`App.java`, `activity/IntroActivity.java`, `activity/MainActivity.java`, `fragment/position/*`, `res/layout/*`, `res/drawable/*`, `res/mipmap-*`, `res/values*/*`, `new-project.ps1`.
화면과 권한 흐름은 대상 앱 방식으로 다시 만든다. 원본 화면 코드는 **사용 예시로만 읽는다** (`PositionFragment`의 버튼 처리와 `ScanReceiveHandler`).

---

## 4. 이식 절차

### 4-1. 패키지 배치

- 대상 앱 패키지 아래에 하위 패키지를 하나 만들어 넣는다. 예: `<대상 패키지>.fls`. 그 아래에 원본의 `scanner`, `model`, `manager`, `utils`, `utils/geo`, `utils/geo/dataobject` 구조를 그대로 둔다.
- 모든 파일의 `package` 선언과 `import com.sox.flssdk.` 를 새 패키지로 바꾼다. 바꾼 뒤 `com.sox.flssdk` 문자열이 남지 않았는지 검색한다.
- 원본 저장소 루트의 `new-project.ps1`은 원본을 통째로 새 앱으로 복제하는 도구다. 기존 앱에 넣는 이 작업에는 쓰지 않는다.

### 4-2. Gradle 의존성

대상 앱 모듈의 `dependencies`에 추가한다. 이미 있으면 대상 앱 버전을 유지하고, 버전 차이가 크면 사용자에게 알린다.

```groovy
implementation 'com.android.volley:volley:1.2.1'
implementation 'com.google.android.gms:play-services-location:21.3.0'
implementation 'org.locationtech.proj4j:proj4j:1.4.1'
```

- `androidx.appcompat`, `androidx.core`는 대상 앱에 보통 있다. 없으면 원본 `app/build.gradle`의 버전을 참고한다.
- multidex, material, constraintlayout, legacy-support는 원본 화면용이라 필요 없다.
- 원본은 Java 11 소스 호환, 빌드 JDK 17, AGP 8.13.2로 확인했다.

### 4-3. 매니페스트

원본 [AndroidManifest.xml](app/src/main/AndroidManifest.xml)에서 `<uses-permission>`과 `<uses-feature>`만 대상 앱 매니페스트로 옮긴다. `<application>`, `<activity>`는 옮기지 않는다.

- 꼭 필요: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_NETWORK_STATE`, `READ_PHONE_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`(neverForLocation), `BLUETOOTH_CONNECT`.
- 원본 그대로 옮기되 사용자에게 알릴 것: `NEARBY_WIFI_DEVICES`, `BLUETOOTH_PRIVILEGED`, `FOREGROUND_SERVICE`.
- 2절 답에 따를 것: `uses-feature bluetooth_le`의 `required` 값.
- 저장소 권한은 옮기지 않는다 (4-7절).
- 측위 서버가 `http://` 이면 평문 통신 허용이 필요하다. 원본은 `android:usesCleartextTraffic="true"`를 쓴다. 대상 앱에서는 앱 전체를 열지 말고 `network_security_config`로 해당 도메인만 허용하는 것을 우선 제안한다.

### 4-4. 런타임 권한

- 원본이 시작 화면에서 요청하는 권한은 `IntroActivity.PERMISSIONS`에 있다: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`. 원본은 GPS가 켜져 있는지도 확인한다.
- 대상 앱의 권한 요청 흐름에 이 권한들을 넣고, **권한이 허용된 뒤에만** 측위를 시작하게 한다.
- `GoogleFLPScanner.checkLocationPermission()`은 `ActivityCompat.requestPermissions((MainActivity)mContext, ...)`를 호출한다. 대상 앱에는 `MainActivity`가 없거나 다른 클래스라서 컴파일 오류나 `ClassCastException`이 난다. 다음 둘 중 하나로 고친다.
  - (권장) 권한 요청은 대상 앱이 미리 끝낸다는 전제로 이 요청 코드를 지우고, 권한이 없으면 아무것도 하지 않게 둔다. `startScan()`에 이미 권한 검사가 있다.
  - `mContext instanceof Activity`일 때만 `(Activity) mContext`로 요청한다.
- 대상 앱 `targetSdk`가 33 이상이면 Android 13+ 단말에서 Wi-Fi 스캔에 `NEARBY_WIFI_DEVICES` 런타임 요청이 필요하다. 원본은 targetSdk 32로만 확인했으므로, 33 이상에서의 동작은 **미확인**이다. 실기기로 Wi-Fi 수집 여부를 시험하고 결과를 보고한다.

### 4-5. 결과 수신 연결 (핵심 수정)

원본 `PositionScannerManager.sendResultToFragment()`는 정적 필드 `PositionFragment.sScanReceiveHandler`(`android.os.Handler`)로 결과를 보낸다. 대상 앱에는 이 Fragment가 없으므로 콜백 인터페이스로 바꾼다.

전달되는 세 가지 상태(`CommonDoc.Position` 상수):

| 상태 | 의미 | 함께 오는 값 |
|------|------|--------------|
| `HANDLER_STATUS_UPDATE` (`"status_update"`) | 진행 상태 | `{"message": "서버 요청"}` |
| `HANDLER_SUCCEED_RESULT` (`"succeed"`) | 서버 응답 수신 | 5절의 결과 JSON |
| `HANDLER_FAILED_RESULT` (`"failed"`) | 서버 응답을 받지 못함 (통신·HTTP·앱 내부 오류) | `{"message": "<표시용 문장>", "error": {...}}` (5-1절) |

교체 예시 (원본 코드 스타일을 따른다):

```java
public interface PositionResultListener {
    void onStatus(String message);
    void onResult(JSONObject result);
    void onFailure(PositionError error);
}
```

```java
// PositionScannerManager
private final Handler mMainHandler = new Handler(Looper.getMainLooper());
private PositionResultListener mListener;

public void setPositionResultListener(PositionResultListener listener) {
    this.mListener = listener;
}

public void sendResultToFragment(String status, JSONObject object) {
    if (mListener == null) {
        return;
    }
    mMainHandler.post(() -> {
        if (CommonDoc.Position.HANDLER_FAILED_RESULT.equals(status)) {
            mListener.onFailure(PositionError.fromJson(object == null ? null : object.optJSONObject(PositionError.KEY)));
        } else if (CommonDoc.Position.HANDLER_SUCCEED_RESULT.equals(status)) {
            mListener.onResult(object);
        } else if (CommonDoc.Position.HANDLER_STATUS_UPDATE.equals(status) && object != null) {
            mListener.onStatus(object.optString("message", ""));
        }
    });
}
```

- 진행 상태는 `Timer` 스레드에서 오므로 메인 스레드로 넘겨야 한다(위 `mMainHandler`).
- `import ...fragment.position.PositionFragment`를 지운다.
- 메서드 이름 `sendResultToFragment`는 바꿔도 된다. 바꾸면 호출하는 곳(같은 파일)도 모두 고친다.
- 화면이 사라질 때 리스너를 `null`로 돌리거나 `stopScan()`을 호출해 화면 누수를 막는다.

### 4-6. 설정 값 주입

원본은 `PreferenceManager.readPositionSetting(context)`로 SharedPreferences(`flssdk_position`)를 읽어 아래 정적 필드를 채운다. 대상 앱은 어떤 방식을 쓰든 **측위 시작 전에** 이 값들을 채우면 된다.

| 필드 | 값 |
|------|----|
| `CommonDoc.Position.positioningServer` | 최종 요청 URL. `주소[:포트]` + `CommonDoc.Position.POSITIONING_API_URI`(`/LocationDBService/getPosition.do`) |
| `CommonDoc.Position.positioningServerApiKey` | API 키. 비어 있으면 `X-API-KEY` 헤더를 넣지 않는다 |
| `CommonDoc.Position.position_type` | `"server"` 고정. 다른 값이면 서버 요청을 보내지 않는다 |

- `positioningServerIp`(포트 포함 주소)는 원본 설정 화면용이다. URL을 직접 만들면 쓰지 않아도 된다. 포트는 별도 필드 없이 주소에 함께 적는다. `PREFER_KEY_POSITIONING_SERVER_OPTION_PORT`는 이전 버전 설정을 옮기는 용도로만 남아 있다.
- 서버 주소와 API 키를 소스에 문자열로 박지 않는다. 키는 Git에 올라가지 않는 곳(예: `local.properties`에서 읽어 `BuildConfig`로 주입, 또는 대상 앱이 이미 쓰는 비밀값 저장 방식)에 둔다. 어디에 둘지는 2절 답을 따른다.
- `CommonDoc`에서 대상 앱이 쓰지 않는 필드(`REQUEST_ONDEVICE_MODEL_URL`, `MODEL_DOWNLOAD_DIRECTORY`, `ServerCall.DEFAULT_*`, `WAIT_TIME2`, `RETRY2`, `target_type`)는 지워도 된다. 지운 뒤 빌드로 참조가 없는지 확인한다.
- `CommonDoc.Scanner`, `CommonDoc.Position.SCAN_INTERVAL`, `CommonDoc.ServerCall.WAIT_TIME`/`RETRY` 값은 **바꾸지 않는다** (7절).

### 4-7. 파일 저장 없음

원본은 단말에 파일을 저장하지 않는다. 측위 로그 파일과 비정상 종료 로그 파일 기능은 2026-11-05 사용자 결정으로 제거했다. 그래서 저장소 권한(`READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`)도 쓰지 않는다.

- 이식할 때 로그 파일 저장이나 저장소 권한을 새로 넣지 않는다. 필요해 보이면 사용자에게 먼저 묻는다.
- 오류 확인은 `PositionError`(5-1절)와 logcat `ScanTimerTask` 태그로 한다.

### 4-8. 측위 실행

원본 `PositionFragment.onClick()`과 같은 흐름이다.

```java
// 메인 스레드에서 호출한다 (스캐너가 Toast, 위치 콜백 Looper를 쓴다).
CommonDoc.Position.position_type = "server";

PositionScannerManager manager = new PositionScannerManager(context, CommonDoc.Position.positioningServer);
manager.setPositionResultListener(listener);   // 4-5절
manager.startScan();
```

지켜야 할 동작 특성 (코드로 확인한 사실):

- **1회용 객체다.** 서버 요청을 보낸 직후 `stopScan()`이 내부 `Timer`를 `cancel()`하고 `null`로 만든다. 다음 측위는 새 인스턴스로 한다. 같은 인스턴스로 `startScan()`을 다시 부르면 `NullPointerException`이 난다.
- **자체 시간 제한이 없다.** 요청 조건을 만족하는 신호가 모이지 않으면 `CommonDoc.Position.SCAN_INTERVAL`(3초) 주기로 계속 확인하고 서버 요청을 보내지 않는다. 대상 앱에서 제한 시간(예: 수십 초)을 두고, 지나면 `stopScan()` 후 실패로 처리하도록 제안한다. 제한 시간 값은 사용자와 정한다.
- 서버 요청 타임아웃은 `CommonDoc.ServerCall.WAIT_TIME`(60초), 재시도 0회다.
- 화면을 벗어나거나 앱이 백그라운드로 가면 `stopScan()`을 호출한다. 백그라운드 측위는 원본에서 시험하지 않았다(**미확인**).
- 생성자에 넘기는 `Context`는 Activity 또는 Application 모두 컴파일은 되지만, 4-4절 수정 전에는 Activity만 안전하다.

---

## 5. 결과 JSON 해석

`onResult(JSONObject result)`로 오는 값은 서버 원문이 아니라 `ServerFLPManager.findLocation()`이 정리한 객체다.

| 키 | 형 | 의미 |
|----|----|------|
| `result_code` | int | 서버 결과 코드 |
| `result_msg` | String | 서버 메시지 |
| `eTime` | String | 요청 시각 |
| `s_latitude` | double | 위도 (WGS84) |
| `s_longitude` | double | 경도 (WGS84) |
| `findtype` | int | 결과 구분 코드. 표시용으로만 쓰고 의미를 추정해 문서화하지 않는다 |

결과 코드:

| 코드 | 확인 상태 | 처리 |
|------|-----------|------|
| `0` | 문서화됨 | 서버 측위 성공. `s_latitude`/`s_longitude`가 서버 결과 |
| `999` | 문서화됨 | 실패. 이때 좌표는 서버 값이 아니라 단말의 Fused Location 값으로 채워진다. 서버 측위 결과로 보여 주지 않는다 |
| `888` | 2026-11-03 실서버 응답으로 관찰, 의미는 문서 없음 | 메시지 `"No Result - GPS"`. 좌표는 단말 GPS 값을 되돌려 준 것으로 보인다(추정) |
| 그 밖 | 미확인 | |

- 대상 앱에서는 **`result_code == 0`일 때만 서버 측위 성공**으로 취급하도록 구현한다. 원본 `ServerFLPManager`는 `999`가 아니면 `pos_info`를 읽는데, 이 동작은 바꾸지 않는다.
- 결과 코드가 0이 아니면 결과 JSON에 `error`(5-1절)가 함께 들어 있다.
- 원본 화면은 결과 좌표와 단말 GPS 좌표 사이 거리를 `CoordTransformUtil.getDistanceBetween(new GPoint(lon, lat), new GPoint(sLon, sLat), true)`로 계산해 표시한다. `GPoint` 인자 순서는 (경도, 위도)다. 필요할 때만 쓴다.

### 5-1. 오류 정보 (`PositionError`)

요청이 실패하면 원인을 네 종류로 나눈 `PositionError`가 만들어진다. 대상 앱 화면에도 종류와 이유를 함께 보여 주게 구현한다. "오류"라고만 표시하지 않는다. 원본 화면은 서버 측위 결과 아래에 오류일 때만 보이는 상세 패널을 두고(`PositionFragment.showError()` / `hideError()`), 하단 상태 표시줄에는 `[종류] 내용` 요약만 둔다. 대상 앱도 이 방식을 기본안으로 제안한다.

| `type` | 뜻 | 전달 경로 |
|--------|----|-----------|
| `positioning` | 서버는 정상 응답, 측위 결과가 0이 아님 (`RESULT_<코드>`) | 결과 JSON의 `error` |
| `server` | HTTP 오류(`HTTP_<상태>`), 응답 형식 오류(`BAD_RESPONSE`) | 실패 페이로드 또는 결과 JSON의 `error` |
| `network` | 서버에 닿지 못함 (`NO_NETWORK`, `UNKNOWN_HOST`, `CONNECT_FAILED`, `TIMEOUT`, `SSL`, `NETWORK`) | 실패 페이로드 |
| `app` | 설정·앱 처리 오류 (`NO_SERVER`, `BAD_URL`, `CLEARTEXT_BLOCKED`, `EXCEPTION`, `UNKNOWN`) | 실패 페이로드 또는 결과 JSON의 `error` |

- 필드: `type`, `type_label`, `code`, `message`, `detail`, `http_status`, `time`. 자세한 내용은 [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) 9절에 있다.
- 콜백 밖에서 조회: `PositionScannerManager.getLastError()`. `startScan()` 때 `null`로 초기화된다.
- 표시 문장: `error.toDisplayText()`.

---

## 6. Kotlin 앱에 넣을 때

- 원본 파일은 Java로 그대로 넣는다. Kotlin으로 변환하지 않는다. 변환하면 수집·요청 동작이 원본과 달라질 위험이 있고 검증 기준이 없어진다.
- 4-5절 리스너는 메서드가 3개라 람다로 쓸 수 없다. Kotlin에서는 `object : PositionResultListener { ... }` 익명 객체로 구현한다.
- Compose 화면이면 리스너에서 `MutableState` / `StateFlow`를 갱신한다. 리스너는 이미 메인 스레드에서 불린다.

---

## 7. 하지 말 것

- **스캐너(`scanner/*`)의 수집 로직, 주기·임계값 상수(`CommonDoc.Scanner`, `CommonDoc.Position.SCAN_INTERVAL`), 요청 본문을 만드는 `PositionScannerManager.getResult()`를 바꾸지 않는다.** 서버와 맞춰진 형식이다. 고쳐야 할 이유가 보이면 수정하지 말고 사용자에게 보고한다.
- 측위 방식, 신호 처리, 서버 알고리즘을 추측해 주석·문서·커밋 메시지에 쓰지 않는다. 대상 앱 문서에는 "SOX 서버 측위 연동"처럼 기능 이름만 적는다.
- API 키·서버 주소를 소스, 테스트, 문서, 로그, 커밋에 넣지 않는다. 시험용 값도 커밋 전에 지운다.
- 원본의 `LICENSE`와 저작권 표시(`Copyright (C) 2026 S.O.X Ltd. Co.`)를 지우지 않는다. GPL로 배포하는 경우 대상 앱에도 GPL-3.0 전문과 고지를 둔다. 상용 라이선스 계약을 맺은 경우의 표기는 계약을 따르며, 사용자에게 확인한다.
- 원본 저장소(`flssdk`)는 수정하지 않는다. 읽기만 한다.
- 대상 앱의 기존 권한·네트워크 보안 설정을 넓히는 변경(앱 전체 평문 통신 허용 등)은 사용자 승인 후에만 한다.

---

## 8. 검증

1. **빌드**: 대상 앱 `assembleDebug` 성공. 경고 중 이식으로 새로 생긴 것은 보고한다.
2. **잔여 참조 검색**: `com.sox.flssdk`, `MainActivity`(원본 것), `PositionFragment`, `sScanReceiveHandler`가 이식 코드에 남지 않았는지.
3. **실행**: 실기기 권장. 에뮬레이터는 신호 수집이 실제와 다르다. 권한 허용 후 측위를 시작하고 logcat에서 다음 순서를 확인한다.
   - `Position Scanner Manager: start scan`
   - `ScanTimerTask: Positioning Server Request`
   - `ScanTimerTask: Receive Location Data - Server` (성공) 또는 `ScanTimerTask: Positioning Error: {...}` (실패, 오류 종류 포함)
   - `Position Scanner Manager: stop scan`
4. **실서버 시험**: 측위 시험은 실제 측위 서버로만 의미가 있다. 임의 주소나 로컬 수신기를 대상으로 시험하지 않는다. 서버 주소와 API 키는 사용자가 앱에 직접 넣고, 실행 여부도 사용자가 정한다. 에이전트는 시험 절차를 안내하고, 사용자가 알려 준 결과 코드·메시지·오류 종류를 정리한다.
5. **반복 측위**: 두 번 이상 연속 실행해 4-8절의 1회용 객체 규칙이 지켜지는지 확인한다. 4번과 같이 사용자가 실서버로 한다.

---

## 9. 완료 보고 형식

에이전트는 작업 끝에 사용자에게 다음을 보고한다.

- 2절 질문과 사용자 답
- 가져온 파일 / 고친 파일 / 가져오지 않은 파일 목록과 이유
- 대상 앱에서 바꾼 설정: Gradle, 매니페스트, 권한 흐름, 네트워크 보안
- 검증 결과: 8절 항목별로 "확인함 / 실패 / 시험 안 함"을 나눠 적는다. 시험하지 못한 항목은 이유와 함께 "미확인"으로 적는다
- 남은 결정 사항 (예: 측위 제한 시간, 라이선스 표기)

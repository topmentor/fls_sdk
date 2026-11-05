package com.sox.flssdk.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 측위 요청 1회의 오류 정보.
 * type 으로 원인이 어디에 있는지 구분한다.
 */
public class PositionError {

    // 서버가 정상 응답했고, 측위 결과가 실패 (result_code != 0)
    public static final String TYPE_POSITIONING = "positioning";
    // 서버까지 연결됐지만 HTTP 오류 또는 응답 형식 오류
    public static final String TYPE_SERVER = "server";
    // 서버에 닿지 못함 (네트워크 없음, 주소 못 찾음, 연결 거부, 시간 초과, SSL)
    public static final String TYPE_NETWORK = "network";
    // 앱 설정 또는 앱 내부 처리 오류
    public static final String TYPE_APP = "app";

    public static final String KEY = "error";

    public final String type;
    public final String code;
    public final String message;
    public final String detail;
    public final int httpStatus;
    public final long time;

    public PositionError(String type, String code, String message, String detail, int httpStatus) {
        this.type = type;
        this.code = code;
        this.message = message;
        this.detail = detail == null ? "" : detail;
        this.httpStatus = httpStatus;
        this.time = System.currentTimeMillis();
    }

    public PositionError(String type, String code, String message, String detail) {
        this(type, code, message, detail, 0);
    }

    private PositionError(JSONObject obj) {
        this.type = obj.optString("type", TYPE_APP);
        this.code = obj.optString("code", "");
        this.message = obj.optString("message", "");
        this.detail = obj.optString("detail", "");
        this.httpStatus = obj.optInt("http_status", 0);
        this.time = obj.optLong("time", 0);
    }

    public static PositionError fromJson(JSONObject obj) {
        return obj == null ? null : new PositionError(obj);
    }

    public String getTypeLabel() {
        switch (type) {
            case TYPE_POSITIONING:
                return "측위 실패";
            case TYPE_SERVER:
                return "서버 오류";
            case TYPE_NETWORK:
                return "통신 오류";
            default:
                return "앱 내부 오류";
        }
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", type);
            obj.put("type_label", getTypeLabel());
            obj.put("code", code);
            obj.put("message", message);
            obj.put("detail", detail);
            obj.put("http_status", httpStatus);
            obj.put("time", time);
        } catch (JSONException ignore) {
        }
        return obj;
    }

    /**
     * 화면 표시용 문장. 예: "[통신 오류] 서버에 연결할 수 없습니다. (CONNECT_REFUSED)"
     */
    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getTypeLabel()).append("] ").append(message);
        if (code != null && !code.isEmpty()) {
            sb.append(" (").append(code).append(")");
        }
        if (!detail.isEmpty()) {
            sb.append("\n").append(detail);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}

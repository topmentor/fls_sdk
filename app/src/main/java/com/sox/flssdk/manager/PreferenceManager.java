package com.sox.flssdk.manager;

import android.content.Context;
import android.content.SharedPreferences;
import com.sox.flssdk.CommonDoc;

import java.util.HashMap;
import java.util.Map;

// 설정정보 관련
public class PreferenceManager {

    private static SharedPreferences getPreference(Context context, String fileName) {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }


    public static boolean setPreference(Context context, String fileName, String key, Object value) {
        Map<String, Object> preferMap = new HashMap<>();
        preferMap.put(key, value);

        return setPreferences(context, fileName, preferMap);
    }


    /**
     * SharedPreference 저장
     */
    public static boolean setPreferences(Context context, String fileName, Map<String, Object> settingValue) {
        SharedPreferences sharedPreferences = getPreference(context, fileName);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        for (Map.Entry<String, Object> entry : settingValue.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof String) {
                editor.putString(entry.getKey(), String.valueOf(value));
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (int) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (boolean) value);
            }
        }

        return editor.commit();
    }


    /**
     * SharedPreference 가져오기
     */
    public static Map<String, Object> getPreferenceToMap(Context context, String fileName) {
        SharedPreferences sharedPreferences = getPreference(context, fileName);

        return (Map<String, Object>) sharedPreferences.getAll();
    }


    /**
     * 측위 설정 정보 읽기
     */
    public static void readPositionSetting(Context context) {
        Map<String, Object> settingMap = getPreferenceToMap(context, CommonDoc.Position.PREFER_SETTING_FILE);

        // 측위 서버
        CommonDoc.Position.positioningServerIp = (String) settingMap.getOrDefault(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP, "");
        CommonDoc.Position.positioningServerPort = (String) settingMap.getOrDefault(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_PORT, "");
        CommonDoc.Position.positioningServerApiKey = (String) settingMap.getOrDefault(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY, "");

        CommonDoc.Position.positioningServer = null;
        if (CommonDoc.Position.positioningServerIp != null && !"".equals(CommonDoc.Position.positioningServerIp)) {
            StringBuilder sb = new StringBuilder();
            sb.append(CommonDoc.Position.positioningServerIp);

            if (CommonDoc.Position.positioningServerPort != null && !"".equals(CommonDoc.Position.positioningServerPort)) {
                sb.append(":").append(CommonDoc.Position.positioningServerPort);
            }

            sb.append(CommonDoc.Position.POSITIONING_API_URI);

            CommonDoc.Position.positioningServer = sb.toString();
        }

        CommonDoc.Position.target_type = (String) settingMap.getOrDefault(CommonDoc.Position.PREFER_KEY_TARGET_OPTION, "gps");
    }
}

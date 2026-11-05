package com.sox.flssdk.manager;

import android.content.Context;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import com.sox.flssdk.model.PositionError;
import com.sox.flssdk.scanner.GoogleFLPScanner;
import com.sox.flssdk.scanner.GpsScanner;
import com.sox.flssdk.utils.DateTimeUtils;
import com.sox.flssdk.utils.geo.dataobject.GPoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ServerFLPManager {

    private static JSONObject statusObject;


    public static double result_longitude = 0.0;
    public static double result_latitude = 0.0;


    public static void findLocation(JSONObject sObj, JSONObject request, JSONObject response) {
        statusObject = sObj;

        try {
            requestDevice_II1 requestObj = new requestDevice_II1(request);
            String timeStr = DateTimeUtils.convertTimestampToDate(requestObj.request_time);

            int result_code = response.getInt("result_code");
            String res_msg =  response.getString("result_msg");
            statusObject.put("result_code",result_code);
            statusObject.put("result_msg", res_msg);
            statusObject.put("eTime", timeStr);

            if(result_code != 999){

                JSONObject positionObject = response.getJSONObject("result_data").getJSONObject("pos_info");
                result_longitude = positionObject.getDouble("longitude");
                result_latitude = positionObject.getDouble("latitude");
                statusObject.put("s_latitude", result_latitude);
                statusObject.put("s_longitude", result_longitude);

                if(response.getJSONObject("result_data").has("findtype")){
                    String resStr = response.getJSONObject("result_data").getString("findtype");
                    int findType = requestDevice_II1.getFindTypeValue(resStr) ;
                    statusObject.put("findtype", findType);
                }else{
                    statusObject.put("findtype", 22);
                }


            }else{
                result_longitude = GoogleFLPScanner.sLongitude;
                result_latitude = GoogleFLPScanner.sLatitude;
                statusObject.put("s_latitude", result_latitude);
                statusObject.put("s_longitude", result_longitude);
                statusObject.put("findtype", 0);
            }

            // 서버는 정상 응답했지만 측위 결과가 성공(0)이 아닌 경우
            if (result_code != 0) {
                putError(new PositionError(PositionError.TYPE_POSITIONING, "RESULT_" + result_code, res_msg, null));
            }

        } catch (JSONException e) {
            // 응답에 필요한 값(result_code, result_msg, result_data.pos_info 등)이 없음
            e.printStackTrace();
            putFallback(new PositionError(PositionError.TYPE_SERVER, "BAD_RESPONSE",
                    "서버 응답에 필요한 값이 없습니다.", e.getMessage() + " / " + abbreviate(response)));
        } catch (Exception e) {
            e.printStackTrace();
            putFallback(new PositionError(PositionError.TYPE_APP, "EXCEPTION",
                    "서버 응답 처리 중 앱 내부 오류가 발생했습니다.", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static void putFallback(PositionError error) {
        try {
            statusObject.put("result_code", 999);
            statusObject.put("result_msg", error.message);
            result_longitude = GpsScanner.sLongitude;
            result_latitude = GpsScanner.sLatitude;
            statusObject.put("s_latitude", result_latitude);
            statusObject.put("s_longitude", result_longitude);
            statusObject.put("findtype", 0);
        } catch (JSONException ignore) {
        }
        putError(error);
    }

    private static void putError(PositionError error) {
        try {
            statusObject.put(PositionError.KEY, error.toJson());
        } catch (JSONException ignore) {
        }
    }

    private static String abbreviate(JSONObject response) {
        String text = response == null ? "null" : response.toString();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }


}

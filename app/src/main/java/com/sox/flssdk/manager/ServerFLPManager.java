package com.sox.flssdk.manager;

import android.content.Context;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

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

        } catch (Exception e) {
            e.printStackTrace();
            try {
                statusObject.put("result_code", 999);
                statusObject.put("result_msg", "Error");
                result_longitude = GpsScanner.sLongitude;
                result_latitude = GpsScanner.sLatitude;
                statusObject.put("s_latitude", result_latitude);
                statusObject.put("s_longitude", result_longitude);
                statusObject.put("findtype", 0);

            } catch (JSONException ex) {
            }
            return;
        }


    }


}

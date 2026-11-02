package com.sox.flssdk.manager;

import android.util.Log;

import com.sox.flssdk.utils.DateTimeUtils;
import com.sox.flssdk.utils.geo.CoordTransformUtil;
import com.sox.flssdk.utils.geo.dataobject.GPoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class requestDevice_II1 {
    public final static int FINDTYPE_LTE = 1;
    public final static int FINDTYPE_FUSED_LTE = 20;
    public final static int FINDTYPE_FUSED_LTE_WIFI_BLE = 21;
    public final static int FINDTYPE_FUSED_LTE_WIFI = 22;
    public final static int FINDTYPE_FUSED_WIFI_BLE = 23;
    public final static int FINDTYPE_FUSED_LTE_BLE = 25;
    public final static int FINDTYPE_TEMP_MATCH = 30;


    public JSONObject jParam = null;

    public int proto_ver = 0;
    public String session_id = "";
    public String model_name = "";
    public String req_posmethod = "";
    public int req_cellidtype = 0;
    public int req_targettype = 0;
    public String req_telecom = "";
    public long request_time = 1623031480634L;
    public long collect_time = 1623031480634L;
    public int mnc = 0;
    public int mcc = 450;
    public String cellID = "";

    public int batch_mode = 0;


    public JSONArray lteList = null;
    public JSONArray wifiList = null;
    public JSONArray bleList = null;
    public JSONArray gpsList = null;
    public JSONArray sensorList = null;
    public JSONObject coarse_pos_info = null;
    public JSONObject vertical_pos_info = null;
    public double longitude_coarse = 0.0;
    public double latitude_coarse = 0.0;
    public int  accuracy_h = 0;
    public double altitude = 0;
    public int  accuracy_v = 0;
    public int  floor = 1;

    public double longitude = 0;
    public double latitude = 0;
    public double orgLatitude = 0;
    public double orgLongitude = 0;
    public double lteLatitude = 0;
    public double lteLongitude = 0;


    public int xId = 0;
    public int yId = 0;
    public int findType = 0;
    public double dist = -1;
    public long response_time = 0;
    public int lteMaxMatch = 0;
    public int lteServingMatch = 0;
    public int lteNeighboringMatch = 0;
    public int wifiMaxMatch = 0;
    public int bleMaxMatch = 0;
    public int lteLength = 0;
    public int wifiLength = 0;
    public int bleLength = 0;
    public double lteMatchAccuracy = 0;
    public double wifiMatchAccuracy = 0;
    public double bleMatchAccuracy = 0;


    public String queryLteServingKey = "";
    public String queryLteNeighboringKey = "";

    public double matchingScore = 0;


    public String singleLteKey = "";
    public String allLteKey = "";


    public String OtherLteMatchKey = "";
    public String OtherWifiMatchKey = "";

    public int findLTEType = 1 ;
    public String step1LteMatchKey = "";
    public String step15LteMatchKey = "";


    public String lteMatchKey = "";
    public String lteServingMatchKey = "";
    public String lteNeighboringMatchKey = "";

    public String wifiMatchKey = "";
    public String bleMatchKey = "";
    public int pointCount = 0;


    public JSONObject jResult = null;

    public boolean loadObject = false;


    public boolean checkMultiMNC = false;

    public requestDevice_II1(JSONObject jObj){
        setDeviceInfo(jObj);
    }


    public void setDeviceInfo(JSONObject jObj){
        this.jParam = jObj;

        try {
            proto_ver = jObj.getInt("proto_ver");
            model_name = jObj.getString("model_name");
            session_id = jObj.getString("session_id");
            request_time = jObj.getLong("request_time");


            if(jObj.has("collect_time")){

                collect_time = jObj.getLong("collect_time");
            }else{
                collect_time = request_time;
            }

            if(jObj.has("batch_mode")){
                batch_mode = jObj.getBoolean("batch_mode") == true ? 1 : 0;
            }


            try {
                orgLongitude = jObj.getDouble("longitude");
            } catch (Exception e) {
                orgLongitude = 0.0;

            }

            try {
                orgLatitude = jObj.getDouble("latitude");
            } catch (Exception e) {
                orgLatitude = 0.0;

            }

            setLocation(orgLongitude, orgLatitude);

            try {
                req_posmethod = jObj.getString("req_posmethod");
            } catch (Exception e) {
                req_posmethod = "Fused";
            }

            try {
                req_cellidtype = jObj.getInt("req_cellidtype");
            } catch (Exception e) {
                req_cellidtype = 1;
            }

            try {
                req_targettype = jObj.getInt("req_targettype");
            } catch (Exception e) {
            }

            JSONArray lArr = null;
            JSONArray wArr =  null;
            JSONArray bArr = null;
            JSONArray gArr = null;
            JSONArray sArr =  null;
            JSONObject cObj = null;
            JSONObject cObj2 = null;
            JSONObject vObj = null;

            try {
                lArr = jObj.getJSONArray("lte_measurements");

                cellID = lArr.getJSONArray(0).getJSONObject(0).getString("cid");

                for (int i = 0; i < lArr.length(); i++) {
                    JSONArray subArr = lArr.getJSONArray(i);
                    for (int j = 0; j < subArr.length(); j++) {
                        JSONObject jLTEObj = subArr.getJSONObject(j);

                        if(jLTEObj.getInt("cid") > 0) {
                            cellID = jLTEObj.getString("cid");

                            mnc = jLTEObj.getInt("mnc");

                            mnc = (mnc == 12) ? 5 : mnc;
                            req_telecom = getTelecomName(mnc);

                            break;
                        }
                    }
                }


            } catch (Exception e) {
            }

            try {
                wArr = jObj.getJSONArray("wifi_measurements");

                try {
                    for(int i=0 ; i< wArr.length() ; i++){
                        try {
                            JSONArray arr =  wifiList.getJSONArray(i);
                            for(int j=0; j < arr.length() ; j++){
                                JSONObject wObj = arr.getJSONObject(j);
                                String ssid = wObj.getString("ssid");
                                ssid = ssid.replaceAll("\"", "");
                                ssid = ssid.replaceAll("'", "");
                                wObj.put("ssid", ssid);
                            }
                        } catch (JSONException ex) {

                        }
                    }
                } catch (Exception e) {
                }
            } catch (Exception e) {
            }

            try {
                bArr = jObj.getJSONArray("bluetooth_measurements") ;
            } catch (Exception e) {
            }

            try {
                gArr = jObj.getJSONArray("gnss");
            } catch (Exception e) {
            }


            try {
                sArr = jObj.getJSONArray("sensor");
            } catch (Exception e) {
            }


            try {
                cObj2 = jObj.getJSONObject("coarse_pos_info");
                longitude_coarse = cObj2.getDouble("longitude_coarse");
                latitude_coarse = cObj2.getDouble("latitude_coarse");
                accuracy_h = cObj2.getInt("accuracy_h");
            } catch (Exception e) {
            }

            try {
                vObj = jObj.getJSONObject("vertical_pos_info");
                altitude = vObj.getDouble("altitude");
                accuracy_v = vObj.getInt("accuracy_v");
                floor = vObj.getInt("floor");
            } catch (Exception e) {
            }


            setSigData(lArr, wArr, bArr, gArr, sArr, cObj2, vObj);
            loadObject = true;

        } catch (JSONException ex) {
            ex.printStackTrace();
        }

    }

    public void setLocation(double lnt, double lat){
        latitude = lat;
        longitude = lnt;
    }


    public void setSigData(JSONArray lList, JSONArray wList, JSONArray bList, JSONArray gList, JSONArray sList, JSONObject coarsePos, JSONObject verticalPos ){
        lteList = lList;
        wifiList = wList;
        bleList = bList;
        gpsList = gList;
        sensorList = sList;

        if(verticalPos != null){
            coarse_pos_info = coarsePos;
            vertical_pos_info = verticalPos;
        }else{
            coarse_pos_info = null;
            vertical_pos_info = null;
        }


    }


    public static String getTelecomName(int mnc){

        String corpName = "";
        if(mnc == 8){
            corpName = "kt";
        }else if(mnc == 5 || mnc == 12){
            corpName = "skt";
        }else if(mnc == 6 || mnc == 7){
            corpName = "lg";
        }
        return corpName;

    }


    public static int getFindTypeValue(String fType){
        int result = 0;


        if(fType.equals("LTE")){
            result = FINDTYPE_LTE;


        }else if(fType.equals("Fused(LTE+WIFI+BLE)")){
            result = FINDTYPE_FUSED_LTE_WIFI_BLE;

        }else if(fType.equals("Fused(LTE+WIFI)")){
            result = FINDTYPE_FUSED_LTE_WIFI;

        }else if(fType.equals("Fused(WIFI+BLE)")){
            result = FINDTYPE_FUSED_WIFI_BLE;

        }else if(fType.equals("Fused(LTE+BLE)")){
            result = FINDTYPE_FUSED_LTE_BLE;

        }else if(fType.equals("Fused(LTE)")){
            result = FINDTYPE_FUSED_LTE;

        }else if(fType.equals("Temp Match")){
            result = FINDTYPE_TEMP_MATCH;

        }else{
            result = 0;
        }
        return result;
    }

}


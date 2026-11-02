package com.sox.flssdk.manager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.fragment.position.PositionFragment;
import com.sox.flssdk.scanner.BluetoothScanner;
import com.sox.flssdk.scanner.GoogleFLPScanner;
import com.sox.flssdk.scanner.GpsScanner;
import com.sox.flssdk.scanner.LteScanner;
import com.sox.flssdk.scanner.Scanner;
import com.sox.flssdk.scanner.WifiScanner;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.HashMap;

public class PositionScannerManager {
    private final Context mContext;

    Scanner mGpsScanner;
    Scanner mGFlpScanner;
    Scanner mLteScanner;
    Scanner mWifiScanner;
    Scanner mBleScanner;

    String mServer;
    String mLatitude;
    String mLongitude;

    String mHandlerKey;

    Timer mScanTimer;
    TimerTask mScanTimerTask;


    public PositionScannerManager(Context context, String server) {
        this(context, server, null, null);

        this.mHandlerKey = CommonDoc.Position.HANDLER_POSITION_RESULT;
    }

    public PositionScannerManager(Context context, String server, String latitude, String longitude) {
        this.mContext = context;

        this.mGpsScanner = new GpsScanner(this.mContext);
        this.mGFlpScanner = new GoogleFLPScanner(this.mContext);
        this.mLteScanner = new LteScanner(this.mContext);
        this.mWifiScanner = new WifiScanner(this.mContext);
        this.mBleScanner = new BluetoothScanner(this.mContext);

        this.mServer = server;
        this.mLatitude = latitude;
        this.mLongitude = longitude;

        this.mHandlerKey = CommonDoc.Position.HANDLER_LOG_RESULT;

        this.mScanTimer = new Timer();
    }


    private boolean isInternetAvailable() {
        ConnectivityManager cm =
            (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.w("PositionScannerManager", "ConnectivityManager is null");
            return false;
        }

        Network network = cm.getActiveNetwork();
        if (network == null) {
            Log.w("PositionScannerManager", "No active network");
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        boolean connected = caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));

        Log.d("PositionScannerManager", "Internet available: " + connected);
        return connected;
    }
    
    private class ScanTimerTask extends TimerTask {

        @Override
        public void run() {
            JSONObject object = getResult();


            if (!object.isNull(CommonDoc.Scanner.LTE_SCAN_RESULT_KEY) &&
                (!object.isNull(CommonDoc.Scanner.WIFI_SCAN_RESULT_KEY) ||
                 !object.isNull(CommonDoc.Scanner.BLE_SCAN_RESULT_KEY))) {


                CommonDoc.writelog("target type : gps");

                try {
                    object.put("tLongitude", GpsScanner.sLongitude);
                    object.put("tLatitude", GpsScanner.sLatitude);

                    CommonDoc.writelog("target location : " + String.format("%.6f", GpsScanner.sLongitude) + " , " + String.format("%.6f", GpsScanner.sLatitude));

                } catch(Exception e){
                }


                if(CommonDoc.Position.position_type.equals("server")) {
                    JSONObject statusObject = new JSONObject();

                    ServerManager.getRequestQueue(mContext);


                    Log.i("ScanTimerTask", "Positioning Server Request");

                    try {
                        JSONObject progressStatus = new JSONObject();
                        progressStatus.put("message", "서버 요청");
                        sendResultToFragment(CommonDoc.Position.HANDLER_STATUS_UPDATE, progressStatus);
                    } catch (JSONException ignore) {}

                    CommonDoc.writelog("request (scan data) : " + object.toString());
                    
                    JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                        Request.Method.POST, 
                        mServer, 
                        object, 
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                Log.i("ScanTimerTask", "Receive Location Data - Server");

                                CommonDoc.writelog("receive location from server  : \n" + response.toString());
                                ServerFLPManager.findLocation(statusObject, object, response);

                                sendResultToFragment(CommonDoc.Position.HANDLER_SUCCEED_RESULT, statusObject);
                            }
                        }, 
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                sendResultToFragment(CommonDoc.Position.HANDLER_FAILED_RESULT, null);
                            }
                        }
                    ) {
                        @Override
                        public Map<String, String> getHeaders() throws AuthFailureError {
                            Map<String, String> headers = new HashMap<String, String>();
                            headers.put("Content-Type", "application/json");
                            headers.put("Accept", "application/json");
                            String apiKey = CommonDoc.Position.positioningServerApiKey;
                            if (apiKey != null && !apiKey.trim().isEmpty()) {
                                headers.put("X-API-KEY", apiKey.trim());
                            }
                            return headers;
                        }
                    };
                    
                    jsonObjectRequest.setRetryPolicy(new RetryPolicy() {
                        @Override
                        public int getCurrentTimeout() {
                            return CommonDoc.ServerCall.WAIT_TIME ;
                        }

                        @Override
                        public int getCurrentRetryCount() {
                            return CommonDoc.ServerCall.RETRY ;
                        }

                        @Override
                        public void retry(VolleyError error) throws VolleyError {
                            throw error;
                        }
                    });
                    
                    ServerManager.getRequestQueue(mContext).add(jsonObjectRequest);

                    stopScan();

                }else{

                    JSONObject statusObject = new JSONObject();
                    try {
                        statusObject.put("message", "");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendResultToFragment(CommonDoc.Position.HANDLER_STATUS_UPDATE, statusObject);

                }

                if (mScanTimerTask != null) {
                    mScanTimerTask.cancel();
                    mScanTimerTask = null;
                }
            }
        }
    }

    public void startScan() {
        this.mGpsScanner.startScan();
        this.mGFlpScanner.startScan();
        this.mLteScanner.startScan();
        this.mWifiScanner.startScan();
        this.mBleScanner.startScan();

        mScanTimerTask = new ScanTimerTask();
        mScanTimer.schedule(mScanTimerTask, 0, CommonDoc.Position.SCAN_INTERVAL);

        Log.i("Position Scanner Manager", "start scan");
    }


    public void stopScan() {
        if (this.mGpsScanner != null) {
            this.mGpsScanner.stopScan();
        }

        if (this.mGFlpScanner != null) {
            this.mGFlpScanner.stopScan();
        }

        if (this.mLteScanner != null) {
            this.mLteScanner.stopScan();
        }

        if (this.mWifiScanner != null) {
            this.mWifiScanner.stopScan();
        }

        if (this.mBleScanner != null) {
            this.mBleScanner.stopScan();
        }

        if (this.mScanTimerTask != null) {
            this.mScanTimerTask.cancel();
            this.mScanTimerTask = null;
        }

        if (this.mScanTimer != null) {
            this.mScanTimer.cancel();
            this.mScanTimer = null;
        }

        Log.i("Position Scanner Manager", "stop scan");
    }

    public JSONObject getResult() {
        JSONObject object = new JSONObject();

        try {

            long timeNow = System.currentTimeMillis();
            object.put("name", this.mLatitude != null? this.mLatitude : "");
            object.put("comment", this.mLongitude != null? this.mLongitude : "");

            object.put("latitude", GpsScanner.sLatitude);
            object.put("longitude", GpsScanner.sLongitude);
            object.put("gpslatitude", GpsScanner.sLatitude);
            object.put("gpslongitude", GpsScanner.sLongitude);
            object.put("gfLatitude", GoogleFLPScanner.sLatitude);
            object.put("gfLongitude", GoogleFLPScanner.sLongitude);
            object.put("stime", timeNow);
            object.put("board", Build.BOARD);
            object.put("bootloader", Build.BOOTLOADER);
            object.put("manufacturer", Build.MANUFACTURER);
            object.put("model_name", Build.MODEL);


            object.put("req_targettype", 0);
            object.put("idx_locserver", 0);

            object.put("proto_ver", 0);
            object.put("session_id", "nothing");
            object.put("req_cellidtype", 1);
            object.put("request_time", timeNow);
            object.put("collect_time", timeNow);
            object.put("sensor", null);
            object.put("req_posmethod", "FUSED");
            object.put("batch_mode", false);
            object.put("test_option", false);
            object.put("ltematch_method", 0);


            List<Map<String, Object>> gpsResultList = this.mGpsScanner.getResult();
            if(gpsResultList != null){
                JSONArray gpsArr = new JSONArray();
                gpsArr.put(new JSONArray(gpsResultList));
                object.put(CommonDoc.Scanner.GPS_SCAN_RESULT_KEY, gpsArr);
            }else{
                object.put(CommonDoc.Scanner.GPS_SCAN_RESULT_KEY, JSONObject.NULL);
            }

            List<Map<String, Object>> flpResultList = this.mGFlpScanner.getResult();
            if(flpResultList != null){
                JSONArray flpArr = new JSONArray();
                flpArr.put(new JSONArray(flpResultList));
                object.put(CommonDoc.Scanner.GFLP_SCAN_RESULT_KEY, flpArr);
            }else{
                object.put(CommonDoc.Scanner.GFLP_SCAN_RESULT_KEY, JSONObject.NULL);
            }

            List<Map<String, Object>> lteResultList = this.mLteScanner.getResult();
            if(lteResultList != null){
                JSONArray lteArr = new JSONArray();
                try {
                    lteArr.put(new JSONArray(new ArrayList<>(lteResultList)));
                    object.put(CommonDoc.Scanner.LTE_SCAN_RESULT_KEY, lteArr);
                }catch (Exception e){
                    System.out.println("간섭에 따른 에러");
                    object.put(CommonDoc.Scanner.LTE_SCAN_RESULT_KEY, JSONObject.NULL);
                }
            }else{
                object.put(CommonDoc.Scanner.LTE_SCAN_RESULT_KEY, JSONObject.NULL);
            }


            if (this.mWifiScanner != null) {
                List<Map<String, Object>> wifiResultList = this.mWifiScanner.getResult();

                if (wifiResultList != null && !wifiResultList.isEmpty()) {
                    JSONArray wifiArr = new JSONArray();
                    wifiArr.put(new JSONArray(wifiResultList));
                    object.put(CommonDoc.Scanner.WIFI_SCAN_RESULT_KEY, wifiArr);
                    Log.d("getResult", "WIFI data collected: " + wifiResultList.size() + " items");
                } else {
                    object.put(CommonDoc.Scanner.WIFI_SCAN_RESULT_KEY, JSONObject.NULL);
                    Log.w("getResult", "WIFI scan result is null or empty after polling");
                }
            } else {
                object.put(CommonDoc.Scanner.WIFI_SCAN_RESULT_KEY, JSONObject.NULL);
                Log.w("getResult", "WIFI scanner instance is null");
            }


            List<Map<String, Object>> bleResultList = this.mBleScanner.getResult();
            if(bleResultList != null){
                JSONArray bleArr = new JSONArray();
                bleArr.put(new JSONArray(bleResultList));
                object.put(CommonDoc.Scanner.BLE_SCAN_RESULT_KEY, bleArr);
            }else{
                object.put(CommonDoc.Scanner.BLE_SCAN_RESULT_KEY, JSONObject.NULL);
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object;
    }

    public JSONObject getResult(double lnt, double lat) {
        JSONObject object = new JSONObject();

        try {

            long timeNow = System.currentTimeMillis();
            object.put("name", this.mLatitude != null? this.mLatitude : "");
            object.put("comment", this.mLongitude != null? this.mLongitude : "");

            object.put("latitude", lat);
            object.put("longitude", lnt);
            object.put("stime", timeNow);
            object.put("board", Build.BOARD);
            object.put("bootloader", Build.BOOTLOADER);
            object.put("manufacturer", Build.MANUFACTURER);
            object.put("model_name", Build.MODEL);


            object.put("req_targettype", 0);
            object.put("idx_locserver", 0);

            object.put("proto_ver", 0);
            object.put("session_id", "nothing");
            object.put("req_cellidtype", 1);
            object.put("request_time", timeNow);
            object.put("collect_time", timeNow);
            object.put("sensor", null);
            object.put("req_posmethod", "FUSED");
            object.put("batch_mode", false);
            object.put("test_option", false);
            object.put("ltematch_method", 0);


            List<Map<String, Object>> gpsResultList = this.mGpsScanner.getResult();
            if(gpsResultList != null){
                JSONArray gpsArr = new JSONArray();
                gpsArr.put(new JSONArray(gpsResultList));
                object.put(CommonDoc.Scanner.GPS_SCAN_RESULT_KEY, gpsArr);
            }else{
                object.put(CommonDoc.Scanner.GPS_SCAN_RESULT_KEY, JSONObject.NULL);
            }

            List<Map<String, Object>> flpResultList = this.mGFlpScanner.getResult();
            if(flpResultList != null){
                JSONArray flpArr = new JSONArray();
                flpArr.put(new JSONArray(flpResultList));
                object.put(CommonDoc.Scanner.GFLP_SCAN_RESULT_KEY, flpArr);
            }else{
                object.put(CommonDoc.Scanner.GFLP_SCAN_RESULT_KEY, JSONObject.NULL);
            }

            List<Map<String, Object>> lteResultList = this.mLteScanner.getResult();
            if(lteResultList != null){
                JSONArray lteArr = new JSONArray();
                lteArr.put(new JSONArray(lteResultList));
                object.put(CommonDoc.Scanner.LTE_SCAN_RESULT_KEY, lteArr);
            }else{
                object.put(CommonDoc.Scanner.LTE_SCAN_RESULT_KEY, JSONObject.NULL);
            }

            List<Map<String, Object>> wifiResultList = this.mWifiScanner.getResult();
            if(wifiResultList != null){
                JSONArray wifiArr = new JSONArray();
                wifiArr.put(new JSONArray(wifiResultList));
                object.put(CommonDoc.Scanner.WIFI_SCAN_RESULT_KEY, wifiArr);
            }else{
                object.put(CommonDoc.Scanner.WIFI_SCAN_RESULT_KEY, JSONObject.NULL);
            }

            List<Map<String, Object>> bleResultList = this.mBleScanner.getResult();
            if(bleResultList != null){
                JSONArray bleArr = new JSONArray();
                bleArr.put(new JSONArray(bleResultList));
                object.put(CommonDoc.Scanner.BLE_SCAN_RESULT_KEY, bleArr);
            }else{
                object.put(CommonDoc.Scanner.BLE_SCAN_RESULT_KEY, JSONObject.NULL);
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object;
    }

    public void sendResultToFragment(String status, JSONObject object) {
        if (PositionFragment.sScanReceiveHandler != null) {
            Handler handler = PositionFragment.sScanReceiveHandler;

            Bundle bundle = new Bundle();
            bundle.putString(CommonDoc.Position.HANDLER_STATUS_KEY, status);

            if (CommonDoc.Position.HANDLER_SUCCEED_RESULT.equals(status)) {
                bundle.putString(CommonDoc.Position.HANDLER_KEY, mHandlerKey);
                bundle.putString(CommonDoc.Position.HANDLER_RESULT_KEY, object.toString());
            }

            if (CommonDoc.Position.HANDLER_STATUS_UPDATE.equals(status) && object != null) {
                bundle.putString(CommonDoc.Position.HANDLER_RESULT_KEY, object.toString());
            }

            Message message = handler.obtainMessage();
            message.setData(bundle);

            handler.sendMessage(message);
        }
    }


    private JSONObject createTestJson() {
        String jsonstr="{\n" +
                "  \"req_targettype\": 0,\n" +
                "  \"idx_locserver\": 3,\n" +
                "  \"vertical_pos_info\": {\n" +
                "    \"altitude\": 0,\n" +
                "    \"accuracy_v\": 0,\n" +
                "    \"floor\": 0\n" +
                "  },\n" +
                "  \"latitude\": 35.104185,\n" +
                "  \"lte_measurements\": [\n" +
                "    [\n" +
                "      {\n" +
                "        \"rssi\": 0,\n" +
                "        \"mnc\": \"8\",\n" +
                "        \"rsrq\": -8,\n" +
                "        \"net_type\": 4,\n" +
                "        \"pci\": 338,\n" +
                "        \"rsrp\": -75,\n" +
                "        \"band\": \"3743\",\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"mcc\": \"450\",\n" +
                "        \"ta\": 0,\n" +
                "        \"cid\": \"44249857\"\n" +
                "      }\n" +
                "    ]\n" +
                "  ],\n" +
                "  \"coarse_pos_info\": {\n" +
                "    \"latitude_coarse\": 0,\n" +
                "    \"longitude_coarse\": 0,\n" +
                "    \"accuracy_h\": 0\n" +
                "  },\n" +
                "  \"proto_ver\": \"0\",\n" +
                "  \"session_id\": \"18874\",\n" +
                "  \"req_cellidtype\": 1,\n" +
                "  \"request_time\": 1746144703000,\n" +
                "  \"model_name\": \"\",\n" +
                "  \"wifi_measurements\": [\n" +
                "    [\n" +
                "      {\n" +
                "        \"rssi\": -73,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"1296cd839a13\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -78,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"12f45e04e00b\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -65,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"144d67dab88d\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -69,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"2e4ee91218a7\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -69,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"3464a96662d9\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -76,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"38f45e04e00b\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -61,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"588694467e9a\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -65,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"705dcccb8374\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -65,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"769779421b20\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -27,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"ae8d015d7a4d\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -75,\n" +
                "        \"band\": 24,\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"ssid\": \"\",\n" +
                "        \"mac\": \"b21921d090b3\"\n" +
                "      }\n" +
                "    ]\n" +
                "  ],\n" +
                "  \"gnss\": [],\n" +
                "  \"sensor\": [],\n" +
                "  \"req_posmethod\": \"Fused\",\n" +
                "  \"longitude\": 129.039699,\n" +
                "  \"bluetooth_measurements\": [\n" +
                "    [\n" +
                "      {\n" +
                "        \"rssi\": -55,\n" +
                "        \"major\": \"0\",\n" +
                "        \"minor\": \"0\",\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"uuid\": \"\",\n" +
                "        \"mac\": \"15203425ABEB\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -75,\n" +
                "        \"major\": \"0\",\n" +
                "        \"minor\": \"0\",\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"uuid\": \"\",\n" +
                "        \"mac\": \"1E356BCEFC95\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -85,\n" +
                "        \"major\": \"0\",\n" +
                "        \"minor\": \"0\",\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"uuid\": \"\",\n" +
                "        \"mac\": \"405FBA0946C2\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -89,\n" +
                "        \"major\": \"0\",\n" +
                "        \"minor\": \"0\",\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"uuid\": \"\",\n" +
                "        \"mac\": \"5181BE393F5C\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"rssi\": -67,\n" +
                "        \"major\": \"0\",\n" +
                "        \"minor\": \"0\",\n" +
                "        \"time\": 1752125220552,\n" +
                "        \"uuid\": \"\",\n" +
                "        \"mac\": \"7104D0470B11\"\n" +
                "      }\n" +
                "    ]\n" +
                "  ],\n" +
                "  \"test_option\": true\n" +
                "}";

        JSONObject jObj = null;
        try {
            jObj = new JSONObject(jsonstr);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return jObj;
    }


}

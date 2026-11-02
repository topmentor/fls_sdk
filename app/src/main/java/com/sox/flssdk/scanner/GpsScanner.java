package com.sox.flssdk.scanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.model.Buffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GpsScanner implements Scanner, LocationListener {
    private static final int BUFFER_CAPACITY = 30;

    private final Context mContext;
    LocationManager mLocationManager;

    Buffer<Map<String, Object>> mBuffer;

    public static volatile double sLatitude;
    public static volatile double sLongitude;

    public GpsScanner(Context context) {
        this.mContext = context;
        this.mLocationManager = (LocationManager) this.mContext.getSystemService(Context.LOCATION_SERVICE);
        this.mBuffer = new Buffer<>(BUFFER_CAPACITY);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        sLatitude = location.getLatitude();
        sLongitude = location.getLongitude();

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("time", System.currentTimeMillis());
        resultMap.put("latitude", location.getLatitude());
        resultMap.put("longitude", location.getLongitude());

        mBuffer.enqueue(resultMap);
    }


    @Override
    public void startScan() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(mContext, "GPS를 확인해주세요.", Toast.LENGTH_SHORT).show();

            return;
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                CommonDoc.Scanner.GPS_SCAN_INTERVAL,
                0.0f,
                this);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                CommonDoc.Scanner.GPS_SCAN_INTERVAL,
                0.0f,
                this);
        mLocationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,
                CommonDoc.Scanner.GPS_SCAN_INTERVAL,
                0.0f,
                this);

        Log.i("GPS Scanner", "start scan");
    }


    @Override
    public void stopScan() {
        mLocationManager.removeUpdates(this);

        Log.i("GPS Scanner", "stop scan");
    }


    @Override
    public List<Map<String, Object>> getResult() {
        Long currentTime = System.currentTimeMillis();

        List<Map<String, Object>> resultList = new ArrayList<>();
        List<Map<String, Object>> dataList = mBuffer.toList();
        for (Map<String, Object> dataMap : dataList) {
            Long collectTime = (Long) dataMap.get("time");

            if (currentTime - collectTime < CommonDoc.Scanner.SCAN_THRESHOLD) {
                resultList.add(dataMap);
            }
        }

        return !resultList.isEmpty()? resultList : null;
    }
}

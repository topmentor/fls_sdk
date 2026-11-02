package com.sox.flssdk.scanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;
import android.os.Looper;


import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.activity.MainActivity;
import com.sox.flssdk.model.Buffer;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleFLPScanner implements Scanner{
    private static final int BUFFER_CAPACITY = 30;

    private static final String TAG = "Google FLP Scanner";

    private final Context mContext;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    Buffer<Map<String, Object>> mBuffer;

    public static volatile double sLatitude;
    public static volatile double sLongitude;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    public GoogleFLPScanner(Context context) {
        this.mContext = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.mContext);
        this.mBuffer = new Buffer<>(BUFFER_CAPACITY);

        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    String locationInfo = "위도: " + location.getLatitude() + "\n" +
                            "경도: " + location.getLongitude() + "\n" +
                            "정확도: " + location.getAccuracy() + "m";
                    Log.d(TAG, "Google FLP 위치 업데이트: " + locationInfo);

                    sLatitude = location.getLatitude();
                    sLongitude = location.getLongitude();

                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("time", System.currentTimeMillis());
                    resultMap.put("latitude", location.getLatitude());
                    resultMap.put("longitude", location.getLongitude());
                    resultMap.put("accuracy", location.getAccuracy());

                    mBuffer.enqueue(resultMap);


                }
            }
        };

        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity)mContext,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startScan();
        }
    }

    @Override
    public void startScan() {

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(mContext, "ACCESS_FINE_LOCATION 권한을 확인해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());

        Log.i(TAG, "start scan");
    }


    @Override
    public void stopScan() {
        fusedLocationClient.removeLocationUpdates(locationCallback);

        Log.i(TAG, "stop scan");
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

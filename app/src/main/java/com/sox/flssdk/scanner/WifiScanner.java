package com.sox.flssdk.scanner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.sox.flssdk.CommonDoc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class WifiScanner implements Scanner {
    private static final String TAG = "WIFI Scanner";

    private final Context mContext;
    volatile WifiManager mWifiManager;
    WifiScanReceiver mWifiScanReceiver;

    volatile List<Map<String, Object>> mResultList;

    Timer mScanTimer;
    TimerTask mScanTimerTask;

    public WifiScanner(Context context) {
        this.mContext = context;
        this.mWifiManager = (WifiManager) this.mContext.getSystemService(Context.WIFI_SERVICE);
        this.mResultList = new ArrayList<>();
    }

    private boolean hasWifiScanPermission() {
        if (Build.VERSION.SDK_INT >= 33 && mContext.getApplicationInfo().targetSdkVersion >= 33) {
            return ContextCompat.checkSelfPermission(mContext,
                "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(mContext,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }


    private class ScanTimerTask extends TimerTask {
        @Override
        public void run() {
            if (!hasWifiScanPermission()) {
                Log.e(TAG, "No Wi-Fi scan permission");
                return;
            }

            WifiManager wifiManager = mWifiManager;
            if (wifiManager == null) return;

            boolean started;
            try {
                started = wifiManager.startScan();
            } catch (SecurityException e) {
                Log.e(TAG, "startScan() denied: " + e.getMessage());
                return;
            }

            if (started) {
                Log.d(TAG, "startScan() succeeded — waiting for broadcast");
            } else {
                Log.w(TAG, "startScan() throttled — using cached results immediately");
                collectResults(false);
            }
        }
    }


    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) return;

            boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            Log.d(TAG, "SCAN_RESULTS_AVAILABLE — updated=" + updated + (updated ? "" : " (cached)"));
            collectResults(updated);
        }
    }


    private void collectResults(boolean fresh) {
        WifiManager wifiManager = mWifiManager;
        if (wifiManager == null) return;

        List<ScanResult> scanResults;
        try {
            scanResults = wifiManager.getScanResults();
        } catch (SecurityException e) {
            Log.e(TAG, "getScanResults() denied: " + e.getMessage());
            return;
        }
        if (scanResults == null) return;

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (ScanResult r : scanResults) {
            Map<String, Object> m = new HashMap<>();
            m.put("rssi", r.level);
            m.put("band", r.frequency);
            m.put("ssid", r.SSID);
            m.put("mac", r.BSSID);
            m.put("time", System.currentTimeMillis());
            resultList.add(m);
        }
        mResultList = resultList;

        Log.i(TAG, "Collected " + resultList.size() + " networks" + (fresh ? "" : " (from cache)"));
    }


    @Override
    public void startScan() {
        if (!hasWifiScanPermission()) {
            Log.e(TAG, "Missing Wi-Fi scan permission — scan aborted");
            return;
        }

        if (mWifiManager == null) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
        if (mScanTimer == null) {
            mScanTimer = new Timer();
        }
        if (mScanTimerTask != null) {
            mScanTimerTask.cancel();
        }

        if (mWifiScanReceiver != null) {
            try { mContext.unregisterReceiver(mWifiScanReceiver); } catch (Exception ignored) {}
        }
        mWifiScanReceiver = new WifiScanReceiver();
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mContext.registerReceiver(mWifiScanReceiver, intentFilter);

        mScanTimerTask = new ScanTimerTask();
        mScanTimer.schedule(mScanTimerTask, 0, CommonDoc.Scanner.WIFI_SCAN_INTERVAL);
        Log.i(TAG, "Wi-Fi scan started (interval=" + CommonDoc.Scanner.WIFI_SCAN_INTERVAL + "ms)");
    }


    @Override
    public void stopScan() {
        if (mScanTimerTask != null) {
            mScanTimerTask.cancel();
            mScanTimerTask = null;
        }

        if (mScanTimer != null) {
            mScanTimer.cancel();
            mScanTimer = null;
        }

        if (mWifiScanReceiver != null) {
            try { mContext.unregisterReceiver(mWifiScanReceiver); } catch (Exception ignored) {}
            mWifiScanReceiver = null;
        }

        mWifiManager = null;
        Log.i(TAG, "Wi-Fi scan stopped");
    }


    @Override
    public List<Map<String, Object>> getResult() {
        return mResultList;
    }
}
